package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Guards the shipped class listings and, above all, the examples that make them runnable. */
class TextbookCodeLibraryTest {

    private final TextbookCodeLibrary library = new TextbookCodeLibrary(new ObjectMapper());

    @Test
    void shipsEveryChapterInBookOrder() {
        assertThat(library.chapters()).hasSize(8);
        assertThat(library.fragmentCount()).isEqualTo(99);
        assertThat(library.chapters().get(0).chapter()).isEqualTo("ch02");
        assertThat(library.chapters().get(0).title()).contains("线性表");
    }

    @Test
    void everyFragmentCarriesVerbatimSource() {
        for (TextbookCodeChapter chapter : library.chapters()) {
            assertThat(chapter.fragments()).isNotEmpty();
            for (TextbookCodeFragment fragment : chapter.fragments()) {
                assertThat(fragment.id()).startsWith(chapter.chapter() + "-");
                assertThat(fragment.file()).isNotBlank();
                assertThat(fragment.title()).isNotBlank();
                assertThat(fragment.code()).isNotBlank();
                assertThat(fragment.kind()).isIn("algorithm", "type");
                assertThat(fragment.startWith()).isIn("main", "types");
            }
        }
    }

    /**
     * The point of an example is that the editor can hand the learner a program that really runs, so
     * every one of them has to carry its code, the input it was fed, and the output it produced.
     */
    @Test
    void everyExampleIsCompleteAndVerified() {
        int examples = 0;
        int blocked = 0;
        for (TextbookCodeChapter chapter : library.chapters()) {
            for (TextbookCodeFragment fragment : chapter.fragments()) {
                TextbookCodeExample example = fragment.example();
                if (example == null) {
                    assertThat(fragment.blocked())
                        .as("%s 既没有示例也没有说明", fragment.id())
                        .isNotBlank();
                    blocked++;
                    continue;
                }
                examples++;
                // A handful of listings carry the book's own `void main()`; either shape counts, the
                // point is that the example is a complete program with an entry point.
                assertThat(example.code()).as("%s 的示例代码", fragment.id()).contains("main(");
                assertThat(example.expectedStdout()).as("%s 的示例没有实测输出", fragment.id()).isNotBlank();
                assertThat(example.note()).as("%s 的示例没有说明补了什么", fragment.id()).isNotBlank();
            }
        }
        assertThat(examples).as("有示例的片段数").isGreaterThanOrEqualTo(90);
        assertThat(examples + blocked).isEqualTo(99);
    }
}
