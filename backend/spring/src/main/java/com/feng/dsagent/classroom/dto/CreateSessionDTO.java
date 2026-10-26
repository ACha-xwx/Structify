package com.feng.dsagent.classroom.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionDTO(@NotBlank String scriptId) {}
