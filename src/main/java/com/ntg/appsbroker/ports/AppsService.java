package com.ntg.appsbroker.ports;

import java.nio.file.Path;
import java.util.Map;

/**
 * Port: Apps service interface for external app management APIs.
 */
public interface AppsService {
    AppsResponse saveApp(Map<String, Object> spec, String sessionToken);

    /**
     * Upload an exported app package for import (multipart/form-data; part name: file).
     */
    AppsResponse uploadImportFile(Path file, String sessionToken);

    /**
     * Validate whether an imported app identifier already exists / can be merged.
     */
    AppsResponse validateAppIdentifier(Map<String, Object> payload, String sessionToken);

    /**
     * Perform the actual app import using the payload returned from uploadFile (plus override fields).
     */
    AppsResponse importApp(Map<String, Object> payload, String sessionToken);
    
    record AppsResponse(int statusCode, Object body) {}
}

