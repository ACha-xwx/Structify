package com.feng.dsagent.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.feng.dsagent.presentation.config.PresentationProperties;
import com.feng.dsagent.presentation.domain.PresentationSlide;
import com.feng.dsagent.presentation.support.PresentationCatalog;
import com.feng.dsagent.presentation.support.SlideImageLinks;
class StudyroomPresentationIndexIntegrationTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    Path renderRoot;

    @Test
    void parsesSlideIndexAndLessonPlansAndServesSiblingWebp() throws IOException {
        Path deck = renderRoot.resolve("第六章树和二叉树/第六章-树的基本概念01");
        Files.createDirectories(deck);
        Files.write(deck.resolve("幻灯片1.PNG"), new byte[] {1});
        Files.write(deck.resolve("幻灯片1.webp"), new byte[] {2});
        Files.writeString(renderRoot.resolve("slides.json"), """
            {"version":1,"builtAt":"2026-10-12T00:00:00Z","slides":[
              {"id":"ch06-deck01-s001","deckId":"ch06-deck01","deckTitle":"第六章-树的基本概念01",
               "slideNumber":1,"chapter":"06","title":"第六章-树的基本概念01 第1页","rawText":"",
               "speakerNotes":"","semanticSummary":"","teachingRole":"","teachingFocus":"",
               "concepts":[],"visualAnchors":[],"shouldShow":true,"lessonIds":[],
               "imagePath":"第六章树和二叉树/第六章-树的基本概念01/幻灯片1.PNG"}
            ]}
            """);
        Files.writeString(renderRoot.resolve("lesson-presentation-plans.json"), """
            {"version":1,"builtAt":"2026-10-12T00:00:00Z","lessons":{}}
            """);

        PresentationProperties properties = new PresentationProperties(true, renderRoot.toString(), "", "", "test-secret");
        PresentationCatalog catalog = new PresentationCatalog(properties, new SlideImageLinks(properties), MAPPER);
        JsonNode index = MAPPER.readTree(Files.readString(renderRoot.resolve("slides.json")));

        assertThat(catalog.snapshot().ready).isTrue();
        assertThat(index.path("builtAt").asText()).isEqualTo("2026-10-12T00:00:00Z");
        assertThat(catalog.decks()).extracting(PresentationCatalog.Deck::deckId).containsExactly("ch06-deck01");
        assertThat(catalog.snapshot().subLessons()).isEmpty();
        assertThat(catalog.deckSlides("ch06-deck01")).extracting(PresentationSlide::id)
            .containsExactly("ch06-deck01-s001");
        assertThat(catalog.slide("ch06-deck01-s001").imageUrl()).endsWith(".webp");
        assertThat(catalog.imageFile("ch06-deck01-s001"))
            .isEqualTo(deck.resolve("幻灯片1.webp").toRealPath());
    }
}
