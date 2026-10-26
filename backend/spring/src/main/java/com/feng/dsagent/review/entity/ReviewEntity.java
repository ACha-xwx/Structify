package com.feng.dsagent.review.entity;

import com.feng.dsagent.review.domain.ReviewType;
import java.time.Instant;

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
