package com.ntg.appsbroker.infrastructure.context;

import com.ntg.appsbroker.ports.ContextProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Infrastructure: System context provider (default-deny, allowlist-based).
 */
@Component
public class SystemContextProvider implements ContextProvider {
    @Override
    public String name() {
        return "system";
    }
    
    @Override
    public Map<String, Object> provide(Map<String, Object> requestParameters, Set<String> allowlist) {
        // Default-deny: only return explicitly allowlisted system context
        Map<String, Object> context = new HashMap<>();
        
        if (allowlist.contains("system.timezone")) {
            context.put("timezone", java.util.TimeZone.getDefault().getID());
        }
        if (allowlist.contains("system.locale")) {
            context.put("locale", java.util.Locale.getDefault().toString());
        }
        
        return context;
    }
}

