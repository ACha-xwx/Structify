package com.feng.dsagent.classroom;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.presentation.PresentationCatalog;
import com.feng.dsagent.presentation.PresentationService;
import com.feng.dsagent.presentation.PresentationSlide;
import com.feng.dsagent.presentation.SlidePlanner;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Decides which courseware page belongs on screen for a step of a prepared lesson.
 *
 * <p>Resolution order, strongest first: a human correction stored for this script and step, the page the
 * prepared step carries itself, then - for a step that declares its teaching range but no page - the page
 * already on screen, because such a step is deliberately teaching textbook material over that page. Only
 * when a step says nothing about courseware at all does {@link SlidePlanner}'s deterministic alignment
 * take over. Every answer reports why it was chosen, so the classroom can tell "this page is the step's
 * own" from "the screen stayed put" instead of silently drifting.
 */
@Service
public class ClassroomSlideAlignment {

    /** A page to show, with the reason it was chosen. */
    public record StepSlide(String slideId, String slideTitle, String kind, double score, String subLessonId,
                            String subLessonTitle, String scene, String source, String reason) {
    }

    private final PresentationService presentations;
    private final PresentationCatalog catalog;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Map<String, List<SlidePlanner.StepPlan>> planned = new ConcurrentHashMap<>();

    public ClassroomSlideAlignment(PresentationService presentations, PresentationCatalog catalog, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.presentations = presentations;
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Slide for one step; {@code stepIndex < 0} is the opening, which shows the deck's first page. */
    public StepSlide forStep(String scriptId, String scriptJson, String lessonId, int stepIndex, JsonNode step) {
        List<PresentationSlide> deck = deck(lessonId);
        if (deck.isEmpty()) {
            return null;
        }
        String override = override(scriptId, stepIndex);
        if (override != null) {
            PresentationSlide slide = catalog.slide(override);
            if (slide != null && belongs(deck, override)) {
                return new StepSlide(override, label(slide), SlidePlanner.Kind.DIRECT.name(), 1, null, null, null, "override", "pinned");
            }
        }
        if (step != null) {
            List<String> references = new ArrayList<>();
            if (step.path("slideRefs").isArray()) {
                step.path("slideRefs").forEach(reference -> {
                    String value = reference.asText("");
                    if (!value.isBlank() && belongs(deck, value)) {
                        references.add(value);
                    }
                });
            }
            if (!references.isEmpty()) {
                PresentationSlide slide = catalog.slide(references.get(0));
                if (slide != null) {
                    return new StepSlide(slide.id(), label(slide), SlidePlanner.Kind.DIRECT.name(), 1, null, null, null, "script",
                        "script-refs");
                }
            }
            // Declared range, chosen nothing: the step is teaching material the deck has no page for, so
            // the page under discussion stays up rather than the pane jumping somewhere unrelated.
            if (step.path("slideScope").isObject()) {
                String carried = lastShownSlide(scriptJson, stepIndex);
                PresentationSlide slide = carried == null ? null : catalog.slide(carried);
                if (slide != null && belongs(deck, carried)) {
                    return new StepSlide(carried, label(slide), SlidePlanner.Kind.CONTINUITY.name(), 1,
                        step.path("slideScope").path("subLessonId").asText(null),
                        null, step.path("slideScope").path("scene").asText(null), "script", "scope-only");
                }
            }
        }
        if (stepIndex < 0) {
            PresentationSlide first = deck.get(0);
            return new StepSlide(first.id(), label(first), SlidePlanner.Kind.DIRECT.name(), 0, null, null, null, "auto", "opening");
        }
        SlidePlanner.StepPlan plan = plans(scriptId, scriptJson, lessonId).stream()
            .filter(item -> item.stepIndex() == stepIndex)
            .findFirst()
            .orElse(null);
        if (plan == null || plan.slideId() == null) {
            return null;
        }
        return new StepSlide(plan.slideId(), plan.slideTitle(), plan.kind().name(), plan.score(),
            plan.subLessonId(), plan.subLessonTitle(), plan.scene(), "auto", "aligned");
    }

    /**
     * The page the previous steps already put on screen, so a textbook-only step can stay on it. Walks
     * back through the script and returns the most recent page a step carried, if any.
     */
    private String lastShownSlide(String scriptJson, int stepIndex) {
        try {
            JsonNode steps = mapper.readTree(scriptJson).path("steps");
            for (int index = Math.min(stepIndex - 1, steps.size() - 1); index >= 0; index--) {
                for (JsonNode reference : steps.get(index).path("slideRefs")) {
                    String value = reference.asText("");
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (RuntimeException error) {
            return null;
        }
        return null;
    }

    /** Records a human decision for one step of a lesson; it applies to every learner of that lesson. */
    public StepSlide pin(long userId, String scriptId, String scriptJson, String lessonId, int stepIndex, String slideId) {
        List<PresentationSlide> deck = deck(lessonId);
        if (deck.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASSROOM_SLIDES_UNAVAILABLE", "本课时没有配套课件，无法指定页面");
        }
        if (stepIndex < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLASSROOM_SLIDE_STEP_INVALID", "步骤序号无效");
        }
        if (slideId == null || slideId.isBlank() || !belongs(deck, slideId.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLASSROOM_SLIDE_NOT_IN_LESSON", "该页面不属于本课时课件");
        }
        jdbc.update("""
            INSERT INTO classroom_slide_overrides (script_id, step_index, slide_id, updated_by, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE slide_id = VALUES(slide_id), updated_by = VALUES(updated_by), updated_at = CURRENT_TIMESTAMP
            """, scriptId, stepIndex, slideId.trim(), userId);
        return forStep(scriptId, scriptJson, lessonId, stepIndex, null);
    }

    public List<PresentationSlide> deck(String lessonId) {
        if (lessonId == null || lessonId.isBlank()) {
            return List.of();
        }
        return presentations.forLesson(lessonId).slides();
    }

    private boolean belongs(List<PresentationSlide> deck, String slideId) {
        return deck.stream().anyMatch(slide -> slide.id().equals(slideId));
    }

    private String label(PresentationSlide slide) {
        return slide.title().isBlank() ? slide.semanticSummary() : slide.title();
    }

    private String override(String scriptId, int stepIndex) {
        if (stepIndex < 0) {
            return null;
        }
        List<String> values = jdbc.query("SELECT slide_id FROM classroom_slide_overrides WHERE script_id = ? AND step_index = ?",
            (row, index) -> row.getString(1), scriptId, stepIndex);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<SlidePlanner.StepPlan> plans(String scriptId, String scriptJson, String lessonId) {
        return planned.computeIfAbsent(scriptId, key -> compute(scriptJson, lessonId));
    }

    private List<SlidePlanner.StepPlan> compute(String scriptJson, String lessonId) {
        String key = presentations.forLesson(lessonId).coursewareKey();
        List<PresentationCatalog.SubLessonPlan> subLessons = catalog.subLessons(key);
        if (subLessons.isEmpty()) {
            return List.of();
        }
        JsonNode root = mapper.readTree(scriptJson);
        List<SlidePlanner.StepInput> inputs = new ArrayList<>();
        int index = 0;
        for (JsonNode step : root.path("steps")) {
            inputs.add(new SlidePlanner.StepInput(stepText(step), stringList(step.path("keywords"))));
            index++;
        }
        return SlidePlanner.plan(inputs, subLessons, catalog::slide);
    }

    /** Everything the step says, plus the textbook evidence it was built from. */
    private String stepText(JsonNode step) {
        StringBuilder text = new StringBuilder();
        for (String field : List.of("title", "content", "prompt", "evidence", "contentRef")) {
            if (step.path(field).isTextual()) {
                text.append(step.path(field).asText()).append(' ');
            }
        }
        text.append(String.join(" ", stringList(step.path("keywords"))));
        text.append(' ').append(String.join(" ", stringList(step.path("sourcePages"))));
        String value = text.toString();
        return value.length() > 6000 ? value.substring(0, 6000) : value;
    }

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    /** Payload the browser reads for the current step. */
    public ObjectNode describe(StepSlide slide) {
        ObjectNode node = mapper.createObjectNode();
        node.put("slideId", slide.slideId());
        if (slide.slideTitle() != null) {
            node.put("slideTitle", slide.slideTitle());
        }
        node.put("kind", slide.kind());
        node.put("score", slide.score());
        node.put("source", slide.source());
        // Why this page: the classroom says "this page is the step's own" or "the screen stayed put".
        node.put("reason", slide.reason());
        if (slide.subLessonId() != null) {
            node.put("subLessonId", slide.subLessonId());
            node.put("subLessonTitle", slide.subLessonTitle());
        }
        if (slide.scene() != null) {
            node.put("scene", slide.scene());
        }
        return node;
    }
}
