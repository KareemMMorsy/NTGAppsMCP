package com.ntg.appsbroker.infrastructure.auth;

import com.ntg.appsbroker.ports.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import com.ntg.appsbroker.infrastructure.util.BaseUrlUtil;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Infrastructure: HTTP implementation of AuthService.
 */
@Service
public class HttpAuthService implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(HttpAuthService.class);
    private final WebClient webClient;
    private final String baseUrl;
    
    public HttpAuthService(
        @Value("${mcp.auth.base-url:http://localhost:7070/Smart2Go}") String baseUrl
    ) {
        this.baseUrl = BaseUrlUtil.normalize(baseUrl);
        this.webClient = WebClient.builder()
            .baseUrl(this.baseUrl)
            .defaultHeader("SessionToken", "NTG")
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
    
    @Override
    public LoginResult login(String username, String password, String companyname) {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> loginUserInfo = new HashMap<>();
        loginUserInfo.put("loginUserName", username);
        loginUserInfo.put("companyName", companyname);
        payload.put("LoginUserInfo", loginUserInfo);
        payload.put("Password", password);
        
        log.info("Calling login API: {}", baseUrl + "/rest/MainFunciton/login");
        
        try {
            Map<String, Object> response = webClient.post()
                .uri("/rest/MainFunciton/login")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                    .filter(throwable -> throwable instanceof java.net.ConnectException))
                .timeout(Duration.ofSeconds(10))
                .block();
            
            log.info("Login API response received");
            
            // Extract UserSessionToken from response
            String token = extractSessionToken(response);
            if (token == null) {
                throw new RuntimeException("No session token in response");
            }
            
            return new LoginResult(token, response);
        } catch (Exception e) {
            log.error("Login API call failed", e);
            throw new RuntimeException("Login failed: " + e.getMessage(), e);
        }
    }
    
    private String extractSessionToken(Map<String, Object> body) {
        if (body == null) return null;
        
        // Try UserSessionToken first (primary)
        Object token = body.get("UserSessionToken");
        if (token instanceof String && !((String) token).isBlank()) {
            return (String) token;
        }
        
        // Try userSessionToken (lowercase)
        token = body.get("userSessionToken");
        if (token instanceof String && !((String) token).isBlank()) {
            return (String) token;
        }
        
        // Fallback to other common fields
        for (String key : new String[]{"sessionToken", "SessionToken", "token"}) {
            token = body.get(key);
            if (token instanceof String && !((String) token).isBlank()) {
                return (String) token;
            }
        }
        
        return null;
    }
}

