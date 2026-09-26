package com.feng.dsagent.compiler;

import java.util.List;

/** The textbook code of one chapter, in book order. */
public record TextbookCodeChapter(String chapter, String title, List<TextbookCodeFragment> fragments) {

    public TextbookCodeChapter {
        fragments = fragments == null ? List.of() : List.copyOf(fragments);
    }
}
