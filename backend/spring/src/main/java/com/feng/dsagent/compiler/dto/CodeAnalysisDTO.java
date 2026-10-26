package com.feng.dsagent.compiler.dto;

public record CodeAnalysisDTO(
    String language,
    String code,
    String stdin,
    String stdout,
    String stderr,
    String status,
    String chapterId,
    String runId
) {
}
