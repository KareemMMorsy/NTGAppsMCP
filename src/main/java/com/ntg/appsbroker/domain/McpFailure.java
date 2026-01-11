package com.ntg.appsbroker.domain;

import java.util.UUID;

/**
 * Domain entity: failed MCP operation result.
 */
public record McpFailure(
    UUID requestId,
    AppError error
) implements McpOutcome {}

