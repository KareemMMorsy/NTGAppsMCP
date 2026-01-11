package com.ntg.appsbroker.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs effective runtime configuration (non-secret) to help debug deployments (e.g. Railway env vars).
 */
@Component
public class StartupConfigLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupConfigLogger.class);

    private final Environment env;

    public StartupConfigLogger(Environment env) {
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Do NOT log tokens/secrets here.
        String httpSseMode = getenv("MCP_HTTP_SSE_MODE");
        String stdioMode = getenv("MCP_STDIO_MODE");

        String authEnabled = env.getProperty("mcp.auth.integration-enabled", "false");
        String authBaseUrl = env.getProperty("mcp.auth.base-url", "");
        String appsEnabled = env.getProperty("mcp.apps.integration-enabled", "false");
        String appsBaseUrl = env.getProperty("mcp.apps.base-url", "");
        String importDir = env.getProperty("mcp.import.apps-dir", "");

        log.info(
            "MCP runtime config: MCP_HTTP_SSE_MODE={}, MCP_STDIO_MODE={}, mcp.auth.integration-enabled={}, mcp.auth.base-url={}, mcp.apps.integration-enabled={}, mcp.apps.base-url={}, mcp.import.apps-dir={}",
            nullToEmpty(httpSseMode),
            nullToEmpty(stdioMode),
            authEnabled,
            authBaseUrl,
            appsEnabled,
            appsBaseUrl,
            importDir
        );
    }

    private static String getenv(String key) {
        return System.getenv(key);
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}


