package com.feng.dsagent.compiler;

import java.util.List;

/** Payload of {@code GET /api/v1/code/samples}: the samples the player may offer, grouped by lesson. */
public record ClassroomCodeSamplesResponse(List<ClassroomCodeLesson> lessons, int sampleCount) {

    public ClassroomCodeSamplesResponse {
        lessons = lessons == null ? List.of() : List.copyOf(lessons);
    }
}
