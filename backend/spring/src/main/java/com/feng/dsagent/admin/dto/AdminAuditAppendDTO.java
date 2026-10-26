package com.feng.dsagent.admin.dto;

public record AdminAuditAppendDTO(
    long actorUserId,
    String action,
    String targetType,
    String targetId,
    String result,
    String requestId,
    String beforeSummary,
    String afterSummary
) {
}
