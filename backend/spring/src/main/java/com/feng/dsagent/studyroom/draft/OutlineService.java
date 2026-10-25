package com.feng.dsagent.studyroom.draft;

import com.feng.dsagent.studyroom.generation.JdbcGenerationRepository;
import com.feng.dsagent.studyroom.fragment.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class OutlineService {
    private final JdbcOutlineRepository store;
    private final JdbcGenerationRepository jobs;
    private final OutlineJson json;
    private final OutlineValidator validator;
    private final OutlineFeasibilityService feasibility;
    private final ContentPlanCompiler compiler;
    private final JdbcFragmentRepository fragments;
    private final PreparationScheduler scheduler;
    private com.feng.dsagent.studyroom.events.ClassroomEventService events;
    @org.springframework.beans.factory.annotation.Autowired
    void events(com.feng.dsagent.studyroom.events.ClassroomEventService events) {this.events=events;}

    public OutlineService(
            JdbcOutlineRepository store,
            JdbcGenerationRepository jobs,
            OutlineJson json,
            OutlineValidator validator,
            OutlineFeasibilityService feasibility,
            ContentPlanCompiler compiler, JdbcFragmentRepository fragments, PreparationScheduler scheduler) {
        this.store = store;
        this.jobs = jobs;
        this.json = json;
        this.validator = validator;
        this.feasibility = feasibility;
        this.compiler = compiler;
        this.fragments = fragments;
        this.scheduler = scheduler;
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
            throw OutlineJson.error(
                    HttpStatus.BAD_REQUEST, "INVALID_OUTLINE_REQUEST", "请输入问题并选择详细程度");
        store.lockUser(user);
        String payload = json.write(Arrays.asList(question, depth, chapter, sourceRoom));
        String previous = store.key(user, "outline_create", key, payload);
        if (previous != null) return Map.of("draftId", previous);
        Map<String, Object> room = null;
        if (sourceRoom != null) {
            room =
                    store.row(
                            "SELECT * FROM study_rooms WHERE id=? AND user_id=? AND deleted_at IS NULL FOR UPDATE",
                            sourceRoom,
                            user);
            if (room == null)
                throw OutlineJson.error(HttpStatus.NOT_FOUND, "STUDYROOM_NOT_FOUND", "学习空间不存在");
            if (chapter == null) chapter = (String) room.get("chapter_id");
        }
        if (chapter != null
                && store.row("SELECT id FROM chapters WHERE id=? AND status='PUBLISHED'", chapter)
                        == null)
            throw OutlineJson.error(HttpStatus.BAD_REQUEST, "CHAPTER_NOT_FOUND", "章节不存在");
        String id = OutlineJson.id();
        store.db()
                .update(
                        "INSERT INTO"
                            + " study_outline_drafts(id,user_id,question,chapter_id,depth,status,source_room_id,source_run_id)"
                            + " VALUES(?,?,?,?,?,'GENERATING',?,?)",
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
        store.db().update("UPDATE study_outline_drafts SET pending_job_id=? WHERE id=?", job, id);
        store.message(id, "USER", question, 0);
        store.saveKey(user, "outline_create", key, payload, id);
        return Map.of("draftId", id);
    }

    public Map<String, Object> get(long user, String id) {
        var draft = store.owned(user, id, false);
        int revision = JdbcOutlineRepository.number(draft, "latest_revision");
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
                        : store.row(
                                "SELECT room_id FROM studyroom_runs WHERE id=? AND user_id=?",
                                draft.get("confirmed_run_id"),
                                user);
        result.put("confirmedRoomId", confirmed == null ? null : confirmed.get("room_id"));
        var saved = revision == 0 ? null : store.revision(id, revision);
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
        result.put(
                "messages",
                store.db()
                        .queryForList(
                                "SELECT role,content,revision FROM study_outline_messages WHERE"
                                        + " draft_id=? ORDER BY message_order",
                                id));
        result.put(
                "revisions",
                store.db()
                        .queryForList(
                                "SELECT revision,base_revision,feedback,created_at FROM"
                                    + " study_outline_revisions WHERE draft_id=? ORDER BY revision",
                                id));
        var last =
                store.row(
                        "SELECT status,failure_code,failure_message FROM studyroom_generation_jobs"
                            + " WHERE draft_id=? AND job_type IN ('PLAN','REVISE') ORDER BY created_at DESC,id"
                            + " DESC LIMIT 1",
                        id);
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
            throw OutlineJson.error(
                    HttpStatus.BAD_REQUEST, "INVALID_OUTLINE_FEEDBACK", "请输入修改意见，最多4000字");
        store.lockUser(user);
        String payload = json.write(Arrays.asList(id, base, feedback, depth));
        String prior = store.key(user, "outline_revise", key, payload);
        if (prior != null) return Map.of("draftId", id, "jobId", prior);
        var draft = store.owned(user, id, true);
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
        store.db()
                .update(
                        "UPDATE study_outline_drafts SET"
                            + " depth=?,status='GENERATING',pending_job_id=?,updated_at=CURRENT_TIMESTAMP"
                            + " WHERE id=?",
                        target.name(),
                        job,
                        id);
        store.message(id, "USER", feedback, base);
        store.db().update("UPDATE study_outline_drafts SET activity_at=CURRENT_TIMESTAMP WHERE id=?",id);
        store.saveKey(user, "outline_revise", key, payload, job);
        return Map.of("draftId", id, "jobId", job);
    }

    @Transactional
    public Map<String, Object> manualRevision(long user, String id, int base,
            List<OutlineController.ManualUnit> edits, String key) {
        store.owned(user,id,false);
        store.lockUser(user);
        String payload=json.write(Arrays.asList(id,base,edits));
        String prior=store.key(user,"outline_manual",key,payload);
        if(prior!=null) return Map.of("draftId",id,"revision",Integer.parseInt(prior));
        var draft=store.owned(user,id,true);
        requireEditable(draft,base);
        OutlineJson.require(!"GENERATING".equals(draft.get("status")),"OUTLINE_GENERATING","大纲正在生成，请等待完成");
        OutlineJson.require(draft.get("confirmed_run_id")==null && !"CONFIRMED".equals(draft.get("status")),"OUTLINE_ALREADY_CONFIRMED","已确认的大纲不能编辑");
        var before=json.read((String)store.revision(id,base).get("outline_json"),ReviewedOutline.class);
        var originals=new HashMap<String,ReviewedOutline.Unit>();
        before.units().forEach(u->originals.put(u.id(),u));
        if(edits==null || edits.isEmpty() || edits.size()>24)
            throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_OUTLINE_REQUEST","知识点数量必须为1到24");
        var units=new ArrayList<ReviewedOutline.Unit>();
        for(var edit:edits) {
            if(edit==null) throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_OUTLINE_REQUEST","知识点不能为空");
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
        catch(IllegalArgumentException error) { throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_OUTLINE_REQUEST",error.getMessage()); }
        var assessment=feasibility.assess(edited,(String)draft.get("question"),(String)draft.get("chapter_id"));
        try {
            validator.validate(assessment.outline(),before.depth());
            compiler.preview(assessment.outline());
            compiler.hash(assessment.outline(),json);
        } catch(IllegalArgumentException error) { throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_OUTLINE_REQUEST",error.getMessage()); }
        int revision=base+1;
        store.db().update("INSERT INTO study_outline_revisions(id,draft_id,revision,base_revision,outline_json,issues_json,change_summary_json,feedback,curriculum_version) VALUES(?,?,?,?,?,?,?,?,?)",
            OutlineJson.id(),id,revision,base,json.write(assessment.outline()),json.write(assessment.issues()),
            json.write(OutlineDiff.between(before,assessment.outline())),"人工编辑",assessment.outline().curriculumVersion());
        store.db().update("UPDATE study_outline_drafts SET latest_revision=?,status='REVIEWABLE',updated_at=CURRENT_TIMESTAMP,activity_at=CURRENT_TIMESTAMP WHERE id=?",revision,id);
        store.message(id,"USER","人工编辑大纲",revision);
        store.saveKey(user,"outline_manual",key,payload,Integer.toString(revision));
        return Map.of("draftId",id,"revision",revision);
    }

    /** Records a targeted fix as the next revision, so the plan hash and the taught fragments
     * are both compiled from the repaired outline rather than from the blocked one. */
    @Transactional
    public Map<String, Object> repair(long user, String id, int base,
            List<OutlineController.RepairFix> fixes, String key) {
        store.owned(user,id,false);
        store.lockUser(user);
        String payload=json.write(Arrays.asList(id,base,fixes));
        String prior=store.key(user,"outline_repair",key,payload);
        if(prior!=null) return Map.of("draftId",id,"revision",Integer.parseInt(prior));
        var draft=store.owned(user,id,true);
        requireEditable(draft,base);
        OutlineJson.require(draft.get("confirmed_run_id")==null && !"CONFIRMED".equals(draft.get("status")),"OUTLINE_ALREADY_CONFIRMED","已确认的大纲不能修改");
        if(fixes==null||fixes.isEmpty())
            throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_OUTLINE_REQUEST","请选择要修复的问题");
        var before=json.read((String)store.revision(id,base).get("outline_json"),ReviewedOutline.class);
        var found=feasibility.assess(before,(String)draft.get("question"),(String)draft.get("chapter_id")).issues();
        var activity=new LinkedHashMap<String,LinkedHashSet<String>>();
        before.units().forEach(unit->activity.put(unit.id(),new LinkedHashSet<>(unit.activities())));
        var notes=new ArrayList<String>();
        for(var fix:fixes) {
            if(fix==null||fix.code()==null)
                throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_OUTLINE_REQUEST","修复项不能为空");
            var issue=found.stream().filter(i->i.code().equals(fix.code())&&Objects.equals(i.unitId(),fix.unitId())).findFirst()
                .orElseThrow(()->OutlineJson.error(HttpStatus.CONFLICT,"REPAIR_NOT_APPLICABLE","该问题已不存在，请刷新最新大纲后重试"));
            OutlineJson.require("BLOCKER".equals(issue.severity()),"REPAIR_NOT_APPLICABLE","只有阻断问题可以定向修复");
            String addition;
            if("PROCESS_ACTIVITY_MISSING".equals(issue.code())) addition="PROCESS";
            else if("IMPLEMENTATION_ACTIVITY_MISSING".equals(issue.code())) addition="IMPLEMENTATION";
            else throw OutlineJson.error(HttpStatus.BAD_REQUEST,"REPAIR_NOT_SUPPORTED","该问题无法自动修复，请调整大纲或缩小学习范围");
            var unit=before.units().stream().filter(u->u.id().equals(issue.unitId())).findFirst()
                .orElseThrow(()->OutlineJson.error(HttpStatus.CONFLICT,"REPAIR_NOT_APPLICABLE","该问题已不存在，请刷新最新大纲后重试"));
            if(activity.get(unit.id()).add(addition)) notes.add(unit.title()+"："+activityNote(addition));
        }
        OutlineJson.require(!notes.isEmpty(),"REPAIR_NOT_APPLICABLE","该问题已不存在，请刷新最新大纲后重试");
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
        catch(IllegalArgumentException error) { throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_OUTLINE_REQUEST",error.getMessage()); }
        var assessment=feasibility.assess(edited,(String)draft.get("question"),(String)draft.get("chapter_id"));
        try {
            validator.validate(assessment.outline(),before.depth());
            compiler.preview(assessment.outline());
            compiler.hash(assessment.outline(),json);
        } catch(IllegalArgumentException error) { throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_OUTLINE_REQUEST",error.getMessage()); }
        int revision=base+1;
        String note="定向修复："+String.join("；",notes);
        store.db().update("INSERT INTO study_outline_revisions(id,draft_id,revision,base_revision,outline_json,issues_json,change_summary_json,feedback,curriculum_version) VALUES(?,?,?,?,?,?,?,?,?)",
            OutlineJson.id(),id,revision,base,json.write(assessment.outline()),json.write(assessment.issues()),
            json.write(OutlineDiff.between(before,assessment.outline())),note,assessment.outline().curriculumVersion());
        store.db().update("UPDATE study_outline_drafts SET latest_revision=?,status='REVIEWABLE',updated_at=CURRENT_TIMESTAMP,activity_at=CURRENT_TIMESTAMP WHERE id=?",revision,id);
        store.message(id,"USER",note,revision);
        store.saveKey(user,"outline_repair",key,payload,Integer.toString(revision));
        return Map.of("draftId",id,"revision",revision);
    }

    private static String activityNote(String activity) {
        return "PROCESS".equals(activity)?"补充过程讲解":"补充代码讲解";
    }

    private void requireEditable(Map<String, Object> draft, int base) {
        OutlineJson.require(
                draft.get("pending_job_id") == null, "OUTLINE_GENERATING", "大纲正在生成，请等待完成");
        OutlineJson.require(
                JdbcOutlineRepository.number(draft, "latest_revision") == base && base > 0,
                "OUTLINE_VERSION_CONFLICT",
                "大纲已更新，请重新审阅最新版本");
    }

    public void complete(Map<String, Object> job, String raw) {
        var snapshot =
                store.owned(
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
                            store.lockUser(user);
                            var draft = store.owned(user, (String) job.get("draft_id"), true);
                            OutlineJson.require(
                                    jobs.active(job)
                                            && Objects.equals(
                                                    draft.get("pending_job_id"), job.get("id")),
                                    "OUTLINE_JOB_STALE",
                                    "大纲生成任务已失效");
                            int base = JdbcOutlineRepository.number(draft, "latest_revision");
                            var payload = json.object((String) job.get("payload_json"));
                            OutlineJson.require(
                                    ((Number) payload.get("baseRevision")).intValue() == base,
                                    "OUTLINE_VERSION_CONFLICT",
                                    "大纲版本已改变");
                            var before =
                                    base == 0
                                            ? null
                                            : json.read(
                                                    (String)
                                                            store.revision(
                                                                            (String)
                                                                                    draft.get("id"),
                                                                            base)
                                                                    .get("outline_json"),
                                                    ReviewedOutline.class);
                            int revision = base + 1;
                            store.db()
                                    .update(
                                            "INSERT INTO"
                                                + " study_outline_revisions(id,draft_id,revision,base_revision,outline_json,issues_json,change_summary_json,feedback,curriculum_version)"
                                                + " VALUES(?,?,?,?,?,?,?,?,?)",
                                            OutlineJson.id(),
                                            draft.get("id"),
                                            revision,
                                            base,
                                            json.write(outline),
                                            json.write(assessment.issues()),
                                            json.write(OutlineDiff.between(before, outline)),
                                            payload.get("feedback"),
                                            outline.curriculumVersion());
                            store.db()
                                    .update(
                                            "UPDATE study_outline_drafts SET"
                                                + " latest_revision=?,status='REVIEWABLE',pending_job_id=NULL,confirmed_run_id=NULL,updated_at=CURRENT_TIMESTAMP"
                                                + " WHERE id=?",
                                            revision,
                                            draft.get("id"));
                            store.message(
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
        store.lockUser(user);
        String payload = json.write(Arrays.asList(id, revision, planHash));
        String prior = store.key(user, "outline_confirm", key, payload);
        if (prior != null) return confirmation(prior);
        var draft = store.owned(user, id, true);
        requireEditable(draft, revision);
        OutlineJson.require(
                draft.get("confirmed_run_id") == null,
                "OUTLINE_ALREADY_CONFIRMED",
                "该大纲已确认，请修改后再提交");
        var saved = store.revision(id, revision);
        var outline = json.read((String) saved.get("outline_json"), ReviewedOutline.class);
        validator.validate(outline, LearningDepth.valueOf((String) draft.get("depth")));
        OutlineJson.require(Objects.equals(compiler.hash(outline,json),planHash),
            "OUTLINE_PLAN_CHANGED","讲解安排已改变，请重新审阅大纲");
        var assessment =
                feasibility.assess(
                        outline, (String) draft.get("question"), (String) draft.get("chapter_id"));
        OutlineJson.require(
                assessment.issues().stream().noneMatch(i -> "BLOCKER".equals(i.severity())),
                "OUTLINE_MATERIALS_UNAVAILABLE",
                "必要材料不可用，请先修改大纲");
        String room = (String) draft.get("source_room_id");
        int runRevision = 1;
        if (room == null) {
            room = OutlineJson.id();
            store.db()
                    .update(
                            "INSERT INTO"
                                + " study_rooms(id,user_id,chapter_id,title,initial_question,summary)"
                                + " VALUES(?,?,?,?,?,?)",
                            room,
                            user,
                            draft.get("chapter_id"),
                            outline.title(),
                            draft.get("question"),
                            String.join("；", outline.objectives()));
        } else {
            var current =
                    store.row(
                            "SELECT * FROM study_rooms WHERE id=? AND user_id=? AND deleted_at IS NULL FOR UPDATE",
                            room,
                            user);
            OutlineJson.require(
                    current != null
                            && Objects.equals(
                                    current.get("current_run_id"), draft.get("source_run_id")),
                    "ROOM_RUN_CONFLICT",
                    "当前学习路线已改变，请重新规划");
            // The personal title belongs to the course across confirmed outline replacements.
            var previousTitle=store.row("SELECT d.display_title FROM study_outline_drafts d JOIN studyroom_runs r ON r.draft_id=d.id WHERE r.id=? AND d.user_id=?",current.get("current_run_id"),user);
            if(previousTitle!=null&&previousTitle.get("display_title")!=null)
                store.db().update("UPDATE study_outline_drafts SET display_title=COALESCE(display_title,?) WHERE id=?",previousTitle.get("display_title"),id);
            runRevision =
                    store.db()
                            .queryForObject(
                                    "SELECT COALESCE(MAX(run_revision),0)+1 FROM studyroom_runs"
                                            + " WHERE room_id=?",
                                    Integer.class,
                                    room);
        }
        String run = OutlineJson.id();
        store.db()
                .update(
                        "INSERT INTO"
                            + " studyroom_runs(id,room_id,user_id,draft_id,outline_revision,run_revision,status,approved_outline_json)"
                            + " VALUES(?,?,?,?,?,?,'PREPARING',?)",
                        run,
                        room,
                        user,
                        id,
                        revision,
                        runRevision,
                        json.write(outline));
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
            store.db()
                    .update(
                            "INSERT INTO"
                                + " studyroom_units(id,run_id,unit_key,unit_order,status,unit_spec_json,content_hash)"
                                + " VALUES(?,?,?,?,'PENDING',?,?)",
                            unitId,
                            run,
                            unit.id(),
                            order++,
                            spec,
                            contentHash);
            for(var plan:compiler.compile(outline.depth(),unit))fragments.insert(run,unitId,plan);
        }
        store.db()
                .update(
                        "UPDATE studyroom_generation_jobs SET"
                            + " status='CANCELLED',failure_code='RUN_SUPERSEDED',lease_until=NULL"
                            + " WHERE run_id IN (SELECT id FROM studyroom_runs WHERE room_id=? AND"
                            + " id<>?) AND status='QUEUED'",
                        room,
                        run);
        store.db()
                .update(
                        "UPDATE studyroom_runs SET status='SUPERSEDED' WHERE room_id=? AND id<>?",
                        room,
                        run);
        store.db()
                .update(
                        "UPDATE study_rooms SET"
                            + " current_run_id=?,current_step_id=NULL,title=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
                            + " WHERE id=?",
                        run,
                        outline.title(),
                        room);
        store.db()
                .update(
                        "UPDATE study_outline_drafts SET"
                            + " confirmed_run_id=?,source_room_id=?,source_run_id=?,status='CONFIRMED'"
                            + " WHERE id=?",
                        run,
                        room,
                        run,
                        id);
        store.db().update("UPDATE study_outline_drafts SET activity_at=CURRENT_TIMESTAMP WHERE id=?",id);
        store.saveKey(user, "outline_confirm", key, payload, run);
        scheduler.reconcile(user,run);
        return confirmation(run);
    }

    private Map<String, Object> confirmation(String run) {
        var row = store.row("SELECT room_id FROM studyroom_runs WHERE id=?", run);
        var unit =
                store.row(
                        "SELECT id FROM studyroom_units WHERE run_id=? ORDER BY unit_order LIMIT 1",
                        run);
        return Map.of("roomId", row.get("room_id"), "runId", run, "firstUnitId", unit.get("id"));
    }

    @Transactional
    public Map<String, Object> retry(long user, String id, String key) {
        store.lockUser(user);
        var draft = store.owned(user, id, true);
        String payload =
                json.write(List.of(id, JdbcOutlineRepository.number(draft, "latest_revision")));
        String prior = store.key(user, "outline_retry", key, payload);
        if (prior != null) return Map.of("draftId", id, "jobId", prior);
        OutlineJson.require(draft.get("pending_job_id") == null, "OUTLINE_GENERATING", "大纲仍在生成");
        var failed =
                store.row(
                        "SELECT * FROM studyroom_generation_jobs WHERE draft_id=? AND"
                            + " job_type IN ('PLAN','REVISE') AND status='FAILED' ORDER BY created_at DESC,id"
                            + " DESC LIMIT 1",
                        id);
        OutlineJson.require(failed != null, "OUTLINE_RETRY_UNAVAILABLE", "当前没有失败的大纲任务");
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
        store.db()
                .update(
                        "UPDATE study_outline_drafts SET"
                                + " depth=?,status='GENERATING',pending_job_id=? WHERE id=?",
                        targetDepth,
                        job,
                        id);
        store.db().update("UPDATE study_outline_drafts SET activity_at=CURRENT_TIMESTAMP WHERE id=?",id);
        store.saveKey(user, "outline_retry", key, payload, job);
        return Map.of("draftId", id, "jobId", job);
    }

    @Transactional
    public Map<String, Object> restore(long user, String id, int base, int target, String key) {
        store.lockUser(user);
        String payload = json.write(List.of(id, base, target));
        String prior = store.key(user, "outline_restore", key, payload);
        if (prior != null) {
            var existing =
                    store.row("SELECT revision FROM study_outline_revisions WHERE id=?", prior);
            return Map.of("draftId", id, "revision", existing.get("revision"));
        }
        var draft = store.owned(user, id, true);
        requireEditable(draft, base);
        var source = store.revision(id, target);
        OutlineJson.require(source != null, "OUTLINE_REVISION_NOT_FOUND", "历史版本不存在");
        var restored = json.read((String) source.get("outline_json"), ReviewedOutline.class);
        validator.validate(restored, restored.depth());
        var assessment =
                feasibility.assess(
                        restored, (String) draft.get("question"), (String) draft.get("chapter_id"));
        var before =
                json.read(
                        (String) store.revision(id, base).get("outline_json"),
                        ReviewedOutline.class);
        String revisionId = OutlineJson.id();
        int revision = base + 1;
        store.db()
                .update(
                        "INSERT INTO"
                            + " study_outline_revisions(id,draft_id,revision,base_revision,outline_json,issues_json,change_summary_json,feedback,curriculum_version)"
                            + " VALUES(?,?,?,?,?,?,?,?,?)",
                        revisionId,
                        id,
                        revision,
                        base,
                        json.write(assessment.outline()),
                        json.write(assessment.issues()),
                        json.write(OutlineDiff.between(before, assessment.outline())),
                        "恢复第" + target + "版",
                        assessment.outline().curriculumVersion());
        store.db()
                .update(
                        "UPDATE study_outline_drafts SET"
                            + " latest_revision=?,depth=?,status='REVIEWABLE',confirmed_run_id=NULL,updated_at=CURRENT_TIMESTAMP"
                            + " WHERE id=?",
                        revision,
                        restored.depth().name(),
                        id);
        store.message(id, "USER", "恢复第" + target + "版大纲", revision);
        store.db().update("UPDATE study_outline_drafts SET activity_at=CURRENT_TIMESTAMP WHERE id=?",id);
        store.saveKey(user, "outline_restore", key, payload, revisionId);
        return Map.of("draftId", id, "revision", revision);
    }
}
