package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.feng.dsagent.studyroom.support.StudyroomModelOutput;
import com.feng.dsagent.studyroom.support.StudyroomResourceCatalog;
import com.feng.dsagent.studyroom.support.StudyroomResourceSelector;
class StudyroomResourceSelectorTest {
    @Test void ranksCodeByStepObjectiveInsteadOfBroadOriginalQuestion() {
        var candidates = List.of(code("inorder", "中序遍历", "左根右"), code("preorder", "先序遍历递归实现", "根左右递归"), code("queue", "队列入队", "入队"));
        var result = StudyroomResourceSelector.select(candidates, "讲讲二叉树", "先序遍历递归实现");
        assertThat(result.getFirst().id()).isEqualTo("preorder");
    }
    @Test void excludesIrrelevantResourcesAndDeduplicates() {
        var candidate = code("queue", "队列入队", "入队操作");
        assertThat(StudyroomResourceSelector.select(List.of(candidate, candidate), "队列", "入队实现")).hasSize(1);
        assertThat(StudyroomResourceSelector.select(List.of(candidate), "二叉树", "先序遍历")).isEmpty();
    }
    @Test void retainsTheAlgorithmTopicForGenericExperimentGoals() {
        var candidates = new java.util.ArrayList<StudyroomResourceCatalog.Candidate>();
        for (int i = 0; i < 8; i++) candidates.add(code("factorial-" + i, "递归调用顺序实现", "修改递归调用顺序，运行实验"));
        candidates.add(code("06-03-s1", "遍历：先序、中序、后序", "三种树遍历"));
        var selected = StudyroomResourceSelector.select(candidates,
            "二叉树先序遍历，用代码修改左右递归调用顺序运行实验", "交换左右递归调用顺序");
        assertThat(selected.getFirst().id()).isEqualTo("06-03-s1");
    }
    private StudyroomResourceCatalog.Candidate code(String id, String title, String description) {
        return new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.C_CODE, id, title, description, List.of());
    }
}
