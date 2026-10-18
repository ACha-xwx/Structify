package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class StudyroomStepRepairTest {
    @Test
    void onlyReplacesInvalidStepsAndPreservesLessonScope() {
        var mapper = new ObjectMapper();
        String original =
                """
{"version":1,"title":"完整课程","summary":"原范围","evidenceIds":[],"steps":[{"id":"s1","title":"保留两页课件","resources":[{"id":"p1"},{"id":"p2"}]},{"id":"s2","title":"损坏步骤"},{"id":"s3","title":"保留代码","resources":[{"id":"code"}]}]}
""";
        String repaired =
                """
{"version":1,"title":"缩水课程","summary":"缩水范围","evidenceIds":[],"steps":[{"id":"s1","title":"不该替换"},{"id":"s2","title":"修复后的动画"}]}
""";
        var merged =
                mapper.readTree(
                        StudyroomStepRepair.merge(
                                mapper, original, repaired, single -> !single.contains("损坏步骤")));
        assertThat(merged.path("title").asString()).isEqualTo("完整课程");
        assertThat(merged.path("steps").size()).isEqualTo(3);
        assertThat(merged.path("steps").path(0).path("resources").size()).isEqualTo(2);
        assertThat(merged.path("steps").path(1).path("title").asString()).isEqualTo("修复后的动画");
        assertThat(merged.path("steps").path(2).path("title").asString()).isEqualTo("保留代码");
    }
}
