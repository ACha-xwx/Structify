package com.feng.dsagent.knowledge.vo;

/** Ranked knowledge hit for search and fragment evidence binding. */
public record KnowledgeSearchResultVO(
    String id,
    String chapterId,
    String title,
    String content,
    String source,
    String pageLabel,
    String licenseScope,
    double score
) {
}
