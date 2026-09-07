package com.feng.dsagent.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Performs a bounded, non-redirecting health probe without exposing upstream details. */
@Component
final class HttpSandboxConnectionTester implements SandboxConnectionTester {

    private static final int MAXIMUM_RESPONSE_BYTES = 65_536;

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    private final ObjectMapper objectMapper;

    @Autowired
    HttpSandboxConnectionTester(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    HttpSandboxConnectionTester() {
        this(new ObjectMapper());
    }

    @Override
    public SandboxConnectionResult test(SandboxRuntimeSettings settings) {
        if (settings == null || settings.provider() == null || settings.baseUrl() == null
            || settings.baseUrl().isBlank() || !settings.enabled()) {
            return new SandboxConnectionResult(false, "CONNECTION_FAILED");
        }
        URI endpoint;
        try {
            String base = settings.baseUrl().strip().replaceAll("/+$", "");
            String path = settings.provider() == SandboxProvider.JUDGE0 ? "/about" : "/runtimes";
            endpoint = URI.create(base + path);
        } catch (IllegalArgumentException error) {
            return new SandboxConnectionResult(false, "CONNECTION_FAILED");
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET()
            .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    return new SandboxConnectionResult(false, "REDIRECT_REJECTED");
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return compatibleResponse(settings.provider(), body)
                        ? new SandboxConnectionResult(true, "CONNECTION_OK")
                        : new SandboxConnectionResult(false, "CONNECTION_RESPONSE_INVALID");
                }
                return new SandboxConnectionResult(false, "UPSTREAM_REJECTED");
            }
        } catch (java.net.http.HttpTimeoutException error) {
            return new SandboxConnectionResult(false, "CONNECTION_TIMEOUT");
        } catch (IOException error) {
            return new SandboxConnectionResult(false, "CONNECTION_FAILED");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new SandboxConnectionResult(false, "CONNECTION_INTERRUPTED");
        }
    }

    private boolean compatibleResponse(SandboxProvider provider, InputStream body) {
        try {
            byte[] bytes = body.readNBytes(MAXIMUM_RESPONSE_BYTES + 1);
            if (bytes.length > MAXIMUM_RESPONSE_BYTES) {
                return false;
            }
            JsonNode root = objectMapper.readTree(bytes);
            if (provider == SandboxProvider.JUDGE0) {
                JsonNode version = root.path("version");
                return root.isObject() && version.isTextual() && !version.asText().isBlank();
            }
            if (!root.isArray() || root.isEmpty()) {
                return false;
            }
            for (JsonNode runtime : root) {
                if (runtime.isObject()
                    && ((runtime.path("language").isTextual() && !runtime.path("language").asText().isBlank())
                        || (runtime.path("alias").isTextual() && !runtime.path("alias").asText().isBlank()))) {
                    return true;
                }
            }
            return false;
        } catch (Exception error) {
            return false;
        }
    }
}
