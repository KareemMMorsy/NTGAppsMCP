package com.ntg.appsbroker.ports;

import java.util.Map;
import java.util.Set;

/**
 * Port: Context provider interface for injecting context into requests.
 */
public interface ContextProvider {
    String name();
    Map<String, Object> provide(Map<String, Object> requestParameters, Set<String> allowlist);
}

