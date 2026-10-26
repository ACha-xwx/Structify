package com.feng.dsagent.compiler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSandboxConfigDTO(
    @NotBlank @Size(max = 16) String provider,
    @NotBlank @Size(max = 2048) String baseUrl,
    @NotNull Boolean enabled
) {}
