package com.feng.dsagent.studyroom.draft.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RepairFixDTO(
        @NotBlank @Size(max = 64) String code,
        @Size(max = 80) String unitId) {}
