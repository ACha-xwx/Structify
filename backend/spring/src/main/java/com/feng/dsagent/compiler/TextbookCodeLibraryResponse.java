package com.feng.dsagent.compiler;

import java.util.List;

/**
 * Payload of {@code GET /api/v1/code/library}: the class listings, each with the runnable example
 * built around it (or the reason there is none).
 */
public record TextbookCodeLibraryResponse(
    List<TextbookCodeChapter> chapters,
    int fragmentCount
) {

    public TextbookCodeLibraryResponse {
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
    }
}
