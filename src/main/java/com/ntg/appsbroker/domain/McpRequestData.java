package com.ntg.appsbroker.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Domain entity: MCP request data.
 */
public record McpRequestData(
    UUID requestId,
    String action,
    Map<String, Object> parameters
) {}

