package com.feng.dsagent.animation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ObservationDTO(@NotBlank @Size(max = 2000) String observation) {}
