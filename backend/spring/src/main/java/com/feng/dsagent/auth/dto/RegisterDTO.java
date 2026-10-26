package com.feng.dsagent.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDTO(
    @Email @NotBlank String email,
    @Size(max = 32) String username,
    @NotBlank @Size(min = 6, max = 6) String code,
    @NotBlank @Size(min = 8, max = 128) String password
) {}
