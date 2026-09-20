package com.feng.dsagent.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.common.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ClassroomScriptParserTest {

    private final ClassroomScriptParser parser = new ClassroomScriptParser(new ObjectMapper());

    @Test void normalizesCasingOfClosedVocabularyFieldsButStillRejectsUnknownValues() {
        // The model sometimes returns "Explain"/"QUESTION"; that casing slip used to make a whole lesson
        // unteachable, so the closed-vocabulary fields are lower-cased before validation.
        ClassroomScriptPlan normalized = parser.parse("""
            {"lessonId":"case-test","title":"测试","steps":[{"type":"QUESTION","role":"TEACHER","prompt":"问题","expected":["答案"]}]}
            """);
        assertThat(normalized.question().path("expected").get(0).asText()).isEqualTo("答案");
        assertThat(normalized.stage(ClassroomState.WAITING).path("prompt").asText()).isEqualTo("问题");

        // Anything outside the vocabulary must still fail loudly instead of being guessed at.
        assertThatThrownBy(() -> parser.parse("""
            {"lessonId":"case-test-2","title":"测试","steps":[{"type":"quiz","prompt":"问题","expected":["答案"]}]}
            """)).hasMessageContaining("$.steps[0]").hasMessageContaining("type 必须使用小写");
    }

    @Test void acceptsNullOptionalAnimationAndReportsExactInvalidStep() {
        assertThat(parser.parse("""
            {"lessonId":"null-animation","title":"测试","steps":[{"type":"explain","role":"teacher","content":"讲解","animationRef":null}]}
            """).stage(ClassroomState.EXPLAIN).path("content").asText()).isEqualTo("讲解");
        assertThatThrownBy(() -> parser.parse("""
            {"lessonId":"invalid-field","title":"测试","steps":[{"type":"question","prompt":"","expected":["参考"]}]}
            """)).hasMessageContaining("$.steps[0]").hasMessageContaining("prompt");
    }

    @Test
    void parsesTheTeacherApprovedStepBasedContract() {
        ClassroomScriptPlan plan = parser.parse("""
            {
              "lessonId":"03-stack-queue-01",
              "title":"栈的定义与基本操作",
              "objectives":["理解后进先出","掌握 push 和 pop"],
              "steps":[
                {
                  "type":"explain",
                  "role":"teacher",
                  "contentRef":"slide-03",
                  "animationRef":"stack-push"
                },
                {
                  "type":"question",
                  "role":"teacher",
                  "prompt":"依次入栈 A、B、C 后，第一次出栈得到什么？",
                  "expected":["C"],
                  "misconceptions":["A","B"]
                }
              ]
            }
            """);

        assertThat(plan.lessonId()).isEqualTo("03-stack-queue-01");
        assertThat(plan.objectives()).containsExactly("理解后进先出", "掌握 push 和 pop");
        assertThat(plan.stage(ClassroomState.EXPLAIN).path("animationRef").asText()).isEqualTo("stack-push");
        assertThat(plan.stage(ClassroomState.WAITING).path("prompt").asText()).contains("第一次出栈");
        assertThat(plan.stage(ClassroomState.WAITING).has("expected")).isFalse();
        assertThat(plan.question().path("expected").get(0).asText()).isEqualTo("C");
    }

    @Test
    void keepsLegacyStageScriptsReadableDuringMigration() {
        ClassroomScriptPlan plan = parser.parse("""
            {"stages":{"OPENING":{"speaker":"teacher","content":"欢迎进入课堂"}}}
            """);

        assertThat(plan.legacy()).isTrue();
        assertThat(plan.stage(ClassroomState.OPENING).path("content").asText()).isEqualTo("欢迎进入课堂");
    }

    @Test
    void rejectsQuestionsWithoutExpectedAnswers() {
        assertThatThrownBy(() -> parser.parse("""
            {
              "lessonId":"broken",
              "title":"无效脚本",
              "steps":[{"type":"question","role":"teacher","prompt":"答案是什么？"}]
            }
            """))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo("CLASSROOM_SCRIPT_INVALID")
            );
    }

    @Test
    void acceptsValidatedDsvpAnimationReferencesAndRejectsUnknownOperations() {
        ClassroomScriptPlan plan = parser.parse("""
            {
              "lessonId":"03-dsvp",
              "title":"DSVP classroom",
              "steps":[{
                "type":"explain",
                "role":"teacher",
                "animationRef":{
                  "protocol":"dsvp/1.0",
                  "request":{
                    "version":"1.0",
                    "structure":"stack",
                    "operation":"push",
                    "params":{"value":3,"capacity":8},
                    "initial_state":{"data":[1,2],"metadata":{"capacity":8}}
                  }
                }
              }]
            }
            """);
        assertThat(plan.stage(ClassroomState.EXPLAIN).path("animationRef").path("protocol").asText())
            .isEqualTo("dsvp/1.0");

        assertThatThrownBy(() -> parser.parse("""
            {
              "lessonId":"03-dsvp-broken",
              "title":"Broken DSVP classroom",
              "steps":[{
                "type":"explain",
                "animationRef":{
                  "protocol":"dsvp/1.0",
                  "request":{"version":"1.0","structure":"stack","operation":"execute","params":{},"initial_state":{"data":[]}}
                }
              }]
            }
            """))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo("CLASSROOM_SCRIPT_INVALID")
            );
    }

    @Test
    void acceptsLabeledQuestionSourcesAndRejectsUnknownOnes() {
        // Where a question came from (the model's design, the textbook, or a courseware page) is shown in
        // the classroom, so a value outside that vocabulary is a mistake worth echoing back.
        ClassroomScriptPlan plan = parser.parse("""
            {"lessonId":"source-test","title":"测试","steps":[
            {"type":"question","prompt":"中序遍历的结果是什么？","expected":["左、根、右"],"questionSource":"slide"}]}
            """);
        assertThat(plan.stage(ClassroomState.WAITING).path("questionSource").asText()).isEqualTo("slide");

        assertThatThrownBy(() -> parser.parse("""
            {"lessonId":"source-test-2","title":"测试","steps":[
            {"type":"question","prompt":"问题","expected":["答案"],"questionSource":"teacher"}]}
            """)).hasMessageContaining("questionSource 必须是 model、textbook 或 slide")
            .hasMessageContaining("teacher");
    }
    @Test
    void acceptsALongCoursewareLessonWithInterleavedQuestions() {
        // A 36-page deck with a question after every third page is 48 steps. The guard used to sit at 40,
        // which rejected the whole lesson at runtime - after preparation had already paid for it.
        StringBuilder steps = new StringBuilder();
        for (int page = 1; page <= 36; page++) {
            if (page > 1) steps.append(',');
            steps.append("{\"type\":\"explain\",\"role\":\"teacher\",\"content\":\"第 ").append(page).append(" 页讲解\"}");
            if (page % 3 == 0) {
                steps.append(",{\"type\":\"question\",\"role\":\"teacher\",\"prompt\":\"第 ").append(page)
                    .append(" 页的要点是什么？\",\"expected\":[\"要点\"],\"questionSource\":\"slide\"}");
            }
        }
        ClassroomScriptPlan plan = parser.parse("{\"lessonId\":\"long-deck\",\"title\":\"长课件课时\",\"steps\":[" + steps + "]}");
        assertThat(plan.lessonId()).isEqualTo("long-deck");
        assertThat(plan.stage(ClassroomState.WAITING).path("prompt").asText()).contains("第 3 页");
    }

    @Test
    void namesTheFieldThatBrokeAControlledArray() {
        // A bare "format invalid" leaves the repair round nothing to fix, so the offending element is echoed.
        assertThatThrownBy(() -> parser.parse("""
            {"lessonId":"bad-expected","title":"测试","steps":[{"type":"question","prompt":"问题","expected":[1,2]}]}
            """)).hasMessageContaining("expected 的每一项都必须是字符串")
            .hasMessageContaining("$.steps[0]");
    }

    @Test
    void acceptsLegacyDsvpProtocolAliasAndNormalizesTheStoredPlan() {
        ClassroomScriptPlan plan = parser.parse("""
            {
              "lessonId":"03-dsvp-legacy",
              "title":"Legacy DSVP classroom",
              "steps":[{
                "type":"explain",
                "role":"teacher",
                "animationRef":{
                  "protocol":"dsvp/1",
                  "request":{
                    "version":"1.0",
                    "structure":"stack",
                    "operation":"push",
                    "params":{"value":7},
                    "initial_state":{"data":[2,5]}
                  }
                }
              }]
            }
            """);

        assertThat(plan.stage(ClassroomState.EXPLAIN).path("animationRef").path("protocol").asText())
            .isEqualTo("dsvp/1.0");
    }
}
