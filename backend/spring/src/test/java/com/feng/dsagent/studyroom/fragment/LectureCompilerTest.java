package com.feng.dsagent.studyroom.fragment;

import static org.junit.jupiter.api.Assertions.*;

import com.feng.dsagent.studyroom.domain.StudyroomScene;
import com.feng.dsagent.studyroom.teaching.domain.LectureRegion;
import com.feng.dsagent.studyroom.teaching.support.LectureCompiler;
import com.feng.dsagent.studyroom.vo.StudyroomVO;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LectureCompilerTest {
    @Test
    void bindsOnlyAvailableMaterialsAndActualCodeLines() {
        var scene = new StudyroomScene(
                "code", "C_CODE", "源码", List.of(new StudyroomScene.Item("c", "看判空。", 1, 2)), "READ", null, null, null);
        var step = new StudyroomVO.Step(
                "s",
                "f",
                "CONCEPT",
                "栈",
                "先认识栈。再理解后进先出。",
                List.of(),
                List.of(new StudyroomVO.Resource("c_code", "c", "代码", "", null, true)),
                0,
                false,
                null,
                null,
                new StudyroomScene.Orchestration(2, "理解栈", "CONCEPT", scene, List.of()));
        var compiler = new LectureCompiler();
        var valid = compiler.compile(step, List.of(), Map.of("c", 3), Map.of(), true);
        assertTrue(valid.stream().anyMatch(c -> "CODE_HIGHLIGHT".equals(c.get("action"))));
        assertEquals("CHECK", valid.getLast().get("kind"));
        assertFalse(compiler.compile(step, List.of(), Map.of("c", 1), Map.of(), false).stream()
                .anyMatch(c -> "CODE_HIGHLIGHT".equals(c.get("action"))));
        assertEquals(valid, compiler.compile(step, List.of(), Map.of("c", 3), Map.of(), true));
    }

    @Test
    void rejectsNonFiniteAndOutOfImageRegions() {
        String id = UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class, () -> new LectureRegion(id, "s", "p", .9, .1, .2, .2, "讲解"));
        assertThrows(
                IllegalArgumentException.class, () -> new LectureRegion(id, "s", "p", Double.NaN, 0, .2, .2, "讲解"));
        assertDoesNotThrow(() -> new LectureRegion(id, "s", "p", .1, .2, .3, .4, "讲解"));
    }
}
