package com.feng.dsagent.presentation;

import com.feng.dsagent.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only index over the offline courseware produced by the deck pipeline: slide metadata
 * ({@code slides.json}) plus the per-sub-lesson slide plans ({@code lesson-presentation-plans.json}).
 * Nothing here is derived from the model; a slide is only ever shown because the pipeline rendered it.
 */
@Service
public class PresentationCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(PresentationCatalog.class);
    private static final String SLIDES_FILE = "slides.json";
    private static final String ANNOTATION_VERSION = "version";
    private static final String PLANS_FILE = "lesson-presentation-plans.json";
    private static final Pattern SLIDE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,160}$");

    public record Deck(String deckId, String title, String chapter, int slideCount, String coverSlideId) {
    }

    /** One scene of a planned sub-lesson: the pages the deck pipeline assigned to that teaching beat. */
    public record ScenePlan(String key, String coverage, double score, List<String> slideIds) {
    }

    /** A sub-lesson of the courseware plan, e.g. 08-02B 二叉排序树的删除与性能. */
    public record SubLessonPlan(String lessonId, String title, List<ScenePlan> scenes) {
    }

    static final class Snapshot {
        final boolean ready;
        final String builtAt;
        final List<PresentationSlide> slides;
        final Map<String, PresentationSlide> byId;
        final Map<String, Deck> decks;
        final Map<String, List<String>> deckOrder;
        final Map<String, List<String>> lessonOrder;
        final Map<String, String> slideImagePaths;
        final long slidesStamp;
        final long plansStamp;
        final Map<String, SubLessonPlan> subLessons;

        Snapshot(boolean ready, String builtAt, List<PresentationSlide> slides, Map<String, PresentationSlide> byId,
                 Map<String, Deck> decks, Map<String, List<String>> deckOrder, Map<String, List<String>> lessonOrder,
                 Map<String, String> slideImagePaths, long slidesStamp, long plansStamp,
                 Map<String, SubLessonPlan> subLessons) {
            this.ready = ready;
            this.builtAt = builtAt;
            this.slides = slides;
            this.byId = byId;
            this.decks = decks;
            this.deckOrder = deckOrder;
            this.lessonOrder = lessonOrder;
            this.slideImagePaths = slideImagePaths;
            this.slidesStamp = slidesStamp;
            this.plansStamp = plansStamp;
            this.subLessons = subLessons;
        }

        Map<String, String> slideImagePaths() {
            return slideImagePaths;
        }

        Map<String, PresentationSlide> byId() {
            return byId;
        }

        Map<String, Deck> decks() {
            return decks;
        }

        Map<String, List<String>> deckOrder() {
            return deckOrder;
        }

        Map<String, SubLessonPlan> subLessons() {
            return subLessons;
        }
    }

    private final PresentationProperties properties;
    private final SlideImageLinks links;
    private final ObjectMapper mapper;
    private volatile Snapshot snapshot;

    public PresentationCatalog(PresentationProperties properties, SlideImageLinks links, ObjectMapper mapper) {
        this.properties = properties;
        this.links = links;
        this.mapper = mapper;
    }

    public Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null || stale(current)) {
            synchronized (this) {
                if (snapshot == null || stale(snapshot)) {
                    snapshot = load();
                }
                return snapshot;
            }
        }
        return current;
    }

    private boolean stale(Snapshot candidate) {
        return candidate.slidesStamp != stamp(SLIDES_FILE) || candidate.plansStamp != stamp(PLANS_FILE);
    }

    private long stamp(String fileName) {
        try {
            Path file = properties.root().resolve(fileName);
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
        } catch (IOException | RuntimeException error) {
            return -1L;
        }
    }

    private Snapshot load() {
        if (!properties.ready()) {
            return new Snapshot(false, "", List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), -1L, -1L, Map.of());
        }
        try {
            Path root = properties.root();
            Path slidesFile = root.resolve(SLIDES_FILE);
            Path plansFile = root.resolve(PLANS_FILE);
            if (!Files.isRegularFile(slidesFile) || !Files.isRegularFile(plansFile)) {
                LOGGER.warn("Courseware index is incomplete under {}; the slide panel stays empty", root);
                return new Snapshot(false, "", List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), -1L, -1L, Map.of());
            }
            JsonNode slidesRoot = mapper.readTree(Files.readString(slidesFile));
            JsonNode annotations = loadAnnotations();
            JsonNode plansRoot = mapper.readTree(Files.readString(plansFile));
            List<PresentationSlide> slides = new ArrayList<>();
            Map<String, PresentationSlide> byId = new LinkedHashMap<>();
            Map<String, List<String>> deckOrder = new LinkedHashMap<>();
            Map<String, String> imagePaths = new LinkedHashMap<>();
            Map<String, String> deckTitles = new LinkedHashMap<>();
            Map<String, String> deckChapters = new LinkedHashMap<>();
            for (JsonNode node : slidesRoot.path("slides")) {
                String id = node.path("id").asText("");
                if (id.isBlank()) {
                    continue;
                }
                PresentationSlide slide = new PresentationSlide(
                    id,
                    node.path("deckId").asText(""),
                    node.path("deckTitle").asText(""),
                    node.path("slideNumber").asInt(0),
                    node.path("chapter").asText(""),
                    node.path("title").asText(""),
                    node.path("rawText").asText(""),
                    node.path("speakerNotes").asText(""),
                    node.path("semanticSummary").asText(""),
                    node.path("teachingRole").asText(""),
                    node.path("teachingFocus").asText(""),
                    strings(node.path("concepts")),
                    strings(node.path("visualAnchors")),
                    node.path("shouldShow").asBoolean(true),
                    strings(node.path("lessonIds")),
                    imageUrl(id, node.path("imagePath").asText("")),
                    annotations.path(id).path("section").asText(""),
                    annotations.path(id).path("role").asText(""),
                    strings(annotations.path(id).path("terms"))
                );
                slides.add(slide);
                byId.put(id, slide);
                deckOrder.computeIfAbsent(slide.deckId(), key -> new ArrayList<>()).add(id);
                String imagePath = node.path("imagePath").asText("");
                if (!imagePath.isBlank()) {
                    imagePaths.put(id, imagePath);
                }
                deckTitles.putIfAbsent(slide.deckId(), slide.deckTitle());
                deckChapters.putIfAbsent(slide.deckId(), slide.chapter());
            }
            Map<String, Deck> decks = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : deckOrder.entrySet()) {
                String deckId = entry.getKey();
                List<String> ordered = entry.getValue();
                decks.put(deckId, new Deck(deckId, deckTitles.getOrDefault(deckId, deckId), deckChapters.getOrDefault(deckId, ""), ordered.size(), ordered.get(0)));
            }
            Map<String, List<String>> lessonOrder = new LinkedHashMap<>();
            Map<String, SubLessonPlan> subLessons = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : plansRoot.path("lessons").properties()) {
                lessonOrder.put(entry.getKey(), plannedSlides(entry.getValue()));
                subLessons.put(entry.getKey(), new SubLessonPlan(entry.getKey(), entry.getValue().path("title").asText(""), scenePlans(entry.getValue())));
            }
            // Order matters: decks, deck pages and lesson scenes are displayed in pipeline order, and
            // Map.copyOf does not preserve iteration order.
            return new Snapshot(true, slidesRoot.path("builtAt").asText(""), List.copyOf(slides), ordered(byId),
                ordered(decks), ordered(deckOrder), ordered(lessonOrder), ordered(imagePaths), stamp(SLIDES_FILE), stamp(PLANS_FILE),
                ordered(subLessons));
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Courseware index could not be read: {}", error.getMessage());
            return new Snapshot(false, "", List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), -1L, -1L, Map.of());
        }
    }

    /** Slides of one planned sub-lesson, in scene order, de-duplicated. */
    private static List<String> plannedSlides(JsonNode plan) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        JsonNode scenes = plan.path("scenes");
        if (scenes.isObject()) {
            for (JsonNode scene : scenes.properties().stream().map(Map.Entry::getValue).toList()) {
                if (scene.path("slides").isArray()) {
                    scene.path("slides").forEach(slide -> {
                        String value = slide.asText("");
                        if (!value.isBlank()) {
                            ordered.add(value);
                        }
                    });
                }
            }
        }
        if (ordered.isEmpty() && plan.path("slideOrder").isArray()) {
            plan.path("slideOrder").forEach(slide -> {
                String value = slide.asText("");
                if (!value.isBlank()) {
                    ordered.add(value);
                }
            });
        }
        return List.copyOf(ordered);
    }

    private static <K, V> Map<K, V> ordered(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /** Local annotations, or an empty object when they have not been built yet. */
    private JsonNode loadAnnotations() {
        try {
            Path file = properties.annotationFile();
            if (file == null || !Files.isRegularFile(file)) {
                return mapper.createObjectNode();
            }
            JsonNode root = mapper.readTree(Files.readString(file));
            return root.path("slides").isObject() ? root.path("slides") : mapper.createObjectNode();
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Slide annotations could not be read: {}", error.getMessage());
            return mapper.createObjectNode();
        }
    }

    /** Scenes in pipeline order; each carries the pages assigned to that teaching beat. */
    private static List<ScenePlan> scenePlans(JsonNode plan) {
        List<ScenePlan> scenes = new ArrayList<>();
        JsonNode node = plan.path("scenes");
        if (!node.isObject()) {
            return List.copyOf(scenes);
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            List<String> ids = new ArrayList<>();
            if (entry.getValue().path("slides").isArray()) {
                entry.getValue().path("slides").forEach(slide -> {
                    String value = slide.asText("");
                    if (!value.isBlank()) {
                        ids.add(value);
                    }
                });
            }
            if (!ids.isEmpty()) {
                scenes.add(new ScenePlan(entry.getKey(), entry.getValue().path("coverage").asText(""),
                    entry.getValue().path("score").asDouble(0), List.copyOf(ids)));
            }
        }
        return List.copyOf(scenes);
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    /** Sub-lesson plan ids belonging to one course lesson number, e.g. {@code 08-01 -> [08-01A, 08-01B]}. */
    public List<String> planIds(String coursewareKey) {
        if (coursewareKey == null || coursewareKey.isBlank()) {
            return List.of();
        }
        // Any single-letter sub-lesson suffix belongs to the lesson: plans exist as 08-01A/B but also
        // 01-06A..D and 08-04A..C, and dropping the C/D ones would silently drop their pages.
        return snapshot().lessonOrder.keySet().stream()
            .filter(id -> id.equals(coursewareKey)
                || (id.startsWith(coursewareKey) && id.length() == coursewareKey.length() + 1))
            .sorted()
            .toList();
    }

    /**
     * Ordered slides for a course lesson number. The decks themselves claim pages for a lesson
     * ({@code lessonIds} on each slide); the deck with the most claims is the one that actually teaches
     * this lesson, and its pages are used in the deck's own order - one lesson follows one deck, exactly
     * as the PPT is laid out. Cross-deck stragglers (mostly a neighbouring deck's "上次课回顾" pages that
     * merely repeat this lesson's topic) are dropped, because teaching them here would put a page on
     * screen that belongs to another lecture's flow. Lessons no deck claims fall back to the scene-ordered
     * plans, re-sorted into deck order so even those follow the PPTs page by page.
     */
    public List<PresentationSlide> slidesForCoursewareKey(String coursewareKey) {
        Snapshot current = snapshot();
        List<String> ids = planIds(coursewareKey);
        if (ids.isEmpty()) {
            return List.of();
        }
        Set<String> subLessons = Set.copyOf(ids);
        Map<String, List<PresentationSlide>> claimed = new LinkedHashMap<>();
        for (PresentationSlide slide : current.slides) {
            if (slide.lessonIds().stream().noneMatch(subLessons::contains)) {
                continue;
            }
            claimed.computeIfAbsent(slide.deckId(), key -> new ArrayList<>()).add(slide);
        }
        if (claimed.isEmpty()) {
            LinkedHashSet<String> planned = new LinkedHashSet<>();
            for (String planId : ids) {
                planned.addAll(current.lessonOrder.getOrDefault(planId, List.of()));
            }
            return planned.stream()
                .map(current.byId::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt((PresentationSlide slide) -> deckRank(current, slide.deckId()))
                    .thenComparingInt(PresentationSlide::slideNumber))
                .toList();
        }
        String primary = null;
        int best = -1;
        for (Map.Entry<String, List<PresentationSlide>> entry : claimed.entrySet()) {
            // Strictly greater: the earlier deck wins a tie, matching how the courseware folder orders them.
            if (entry.getValue().size() > best) {
                best = entry.getValue().size();
                primary = entry.getKey();
            }
        }
        List<PresentationSlide> result = new ArrayList<>();
        for (String id : current.deckOrder.getOrDefault(primary, List.of())) {
            PresentationSlide slide = current.byId.get(id);
            if (slide != null && slide.lessonIds().stream().anyMatch(subLessons::contains)) {
                result.add(slide);
            }
        }
        result.sort(Comparator.comparingInt(PresentationSlide::slideNumber));
        return List.copyOf(result);
    }

    /** Position of a deck in the pipeline's own order, for stable cross-deck sorting. */
    private static int deckRank(Snapshot current, String deckId) {
        int rank = 0;
        for (String candidate : current.deckOrder.keySet()) {
            if (candidate.equals(deckId)) {
                return rank;
            }
            rank++;
        }
        return Integer.MAX_VALUE;
    }

    /** Planned sub-lessons of one course lesson number, in plan order, with their ordered scenes. */
    public List<SubLessonPlan> subLessons(String coursewareKey) {
        return planIds(coursewareKey).stream().map(id -> snapshot().subLessons().get(id)).filter(java.util.Objects::nonNull).toList();
    }

    public PresentationSlide slide(String slideId) {
        return slideId == null ? null : snapshot().byId().get(slideId);
    }

    public List<Deck> decks() {
        return List.copyOf(snapshot().decks().values());
    }

    public List<PresentationSlide> deckSlides(String deckId) {
        Snapshot current = snapshot();
        List<String> ids = current.deckOrder().get(deckId);
        if (ids == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRESENTATION_DECK_NOT_FOUND", "课件不存在");
        }
        List<PresentationSlide> result = new ArrayList<>();
        for (String id : ids) {
            PresentationSlide slide = current.byId().get(id);
            if (slide != null) {
                result.add(slide);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Absolute path of a rendered page image. The stored path is relative to the courseware root and
     * is re-checked after normalisation so a slide record can never address a file outside the mount.
     *
     * <p>When the pipeline has also published a converted twin of the page, the twin is what gets served:
     * it is the same picture at a fraction of the bytes, which is the difference between a page turn that
     * waits on the network and one that does not.
     */
    public Path imageFile(String slideId) {
        Snapshot current = snapshot();
        if (slideId == null || !SLIDE_ID.matcher(slideId).matches()) {
            throw notFound();
        }
        PresentationSlide slide = current.byId().get(slideId);
        if (slide == null) {
            throw notFound();
        }
        String stored = slideImagePath(slideId);
        if (stored.isBlank()) {
            throw notFound();
        }
        try {
            Path root = properties.imageRoot();
            Path candidate = root.resolve(stored).normalize();
            if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
                throw notFound();
            }
            Path twin = convertedTwin(candidate);
            Path chosen = twin != null && Files.isRegularFile(twin) ? twin : candidate;
            Path realRoot = root.toRealPath();
            Path realCandidate = chosen.toRealPath();
            if (!realCandidate.startsWith(realRoot)) {
                throw notFound();
            }
            return realCandidate;
        } catch (ApiException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw notFound();
        }
    }

    /**
     * The url a page image is fetched from. It carries a signature so the deck cannot be walked by guessing
     * page ids, and the extension of the form that will actually be served, because that is how a cache in
     * front of the deployment decides an object is worth storing.
     */
    private String imageUrl(String slideId, String storedPath) {
        return links.url(slideId, convertedFormat(storedPath));
    }

    private String convertedFormat(String storedPath) {
        Path file = imageRootOrNull();
        if (file != null && !storedPath.isBlank()) {
            Path twin = convertedTwin(file.resolve(storedPath).normalize());
            if (twin != null && Files.isRegularFile(twin)) {
                return SlideImageLinks.WEBP;
            }
        }
        return SlideImageLinks.PNG;
    }

    /** Sibling file a converted page would live in, or null when the page is already the converted form. */
    private static Path convertedTwin(Path image) {
        String name = image.getFileName().toString();
        return name.endsWith(".png")
            ? image.resolveSibling(name.substring(0, name.length() - ".png".length()) + ".webp")
            : null;
    }

    private Path imageRootOrNull() {
        try {
            return properties.imageRoot();
        } catch (RuntimeException error) {
            return null;
        }
    }

    /** Relative render path recorded for a slide by the pipeline. */
    public String slideImagePath(String slideId) {
        Snapshot current = snapshot();
        if (current.slideImagePaths() == null) {
            return "";
        }
        return current.slideImagePaths().getOrDefault(slideId, "");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PRESENTATION_SLIDE_NOT_FOUND", "课件页不存在");
    }
}
