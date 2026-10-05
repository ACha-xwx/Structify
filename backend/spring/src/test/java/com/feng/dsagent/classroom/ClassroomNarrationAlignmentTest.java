package com.feng.dsagent.classroom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Keeping a step's narration on the page it shows.
 *
 * <p>The structural validator cannot see this: a step that references the right page while narrating a
 * different topic breaks no rule - which is how a lesson came to explain {@code int} over the school's
 * organisation chart. These cover the three pieces of the consistency pass that do not need a model: the
 * payload it sends, the step numbers it accepts back, and the rewrite it applies.
 */
class ClassroomNarrationAlignmentTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static SlideSpinePlan.Slide page(String id, String summary, String body) {
        return new SlideSpinePlan.Slide(id, 1, "学校简介", body, summary, "定义", "1.1", "01-01A", "", "concept", List.of());
    }

    private ArrayNode steps() {
        ArrayNode steps = mapper.createArrayNode();
        ObjectNode teaching = steps.addObject();
        teaching.put("type", "explain");
        teaching.putArray("slideRefs").add("s003");
        teaching.put("content", "这一页讲的是整数的取值范围。");
        ObjectNode question = steps.addObject();
        question.put("type", "question");
        question.putArray("slideRefs").add("s004");
        question.put("content", "");
        ObjectNode closing = steps.addObject();
        closing.put("type", "summary");
        closing.put("content", "这节课就到这里。");
        return steps;
    }

    @Test
    void theReviewCarriesWhatThePageHoldsAndOnlyTheStepsThatShowOne() {
        List<SlideSpinePlan.Slide> part = List.of(
            page("s003", "1.1节的基础概念列表，引出后续定义。", "学校简介 数据对象（ Data Object ）定义：数据对象是性质相同的数据元素的集合"),
            page("s004", "给出数据的定义。", "数据是描述客观事物的符号集合"));

        ArrayNode review = ClassroomPreparation.narrationReview(mapper, steps(), part);

        // The closing step shows no page and the question step carries no narration: neither is reviewed.
        assertThat(review).hasSize(1);
        JsonNode row = review.get(0);
        assertThat(row.path("step").asInt()).isZero();
        assertThat(row.path("这一页讲的是").asText()).startsWith("1.1节的基础概念列表");
        assertThat(row.path("这一页讲的是").asText()).doesNotContain("学校简介");
        assertThat(row.path("讲稿").asText()).contains("整数的取值范围");
    }

    @Test
    void theNamedStepsAreDeduplicatedCappedAndRangeChecked() {
        JsonNode verdict = mapper.readTree("""
            {"off":[3,3,7,1,"x",-2,9,11]}""");
        assertThat(ClassroomPreparation.narrationOff(verdict, 4)).containsExactly(3, 7, 1, 9);
        assertThat(ClassroomPreparation.narrationOff(mapper.readTree("{\"off\":[]}"), 4)).isEmpty();
    }

    @Test
    void onlyTheNamedNarrationsAreReplaced() {
        ArrayNode steps = steps();
        JsonNode rewrites = mapper.readTree("""
            {"fixed":[{"step":0,"讲稿":"这一页讲的是数据对象的定义。"},{"step":9,"讲稿":"越界"},{"step":1,"讲稿":"  "}]}""");

        assertThat(ClassroomPreparation.applyNarrationRewrites(steps, rewrites)).isEqualTo(1);
        assertThat(steps.get(0).path("content").asText()).isEqualTo("这一页讲的是数据对象的定义。");
        // The question and closing steps are untouched, so a rewrite can never disturb the walk.
        assertThat(steps.get(1).path("content").asText()).isEmpty();
        assertThat(steps.get(2).path("content").asText()).isEqualTo("这节课就到这里。");
    }
}
