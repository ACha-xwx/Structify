package com.feng.dsagent.admin.vo;

import java.time.Instant;

public record AdminAuditEventVO(
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
