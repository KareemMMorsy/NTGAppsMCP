package com.ntg.appsbroker.infrastructure.apps;

import com.ntg.appsbroker.infrastructure.context.UpstreamBaseUrlContext;
import com.ntg.appsbroker.ports.AppsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.http.client.MultipartBodyBuilder;
import reactor.util.retry.Retry;
import com.ntg.appsbroker.infrastructure.util.BaseUrlUtil;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Infrastructure: HTTP implementation of AppsService.
 */
@Service
public class HttpAppsService implements AppsService {
    private static final Logger log = LoggerFactory.getLogger(HttpAppsService.class);
    private static final long DEFAULT_TIME_OFFSET_MS = 7200000L; // 2 hours in milliseconds
    
    private final WebClient webClient;
    private final String baseUrl;
    private final UpstreamBaseUrlContext upstreamBaseUrlContext;
    
    public HttpAppsService(
        @Value("${mcp.apps.base-url:http://localhost:7070/Smart2Go}") String baseUrl,
        UpstreamBaseUrlContext upstreamBaseUrlContext
    ) {
        this.baseUrl = BaseUrlUtil.normalize(baseUrl);
        this.upstreamBaseUrlContext = upstreamBaseUrlContext;
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
        String overrideBaseUrl = upstreamBaseUrlContext.getAppsBaseUrlOrNull();
        String effectiveBaseUrl = overrideBaseUrl != null ? overrideBaseUrl : baseUrl;
        WebClient client = webClient.mutate().baseUrl(effectiveBaseUrl).build();
        
        log.info("Calling saveApp API: {}", effectiveBaseUrl + "/rest/Apps/saveApp");
        log.debug("App spec: {}, sessionToken: {}", spec, sessionToken != null ? "***" : "null");
        
        try {
            var response = client.post()
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
        String overrideBaseUrl = upstreamBaseUrlContext.getAppsBaseUrlOrNull();
        String effectiveBaseUrl = overrideBaseUrl != null ? overrideBaseUrl : baseUrl;
        WebClient client = webClient.mutate().baseUrl(effectiveBaseUrl).build();

        log.info("Calling uploadFile API: {}", effectiveBaseUrl + "/rest/importExport/uploadFile");
        log.debug("Uploading import file: {}, sessionToken: {}", file != null ? file.toString() : "null", sessionToken != null ? "***" : "null");

        try {
            if (file == null) {
                return new AppsResponse(400, Map.of("error", "file is required"));
            }
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                return new AppsResponse(404, Map.of("error", "File not found: " + file));
            }

            // Match Python script behavior: enforce .NTGapps extension only (case-insensitive).
            String filename = file.getFileName() != null ? file.getFileName().toString() : "";
            if (!filename.toLowerCase().endsWith(".ntgapps")) {
                return new AppsResponse(400, Map.of("error", "Only .NTGapps files are supported.", "file", filename));
            }

            long fileSize = Files.size(file);
            if (fileSize <= 0) {
                return new AppsResponse(400, Map.of("error", "File is empty", "file", filename));
            }

            // Match Python script: read fully into memory and upload as raw bytes (octet-stream) inside multipart.
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length != fileSize) {
                return new AppsResponse(400, Map.of(
                    "error", "File read incomplete",
                    "expectedBytes", fileSize,
                    "actualBytes", bytes.length,
                    "file", filename
                ));
            }

            ByteArrayResource fileResource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", fileResource)
                .filename(filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

            var response = client.post()
                .uri("/rest/importExport/uploadFile")
                .headers(h -> applyUploadHeadersWithoutTimeOffset(h, sessionToken))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Object.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                    .filter(t -> t instanceof java.net.ConnectException || t instanceof WebClientRequestException))
                // Match Python script behavior: allow large files (10 minutes).
                .timeout(Duration.ofSeconds(600))
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
        String overrideBaseUrl = upstreamBaseUrlContext.getAppsBaseUrlOrNull();
        String effectiveBaseUrl = overrideBaseUrl != null ? overrideBaseUrl : baseUrl;
        WebClient client = webClient.mutate().baseUrl(effectiveBaseUrl).build();

        log.info("Calling validateAppIdentifier API: {}", effectiveBaseUrl + "/rest/importExport/validateAppIdentifier");

        try {
            var response = client.post()
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
        String overrideBaseUrl = upstreamBaseUrlContext.getAppsBaseUrlOrNull();
        String effectiveBaseUrl = overrideBaseUrl != null ? overrideBaseUrl : baseUrl;
        WebClient client = webClient.mutate().baseUrl(effectiveBaseUrl).build();

        log.info("Calling importApp API: {}", effectiveBaseUrl + "/rest/importExport/importApp");

        try {
            var response = client.post()
                .uri("/rest/importExport/importApp")
                .headers(h -> applyJsonHeadersWithTimeOffset(h, sessionToken))
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

    /**
     * Upload headers without TimeOffset:
     * - SessionToken (we also send sessiontoken + X-Session-Token)
     * - ngsw-bypass=true
     */
    private static void applyUploadHeadersWithoutTimeOffset(HttpHeaders headers, String sessionToken) {
        applySessionHeaders(headers, sessionToken);
        headers.set("ngsw-bypass", "true");
    }

    /**
     * JSON headers with TimeOffset:
     * - SessionToken (we also send sessiontoken + X-Session-Token)
     * - TimeOffset (default: 7200000 ms = 2 hours)
     */
    private static void applyJsonHeadersWithTimeOffset(HttpHeaders headers, String sessionToken) {
        applySessionHeaders(headers, sessionToken);
        headers.set("TimeOffset", String.valueOf(DEFAULT_TIME_OFFSET_MS));
    }

}

