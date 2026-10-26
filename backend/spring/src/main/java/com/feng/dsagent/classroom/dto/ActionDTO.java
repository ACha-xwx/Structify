package com.feng.dsagent.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ActionDTO(@NotBlank String action, @Size(max = 4000) String content, @PositiveOrZero Integer expectedRevision) {}
