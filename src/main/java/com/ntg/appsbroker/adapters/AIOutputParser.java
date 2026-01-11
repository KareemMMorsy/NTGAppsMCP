package com.ntg.appsbroker.adapters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adapter: Validates and parses AI output against strict schemas.
 */
@Component
public class AIOutputParser {
    private static final Logger log = LoggerFactory.getLogger(AIOutputParser.class);
    private final ObjectMapper objectMapper;
    
    public AIOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public Map<String, Object> parseAndValidate(String aiOutput, Map<String, Object> expectedSchema) {
        try {
            // Parse JSON output
            Map<String, Object> parsed = objectMapper.readValue(aiOutput, new TypeReference<Map<String, Object>>() {});
            
            // Basic validation (can be extended)
            if (expectedSchema != null) {
                validateAgainstSchema(parsed, expectedSchema);
            }
            
            return parsed;
        } catch (Exception e) {
            log.error("Failed to parse AI output", e);
            throw new RuntimeException("Invalid AI output format", e);
        }
    }
    
    private void validateAgainstSchema(Map<String, Object> parsed, Map<String, Object> schema) {
        // Basic schema validation - can be extended with JSON Schema library
        // For now, just ensure required fields exist
        Object required = schema.get("required");
        if (required instanceof java.util.List<?> requiredFields) {
            for (Object fieldObj : requiredFields) {
                if (!(fieldObj instanceof String field) || field.isBlank()) {
                    continue;
                }
                if (!parsed.containsKey(field)) {
                    throw new RuntimeException("Missing required field: " + field);
                }
            }
        }
    }
}

