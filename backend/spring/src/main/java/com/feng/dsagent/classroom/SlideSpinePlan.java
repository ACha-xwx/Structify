package com.feng.dsagent.classroom;

import com.feng.dsagent.knowledge.TextbookSections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds the teaching spine of one lesson out of its courseware.
 *
 * Every courseware page becomes exactly one teaching step, in the order the deck was authored, and the
 * textbook supplies the depth behind that page. Because a step is created together with its page, the
 * pairing cannot drift: the classroom never has to guess which slide a piece of teaching belongs to.
 * Reviewed textbook material that has no page of its own - a worked example or a derivation - is kept as
 * an extension step that deliberately carries no page, so the screen stays on the page being discussed.
 */
public final class SlideSpinePlan {

    /** A courseware page already resolved to the teaching beat it belongs to. */
    public record Slide(String id, int number, String title, String body, String summary, String role,
                        String section, String subLessonId, String subLessonTitle, String scene,
                        List<String> terms) {
    }

    public record Built(ObjectNode plan, List<String> notices) {
    }

    private static final int MAX_EXTENSION_STEPS = 4;
    private static final int NARRATION_LIMIT = 360;
    private static final int EXTENSION_LIMIT = 300;
    private static final Pattern WORKED_EXAMPLE = Pattern.compile(
        "例\\s*\\d+\\.\\d+|【算法描述】|【算法思想】|【算法分析】|【分析】|解："
    );

    private SlideSpinePlan() {
    }

    public static Built build(ObjectMapper mapper, String lessonId, String lessonTitle, String pages,
                              List<Slide> slides, LessonPassageIndex textbook) {
        List<String> notices = new ArrayList<>();
        if (slides.isEmpty()) {
            throw new IllegalArgumentException("本课时没有课件页，无法按课件主线备课");
        }

        List<ObjectNode> stepNodes = new ArrayList<>();
        List<String> stepSections = new ArrayList<>();
        Set<String> usedPassageIds = new LinkedHashSet<>();

        for (Slide slide : slides) {
            LessonPassageIndex.Evidence evidence = textbook.evidence(slide.section(), query(slide), 2);
            ObjectNode step = slideStep(mapper, slide, evidence);
            stepNodes.add(step);
            stepSections.add(TextbookSections.number(slide.section()));
            evidence.passages().forEach(passage -> usedPassageIds.add(passage.id()));
        }

        // Textbook examples and derivations the deck does not page for: kept, but placed next to the
        // courseware they belong with and never given a page of their own.
        for (LessonPassageIndex.Passage passage : extensionPassages(textbook, slides, notices)) {
            // A page whose section cannot be read goes to the end rather than in front of the lesson.
            String section = sectionNumberOf(passage);
            int at = section.isBlank() ? stepNodes.size() : insertionPoint(stepSections, section);
            stepNodes.add(at, extensionStep(mapper, passage, at > 0 ? stepNodes.get(at - 1) : null));
            stepSections.add(at, sectionNumberOf(passage));
            usedPassageIds.add(passage.id());
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("lessonId", lessonId);
        root.put("title", cleanTitle(lessonTitle));
        root.put("textbookPages", pages == null ? "" : pages);
        ArrayNode objectives = root.putArray("objectives");
        slides.stream().map(Slide::subLessonTitle).filter(title -> !title.isBlank()).distinct().limit(12)
            .forEach(objectives::add);
        ArrayNode steps = root.putArray("steps");
        stepNodes.forEach(steps::add);
        ArrayNode sources = root.putArray("textbookSources");
        textbook.passages().stream().filter(passage -> usedPassageIds.contains(passage.id())).forEach(passage -> {
            ObjectNode source = sources.addObject();
            source.put("id", passage.id());
            source.put("page_label", passage.pageLabel());
            source.put("content", passage.text());
        });
        root.put("spine", "courseware");
        return new Built(root, notices);
    }

    /**
     * The page steps of one part, for a caller that assembles a lesson part by part - the fallback used when
     * a model answer for that part was rejected. Extensions are only added for the final part, because the
     * steps after them belong to the next part of the deck.
     */
    public static List<ObjectNode> steps(ObjectMapper mapper, List<Slide> slides, LessonPassageIndex textbook,
                                         boolean withExtensions) {
        List<ObjectNode> steps = new ArrayList<>();
        for (Slide slide : slides) {
            steps.add(slideStep(mapper, slide, textbook.evidence(slide.section(), query(slide), 2)));
        }
        if (withExtensions && !steps.isEmpty()) {
            for (LessonPassageIndex.Passage passage : extensionPassages(textbook, slides, new ArrayList<>())) {
                steps.add(extensionStep(mapper, passage, steps.get(steps.size() - 1)));
            }
        }
        return steps;
    }

    /**
     * How precisely the textbook backs one courseware page: its own section, the parent section, or only
     * the lesson as a whole. Shared by both narration paths so a model-written step reports the same
     * textbook provenance a locally assembled one does; without it the classroom cannot tell the learner
     * whether the wording comes from the page's own section or from a wider part of the book.
     */
    public static String textbookMatch(LessonPassageIndex textbook, Slide slide) {
        return textbook.evidence(slide.section(), query(slide), 2).level().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static ObjectNode slideStep(ObjectMapper mapper, Slide slide, LessonPassageIndex.Evidence evidence) {
        ObjectNode step = mapper.createObjectNode();
        boolean closing = slide.role().contains("小结") || slide.role().contains("总结");
        step.put("type", closing ? "summary" : "explain");
        step.put("role", "teacher");
        step.put("content", narration(slide, evidence));
        applyKeywords(step, slide.terms());
        ObjectNode scope = step.putObject("slideScope");
        scope.put("subLessonId", slide.subLessonId());
        scope.put("scene", slide.scene());
        step.putArray("slideRefs").add(slide.id());
        step.put("slideTitle", slide.title().isBlank() ? slide.summary() : slide.title());
        step.put("slidePage", slide.number());
        step.put("textbookMatch", evidence.level().name().toLowerCase(java.util.Locale.ROOT));
        applyEvidence(mapper, step, evidence);
        return step;
    }

    private static ObjectNode extensionStep(ObjectMapper mapper, LessonPassageIndex.Passage passage, ObjectNode previous) {
        ObjectNode step = mapper.createObjectNode();
        step.put("type", "explain");
        step.put("role", "teacher");
        step.put("content", shorten("教材延伸（" + passage.pageLabel() + "）：" + plain(passage.text()), EXTENSION_LIMIT));
        applyKeywords(step, List.of("教材延伸", passage.pageLabel()));
        // Keeping the previous range without naming a page is what tells the classroom to leave the
        // current courseware page on screen while this textbook material is being taught.
        if (previous != null && previous.path("slideScope").isObject()) {
            step.set("slideScope", previous.path("slideScope").deepCopy());
        }
        step.put("textbookMatch", "extension");
        ArrayNode ids = step.putArray("sourceChunkIds");
        ids.add(passage.id());
        step.put("evidence", passage.text());
        step.putArray("sourcePages").add(passage.pageLabel());
        return step;
    }

    private static void applyKeywords(ObjectNode step, List<String> terms) {
        ArrayNode keywords = step.putArray("keywords");
        terms.stream().filter(term -> term != null && !term.isBlank()).limit(4).forEach(keywords::add);
    }

    private static void applyEvidence(ObjectMapper mapper, ObjectNode step, LessonPassageIndex.Evidence evidence) {
        ArrayNode ids = step.putArray("sourceChunkIds");
        ArrayNode pages = step.putArray("sourcePages");
        StringBuilder text = new StringBuilder();
        for (LessonPassageIndex.Passage passage : evidence.passages()) {
            ids.add(passage.id());
            pages.add(passage.pageLabel());
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(passage.text());
        }
        step.put("evidence", text.toString());
    }

    /**
     * Textbook material of this lesson that no courseware page covers, limited to the pages that really
     * carry a worked example or an algorithm description and to a handful per lesson.
     */
    private static List<LessonPassageIndex.Passage> extensionPassages(LessonPassageIndex textbook,
                                                                     List<Slide> slides, List<String> notices) {
        // A worked example only counts as covered when the deck really has an exercise page in that
        // section. A deck that defines the topic but never practises it leaves the textbook example to
        // the classroom, which is exactly the case this keeps.
        Set<String> practisedSections = new LinkedHashSet<>();
        for (Slide slide : slides) {
            if (isExerciseRole(slide.role())) {
                String number = TextbookSections.number(slide.section());
                if (!number.isBlank()) {
                    practisedSections.add(number);
                }
            }
        }
        List<LessonPassageIndex.Passage> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (LessonPassageIndex.Passage passage : textbook.passages()) {
            List<String> sections = new ArrayList<>(passage.sectionsOnPage());
            if (sections.isEmpty() && !passage.section().isBlank()) {
                sections.add(passage.section());
            }
            if (sections.isEmpty()) {
                continue;
            }
            boolean covered = sections.stream()
                .anyMatch(section -> practisedSections.stream().anyMatch(scope -> TextbookSections.under(section, scope)));
            if (covered || !WORKED_EXAMPLE.matcher(passage.text()).find()) {
                continue;
            }
            if (!seen.add(sections.get(0))) {
                continue;
            }
            if (result.size() >= MAX_EXTENSION_STEPS) {
                notices.add("教材延伸步骤已达上限 " + MAX_EXTENSION_STEPS + "，其余教材内容未加入课堂");
                break;
            }
            result.add(passage);
        }
        return result;
    }

    /** True for pages whose job is to practise: an example, an exercise or a test. */
    private static boolean isExerciseRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        return role.contains("示例") || role.contains("练习") || role.contains("测试") || role.contains("例");
    }

    /** Index just after the last step whose section precedes the given section, or 0 when none does. */
    private static int insertionPoint(List<String> stepSections, String section) {
        int at = 0;
        for (int index = 0; index < stepSections.size(); index++) {
            String candidate = stepSections.get(index);
            if (!candidate.isBlank() && compareSections(candidate, section) <= 0) {
                at = index + 1;
            }
        }
        return at;
    }

    private static String sectionNumberOf(LessonPassageIndex.Passage passage) {
        if (!passage.sectionsOnPage().isEmpty()) {
            return passage.sectionsOnPage().get(0);
        }
        return passage.section();
    }

    private static int compareSections(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int valueA = index < a.length ? parseComponent(a[index]) : 0;
            int valueB = index < b.length ? parseComponent(b[index]) : 0;
            if (valueA != valueB) {
                return Integer.compare(valueA, valueB);
            }
        }
        return 0;
    }

    private static int parseComponent(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static String query(Slide slide) {
        return String.join(" ", slide.title(), slide.summary(), slide.body(),
            slide.section(), String.join(" ", slide.terms()));
    }

    /**
     * Draft narration for one page: what the page is, followed by the reviewed textbook wording that backs
     * it. Deliberately plain - the model rewrites this into a teacher's voice when narration is enabled.
     */
    private static String narration(Slide slide, LessonPassageIndex.Evidence evidence) {
        String subject = slide.title().isBlank() ? slide.summary() : slide.title();
        StringBuilder narration = new StringBuilder("本页是").append(shorten(subject, 40)).append("。");
        String quoted = evidence.present() ? plain(evidence.passages().get(0).text()) : "";
        if (!quoted.isBlank()) {
            narration.append("教材（").append(evidence.passages().get(0).pageLabel()).append("）的表述：").append(quoted);
        } else {
            String body = plain(slide.body());
            narration.append(body.isBlank() ? shorten(slide.summary(), 80) : "本页要点：" + body);
        }
        return shorten(narration.toString(), NARRATION_LIMIT);
    }

    /** Drops layout markers and OCR/annotation lines so only the book's own wording is quoted. */
    static String plain(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(">") || trimmed.startsWith("```")
                || trimmed.startsWith("<!--") || trimmed.startsWith("|")) {
                continue;
            }
            trimmed = trimmed.replaceFirst("^[*-]\\s+", "").replaceFirst("^\\d+\\.\\s+", "");
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(trimmed);
        }
        return result.toString().replaceAll("\\s+", " ").strip();
    }

    private static String cleanTitle(String title) {
        String cleaned = title == null ? "" : title.strip();
        int cut = cleaned.indexOf('（');
        if (cut > 0) {
            cleaned = cleaned.substring(0, cut).strip();
        }
        return cleaned.isBlank() ? "课堂" : cleaned;
    }

    private static String shorten(String text, int limit) {
        String value = text == null ? "" : text.strip();
        return value.codePointCount(0, value.length()) <= limit
            ? value
            : value.substring(0, value.offsetByCodePoints(0, limit)).strip() + "…";
    }
}
