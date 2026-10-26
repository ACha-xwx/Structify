package com.feng.dsagent.review.entity;

import java.time.Instant;

public record ReviewHistoryEntity(
    long id,
    String previousStatus,
    String nextStatus,
    String note,
    Long reviewerUserId,
    String requestId,
    Instant createdAt
) {
}
