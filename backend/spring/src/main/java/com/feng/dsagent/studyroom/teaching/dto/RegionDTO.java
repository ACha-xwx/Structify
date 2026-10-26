package com.feng.dsagent.studyroom.teaching.dto;

import com.feng.dsagent.studyroom.teaching.domain.LectureRegion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RegionDTO(@Min(1) int contentRevision, @NotNull LectureRegion region) {}
