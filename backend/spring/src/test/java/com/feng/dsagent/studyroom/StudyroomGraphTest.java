package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.aiquota.AiQuotaExecution;
import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import com.feng.dsagent.knowledge.KnowledgeChunk;
import com.feng.dsagent.knowledge.KnowledgeSearchService;
import com.feng.dsagent.model.ModelClientException;
import com.feng.dsagent.model.ModelErrorCode;
import com.feng.dsagent.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StudyroomGraphTest {
    @Test
    void validatesSupplementAgainstRetrievedEvidenceRatherThanAnswerCitationSubset() {
        CapturingRepository repository = existingRoomRepository();
        AiQuotaExecution quota = (userId, operation, requestId, request) -> new ModelResponse("""
            {"version":1,"intent":"supplement","answer":"补充说明","evidenceIds":[],"supplement":{
              "version":1,"title":"补充","summary":"链表","evidenceIds":["ev-1"],"steps":[
                {"id":"extra","type":"ppt","title":"节点","explanation":"节点连接","resources":[
                  {"type":"ppt","id":"slide-1","parameters":{}}]}]}}
            """);
        graph(quota, repository).followUp(5L, "room-existing", "请展开链表", KnowledgeAudience.STUDENT, "citation-subset");
        assertThat(repository.appendedSteps).hasSize(1);
    }
    @Test
    void regeneratesTruncatedOutlineAndNeverPersistsPartialJson() {
        AtomicInteger calls = new AtomicInteger();
        CapturingRepository repository = new CapturingRepository(new AtomicInteger());
        AiQuotaExecution quota = (userId, operation, requestId, request) -> {
            assertThat(request.maxTokens()).isEqualTo(6000);
            if (calls.incrementAndGet() == 1) return new ModelResponse("{\"steps\":[", 4000L, "length");
            assertThat(operation).isEqualTo("studyroom-repair");
            assertThat(request.messages().getFirst().content()).contains("不减少资源");
            return new ModelResponse(VALID_OUTPUT, 700L, "stop");
        };
        graph(quota, repository).create(5L, "03-list", "链表怎么工作？", KnowledgeAudience.STUDENT, "truncated-1");
        assertThat(calls).hasValue(2);
        assertThat(repository.writes).hasValue(1);
    }

    @Test
    void reportsRepeatedTruncationAndDoesNotSaveAnInvalidRoom() {
        CapturingRepository repository = new CapturingRepository(new AtomicInteger());
        AiQuotaExecution quota = (userId, operation, requestId, request) -> new ModelResponse("{", 6000L, "length");
        assertThatThrownBy(() -> graph(quota, repository).create(5L, "03-list", "链表", KnowledgeAudience.STUDENT, "truncated-2"))
            .isInstanceOf(ApiException.class).hasMessageContaining("长度上限");
        assertThat(repository.writes).hasValue(0);
    }
    @Test
    void quotasKeepCreateAndFollowUpSeparateEvenWhenRequestIdsMatch() {
        assertThat(com.feng.dsagent.aiquota.AiQuotaRequestId.operationScoped("studyroom_create", "same-request"))
            .isNotEqualTo(com.feng.dsagent.aiquota.AiQuotaRequestId.operationScoped("studyroom_followup", "same-request"));
    }

    @Test
    void ranksAndBoundsCandidatesWithoutDroppingAnimationOrCodeKinds() {
        var candidates = new java.util.ArrayList<StudyroomResourceCatalog.Candidate>();
        for (int i = 0; i < 100; i++) candidates.add(new StudyroomResourceCatalog.Candidate(
            StudyroomModelOutput.ResourceType.PPT, "page-" + i, i == 99 ? "二叉树遍历" : "线性表", "正文".repeat(1000), List.of()));
        candidates.add(new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.ANIMATION, "tree.preorder", "二叉树先序遍历", "", List.of("initialData")));
        candidates.add(new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.C_CODE, "code-1", "二叉树遍历", "", List.of()));
        var selected = StudyroomGraph.selectPromptCandidates(candidates, "二叉树遍历");
        assertThat(selected).hasSize(14);
        assertThat(selected.getFirst().id()).isEqualTo("page-99");
        assertThat(selected).extracting(StudyroomResourceCatalog.Candidate::id).contains("tree.preorder", "code-1");
        assertThat(selected).allSatisfy(candidate -> assertThat(candidate.description().length()).isLessThanOrEqualTo(240));
    }

    @Test
    void plansBeforeSelectingResourcesAndPersistsBoundScenes() {
        AtomicInteger calls = new AtomicInteger();
        var repository = new CapturingRepository(new AtomicInteger());
        AiQuotaExecution quota = (userId, operation, requestId, request) -> {
            if (calls.incrementAndGet() == 1) return new ModelResponse("""
                {"schemaVersion":2,"title":"链表入门","summary":"理解节点","evidenceIds":["ev-1"],"steps":[{"id":"outline-1","title":"节点结构","objective":"理解节点连接","intent":"CONCEPT"}]}
                """);
            assertThat(operation).isEqualTo("studyroom_bind");
            assertThat(request.messages().getLast().content()).contains("理解节点连接", "slide-1");
            return new ModelResponse(VALID_OUTPUT.replace("\"resources\":[", "\"orchestration\":{\"schemaVersion\":2,\"objective\":\"理解节点连接\",\"intent\":\"CONCEPT\",\"primary\":{\"id\":\"main\",\"kind\":\"PPT\",\"purpose\":\"观察节点\",\"items\":[{\"resourceId\":\"slide-1\",\"explanation\":\"数据与后继指针\"}]},\"supplementary\":[]},\"resources\":["));
        };
        graph(quota, repository).create(5L, "03-list", "链表怎么工作", KnowledgeAudience.STUDENT, "planned");
        assertThat(calls).hasValue(2);
        assertThat(repository.findOwned(repository.savedRoom.id(), 5L).orElseThrow().steps().getFirst().orchestrationJson()).contains("理解节点连接");
    }

    @Test
    void doesNotFreezeUnspecifiedChapterToTheFirstEvidenceHit() {
        var repository = new CapturingRepository(new AtomicInteger());
        var catalog = new StudyroomResourceCatalog(List.of(new StudyroomResourceCatalog.Candidate(
            StudyroomModelOutput.ResourceType.PPT, "slide-1", "节点", "链表", List.of()))) {
            @Override public List<Candidate> candidates(String chapterId) {
                assertThat(chapterId).as("Only an explicitly selected chapter restricts the catalog").isNull();
                return super.candidates(chapterId);
            }
        };
        var graph = new StudyroomGraph((u,o,r,q) -> new ModelResponse(VALID_OUTPUT),
            new KnowledgeSearchService(List.of(new KnowledgeChunk("ev-1", "03-list", "链表", "链表", "textbook/03", null, "PUBLIC")), 0), catalog, repository, new ObjectMapper());
        graph.create(5L, null, "链表", KnowledgeAudience.STUDENT, "no-inferred-scope");
    }

    private static final String VALID_OUTPUT = """
        {"version":1,"title":"链表入门","summary":"理解节点和指针","evidenceIds":["ev-1"],"steps":[
          {"id":"outline-1","type":"ppt","title":"节点结构","explanation":"每个节点保存数据和后继地址。","resources":[
            {"type":"ppt","id":"slide-1","parameters":{}}]}]}
        """;

    @Test
    void createsRoomAfterEvidenceAndResourceValidationUsingOneQuotaCall() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        CapturingRepository repository = new CapturingRepository(writes);
        AiQuotaExecution quota = (userId, operation, requestId, request) -> {
            assertThat(userId).isEqualTo(5L);
            assertThat(operation).isEqualTo("studyroom_create");
            assertThat(requestId).isEqualTo("create-1");
            modelCalls.incrementAndGet();
            return new ModelResponse(VALID_OUTPUT);
        };
        StudyroomGraph graph = graph(quota, repository);

        StudyroomRecords.Aggregate result = graph.create(5L, "03-list", "链表怎么工作？", KnowledgeAudience.STUDENT, "create-1");

        assertThat(result.room().title()).isEqualTo("链表入门");
        assertThat(result.room().currentStepId()).isEqualTo(result.steps().getFirst().id());
        assertThat(result.steps()).hasSize(1);
        assertThat(result.messages()).extracting(StudyroomRecords.Message::role).containsExactly("user", "assistant");
        assertThat(modelCalls).hasValue(1);
        assertThat(writes).hasValue(1);
    }

    @Test
    void repairsAnInvalidResourceReferenceOnlyOnceBeforeSaving() {
        AtomicInteger modelCalls = new AtomicInteger();
        var invocationKeys = new java.util.HashSet<String>();
        CapturingRepository repository = new CapturingRepository(new AtomicInteger());
        AiQuotaExecution quota = (userId, operation, requestId, request) -> {
            assertThat(invocationKeys.add(com.feng.dsagent.aiquota.AiQuotaRequestId.operationScoped(operation, requestId)))
                .as("Each provider invocation must reserve a distinct quota key").isTrue();
            return new ModelResponse(modelCalls.incrementAndGet() == 1
                ? VALID_OUTPUT.replace("slide-1", "invented-slide") : VALID_OUTPUT);
        };
        StudyroomGraph graph = graph(quota, repository);

        graph.create(5L, "03-list", "链表怎么工作？", KnowledgeAudience.STUDENT, "create-2");

        assertThat(modelCalls).hasValue(2);
        assertThat(repository.savedRoom).isNotNull();
    }

    @Test
    void failsWithoutEvidenceAndDoesNotCallModelOrPersist() {
        AtomicInteger modelCalls = new AtomicInteger();
        CapturingRepository repository = new CapturingRepository(new AtomicInteger());
        AiQuotaExecution quota = (userId, operation, requestId, request) -> {
            modelCalls.incrementAndGet();
            return new ModelResponse(VALID_OUTPUT);
        };
        StudyroomGraph graph = new StudyroomGraph(
            quota,
            new KnowledgeSearchService(List.of(), 0),
            new StudyroomResourceCatalog(List.of()),
            repository,
            new ObjectMapper()
        );

        assertThatThrownBy(() -> graph.create(5L, "03-list", "没有资料的问题", KnowledgeAudience.STUDENT, "create-3"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("授权课程资料");
        assertThat(modelCalls).hasValue(0);
        assertThat(repository.savedRoom).isNull();
    }

    @Test
    void refusesToEraseResourceCommitmentsAfterRepairFailure() {
        AtomicInteger modelCalls = new AtomicInteger();
        CapturingRepository repository = new CapturingRepository(new AtomicInteger());
        AiQuotaExecution quota = (userId, operation, requestId, request) -> {
            modelCalls.incrementAndGet();
            return new ModelResponse(VALID_OUTPUT.replace("slide-1", "invented-slide"));
        };
        StudyroomGraph graph = graph(quota, repository);

        assertThatThrownBy(() -> graph.create(5L, "03-list", "链表怎么工作？", KnowledgeAudience.STUDENT, "create-4"))
            .isInstanceOf(ApiException.class);
        assertThat(modelCalls).hasValue(2);
        assertThat(repository.writes).hasValue(0);
    }

    @Test
    void resourceFallbackNeverBypassesEvidenceAuthorization() {
        CapturingRepository repository = new CapturingRepository(new AtomicInteger());
        AiQuotaExecution quota = (userId, operation, requestId, request) -> new ModelResponse(
            VALID_OUTPUT.replace("ev-1", "unauthorized-evidence").replace("slide-1", "invented-slide"));
        assertThatThrownBy(() -> graph(quota, repository).create(5L, "03-list", "链表", KnowledgeAudience.STUDENT, "invalid-evidence"))
            .isInstanceOf(ApiException.class);
        assertThat(repository.writes).hasValue(0);
    }

    @Test
    void modelFailureDoesNotPersistAnIncompleteRoom() {
        CapturingRepository repository = new CapturingRepository(new AtomicInteger());
        AiQuotaExecution quota = (userId, operation, requestId, request) -> {
            throw new ModelClientException(ModelErrorCode.MODEL_REQUEST_FAILED);
        };
        StudyroomGraph graph = graph(quota, repository);

        assertThatThrownBy(() -> graph.create(5L, "03-list", "链表怎么工作？", KnowledgeAudience.STUDENT, "create-5"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("模型服务暂时不可用");
        assertThat(repository.writes).hasValue(0);
        assertThat(repository.savedRoom).isNull();
    }

    @Test
    void answersFollowUpInTheExistingRoomWithoutAddingOutlineSteps() {
        CapturingRepository repository = existingRoomRepository();
        AiQuotaExecution quota = (userId, operation, requestId, request) -> {
            assertThat(operation).isEqualTo("studyroom_followup");
            assertThat(requestId).isEqualTo("follow-1");
            assertThat(request.messages().getLast().content()).contains("当前课堂 JSON", "初始问题", "当前结构");
            return new ModelResponse("""
                {"version":1,"intent":"answer","answer":"节点通过指针连接。","evidenceIds":["ev-1"],"supplement":null}
                """);
        };
        StudyroomGraph graph = graph(quota, repository);

        StudyroomRecords.Aggregate result = graph.followUp(5L, "room-existing", "指针如何连接？", KnowledgeAudience.STUDENT, "follow-1");

        assertThat(result.room().id()).isEqualTo("room-existing");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.messages()).extracting(StudyroomRecords.Message::role).containsExactly("assistant", "user", "assistant");
        assertThat(result.messages().getLast().content()).isEqualTo("节点通过指针连接。");
        assertThat(repository.appendedSteps).isEmpty();
    }

    @Test
    void appendsValidatedSupplementToCurrentStepOnlyWhenFollowUpRequestsDetail() {
        CapturingRepository repository = existingRoomRepository();
        AiQuotaExecution quota = (userId, operation, requestId, request) -> new ModelResponse("""
            {"version":1,"intent":"supplement","answer":"我用更细的示例说明。","evidenceIds":["ev-1"],"supplement":{
              "version":1,"title":"指针细化","summary":"节点链接过程","evidenceIds":["ev-1"],"steps":[
                {"id":"follow-step-1","type":"ppt","title":"链表连接演示","explanation":"新节点接到原链表尾部。","resources":[
                  {"type":"ppt","id":"slide-1","parameters":{}}]}]}}
            """);
        StudyroomGraph graph = graph(quota, repository);

        StudyroomRecords.Aggregate result = graph.followUp(5L, "room-existing", "请展开并用课件细讲", KnowledgeAudience.STUDENT, "follow-2");

        assertThat(result.room().id()).isEqualTo("room-existing");
        assertThat(result.steps()).hasSize(2);
        assertThat(repository.appendedSteps).singleElement().satisfies(step -> {
            assertThat(step.supplemental()).isTrue();
            assertThat(step.parentStepId()).isEqualTo("db-step-1");
            assertThat(step.stepOrder()).isEqualTo(0);
        });
        assertThat(result.messages()).hasSize(3);
    }

    @Test
    void fallsBackToSavingAnswerWhenSupplementReferencesUnauthorizedResource() {
        CapturingRepository repository = existingRoomRepository();
        AiQuotaExecution quota = (userId, operation, requestId, request) -> new ModelResponse("""
            {"version":1,"intent":"supplement","answer":"我先回答你的问题。","evidenceIds":["ev-1"],"supplement":{
              "version":1,"title":"细化","summary":"细化内容","evidenceIds":["ev-1"],"steps":[
                {"id":"step-invalid","type":"ppt","title":"伪造资源","explanation":"解释","resources":[
                  {"type":"ppt","id":"invented-slide","parameters":{}}]}]}}
            """);
        StudyroomGraph graph = graph(quota, repository);

        StudyroomRecords.Aggregate result = graph.followUp(5L, "room-existing", "请细讲", KnowledgeAudience.STUDENT, "follow-3");

        assertThat(result.steps()).hasSize(1);
        assertThat(repository.appendedSteps).isEmpty();
        assertThat(result.messages()).hasSize(3);
        assertThat(result.messages().getLast().content()).contains("未添加新步骤");
    }

    @Test
    void modelFailureDuringFollowUpDoesNotPersistAnIncompleteExchange() {
        CapturingRepository repository = existingRoomRepository();
        AiQuotaExecution quota = (userId, operation, requestId, request) -> {
            throw new ModelClientException(ModelErrorCode.MODEL_REQUEST_FAILED);
        };
        StudyroomGraph graph = graph(quota, repository);

        assertThatThrownBy(() -> graph.followUp(5L, "room-existing", "再解释一下", KnowledgeAudience.STUDENT, "follow-failure"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("模型服务暂时不可用");

        assertThat(repository.appendedSteps).isEmpty();
        assertThat(repository.savedMessages).hasSize(1);
    }

    private static CapturingRepository existingRoomRepository() {
        CapturingRepository repository = new CapturingRepository(new AtomicInteger());
        java.time.Instant now = java.time.Instant.now();
        repository.savedRoom = new StudyroomRecords.Room("room-existing", 5L, "03-list", "链表入门", "初始问题",
            "当前结构", "db-step-1", 1, now, now);
        repository.savedSteps = List.of(new StudyroomRecords.Step("db-step-1", "outline-1", "explanation", "初始步骤",
            "当前步骤讲解", "[\"ev-1\"]", "[]", 0, false, null, now));
        repository.savedMessages = List.of(new StudyroomRecords.Message("initial-message", "assistant", "欢迎", "[]", "db-step-1", now));
        return repository;
    }

    private static StudyroomGraph graph(AiQuotaExecution quota, CapturingRepository repository) {
        return new StudyroomGraph(
            quota,
            new KnowledgeSearchService(List.of(new KnowledgeChunk("ev-1", "03-list", "链表", "链表由节点和指针组成。", "textbook/03", null, "PUBLIC")), 0),
            new StudyroomResourceCatalog(List.of(new StudyroomResourceCatalog.Candidate(
                StudyroomModelOutput.ResourceType.PPT, "slide-1", "节点结构", "链表节点和指针", List.of()
            ))),
            repository,
            new ObjectMapper()
        );
    }

    private static final class CapturingRepository implements StudyroomRepository {

        private final AtomicInteger writes;
        private StudyroomRecords.Room savedRoom;
        private List<StudyroomRecords.Step> savedSteps = List.of();
        private List<StudyroomRecords.Message> savedMessages = List.of();
        private List<StudyroomRecords.Step> appendedSteps = List.of();

        private CapturingRepository(AtomicInteger writes) {
            this.writes = writes;
        }

        @Override
        public boolean isPublishedChapter(String chapterId) {
            return true;
        }

        @Override
        public void create(StudyroomRecords.Room room, List<StudyroomRecords.Step> steps, List<StudyroomRecords.Message> messages) {
            writes.incrementAndGet();
            savedRoom = room;
            savedSteps = List.copyOf(steps);
            savedMessages = List.copyOf(messages);
        }

        @Override
        public Optional<StudyroomRecords.Aggregate> findOwned(String studyroomId, long userId) {
            return savedRoom == null ? Optional.empty() : Optional.of(new StudyroomRecords.Aggregate(savedRoom, savedSteps, savedMessages));
        }

        @Override
        public void appendExchange(String studyroomId, long userId, List<StudyroomRecords.Message> messages, List<StudyroomRecords.Step> supplementalSteps) {
            appendedSteps = List.copyOf(supplementalSteps);
            List<StudyroomRecords.Step> combinedSteps = new ArrayList<>(savedSteps);
            combinedSteps.addAll(supplementalSteps);
            savedSteps = List.copyOf(combinedSteps);
            List<StudyroomRecords.Message> combinedMessages = new ArrayList<>(savedMessages);
            combinedMessages.addAll(messages);
            savedMessages = List.copyOf(combinedMessages);
        }

        @Override
        public Optional<StudyroomRecords.Room> updateProgress(long userId, String studyroomId, String stepId) {
            throw new UnsupportedOperationException();
        }
    }
}
