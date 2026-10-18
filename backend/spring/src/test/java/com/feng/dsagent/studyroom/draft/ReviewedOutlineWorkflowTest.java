package com.feng.dsagent.studyroom.draft;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.studyroom.generation.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.studyroom-generation.enabled=false",
            "spring.datasource.url=jdbc:h2:mem:reviewed-outline-workflow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        })
class ReviewedOutlineWorkflowTest {
    @Autowired OutlineService service;
    @Autowired org.springframework.test.web.servlet.MockMvc http;
    @Autowired com.feng.dsagent.security.JwtTokenService tokens;
    @Autowired StudyroomGenerationWorker worker;
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcGenerationRepository jobs;
    @Autowired GenerationCallService calls;
    @Autowired com.feng.dsagent.studyroom.StudyroomRepository rooms;
    @MockitoBean com.feng.dsagent.aiquota.AiQuotaExecutionService execution;
    @MockitoBean OutlineModelGateway model;
    @MockitoBean UnitGenerationService units;
    long user;
    static final String OUTLINE =
            """
{"schemaVersion":1,"depth":"STANDARD","title":"学习概念","objectives":["理解概念"],"prerequisites":[],"units":[{"id":"concept","title":"概念","objective":"理解概念","coverageKeys":["concept"],"expansion":"EXPLAIN","activities":["CONCEPT"],"materials":[]}],"excludedCoverageKeys":[],"exclusions":[],"evidenceIds":[],"curriculumVersion":null,"coverageAssessment":"EVIDENCE_ONLY"}
""";

    @BeforeEach
    void setup() {
        user = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        jdbc.update(
                "INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",
                user,
                user + "@outline.test",
                "hash");
        when(model.plan(anyLong(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(OUTLINE);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM users WHERE id=?", user);
    }

    String create() {
        return (String)
                service.create(
                                user,
                                "学习一个概念",
                                LearningDepth.STANDARD,
                                null,
                                null,
                                UUID.randomUUID().toString())
                        .get("draftId");
    }

    @Test
    void onlyPlansBeforeConfirmationAndConfirmationIsIdempotent() {
        String id = create();
        verifyNoInteractions(model, units);
        assertThat(service.get(user, id).get("status")).isEqualTo("GENERATING");
        assertThat(worker.runOnce()).isTrue();
        assertThat(service.get(user, id).get("latestRevision")).isEqualTo(1);
        verifyNoInteractions(units);
        var first = service.confirm(user, id, 1, "confirm-one");
        assertThat(service.confirm(user, id, 1, "confirm-one")).isEqualTo(first);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM studyroom_runs WHERE user_id=?",
                                Integer.class,
                                user))
                .isEqualTo(1);
    }

    @Test
    void rejectsStaleConfirmationAndKeepsLastGoodOutlineAfterFailedRevision() {
        String id = create();
        worker.runOnce();
        service.revise(user, id, 1, "换个例子", LearningDepth.DEEP, "revision-one");
        assertThatThrownBy(() -> service.confirm(user, id, 1, "confirm-pending"))
                .isInstanceOf(ApiException.class);
        when(model.plan(anyLong(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("模型不可用"));
        worker.runOnce();
        assertThat(service.get(user, id).get("latestRevision")).isEqualTo(1);
        assertThat(service.get(user, id).get("outline")).isNotNull();
        assertThat(service.get(user, id).get("depth")).isEqualTo("STANDARD");
        assertThat(service.confirm(user, id, 1, "confirm-preserved")).containsKey("runId");
        assertThatThrownBy(() -> service.get(user + 1, id)).isInstanceOf(ApiException.class);
    }

    @Test
    void conflictingIdempotencyPayloadDoesNotStartAnotherJob() {
        service.create(user, "概念一", LearningDepth.QUICK, null, null, "same-key");
        assertThatThrownBy(
                        () ->
                                service.create(
                                        user, "概念二", LearningDepth.QUICK, null, null, "same-key"))
                .isInstanceOf(ApiException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM studyroom_generation_jobs WHERE user_id=?",
                                Integer.class,
                                user))
                .isEqualTo(1);
    }

    @Test
    void confirmationQueuesOneUnitAndWorkerPublishesItOnlyOnce() {
        String id = create();
        worker.runOnce();
        var confirmed = service.confirm(user, id, 1, "confirm-body");
        when(units.generate(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(
                        """
{"version":1,"title":"概念","summary":"目标","evidenceIds":[],"steps":[{"id":"activity-1","type":"explanation","title":"概念","explanation":"概念讲解","resources":[]}]}
""");
        assertThat(worker.runOnce()).isTrue();
        assertThat(worker.runOnce()).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM studyroom_units WHERE id=?",
                                String.class,
                                confirmed.get("firstUnitId")))
                .isEqualTo("READY");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM study_room_steps WHERE run_id=?",
                                Integer.class,
                                confirmed.get("runId")))
                .isEqualTo(1);
        verify(units, times(1)).generate(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void invalidRevisionKeepsItsPredecessorAndIsNotConfirmableWhilePending() {
        String id = create();
        worker.runOnce();
        service.revise(user, id, 1, "增加实现", null, "invalid-revision");
        when(model.plan(anyLong(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn("{}");
        worker.runOnce();
        assertThat(service.get(user, id).get("latestRevision")).isEqualTo(1);
        assertThat(service.get(user, id).get("status")).isEqualTo("REVIEWABLE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM study_outline_revisions WHERE draft_id=?",
                                Integer.class,
                                id))
                .isEqualTo(1);
    }

    @Test
    void restoresAnEarlierOutlineAsANewImmutableRevision() {
        String id = create();
        worker.runOnce();
        service.revise(user, id, 1, "增加说明", null, "revise-restore");
        worker.runOnce();
        service.restore(user, id, 2, 1, "restore-one");
        assertThat(service.get(user, id).get("latestRevision")).isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM study_outline_revisions WHERE draft_id=?",
                                Integer.class,
                                id))
                .isEqualTo(3);
        assertThat(service.restore(user, id, 2, 1, "restore-one")).containsEntry("revision", 3);
        verify(model, times(2))
                .plan(anyLong(), anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void cachesCompletedModelCallsAndDoesNotReplayUncertainCalls() {
        String id = create();
        var job = jobs.claim(java.time.Instant.now());
        when(execution.complete(anyLong(), anyString(), anyString(), any()))
                .thenReturn(new com.feng.dsagent.model.ModelResponse("saved-response"));
        String jobId = (String) job.get("id");
        assertThat(calls.complete(user, jobId, "first", "system", "question", 100))
                .isEqualTo("saved-response");
        assertThat(calls.complete(user, jobId, "first", "system", "question", 100))
                .isEqualTo("saved-response");
        jdbc.update(
                "INSERT INTO"
                    + " studyroom_generation_calls(id,job_id,attempt,phase,status,quota_request_id)"
                    + " VALUES(?,?,1,'uncertain','SENT',?)",
                UUID.randomUUID().toString(),
                jobId,
                UUID.randomUUID().toString());
        assertThatThrownBy(
                        () -> calls.complete(user, jobId, "uncertain", "system", "question", 100))
                .isInstanceOf(ApiException.class);
        verify(execution, times(1)).complete(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void expiredSentCallFailsVisiblyWithoutAnotherModelRequest() {
        String id = create();
        var job = jobs.claim(java.time.Instant.now());
        String jobId = (String) job.get("id");
        jdbc.update(
                "INSERT INTO"
                    + " studyroom_generation_calls(id,job_id,attempt,phase,status,quota_request_id)"
                    + " VALUES(?,?,1,'outline','SENT',?)",
                UUID.randomUUID().toString(),
                jobId,
                UUID.randomUUID().toString());
        jdbc.update(
                "UPDATE studyroom_generation_jobs SET lease_until=? WHERE id=?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
                jobId);
        assertThat(worker.runOnce()).isFalse();
        assertThat(service.get(user, id).get("status")).isEqualTo("FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT failure_code FROM studyroom_generation_jobs WHERE id=?",
                                String.class,
                                jobId))
                .isEqualTo("MODEL_CALL_UNCERTAIN");
        verifyNoInteractions(execution, model, units);
    }

    @Test
    void httpEndpointsRequireOwnershipAndExplicitConfirmation() throws Exception {
        var auth = "Bearer " + tokens.issue(user, user + "@outline.test", Set.of("STUDENT"));
        http.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/v1/study-outlines")
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isUnauthorized());
        String id = create();
        http.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/v1/study-outlines/" + id + "/confirm")
                                .header("Authorization", auth)
                                .header("Idempotency-Key", "http-confirm")
                                .contentType("application/json")
                                .content("{\"revision\":1}"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict());
        http.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/v1/study-rooms")
                                .header("Authorization", auth)
                                .contentType("application/json")
                                .content("{\"question\":\"栈是什么\"}"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict());
        verifyNoInteractions(model, units);
    }

    @Test
    void replanSeparatesOldStepsAndRejectsOldProgressAndMessages() {
        when(units.generate(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(
                        "{\"version\":1,\"title\":\"概念\",\"summary\":\"目标\",\"evidenceIds\":[],\"steps\":[{\"id\":\"activity-1\",\"type\":\"explanation\",\"title\":\"概念\",\"explanation\":\"概念讲解\",\"resources\":[]}]}");
        String first = create();
        worker.runOnce();
        var old = service.confirm(user, first, 1, "old-confirm");
        worker.runOnce();
        String room = (String) old.get("roomId"), oldRun = (String) old.get("runId");
        String oldStep = rooms.findOwned(room, user).orElseThrow().steps().getFirst().id();
        String next =
                (String)
                        service.create(
                                        user,
                                        "换个例子",
                                        LearningDepth.STANDARD,
                                        null,
                                        room,
                                        "new-draft")
                                .get("draftId");
        worker.runOnce();
        var current = service.confirm(user, next, 1, "new-confirm");
        assertThat(current.get("roomId")).isEqualTo(room);
        assertThat(rooms.findOwned(room, user).orElseThrow().steps()).isEmpty();
        assertThat(rooms.updateProgress(user, room, oldStep)).isEmpty();
        assertThatThrownBy(() -> rooms.appendExchange(room, user, List.of(), List.of(), oldRun))
                .isInstanceOf(ApiException.class);
        worker.runOnce();
        assertThat(rooms.findOwned(room, user).orElseThrow().steps())
                .hasSize(1)
                .noneMatch(step -> step.id().equals(oldStep));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM study_room_steps WHERE study_room_id=?",
                                Integer.class,
                                room))
                .isEqualTo(2);
    }
}
