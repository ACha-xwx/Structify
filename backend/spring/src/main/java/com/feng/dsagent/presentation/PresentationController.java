package com.feng.dsagent.presentation;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only courseware for the classroom's right-hand pane and the standalone deck browser.
 * Images are streamed by the backend so the reviewed decks never become a public static folder.
 *
 * <p>Every response here is derived from the reviewed snapshot on disk, is identical for every account,
 * and is addressed by an id that already carries the deck's content hash. That is why each endpoint
 * states its own caching policy: on a deployment reached through an edge cache, this is the difference
 * between one round trip per page turn and the bytes travelling exactly once.
 */
@RestController
@RequestMapping("/api/v1/presentation")
public class PresentationController {

    /**
     * A slide id embeds the deck's content hash, so the bytes behind one id never change. Letting the
     * browser keep the page indefinitely turns 上一页/下一页 into a local lookup.
     */
    private static final CacheControl SLIDE_IMAGE_CACHE =
        CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();

    /**
     * Catalogues only change when a release lands. A short window keeps navigation instant without ever
     * showing a lesson list from a previous publication.
     */
    private static final CacheControl CATALOG_CACHE =
        CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic();

    private final PresentationService presentations;
    private final SlideImageLinks links;

    public PresentationController(PresentationService presentations, SlideImageLinks links) {
        this.presentations = presentations;
        this.links = links;
    }

    @GetMapping("/meta")
    ResponseEntity<PresentationService.Meta> meta() {
        return ResponseEntity.ok().cacheControl(CATALOG_CACHE).body(presentations.meta());
    }

    @GetMapping("/lessons/{lessonId}/slides")
    ResponseEntity<PresentationService.LessonCourseware> lessonSlides(@PathVariable String lessonId) {
        return ResponseEntity.ok().cacheControl(CATALOG_CACHE).body(presentations.forLesson(lessonId));
    }

    @GetMapping("/courseware/{coursewareKey}/slides")
    ResponseEntity<PresentationService.LessonCourseware> coursewareSlides(@PathVariable String coursewareKey) {
        return ResponseEntity.ok().cacheControl(CATALOG_CACHE).body(presentations.forCoursewareKey(coursewareKey));
    }

    @GetMapping("/decks")
    ResponseEntity<List<PresentationCatalog.Deck>> decks() {
        return ResponseEntity.ok().cacheControl(CATALOG_CACHE).body(presentations.decks());
    }

    @GetMapping("/decks/{deckId}/slides")
    ResponseEntity<List<PresentationSlide>> deckSlides(@PathVariable String deckId) {
        return ResponseEntity.ok().cacheControl(CATALOG_CACHE).body(presentations.deckSlides(deckId));
    }

    /**
     * The page image for callers that hold a session: the same bytes as the signed url below, reached with
     * the classroom's own credentials.
     */
    @GetMapping("/slides/{slideId}/image")
    ResponseEntity<FileSystemResource> image(@PathVariable String slideId) {
        return page(presentations.imageFile(slideId));
    }

    /**
     * The signed form of a page image, which is what the courseware is actually browsed through.
     *
     * <p>It exists so a cache between the browser and the origin can store a page: a cache filling itself
     * has no session, so the url carries its own proof of origin instead of relying on a cookie, and it
     * ends in an extension so the cache recognises the response as a static object rather than an API call.
     * The two routes are the two forms the pipeline may have published a page in; which one is served is
     * decided by what is actually on disk, not by the url.
     */
    @GetMapping("/slides/{slideId}/{signature}.webp")
    ResponseEntity<FileSystemResource> signedWebpImage(@PathVariable String slideId, @PathVariable String signature) {
        return signedPage(slideId, signature);
    }

    @GetMapping("/slides/{slideId}/{signature}.png")
    ResponseEntity<FileSystemResource> signedPngImage(@PathVariable String slideId, @PathVariable String signature) {
        return signedPage(slideId, signature);
    }

    private ResponseEntity<FileSystemResource> signedPage(String slideId, String signature) {
        if (!links.verify(slideId, signature)) {
            // A url whose signature does not verify is not a page. Say so with an explicit no-store, so no
            // shared cache starts answering for the picture with the refusal.
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore())
                .build();
        }
        return page(presentations.imageFile(slideId));
    }

    private ResponseEntity<FileSystemResource> page(Path file) {
        return ResponseEntity.ok()
            .contentType(imageMediaType(file))
            .cacheControl(SLIDE_IMAGE_CACHE)
            .body(new FileSystemResource(file));
    }

    /** The form the bytes are actually in, which the pipeline - not the caller - decides. */
    private static MediaType imageMediaType(Path file) {
        return file.getFileName().toString().endsWith(".webp")
            ? MediaType.parseMediaType("image/webp")
            : MediaType.IMAGE_PNG;
    }
}
