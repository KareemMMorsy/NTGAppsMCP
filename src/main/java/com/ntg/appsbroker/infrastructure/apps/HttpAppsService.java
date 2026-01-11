package com.ntg.appsbroker.infrastructure.apps;

import com.ntg.appsbroker.ports.AppsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.http.client.MultipartBodyBuilder;
import reactor.util.retry.Retry;
import com.ntg.appsbroker.infrastructure.util.BaseUrlUtil;

import java.time.Duration;
import java.nio.file.Path;
import java.util.Map;

/**
 * Infrastructure: HTTP implementation of AppsService.
 */
@Service
public class HttpAppsService implements AppsService {
    private static final Logger log = LoggerFactory.getLogger(HttpAppsService.class);
    private final WebClient webClient;
    private final String baseUrl;
    private final boolean enabled;
    
    public HttpAppsService(
        @Value("${mcp.apps.base-url:http://localhost:7070/Smart2Go}") String baseUrl,
        @Value("${mcp.apps.integration-enabled:false}") boolean enabled
    ) {
        this.baseUrl = BaseUrlUtil.normalize(baseUrl);
        this.enabled = enabled;
        // Bump in-memory buffer in case uploadFile returns larger payloads (integrationRepositories etc.)
        var strategies = ExchangeStrategies.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();

        this.webClient = WebClient.builder()
            .baseUrl(this.baseUrl)
            .exchangeStrategies(strategies)
            .build();
    }
    
    @Override
    public AppsResponse saveApp(Map<String, Object> spec, String sessionToken) {
        if (!enabled) {
            log.warn("Apps integration disabled, returning dummy response");
            return new AppsResponse(200, Map.of("message", "dummy response"));
        }
        
        log.info("Calling saveApp API: {}", baseUrl + "/rest/Apps/saveApp");
        log.debug("App spec: {}, sessionToken: {}", spec, sessionToken != null ? "***" : "null");
        
        try {
            var response = webClient.post()
                .uri("/rest/Apps/saveApp")
                .headers(h -> applySessionHeaders(h, sessionToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(spec)
                .retrieve()
                .bodyToMono(Object.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                    .filter(throwable -> throwable instanceof java.net.ConnectException))
                .timeout(Duration.ofSeconds(10))
                .block();
            
            log.info("saveApp API response received: status=200");
            
            return new AppsResponse(200, response);
        } catch (WebClientResponseException e) {
            log.error("saveApp API call failed: status={}", e.getStatusCode(), e);
            return new AppsResponse(
                e.getStatusCode().value(),
                Map.of(
                    "error", e.getMessage(),
                    "response_body", e.getResponseBodyAsString(),
                    "status", e.getStatusCode().toString()
                )
            );
        } catch (Exception e) {
            log.error("saveApp API call failed", e);
            throw new RuntimeException("Failed to save app: " + e.getMessage(), e);
        }
    }

    @Override
    public AppsResponse uploadImportFile(Path file, String sessionToken) {
        if (!enabled) {
            log.warn("Apps integration disabled, cannot upload import file");
            return new AppsResponse(503, Map.of("error", "Apps integration disabled"));
        }

        log.info("Calling uploadFile API: {}", baseUrl + "/rest/importExport/uploadFile");
        log.debug("Uploading import file: {}, sessionToken: {}", file != null ? file.toString() : "null", sessionToken != null ? "***" : "null");

        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new FileSystemResource(file.toFile()));

            var response = webClient.post()
                .uri("/rest/importExport/uploadFile")
                .headers(h -> applySessionHeaders(h, sessionToken))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Object.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                    .filter(t -> t instanceof java.net.ConnectException || t instanceof WebClientRequestException))
                .timeout(Duration.ofSeconds(30))
                .block();

            return new AppsResponse(200, response);
        } catch (WebClientResponseException e) {
            log.error("uploadFile API call failed: status={}", e.getStatusCode(), e);
            return new AppsResponse(
                e.getStatusCode().value(),
                Map.of(
                    "error", e.getMessage(),
                    "response_body", e.getResponseBodyAsString(),
                    "status", e.getStatusCode().toString()
                )
            );
        } catch (Exception e) {
            log.error("uploadFile API call failed", e);
            throw new RuntimeException("Failed to upload import file: " + e.getMessage(), e);
        }
    }

    @Override
    public AppsResponse validateAppIdentifier(Map<String, Object> payload, String sessionToken) {
        if (!enabled) {
            log.warn("Apps integration disabled, cannot validate app identifier");
            return new AppsResponse(503, Map.of("error", "Apps integration disabled"));
        }

        log.info("Calling validateAppIdentifier API: {}", baseUrl + "/rest/importExport/validateAppIdentifier");

        try {
            var response = webClient.post()
                .uri("/rest/importExport/validateAppIdentifier")
                .headers(h -> applySessionHeaders(h, sessionToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Object.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                    .filter(t -> t instanceof java.net.ConnectException || t instanceof WebClientRequestException))
                .timeout(Duration.ofSeconds(15))
                .block();

            return new AppsResponse(200, response);
        } catch (WebClientResponseException e) {
            log.error("validateAppIdentifier API call failed: status={}", e.getStatusCode(), e);
            return new AppsResponse(
                e.getStatusCode().value(),
                Map.of(
                    "error", e.getMessage(),
                    "response_body", e.getResponseBodyAsString(),
                    "status", e.getStatusCode().toString()
                )
            );
        } catch (Exception e) {
            log.error("validateAppIdentifier API call failed", e);
            throw new RuntimeException("Failed to validate app identifier: " + e.getMessage(), e);
        }
    }

    @Override
    public AppsResponse importApp(Map<String, Object> payload, String sessionToken) {
        if (!enabled) {
            log.warn("Apps integration disabled, cannot import app");
            return new AppsResponse(503, Map.of("error", "Apps integration disabled"));
        }

        log.info("Calling importApp API: {}", baseUrl + "/rest/importExport/importApp");

        try {
            var response = webClient.post()
                .uri("/rest/importExport/importApp")
                .headers(h -> applySessionHeaders(h, sessionToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Object.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                    .filter(t -> t instanceof java.net.ConnectException || t instanceof WebClientRequestException))
                .timeout(Duration.ofSeconds(60))
                .block();

            return new AppsResponse(200, response);
        } catch (WebClientResponseException e) {
            log.error("importApp API call failed: status={}", e.getStatusCode(), e);
            return new AppsResponse(
                e.getStatusCode().value(),
                Map.of(
                    "error", e.getMessage(),
                    "response_body", e.getResponseBodyAsString(),
                    "status", e.getStatusCode().toString()
                )
            );
        } catch (Exception e) {
            log.error("importApp API call failed", e);
            throw new RuntimeException("Failed to import app: " + e.getMessage(), e);
        }
    }

    private static void applySessionHeaders(HttpHeaders headers, String sessionToken) {
        if (sessionToken == null) return;
        headers.set("SessionToken", sessionToken);
        headers.set("sessiontoken", sessionToken);
        headers.set("X-Session-Token", sessionToken);
    }
}

