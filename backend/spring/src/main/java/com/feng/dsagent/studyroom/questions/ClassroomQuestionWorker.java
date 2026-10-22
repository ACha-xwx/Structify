package com.feng.dsagent.studyroom.questions;

import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.events.*;
import com.feng.dsagent.studyroom.generation.*;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ClassroomQuestionWorker {
    private final JdbcClassroomQuestionRepository questions;
    private final JdbcGenerationRepository jobs;
    private final ClassroomEventService events;
    private final GenerationCallService calls;
    private final StudyroomFollowUpService followups;
    private final StudyroomRepository rooms;
    private com.feng.dsagent.studyroom.teaching.TeachingAttachmentService attachments;
    @org.springframework.beans.factory.annotation.Autowired
    void attachments(com.feng.dsagent.studyroom.teaching.TeachingAttachmentService attachments){this.attachments=attachments;}
    public ClassroomQuestionWorker(JdbcClassroomQuestionRepository questions,JdbcGenerationRepository jobs,ClassroomEventService events,GenerationCallService calls,StudyroomFollowUpService followups,StudyroomRepository rooms) {
        this.questions=questions;this.jobs=jobs;this.events=events;this.calls=calls;this.followups=followups;this.rooms=rooms;
    }
    public void run(Map<String,Object> job) {
        long user=((Number)job.get("user_id")).longValue();
        String id=(String)questions.json().object((String)job.get("payload_json")).get("questionId");
        var question=questions.store().row("SELECT * FROM studyroom_questions WHERE id=? AND user_id=?",id,user);
        OutlineJson.require(question!=null,"QUESTION_NOT_FOUND","课堂问题不存在");
        String room=(String)question.get("room_id");
        jobs.tx().executeWithoutResult(status->{
            questions.store().lockUser(user);requireActive(job,room,id);
            questions.store().db().update("UPDATE studyroom_questions SET status='RUNNING' WHERE id=?",id);
        });
        var prepared=followups.prepare(user,room,(String)question.get("question"),KnowledgeAudience.valueOf((String)question.get("audience")),(String)question.get("context_json"));
        var text=new StringBuilder();var pending=new StringBuilder();long[] last={System.nanoTime()};
        java.util.function.Consumer<String> flush=delta -> jobs.tx().executeWithoutResult(status->{
            questions.store().lockUser(user);requireActive(job,room,id);
            questions.store().db().update("UPDATE studyroom_questions SET answer_text=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",text.toString(),id);
            events.append(user,"question:"+id,(String)job.get("id"),((Number)job.get("attempt")).intValue(),"answer_delta",Map.of("delta",delta),Map.of("status","RUNNING","answer",text.toString()),false);
        });
        String answer=calls.streamText(user,(String)job.get("id"),"question-answer",StudyroomFollowUpService.SYSTEM,prepared.prompt(),1800,delta->{
            OutlineJson.require(text.length()+delta.length()<=12000,"STUDYROOM_REPLY_INVALID","回答超出长度限制");
            // Limit persisted delta payloads independently of provider chunk boundaries.
            for(int start=0;start<delta.length();start+=1000) {
                String part=delta.substring(start,Math.min(start+1000,delta.length()));text.append(part);pending.append(part);
                if(pending.length()>=1000||System.nanoTime()-last[0]>=100_000_000L) {flush.accept(pending.toString());pending.setLength(0);last[0]=System.nanoTime();}
            }
        });
        OutlineJson.require(!answer.isBlank()&&answer.length()<=12000,"STUDYROOM_REPLY_INVALID","追问回答暂未完成，请重试");
        // Cached successful calls have no live deltas and are published from their saved response.
        if(text.isEmpty())text.append(answer);
        if(!pending.isEmpty())flush.accept(pending.toString());
        var actions=attachments==null?List.<Map<String,Object>>of():attachments.generate(user,job,question,answer);
        jobs.tx().executeWithoutResult(status->{
            questions.store().lockUser(user);var current=requireActive(job,room,id);
            Instant now=Instant.now();
            rooms.appendExchange(room,user,List.of(new StudyroomRecords.Message((String)current.get("user_message_id"),"user",(String)current.get("question"),"[]",prepared.stepId(),now),
                new StudyroomRecords.Message((String)current.get("assistant_message_id"),"assistant",answer,prepared.sourcesJson(),prepared.stepId(),now)),prepared.runId());
            questions.store().db().update("UPDATE studyroom_questions SET status='COMPLETED',answer_text=?,sources_json=?,actions_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",answer,prepared.sourcesJson(),questions.json().write(actions),id);
            events.append(user,"question:"+id,(String)job.get("id"),((Number)job.get("attempt")).intValue(),"answer_completed",Map.of("questionId",id,"actions",actions),Map.of("status","COMPLETED","answer",answer,"actions",actions),true);
            jobs.finish(job);
        });
    }
    private Map<String,Object> requireActive(Map<String,Object> job,String room,String id) {
        long user=((Number)job.get("user_id")).longValue();
        OutlineJson.require(jobs.active(job),"JOB_LEASE_LOST","生成任务租约已失效");
        var row=questions.owned(user,room,id,true);
        OutlineJson.require(Set.of("QUEUED","RUNNING").contains((String)row.get("status")),"QUESTION_STOPPED","回答已停止");
        var fragment=questions.store().row("SELECT content_revision FROM studyroom_fragments WHERE id=? AND run_id=?",row.get("fragment_id"),row.get("run_id"));
        OutlineJson.require(fragment!=null&&Objects.equals(fragment.get("content_revision"),row.get("content_revision")),"FRAGMENT_REVISION_CHANGED","引用的讲解已更新");
        return row;
    }
}
