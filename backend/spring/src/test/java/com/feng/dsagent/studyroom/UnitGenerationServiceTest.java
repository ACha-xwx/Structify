package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.feng.dsagent.aiquota.AiQuotaExecutionService;
import com.feng.dsagent.knowledge.*;
import com.feng.dsagent.model.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.generation.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;
import java.util.concurrent.atomic.*;

@SpringBootTest(
        properties = {
            "app.studyroom-generation.enabled=false",
            "spring.datasource.url=jdbc:h2:mem:unit-generation-workflow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        })
class UnitGenerationServiceTest {
    @Autowired OutlineService outlines;
    @Autowired StudyroomGenerationWorker worker;
    @Autowired UnitPublicationService publication;
    @Autowired OutlineJson json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiQuotaExecutionService execution;
    @MockitoBean OutlineModelGateway planner;
    @MockitoBean StudyroomResourceCatalog catalog;
    @MockitoBean KnowledgeSearchService knowledge;
    long user;
    AtomicBoolean processWorks = new AtomicBoolean(false);
    AtomicInteger conceptCalls = new AtomicInteger();
    AtomicInteger planningCalls = new AtomicInteger();
    AtomicInteger processCalls = new AtomicInteger();
    AtomicInteger codeCalls = new AtomicInteger();

    @BeforeEach
    void setup() {
        user = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        jdbc.update(
                "INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",
                user,
                user + "@unit.test",
                "hash");
        when(catalog.candidates(null))
                .thenReturn(
                        List.of(
                                candidate(StudyroomModelOutput.ResourceType.PPT, "p1"),
                                candidate(StudyroomModelOutput.ResourceType.PPT, "p2"),
                                candidate(StudyroomModelOutput.ResourceType.ANIMATION, "anim"),
                                candidate(StudyroomModelOutput.ResourceType.C_CODE, "code")));
        when(catalog.codeDetails(anyList())).thenReturn(List.of());
        when(knowledge.search(anyString(), isNull(), anyInt(), any()))
                .thenReturn(
                        List.of(
                                new KnowledgeSearchResult(
                                        new KnowledgeChunk(
                                                "e1", null, "栈", "栈的操作与实现", "source", "1",
                                                "PUBLIC"),
                                        1)));
        var unit =
                new ReviewedOutline.Unit(
                        "stack",
                        "顺序栈",
                        "理解栈操作与实现",
                        List.of("stack.definition"),
                        "EXPLAIN",
                        List.of("CONCEPT", "PROCESS", "IMPLEMENTATION"),
                        List.of());
        var outline =
                new ReviewedOutline(
                        1,
                        LearningDepth.STANDARD,
                        "栈",
                        List.of("理解栈"),
                        List.of(),
                        List.of(unit),
                        List.of(),
                        List.of(),
                        List.of("e1"),
                        null,
                        "EVIDENCE_ONLY");
        when(planner.plan(anyLong(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(json.write(outline));
        when(execution.complete(anyLong(), anyString(), anyString(), any()))
                .thenAnswer(
                        call -> {
                            ModelRequest request = call.getArgument(3);
                            if (request.messages().getFirst().content().contains("校验员"))
                                return new ModelResponse(
                                        "{\"valid\":true,\"reason\":\"目标与资料相符\",\"issues\":[]}");
                            if (request.messages().getFirst().content().contains("先规划教学目标")) {
                                planningCalls.incrementAndGet();
                                var goals = new ArrayList<Map<String, String>>();
                                int index = 0;
                                for (String intent :
                                        List.of("CONCEPT", "PROCESS", "IMPLEMENTATION"))
                                    goals.add(
                                            Map.of(
                                                    "id",
                                                    "activity-" + (++index),
                                                    "title",
                                                    "栈" + intent,
                                                    "objective",
                                                    "理解栈" + intent,
                                                    "intent",
                                                    intent));
                                return new ModelResponse(
                                        json.write(
                                                Map.of(
                                                        "schemaVersion",
                                                        2,
                                                        "title",
                                                        "栈",
                                                        "summary",
                                                        "理解栈",
                                                        "evidenceIds",
                                                        List.of("e1"),
                                                        "steps",
                                                        goals)));
                            }
                            var prompt =
                                    json.mapper().readTree(
                                            request.messages()
                                                    .getLast()
                                                    .content()
                                                    .split("\\n仅修复")[0]);
                            var goal = prompt.path("plan").path("steps").get(0);
                            String intent = goal.path("intent").asString();
                            String id = goal.path("id").asString();
                            if (intent.equals("PROCESS")) processCalls.incrementAndGet();
                            if (intent.equals("PROCESS") && !processWorks.get())
                                return new ModelResponse("{bad json");
                            var step = new LinkedHashMap<String, Object>();
                            step.put("id", id);
                            step.put("title", "栈");
                            step.put("explanation", "栈只从栈顶操作。");
                            if (intent.equals("CONCEPT")) {
                                conceptCalls.incrementAndGet();
                                step.put("type", "ppt");
                                step.put("resources", List.of(ref("ppt", "p1"), ref("ppt", "p2")));
                                step.put("pageExplanations", Map.of("p1", "定义", "p2", "操作示例"));
                            } else if (intent.equals("PROCESS")) {
                                step.put("type", "animation");
                                step.put(
                                        "resources",
                                        List.of(
                                                ref(
                                                        "animation",
                                                        processWorks.get()
                                                                ? "anim"
                                                                : "outside-candidate")));
                            } else {
                                step.put("type", "c_code");
                                step.put("resources", List.of(ref("c_code", "code")));
                                step.put(
                                        "codeMode",
                                        codeCalls.getAndIncrement() == 0 ? "EXPERIMENT" : "READ");
                                step.put("task", "运行验证");
                                step.put("expectedObservation", "观察栈顶");
                            }
                            return new ModelResponse(
                                    json.write(
                                            Map.of(
                                                    "version",
                                                    1,
                                                    "title",
                                                    "栈",
                                                    "summary",
                                                    "理解栈",
                                                    "evidenceIds",
                                                    List.of("e1"),
                                                    "steps",
                                                    List.of(step))));
                        });
    }

    static StudyroomResourceCatalog.Candidate candidate(
            StudyroomModelOutput.ResourceType type, String id) {
        return new StudyroomResourceCatalog.Candidate(
                type, id, "顺序栈操作与实现", "栈的定义、入栈出栈与实现", List.of());
    }

    static Map<String, Object> ref(String type, String id) {
        return Map.of("type", type, "id", id, "parameters", Map.of());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM users WHERE id=?", user);
    }

    @Test
    void retriesOnlyTheFailedActivityAndRetainsTwoSlidePages() {
        String draft =
                (String)
                        outlines.create(user, "学习栈操作", LearningDepth.STANDARD, null, null, "create")
                                .get("draftId");
        assertThat(worker.runOnce()).isTrue();
        var confirmed = outlines.confirm(user, draft, 1, "confirm");
        assertThat(worker.runOnce()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM studyroom_units WHERE id=?",
                                String.class,
                                confirmed.get("firstUnitId")))
                .isEqualTo("FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM study_room_steps WHERE run_id=?",
                                Integer.class,
                                confirmed.get("runId")))
                .isZero();
        var checkpoint =
                StudyroomModelOutput.parse(
                        json.mapper(),
                        jdbc.queryForObject(
                                "SELECT content_json FROM studyroom_units WHERE id=?",
                                String.class,
                                confirmed.get("firstUnitId")));
        assertThat(processCalls).hasValue(2);
        assertThat(checkpoint.steps()).hasSize(1);
        assertThat(checkpoint.steps().getFirst().resources()).hasSize(2);
        processWorks.set(true);
        publication.prepare(
                user,
                (String) confirmed.get("runId"),
                (String) confirmed.get("firstUnitId"),
                "retry");
        assertThat(worker.runOnce()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM studyroom_units WHERE id=?",
                                String.class,
                                confirmed.get("firstUnitId")))
                .isEqualTo("READY");
        assertThat(conceptCalls).hasValue(1);
        assertThat(planningCalls).hasValue(1);
        assertThat(codeCalls).hasValue(2);
        assertThat(processCalls).hasValue(3);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM study_room_steps WHERE run_id=?",
                                Integer.class,
                                confirmed.get("runId")))
                .isEqualTo(3);
    }
}
