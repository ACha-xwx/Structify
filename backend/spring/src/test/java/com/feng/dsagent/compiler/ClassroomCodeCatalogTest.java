package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Guards the shipped code library: every sample must be complete enough to run as-is. */
class ClassroomCodeCatalogTest {

    private final ClassroomCodeCatalog catalog = new ClassroomCodeCatalog(new ObjectMapper());

    @Test
    void keepsTheHandWrittenPilotSamplesForTheFirstStackLesson() {
        ClassroomCodeLesson lesson = catalog.lesson("03-01").orElseThrow();

        assertThat(lesson.lessonTitle()).contains("栈");
        // Four samples are still the hand-written pilots; anything after them came from the map.
        assertThat(lesson.samples()).hasSizeGreaterThanOrEqualTo(4);
        for (ClassroomCodeSample sample : lesson.samples().subList(0, 4)) {
            assertThat(sample.sections()).isNotEmpty();
            assertThat(sample.targets()).isNotEmpty();
        }
    }

    @Test
    void coversMostOfTheReviewedLessons() {
        assertThat(catalog.lessons()).hasSizeGreaterThan(40);
        assertThat(catalog.sampleCount()).isGreaterThan(100);
        Set<String> keys = new LinkedHashSet<>();
        for (ClassroomCodeLesson lesson : catalog.lessons()) {
            assertThat(keys.add(lesson.coursewareKey())).isTrue();
            assertThat(lesson.samples()).isNotEmpty();
        }
    }

    @Test
    void everySampleCarriesCodeADriverAndTheTextbookReference() {
        for (ClassroomCodeLesson lesson : catalog.lessons()) {
            for (ClassroomCodeSample sample : lesson.samples()) {
                assertThat(sample.id()).startsWith(lesson.coursewareKey() + "-");
                assertThat(sample.title()).isNotBlank();
                assertThat(sample.sourceFile()).startsWith("ch");
                assertThat(sample.summary()).isNotBlank();
                assertThat(sample.code()).contains("#include <stdio.h>");
                // The textbook prints `void main()` and those listings are quoted verbatim; both
                // forms are complete programs, which is what this guard is about.
                assertThat(sample.code()).matches("(?s).*\\b(int\\s+main\\s*\\([^)]*\\)|void\\s+main\\s*\\(\\)).*");
                assertThat(sample.expectedStdout()).isNotEmpty();
            }
        }
    }

    /**
     * Every sample points back at a listing that actually exists in the textbook library - the
     * library is the source of truth, and a sample without a backing listing is a dead link.
     */
    @Test
    void everySamplePointsAtAListingThatShipsWithTheTextbookLibrary() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode library;
        try (InputStream stream = new ClassPathResource(
                "classroom-code/textbook/library.json").getInputStream()) {
            library = mapper.readTree(stream);
        }

        Set<String> listings = new LinkedHashSet<>();
        for (JsonNode chapter : library.path("chapters")) {
            String number = chapter.path("chapter").asString("").replaceAll("\\D", "");
            for (JsonNode fragment : chapter.path("fragments")) {
                listings.add("ch" + number + "/code/" + fragment.path("file").asString(""));
            }
        }

        assertThat(listings).isNotEmpty();
        for (ClassroomCodeLesson lesson : catalog.lessons()) {
            for (ClassroomCodeSample sample : lesson.samples()) {
                assertThat(listings)
                    .as("来自 %s 的 %s", lesson.coursewareKey(), sample.id())
                    .contains(sample.sourceFile());
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
