package com.feng.dsagent.studyroom.draft;

import com.feng.dsagent.aiquota.service.AiQuotaExecutionService;
import com.feng.dsagent.model.ModelResponse;
import com.feng.dsagent.security.service.JwtTokenService;
import com.feng.dsagent.studyroom.StudyroomRepository;
import com.feng.dsagent.studyroom.draft.controller.OutlineController;
import com.feng.dsagent.studyroom.draft.service.OutlineService;
import com.feng.dsagent.studyroom.fragment.FragmentPlan;
import com.feng.dsagent.studyroom.generation.service.GenerationCallService;

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
    @Autowired com.feng.dsagent.security.service.JwtTokenService tokens;
    @Autowired StudyroomGenerationWorker worker;
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcGenerationRepository jobs;
    @Autowired GenerationCallService calls;
    @Autowired com.feng.dsagent.studyroom.StudyroomRepository rooms;
    @MockitoBean com.feng.dsagent.aiquota.service.AiQuotaExecutionService execution;
    @MockitoBean OutlineModelGateway model;
    @Autowired OutlineJson json;
    long user;
    static final String OUTLINE =
            """
{"schemaVersion":1,"depth":"STANDARD","title":"学习概念","objectives":["理解概念"],"prerequisites":[],"units":[{"id":"concept","title":"概念","objective":"理解概念","coverageKeys":["concept"],"expansion":"EXPLAIN","activities":["CONCEPT"],"materials":[]}],"excludedCoverageKeys":[],"exclusions":[],"evidenceIds":[],"curriculumVersion":null,"coverageAssessment":"EVIDENCE_ONLY"}
""";

    /** A knowledge point whose coverage key demands a process activity the model never scheduled. */
    static final String BLOCKED_OUTLINE =
            """
{"schemaVersion":1,"depth":"STANDARD","title":"单链表","objectives":["掌握单链表"],"prerequisites":[],"units":[{"id":"head","title":"结点结构","objective":"说明结点结构","coverageKeys":["list.linked.singly.structure"],"expansion":"EXPLAIN","activities":["CONCEPT"],"materials":[]},{"id":"ops","title":"反转链表","objective":"实现反转","coverageKeys":["list.linked.singly.operations"],"expansion":"EXPLAIN","activities":["IMPLEMENTATION"],"materials":[]}],"excludedCoverageKeys":[],"exclusions":[],"evidenceIds":[],"curriculumVersion":null,"coverageAssessment":"EVIDENCE_ONLY"}
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
    void manualRevisionPreservesHistoryAndFreezesOrderWithoutModelCalls() {
        String id=create();
        assertThatThrownBy(()->service.manualRevision(user,id,1,List.of(new OutlineController.ManualUnit("concept","Title","Objective")),"pending")).isInstanceOf(ApiException.class);
        worker.runOnce();clearInvocations(model);
        var hash=service.get(user,id).get("planHash");
        var edits=List.of(new OutlineController.ManualUnit("new-unit","Added","Understand added"),
            new OutlineController.ManualUnit("concept","Edited","Understand edited"));
        var result=service.manualRevision(user,id,1,edits,"manual");
        assertThat(result).containsEntry("revision",2);
        assertThat(service.manualRevision(user,id,1,edits,"manual")).isEqualTo(result);
        verifyNoInteractions(model);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM studyroom_generation_jobs WHERE draft_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_outline_revisions WHERE draft_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(service.get(user,id).get("planHash")).isNotEqualTo(hash);
        assertThatThrownBy(()->service.manualRevision(user,id,1,edits,"stale")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.manualRevision(user+1,id,2,edits,"foreign")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.manualRevision(user,id,2,List.of(edits.get(0),edits.get(0)),"duplicate")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.manualRevision(user,id,2,List.of(),"empty")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.manualRevision(user,id,2,List.of(new OutlineController.ManualUnit("concept"," ","Objective")),"blank")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.manualRevision(user,id,2,List.of(new OutlineController.ManualUnit("invalid id","Title","Objective")),"bad-id")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.manualRevision(user,id,2,edits,"manual")).isInstanceOf(ApiException.class);
        var run=service.confirm(user,id,2,"confirm-manual",(String)service.get(user,id).get("planHash"));
        assertThat(jdbc.queryForList("SELECT unit_key FROM studyroom_units WHERE run_id=? ORDER BY unit_order",String.class,run.get("runId"))).containsExactly("new-unit","concept");
        assertThatThrownBy(()->service.manualRevision(user,id,2,edits,"confirmed")).isInstanceOf(ApiException.class);
    }

    @Test
    void onlyPlansBeforeConfirmationAndConfirmationIsIdempotent() {
        String id = create();
        verifyNoInteractions(model);
        assertThat(service.get(user, id).get("status")).isEqualTo("GENERATING");
        assertThat(worker.runOnce()).isTrue();
        assertThat(service.get(user, id).get("latestRevision")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM studyroom_fragments WHERE run_id IN (SELECT id FROM studyroom_runs WHERE user_id=?)",Integer.class,user)).isZero();
        var first = service.confirm(user, id, 1, "confirm-one", (String) service.get(user, id).get("planHash"));
        assertThat(service.confirm(user, id, 1, "confirm-one", (String) service.get(user, id).get("planHash"))).isEqualTo(first);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM studyroom_runs WHERE user_id=?",
                                Integer.class,
                                user))
                .isEqualTo(1);
    }

    @Test
    void confirmingARevisedCoursePreservesItsPersonalDisplayName() {
        String id=create();worker.runOnce();
        var first=service.confirm(user,id,1,"confirm-named",(String)service.get(user,id).get("planHash"));
        jdbc.update("UPDATE study_outline_drafts SET display_title='我的课程名' WHERE id=?",id);
        jdbc.update("UPDATE studyroom_generation_jobs SET status='CANCELLED' WHERE run_id=?",first.get("runId"));
        String revised=(String)service.create(user,"调整学习概念",LearningDepth.STANDARD,null,(String)first.get("roomId"),"named-revision").get("draftId");
        worker.runOnce();
        service.confirm(user,revised,1,"confirm-revised-name",(String)service.get(user,revised).get("planHash"));
        assertThat(jdbc.queryForObject("SELECT display_title FROM study_outline_drafts WHERE id=?",String.class,revised)).isEqualTo("我的课程名");
    }

    @Test
    void rejectsStaleConfirmationAndKeepsLastGoodOutlineAfterFailedRevision() {
        String id = create();
        worker.runOnce();
        service.revise(user, id, 1, "换个例子", LearningDepth.DEEP, "revision-one");
        assertThatThrownBy(() -> service.confirm(user, id, 1, "confirm-pending", (String) service.get(user, id).get("planHash")))
                .isInstanceOf(ApiException.class);
        when(model.plan(anyLong(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("模型不可用"));
        worker.runOnce();
        assertThat(service.get(user, id).get("latestRevision")).isEqualTo(1);
        assertThat(service.get(user, id).get("outline")).isNotNull();
        assertThat(service.get(user, id).get("depth")).isEqualTo("STANDARD");
        assertThat(service.confirm(user, id, 1, "confirm-preserved", (String) service.get(user, id).get("planHash"))).containsKey("runId");
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
        verifyNoInteractions(execution, model);
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
                                .content("{\"revision\":1,\"planHash\":\"" + service.get(user,id).get("planHash") + "\"}"))
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
                                .isNotFound());
        verifyNoInteractions(model);
    }

    @Test
    void courseLibraryHttpEndpointsRequireAuthenticationAndValidateTitles() throws Exception {
        var auth="Bearer "+tokens.issue(user,user+"@outline.test",Set.of("STUDENT"));
        String id=create();
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/courses"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/courses").header("Authorization",auth))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id").value(id));
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/courses/"+id).header("Authorization",auth).contentType("application/json").content("{\"title\":\"  \"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/courses/missing").header("Authorization",auth).contentType("application/json").content("{\"title\":\"课程\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aBlockedOutlineIsRepairedAsANewRevisionThatReallyTeachesTheMissingActivity() {
        when(model.plan(anyLong(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(BLOCKED_OUTLINE);
        String id = create();
        assertThat(worker.runOnce()).isTrue();
        var blocked = service.get(user, id);
        assertThat((List<ReviewedOutline.Issue>) blocked.get("issues"))
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("PROCESS_ACTIVITY_MISSING");
                    assertThat(issue.severity()).isEqualTo("BLOCKER");
                    assertThat(issue.unitId()).isEqualTo("ops");
                });
        var hash = blocked.get("planHash");
        var fixes = List.of(new OutlineController.RepairFix("PROCESS_ACTIVITY_MISSING", "ops"));
        var result = service.repair(user, id, 1, fixes, "repair-one");
        assertThat(result).containsEntry("revision", 2);
        assertThat(service.repair(user, id, 1, fixes, "repair-one")).isEqualTo(result);
        // The repaired outline is what gets stored, so the plan hash, the preview and the
        // fragments taught in the classroom are all compiled from it, not from the blocked one.
        var stored =
                json.read(
                        (String)
                                jdbc.queryForObject(
                                        "SELECT outline_json FROM study_outline_revisions WHERE"
                                                + " draft_id=? AND revision=2",
                                        String.class,
                                        id),
                        ReviewedOutline.class);
        var unit = stored.units().stream().filter(u -> u.id().equals("ops")).findFirst().orElseThrow();
        assertThat(unit.activities()).contains("PROCESS");
        var after = service.get(user, id);
        assertThat(after.get("planHash")).isNotEqualTo(hash);
        assertThat((List<ReviewedOutline.Issue>) after.get("issues"))
                .noneMatch(issue -> "BLOCKER".equals(issue.severity()));
        var preview =
                (Map<String, List<com.feng.dsagent.studyroom.fragment.FragmentPlan>>)
                        after.get("fragmentPreview");
        assertThat(preview.get("ops")).anySatisfy(plan -> assertThat(plan.activity()).isEqualTo("PROCESS"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT feedback FROM study_outline_revisions WHERE draft_id=? AND revision=2",
                                String.class,
                                id))
                .contains("补充过程讲解");
        service.confirm(user, id, 2, "confirm-repaired", (String) after.get("planHash"));
    }

    @Test
    void repairRefusesStaleForeignEmptyAndUnknownIssues() {
        when(model.plan(anyLong(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(BLOCKED_OUTLINE);
        String id = create();
        assertThat(worker.runOnce()).isTrue();
        var fixes = List.of(new OutlineController.RepairFix("PROCESS_ACTIVITY_MISSING", "ops"));
        assertThatThrownBy(() -> service.repair(user, id, 2, fixes, "stale")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.repair(user, id, 1, List.of(), "empty")).isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                service.repair(
                                        user,
                                        id,
                                        1,
                                        List.of(new OutlineController.RepairFix("PROCESS_ACTIVITY_MISSING", "absent")),
                                        "absent"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.repair(user + 1, id, 1, fixes, "foreign"))
                .isInstanceOf(ApiException.class);
        service.repair(user, id, 1, fixes, "repair-refused");
        service.confirm(user, id, 2, "confirm-refused", (String) service.get(user, id).get("planHash"));
        assertThatThrownBy(() -> service.repair(user, id, 2, fixes, "confirmed")).isInstanceOf(ApiException.class);
    }

}
