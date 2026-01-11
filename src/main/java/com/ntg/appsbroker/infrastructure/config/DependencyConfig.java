package com.ntg.appsbroker.infrastructure.config;

import com.ntg.appsbroker.ports.*;
import com.ntg.appsbroker.infrastructure.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Dependency injection configuration.
 */
@Configuration
public class DependencyConfig {
    
    @Bean
    public Map<String, ContextProvider> contextProviders(
        UserContextProvider userContextProvider,
        SystemContextProvider systemContextProvider,
        FileContextProvider fileContextProvider
    ) {
        Map<String, ContextProvider> providers = new HashMap<>();
        providers.put("user", userContextProvider);
        providers.put("system", systemContextProvider);
        providers.put("files", fileContextProvider);
        return providers;
    }
    
    // AuthService and AppsService are already @Service, so Spring will auto-wire them
    // This config just ensures they're available as beans
}

