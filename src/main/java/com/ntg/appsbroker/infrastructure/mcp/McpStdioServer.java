package com.ntg.appsbroker.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntg.appsbroker.domain.McpRequestData;
import com.ntg.appsbroker.infrastructure.context.UpstreamBaseUrlContext;
import com.ntg.appsbroker.usecases.HandleMcpRequestUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Infrastructure: MCP stdio server for Cursor integration.
 * 
 * Communicates via JSON-RPC 2.0 over stdin/stdout.
 * Only runs when MCP_STDIO_MODE environment variable is set.
 */
@Component
public class McpStdioServer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(McpStdioServer.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "ntg-apps-broker";
    private static final String SERVER_VERSION = "1.0.0";
    
    private final HandleMcpRequestUseCase useCase;
    private final ObjectMapper objectMapper;
    private final UpstreamBaseUrlContext upstreamBaseUrlContext;
    
    public McpStdioServer(HandleMcpRequestUseCase useCase, ObjectMapper objectMapper, UpstreamBaseUrlContext upstreamBaseUrlContext) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
        this.upstreamBaseUrlContext = upstreamBaseUrlContext;
    }
    
    @Override
    public void run(String... args) {
        // Only run if MCP_STDIO_MODE env var is set or mcp.stdio.enabled property is true
        String stdioMode = System.getenv("MCP_STDIO_MODE");
        String stdioEnabled = System.getProperty("mcp.stdio.enabled");
        
        if (!"true".equals(stdioMode) && !"true".equals(stdioEnabled)) {
            log.debug("MCP stdio mode not enabled, skipping stdio server");
            return;
        }
        
        log.info("Starting MCP stdio server (protocol version: {})", PROTOCOL_VERSION);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msg = objectMapper.readValue(line, Map.class);
                    handleMessage(msg);
                } catch (Exception e) {
                    log.error("Error parsing MCP message", e);
                    writeError(null, -32700, "Parse error", Map.of("error", e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("MCP stdio server error", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void handleMessage(Map<String, Object> msg) {
        Object id = msg.get("id");
        String method = (String) msg.get("method");
        Map<String, Object> params = (Map<String, Object>) msg.getOrDefault("params", new HashMap<>());
        
        try {
            switch (method) {
                case "initialize" -> handleInitialize(id);
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolCall(id, params);
                case "shutdown", "exit" -> {
                    writeResult(id, Map.of());
                    System.exit(0);
                }
                default -> writeError(id, -32601, "Method not found", 
                    Map.of("method", method != null ? method : "null"));
            }
        } catch (Exception e) {
            log.error("Error handling MCP message: method={}", method, e);
            writeError(id, -32603, "Internal error", Map.of("error", e.getMessage()));
        }
    }
    
    private void handleInitialize(Object id) {
        Map<String, Object> result = Map.of(
            "protocolVersion", PROTOCOL_VERSION,
            "capabilities", Map.of("tools", Map.of()),
            "serverInfo", Map.of("name", SERVER_NAME, "version", SERVER_VERSION)
        );
        writeResult(id, result);
    }
    
    private void handleToolsList(Object id) {
        List<Map<String, Object>> tools = List.of(
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
                            "Optional. Used only if the app already exists. New app name to import under."),
                        "debug", Map.of("type", "boolean", "description",
                            "Optional. If true, include full upstream API payloads for debugging. Default: false.")
                    ),
                    "required", List.of("appName"),
                    "additionalProperties", false
                )
            )
        );
        writeResult(id, Map.of("tools", tools));
    }
    
    @SuppressWarnings("unchecked")
    private void handleToolCall(Object id, Map<String, Object> params) {
        String name = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", new HashMap<>());
        
        if (name == null) {
            writeError(id, -32602, "Invalid params", Map.of("error", "Missing 'name' in params"));
            return;
        }
        
        String clientId = (String) arguments.get("clientId");

        String authBaseUrl = (String) arguments.get("authBaseUrl");
        String appsBaseUrl = (String) arguments.get("appsBaseUrl");

        var request = new McpRequestData(
            UUID.randomUUID(),
            name,
            arguments
        );

        var outcome = (com.ntg.appsbroker.domain.McpOutcome) null;
        try {
            upstreamBaseUrlContext.set(authBaseUrl, appsBaseUrl);
            outcome = useCase.execute(request, clientId);
        } finally {
            upstreamBaseUrlContext.clear();
        }
        
        try {
            Map<String, Object> content;
            if (outcome instanceof com.ntg.appsbroker.domain.McpSuccess success) {
                String jsonText = objectMapper.writeValueAsString(success.result());
                content = Map.of("type", "text", "text", jsonText);
            } else {
                var failure = (com.ntg.appsbroker.domain.McpFailure) outcome;
                Map<String, Object> errorObj = Map.of(
                    "code", failure.error().code(),
                    "message", failure.error().message(),
                    "details", failure.error().details() != null ? failure.error().details() : Map.of()
                );
                String jsonText = objectMapper.writeValueAsString(errorObj);
                content = Map.of("type", "text", "text", jsonText);
            }
            
            writeResult(id, Map.of("content", List.of(content)));
        } catch (Exception e) {
            log.error("Error serializing tool response", e);
            writeError(id, -32603, "Internal error", Map.of("error", e.getMessage()));
        }
    }
    
    private void writeResult(Object id, Object result) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("result", result);
            
            String json = objectMapper.writeValueAsString(response);
            System.out.println(json);
            System.out.flush();
        } catch (Exception e) {
            log.error("Error writing result", e);
        }
    }
    
    private void writeError(Object id, int code, String message, Map<String, Object> data) {
        try {
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
            
            String json = objectMapper.writeValueAsString(response);
            System.out.println(json);
            System.out.flush();
        } catch (Exception e) {
            log.error("Error writing error", e);
        }
    }
}

