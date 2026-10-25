package com.feng.dsagent.studyroom.questions.service;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.studyroom.events.service.ClassroomEventService;
import com.feng.dsagent.studyroom.service.StudyroomService;

import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.events.*;
import com.feng.dsagent.studyroom.generation.*;
import com.feng.dsagent.studyroom.questions.mapper.ClassroomQuestionMapper;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ClassroomQuestionService {
    private final ClassroomQuestionMapper questions;
    private final JdbcOutlineRepository store;
    private final OutlineJson json;
    private final JdbcGenerationRepository jobs;
    private final ClassroomEventService events;
    private final StudyroomService rooms;
    public ClassroomQuestionService(ClassroomQuestionMapper questions,JdbcOutlineRepository store,OutlineJson json,JdbcGenerationRepository jobs,ClassroomEventService events,StudyroomService rooms) {
        this.questions=questions;this.store=store;this.json=json;this.jobs=jobs;this.events=events;this.rooms=rooms;
    }

    private Map<String,Object> owned(long user,String room,String id,boolean lock) {
        var question=questions.selectOwned(id,room,user,lock);
        if(question==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"QUESTION_NOT_FOUND","课堂问题不存在");
        return question;
    }

    public Map<String,Object> create(long user,String room,String run,String fragment,int revision,String question,StudyroomFollowUpContext context,String key,KnowledgeAudience audience) {
        if(question==null || question.isBlank() || question.length()>4000)throw OutlineJson.error(HttpStatus.BAD_REQUEST,"STUDYROOM_QUESTION_INVALID","追问不能为空且不能超过4000字");
        return jobs.tx().execute(status -> {
            store.lockUser(user);
            String payload=json.write(Arrays.asList(room,run,fragment,revision,question.strip(),context));
            String prior=store.key(user,"question_create",key,payload);
            if(prior!=null)return receipt(user,room,prior);
            var source=questions.selectFragmentSource(fragment,run,room,user);
            if(source==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"FRAGMENT_NOT_FOUND","课堂讲解不存在");
            OutlineJson.require(source.get("step_id")!=null && revision==((Number)source.get("content_revision")).intValue(),"FRAGMENT_REVISION_CHANGED","当前材料已更新，请重新选择引用");
            String frozenContext=rooms.validateQuestionContext(user,room,context);
            var parsed=json.object(frozenContext);
            OutlineJson.require(Objects.equals(parsed.get("stepId"),source.get("step_id")),"STUDYROOM_CONTEXT_INVALID","引用与当前讲解不一致");
            OutlineJson.require(questions.selectActiveQuestion(user,run)==null,"QUESTION_ALREADY_ACTIVE","请先等待或停止当前回答");
            String id=OutlineJson.id();
            questions.insertQuestion(id,user,room,run,fragment,revision,question.strip(),frozenContext,audience.name(),OutlineJson.id(),OutlineJson.id());
            String job=jobs.enqueue(user,(String)source.get("draft_id"),run,null,"FOLLOWUP","question:"+id,Map.of("questionId",id));
            questions.setJobPriority(job,400);
            questions.setQuestionJob(job,id);
            events.open(user,"question:"+id,"QUESTION",id,room,job,1,run,revision,Map.of("questionId",id,"status","QUEUED","answer",""));
            store.saveKey(user,"question_create",key,payload,id);
            return receipt(user,room,id);
        });
    }

    private Map<String,Object> receipt(long user,String room,String id) {
        var row=owned(user,room,id,false);
        return Map.of("questionId",id,"status",row.get("status"),"streamId","question:"+id,"cursor",events.read(user,"question:"+id,0).snapshot().cursor());
    }

    public String activeQuestion(long user,String room) {
        var row=questions.selectActiveQuestionInRoom(user,room);
        return row==null?null:(String)row.get("id");
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=2000L)
    public void reconcileStoppedJobs() {
        for(var row:questions.selectStoppedQuestions()) {
            try {get(((Number)row.get("user_id")).longValue(),(String)row.get("room_id"),(String)row.get("id"));}
            catch(com.feng.dsagent.common.ApiException inaccessible) { /* Deleted or superseded between reads. */ }
        }
    }

    public Map<String,Object> get(long user,String room,String id) {
        return jobs.tx().execute(status -> {
            store.lockUser(user);
            var row=owned(user,room,id,false);
            // A recovered uncertain invocation is a failed question, never an automatic re-run.
            var job=questions.selectJobStatus(row.get("job_id"));
            if(Set.of("QUEUED","RUNNING").contains((String)row.get("status")) && job!=null && Set.of("FAILED","CANCELLED").contains((String)job.get("status"))) {
                var stream=events.read(user,"question:"+id,0).snapshot();
                if(!stream.terminal())events.append(user,"question:"+id,stream.operationId(),stream.attempt(),"CANCELLED".equals(job.get("status"))?"cancelled":"failed",Map.of("status",job.get("status")),Map.of("status",job.get("status"),"answer",row.get("answer_text")),true);
                questions.updateQuestionFromJob(job.get("status"),job.get("failure_code"),job.get("failure_message"),id);
                row=owned(user,room,id,false);
            }
            var result=new LinkedHashMap<String,Object>(receipt(user,room,id));
            result.put("question",row.get("question"));result.put("answer",row.get("answer_text"));result.put("context",json.object((String)row.get("context_json")));
            result.put("runId",row.get("run_id"));result.put("fragmentId",row.get("fragment_id"));result.put("contentRevision",row.get("content_revision"));
            result.put("actions",json.value((String)row.get("actions_json")));
            if(row.get("failure_code")!=null)result.put("error",Map.of("code",row.get("failure_code"),"message",Objects.toString(row.get("failure_message"),"回答未完成")));
            return result;
        });
    }

    public Map<String,Object> cancel(long user,String room,String id) {
        return jobs.tx().execute(status -> {
            store.lockUser(user);var row=owned(user,room,id,true);
            if(Set.of("QUEUED","RUNNING").contains((String)row.get("status"))) {
                var job=questions.selectJobForUpdate(row.get("job_id"));
                events.append(user,"question:"+id,(String)row.get("job_id"),((Number)job.get("attempt")).intValue(),"cancelled",Map.of("status","CANCELLED"),Map.of("status","CANCELLED","answer",row.get("answer_text")),true);
                questions.cancelQuestion(id);
                questions.cancelJob(row.get("job_id"));
            }
            return get(user,room,id);
        });
    }
}
