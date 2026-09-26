package com.feng.dsagent.classroom;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class ClassroomPreparationTest {
    @Autowired ClassroomPreparation preparation;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean ClassroomModelJson model;

    @Test void backgroundJobUsesReviewedTextbookAndRemainsOwned() throws Exception {
        // Synthetic test evidence only; no production textbook claim.
        String quote = "测试教材片段：栈是限定在表尾进行插入或删除操作的线性表。";
        jdbc.update("INSERT INTO users (id,email,password_hash) VALUES (8921,'preparation-test@example.com','hash')");
        jdbc.update("INSERT INTO knowledge_chunks (id,chapter_id,title,content,source_path,page_label,review_status,license_scope) VALUES ('prep-test-chunk','03-stack-queue','测试教材',?,'textbook/test-preparation.md','测试页','VERIFIED','CLASSROOM_ONLY')",quote);
        when(model.generate(eq(8921L),anyString(),anyString(),anyInt(),any())).thenAnswer(call -> {
            String input = call.getArgument(2);
            String lessonId = preparation.lessons("03-stack-queue").stream().filter(item -> item.source().equals("textbook/test-preparation.md")).findFirst().orElseThrow().id();
            assertThat(input).contains(quote);
            JsonNode plan = mapper.readTree("""
                {"lessonId":"%s","title":"测试课堂","steps":[
                {"type":"explain","role":"teacher","content":"讲解","keywords":["栈"],"sourceChunkIds":["prep-test-chunk"],"evidence":"%s"},
                {"type":"question","role":"teacher","prompt":"什么是栈？","expected":["参考"],"keywords":["后进先出"],"sourceChunkIds":["prep-test-chunk"],"evidence":"%s"},
                {"type":"summary","role":"teacher","content":"总结","keywords":["栈顶"],"sourceChunkIds":["prep-test-chunk"],"evidence":"%s"}]}
                """.formatted(lessonId,quote,quote,quote));
            // The model need not re-transcribe quotations; the server attaches exact source text.
            for (JsonNode step : plan.path("steps")) ((tools.jackson.databind.node.ObjectNode) step).put("evidence", "不能作为引用的模型改写");
            Consumer<JsonNode> validate = call.getArgument(4); validate.accept(plan);
            assertThat(plan.path("steps").get(0).path("evidence").asText()).isEqualTo(quote);
            return plan;
        });
        var lesson = preparation.lessons("03-stack-queue").stream().filter(item -> item.source().equals("textbook/test-preparation.md")).findFirst().orElseThrow();
        var job = preparation.start(8921,lesson.id());
        assertThatThrownBy(() -> preparation.status(8922,job.id())).hasMessageContaining("不存在");
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (preparation.status(8921,job.id()).state().equals("preparing") && System.nanoTime()<deadline) Thread.sleep(50);
        var ready = preparation.status(8921,job.id());
        assertThat(ready.state()).as(ready.error()).isEqualTo("ready");
        assertThat(ready.session().stage().path("stepCount").asInt()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT review_status FROM classroom_scripts WHERE id = ?",String.class,ready.session().scriptId())).isEqualTo("DRAFT");
    }
    @Test void rejectsMissingTextbookRatherThanSubstitutingDemoOrPpt() {
        assertThatThrownBy(() -> preparation.start(8922,"not-a-textbook")).hasMessageContaining("没有已审核教材来源");
    }

    @Test void aRepairedPartKeepsOnlyItsAcceptedSteps() throws Exception {
        // The regression: the repair loop runs the validator once per attempt, so a rejected attempt's
        // steps must not survive into the accepted answer. They used to be collected in one array that
        // outlived the attempt, and a 26-page courseware lesson was published as 50 page steps - the same
        // pages walked twice, which is exactly what the learner must never see.
        String quote = "测试教材片段：二叉排序树左子树所有结点的值均小于根结点的值。";
        jdbc.update("INSERT INTO users (id,email,password_hash) VALUES (8923,'preparation-repair@example.com','hash')");
        jdbc.update("INSERT INTO knowledge_chunks (id,chapter_id,title,content,source_path,page_label,review_status,license_scope) VALUES ('prep-repair-chunk','03-stack-queue','测试教材',?,'textbook/test-preparation-repair.md','测试页','VERIFIED','CLASSROOM_ONLY')",quote);
        when(model.generate(eq(8923L),anyString(),anyString(),anyInt(),any())).thenAnswer(call -> {
            String lessonId = preparation.lessons("03-stack-queue").stream()
                .filter(item -> item.source().equals("textbook/test-preparation-repair.md")).findFirst().orElseThrow().id();
            Consumer<JsonNode> validate = call.getArgument(4);
            // An answer that walks every step and only then breaks the contract: the lesson has no teacher.
            JsonNode rejected = mapper.readTree("""
                {"lessonId":"%s","title":"测试课堂","steps":[
                {"type":"explain","role":"student","content":"讲解","keywords":["栈"],"sourceChunkIds":["prep-repair-chunk"]},
                {"type":"explain","role":"student","content":"讲解","keywords":["栈"],"sourceChunkIds":["prep-repair-chunk"]},
                {"type":"summary","role":"student","content":"总结","keywords":["栈"],"sourceChunkIds":["prep-repair-chunk"]}]}
                """.formatted(lessonId));
            assertThatThrownBy(() -> validate.accept(rejected)).hasMessageContaining("主讲老师");
            JsonNode plan = mapper.readTree("""
                {"lessonId":"%s","title":"测试课堂","steps":[
                {"type":"explain","role":"teacher","content":"讲解","keywords":["栈"],"sourceChunkIds":["prep-repair-chunk"]},
                {"type":"question","role":"teacher","prompt":"什么是栈？","expected":["参考"],"keywords":["后进先出"],"sourceChunkIds":["prep-repair-chunk"]},
                {"type":"summary","role":"teacher","content":"总结","keywords":["栈顶"],"sourceChunkIds":["prep-repair-chunk"]}]}
                """.formatted(lessonId));
            validate.accept(plan);
            return plan;
        });
        var lesson = preparation.lessons("03-stack-queue").stream()
            .filter(item -> item.source().equals("textbook/test-preparation-repair.md")).findFirst().orElseThrow();
        var job = preparation.start(8923,lesson.id());
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (preparation.status(8923,job.id()).state().equals("preparing") && System.nanoTime()<deadline) Thread.sleep(50);
        var ready = preparation.status(8923,job.id());
        assertThat(ready.state()).as(ready.error()).isEqualTo("ready");
        assertThat(ready.session().stage().path("stepCount").asInt()).isEqualTo(3);
    }
}
