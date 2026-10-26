package com.feng.dsagent.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateClassDTO(@NotBlank String name, String term) {}
