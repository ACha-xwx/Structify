package com.feng.dsagent.compiler.vo;

import java.util.List;

import com.feng.dsagent.compiler.domain.ClassroomCodeLesson;
/** Payload of {@code GET /api/v1/code/samples}: the samples the player may offer, grouped by lesson. */
public record ClassroomCodeSamplesVO(List<ClassroomCodeLesson> lessons, int sampleCount) {

    public ClassroomCodeSamplesVO {
        lessons = lessons == null ? List.of() : List.copyOf(lessons);
    }
}
