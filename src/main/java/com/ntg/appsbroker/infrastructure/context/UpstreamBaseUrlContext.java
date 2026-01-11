package com.ntg.appsbroker.infrastructure.context;

import com.ntg.appsbroker.infrastructure.util.BaseUrlUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-request upstream base URL overrides, typically injected by the local bridge from Cursor's mcp.json.
 *
 * <p>Note: A remote server cannot read a user's local mcp.json directly. The bridge must send these values
 * as part of each MCP tool call (e.g., in the arguments map).</p>
 */
@Component
public class UpstreamBaseUrlContext {
    private static final ThreadLocal<Overrides> OVERRIDES = new ThreadLocal<>();

    private final boolean overrideEnabled;

    public UpstreamBaseUrlContext(
        @Value("${mcp.upstream.baseurl-override-enabled:true}") boolean overrideEnabled
    ) {
        this.overrideEnabled = overrideEnabled;
    }

    public void set(String authBaseUrl, String appsBaseUrl) {
        if (!overrideEnabled) {
            return;
        }
        String normalizedAuth = normalizeOrNull(authBaseUrl);
        String normalizedApps = normalizeOrNull(appsBaseUrl);
        if (normalizedAuth == null && normalizedApps == null) {
            return;
        }
        OVERRIDES.set(new Overrides(normalizedAuth, normalizedApps));
    }

    public String getAuthBaseUrlOrNull() {
        Overrides o = OVERRIDES.get();
        return o == null ? null : o.authBaseUrl;
    }

    public String getAppsBaseUrlOrNull() {
        Overrides o = OVERRIDES.get();
        return o == null ? null : o.appsBaseUrl;
    }

    public void clear() {
        OVERRIDES.remove();
    }

    private static String normalizeOrNull(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isBlank()) return null;
        return BaseUrlUtil.normalize(trimmed);
    }

    private record Overrides(String authBaseUrl, String appsBaseUrl) {}
}


