package com.feng.dsagent.admin.dto;

public record SettingsDTO(
    Double minimumScore,
    Integer searchLimit,
    Double titleWeightLong,
    Double titleWeightShort
) {}
