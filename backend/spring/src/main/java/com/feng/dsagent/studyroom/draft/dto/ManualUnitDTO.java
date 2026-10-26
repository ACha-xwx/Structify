package com.feng.dsagent.studyroom.draft.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ManualUnitDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,79}") String id,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 400) String objective) {}
