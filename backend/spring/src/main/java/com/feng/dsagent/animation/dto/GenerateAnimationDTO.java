package com.feng.dsagent.animation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateAnimationDTO(
    @NotBlank @Size(max = 2000) String prompt,
    @Size(max = 16) String preferredType,
    @Size(max = 64) String chapterId
) {}
