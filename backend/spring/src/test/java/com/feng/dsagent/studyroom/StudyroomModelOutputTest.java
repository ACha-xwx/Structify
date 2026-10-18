package com.feng.dsagent.studyroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StudyroomModelOutputTest {
    @org.junit.jupiter.api.Test
    void preservesMissingTreeChildrenAsNullArrayEntries() {
        var output = StudyroomModelOutput.parse(new tools.jackson.databind.ObjectMapper(), """
            {"version":1,"title":"树","summary":"遍历","evidenceIds":[],"steps":[
              {"id":"tree-step","type":"animation","title":"中序","explanation":"访问左右子树","resources":[
                {"type":"animation","id":"tree.inorder","parameters":{"initialData":["A",null,"B"]}}]}]}
            """);
        org.assertj.core.api.Assertions.assertThat(((java.util.Map<?, ?>) output.steps().getFirst().resources().getFirst().parameters()).get("initialData"))
            .isEqualTo(java.util.Arrays.asList("A", null, "B"));
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void normalizesMisplacedCodeSceneFieldsWithoutAcceptingUnknownResourceIds() {
        String json = """
            {"version":1,"title":"遍历","summary":"递归实现","evidenceIds":[],"steps":[{"id":"s1","type":"c_code","title":"实现","explanation":"递归","codeMode":"READ","resources":[{"type":"c_code","id":"code-1","parameters":{}}],"orchestration":{"schemaVersion":2,"objective":"理解实现","intent":"IMPLEMENTATION","primary":{"id":"main","kind":"C_CODE","purpose":"看实现","items":[{"resourceId":"code-1","explanation":"观察调用"}]},"supplementary":[]}}]}
            """;
        var result = StudyroomModelOutput.parse(new tools.jackson.databind.ObjectMapper(), json);
        org.assertj.core.api.Assertions.assertThat(result.steps().getFirst().orchestration().primary().codeMode()).isEqualTo("READ");
    }

    @Test
    void parsesVersionedOutlineAndTypedResourceReferences() {
        StudyroomModelOutput output = StudyroomModelOutput.parse(mapper, """
            {
              "version": 1,
              "title": "链表基础",
              "summary": "理解节点与指针",
              "evidenceIds": ["ev-1"],
              "steps": [{
                "id": "step-1",
                "type": "ppt",
                "title": "节点结构",
                "explanation": "每个节点保存数据和后继地址。",
                "resources": [{"type": "ppt", "id": "slide-1", "parameters": {"slideId": "slide-1"}}]
              }]
            }
            """);

        assertEquals(1, output.version());
        assertEquals(StudyroomModelOutput.StepType.PPT, output.steps().getFirst().type());
        assertEquals("slide-1", output.steps().getFirst().resources().getFirst().id());
    }

    @Test
    void rejectsDuplicateStepIdsAndUnknownStepTypes() {
        String duplicate = """
            {"version":1,"title":"T","summary":"S","evidenceIds":[],"steps":[
              {"id":"same","type":"explanation","title":"A","explanation":"A","resources":[]},
              {"id":"same","type":"explanation","title":"B","explanation":"B","resources":[]}]}
            """;
        String unknownType = """
            {"version":1,"title":"T","summary":"S","evidenceIds":[],"steps":[
              {"id":"one","type":"quiz","title":"Q","explanation":"Q","resources":[]}]}
            """;

        assertThrows(IllegalArgumentException.class, () -> StudyroomModelOutput.parse(mapper, duplicate));
        assertThrows(IllegalArgumentException.class, () -> StudyroomModelOutput.parse(mapper, unknownType));
    }

    @Test
    void rejectsEmptyOrOversizedOutlines() {
        String empty = """
            {"version":1,"title":"T","summary":"S","evidenceIds":[],"steps":[]}
            """;
        String oversized = """
            {"version":1,"title":"T","summary":"S","evidenceIds":[],"steps":[
              {"id":"one","type":"explanation","title":"T","explanation":"x","resources":[]}]
            }
            """;
        StringBuilder longText = new StringBuilder(oversized);
        longText.replace(longText.indexOf("\"explanation\":\"x\""), longText.indexOf("\"explanation\":\"x\"") + 18,
            "\"explanation\":\"" + "x".repeat(StudyroomModelOutput.MAX_EXPLANATION_LENGTH + 1) + "\"");

        assertThrows(IllegalArgumentException.class, () -> StudyroomModelOutput.parse(mapper, empty));
        assertThrows(IllegalArgumentException.class, () -> StudyroomModelOutput.parse(mapper, longText.toString()));
    }

    @Test
    void rejectsUnknownFieldsAndUnauthorizedEvidence() {
        String unknownField = """
            {"version":1,"title":"T","summary":"S","evidenceIds":[],"code":"run me","steps":[
              {"id":"one","type":"explanation","title":"T","explanation":"x","resources":[]}]}
            """;
        StudyroomModelOutput output = StudyroomModelOutput.parse(mapper, """
            {"version":1,"title":"T","summary":"S","evidenceIds":["ev-1"],"steps":[
              {"id":"one","type":"explanation","title":"T","explanation":"x","resources":[]}]}
            """);

        assertThrows(IllegalArgumentException.class, () -> StudyroomModelOutput.parse(mapper, unknownField));
        assertThrows(IllegalArgumentException.class, () -> output.validateEvidenceIds(Set.of("ev-2")));
        output.validateEvidenceIds(Set.of("ev-1", "ev-2"));
    }

    @Test
    void rejectsMalformedJsonAndMismatchedResourceStepType() {
        assertThrows(IllegalArgumentException.class, () -> StudyroomModelOutput.parse(mapper, "{not json}"));
        assertThrows(IllegalArgumentException.class, () -> StudyroomModelOutput.parse(mapper, """
            {"version":1,"title":"T","summary":"S","evidenceIds":[],"steps":[
              {"id":"one","type":"ppt","title":"T","explanation":"x","resources":[
                {"type":"animation","id":"linked_list.reverse","parameters":{}}]}]}
            """));
    }
}

