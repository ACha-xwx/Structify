package com.feng.dsagent.compiler;

public record CodeRunSnapshot(
    String id,
    String chapterId,
    String language,
    String code,
    String stdin,
    String stdout,
    String stderr,
    String status
) {
}
