package com.ntg.appsbroker.usecases;

import com.ntg.appsbroker.domain.*;
import com.ntg.appsbroker.ports.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Use case: Handle MCP requests and dispatch to appropriate action handlers.
 */
@Service
public class HandleMcpRequestUseCase {
    private static final Logger log = LoggerFactory.getLogger(HandleMcpRequestUseCase.class);
    private static final String DEFAULT_CLIENT_ID = "default";
    private static final SecureRandom RNG = new SecureRandom();
    
    private final AuthService authService;
    private final AppsService appsService;
    private final SessionStore sessionStore;
    private final String importAppsDir;
    
    public HandleMcpRequestUseCase(
        AuthService authService,
        AppsService appsService,
        SessionStore sessionStore,
        @Value("${mcp.import.apps-dir:storage/import-apps}") String importAppsDir
    ) {
        this.authService = authService;
        this.appsService = appsService;
        this.sessionStore = sessionStore;
        this.importAppsDir = importAppsDir;
    }
    
    public McpOutcome execute(McpRequestData request, String clientId) {
        log.debug("Executing MCP request: action={}, clientId={}", request.action(), clientId);
        
        // Session enforcement for protected actions
        if (!request.action().equals("ping") && 
            !request.action().equals("login") &&
            !request.action().equals("ai.intent")) {
            
            // Allow callers to pass sessionToken explicitly (e.g., from Cursor env),
            // otherwise fall back to the stored/default token for the provided clientId.
            String providedToken = null;
            Object providedTokenObj = request.parameters().get("sessionToken");
            if (providedTokenObj instanceof String s && !s.isBlank()) {
                providedToken = s;
            }

            if (providedToken == null) {
                String effectiveClientId = (clientId == null || clientId.isBlank())
                    ? DEFAULT_CLIENT_ID
                    : clientId;
            
                String token = sessionStore.getToken(effectiveClientId);
            if (token == null || token.isBlank()) {
                return new McpFailure(
                    request.requestId(),
                    new AppError("forbidden", "you must log in first", null)
                );
            }
            
            // Inject session token into parameters
            Map<String, Object> params = new HashMap<>(request.parameters());
            params.put("sessionToken", token);
            request = new McpRequestData(request.requestId(), request.action(), params);
            }
        }
        
        return switch (request.action()) {
            case "ping" -> handlePing(request);
            case "login" -> handleLogin(request, clientId);
            case "create_app" -> handleCreateApp(request);
            case "import_app" -> handleImportApp(request);
            default -> new McpFailure(
                request.requestId(),
                new AppError("invalid_action", "Unknown action", 
                    Map.of("action", request.action()))
            );
        };
    }
    
    private McpOutcome handlePing(McpRequestData request) {
        return new McpSuccess(request.requestId(), Map.of("message", "pong"));
    }
    
    private McpOutcome handleLogin(McpRequestData request, String clientId) {
        Map<String, Object> params = request.parameters();
        String username = (String) params.get("username");
        String password = (String) params.get("password");
        String companyname = (String) params.get("companyname");
        
        if (username == null || username.isBlank() || 
            password == null || password.isBlank() || 
            companyname == null || companyname.isBlank()) {
            return new McpFailure(
                request.requestId(),
                new AppError("validation_failed", "Missing required fields: username, password, companyname", null)
            );
        }
        
        try {
            var result = authService.login(username, password, companyname);
            String stableClientId = clientId != null && !clientId.isBlank() ? clientId : 
                companyname + "::" + username;
            sessionStore.setToken(stableClientId, result.sessionToken());
            
            log.info("Login successful: clientId={}", stableClientId);
            
            return new McpSuccess(
                request.requestId(),
                Map.of(
                    "sessionToken", result.sessionToken(),
                    "clientId", stableClientId
                )
            );
        } catch (Exception e) {
            log.error("Login failed", e);
            return new McpFailure(
                request.requestId(),
                new AppError("forbidden", "Login failed: " + e.getMessage(), 
                    Map.of("error", e.getMessage()))
            );
        }
    }
    
    private McpOutcome handleCreateApp(McpRequestData request) {
        Map<String, Object> params = request.parameters();
        
        // Validation
        Object appearOnMobileObj = params.get("AppearOnMobile");
        Boolean appearOnMobile = appearOnMobileObj instanceof Boolean ? 
            (Boolean) appearOnMobileObj : true;
        
        String appName = (String) params.get("appName");
        String appIdentifier = (String) params.get("appIdentifier");
        String shortNotes = (String) params.getOrDefault("shortNotes", null);
        String icon = (String) params.getOrDefault("icon", null);
        
        if (appName == null || appName.isBlank()) {
            return new McpFailure(
                request.requestId(),
                new AppError("validation_failed", 
                    "appName is required", null)
            );
        }

        String resolvedAppName = appName.trim();
        String resolvedAppIdentifier = (appIdentifier == null || appIdentifier.isBlank())
            ? generateAppIdentifier(resolvedAppName)
            : appIdentifier.trim();
        String resolvedShortNotes = (shortNotes == null || shortNotes.isBlank())
            ? resolvedAppName
            : shortNotes;
        String resolvedIcon = (icon == null || icon.isBlank())
            ? "fa fa-heart"
            : icon;
        
        Map<String, Object> spec = Map.of(
            "AppearOnMobile", appearOnMobile,
            "appName", resolvedAppName,
            "appIdentifier", resolvedAppIdentifier,
            "shortNotes", resolvedShortNotes,
            "icon", resolvedIcon
        );
        
        String sessionToken = (String) params.get("sessionToken");
        if (sessionToken == null || sessionToken.isBlank()) {
            return new McpFailure(
                request.requestId(),
                new AppError("validation_failed", "Missing sessionToken", null)
            );
        }
        
        try {
            var response = appsService.saveApp(spec, sessionToken);
            if (response.statusCode() != 200) {
                String message = "Apps service returned non-success status";
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    message = "Apps service rejected the sessionToken (unauthorized). Provide a valid Smart2Go UserSessionToken (set MCP_DEFAULT_SESSION_TOKEN or pass sessionToken).";
                }
                return new McpFailure(
                    request.requestId(),
                    new AppError("validation_failed", 
                        message,
                        Map.of(
                            "status_code", response.statusCode(),
                            "body", response.body() != null ? response.body() : Map.of()
                        ))
                );
            }
            
            log.info("App created successfully: appName={}, appIdentifier={}", resolvedAppName, resolvedAppIdentifier);
            
            return new McpSuccess(
                request.requestId(),
                Map.of(
                    "app", spec,
                    "appsService", Map.of(
                        "status_code", response.statusCode(),
                        "body", response.body()
                    )
                )
            );
        } catch (Exception e) {
            log.error("Failed to create app", e);
            return new McpFailure(
                request.requestId(),
                new AppError("internal_error", e.getMessage(), null)
            );
        }
    }

    private McpOutcome handleImportApp(McpRequestData request) {
        Map<String, Object> params = request.parameters();

        String appName = (String) params.get("appName");
        if (appName == null || appName.isBlank()) {
            return new McpFailure(
                request.requestId(),
                new AppError("validation_failed", "appName is required", null)
            );
        }

        String sessionToken = (String) params.get("sessionToken");
        if (sessionToken == null || sessionToken.isBlank()) {
            return new McpFailure(
                request.requestId(),
                new AppError("validation_failed", "Missing sessionToken", null)
            );
        }

        String requestedNewAppIdentifier = (String) params.get("newAppIdentifier");
        String requestedNewAppName = (String) params.get("newAppName");
        boolean debug = Boolean.TRUE.equals(params.get("debug"));

        Path selectedFile;
        try {
            selectedFile = resolveNewestImportFile(appName.trim());
        } catch (Exception e) {
            return new McpFailure(
                request.requestId(),
                new AppError("not_found", e.getMessage(), Map.of(
                    "importAppsDir", importAppsDir,
                    "appName", appName
                ))
            );
        }

        try {
            var uploadResp = appsService.uploadImportFile(selectedFile, sessionToken);
            if (uploadResp.statusCode() != 200) {
                return new McpFailure(
                    request.requestId(),
                    new AppError("upstream_error", "uploadFile failed", Map.of(
                        "status_code", uploadResp.statusCode(),
                        "body", uploadResp.body() != null ? uploadResp.body() : Map.of()
                    ))
                );
            }

            Map<String, Object> uploadBody = asMap(uploadResp.body());

            // Important note: uuid/appPath/... come from uploadFile response.
            String uploadedAppName = asString(uploadBody.get("appName"));
            String uploadedAppIdentifier = asString(uploadBody.get("appIdentifier"));
            String uploadedAppUuid = asString(uploadBody.get("appUuid"));

            if (isBlank(uploadedAppName) || isBlank(uploadedAppIdentifier) || isBlank(uploadedAppUuid)) {
                return new McpFailure(
                    request.requestId(),
                    new AppError("upstream_error", "uploadFile response missing required fields", Map.of(
                        "missing", List.of(
                            isBlank(uploadedAppName) ? "appName" : null,
                            isBlank(uploadedAppIdentifier) ? "appIdentifier" : null,
                            isBlank(uploadedAppUuid) ? "appUuid" : null
                        ).stream().filter(x -> x != null).toList(),
                        "uploadBody", uploadBody
                    ))
                );
            }

            Map<String, Object> validatePayload = Map.of(
                "appName", uploadedAppName,
                "appIdentifier", uploadedAppIdentifier,
                "appUuid", uploadedAppUuid
            );

            var validateResp = appsService.validateAppIdentifier(validatePayload, sessionToken);
            if (validateResp.statusCode() != 200) {
                return new McpFailure(
                    request.requestId(),
                    new AppError("upstream_error", "validateAppIdentifier failed", Map.of(
                        "status_code", validateResp.statusCode(),
                        "body", validateResp.body() != null ? validateResp.body() : Map.of()
                    ))
                );
            }

            Map<String, Object> validateBody = asMap(validateResp.body());
            boolean exists = !isBlank(asString(validateBody.get("existAppName"))) || Boolean.TRUE.equals(validateBody.get("allowMerge"));

            Map<String, Object> importPayload = new HashMap<>(uploadBody);

            Map<String, Object> conflictResolution = Map.of(
                "exists", exists,
                "requestedNewAppIdentifier", requestedNewAppIdentifier != null ? requestedNewAppIdentifier : "",
                "requestedNewAppName", requestedNewAppName != null ? requestedNewAppName : ""
            );

            if (exists) {
                String newAppName = !isBlank(requestedNewAppName)
                    ? requestedNewAppName.trim()
                    : (uploadedAppName + " (Imported)");

                String newAppIdentifier = !isBlank(requestedNewAppIdentifier)
                    ? requestedNewAppIdentifier.trim().toUpperCase()
                    : generateRandomAppIdentifier();

                importPayload.put("replaceAppIdentifier", true);
                importPayload.put("newAppIdentifier", newAppIdentifier);
                importPayload.put("newAppName", newAppName);
            }

            var importResp = appsService.importApp(importPayload, sessionToken);
            if (importResp.statusCode() != 200) {
                return new McpFailure(
                    request.requestId(),
                    new AppError("upstream_error", "importApp failed", Map.of(
                        "status_code", importResp.statusCode(),
                        "body", importResp.body() != null ? importResp.body() : Map.of()
                    ))
                );
            }

            // Return a clean summary by default (avoid dumping large upstream payloads like integrationRepositories).
            Map<String, Object> uploadSummary = Map.of(
                "appName", asString(uploadBody.get("appName")),
                "appIdentifier", asString(uploadBody.get("appIdentifier")),
                "appUuid", asString(uploadBody.get("appUuid")),
                "version", asString(uploadBody.get("version"))
            );

            Map<String, Object> validateSummary = Map.of(
                "isValid", validateBody.get("isValid"),
                "existAppName", validateBody.get("existAppName"),
                "allowMerge", validateBody.get("allowMerge")
            );

            Map<String, Object> importBody = asMap(importResp.body());
            Map<String, Object> importSummary = importBody.isEmpty()
                ? Map.of("body", importResp.body() != null ? importResp.body() : Map.of())
                : Map.of("returnValue", importBody.get("returnValue"));

            Map<String, Object> baseResult = new HashMap<>();
            baseResult.put("message", exists ? "imported_with_conflict_resolution" : "imported");
            baseResult.put("selectedFile", selectedFile.toString());
            baseResult.put("uploaded", uploadSummary);
            baseResult.put("validate", validateSummary);
            baseResult.put("import", importSummary);
            baseResult.put("conflictResolution", conflictResolution);

            if (exists) {
                baseResult.put("importedAs", Map.of(
                    "newAppName", importPayload.getOrDefault("newAppName", ""),
                    "newAppIdentifier", importPayload.getOrDefault("newAppIdentifier", "")
                ));
            }

            if (debug) {
                baseResult.put("debugUpstream", Map.of(
                    "uploadFile", Map.of("status_code", uploadResp.statusCode(), "body", uploadBody),
                    "validateAppIdentifier", Map.of("status_code", validateResp.statusCode(), "body", validateBody),
                    "importApp", Map.of("status_code", importResp.statusCode(), "body", importResp.body())
                ));
            }

            return new McpSuccess(
                request.requestId(),
                baseResult
            );
        } catch (Exception e) {
            log.error("Failed to import app", e);
            return new McpFailure(
                request.requestId(),
                new AppError("internal_error", e.getMessage(), null)
            );
        }
    }

    private Path resolveNewestImportFile(String appName) throws IOException {
        Path root = Paths.get(importAppsDir);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IOException("Import storage directory not found: " + root.toAbsolutePath());
        }

        Path resolvedAppDir = root.resolve(appName);
        if (!Files.exists(resolvedAppDir) || !Files.isDirectory(resolvedAppDir)) {
            // Try case-insensitive match by scanning directories (useful on Linux with mixed casing)
            try (Stream<Path> dirs = Files.list(root)) {
                Optional<Path> match = dirs
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(appName))
                    .findFirst();
                if (match.isPresent()) {
                    resolvedAppDir = match.get();
                }
            }
        }

        if (Files.exists(resolvedAppDir) && Files.isDirectory(resolvedAppDir)) {
            final Path appDir = resolvedAppDir;
            try (Stream<Path> files = Files.list(appDir)) {
                return files
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .orElseThrow(() -> new IOException("No files found in import folder: " + appDir.toAbsolutePath()));
            }
        }

        // Fallback: allow flat files under root that match appName (helps when users just drop files into /apps)
        try (Stream<Path> files = Files.list(root)) {
            return files
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().contains(appName.toLowerCase()))
                .max(Comparator.comparingLong(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis();
                    } catch (IOException e) {
                        return 0L;
                    }
                }))
                .orElseThrow(() -> new IOException(
                    "No folder or matching file found for appName under import storage: " + appName +
                        " (looked in: " + root.toAbsolutePath() + ")"
                ));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object body) {
        if (body instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String generateRandomAppIdentifier() {
        // 3-letter A-Z identifier
        char a = (char) ('A' + RNG.nextInt(26));
        char b = (char) ('A' + RNG.nextInt(26));
        char c = (char) ('A' + RNG.nextInt(26));
        return "" + a + b + c;
    }

    private static String generateAppIdentifier(String appName) {
        // Generate a 3-letter identifier from the app name (A-Z only), padding with X as needed.
        String lettersOnly = appName.replaceAll("[^A-Za-z]", "").toUpperCase();
        if (lettersOnly.length() >= 3) {
            return lettersOnly.substring(0, 3);
        }
        return (lettersOnly + "XXX").substring(0, 3);
    }
}

