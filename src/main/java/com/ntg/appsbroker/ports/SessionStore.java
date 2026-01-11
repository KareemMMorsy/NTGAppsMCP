package com.ntg.appsbroker.ports;

/**
 * Port: Session store interface for managing session tokens.
 */
public interface SessionStore {
    void setToken(String clientId, String token);
    String getToken(String clientId);
    void clearToken(String clientId);
}

