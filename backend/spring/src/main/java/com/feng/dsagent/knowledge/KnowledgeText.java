package com.feng.dsagent.knowledge;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tokenisation and scoring shared by the knowledge search and the classroom's section-scoped retrieval.
 * Keeping one implementation means a passage ranks the same way whichever route asked for it, and the
 * classroom never sees a different ordering than the search box does.
 */
public final class KnowledgeText {

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9\\p{IsHan}]+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> DOMAIN_TERMS = Set.of(
        "数据结构", "算法", "复杂度", "线性表", "顺序表", "链表", "单链表", "双向链表",
        "栈", "队列", "二叉树", "树", "遍历", "前序", "中序", "后序", "层序", "图",
        "查找", "排序", "哈希", "堆", "指针", "结点", "节点", "入栈", "出栈", "头插", "尾插"
    );

    private KnowledgeText() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** Domain words first, then latin words and CJK n-grams, mirroring what the search endpoint uses. */
    public static Set<String> tokens(String text) {
        String normalized = normalize(text);
        Set<String> result = new LinkedHashSet<>();
        for (String term : DOMAIN_TERMS) {
            if (normalized.contains(term)) {
                result.add(term);
            }
        }
        for (String part : NON_WORD.split(normalized)) {
            if (part.isBlank()) {
                continue;
            }
            if (part.matches("[a-z0-9]+")) {
                if (part.length() >= 2) {
                    result.add(part);
                }
                continue;
            }
            for (int size = 2; size <= 4; size++) {
                for (int index = 0; index + size <= part.length(); index++) {
                    result.add(part.substring(index, index + size));
                }
            }
        }
        return result;
    }

    public static int countOccurrences(String text, String token) {
        if (text == null || text.isEmpty() || token.isEmpty()) {
            return 0;
        }
        int count = 0;
        int start = 0;
        while (count < 4) {
            int index = text.indexOf(token, start);
            if (index < 0) {
                break;
            }
            count++;
            start = index + token.length();
        }
        return count;
    }

    /**
     * How often the query tokens occur in the given text, with longer tokens trusted more. Used for a
     * chunk's body; the search endpoint adds its own heavier weighting for the title.
     */
    public static double bodyScore(String text, Set<String> queryTokens) {
        String normalized = normalize(text);
        double score = 0;
        for (String token : queryTokens) {
            int hits = countOccurrences(normalized, token);
            score += hits * (token.length() >= 3 ? 2.5 : 1.5);
        }
        return score;
    }

    /** Share of the query tokens that the text mentions at least once, in the range 0 to 1. */
    public static double coverage(String text, Set<String> queryTokens) {
        if (queryTokens.isEmpty() || text == null || text.isBlank()) {
            return 0;
        }
        String normalized = normalize(text);
        int hit = 0;
        for (String token : queryTokens) {
            if (normalized.contains(token)) {
                hit++;
            }
        }
        return (double) hit / queryTokens.size();
    }
}
