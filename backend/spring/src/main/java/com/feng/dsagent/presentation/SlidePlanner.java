package com.feng.dsagent.presentation;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Places each teaching step on the courseware that was really rendered for the lesson.
 *
 * <p>The decks are the teacher's own slides: they follow the same sub-lesson structure as the reviewed
 * textbook (for example {@code 08-02A 二叉排序树的查找与插入} versus {@code 08-02B 二叉排序树的删除与性能}),
 * but they rarely contain a page for every textbook example. So the planner works in two stages: it
 * first splits the step stream into the ordered sub-lessons of the deck, then walks that sub-lesson's
 * scenes. When a step has no page of its own the pane keeps the current page ({@link Kind#CONTINUITY})
 * instead of jumping to an unrelated one, and a step whose sub-lesson has no page at all reports
 * {@link Kind#NONE} so the interface can say so.
 */
public final class SlidePlanner {

    /** How a step relates to the page that should be on screen. */
    public enum Kind {
        /** The deck has a page for this step's topic. */
        DIRECT,
        /** The deck has no page for this step; the previous page stays on screen. */
        CONTINUITY,
        /** Nothing from this lesson's deck applies to this step. */
        NONE
    }

    public record StepInput(String text, List<String> keywords) {
    }

    public record StepPlan(int stepIndex, String slideId, String slideTitle, Kind kind, double score,
                           String subLessonId, String subLessonTitle, String scene, int sceneIndex) {
        public static StepPlan none(int stepIndex) {
            return new StepPlan(stepIndex, null, null, Kind.NONE, 0, null, null, null, -1);
        }
    }

    /** Text similarity below this adds nothing; used to decide whether a page really belongs to a step. */
    private static final double SCENE_FLOOR = 0.09;
    private static final Pattern SECTION = Pattern.compile("(?<![\\d.])(\\d{1,2}\\.\\d{1,2}(?:\\.\\d{1,2})?)(?![\\d.])");
    private static final Pattern CJK = Pattern.compile("[\\u3400-\\u9fff]");
    private static final Pattern LATIN = Pattern.compile("[a-z0-9_]{2,}");

    private SlidePlanner() {
    }

    public static List<StepPlan> plan(List<StepInput> steps, List<PresentationCatalog.SubLessonPlan> subLessons, java.util.function.Function<String, PresentationSlide> slides) {
        if (steps.isEmpty()) {
            return List.of();
        }
        if (subLessons.isEmpty()) {
            List<StepPlan> empty = new ArrayList<>();
            for (int index = 0; index < steps.size(); index++) {
                empty.add(StepPlan.none(index));
            }
            return List.copyOf(empty);
        }
        List<List<List<Set<String>>>> sceneSlides = new ArrayList<>();
        List<Set<String>> profileTokens = new ArrayList<>();
        List<Set<String>> titleTokens = new ArrayList<>();
        for (PresentationCatalog.SubLessonPlan subLesson : subLessons) {
            List<List<Set<String>>> perScene = new ArrayList<>();
            StringBuilder all = new StringBuilder(subLesson.title());
            for (PresentationCatalog.ScenePlan scene : subLesson.scenes()) {
                List<Set<String>> perSlide = new ArrayList<>();
                for (String slideId : scene.slideIds()) {
                    PresentationSlide slide = slides.apply(slideId);
                    String text = slide == null ? "" : slideText(slide);
                    perSlide.add(tokens(text));
                    all.append(' ').append(text);
                }
                perScene.add(perSlide);
            }
            sceneSlides.add(perScene);
            profileTokens.add(tokens(all.toString()));
            titleTokens.add(tokens(subLesson.title()));
        }

        // Stage 1 — which sub-lesson is the lecture in? Titles such as 查找与插入 / 删除与性能 separate cleanly.
        int[] assignment = new int[steps.size()];
        for (int index = 0; index < steps.size(); index++) {
            Set<String> query = tokens(steps.get(index).text());
            int best = 0;
            double bestScore = -1;
            for (int candidate = 0; candidate < subLessons.size(); candidate++) {
                double score = 0.6 * cosine(query, profileTokens.get(candidate)) + 0.4 * cosine(query, titleTokens.get(candidate));
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            assignment[index] = best;
        }
        // Sub-lessons are taught in order, so the assignment may only move forward; the majority vote
        // removes the occasional paragraph that resembles the next sub-lesson early.
        int highest = 0;
        for (int index = 0; index < steps.size(); index++) {
            assignment[index] = Math.max(highest, majority(assignment, index, subLessons.size()));
            highest = assignment[index];
        }

        List<StepPlan> plans = new ArrayList<>(steps.size());
        for (int index = 0; index < steps.size(); index++) {
            PresentationCatalog.SubLessonPlan subLesson = subLessons.get(assignment[index]);
            List<List<Set<String>>> candidates = sceneSlides.get(assignment[index]);
            Set<String> query = tokens(steps.get(index).text());
            boolean opensSubLesson = index == 0 || assignment[index - 1] != assignment[index];
            int previousScene = opensSubLesson ? 0 : previousScene(plans, index);
            int bestScene = previousScene;
            double bestScore = -1;
            // A scene is judged by its best page: merging every page of a rich scene into one bag would
            // dilute the match and push the pane towards whichever scene has the fewest slides.
            for (int scene = previousScene; scene < candidates.size(); scene++) {
                for (Set<String> signal : candidates.get(scene)) {
                    double score = cosine(query, signal);
                    if (score > bestScore) {
                        bestScore = score;
                        bestScene = scene;
                    }
                }
            }
            // A page only earns its place by opening the sub-lesson or by really matching what is said;
            // everything else keeps the current page instead of drifting to an unrelated one.
            boolean strong = opensSubLesson || bestScore >= SCENE_FLOOR;
            int chosen = strong ? bestScene : previousScene;
            if (candidates.isEmpty() || chosen < 0 || chosen >= candidates.size()) {
                plans.add(StepPlan.none(index));
                continue;
            }
            PresentationCatalog.ScenePlan scene = subLesson.scenes().get(chosen);
            String slideId = bestSlide(scene, candidates.get(chosen), query);
            PresentationSlide slide = slideId == null ? null : slides.apply(slideId);
            if (slideId == null) {
                plans.add(StepPlan.none(index));
            } else {
                plans.add(new StepPlan(index, slideId, slide == null ? null : (slide.title().isBlank() ? slide.semanticSummary() : slide.title()),
                    strong ? Kind.DIRECT : Kind.CONTINUITY, Math.max(bestScore, 0), subLesson.lessonId(), subLesson.title(), scene.key(), chosen));
            }
        }
        return List.copyOf(plans);
    }

    /** The page of a scene that matches the step best, falling back to the scene's first page. */
    private static String bestSlide(PresentationCatalog.ScenePlan scene, List<Set<String>> signals, Set<String> query) {
        if (scene.slideIds().isEmpty()) {
            return null;
        }
        int best = 0;
        double bestScore = -1;
        for (int index = 0; index < signals.size() && index < scene.slideIds().size(); index++) {
            double score = cosine(query, signals.get(index));
            if (score > bestScore) {
                bestScore = score;
                best = index;
            }
        }
        return scene.slideIds().get(best);
    }

    /** Text of one page: title and annotation first, then the fuller summary and concepts. */
    private static String slideText(PresentationSlide slide) {
        return slide.title() + " " + slide.semanticSummary() + " " + slide.teachingFocus() + " "
            + String.join(" ", slide.concepts()) + " " + slide.section() + " " + slide.role() + " "
            + String.join(" ", slide.terms());
    }

    /** Scene already on screen for the previous step of the same sub-lesson, or 0 when the block opens. */
    private static int previousScene(List<StepPlan> plans, int index) {
        for (int cursor = index - 1; cursor >= 0; cursor--) {
            StepPlan plan = plans.get(cursor);
            if (plan.kind() == Kind.NONE) {
                return 0;
            }
            return Math.max(0, plan.sceneIndex());
        }
        return 0;
    }

    /** Majority label in a small window, so one stray paragraph cannot move the lecture to another part. */
    private static int majority(int[] assignment, int index, int candidates) {
        int from = Math.max(0, index - 1);
        int to = Math.min(assignment.length - 1, index + 1);
        int best = assignment[index];
        int bestCount = 0;
        for (int cursor = from; cursor <= to; cursor++) {
            if (assignment[cursor] == best) {
                bestCount++;
            }
        }
        for (int candidate = 0; candidate < candidates; candidate++) {
            if (candidate == best) {
                continue;
            }
            int count = 0;
            for (int cursor = from; cursor <= to; cursor++) {
                if (assignment[cursor] == candidate) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = candidate;
            }
        }
        return best;
    }

    static Set<String> tokens(String text) {
        String clean = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> result = new HashSet<>();
        var latin = LATIN.matcher(clean);
        while (latin.find()) {
            result.add(latin.group());
        }
        StringBuilder cjk = new StringBuilder();
        for (int index = 0; index < clean.length(); index++) {
            if (CJK.matcher(String.valueOf(clean.charAt(index))).matches()) {
                cjk.append(clean.charAt(index));
            }
        }
        for (int index = 0; index + 1 < cjk.length(); index++) {
            result.add(cjk.substring(index, index + 2));
        }
        return result;
    }

    static double cosine(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int shared = 0;
        for (String token : left) {
            if (right.contains(token)) {
                shared++;
            }
        }
        return shared / Math.sqrt((double) left.size() * right.size());
    }

    /** Section numbers ("8.3.4") shared by the textbook and the deck, used by tests and callers. */
    public static Set<String> sections(String text) {
        Set<String> result = new HashSet<>();
        var matcher = SECTION.matcher(text == null ? "" : text);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }
}
