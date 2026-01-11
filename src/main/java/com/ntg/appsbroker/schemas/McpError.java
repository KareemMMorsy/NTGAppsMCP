package com.ntg.appsbroker.schemas;

import java.util.Map;

/**
 * Schema: MCP error DTO.
 */
public record McpError(
    String code,
    String message,
    Map<String, Object> details
) {
    public McpError(String code, String message) {
        this(code, message, null);
    }
}

