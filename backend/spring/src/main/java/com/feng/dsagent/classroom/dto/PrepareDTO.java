package com.feng.dsagent.classroom.dto;

import jakarta.validation.constraints.NotBlank;

public record PrepareDTO(@NotBlank String lessonId) {}
