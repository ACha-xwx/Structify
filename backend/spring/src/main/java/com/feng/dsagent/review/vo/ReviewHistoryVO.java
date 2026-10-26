package com.feng.dsagent.review.vo;

import java.time.Instant;

public record ReviewHistoryVO(
    long id,
    String previousStatus,
    String nextStatus,
    String note,
    Long reviewerUserId,
    String requestId,
    Instant createdAt
) {
}
