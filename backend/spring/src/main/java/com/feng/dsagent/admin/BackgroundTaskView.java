package com.feng.dsagent.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

public record BackgroundTaskView(
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
