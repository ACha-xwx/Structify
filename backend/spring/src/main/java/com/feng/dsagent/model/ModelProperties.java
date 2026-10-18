package com.feng.dsagent.model;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.model")
public record ModelProperties(
    String provider,
    String apiKey,
    String baseUrl,
    String name,
    Duration timeout,
    Duration streamIdleTimeout,
    int maximumResponseBytes,
    Boolean disableThinking
) {
    // SSE envelopes consume bytes as well as generated text; 64K reasoning needs headroom for both.
    public static final int DEFAULT_MAX_RESPONSE_BYTES = 32 * 1_024 * 1_024;
}
