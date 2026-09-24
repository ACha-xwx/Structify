package com.feng.dsagent.compiler;

/**
 * A complete, runnable program built around one listing: the listing itself, the types and helpers it
 * assumes, and a {@code main} that exercises it. {@code expectedStdout} is the output that program
 * really produced when the library was built, so the editor can tell the learner whether their run
 * matched it.
 */
public record TextbookCodeExample(
    String code,
    String stdin,
    String expectedStdout,
    String note
) {

    public TextbookCodeExample {
        code = code == null ? "" : code;
        stdin = stdin == null ? "" : stdin;
        expectedStdout = expectedStdout == null ? "" : expectedStdout;
        note = note == null ? "" : note;
    }
}
