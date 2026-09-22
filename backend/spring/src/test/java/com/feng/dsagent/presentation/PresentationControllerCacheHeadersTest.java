package com.feng.dsagent.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;

/**
 * These headers are the contract the cache in front of the deployment obeys. Without a cacheable slide
 * image every page turn costs a round trip; without a short catalogue window every visit refetches the
 * deck list. Bodies are the service's concern and are intentionally left unset here.
 */
class PresentationControllerCacheHeadersTest {

    private static final String SLIDE = "ch03-deck01-3caa7e8-s003";

    private final PresentationService presentations = mock(PresentationService.class);
    private final SlideImageLinks links = new SlideImageLinks(
        new PresentationProperties(true, ".", "", "", "test-asset-secret"));
    private final PresentationController controller = new PresentationController(presentations, links);

    private String signature() {
        String url = links.url(SLIDE, SlideImageLinks.PNG);
        return url.substring(url.lastIndexOf('/') + 1, url.length() - ".png".length());
    }

    @Test
    void slideImageMayBeKeptForeverBecauseTheIdCarriesTheDeckHash() {
        when(presentations.imageFile(SLIDE)).thenReturn(Path.of("/tmp/slide.png"));

        ResponseEntity<FileSystemResource> response = controller.image(SLIDE);

        assertThat(response.getHeaders().getCacheControl())
            .contains("max-age=31536000")
            .contains("immutable")
            .doesNotContain("no-store")
            .doesNotContain("no-cache");
        assertThat(response.getHeaders().getContentType()).hasToString("image/png");
    }

    @Test
    void aSignedUrlServesThePageWithTheSameForeverPolicy() {
        when(presentations.imageFile(SLIDE)).thenReturn(Path.of("/tmp/slide.png"));

        ResponseEntity<FileSystemResource> response = controller.signedPngImage(SLIDE, signature());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=31536000").contains("immutable");
    }

    @Test
    void aSignedUrlServesTheConvertedTwinAndSaysSo() {
        when(presentations.imageFile(SLIDE)).thenReturn(Path.of("/tmp/slide.webp"));

        ResponseEntity<FileSystemResource> response = controller.signedWebpImage(SLIDE, signature());

        // The pipeline decides the form; the header has to describe the bytes actually sent.
        assertThat(response.getHeaders().getContentType()).hasToString("image/webp");
    }

    @Test
    void aForgedSignatureIsRefusedAndMustNotBeCached() {
        ResponseEntity<FileSystemResource> response =
            controller.signedPngImage(SLIDE, "AAAAAAAAAAAAAAAAAAAAAA");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        // A shared cache must not start answering for the picture with the refusal.
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void metaCarriesTheCatalogueWindow() {
        ResponseEntity<PresentationService.Meta> response = controller.meta();

        assertThat(response.getHeaders().getCacheControl())
            .contains("max-age=600")
            .doesNotContain("no-store");
    }

    @Test
    void deckListCarriesTheCatalogueWindow() {
        ResponseEntity<List<PresentationCatalog.Deck>> response = controller.decks();

        assertThat(response.getHeaders().getCacheControl())
            .contains("max-age=600")
            .doesNotContain("no-store");
    }

    @Test
    void deckSlidesCarryTheCatalogueWindow() {
        ResponseEntity<List<PresentationSlide>> response = controller.deckSlides("ch03-deck01-3caa7e8");

        assertThat(response.getHeaders().getCacheControl()).contains("max-age=600");
    }

    @Test
    void lessonCoursewareCarriesTheCatalogueWindow() {
        ResponseEntity<PresentationService.LessonCourseware> response = controller.lessonSlides("textbook-abc");

        assertThat(response.getHeaders().getCacheControl()).contains("max-age=600");
    }
}
