package com.feng.dsagent.modelconfig;

public record ModelConfigGenerationControls(
    double temperature,
    int maxOutputTokens,
    long requestTimeoutMs,
    int retryCount,
    long dailyTokenQuota,
    boolean enabled
) {

    public static final double DEFAULT_TEMPERATURE = 0.2;
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 1_024;
    public static final int MAX_OUTPUT_TOKENS = 65_536;
    public static final long DEFAULT_REQUEST_TIMEOUT_MS = 45_000;
    public static final int DEFAULT_RETRY_COUNT = 0;
    public static final long DEFAULT_DAILY_TOKEN_QUOTA = 0;

    public static ModelConfigGenerationControls defaults() {
        return new ModelConfigGenerationControls(
            DEFAULT_TEMPERATURE,
            DEFAULT_MAX_OUTPUT_TOKENS,
            DEFAULT_REQUEST_TIMEOUT_MS,
            DEFAULT_RETRY_COUNT,
            DEFAULT_DAILY_TOKEN_QUOTA,
            false
        );
    }

    public static ModelConfigGenerationControls from(ModelConfigRepository.StoredModelConfig stored) {
        return new ModelConfigGenerationControls(
            stored.temperature(),
            stored.maxOutputTokens(),
            stored.requestTimeoutMs(),
            stored.retryCount(),
            stored.dailyTokenQuota(),
            stored.enabled()
        );
    }
}
