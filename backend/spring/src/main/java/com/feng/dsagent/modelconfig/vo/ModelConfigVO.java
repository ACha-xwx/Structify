package com.feng.dsagent.modelconfig.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ModelConfigVO(
    String provider,
    String baseUrl,
    String model,
    boolean apiKeyConfigured,
    double temperature,
    int maxOutputTokens,
    long requestTimeoutMs,
    int retryCount,
    long dailyTokenQuota,
    boolean enabled,
    @JsonInclude(JsonInclude.Include.NON_NULL) String lastConnectionTestStatus,
    @JsonInclude(JsonInclude.Include.NON_NULL) java.time.Instant lastConnectionTestedAt,
    java.time.Instant updatedAt
) {}
