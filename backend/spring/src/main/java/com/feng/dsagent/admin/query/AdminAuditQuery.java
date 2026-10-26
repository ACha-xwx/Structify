package com.feng.dsagent.admin.query;

import com.feng.dsagent.common.query.PagedQuery;
import java.time.Instant;

public record AdminAuditQuery(
    int page,
    int size,
    Long actorUserId,
    String action,
    String targetType,
    String targetId,
    Instant from,
    Instant to
) implements PagedQuery {
}
