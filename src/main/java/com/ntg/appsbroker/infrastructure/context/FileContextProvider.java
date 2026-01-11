package com.ntg.appsbroker.infrastructure.context;

import com.ntg.appsbroker.ports.ContextProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Infrastructure: File context provider (default-deny, allowlist-based).
 */
@Component
public class FileContextProvider implements ContextProvider {
    @Value("${mcp.file.allowed-roots:}")
    private String allowedRootsConfig;
    
    @Override
    public String name() {
        return "files";
    }
    
    @Override
    public Map<String, Object> provide(Map<String, Object> requestParameters, Set<String> allowlist) {
        // Default-deny: only return explicitly allowlisted files
        Map<String, Object> context = new HashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requested = (List<Map<String, Object>>) requestParameters.get("files");
        if (requested == null) {
            return context;
        }
        
        List<Path> allowedRoots = parseAllowedRoots();
        
        for (Map<String, Object> item : requested) {
            String pathStr = (String) item.get("path");
            if (pathStr == null || pathStr.isBlank()) {
                continue;
            }
            
            Path path = Paths.get(pathStr).normalize();
            String normalized = path.toString();
            
            if (!allowlist.contains(normalized)) {
                continue; // Default-deny
            }
            
            // If content is provided inline, use it
            Object content = item.get("content");
            if (content instanceof String) {
                files.add(Map.of(
                    "path", normalized,
                    "content", content,
                    "source", "inline"
                ));
                continue;
            }
            
            // Disk read (must be under allowed roots)
            if (allowedRoots.isEmpty()) {
                continue; // Disk access disabled
            }
            
            if (!isUnderAllowedRoots(path, allowedRoots)) {
                continue; // Outside allowed roots
            }
            
            try {
                String fileContent = Files.readString(path);
                files.add(Map.of(
                    "path", normalized,
                    "content", fileContent,
                    "source", "disk"
                ));
            } catch (Exception e) {
                // Skip files that can't be read
            }
        }
        
        context.put("files", files);
        return context;
    }
    
    private List<Path> parseAllowedRoots() {
        if (allowedRootsConfig == null || allowedRootsConfig.isBlank()) {
            return Collections.emptyList();
        }
        
        List<Path> roots = new ArrayList<>();
        for (String root : allowedRootsConfig.split(",")) {
            root = root.trim();
            if (!root.isBlank()) {
                roots.add(Paths.get(root).normalize().toAbsolutePath());
            }
        }
        return roots;
    }
    
    private boolean isUnderAllowedRoots(Path path, List<Path> allowedRoots) {
        Path absolute = path.toAbsolutePath().normalize();
        return allowedRoots.stream()
            .anyMatch(root -> absolute.startsWith(root));
    }
}

