package com.ntg.appsbroker.infrastructure.auth;

import java.util.Map;

/**
 * Result of login operation.
 */
public record LoginResult(
    String sessionToken,
    Map<String, Object> body
) {}

