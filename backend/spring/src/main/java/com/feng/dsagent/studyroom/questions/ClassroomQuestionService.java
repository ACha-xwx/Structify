package com.feng.dsagent.studyroom.questions;

import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.events.*;
import com.feng.dsagent.studyroom.generation.*;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ClassroomQuestionService {
    private final JdbcClassroomQuestionRepository questions;
    private final JdbcGenerationRepository jobs;
    private final ClassroomEventService events;
    private final StudyroomService rooms;
    public ClassroomQuestionService(JdbcClassroomQuestionRepository questions,JdbcGenerationRepository jobs,ClassroomEventService events,StudyroomService rooms) {
        this.questions=questions;this.jobs=jobs;this.events=events;this.rooms=rooms;
    }

    public Map<String,Object> create(long user,String room,String run,String fragment,int revision,String question,StudyroomFollowUpContext context,String key,KnowledgeAudience audience) {
        if(question==null || question.isBlank() || question.length()>4000)throw OutlineJson.error(HttpStatus.BAD_REQUEST,"STUDYROOM_QUESTION_INVALID","追问不能为空且不能超过4000字");
        return jobs.tx().execute(status -> {
            var store=questions.store();store.lockUser(user);
            String payload=questions.json().write(Arrays.asList(room,run,fragment,revision,question.strip(),context));
            String prior=store.key(user,"question_create",key,payload);
            if(prior!=null)return receipt(user,room,prior);
            var source=store.row("SELECT f.*,r.draft_id FROM studyroom_fragments f JOIN studyroom_runs r ON r.id=f.run_id JOIN study_rooms s ON s.current_run_id=r.id JOIN study_outline_drafts d ON d.id=r.draft_id WHERE f.id=? AND f.run_id=? AND s.id=? AND r.user_id=? AND s.deleted_at IS NULL AND d.deleted_at IS NULL",fragment,run,room,user);
            if(source==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"FRAGMENT_NOT_FOUND","课堂讲解不存在");
            OutlineJson.require(source.get("step_id")!=null && revision==((Number)source.get("content_revision")).intValue(),"FRAGMENT_REVISION_CHANGED","当前材料已更新，请重新选择引用");
            String frozenContext=rooms.validateQuestionContext(user,room,context);
            var parsed=questions.json().object(frozenContext);
            OutlineJson.require(Objects.equals(parsed.get("stepId"),source.get("step_id")),"STUDYROOM_CONTEXT_INVALID","引用与当前讲解不一致");
            OutlineJson.require(store.row("SELECT q.id FROM studyroom_questions q JOIN studyroom_generation_jobs j ON j.id=q.job_id WHERE q.user_id=? AND q.run_id=? AND q.status IN ('QUEUED','RUNNING') AND j.status IN ('QUEUED','RUNNING')",user,run)==null,"QUESTION_ALREADY_ACTIVE","请先等待或停止当前回答");
            String id=OutlineJson.id();
            store.db().update("INSERT INTO studyroom_questions(id,user_id,room_id,run_id,fragment_id,content_revision,question,context_json,audience,status,answer_text,sources_json,actions_json,assistant_message_id,user_message_id) VALUES(?,?,?,?,?,?,?,?,?,'QUEUED','','[]','[]',?,?)",id,user,room,run,fragment,revision,question.strip(),frozenContext,audience.name(),OutlineJson.id(),OutlineJson.id());
            String job=jobs.enqueue(user,(String)source.get("draft_id"),run,null,"FOLLOWUP","question:"+id,Map.of("questionId",id));
            store.db().update("UPDATE studyroom_generation_jobs SET priority=400 WHERE id=?",job);
            store.db().update("UPDATE studyroom_questions SET job_id=? WHERE id=?",job,id);
            events.open(user,"question:"+id,"QUESTION",id,room,job,1,run,revision,Map.of("questionId",id,"status","QUEUED","answer",""));
            store.saveKey(user,"question_create",key,payload,id);
            return receipt(user,room,id);
        });
    }

    private Map<String,Object> receipt(long user,String room,String id) {
        var row=questions.owned(user,room,id,false);
        return Map.of("questionId",id,"status",row.get("status"),"streamId","question:"+id,"cursor",events.read(user,"question:"+id,0).snapshot().cursor());
    }

    public String activeQuestion(long user,String room) {
        var row=questions.store().row("SELECT q.id FROM studyroom_questions q JOIN study_rooms s ON s.id=q.room_id AND s.current_run_id=q.run_id WHERE q.user_id=? AND q.room_id=? AND q.status IN ('QUEUED','RUNNING') AND s.deleted_at IS NULL ORDER BY q.created_at DESC LIMIT 1",user,room);
        return row==null?null:(String)row.get("id");
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=2000L)
    void reconcileStoppedJobs() {
        for(var row:questions.store().db().queryForList("SELECT q.id,q.user_id,q.room_id FROM studyroom_questions q JOIN studyroom_generation_jobs j ON j.id=q.job_id JOIN study_rooms s ON s.id=q.room_id AND s.current_run_id=q.run_id WHERE q.status IN ('QUEUED','RUNNING') AND j.status IN ('FAILED','CANCELLED') AND s.deleted_at IS NULL")) {
            try {get(((Number)row.get("user_id")).longValue(),(String)row.get("room_id"),(String)row.get("id"));}
            catch(com.feng.dsagent.common.ApiException inaccessible) { /* Deleted or superseded between reads. */ }
        }
    }

    public Map<String,Object> get(long user,String room,String id) {
        return jobs.tx().execute(status -> {
            questions.store().lockUser(user);
            var row=questions.owned(user,room,id,false);
            // A recovered uncertain invocation is a failed question, never an automatic re-run.
            var job=questions.store().row("SELECT status,failure_code,failure_message FROM studyroom_generation_jobs WHERE id=?",row.get("job_id"));
            if(Set.of("QUEUED","RUNNING").contains((String)row.get("status")) && job!=null && Set.of("FAILED","CANCELLED").contains((String)job.get("status"))) {
                var stream=events.read(user,"question:"+id,0).snapshot();
                if(!stream.terminal())events.append(user,"question:"+id,stream.operationId(),stream.attempt(),"CANCELLED".equals(job.get("status"))?"cancelled":"failed",Map.of("status",job.get("status")),Map.of("status",job.get("status"),"answer",row.get("answer_text")),true);
                questions.store().db().update("UPDATE studyroom_questions SET status=?,failure_code=?,failure_message=? WHERE id=?",job.get("status"),job.get("failure_code"),job.get("failure_message"),id);
                row=questions.owned(user,room,id,false);
            }
            var result=new LinkedHashMap<String,Object>(receipt(user,room,id));
            result.put("question",row.get("question"));result.put("answer",row.get("answer_text"));result.put("context",questions.json().object((String)row.get("context_json")));
            result.put("runId",row.get("run_id"));result.put("fragmentId",row.get("fragment_id"));result.put("contentRevision",row.get("content_revision"));
            result.put("actions",questions.json().value((String)row.get("actions_json")));
            if(row.get("failure_code")!=null)result.put("error",Map.of("code",row.get("failure_code"),"message",Objects.toString(row.get("failure_message"),"回答未完成")));
            return result;
        });
    }

    public Map<String,Object> cancel(long user,String room,String id) {
        return jobs.tx().execute(status -> {
            questions.store().lockUser(user);var row=questions.owned(user,room,id,true);
            if(Set.of("QUEUED","RUNNING").contains((String)row.get("status"))) {
                var job=questions.store().row("SELECT * FROM studyroom_generation_jobs WHERE id=? FOR UPDATE",row.get("job_id"));
                events.append(user,"question:"+id,(String)row.get("job_id"),((Number)job.get("attempt")).intValue(),"cancelled",Map.of("status","CANCELLED"),Map.of("status","CANCELLED","answer",row.get("answer_text")),true);
                questions.store().db().update("UPDATE studyroom_questions SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
                questions.store().db().update("UPDATE studyroom_generation_jobs SET status='CANCELLED',lease_token=NULL,lease_until=NULL WHERE id=? AND status IN ('QUEUED','RUNNING')",row.get("job_id"));
            }
            return get(user,room,id);
        });
    }
}
