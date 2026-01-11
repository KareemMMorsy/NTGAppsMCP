package com.ntg.appsbroker.infrastructure.context;

import com.ntg.appsbroker.ports.ContextProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Infrastructure: User context provider (default-deny, allowlist-based).
 */
@Component
public class UserContextProvider implements ContextProvider {
    @Override
    public String name() {
        return "user";
    }
    
    @Override
    public Map<String, Object> provide(Map<String, Object> requestParameters, Set<String> allowlist) {
        // Default-deny: only return explicitly allowlisted user context
        Map<String, Object> context = new HashMap<>();
        
        if (allowlist.contains("user.id")) {
            context.put("user_id", requestParameters.getOrDefault("user_id", null));
        }
        if (allowlist.contains("user.tenant_id")) {
            context.put("tenant_id", requestParameters.getOrDefault("tenant_id", null));
        }
        if (allowlist.contains("user.roles")) {
            context.put("roles", requestParameters.getOrDefault("roles", new String[0]));
        }
        
        return context;
    }
}

