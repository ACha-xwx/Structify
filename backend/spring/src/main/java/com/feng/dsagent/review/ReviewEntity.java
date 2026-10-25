package com.feng.dsagent.review;

import com.feng.dsagent.review.service.ReviewType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public record ReviewEntity(
    ReviewType type,
    String id,
    String title,
    String status,
    String chapterId,
    String versionLabel,
    String sourceName,
    String sourcePath,
    String parentId,
    String sourceType,
    String sourceRef,
    String contentHash,
    Instant updatedAt
) {
}
