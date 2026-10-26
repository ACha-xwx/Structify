package com.feng.dsagent.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestCodeDTO(@Email @NotBlank String email, @NotBlank String purpose) {}
