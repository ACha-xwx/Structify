package com.feng.dsagent.studyroom.library;

import com.feng.dsagent.studyroom.draft.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owned library projection. Publication timestamps deliberately do not define user activity. */
@Service
public class CourseLibraryService {
    private final JdbcOutlineRepository store;
    private final OutlineJson json;
    public CourseLibraryService(JdbcOutlineRepository store, OutlineJson json) {this.store=store;this.json=json;}
    public record Course(String id,String title,String status,String roomId,String runId,int totalPoints,
            int completedPoints,int readableFragments,String lastPointTitle,Instant lastLearningAt,Instant activityAt) {}

    @Transactional(readOnly=true)
    public List<Course> list(long user) {
        var courses=new ArrayList<Course>();
        for(var draft:store.db().queryForList("SELECT d.* FROM study_outline_drafts d WHERE d.user_id=? AND d.deleted_at IS NULL AND (d.confirmed_run_id IS NULL OR EXISTS (SELECT 1 FROM study_rooms r WHERE r.current_run_id=d.confirmed_run_id AND r.user_id=d.user_id AND r.deleted_at IS NULL))",user)) {
            String run=(String)draft.get("confirmed_run_id");
            var room=run==null?null:store.row("SELECT id,title,last_learning_at FROM study_rooms WHERE current_run_id=? AND user_id=? AND deleted_at IS NULL",run,user);
            int total=0,completed=0,readable=0,failed=0;String point=null;
            Instant learning=null;
            if(room!=null) {
                total=count("SELECT COUNT(*) FROM studyroom_units WHERE run_id=?",run);
                completed=count("SELECT COUNT(*) FROM studyroom_completed_knowledge WHERE run_id=? AND user_id=?",run,user);
                readable=count("SELECT COUNT(*) FROM studyroom_fragments WHERE run_id=? AND step_id IS NOT NULL",run);
                failed=count("SELECT COUNT(*) FROM studyroom_fragments WHERE run_id=? AND status='FAILED'",run);
                var progress=store.row("SELECT p.updated_at,p.fragment_id,u.unit_spec_json FROM studyroom_learning_progress p JOIN studyroom_units u ON u.id=p.unit_id WHERE p.user_id=? AND p.run_id=?",user,run);
                if(room.get("last_learning_at")!=null)learning=instant(room.get("last_learning_at"));
                if(progress!=null){if(learning==null&&progress.get("fragment_id")!=null)learning=instant(progress.get("updated_at"));point=(String)json.object((String)progress.get("unit_spec_json")).get("title");}
            }
            String title=(String)draft.get("display_title");
            if(title==null&&room!=null)title=(String)room.get("title");
            if(title==null) {
                var revision=store.revision((String)draft.get("id"),((Number)draft.get("latest_revision")).intValue());
                if(revision!=null)title=(String)json.object((String)revision.get("outline_json")).get("title");
            }
            if(title==null) {title=(String)draft.get("question");if(title.length()>160)title=title.substring(0,160);}
            String state=room==null?"DRAFT":total>0&&completed==total?"COMPLETED":failed>0?"NEEDS_ATTENTION":readable>0?"LEARNING":"PREPARING";
            Instant activity=instant(draft.get("activity_at"));if(learning!=null&&learning.isAfter(activity))activity=learning;
            courses.add(new Course((String)draft.get("id"),title,state,room==null?null:(String)room.get("id"),run,total,completed,readable,point,learning,activity));
        }
        courses.sort(Comparator.comparing(Course::activityAt).reversed().thenComparing(Course::id));
        return List.copyOf(courses);
    }
    private int count(String sql,Object...args){return store.db().queryForObject(sql,Integer.class,args);}
    private static Instant instant(Object value){return ((Timestamp)value).toInstant();}

    @Transactional
    public void rename(long user,String id,String title) {
        if(title==null||title.isBlank()||title.trim().length()>160)
            throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_COURSE_TITLE","课程名称须为 1–160 个字符");
        store.lockUser(user);visible(user,id);
        store.db().update("UPDATE study_outline_drafts SET display_title=? WHERE id=? AND user_id=?",title.trim(),id,user);
    }
    private Map<String,Object> visible(long user,String id) {
        var draft=store.owned(user,id,true);
        if(draft.get("confirmed_run_id")!=null&&store.row("SELECT id FROM study_rooms WHERE current_run_id=? AND user_id=? AND deleted_at IS NULL",draft.get("confirmed_run_id"),user)==null)
            throw OutlineJson.error(HttpStatus.NOT_FOUND,"COURSE_NOT_FOUND","课程不存在");
        return draft;
    }
    @Transactional
    public void delete(long user,String id) {
        store.lockUser(user);
        var owned=store.row("SELECT * FROM study_outline_drafts WHERE id=? AND user_id=? FOR UPDATE",id,user);
        if(owned==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"COURSE_NOT_FOUND","课程不存在");
        if(owned.get("deleted_at")!=null)return;
        var draft=visible(user,id);
        // An unconfirmed revision is only a draft: deleting it must not delete its source course.
        String room=draft.get("confirmed_run_id")==null?null:(String)draft.get("source_room_id");
        if(room==null) {
            cancel("draft_id=?",id);
            store.db().update("UPDATE study_outline_drafts SET deleted_at=CURRENT_TIMESTAMP,pending_job_id=NULL WHERE id=?",id);
        } else {
            cancel("draft_id IN (SELECT id FROM study_outline_drafts WHERE source_room_id=?)",room);
            store.db().update("UPDATE studyroom_runs SET status='CANCELLED',state_version=state_version+1 WHERE room_id=? AND user_id=?",room,user);
            store.db().update("UPDATE study_outline_drafts SET deleted_at=CURRENT_TIMESTAMP,pending_job_id=NULL WHERE source_room_id=? AND user_id=?",room,user);
            store.db().update("UPDATE study_rooms SET deleted_at=CURRENT_TIMESTAMP,current_run_id=NULL,current_step_id=NULL WHERE id=? AND user_id=?",room,user);
        }
    }
    private void cancel(String where,String value) {
        store.db().update("UPDATE studyroom_generation_jobs SET status='CANCELLED',lease_token=NULL,lease_until=NULL,failure_code='COURSE_DELETED',updated_at=CURRENT_TIMESTAMP WHERE ("+where+") AND status IN ('QUEUED','RUNNING')",value);
    }
}
