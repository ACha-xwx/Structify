package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StudyroomSceneAdapterTest {
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
