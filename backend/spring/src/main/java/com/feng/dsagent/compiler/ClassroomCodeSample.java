package com.feng.dsagent.compiler;

import java.util.List;

/**
 * A classroom code sample: the textbook listing kept verbatim, plus the small driver and input
 * samples that make it runnable during a lesson. Gradeable metadata ({@code sections}) mirrors the
 * section numbers found in the slide annotations so the player can pick the sample that matches the
 * page currently on screen.
 */
public record ClassroomCodeSample(
    String id,
    String title,
    String sourceFile,
    List<String> sections,
    List<String> targets,
    String summary,
    String stdin,
    String expectedStdout,
    String code
) {

    public ClassroomCodeSample {
        sections = sections == null ? List.of() : List.copyOf(sections);
        targets = targets == null ? List.of() : List.copyOf(targets);
        stdin = stdin == null ? "" : stdin;
        expectedStdout = expectedStdout == null ? "" : expectedStdout;
    }
}
