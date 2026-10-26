package com.feng.dsagent.studyroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProgressDTO(@NotBlank @Size(max = 64) String stepId) {}
