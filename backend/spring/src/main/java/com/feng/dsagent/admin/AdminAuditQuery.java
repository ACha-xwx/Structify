package com.feng.dsagent.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

public record AdminAuditQuery(
    int page,
    int size,
    Long actorUserId,
    String action,
    String targetType,
    String targetId,
    Instant from,
    Instant to
) {
}
