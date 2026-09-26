package com.feng.dsagent.presentation;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Offline courseware built by the deck pipeline: slide metadata plus rendered page images.
 * The directory is read-only material and never serves anything outside its root.
 */
@ConfigurationProperties("app.presentation")
public record PresentationProperties(
    boolean enabled,
    String directory,
    String imageDirectory,
    String annotations,
    String assetSecret
) {

    public Path root() {
        if (directory == null || directory.isBlank()) {
            throw new IllegalStateException("app.presentation.directory must be configured");
        }
        return Path.of(directory).toAbsolutePath().normalize();
    }

    /** Images live under the same root unless a separate image mount is configured. */
    public Path imageRoot() {
        if (imageDirectory == null || imageDirectory.isBlank()) {
            return root();
        }
        return Path.of(imageDirectory).toAbsolutePath().normalize();
    }

    /** Local per-page annotation built by {@code scripts/build-slide-annotations.mjs}; optional. */
    public Path annotationFile() {
        if (annotations == null || annotations.isBlank()) {
            return null;
        }
        return Path.of(annotations).toAbsolutePath().normalize();
    }

    /**
     * Secret that signs the urls page images are served from. A url is a capability, so this has to be a
     * real secret: with a guessable one the whole courseware could be read by walking page ids.
     */
    public String assetSecret() {
        return assetSecret == null ? "" : assetSecret;
    }

    public boolean ready() {
        return enabled && Files.isDirectory(root());
    }
}
