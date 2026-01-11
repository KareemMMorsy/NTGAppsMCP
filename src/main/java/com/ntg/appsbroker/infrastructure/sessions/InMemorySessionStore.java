package com.ntg.appsbroker.infrastructure.sessions;

import com.ntg.appsbroker.ports.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Infrastructure: In-memory implementation of SessionStore.
 */
@Component
public class InMemorySessionStore implements SessionStore {
    private static final Logger log = LoggerFactory.getLogger(InMemorySessionStore.class);
    private final ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();

    private final String defaultClientId;
    private final String defaultSessionToken;
    private final String httpAuthTokenFallback;

    public InMemorySessionStore(
        @Value("${mcp.default-client-id:}") String defaultClientId,
        @Value("${mcp.default-session-token:}") String defaultSessionToken,
        @Value("${mcp.http.auth-token:}") String httpAuthTokenFallback
    ) {
        this.defaultClientId = defaultClientId;
        this.defaultSessionToken = defaultSessionToken;
        this.httpAuthTokenFallback = httpAuthTokenFallback;
    }

    @PostConstruct
    void preloadDefaultSession() {
        // Prefer MCP_DEFAULT_SESSION_TOKEN, fall back to MCP_HTTP_AUTH_TOKEN
        String token = (defaultSessionToken != null && !defaultSessionToken.isBlank())
            ? defaultSessionToken
            : httpAuthTokenFallback;

        if (token == null || token.isBlank()) {
            return;
        }

        if (defaultClientId != null && !defaultClientId.isBlank()) {
            sessions.put(defaultClientId, token);
            log.info("Preloaded default session token for clientId={}", defaultClientId);
        } else {
            // No default clientId configured; we'll still be able to fall back during getToken().
            log.info("Default session token is configured (no default clientId). It will be used as a fallback when needed.");
        }
    }

    @Override
    public void setToken(String clientId, String token) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId cannot be null or blank");
        }
        sessions.put(clientId, token);
        log.debug("Session stored for clientId: {}", clientId);
    }
    
    @Override
    public String getToken(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        String token = sessions.get(clientId);
        if (token != null && !token.isBlank()) {
            log.debug("Session retrieved for clientId={}: found", clientId);
            return token;
        }

        // Fallback: allow operation without explicit login when a default token exists
        String fallback = (defaultSessionToken != null && !defaultSessionToken.isBlank())
            ? defaultSessionToken
            : httpAuthTokenFallback;
        if (fallback != null && !fallback.isBlank()) {
            // Cache it for this clientId so subsequent calls are consistent
            sessions.put(clientId, fallback);
            log.info("No stored session for clientId={}; using configured default token fallback", clientId);
            return fallback;
        }

        log.debug("Session retrieved for clientId={}: not found", clientId);
        return null;
    }
    
    @Override
    public void clearToken(String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            sessions.remove(clientId);
            log.debug("Session cleared for clientId: {}", clientId);
        }
    }
}

