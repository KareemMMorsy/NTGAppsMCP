package com.ntg.appsbroker.domain;

import java.util.Map;

/**
 * Domain entity: application error with code, message, and optional details.
 */
public record AppError(
    String code,
    String message,
    Map<String, Object> details
) {
    public AppError(String code, String message) {
        this(code, message, null);
    }
}

