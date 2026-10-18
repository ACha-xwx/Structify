package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StudyroomSceneAdapterTest {
    @Test void preservesOrderedPagesAndSeparatesOtherMaterials() {
        var value = StudyroomSceneAdapter.legacy("理解遍历", "依次访问", List.of(
            resource("ppt", "p2"), resource("ppt", "p3"), resource("c_code", "code")));
        assertThat(value.primary().kind()).isEqualTo("PPT");
        assertThat(value.primary().items()).extracting(StudyroomScene.Item::resourceId).containsExactly("p2", "p3");
        assertThat(value.supplementary()).singleElement().satisfies(s -> assertThat(s.codeMode()).isEqualTo("READ"));
    }
    @Test void usesTextForNoResourcesAndDoesNotInventMissingResources() {
        var empty = StudyroomSceneAdapter.legacy("目标", "讲解", List.of());
        assertThat(empty.primary().kind()).isEqualTo("TEXT");
        var missing = new StudyroomView.Resource("ppt", "missing", "", "", java.util.Map.of(), false);
        assertThat(StudyroomSceneAdapter.legacy("目标", "讲解", List.of(missing)).primary().items())
            .extracting(StudyroomScene.Item::resourceId).containsExactly("missing");
    }
    @Test void rejectsUnboundReferencesAndInvalidLineRanges() {
        String json = """
            {"schemaVersion":2,"objective":"目标","intent":"IMPLEMENTATION","primary":{"id":"main","kind":"C_CODE","purpose":"实现","items":[{"resourceId":"foreign","explanation":"说明"}],"codeMode":"READ"},"supplementary":[]}
            """;
        assertThatThrownBy(() -> StudyroomSceneAdapter.parse(new ObjectMapper(), json, List.of(resource("c_code", "code"))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudyroomScene.Item("code", "说明", 8, 2)).isInstanceOf(IllegalArgumentException.class);
    }
    private StudyroomView.Resource resource(String type, String id) {
        return new StudyroomView.Resource(type, id, id, "", java.util.Map.of(), true);
    }
}
