package com.feng.dsagent.compiler.entity;

public record CodeRunSnapshotEntity(
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
