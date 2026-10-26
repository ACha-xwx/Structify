package com.feng.dsagent.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserStatusDTO(
    @NotBlank @Size(max = 16) String status,
    @Size(max = 500) String reason
) {}
