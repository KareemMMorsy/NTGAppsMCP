package com.ntg.appsbroker.ports;

import java.util.Map;

/**
 * Port: AI Gateway interface for provider-agnostic AI interactions.
 */
public interface AIGateway {
    String generate(String prompt, String model);
    Map<String, Object> generateStructured(String prompt, String model, Map<String, Object> schema);
}

