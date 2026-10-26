package com.feng.dsagent.admin.vo;

import java.time.Instant;

public record BackgroundTaskVO(
    long id,
    String taskType,
    String status,
    Instant createdAt,
    Instant startedAt,
    Instant deadlineAt,
    Instant heartbeatAt,
    Instant finishedAt,
    String failureCode,
    String failureReason,
    Integer resultCount,
    int retryCount,
    int maxAttempts,
    Instant cancelRequestedAt,
    Long requestedByUserId,
    String requestId
) {
}
