package com.feng.dsagent.admin.dto;

public record PatchChunkDTO(
    String chapterId,
    String title,
    String content,
    String pageLabel,
    String reviewStatus,
    String licenseScope
) {}
