package com.feng.dsagent.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PresentationCatalogTest {

    @TempDir
    Path root;

    private PresentationCatalog catalog;
    private SlideImageLinks links;

    @BeforeEach
    void setUp() throws IOException {
        write("rendered/deck-a/001.png");
        write("rendered/deck-a/002.png");
        Files.writeString(root.resolve("slides.json"), """
            {"version":1,"builtAt":"2026-08-08T04:02:09Z","slideCount":4,"slides":[
              {"id":"deck-a-s001","deckId":"deck-a","deckTitle":"第一章-绪论01","slideNumber":1,"chapter":"01","title":"封面","shouldShow":true,"imagePath":"rendered/deck-a/001.png","lessonIds":["01-01A"]},
              {"id":"deck-a-s002","deckId":"deck-a","deckTitle":"第一章-绪论01","slideNumber":2,"chapter":"01","title":"数据","shouldShow":true,"imagePath":"rendered/deck-a/002.png","lessonIds":["01-01A","01-01B"]},
              {"id":"deck-c-s005","deckId":"deck-c","deckTitle":"第一章-绪论03","slideNumber":5,"chapter":"01","title":"上次课回顾","shouldShow":true,"imagePath":"rendered/deck-a/002.png","lessonIds":["01-01A"]},
              {"id":"deck-b-s001","deckId":"deck-b","deckTitle":"第八章-查找01","slideNumber":1,"chapter":"08","title":"查找","shouldShow":false,"imagePath":"rendered/deck-a/001.png","lessonIds":["08-01A"]}
            ]}
            """);
        Files.writeString(root.resolve("lesson-presentation-plans.json"), """
            {"version":1,"builtAt":"2026-08-08T04:55:24Z","lessons":{
              "01-01A":{"lessonId":"01-01A","title":"数据","scenes":{"intro":{"slides":["deck-a-s001","deck-a-s002"]},"second":{"slides":["deck-a-s002"]}}},
              "01-01B":{"lessonId":"01-01B","title":"结构","scenes":{"intro":{"slides":["deck-a-s002"]}}},
              "01-01C":{"lessonId":"01-01C","title":"回顾","scenes":{"intro":{"slides":["deck-c-s005"]}}},
              "08-01A":{"lessonId":"08-01A","title":"查找","scenes":{"intro":{"slides":["deck-b-s001"]}}},
              "02-99A":{"lessonId":"02-99A","title":"习题","slideOrder":["deck-b-s001","deck-a-s002"]}
            }}
            """);
        catalog = catalogFor(root, true);
    }

    /** A catalogue over `root`, wired the way the application wires it. */
    private PresentationCatalog catalogFor(Path root, boolean enabled) {
        PresentationProperties properties =
            new PresentationProperties(enabled, root.toString(), "", "", "test-asset-secret");
        links = new SlideImageLinks(properties);
        return new PresentationCatalog(properties, links, JsonMapper.builder().build());
    }

    private void write(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "png");
    }

    @Test
    void mergesTheSubLessonsOfOneCourseLessonWithoutRepeatingPages() {
        List<PresentationSlide> slides = catalog.slidesForCoursewareKey("01-01");

        // The primary deck wins (deck-a claims two pages, deck-c only one), so the neighbour deck's
        // "上次课回顾" page never lands in this lesson's flow, and the kept pages stay in deck order.
        assertThat(slides).extracting(PresentationSlide::id).containsExactly("deck-a-s001", "deck-a-s002");
        assertThat(catalog.planIds("01-01")).containsExactly("01-01A", "01-01B", "01-01C");
        assertThat(catalog.snapshot().ready).isTrue();
    }

    @Test
    void fallsBackToPlanOrderSortedByDeckWhenNoDeckClaimsTheLesson() {
        // 02-99A is claimed by no deck (lessonIds never mention it); the scene-ordered plan is re-sorted
        // so even a lesson without an owning deck follows the decks page by page.
        assertThat(catalog.slidesForCoursewareKey("02-99"))
            .extracting(PresentationSlide::id)
            .containsExactly("deck-a-s002", "deck-b-s001");
    }

    @Test
    void keepsPagesThePipelineMarkedAsOptionalButReportsTheirFlag() {
        List<PresentationSlide> slides = catalog.slidesForCoursewareKey("08-01");

        assertThat(slides).hasSize(1);
        assertThat(slides.get(0).shouldShow()).isFalse();
        // The url is signed and carries the form the page is served in, because that is what lets a cache
        // in front of the deployment hold on to it instead of forwarding every page turn to the origin.
        assertThat(slides.get(0).imageUrl()).isEqualTo(links.url("deck-b-s001", SlideImageLinks.PNG));
        assertThat(slides.get(0).imageUrl()).startsWith("/api/v1/presentation/slides/deck-b-s001/").endsWith(".png");
    }

    @Test
    void prefersTheConvertedTwinWhenThePipelinePublishedOne() throws IOException {
        write("rendered/deck-a/002.webp");
        catalog = catalogFor(root, true);

        // Same page, a fraction of the bytes: the url says .webp and the served file is the twin.
        assertThat(catalog.slidesForCoursewareKey("01-01").get(1).imageUrl())
            .isEqualTo(links.url("deck-a-s002", SlideImageLinks.WEBP));
        assertThat(catalog.imageFile("deck-a-s002"))
            .isEqualTo(root.resolve("rendered/deck-a/002.webp").toRealPath());
        // A page without a twin keeps being served as the png it is.
        assertThat(catalog.imageFile("deck-a-s001"))
            .isEqualTo(root.resolve("rendered/deck-a/001.png").toRealPath());
    }

    @Test
    void signsEveryPageDifferentlySoTheDeckCannotBeWalkedById() {
        assertThat(catalog.slide("deck-a-s001").imageUrl())
            .isNotEqualTo(catalog.slide("deck-a-s002").imageUrl());
    }

    @Test
    void listsDecksInSlideOrder() {
        assertThat(catalog.decks()).extracting(PresentationCatalog.Deck::deckId).containsExactly("deck-a", "deck-c", "deck-b");
        assertThat(catalog.deckSlides("deck-a")).extracting(PresentationSlide::slideNumber).containsExactly(1, 2);
        assertThatThrownBy(() -> catalog.deckSlides("missing")).isInstanceOf(ApiException.class);
    }

    @Test
    void refusesUnknownSlidesAndPathsOutsideTheMount() throws IOException {
        assertThat(catalog.imageFile("deck-a-s001")).isEqualTo(root.resolve("rendered/deck-a/001.png").toRealPath());
        assertThatThrownBy(() -> catalog.imageFile("../../etc/passwd")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> catalog.imageFile("deck-a-unknown")).isInstanceOf(ApiException.class);

        // A record that points outside the mount must not resolve, even though its id is well formed.
        Path outside = root.getParent().resolve("secret.png");
        Files.writeString(outside, "secret");
        Files.writeString(root.resolve("slides.json"), """
            {"version":1,"slides":[{"id":"deck-a-s009","deckId":"deck-a","deckTitle":"x","slideNumber":9,"imagePath":"../../secret.png"}],
             "builtAt":"now"}
            """);
        catalog = catalogFor(root, true);
        assertThatThrownBy(() -> catalog.imageFile("deck-a-s009")).isInstanceOf(ApiException.class);
    }

    @Test
    void reportsNotReadyWhenThePipelineHasNotPublishedAnything() {
        PresentationCatalog empty = catalogFor(root.resolve("missing"), false);

        assertThat(empty.snapshot().ready).isFalse();
        assertThat(empty.slidesForCoursewareKey("01-01")).isEmpty();
    }
}
