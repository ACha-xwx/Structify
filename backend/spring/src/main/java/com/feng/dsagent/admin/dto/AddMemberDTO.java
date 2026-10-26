package com.feng.dsagent.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AddMemberDTO(@NotBlank String identifier, @NotBlank String roleInClass) {}
