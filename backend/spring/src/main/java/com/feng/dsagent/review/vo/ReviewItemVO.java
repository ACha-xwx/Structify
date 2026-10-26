package com.feng.dsagent.review.vo;

import java.time.Instant;

public record ReviewItemVO(
    String type,
    String id,
    String title,
    String status,
    String chapterId,
    String versionLabel,
    boolean sourceComplete,
    Instant updatedAt
) {
}
