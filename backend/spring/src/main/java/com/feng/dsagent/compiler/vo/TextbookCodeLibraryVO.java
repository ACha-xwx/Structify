package com.feng.dsagent.compiler.vo;

import java.util.List;

import com.feng.dsagent.compiler.domain.TextbookCodeChapter;
/**
 * Payload of {@code GET /api/v1/code/library}: the class listings, each with the runnable example
 * built around it (or the reason there is none).
 */
public record TextbookCodeLibraryVO(
    List<TextbookCodeChapter> chapters,
    int fragmentCount
) {

    public TextbookCodeLibraryVO {
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
    }
}
