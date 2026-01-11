package com.ntg.appsbroker.schemas;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ntg.appsbroker.domain.mcp.McpVersion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Schema: MCP response DTO.
 */
public record McpResponse(
    @JsonProperty("meta")
    Meta meta,
    
    String status,
    
    Map<String, Object> result,
    
    McpError error
) {
    public static McpResponse success(UUID requestId, Map<String, Object> result) {
        return new McpResponse(
            new Meta(
                McpVersion.SCHEMA_VERSION,
                McpVersion.API_VERSION,
                Instant.now(),
                requestId
            ),
            "success",
            result,
            null
        );
    }
    
    public static McpResponse failure(UUID requestId, McpError error) {
        return new McpResponse(
            new Meta(
                McpVersion.SCHEMA_VERSION,
                McpVersion.API_VERSION,
                Instant.now(),
                requestId
            ),
            "error",
            null,
            error
        );
    }
    
    public record Meta(
        @JsonProperty("schema_version")
        String schemaVersion,
        
        @JsonProperty("api_version")
        String apiVersion,
        
        String timestamp,
        
        @JsonProperty("request_id")
        UUID requestId
    ) {
        public Meta(String schemaVersion, String apiVersion, Instant timestamp, UUID requestId) {
            this(schemaVersion, apiVersion, timestamp.toString(), requestId);
        }
    }
}

