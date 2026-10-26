package com.feng.dsagent.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record TestSearchDTO(
    @NotBlank String query,
    String chapterId,
    String audience,
    Double minimumScore,
    Integer searchLimit,
    Double titleWeightLong,
    Double titleWeightShort
) {}
