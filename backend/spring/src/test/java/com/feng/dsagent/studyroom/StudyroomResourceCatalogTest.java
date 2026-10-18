package com.feng.dsagent.studyroom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyroomResourceCatalogTest {
    @Test void suppliesActualSourceOnlyForSelectedCodeCandidates() {
        var codes = org.mockito.Mockito.mock(com.feng.dsagent.compiler.ClassroomCodeStudyroomCatalog.class);
        var sample = new com.feng.dsagent.compiler.ClassroomCodeSample("code-1", "遍历", "source", List.of(), List.of(), "摘要", "A..", "A", "void show() {}\nint main() { show(); }");
        org.mockito.Mockito.when(codes.lessons()).thenReturn(List.of(new com.feng.dsagent.compiler.ClassroomCodeLesson("06-03", "遍历", "06-tree", List.of(sample))));
        var live = new StudyroomResourceCatalog(null, null, codes, null);
        var chosen = List.of(new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.C_CODE, "code-1", "遍历", "", List.of()));
        org.assertj.core.api.Assertions.assertThat(live.codeDetails(chosen)).singleElement().satisfies(detail -> {
            org.assertj.core.api.Assertions.assertThat(detail.get("source")).isEqualTo(sample.code());
        });
        org.assertj.core.api.Assertions.assertThat(live.codeDetails(List.of())).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void rejectsTreeStringsThatTheSimulationEngineCannotRender() {
        var treeCatalog = new StudyroomResourceCatalog(List.of(new StudyroomResourceCatalog.Candidate(
            StudyroomModelOutput.ResourceType.ANIMATION, "tree.thread_traverse", "线索树遍历", "", List.of("initialData"))));
        var step = step("animation", "tree.thread_traverse", Map.of("initialData", "A(B,C)"));
        assertThrows(IllegalArgumentException.class, () -> treeCatalog.validate(List.of(step)));
        assertDoesNotThrow(() -> treeCatalog.validate(List.of(step("animation", "tree.thread_traverse",
            Map.of("initialData", List.of("A", "B", "C"))))));
    }

    private final StudyroomResourceCatalog catalog = new StudyroomResourceCatalog(List.of(
        new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.PPT, "slide-1", "节点结构", "", List.of()),
        new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.ANIMATION, "linked_list.reverse", "单链表逆置", "", List.of("initialData")),
        new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.C_CODE, "sample-1", "单链表示例", "", List.of())
    ));

    @Test
    void acceptsOnlyCandidatesOfTheMatchingResourceType() {
        assertDoesNotThrow(() -> catalog.validate(List.of(step("ppt", "slide-1", Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(List.of(step("ppt", "forged-slide", Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(List.of(step("c_code", "slide-1", Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(List.of(step("c_code", "forged-sample", Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(List.of(step("animation", "forged-capability", Map.of()))));
    }

    @Test
    void acceptsOnlyDeclaredAnimationParameterKeys() {
        assertDoesNotThrow(() -> catalog.validate(List.of(step("animation", "linked_list.reverse", Map.of("initialData", List.of(1, 2))))));
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(List.of(step("animation", "linked_list.reverse", Map.of("script", "arbitrary code")))));
    }

    @Test
    void doesNotAcceptMoreAnimationOptionsThanTheCandidateAllows() {
        Map<String, Object> parameters = Map.of("initialData", List.of(1), "script", "ignored");

        assertThrows(IllegalArgumentException.class, () -> catalog.validate(List.of(step("animation", "linked_list.reverse", parameters))));
    }

    private static StudyroomModelOutput.Step step(String type, String id, Map<String, Object> parameters) {
        StudyroomModelOutput.ResourceType resourceType = switch (type) {
            case "ppt" -> StudyroomModelOutput.ResourceType.PPT;
            case "animation" -> StudyroomModelOutput.ResourceType.ANIMATION;
            case "c_code" -> StudyroomModelOutput.ResourceType.C_CODE;
            default -> throw new IllegalArgumentException(type);
        };
        return new StudyroomModelOutput.Step(
            "step-" + type,
            StudyroomModelOutput.StepType.valueOf(type.toUpperCase()),
            "标题",
            "讲解内容",
            List.of(new StudyroomModelOutput.ResourceRef(resourceType, id, parameters))
        );
    }
}
