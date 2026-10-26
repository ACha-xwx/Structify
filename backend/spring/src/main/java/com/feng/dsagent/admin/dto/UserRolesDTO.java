package com.feng.dsagent.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UserRolesDTO(
    @NotEmpty @Size(max = 3) Set<@NotBlank @Size(max = 32) String> roles
) {}
