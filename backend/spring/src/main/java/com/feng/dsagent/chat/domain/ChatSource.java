package com.feng.dsagent.chat.domain;

public record ChatSource(
    String id,
    String chapterId,
    String title,
    String content,
    String source,
    String pageLabel,
    double score,
    String evidenceHash
) {
}
