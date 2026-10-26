package com.feng.dsagent.aiquota.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.feng.dsagent.aiquota.domain.AiQuotaReservationStatus;
import com.feng.dsagent.aiquota.domain.AiQuotaUsageSource;
public record AiQuotaReservationEntity(
    String id,
    long userId,
    LocalDate quotaDate,
    String requestId,
    AiQuotaReservationStatus status,
    long estimatedTokens,
    Long actualTokens,
    Instant createdAt,
    Instant expiresAt,
    Instant completedAt,
    String failureCode,
    AiQuotaUsageSource usageSource
) {
}
