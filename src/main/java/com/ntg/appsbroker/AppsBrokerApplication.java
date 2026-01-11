package com.ntg.appsbroker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;

/**
 * Main Spring Boot application.
 * 
 * When MCP_STDIO_MODE=true or mcp.stdio.enabled=true, runs as stdio server.
 * Otherwise, can run as web application (if web dependencies are enabled).
 */
@SpringBootApplication
public class AppsBrokerApplication {
    public static void main(String[] args) {
        String stdioMode = System.getenv("MCP_STDIO_MODE");
        String httpSseMode = System.getenv("MCP_HTTP_SSE_MODE");

        WebApplicationType webApplicationType = WebApplicationType.NONE;
        
        if ("true".equalsIgnoreCase(stdioMode)) {
            System.setProperty("mcp.stdio.enabled", "true");
            System.setProperty("spring.main.web-application-type", "none");
            webApplicationType = WebApplicationType.NONE;
        } else if ("true".equalsIgnoreCase(httpSseMode)) {
            // Fly/remote HTTP mode
            System.setProperty("spring.main.web-application-type", "servlet");
            webApplicationType = WebApplicationType.SERVLET;
        }
        
        SpringApplication app = new SpringApplication(AppsBrokerApplication.class);
        app.setWebApplicationType(webApplicationType);
        app.run(args);
    }
}

