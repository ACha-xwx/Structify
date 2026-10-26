package com.feng.dsagent.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BookmarkDTO(@NotBlank @Size(max = 64) String sessionId, @PositiveOrZero int stepIndex) {}
