package com.feng.dsagent.classroom.support;


import org.springframework.stereotype.Service;
import com.feng.dsagent.classroom.mapper.ClassroomMapper;
import com.feng.dsagent.classroom.error.ClassroomErrorCode;
import com.feng.dsagent.presentation.support.PresentationCatalog;
import com.feng.dsagent.presentation.service.PresentationService;
import com.feng.dsagent.presentation.domain.PresentationSlide;
import com.feng.dsagent.presentation.support.SlidePlanner;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ClassroomSlideAlignment {

    /** How much of a page's own words travel with a mid-lesson question; a page can be a whole slide of text. */
    private static final int PAGE_TEXT_LIMIT = 1500;

    /** A page to show, with the reason it was chosen. */
    public record StepSlide(String slideId, String slideTitle, String kind, double score, String subLessonId,
                            String subLessonTitle, String scene, String source, String reason) {
    }

    private final PresentationService presentations;
    private final PresentationCatalog catalog;
    private final ClassroomMapper classroom;
    private final ObjectMapper mapper;
    private final Map<String, List<SlidePlanner.StepPlan>> planned = new ConcurrentHashMap<>();

    public ClassroomSlideAlignment(
        PresentationService presentations,
        PresentationCatalog catalog,
        ClassroomMapper classroom,
        ObjectMapper mapper
    ) {
        this.presentations = presentations;
        this.catalog = catalog;
        this.classroom = classroom;
        this.mapper = mapper;
    }

    /**
     * The words of the page a step shows, so a question asked mid-lesson can be answered from the screen
     * the learner is looking at - the page itself, not only the narration written about it.
     */
    public String pageText(String scriptId, String scriptJson, String lessonId, int stepIndex, JsonNode step) {
        StepSlide aligned = forStep(scriptId, scriptJson, lessonId, stepIndex, step);
        if (aligned == null) return "";
        PresentationSlide slide = catalog.slide(aligned.slideId());
        if (slide == null) return "";
        StringBuilder text = new StringBuilder();
        appended(text, slide.title());
        appended(text, slide.semanticSummary());
        appended(text, slide.rawText());
        return text.length() <= PAGE_TEXT_LIMIT ? text.toString() : text.substring(0, PAGE_TEXT_LIMIT);
    }

    private static void appended(StringBuilder text, String value) {
        if (value == null || value.isBlank()) return;
        if (text.length() > 0) text.append('\n');
        text.append(value.strip());
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
            throw ClassroomErrorCode.SLIDES_UNAVAILABLE.toException();
        }
        if (stepIndex < 0) {
            throw ClassroomErrorCode.SLIDE_STEP_INVALID.toException();
        }
        if (slideId == null || slideId.isBlank() || !belongs(deck, slideId.trim())) {
            throw ClassroomErrorCode.SLIDE_NOT_IN_LESSON.toException();
        }
        classroom.upsertSlideOverride(scriptId, stepIndex, slideId.trim(), userId);
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
        return classroom.selectSlideOverride(scriptId, stepIndex);
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
