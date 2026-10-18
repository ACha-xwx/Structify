package com.feng.dsagent.studyroom.curriculum;

import com.feng.dsagent.studyroom.draft.LearningDepth;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Initial teaching checklist; only evidence-backed topics may be presented as available learning
 * scope.
 */
@Component
public class CurriculumCatalog {
    public record Node(String key, String title, LearningDepth minimumDepth) {}

    public record Framework(String topic, String version, List<Node> nodes) {}

    public Optional<Framework> find(String question) {
        if (question.contains("二叉树"))
            return Optional.of(
                    new Framework(
                            "二叉树",
                            "binary-tree-1",
                            List.of(
                                    new Node("tree.definition", "定义与基本术语", LearningDepth.QUICK),
                                    new Node("tree.traversal", "前中后序与层序遍历", LearningDepth.QUICK),
                                    new Node("tree.storage", "顺序与链式存储", LearningDepth.STANDARD),
                                    new Node(
                                            "tree.implementation",
                                            "遍历代码与复杂度",
                                            LearningDepth.STANDARD),
                                    new Node("tree.boundaries", "空树、单结点和退化树", LearningDepth.DEEP),
                                    new Node("tree.applications", "构造与应用练习", LearningDepth.DEEP))));
        if (question.contains("队列"))
            return Optional.of(
                    new Framework(
                            "队列",
                            "queue-1",
                            List.of(
                                    new Node("queue.definition", "先进先出与基本操作", LearningDepth.QUICK),
                                    new Node("queue.operations", "入队出队过程", LearningDepth.QUICK),
                                    new Node("queue.storage", "顺序队列与链队列", LearningDepth.STANDARD),
                                    new Node("queue.circular", "循环队列与判空判满", LearningDepth.STANDARD),
                                    new Node(
                                            "queue.implementation",
                                            "实现与复杂度",
                                            LearningDepth.STANDARD),
                                    new Node("queue.boundaries", "边界测试与应用", LearningDepth.DEEP))));
        if (question.contains("栈"))
            return Optional.of(
                    new Framework(
                            "栈",
                            "stack-1",
                            List.of(
                                    new Node("stack.definition", "后进先出与基本术语", LearningDepth.QUICK),
                                    new Node("stack.operations", "入栈与出栈过程", LearningDepth.QUICK),
                                    new Node("stack.storage", "顺序栈与链栈", LearningDepth.STANDARD),
                                    new Node(
                                            "stack.implementation",
                                            "实现与复杂度",
                                            LearningDepth.STANDARD),
                                    new Node("stack.boundaries", "空栈、满栈与边界验证", LearningDepth.DEEP),
                                    new Node(
                                            "stack.applications",
                                            "括号匹配、表达式与递归",
                                            LearningDepth.DEEP))));
        return Optional.empty();
    }

    public List<Node> recommended(Framework framework, LearningDepth depth) {
        return framework.nodes().stream()
                .filter(n -> n.minimumDepth().ordinal() <= depth.ordinal())
                .toList();
    }
}
