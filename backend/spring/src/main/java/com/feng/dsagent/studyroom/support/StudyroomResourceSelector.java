package com.feng.dsagent.studyroom.support;

import com.feng.dsagent.knowledge.support.KnowledgeText;
import java.util.*;

public final class StudyroomResourceSelector {
    private StudyroomResourceSelector() {}
    public static List<StudyroomResourceCatalog.Candidate> select(List<StudyroomResourceCatalog.Candidate> available, String question, String objective) {
        var stepTokens = KnowledgeText.tokens(objective);
        var questionTokens = KnowledgeText.tokens(question);
        var topics = topics(objective);
        if (topics.isEmpty()) topics = topics(question);
        var focus = topics;
        List<StudyroomResourceCatalog.Candidate> selected = new ArrayList<>();
        for (var type : StudyroomModelOutput.ResourceType.values()) {
            int limit = switch (type) { case PPT -> 12; case ANIMATION -> 4; case C_CODE -> 6; };
            available.stream().filter(c -> c.type() == type)
                .filter(c -> score(c, stepTokens) > 0 || score(c, questionTokens) > 0)
                .collect(java.util.stream.Collectors.toMap(c -> c.type() + ":" + c.id(), c -> c, (a,b) -> a, LinkedHashMap::new))
                .values().stream().sorted(Comparator.<StudyroomResourceCatalog.Candidate>comparingDouble(c ->
                    topicScore(c, focus) * 10000 + 3 * score(c, stepTokens) + score(c, questionTokens)).reversed().thenComparing(StudyroomResourceCatalog.Candidate::id))
                .limit(limit).map(c -> new StudyroomResourceCatalog.Candidate(c.type(), c.id(), c.title(), c.description().substring(0, Math.min(500, c.description().length())), c.allowedParameterKeys())).forEach(selected::add);
        }
        return List.copyOf(selected);
    }
    private static double score(StudyroomResourceCatalog.Candidate c, Set<String> tokens) {
        return 4 * KnowledgeText.bodyScore(c.title(), tokens) + KnowledgeText.bodyScore(c.description(), tokens);
    }

    // Retain the named algorithm when a later goal only says "change the calls". Generic words
    // such as recursion/experiment must not outrank the traversal named in the original question.
    private static List<List<String>> topics(String query) {
        String text = KnowledgeText.normalize(query);
        var aliases = List.of(
            List.of("先序", "前序", "preorder"), List.of("中序", "inorder"), List.of("后序", "postorder"),
            List.of("层序", "levelorder"), List.of("阶乘", "factorial"), List.of("汉诺塔", "hanoi"),
            List.of("快速排序", "快排", "quicksort"), List.of("冒泡排序", "冒泡", "bubble"),
            List.of("归并排序", "归并", "mergesort"), List.of("堆排序", "heapsort"),
            List.of("插入排序", "insertionsort"), List.of("选择排序", "selectionsort"),
            List.of("哈夫曼", "huffman"), List.of("广度优先", "bfs"), List.of("深度优先", "dfs"),
            List.of("dijkstra", "迪杰斯特拉"), List.of("kruskal", "克鲁斯卡尔"), List.of("prim", "普里姆"),
            List.of("avl", "平衡二叉树"), List.of("入队", "enqueue"), List.of("出队", "dequeue"),
            List.of("入栈", "push"), List.of("出栈", "pop"));
        return aliases.stream().filter(group -> group.stream().anyMatch(text::contains)).toList();
    }
    private static long topicScore(StudyroomResourceCatalog.Candidate c, List<List<String>> topics) {
        String text = KnowledgeText.normalize(c.title() + " " + c.description());
        return topics.stream().filter(group -> group.stream().anyMatch(text::contains)).count();
    }
}
