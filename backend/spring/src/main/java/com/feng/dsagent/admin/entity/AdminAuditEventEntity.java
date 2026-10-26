package com.feng.dsagent.admin.entity;

import java.time.Instant;

public record AdminAuditEventEntity(
    long id,
    long actorUserId,
    String action,
    String targetType,
    String targetId,
    String result,
    String requestId,
    String beforeSummary,
    String afterSummary,
    Instant createdAt
) {
}
