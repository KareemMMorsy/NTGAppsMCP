package com.ntg.appsbroker.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Domain entity: successful MCP operation result.
 */
public record McpSuccess(
    UUID requestId,
    Map<String, Object> result
) implements McpOutcome {}

