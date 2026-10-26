package com.feng.dsagent.modelconfig.dto;

import com.feng.dsagent.modelconfig.domain.ModelConfigGenerationControls;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateModelConfigDTO(
    @NotBlank @Size(max = 128) String provider,
    @NotBlank @Size(max = 2048) String baseUrl,
    @NotBlank @Size(max = 512) String model,
    @Size(max = 4096) String apiKey,
    @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
    @Min(1) @Max(ModelConfigGenerationControls.MAX_OUTPUT_TOKENS) Integer maxOutputTokens,
    @Min(1000) @Max(120000) Long requestTimeoutMs,
    @Min(0) @Max(5) Integer retryCount,
    @Min(0) @Max(10000000) Long dailyTokenQuota,
    Boolean enabled
) {}
