package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StudyroomSceneBinderTest {
    @Test void permitsSharedNeighborPagesButNeverUnlistedPages() {
        var plan = new StudyroomLessonPlanner.Plan(2, "标题", "摘要", List.of(), List.of(
            new StudyroomLessonPlanner.Goal("s1", "规则", "理解规则", "CONCEPT"), new StudyroomLessonPlanner.Goal("s2", "例子", "理解例子", "CONCEPT")));
        var mapper = new ObjectMapper();
        String raw = """
            {"version":1,"title":"标题","summary":"摘要","evidenceIds":[],"steps":[{"id":"s1","type":"ppt","title":"规则","explanation":"讲解","resources":[{"type":"ppt","id":"deck-s021","parameters":{}}]},{"id":"s2","type":"ppt","title":"例子","explanation":"讲解","resources":[{"type":"ppt","id":"deck-s021","parameters":{}}]}]}
            """;
        var output = StudyroomModelOutput.parse(mapper, StudyroomSceneBinder.normalize(mapper, raw, plan));
        var first = new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.PPT, "deck-s020", "定义", "", List.of());
        var neighbor = new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.PPT, "deck-s021", "例子", "", List.of());
        assertThatCode(() -> StudyroomSceneBinder.validate(output, plan, java.util.Map.of("s1", List.of(first), "s2", List.of(neighbor)), false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> StudyroomSceneBinder.validate(output, plan, java.util.Map.of("s1", List.of(first), "s2", List.of(first)), false)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void usesCatalogDefaultButPreservesTaskSpecificInput() {
        var mapper = new ObjectMapper();
        var plan = new StudyroomLessonPlanner.Plan(2, "标题", "摘要", List.of(), List.of(new StudyroomLessonPlanner.Goal("s1", "实验", "修改递归顺序", "EXPERIMENT")));
        String raw = """
            {"version":1,"title":"标题","summary":"摘要","evidenceIds":[],"steps":[{"id":"s1","type":"c_code","title":"实验","explanation":"实验说明","resources":[{"type":"c_code","id":"code","parameters":{}}],"codeMode":"EXPERIMENT","task":"交换左右调用","expectedObservation":"观察顺序"}]}
            """;
        var result = StudyroomModelOutput.parse(mapper, StudyroomSceneBinder.normalize(mapper, raw, plan, java.util.Map.of("code", "A..")));
        assertThat(result.steps().getFirst().orchestration().primary().stdin()).isEqualTo("A..");
        String custom = raw.replace("\"task\":\"交换左右调用\"", "\"task\":\"验证空树\",\"stdin\":\".\"");
        var emptyTree = StudyroomModelOutput.parse(mapper, StudyroomSceneBinder.normalize(mapper, custom, plan, java.util.Map.of("code", "A..")));
        assertThat(emptyTree.steps().getFirst().orchestration().primary().stdin()).isEqualTo(".");
    }
    @Test void assemblesMultiPageScenesFromFlatModelChoices() {
        var mapper = new ObjectMapper();
        var plan = new StudyroomLessonPlanner.Plan(2, "标题", "摘要", List.of(), List.of(new StudyroomLessonPlanner.Goal("s1", "规则", "理解规则", "CONCEPT")));
        String json = """
            {"version":1,"title":"标题","summary":"摘要","evidenceIds":[],"steps":[{"id":"s1","type":"ppt","title":"规则","explanation":"理解规则","resources":[{"type":"ppt","id":"p1","parameters":{}},{"type":"ppt","id":"p2","parameters":{}}],"pageExplanations":{"p1":"先看定义","p2":"再看例子"}}]}
            """;
        var result = StudyroomModelOutput.parse(mapper, StudyroomSceneBinder.normalize(mapper, json, plan));
        assertThat(result.steps().getFirst().orchestration().primary().items()).extracting(StudyroomScene.Item::explanation)
            .containsExactly("先看定义", "再看例子");
    }
}
