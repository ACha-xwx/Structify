package com.feng.dsagent.studyroom.dto;

import com.feng.dsagent.studyroom.dto.StudyroomFollowUpContextDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FollowUpDTO(
    @NotBlank @Size(max = 4_000) String question,
    @Valid StudyroomFollowUpContextDTO context,
    @NotBlank @Size(max=36) String runId,
    @NotBlank @Size(max=36) String fragmentId,
    @NotNull @Min(1) Integer contentRevision
) {}
