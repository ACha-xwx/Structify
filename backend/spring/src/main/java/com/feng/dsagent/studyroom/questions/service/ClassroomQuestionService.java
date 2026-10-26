package com.feng.dsagent.studyroom.questions.service;


import com.feng.dsagent.studyroom.error.StudyroomErrorCode;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.studyroom.events.service.ClassroomEventService;
import com.feng.dsagent.studyroom.service.StudyroomService;
import com.feng.dsagent.studyroom.questions.mapper.ClassroomQuestionMapper;
import com.feng.dsagent.knowledge.domain.KnowledgeAudience;
import java.util.*;
import org.springframework.stereotype.Service;
import com.feng.dsagent.studyroom.dto.StudyroomFollowUpContextDTO;
import com.feng.dsagent.studyroom.draft.mapper.OutlineMapper;
import com.feng.dsagent.studyroom.draft.support.OutlineJson;
import com.feng.dsagent.studyroom.generation.service.GenerationJobService;

@Service
public class ClassroomQuestionService {
    private final ClassroomQuestionMapper questions;
    private final OutlineMapper outlines;
    private final OutlineJson json;
    private final GenerationJobService jobs;
    private final ClassroomEventService events;
    private final StudyroomService rooms;

    public ClassroomQuestionService(
            ClassroomQuestionMapper questions,
            OutlineMapper outlines,
            OutlineJson json,
            GenerationJobService jobs,
            ClassroomEventService events,
            StudyroomService rooms) {
        this.questions = questions;
        this.outlines = outlines;
        this.json = json;
        this.jobs = jobs;
        this.events = events;
        this.rooms = rooms;
    }

    private String idempotency(long user, String operation, String key, String payload) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw OutlineJson.error(StudyroomErrorCode.IDEMPOTENCY_KEY_REQUIRED, "??????????? Idempotency-Key");
        var prior = outlines.findIdempotency(user, operation, key);
        if (prior == null) return null;
        OutlineJson.require(
                Objects.equals(prior.get("payload_hash"), OutlineJson.hash(payload)), StudyroomErrorCode.IDEMPOTENCY_CONFLICT, "??????????????");
        return (String) prior.get("result_id");
    }

    private void saveKey(long user, String operation, String key, String payload, String result) {
        outlines.saveIdempotency(user, operation, key, OutlineJson.hash(payload), result);
    }

    private Map<String, Object> owned(long user, String room, String id, boolean lock) {
        var question = questions.selectOwned(id, room, user, lock);
        if (question == null)
            throw OutlineJson.error(StudyroomErrorCode.QUESTION_NOT_FOUND, "???????");
        return question;
    }

    public Map<String, Object> create(
            long user,
            String room,
            String run,
            String fragment,
            int revision,
            String question,
            StudyroomFollowUpContextDTO context,
            String key,
            KnowledgeAudience audience) {
        if (question == null || question.isBlank() || question.length() > 4000)
            throw OutlineJson.error(StudyroomErrorCode.STUDYROOM_QUESTION_INVALID, "???????????4000?");
        return jobs.tx()
                .execute(
                        status -> {
                            outlines.lockUser(user);
                            String payload =
                                    json.write(
                                            Arrays.asList(
                                                    room,
                                                    run,
                                                    fragment,
                                                    revision,
                                                    question.strip(),
                                                    context));
                            String prior = idempotency(user, "question_create", key, payload);
                            if (prior != null) return receipt(user, room, prior);
                            var source = questions.selectFragmentSource(fragment, run, room, user);
                            if (source == null)
                                throw OutlineJson.error(StudyroomErrorCode.FRAGMENT_NOT_FOUND, "???????");
                            OutlineJson.require(
                                    source.get("step_id") != null
                                            && revision
                                                    == ((Number) source.get("content_revision"))
                                                            .intValue(), StudyroomErrorCode.FRAGMENT_REVISION_CHANGED, "???????????????");
                            String frozenContext = rooms.validateQuestionContext(user, room, context);
                            var parsed = json.object(frozenContext);
                            OutlineJson.require(
                                    Objects.equals(parsed.get("stepId"), source.get("step_id")), StudyroomErrorCode.STUDYROOM_CONTEXT_INVALID_CONFLICT, "??????????");
                            OutlineJson.require(
                                    questions.selectActiveQuestion(user, run) == null, StudyroomErrorCode.QUESTION_ALREADY_ACTIVE, "???????????");
                            String id = OutlineJson.id();
                            questions.insertQuestion(
                                    id,
                                    user,
                                    room,
                                    run,
                                    fragment,
                                    revision,
                                    question.strip(),
                                    frozenContext,
                                    audience.name(),
                                    OutlineJson.id(),
                                    OutlineJson.id());
                            String job =
                                    jobs.enqueue(
                                            user,
                                            (String) source.get("draft_id"),
                                            run,
                                            null,
                                            "FOLLOWUP",
                                            "question:" + id,
                                            Map.of("questionId", id));
                            questions.setJobPriority(job, 400);
                            questions.setQuestionJob(job, id);
                            events.open(
                                    user,
                                    "question:" + id,
                                    "QUESTION",
                                    id,
                                    room,
                                    job,
                                    1,
                                    run,
                                    revision,
                                    Map.of("questionId", id, "status", "QUEUED", "answer", ""));
                            saveKey(user, "question_create", key, payload, id);
                            return receipt(user, room, id);
                        });
    }

    private Map<String, Object> receipt(long user, String room, String id) {
        var row = owned(user, room, id, false);
        return Map.of(
                "questionId",
                id,
                "status",
                row.get("status"),
                "streamId",
                "question:" + id,
                "cursor",
                events.read(user, "question:" + id, 0).snapshot().cursor());
    }

    public String activeQuestion(long user, String room) {
        var row = questions.selectActiveQuestionInRoom(user, room);
        return row == null ? null : (String) row.get("id");
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 2000L)
    public void reconcileStoppedJobs() {
        for (var row : questions.selectStoppedQuestions()) {
            try {
                get(
                        ((Number) row.get("user_id")).longValue(),
                        (String) row.get("room_id"),
                        (String) row.get("id"));
            } catch (BizException inaccessible) {
                /* Deleted or superseded between reads. */
            }
        }
    }

    public Map<String, Object> get(long user, String room, String id) {
        return jobs.tx()
                .execute(
                        status -> {
                            outlines.lockUser(user);
                            var row = owned(user, room, id, false);
                            var job = questions.selectJobStatus(row.get("job_id"));
                            if (Set.of("QUEUED", "RUNNING").contains((String) row.get("status"))
                                    && job != null
                                    && Set.of("FAILED", "CANCELLED")
                                            .contains((String) job.get("status"))) {
                                var stream = events.read(user, "question:" + id, 0).snapshot();
                                if (!stream.terminal())
                                    events.append(
                                            user,
                                            "question:" + id,
                                            stream.operationId(),
                                            stream.attempt(),
                                            "CANCELLED".equals(job.get("status"))
                                                    ? "cancelled"
                                                    : "failed",
                                            Map.of("status", job.get("status")),
                                            Map.of(
                                                    "status",
                                                    job.get("status"),
                                                    "answer",
                                                    row.get("answer_text")),
                                            true);
                                questions.updateQuestionFromJob(
                                        job.get("status"),
                                        job.get("failure_code"),
                                        job.get("failure_message"),
                                        id);
                                row = owned(user, room, id, false);
                            }
                            var result = new LinkedHashMap<String, Object>(receipt(user, room, id));
                            result.put("question", row.get("question"));
                            result.put("answer", row.get("answer_text"));
                            result.put("context", json.object((String) row.get("context_json")));
                            result.put("runId", row.get("run_id"));
                            result.put("fragmentId", row.get("fragment_id"));
                            result.put("contentRevision", row.get("content_revision"));
                            result.put("actions", json.value((String) row.get("actions_json")));
                            if (row.get("failure_code") != null)
                                result.put(
                                        "error",
                                        Map.of(
                                                "code",
                                                row.get("failure_code"),
                                                "message",
                                                Objects.toString(
                                                        row.get("failure_message"), "?????")));
                            return result;
                        });
    }

    public Map<String, Object> cancel(long user, String room, String id) {
        return jobs.tx()
                .execute(
                        status -> {
                            outlines.lockUser(user);
                            var row = owned(user, room, id, true);
                            if (Set.of("QUEUED", "RUNNING").contains((String) row.get("status"))) {
                                var job = questions.selectJobForUpdate(row.get("job_id"));
                                events.append(
                                        user,
                                        "question:" + id,
                                        (String) row.get("job_id"),
                                        ((Number) job.get("attempt")).intValue(),
                                        "cancelled",
                                        Map.of("status", "CANCELLED"),
                                        Map.of(
                                                "status",
                                                "CANCELLED",
                                                "answer",
                                                row.get("answer_text")),
                                        true);
                                questions.cancelQuestion(id);
                                questions.cancelJob(row.get("job_id"));
                            }
                            return get(user, room, id);
                        });
    }
}
