package com.ntg.appsbroker.infrastructure.util;

/**
 * Small helper for normalizing backend base URLs.
 *
 * Some NTG deployments historically used a "/Smart2Go" segment in the base URL,
 * but the actual REST endpoints used by this MCP server are "/rest/...".
 * To avoid accidental 404s, we strip a trailing "/Smart2Go" by default.
 */
public final class BaseUrlUtil {
    private BaseUrlUtil() {}

    public static String normalize(String baseUrl) {
        if (baseUrl == null) return null;
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/Smart2Go")) {
            return trimmed.substring(0, trimmed.length() - "/Smart2Go".length());
        }
        return trimmed;
    }
}





