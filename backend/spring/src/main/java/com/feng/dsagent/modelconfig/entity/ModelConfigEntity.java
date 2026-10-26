package com.feng.dsagent.modelconfig.entity;

import java.time.Instant;

public record ModelConfigEntity(
    long id,
    String provider,
    String baseUrl,
    String model,
    String apiKeyCiphertext,
    double temperature,
    int maxOutputTokens,
    long requestTimeoutMs,
    int retryCount,
    long dailyTokenQuota,
    boolean enabled,
    String lastConnectionTestStatus,
    Instant lastConnectionTestedAt,
    Instant updatedAt
) {
}
