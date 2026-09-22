package com.feng.dsagent.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The signature is what stands in for a session on the page-image urls, so it has to be unguessable,
 * stable, and specific to one page. These tests pin all three, because a regression here is either a
 * courseware leak or a page that can no longer be cached.
 */
class SlideImageLinksTest {

    private final SlideImageLinks links = new SlideImageLinks(
        new PresentationProperties(true, ".", "", "", "a-deployment-secret"));

    @Test
    void addressesAPageWithItsOwnSignedUrl() {
        String url = links.url("ch08-deck03-5780942-s009", SlideImageLinks.WEBP);

        assertThat(url).startsWith("/api/v1/presentation/slides/ch08-deck03-5780942-s009/").endsWith(".webp");
        String signature = url.substring(url.lastIndexOf('/') + 1, url.length() - ".webp".length());
        assertThat(signature).matches("[A-Za-z0-9_-]{22}");
        assertThat(links.verify("ch08-deck03-5780942-s009", signature)).isTrue();
    }

    @Test
    void neverIssuesTheSameSignatureForTwoPages() {
        assertThat(links.url("deck-a-s001", SlideImageLinks.PNG))
            .isNotEqualTo(links.url("deck-a-s002", SlideImageLinks.PNG));
    }

    @Test
    void staysTheSameAcrossCallsSoACachedUrlKeepsWorking() {
        assertThat(links.url("deck-a-s001", SlideImageLinks.PNG)).isEqualTo(links.url("deck-a-s001", SlideImageLinks.PNG));
    }

    @Test
    void rejectsASignatureThatWasNotIssuedForThatPage() {
        String signature = links.url("deck-a-s001", SlideImageLinks.PNG)
            .replaceAll("^.*/", "").replace(".png", "");

        assertThat(links.verify("deck-a-s002", signature)).isFalse();
        assertThat(links.verify("deck-a-s001", "AAAAAAAAAAAAAAAAAAAAAA")).isFalse();
        assertThat(links.verify("deck-a-s001", "")).isFalse();
        assertThat(links.verify("deck-a-s001", null)).isFalse();
        assertThat(links.verify(null, signature)).isFalse();
    }

    @Test
    void belongsToTheDeploymentThatSignedIt() {
        SlideImageLinks other = new SlideImageLinks(
            new PresentationProperties(true, ".", "", "", "a-different-deployment-secret"));
        String signature = links.url("deck-a-s001", SlideImageLinks.PNG)
            .replaceAll("^.*/", "").replace(".png", "");

        // Rotating the secret is how every issued url is invalidated at once.
        assertThat(other.verify("deck-a-s001", signature)).isFalse();
    }

    @Test
    void stillAnswersWhenNoSecretIsConfiguredRatherThanFailingEveryRequest() {
        SlideImageLinks unconfigured = new SlideImageLinks(new PresentationProperties(true, ".", "", "", null));

        String url = unconfigured.url("deck-a-s001", SlideImageLinks.PNG);
        String signature = url.replaceAll("^.*/", "").replace(".png", "");
        assertThat(unconfigured.verify("deck-a-s001", signature)).isTrue();
    }

    @Test
    void anUnconfiguredDeploymentDoesNotFallBackToSomethingEveryoneKnows() {
        // Falling back to a fixed key would hand the courseware to anyone who can guess a page id, so the
        // fallback is a key that only the process holds - which costs url stability, not secrecy.
        SlideImageLinks first = new SlideImageLinks(new PresentationProperties(true, ".", "", "", ""));
        SlideImageLinks second = new SlideImageLinks(new PresentationProperties(true, ".", "", "", ""));

        String signature = first.url("deck-a-s001", SlideImageLinks.PNG)
            .replaceAll("^.*/", "").replace(".png", "");
        assertThat(second.verify("deck-a-s001", signature)).isFalse();
    }
}
