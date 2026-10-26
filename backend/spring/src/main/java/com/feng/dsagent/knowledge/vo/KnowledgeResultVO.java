package com.feng.dsagent.knowledge.vo;

public record KnowledgeResultVO(
    String id,
    String chapterId,
    String title,
    String lessonNumber,
    String kind,
    String source,
    String pageLabel,
    String sourceLabel,
    String locationLabel,
    String reviewStatus,
    String publicationStatus,
    String excerpt,
    double score
) {}
