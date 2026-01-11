package com.ntg.appsbroker.domain;

/**
 * Sealed interface for MCP operation outcomes.
 */
public sealed interface McpOutcome 
    permits McpSuccess, McpFailure {
}

