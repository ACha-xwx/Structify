package com.feng.dsagent.studyroom.draft.service;


import com.feng.dsagent.studyroom.error.StudyroomErrorCode;
import com.feng.dsagent.studyroom.events.service.ClassroomEventService;

import com.feng.dsagent.studyroom.draft.dto.ManualUnitDTO;
import com.feng.dsagent.studyroom.draft.dto.RepairFixDTO;
import com.feng.dsagent.studyroom.draft.support.OutlineValidator;
import com.feng.dsagent.studyroom.draft.domain.ReviewedOutline;
import com.feng.dsagent.studyroom.draft.domain.LearningDepth;
import com.feng.dsagent.studyroom.draft.support.OutlineDiff;
import com.feng.dsagent.studyroom.draft.support.OutlineJson;

import com.feng.dsagent.studyroom.draft.service.OutlineFeasibilityService;

import com.feng.dsagent.studyroom.draft.mapper.OutlineMapper;
import com.feng.dsagent.studyroom.generation.service.GenerationJobService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import com.feng.dsagent.studyroom.fragment.support.ContentPlanCompiler;
import com.feng.dsagent.studyroom.fragment.mapper.FragmentLearningMapper;
import com.feng.dsagent.studyroom.fragment.worker.PreparationScheduler;
@Service
public class OutlineService {
    private final OutlineMapper outlines;
    private final GenerationJobService jobs;
    private final OutlineJson json;
    private final OutlineValidator validator;
    private final OutlineFeasibilityService feasibility;
    private final ContentPlanCompiler compiler;
    private final FragmentLearningMapper fragments;
    private final PreparationScheduler scheduler;
    private com.feng.dsagent.studyroom.events.service.ClassroomEventService events;
    @org.springframework.beans.factory.annotation.Autowired
    public void events(com.feng.dsagent.studyroom.events.service.ClassroomEventService events) {this.events=events;}

    public OutlineService(
            OutlineMapper outlines,
            GenerationJobService jobs,
            OutlineJson json,
            OutlineValidator validator,
            OutlineFeasibilityService feasibility,
            ContentPlanCompiler compiler, FragmentLearningMapper fragments, PreparationScheduler scheduler) {
        this.outlines = outlines;
        this.jobs = jobs;
        this.json = json;
        this.validator = validator;
        this.feasibility = feasibility;
        this.compiler = compiler;
        this.fragments = fragments;
        this.scheduler = scheduler;
    }

    private Map<String, Object> owned(long user, String id, boolean lock) {
        var row = outlines.selectOwnedDraft(id, user, lock);
        if (row == null)
            throw OutlineJson.error(StudyroomErrorCode.OUTLINE_NOT_FOUND);
        return row;
    }

    private String idempotency(long user, String operation, String key, String payload) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw OutlineJson.error(StudyroomErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        var prior = outlines.findIdempotency(user, operation, key);
        if (prior == null) return null;
        OutlineJson.require(Objects.equals(prior.get("payload_hash"), OutlineJson.hash(payload)), StudyroomErrorCode.IDEMPOTENCY_CONFLICT);
        return (String) prior.get("result_id");
    }

    private void saveKey(long user, String operation, String key, String payload, String result) {
        outlines.saveIdempotency(user, operation, key, OutlineJson.hash(payload), result);
    }

    private void message(String draft, String role, String content, int revision) {
        int order = outlines.nextMessageOrder(draft);
        outlines.insertMessage(OutlineJson.id(), draft, order, role, content, revision);
    }

    @Transactional
    public Map<String, Object> create(
            long user,
            String question,
            LearningDepth depth,
            String chapter,
            String sourceRoom,
            String key) {
        if (question == null || question.isBlank() || question.length() > 4000 || depth == null)
            throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_REQUEST);
        outlines.lockUser(user);
        String payload = json.write(Arrays.asList(question, depth, chapter, sourceRoom));
        String previous = idempotency(user, "outline_create", key, payload);
        if (previous != null) return Map.of("draftId", previous);
        Map<String, Object> room = null;
        if (sourceRoom != null) {
            room = outlines.selectOwnedRoom(sourceRoom, user, true);
            if (room == null)
                throw OutlineJson.error(StudyroomErrorCode.STUDYROOM_NOT_FOUND, "学习空间不存在");
            if (chapter == null) chapter = (String) room.get("chapter_id");
        }
        if (chapter != null && outlines.selectChapter(chapter) == null)
            throw OutlineJson.error(StudyroomErrorCode.CHAPTER_NOT_FOUND);
        String id = OutlineJson.id();
        outlines.insertDraft(
                id,
                user,
                question,
                chapter,
                depth.name(),
                sourceRoom,
                room == null ? null : room.get("current_run_id"));
        String job =
                jobs.enqueue(
                        user,
                        id,
                        null,
                        null,
                        "PLAN",
                        "plan:" + id,
                        Map.of("baseRevision", 0, "feedback", "", "depth", depth.name()));
        outlines.setPendingJob(job, id);
        message(id, "USER", question, 0);
        saveKey(user, "outline_create", key, payload, id);
        return Map.of("draftId", id);
    }

    public Map<String, Object> get(long user, String id) {
        var draft = owned(user, id, false);
        int revision = OutlineJson.number(draft, "latest_revision");
        var result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("question", draft.get("question"));
        result.put("depth", draft.get("depth"));
        result.put("status", draft.get("status"));
        result.put("latestRevision", revision);
        result.put("pendingJobId", draft.get("pending_job_id"));
        result.put("confirmedRunId", draft.get("confirmed_run_id"));
        result.put("sourceRoomId", draft.get("source_room_id"));
        var confirmed =
                draft.get("confirmed_run_id") == null
                        ? null
                        : outlines.selectConfirmedRoom(draft.get("confirmed_run_id"), user);
        result.put("confirmedRoomId", confirmed == null ? null : confirmed.get("room_id"));
        var saved = revision == 0 ? null : outlines.selectRevision(id, revision);
        result.put(
                "outline", saved == null ? null : json.value((String) saved.get("outline_json")));
        result.put(
                "issues",
                saved == null
                        ? List.of()
                        : feasibility
                                .assess(
                                        json.read(
                                                (String) saved.get("outline_json"),
                                                ReviewedOutline.class),
                                        (String) draft.get("question"),
                                        (String) draft.get("chapter_id"))
                                .issues());
        result.put(
                "changes",
                saved == null ? List.of() : json.value((String) saved.get("change_summary_json")));
        result.put("messages", outlines.selectMessages(id));
        result.put("revisions", outlines.selectRevisionSummaries(id));
        var last = outlines.selectLatestGeneration(id);
        result.put("generation", last);
        if(saved!=null) {
            var outline=json.read((String)saved.get("outline_json"),ReviewedOutline.class);
            try {
                result.put("fragmentPreview",compiler.preview(outline));
                result.put("planHash",compiler.hash(outline,json));
            } catch(IllegalArgumentException error) {
                var issues=new ArrayList<ReviewedOutline.Issue>((List<ReviewedOutline.Issue>)result.get("issues"));
                issues.add(new ReviewedOutline.Issue("OUTLINE_FRAGMENT_LIMIT",null,"BLOCKER",error.getMessage()));
                result.put("issues",issues);result.put("planHash",null);result.put("fragmentPreview",Map.of());
            }
        }
        return result;
    }

    @Transactional
    public Map<String, Object> revise(
            long user, String id, int base, String feedback, LearningDepth depth, String key) {
        if (feedback == null || feedback.isBlank() || feedback.length() > 4000)
            throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_FEEDBACK);
        outlines.lockUser(user);
        String payload = json.write(Arrays.asList(id, base, feedback, depth));
        String prior = idempotency(user, "outline_revise", key, payload);
        if (prior != null) return Map.of("draftId", id, "jobId", prior);
        var draft = owned(user, id, true);
        requireEditable(draft, base);
        var target = depth == null ? LearningDepth.valueOf((String) draft.get("depth")) : depth;
        String job =
                jobs.enqueue(
                        user,
                        id,
                        null,
                        null,
                        "REVISE",
                        "revise:" + id + ":" + base + ":" + OutlineJson.id(),
                        Map.of("baseRevision", base, "feedback", feedback, "depth", target.name()));
        outlines.setPendingJobGenerating(target.name(), job, id);
        message(id, "USER", feedback, base);
        outlines.touchActivity(id);
        saveKey(user, "outline_revise", key, payload, job);
        return Map.of("draftId", id, "jobId", job);
    }

    @Transactional
    public Map<String, Object> manualRevision(long user, String id, int base,
            List<ManualUnitDTO> edits, String key) {
        owned(user,id,false);
        outlines.lockUser(user);
        String payload=json.write(Arrays.asList(id,base,edits));
        String prior=idempotency(user,"outline_manual",key,payload);
        if(prior!=null) return Map.of("draftId",id,"revision",Integer.parseInt(prior));
        var draft=owned(user,id,true);
        requireEditable(draft,base);
        OutlineJson.require(!"GENERATING".equals(draft.get("status")), StudyroomErrorCode.OUTLINE_GENERATING);
        OutlineJson.require(draft.get("confirmed_run_id")==null && !"CONFIRMED".equals(draft.get("status")), StudyroomErrorCode.OUTLINE_ALREADY_CONFIRMED);
        var before=json.read((String)outlines.selectRevision(id,base).get("outline_json"),ReviewedOutline.class);
        var originals=new HashMap<String,ReviewedOutline.Unit>();
        before.units().forEach(u->originals.put(u.id(),u));
        if(edits==null || edits.isEmpty() || edits.size()>24)
            throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_REQUEST, "知识点数量必须为1到24");
        var units=new ArrayList<ReviewedOutline.Unit>();
        for(var edit:edits) {
            if(edit==null) throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_REQUEST, "知识点不能为空");
            var old=originals.get(edit.id());
            units.add(new ReviewedOutline.Unit(edit.id(),edit.title(),edit.objective(),
                old==null?List.of("manual:"+edit.id()):old.coverageKeys(),
                old==null?"EXPLAIN":old.expansion(),
                old==null?List.of("CONCEPT"):old.activities(),old==null?List.of():old.materials()));
        }
        var edited=new ReviewedOutline(before.schemaVersion(),before.depth(),before.title(),before.objectives(),
            before.prerequisites(),units,before.excludedCoverageKeys(),before.exclusions(),before.evidenceIds(),
            before.curriculumVersion(),before.coverageAssessment());
        try { validator.validate(edited,before.depth()); }
        catch(IllegalArgumentException error) { throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_REQUEST, error.getMessage()); }
        var assessment=feasibility.assess(edited,(String)draft.get("question"),(String)draft.get("chapter_id"));
        try {
            validator.validate(assessment.outline(),before.depth());
            compiler.preview(assessment.outline());
            compiler.hash(assessment.outline(),json);
        } catch(IllegalArgumentException error) { throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_REQUEST, error.getMessage()); }
        int revision=base+1;
        outlines.insertRevision(OutlineJson.id(),id,revision,base,json.write(assessment.outline()),json.write(assessment.issues()),
            json.write(OutlineDiff.between(before,assessment.outline())),"人工编辑",assessment.outline().curriculumVersion());
        outlines.setReviewable(revision,id);
        message(id,"USER","人工编辑大纲",revision);
        saveKey(user,"outline_manual",key,payload,Integer.toString(revision));
        return Map.of("draftId",id,"revision",revision);
    }

    /** Records a targeted fix as the next revision, so the plan hash and the taught fragments
     * are both compiled from the repaired outline rather than from the blocked one. */
    @Transactional
    public Map<String, Object> repair(long user, String id, int base,
            List<RepairFixDTO> fixes, String key) {
        owned(user,id,false);
        outlines.lockUser(user);
        String payload=json.write(Arrays.asList(id,base,fixes));
        String prior=idempotency(user,"outline_repair",key,payload);
        if(prior!=null) return Map.of("draftId",id,"revision",Integer.parseInt(prior));
        var draft=owned(user,id,true);
        requireEditable(draft,base);
        OutlineJson.require(draft.get("confirmed_run_id")==null && !"CONFIRMED".equals(draft.get("status")), StudyroomErrorCode.OUTLINE_ALREADY_CONFIRMED, "已确认的大纲不能修改");
        if(fixes==null||fixes.isEmpty())
            throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_REQUEST, "请选择要修复的问题");
        var before=json.read((String)outlines.selectRevision(id,base).get("outline_json"),ReviewedOutline.class);
        var found=feasibility.assess(before,(String)draft.get("question"),(String)draft.get("chapter_id")).issues();
        var activity=new LinkedHashMap<String,LinkedHashSet<String>>();
        before.units().forEach(unit->activity.put(unit.id(),new LinkedHashSet<>(unit.activities())));
        var notes=new ArrayList<String>();
        for(var fix:fixes) {
            if(fix==null||fix.code()==null)
                throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_REQUEST, "修复项不能为空");
            var issue=found.stream().filter(i->i.code().equals(fix.code())&&Objects.equals(i.unitId(),fix.unitId())).findFirst()
                .orElseThrow(()->OutlineJson.error(StudyroomErrorCode.REPAIR_NOT_APPLICABLE));
            OutlineJson.require("BLOCKER".equals(issue.severity()), StudyroomErrorCode.REPAIR_NOT_APPLICABLE, "只有阻断问题可以定向修复");
            String addition;
            if("PROCESS_ACTIVITY_MISSING".equals(issue.code())) addition="PROCESS";
            else if("IMPLEMENTATION_ACTIVITY_MISSING".equals(issue.code())) addition="IMPLEMENTATION";
            else throw OutlineJson.error(StudyroomErrorCode.REPAIR_NOT_SUPPORTED);
            var unit=before.units().stream().filter(u->u.id().equals(issue.unitId())).findFirst()
                .orElseThrow(()->OutlineJson.error(StudyroomErrorCode.REPAIR_NOT_APPLICABLE));
            if(activity.get(unit.id()).add(addition)) notes.add(unit.title()+"："+activityNote(addition));
        }
        OutlineJson.require(!notes.isEmpty(), StudyroomErrorCode.REPAIR_NOT_APPLICABLE);
        var units=new ArrayList<ReviewedOutline.Unit>();
        for(var unit:before.units()) {
            var acts=activity.get(unit.id());
            if(acts==null||acts.equals(new LinkedHashSet<>(unit.activities()))) {units.add(unit);continue;}
            units.add(new ReviewedOutline.Unit(unit.id(),unit.title(),unit.objective(),unit.coverageKeys(),
                unit.expansion(),List.copyOf(acts),unit.materials()));
        }
        var edited=new ReviewedOutline(before.schemaVersion(),before.depth(),before.title(),before.objectives(),
            before.prerequisites(),units,before.excludedCoverageKeys(),before.exclusions(),before.evidenceIds(),
            before.curriculumVersion(),before.coverageAssessment());
        try { validator.validate(edited,before.depth()); }
        catch(IllegalArgumentException error) { throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_REQUEST, error.getMessage()); }
        var assessment=feasibility.assess(edited,(String)draft.get("question"),(String)draft.get("chapter_id"));
        try {
            validator.validate(assessment.outline(),before.depth());
            compiler.preview(assessment.outline());
            compiler.hash(assessment.outline(),json);
        } catch(IllegalArgumentException error) { throw OutlineJson.error(StudyroomErrorCode.INVALID_OUTLINE_REQUEST, error.getMessage()); }
        int revision=base+1;
        String note="定向修复："+String.join("；",notes);
        outlines.insertRevision(OutlineJson.id(),id,revision,base,json.write(assessment.outline()),json.write(assessment.issues()),
            json.write(OutlineDiff.between(before,assessment.outline())),note,assessment.outline().curriculumVersion());
        outlines.setReviewable(revision,id);
        message(id,"USER",note,revision);
        saveKey(user,"outline_repair",key,payload,Integer.toString(revision));
        return Map.of("draftId",id,"revision",revision);
    }

    private static String activityNote(String activity) {
        return "PROCESS".equals(activity)?"补充过程讲解":"补充代码讲解";
    }

    private void requireEditable(Map<String, Object> draft, int base) {
        OutlineJson.require(
                draft.get("pending_job_id") == null, StudyroomErrorCode.OUTLINE_GENERATING);
        OutlineJson.require(
                OutlineJson.number(draft, "latest_revision") == base && base > 0, StudyroomErrorCode.OUTLINE_VERSION_CONFLICT);
    }

    public void complete(Map<String, Object> job, String raw) {
        var snapshot =
                owned(
                        ((Number) job.get("user_id")).longValue(),
                        (String) job.get("draft_id"),
                        false);
        var depth = LearningDepth.valueOf((String) snapshot.get("depth"));
        var parsed = json.read(raw, ReviewedOutline.class);
        validator.validate(parsed, depth);
        var assessment =
                feasibility.assess(
                        parsed,
                        (String) snapshot.get("question"),
                        (String) snapshot.get("chapter_id"));
        var outline = assessment.outline();
        validator.validate(outline, depth);
        jobs.tx()
                .executeWithoutResult(
                        status -> {
                            long user = ((Number) job.get("user_id")).longValue();
                            outlines.lockUser(user);
                            var draft = owned(user, (String) job.get("draft_id"), true);
                            OutlineJson.require(
                                    jobs.active(job)
                                            && Objects.equals(
                                                    draft.get("pending_job_id"), job.get("id")), StudyroomErrorCode.OUTLINE_JOB_STALE);
                            int base = OutlineJson.number(draft, "latest_revision");
                            var payload = json.object((String) job.get("payload_json"));
                            OutlineJson.require(
                                    ((Number) payload.get("baseRevision")).intValue() == base, StudyroomErrorCode.OUTLINE_VERSION_CONFLICT, "大纲版本已改变");
                            var before =
                                    base == 0
                                            ? null
                                            : json.read(
                                                    (String)
                                                            outlines.selectRevision(
                                                                            (String)
                                                                                    draft.get("id"),
                                                                            base)
                                                                    .get("outline_json"),
                                                    ReviewedOutline.class);
                            int revision = base + 1;
                            outlines.insertRevision(
                                            OutlineJson.id(),
                                            (String) draft.get("id"),
                                            revision,
                                            base,
                                            json.write(outline),
                                            json.write(assessment.issues()),
                                            json.write(OutlineDiff.between(before, outline)),
                                            payload.get("feedback"),
                                            outline.curriculumVersion());
                            outlines.setReviewableClearPending(revision, (String) draft.get("id"));
                            message(
                                    (String) draft.get("id"),
                                    "ASSISTANT",
                                    "第" + revision + "版大纲已生成，请审阅后确认。",
                                    revision);
                            if(events!=null)events.outlineUpdate(job,"outline_validated",Map.of("validatedRevision",revision),true);
                            jobs.finish(job);
                        });
    }

    @Transactional
    public Map<String, Object> confirm(long user, String id, int revision, String key, String planHash) {
        outlines.lockUser(user);
        String payload = json.write(Arrays.asList(id, revision, planHash));
        String prior = idempotency(user, "outline_confirm", key, payload);
        if (prior != null) return confirmation(prior);
        var draft = owned(user, id, true);
        requireEditable(draft, revision);
        OutlineJson.require(
                draft.get("confirmed_run_id") == null, StudyroomErrorCode.OUTLINE_ALREADY_CONFIRMED, "该大纲已确认，请修改后再提交");
        var saved = outlines.selectRevision(id, revision);
        var outline = json.read((String) saved.get("outline_json"), ReviewedOutline.class);
        validator.validate(outline, LearningDepth.valueOf((String) draft.get("depth")));
        OutlineJson.require(Objects.equals(compiler.hash(outline,json),planHash), StudyroomErrorCode.OUTLINE_PLAN_CHANGED);
        var assessment =
                feasibility.assess(
                        outline, (String) draft.get("question"), (String) draft.get("chapter_id"));
        OutlineJson.require(
                assessment.issues().stream().noneMatch(i -> "BLOCKER".equals(i.severity())), StudyroomErrorCode.OUTLINE_MATERIALS_UNAVAILABLE);
        String room = (String) draft.get("source_room_id");
        int runRevision = 1;
        if (room == null) {
            room = OutlineJson.id();
            outlines.insertRoom(
                            room,
                            user,
                            draft.get("chapter_id"),
                            outline.title(),
                            draft.get("question"),
                            String.join("；", outline.objectives()));
        } else {
            var current = outlines.selectRoomForUpdate(room, user);
            OutlineJson.require(
                    current != null
                            && Objects.equals(
                                    current.get("current_run_id"), draft.get("source_run_id")), StudyroomErrorCode.ROOM_RUN_CONFLICT);
            // The personal title belongs to the course across confirmed outline replacements.
            var previousTitle=outlines.selectPreviousDisplayTitle(current.get("current_run_id"),user);
            if(previousTitle!=null&&previousTitle.get("display_title")!=null)
                outlines.copyDisplayTitle(previousTitle.get("display_title"),id);
            runRevision = outlines.nextRunRevision(room);
        }
        String run = OutlineJson.id();
        outlines.insertRun(run, room, user, id, revision, runRevision, json.write(outline));
        int order = 0;
        for (var unit : outline.units()) {
            String unitId = OutlineJson.id(), spec = json.write(unit);
            String contentHash =
                    OutlineJson.hash(
                            json.write(
                                    Arrays.asList(
                                            "unit-v2",
                                            spec,
                                            outline.depth(),
                                            draft.get("question"),
                                            draft.get("chapter_id"))));
            outlines.insertUnit(unitId, run, unit.id(), order++, spec, contentHash);
            for(var plan:compiler.compile(outline.depth(),unit)) {
                String planJson=json.write(plan);
                fragments.insertFragment(OutlineJson.id(),run,unitId,plan.key(),plan.order(),planJson,OutlineJson.hash(planJson));
            }
        }
        outlines.cancelQueuedJobsForRoom(room, run);
        outlines.supersedeRuns(room, run);
        outlines.setCurrentRun(run, outline.title(), room);
        outlines.confirmDraft(run, room, id);
        outlines.touchActivity(id);
        saveKey(user, "outline_confirm", key, payload, run);
        scheduler.reconcile(user,run);
        return confirmation(run);
    }

    private Map<String, Object> confirmation(String run) {
        var row = outlines.selectRoomId(run);
        var unit = outlines.selectFirstUnit(run);
        return Map.of("roomId", row.get("room_id"), "runId", run, "firstUnitId", unit.get("id"));
    }

    @Transactional
    public Map<String, Object> retry(long user, String id, String key) {
        outlines.lockUser(user);
        var draft = owned(user, id, true);
        String payload =
                json.write(List.of(id, OutlineJson.number(draft, "latest_revision")));
        String prior = idempotency(user, "outline_retry", key, payload);
        if (prior != null) return Map.of("draftId", id, "jobId", prior);
        OutlineJson.require(draft.get("pending_job_id") == null, StudyroomErrorCode.OUTLINE_GENERATING, "大纲仍在生成");
        var failed = outlines.selectFailedJob(id);
        OutlineJson.require(failed != null, StudyroomErrorCode.OUTLINE_RETRY_UNAVAILABLE);
        String job =
                jobs.enqueue(
                        user,
                        id,
                        null,
                        null,
                        (String) failed.get("job_type"),
                        "retry:" + OutlineJson.id(),
                        json.object((String) failed.get("payload_json")));
        var retryPayload = json.object((String) failed.get("payload_json"));
        String targetDepth = (String) retryPayload.getOrDefault("depth", draft.get("depth"));
        outlines.setPendingJobGenerating(targetDepth, job, id);
        outlines.touchActivity(id);
        saveKey(user, "outline_retry", key, payload, job);
        return Map.of("draftId", id, "jobId", job);
    }

    @Transactional
    public Map<String, Object> restore(long user, String id, int base, int target, String key) {
        outlines.lockUser(user);
        String payload = json.write(List.of(id, base, target));
        String prior = idempotency(user, "outline_restore", key, payload);
        if (prior != null) {
            var existing = outlines.selectRevisionById(prior);
            return Map.of("draftId", id, "revision", existing.get("revision"));
        }
        var draft = owned(user, id, true);
        requireEditable(draft, base);
        var source = outlines.selectRevision(id, target);
        OutlineJson.require(source != null, StudyroomErrorCode.OUTLINE_REVISION_NOT_FOUND);
        var restored = json.read((String) source.get("outline_json"), ReviewedOutline.class);
        validator.validate(restored, restored.depth());
        var assessment =
                feasibility.assess(
                        restored, (String) draft.get("question"), (String) draft.get("chapter_id"));
        var before =
                json.read(
                        (String) outlines.selectRevision(id, base).get("outline_json"),
                        ReviewedOutline.class);
        String revisionId = OutlineJson.id();
        int revision = base + 1;
        outlines.insertRevision(
                        revisionId,
                        id,
                        revision,
                        base,
                        json.write(assessment.outline()),
                        json.write(assessment.issues()),
                        json.write(OutlineDiff.between(before, assessment.outline())),
                        "恢复第" + target + "版",
                        assessment.outline().curriculumVersion());
        outlines.setReviewableRestore(revision, restored.depth().name(), id);
        message(id, "USER", "恢复第" + target + "版大纲", revision);
        outlines.touchActivity(id);
        saveKey(user, "outline_restore", key, payload, revisionId);
        return Map.of("draftId", id, "revision", revision);
    }
}
