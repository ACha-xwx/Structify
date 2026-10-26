package com.feng.dsagent.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewStatusDTO(
    @NotBlank @Size(max = 32) String status,
    @Size(max = 500) String note
) {}
