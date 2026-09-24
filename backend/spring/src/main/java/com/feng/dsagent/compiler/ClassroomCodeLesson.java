package com.feng.dsagent.compiler;

import java.util.List;

/**
 * The runnable samples that belong to one courseware lesson. The key is the lesson code the deck and
 * the classroom both use ({@code 03-01}), not the textbook row's opaque id.
 */
public record ClassroomCodeLesson(
    String coursewareKey,
    String lessonTitle,
    String chapterId,
    List<ClassroomCodeSample> samples
) {

    public ClassroomCodeLesson {
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
