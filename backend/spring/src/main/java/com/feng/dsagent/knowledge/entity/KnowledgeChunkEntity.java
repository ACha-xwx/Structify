package com.feng.dsagent.knowledge.entity;

public record KnowledgeChunkEntity(
    String id,
    String chapterId,
    String title,
    String content,
    String source,
    String pageLabel,
    String licenseScope
) {
}
