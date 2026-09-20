package com.feng.dsagent.classroom;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.feng.dsagent.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class ClassroomServiceTest {
    @Autowired ClassroomService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean ClassroomModelJson model;
    final long user = 8911L;
    @BeforeEach void setup() {
        jdbc.update("INSERT INTO users (id,email,password_hash) VALUES (?, 'timeline-test@example.com','hash')", user);
        jdbc.update("""
            INSERT INTO classroom_scripts (id,chapter_id,title,review_status,script_json) VALUES
            ('timeline-test','03-stack-queue','完整课堂','PUBLISHED',?)
            """, """
            {"lessonId":"test","title":"完整课堂","steps":[
            {"type":"explain","role":"teacher","content":"讲解 A"},
            {"type":"question","prompt":"第一个问题","expected":["参考答案 A"]},
            {"type":"explain","role":"teacher","content":"讲解 B"},
            {"type":"question","prompt":"第二个问题","expected":["参考答案 B"]},
            {"type":"blackboard","content":"过程演示"},
            {"type":"summary","content":"完整总结"}]}
            """);
        when(model.generate(anyLong(), anyString(), anyString(), anyInt(), any())).thenAnswer(call -> {
            String system = call.getArgument(1);
            return mapper.readTree(system.contains("语义判断")
                ? "{\"status\":\"CORRECT\",\"feedback\":\"同义表达正确\",\"misconception\":null}"
                : "{\"feedback\":\"先直接回答你的插问\"}");
        });
    }
    @Test void preservesAllStepsAndBothQuestions() {
        String id = service.create(user, "timeline-test").id();
        assertThat(service.get(user,id).state()).isEqualTo(ClassroomState.OPENING);
        assertThat(service.apply(user,id,ClassroomAction.CONTINUE,null).stage().path("content").asText()).isEqualTo("讲解 A");
        assertThat(service.apply(user,id,ClassroomAction.CONTINUE,null).stage().path("prompt").asText()).isEqualTo("第一个问题");
        assertThat(service.get(user,id).stage().has("expected")).isFalse();
        service.apply(user,id,ClassroomAction.ANSWER,"我用同义表达回答");
        assertThat(service.get(user,id).answerEvaluation().feedback()).isEqualTo("同义表达正确");
        assertThat(service.apply(user,id,ClassroomAction.CONTINUE,null).stage().path("content").asText()).isEqualTo("讲解 B");
        assertThat(service.apply(user,id,ClassroomAction.CONTINUE,null).stage().path("prompt").asText()).isEqualTo("第二个问题");
        service.apply(user,id,ClassroomAction.ANSWER,"第二个答案");
        assertThat(service.apply(user,id,ClassroomAction.CONTINUE,null).state()).isEqualTo(ClassroomState.BLACKBOARD);
        assertThat(service.apply(user,id,ClassroomAction.CONTINUE,null).state()).isEqualTo(ClassroomState.SUMMARY);
        verify(model,times(2)).generate(eq(user),anyString(),anyString(),anyInt(),any());
    }
    @Test void questionResponseSurvivesRefreshAndReturnsToSameWaitingQuestion() {
        String id = service.create(user,"timeline-test").id();
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        ClassroomSessionView response = service.apply(user,id,ClassroomAction.ASK,"请重新解释一下");
        assertThat(response.stage().path("teacherResponse").path("feedback").asText()).contains("插问");
        assertThat(service.get(user,id).stage()).isEqualTo(response.stage());
        ClassroomSessionView resumed = service.apply(user,id,ClassroomAction.CONTINUE,null);
        assertThat(resumed.state()).isEqualTo(ClassroomState.WAITING);
        assertThat(resumed.stage().path("stepIndex").asInt()).isEqualTo(1);
        assertThat(resumed.stage().has("teacherResponse")).isFalse();
        assertThatThrownBy(() -> service.apply(user,id,ClassroomAction.CONTINUE,null)).isInstanceOf(ApiException.class);
    }
    @Test void midLessonSummaryStepDoesNotEndTheLesson() {
        jdbc.update("""
            INSERT INTO classroom_scripts (id,chapter_id,title,review_status,script_json) VALUES
            ('timeline-mid-summary','03-stack-queue','中途小结','PUBLISHED',?)
            """, """
            {"lessonId":"test","title":"中途小结","steps":[
            {"type":"explain","role":"teacher","content":"讲解 A"},
            {"type":"summary","content":"小结 A"},
            {"type":"explain","role":"teacher","content":"讲解 B"}]}
            """);
        String id = service.create(user, "timeline-mid-summary").id();
        assertThat(service.apply(user, id, ClassroomAction.CONTINUE, null).stage().path("content").asText()).isEqualTo("讲解 A");
        // A summary page of the deck reports the summary beat without ending the lesson: the next
        // sub-lesson still has to be reachable, otherwise the rest of the courseware is stranded.
        ClassroomSessionView summary = service.apply(user, id, ClassroomAction.CONTINUE, null);
        assertThat(summary.state()).isEqualTo(ClassroomState.SUMMARY);
        assertThat(summary.stage().path("stepIndex").asInt()).isEqualTo(1);
        assertThat(service.apply(user, id, ClassroomAction.CONTINUE, null).stage().path("content").asText()).isEqualTo("讲解 B");
        // Only running past the last step ends the lesson.
        ClassroomSessionView done = service.apply(user, id, ClassroomAction.CONTINUE, null);
        assertThat(done.stage().path("stepIndex").asInt()).isEqualTo(3);
        assertThatThrownBy(() -> service.apply(user, id, ClassroomAction.CONTINUE, null)).isInstanceOf(ApiException.class);
    }
    @Test void aWrongAnswerKeepsTheQuestionUntilItIsAnsweredAgain() {
        String id = service.create(user, "timeline-test").id();
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        when(model.generate(anyLong(),anyString(),anyString(),anyInt(),any()))
            .thenReturn(mapper.readTree("{\"status\":\"INCORRECT\",\"feedback\":\"漏掉了右子树，再按中序看一遍\",\"misconception\":\"只考虑了左子树\"}"));
        ClassroomSessionView wrong = service.apply(user,id,ClassroomAction.ANSWER,"只说了左子树");
        // The question is not consumed: it stays open on the same step and is reported as unanswered,
        // which is what stops CONTINUE from walking past it.
        assertThat(wrong.state()).isEqualTo(ClassroomState.WAITING);
        assertThat(wrong.answerEvaluation().status()).isEqualTo(ClassroomAnswerStatus.INCORRECT);
        assertThat(wrong.stage().path("stepIndex").asInt()).isEqualTo(1);
        assertThat(wrong.stage().path("teacherResponse").path("answered").asBoolean()).isFalse();
        assertThat(wrong.stage().path("teacherResponse").path("retry").asBoolean()).isTrue();
        assertThat(wrong.stage().path("teacherResponse").path("attempts").asInt()).isEqualTo(1);
        assertThat(wrong.stage().path("teacherResponse").path("feedback").asText()).contains("中序");
        // Answering the same open question again is accepted, and the attempt count follows along.
        when(model.generate(anyLong(),anyString(),anyString(),anyInt(),any()))
            .thenReturn(mapper.readTree("{\"status\":\"CORRECT\",\"feedback\":\"这次对了\",\"misconception\":null}"));
        ClassroomSessionView correct = service.apply(user,id,ClassroomAction.ANSWER,"左子树、根结点、右子树");
        assertThat(correct.state()).isEqualTo(ClassroomState.DISCUSS);
        assertThat(correct.stage().path("teacherResponse").path("answered").asBoolean()).isTrue();
        assertThat(correct.stage().path("teacherResponse").path("attempts").asInt()).isEqualTo(2);
        // Only a passed question lets the lesson move on.
        assertThat(service.apply(user,id,ClassroomAction.CONTINUE,null).stage().path("content").asText()).isEqualTo("讲解 B");
    }
    @Test void anUnansweredQuestionCannotBeWalkedPast() {
        String id = service.create(user, "timeline-test").id();
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        when(model.generate(anyLong(),anyString(),anyString(),anyInt(),any()))
            .thenReturn(mapper.readTree("{\"status\":\"MISCONCEPTION\",\"feedback\":\"把中序和前序弄反了\",\"misconception\":\"中序/前序混淆\"}"));
        service.apply(user,id,ClassroomAction.ANSWER,"前序遍历的结果");
        // CONTINUE only clears the failed attempt and returns to the very same question: it never
        // advances the cursor, so a wrong answer cannot carry the lesson forward.
        ClassroomSessionView reopened = service.apply(user,id,ClassroomAction.CONTINUE,null);
        assertThat(reopened.stage().path("stepIndex").asInt()).isEqualTo(1);
        assertThat(reopened.state()).isEqualTo(ClassroomState.WAITING);
        assertThat(reopened.stage().has("teacherResponse")).isFalse();
        assertThatThrownBy(() -> service.apply(user,id,ClassroomAction.CONTINUE,null)).isInstanceOf(ApiException.class);
    }
    @Test void aWrongAnswerOnTheLastStepDoesNotEndTheLesson() {
        jdbc.update("""
            INSERT INTO classroom_scripts (id,chapter_id,title,review_status,script_json) VALUES
            ('timeline-last-question','03-stack-queue','末步提问','PUBLISHED',?)
            """, """
            {"lessonId":"test","title":"末步提问","steps":[
            {"type":"explain","role":"teacher","content":"讲解 A"},
            {"type":"question","prompt":"最后一个问题","expected":["参考答案"]}]}
            """);
        String id = service.create(user, "timeline-last-question").id();
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        when(model.generate(anyLong(),anyString(),anyString(),anyInt(),any()))
            .thenReturn(mapper.readTree("{\"status\":\"INCORRECT\",\"feedback\":\"再想想\",\"misconception\":null}"));
        ClassroomSessionView wrong = service.apply(user,id,ClassroomAction.ANSWER,"答错");
        assertThat(wrong.state()).isEqualTo(ClassroomState.WAITING);
        // The regression this guards: a wrong answer on the final step used to consume it, so the next
        // CONTINUE ran the cursor past the end and reported the whole lesson as finished.
        ClassroomSessionView again = service.apply(user,id,ClassroomAction.CONTINUE,null);
        assertThat(again.state()).isEqualTo(ClassroomState.WAITING);
        assertThat(again.stage().path("stepIndex").asInt()).isEqualTo(1);
        assertThat(again.stage().path("stepCount").asInt()).isEqualTo(2);
        assertThat(again.stage().path("content").asText()).isNotEqualTo("本课已结束。");
    }
    @Test void pausesAndEnforcesOwnership() {
        String id = service.create(user,"timeline-test").id();
        assertThatThrownBy(() -> service.get(user+1,id)).isInstanceOf(ApiException.class);
        service.apply(user,id,ClassroomAction.PAUSE,null);
        assertThatThrownBy(() -> service.apply(user,id,ClassroomAction.ASK,"问题")).isInstanceOf(ApiException.class);
        service.apply(user,id,ClassroomAction.RESUME,null);
        assertThat(service.apply(user,id,ClassroomAction.CONTINUE,null).stage().path("stepIndex").asInt()).isZero();
    }
    @Test void modelFailureDoesNotConsumeCurrentStep() {
        String id = service.create(user,"timeline-test").id();
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        when(model.generate(anyLong(),anyString(),anyString(),anyInt(),any())).thenThrow(new IllegalStateException("model offline"));
        assertThatThrownBy(() -> service.apply(user,id,ClassroomAction.ASK,"问题")).hasMessage("model offline");
        assertThat(service.get(user,id).stage().path("stepIndex").asInt()).isZero();
    }
    @Test void rejectsReplayedNextWithoutConsumingAnotherStep() {
        String id = service.create(user,"timeline-test").id();
        service.apply(user,id,ClassroomAction.CONTINUE,null,0);
        assertThatThrownBy(() -> service.apply(user,id,ClassroomAction.CONTINUE,null,0)).isInstanceOf(ApiException.class);
        assertThat(service.get(user,id).stage().path("stepIndex").asInt()).isZero();
        assertThat(service.get(user,id).stage().path("revision").asInt()).isEqualTo(1);
    }
    @Test void modelCannotSetRuntimeStateThroughAnInterruptionResponse() {
        String id = service.create(user,"timeline-test").id();
        service.apply(user,id,ClassroomAction.CONTINUE,null);
        when(model.generate(anyLong(),anyString(),anyString(),anyInt(),any())).thenReturn(mapper.readTree("""
            {"feedback":"先回答插问","status":"FINISHED","answered":true,"stepIndex":99,"kind":"answer"}
            """));
        ClassroomSessionView result = service.apply(user,id,ClassroomAction.ASK,"为什么？");
        assertThat(result.answerEvaluation()).isNull();
        assertThat(result.stage().path("teacherResponse").has("status")).isFalse();
        assertThat(result.stage().path("teacherResponse").path("answered").asBoolean()).isFalse();
        assertThat(service.apply(user,id,ClassroomAction.CONTINUE,null).stage().path("stepIndex").asInt()).isZero();
    }
}
