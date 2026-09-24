package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Guards the shipped code library: every sample must be complete enough to run as-is. */
class ClassroomCodeCatalogTest {

    private final ClassroomCodeCatalog catalog = new ClassroomCodeCatalog(new ObjectMapper());

    @Test
    void shipsThePilotLessonWithFourRunnableSamples() {
        ClassroomCodeLesson lesson = catalog.lesson("03-01").orElseThrow();

        assertThat(lesson.lessonTitle()).contains("栈");
        assertThat(lesson.samples()).hasSize(4);
        assertThat(catalog.sampleCount()).isEqualTo(4);
    }

    @Test
    void everySampleCarriesCodeADriverAndTheTextbookReference() {
        for (ClassroomCodeLesson lesson : catalog.lessons()) {
            for (ClassroomCodeSample sample : lesson.samples()) {
                assertThat(sample.id()).startsWith(lesson.coursewareKey() + "-");
                assertThat(sample.title()).isNotBlank();
                assertThat(sample.sourceFile()).startsWith("ch");
                assertThat(sample.sections()).isNotEmpty();
                assertThat(sample.targets()).isNotEmpty();
                assertThat(sample.code()).contains("#include <stdio.h>").contains("int main(void)");
                assertThat(sample.expectedStdout()).isNotEmpty();
            }
        }
    }

    @Test
    void resolvesLessonsAndRejectsUnknownOnes() {
        assertThat(catalog.lesson("03-01")).isPresent();
        assertThat(catalog.lesson(" 03-01 ")).isPresent();
        assertThat(catalog.lesson("99-99")).isEmpty();
        assertThat(catalog.lesson(null)).isEmpty();
        assertThat(catalog.lesson("")).isEmpty();
    }

    @Test
    void canBeBuiltFromAnExplicitLessonList() {
        ClassroomCodeCatalog explicit = new ClassroomCodeCatalog(List.of(
            new ClassroomCodeLesson("01-01", "绪论", "01-intro", List.of())
        ));

        assertThat(explicit.sampleCount()).isZero();
        assertThat(explicit.lesson("01-01")).isPresent();
    }}
