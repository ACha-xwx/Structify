package com.feng.dsagent.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SlideDTO(@PositiveOrZero int stepIndex, @NotBlank @Size(max = 160) String slideId) {}
