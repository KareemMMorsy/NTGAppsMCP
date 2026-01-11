package com.ntg.appsbroker.schemas;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Schema: MCP request DTO.
 */
public record McpRequest(
    @NotNull
    @JsonProperty("request_id")
    UUID requestId,
    
    @NotBlank
    String action,
    
    @NotNull
    Map<String, Object> parameters
) {}

