package com.feng.dsagent.classroom;

import com.feng.dsagent.knowledge.KnowledgeText;
import com.feng.dsagent.knowledge.TextbookSections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One lesson's reviewed textbook, ordered by page and tagged with the section each page sits under.
 *
 * A courseware page knows its section number (8.3.4 …) and the textbook uses the same numbering, so the
 * textbook can be narrowed to that section before anything is ranked. Narrowing first is what makes the
 * retrieved passage the one the courseware page is actually about, instead of a keyword coincidence.
 */
public final class LessonPassageIndex {

    public enum Level {
        /** The passage sits in the very section the courseware page declares. */
        EXACT,
        /** The declared section is absent from this lesson, so its parent section was used. */
        PARENT,
        /** Only the lesson as a whole could be used. */
        CHAPTER
    }

    public record Passage(String id, int page, String section, List<String> sectionsOnPage, String text) {

        public String pageLabel() {
            return "第 " + page + " 页";
        }

        /** True when this page belongs to the section scope, or introduces it partway through. */
        public boolean under(String scope) {
            if (scope == null || scope.isBlank()) {
                return true;
            }
            if (TextbookSections.under(section, scope)) {
                return true;
            }
            for (String onPage : sectionsOnPage) {
                if (TextbookSections.under(onPage, scope)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record Evidence(Level level, String scope, List<Passage> passages) {
        public boolean present() {
            return !passages.isEmpty();
        }

        public static Evidence none() {
            return new Evidence(Level.CHAPTER, "", List.of());
        }
    }

    private record Scored(Passage passage, double score) {
    }

    private final List<Passage> passages;

    private LessonPassageIndex(List<Passage> passages) {
        this.passages = List.copyOf(passages);
    }

    public List<Passage> passages() {
        return passages;
    }

    /**
     * Builds the index from {@code id / content / page_label} rows. Rows are re-sorted by page because the
     * stored chunk ids are hashes, so reading order cannot be recovered from the database alone.
     */
    public static LessonPassageIndex of(List<Map<String, Object>> rows) {
        List<Map<String, Object>> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparingInt(LessonPassageIndex::pageOf));
        List<Passage> result = new ArrayList<>();
        String carried = "";
        for (Map<String, Object> row : ordered) {
            String text = row.get("content") == null ? "" : String.valueOf(row.get("content"));
            List<String> onPage = TextbookSections.spans(text).stream()
                .map(span -> TextbookSections.number(span.section()))
                .filter(number -> !number.isBlank())
                .distinct()
                .toList();
            result.add(new Passage(
                String.valueOf(row.get("id")),
                pageOf(row),
                carried,
                onPage,
                text
            ));
            if (!onPage.isEmpty()) {
                carried = onPage.get(onPage.size() - 1);
            }
        }
        return new LessonPassageIndex(result);
    }

    /**
     * Best passages for one courseware page: the declared section first, then its parent section, and only
     * then the whole lesson. The level is reported so the classroom can say how precise the match was.
     */
    public Evidence evidence(String slideSection, String query, int limit) {
        if (passages.isEmpty()) {
            return Evidence.none();
        }
        int bounded = Math.max(1, limit);
        Set<String> tokens = KnowledgeText.tokens(query == null ? "" : query);
        String scope = TextbookSections.number(slideSection);
        if (!scope.isBlank()) {
            List<Passage> exact = passages.stream().filter(passage -> passage.under(scope)).toList();
            if (!exact.isEmpty()) {
                return new Evidence(Level.EXACT, scope, rank(exact, tokens, bounded));
            }
            String parent = TextbookSections.parent(scope);
            if (!parent.isBlank()) {
                List<Passage> wider = passages.stream().filter(passage -> passage.under(parent)).toList();
                if (!wider.isEmpty()) {
                    return new Evidence(Level.PARENT, parent, rank(wider, tokens, bounded));
                }
            }
        }
        return new Evidence(Level.CHAPTER, "", rank(passages, tokens, bounded));
    }

    private List<Passage> rank(List<Passage> candidates, Set<String> tokens, int limit) {
        return candidates.stream()
            .map(passage -> new Scored(passage, score(passage, tokens)))
            .sorted(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparingInt(scored -> scored.passage().page()))
            .limit(limit)
            .map(Scored::passage)
            .toList();
    }

    /**
     * Occurrence-weighted overlap scaled by how many distinct query terms the passage actually mentions.
     * The coverage factor is what stops a long page from winning on repetition alone.
     */
    private static double score(Passage passage, Set<String> tokens) {
        if (tokens.isEmpty()) {
            return 0;
        }
        return KnowledgeText.bodyScore(passage.text(), tokens) * (0.5 + KnowledgeText.coverage(passage.text(), tokens));
    }

    /** Page number of a row, read from its label; rows without a usable label sort first. */
    private static int pageOf(Map<String, Object> row) {
        Object label = row.get("page_label");
        if (label == null) {
            return 0;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(String.valueOf(label));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
