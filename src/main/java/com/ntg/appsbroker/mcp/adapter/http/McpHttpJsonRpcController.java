package com.ntg.appsbroker.mcp.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.appsbroker.domain.McpFailure;
import com.ntg.appsbroker.domain.McpRequestData;
import com.ntg.appsbroker.domain.McpSuccess;
import com.ntg.appsbroker.usecases.HandleMcpRequestUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP over HTTP: JSON-RPC 2.0 POST endpoint compatible with MCP clients/bridges.
 *
 * Endpoint: POST /mcp
 * Body: JSON-RPC message
 */
@RestController
public class McpHttpJsonRpcController {
    private static final Logger log = LoggerFactory.getLogger(McpHttpJsonRpcController.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "ntg-apps-broker";
    private static final String SERVER_VERSION = "1.0.0";

    private final HandleMcpRequestUseCase useCase;
    private final ObjectMapper objectMapper;
    private final String httpAuthToken;

    public McpHttpJsonRpcController(
        HandleMcpRequestUseCase useCase,
        ObjectMapper objectMapper,
        @Value("${mcp.http.auth-token:}") String httpAuthToken
    ) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
        this.httpAuthToken = httpAuthToken;
    }

    @PostMapping(path = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(
        @RequestBody Map<String, Object> msg,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (httpAuthToken != null && !httpAuthToken.isBlank()) {
            String token = extractBearer(authorization);
            if (token == null || !httpAuthToken.equals(token)) {
                return jsonRpcError(msg.get("id"), -32001, "Unauthorized", Map.of());
            }
        }

        Object id = msg.get("id");
        String method = (String) msg.get("method");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) msg.getOrDefault("params", new HashMap<>());

        try {
            return switch (method) {
                case "initialize" -> jsonRpcResult(id, Map.of(
                    "protocolVersion", PROTOCOL_VERSION,
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", SERVER_NAME, "version", SERVER_VERSION)
                ));
                case "tools/list" -> jsonRpcResult(id, Map.of("tools", toolList()));
                case "tools/call" -> handleToolCall(id, params);
                case "shutdown", "exit" -> jsonRpcResult(id, Map.of());
                default -> jsonRpcError(id, -32601, "Method not found", Map.of(
                    "method", method != null ? method : "null"
                ));
            };
        } catch (Exception e) {
            log.error("Error handling MCP HTTP message: method={}", method, e);
            return jsonRpcError(id, -32603, "Internal error", Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Object id, Map<String, Object> params) throws Exception {
        String name = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", new HashMap<>());

        if (name == null) {
            return jsonRpcError(id, -32602, "Invalid params", Map.of("error", "Missing 'name' in params"));
        }

        String clientId = (String) arguments.get("clientId");

        var request = new McpRequestData(UUID.randomUUID(), name, arguments);
        var outcome = useCase.execute(request, clientId);

        Map<String, Object> content;
        if (outcome instanceof McpSuccess success) {
            String jsonText = objectMapper.writeValueAsString(success.result());
            content = Map.of("type", "text", "text", jsonText);
        } else {
            var failure = (McpFailure) outcome;
            Map<String, Object> errorObj = Map.of(
                "code", failure.error().code(),
                "message", failure.error().message(),
                "details", failure.error().details() != null ? failure.error().details() : Map.of()
            );
            String jsonText = objectMapper.writeValueAsString(errorObj);
            content = Map.of("type", "text", "text", jsonText);
        }

        return jsonRpcResult(id, Map.of("content", List.of(content)));
    }

    private List<Map<String, Object>> toolList() {
        return List.of(
            Map.of(
                "name", "ping",
                "description", "Health check: returns pong.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "additionalProperties", false
                )
            ),
            Map.of(
                "name", "login",
                "description", "Login and store session token server-side keyed by clientId.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "username", Map.of("type", "string"),
                        "password", Map.of("type", "string"),
                        "companyname", Map.of("type", "string"),
                        "clientId", Map.of("type", "string")
                    ),
                    "required", List.of("username", "password", "companyname", "clientId"),
                    "additionalProperties", false
                )
            ),
            Map.of(
                "name", "create_app",
                "description", "Create app via saveApp. You can provide only appName; other fields are optional and will be auto-filled.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "clientId", Map.of("type", "string"),
                        "sessionToken", Map.of("type", "string", "description",
                            "Optional. If provided, bypasses stored login session and uses this token for the call."),
                        "AppearOnMobile", Map.of("type", "boolean", "description", "Optional. Default: true"),
                        "appName", Map.of("type", "string", "description", "Required. App display name."),
                        "appIdentifier", Map.of("type", "string", "description", "Optional. Default: derived 3-letter code from appName."),
                        "shortNotes", Map.of("type", "string", "description", "Optional. Default: appName"),
                        "icon", Map.of("type", "string", "description", "Optional. Default: fa fa-heart")
                    ),
                    "required", List.of("appName"),
                    "additionalProperties", false
                )
            ),
            Map.of(
                "name", "import_app",
                "description", "Import an app from MCP storage by appName using Import/Export APIs (uploadFile -> validateAppIdentifier -> importApp).",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "clientId", Map.of("type", "string"),
                        "sessionToken", Map.of("type", "string", "description",
                            "Optional. If provided, bypasses stored login session and uses this token for the call."),
                        "appName", Map.of("type", "string", "description",
                            "Required. App name (folder name under MCP_IMPORT_APPS_DIR). Server chooses the newest file in that folder."),
                        "newAppIdentifier", Map.of("type", "string", "description",
                            "Optional. Used only if the app already exists. New 3-letter identifier to import under."),
                        "newAppName", Map.of("type", "string", "description",
                            "Optional. Used only if the app already exists. New app name to import under.")
                    ),
                    "required", List.of("appName"),
                    "additionalProperties", false
                )
            )
        );
    }

    private static Map<String, Object> jsonRpcResult(Object id, Object result) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private static Map<String, Object> jsonRpcError(Object id, int code, String message, Map<String, Object> data) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (data != null && !data.isEmpty()) {
            error.put("data", data);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return response;
    }

    private static String extractBearer(String authorization) {
        if (authorization == null) return null;
        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix)) return null;
        return authorization.substring(prefix.length()).trim();
    }
}


