package com.feng.dsagent.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChunkDTO(
    String chapterId,
    @NotBlank String title,
    @NotBlank String content,
    String pageLabel,
    String reviewStatus,
    String licenseScope
) {}
