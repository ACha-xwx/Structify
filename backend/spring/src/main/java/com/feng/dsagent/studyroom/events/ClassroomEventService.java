package com.feng.dsagent.studyroom.events;

import com.feng.dsagent.studyroom.draft.OutlineJson;
import com.feng.dsagent.studyroom.generation.GenerationLease;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClassroomEventService {
    private static final Set<String> KINDS=Set.of("phase","outline_item","outline_validated","content_published","answer_delta","answer_completed","failed","cancelled");
    private final JdbcClassroomEventRepository repository;
    private final TransactionTemplate tx;
    public ClassroomEventService(JdbcClassroomEventRepository repository, PlatformTransactionManager manager) {
        this.repository=repository; this.tx=new TransactionTemplate(manager);
    }

    public void requireRoom(long user,String stream,String room) {
        tx.executeWithoutResult(status -> {
            var row=repository.owned(user,stream,false);
            if(!Objects.equals(room,row.get("room_id"))) throw OutlineJson.error(HttpStatus.NOT_FOUND,"STREAM_NOT_FOUND","课堂进度不存在");
        });
    }

    public void beginOutline(Map<String,Object> job) {
        long user=((Number)job.get("user_id")).longValue();
        var draft=repository.store().owned(user,(String)job.get("draft_id"),false);
        OutlineJson.require(Objects.equals(draft.get("pending_job_id"),job.get("id")),"OUTLINE_JOB_STALE","大纲生成任务已更新");
        open(user,"outline:"+draft.get("id"),"OUTLINE",(String)draft.get("id"),null,(String)job.get("id"),((Number)job.get("attempt")).intValue(),null,((Number)draft.get("latest_revision")).intValue()+1,Map.of("phase","QUEUED","units",List.of()));
    }

    public void outlineUpdate(Map<String,Object> job,String kind,Map<String,Object> payload,boolean terminal) {
        long user=((Number)job.get("user_id")).longValue();
        String stream="outline:"+job.get("draft_id");
        tx.executeWithoutResult(status -> {
            repository.store().lockUser(user);
            var row=repository.owned(user,stream,true);
            var state=new LinkedHashMap<>(repository.json().object((String)row.get("snapshot_json")));
            if("outline_item".equals(kind)) {
                var units=new ArrayList<Object>((List<?>)state.getOrDefault("units",List.of()));
                units.add(payload);state.put("units",units);
            } else state.putAll(payload);
            append(user,stream,(String)job.get("id"),((Number)job.get("attempt")).intValue(),kind,payload,state,terminal);
        });
    }

    private void ensureRun(long user,String runId) {
        var run=repository.store().row("SELECT r.* FROM studyroom_runs r JOIN study_rooms s ON s.current_run_id=r.id JOIN study_outline_drafts d ON d.id=r.draft_id WHERE r.id=? AND r.user_id=? AND s.deleted_at IS NULL AND d.deleted_at IS NULL",runId,user);
        if(run==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"STREAM_NOT_FOUND","课堂进度不存在");
        open(user,"run:"+runId,"RUN",runId,(String)run.get("room_id"),runId,1,runId,((Number)run.get("outline_revision")).intValue(),Map.of("phase","QUEUED","status",run.get("status")));
    }

    public void runUpdate(Map<String,Object> job,String kind,Map<String,Object> payload) {
        long user=((Number)job.get("user_id")).longValue();String run=(String)job.get("run_id");
        tx.executeWithoutResult(status -> {
            repository.store().lockUser(user);ensureRun(user,run);
            var row=repository.owned(user,"run:"+run,true);
            var state=new LinkedHashMap<>(repository.json().object((String)row.get("snapshot_json")));
            state.putAll(payload);
            var ready=repository.store().row("SELECT f.id FROM studyroom_fragments f JOIN studyroom_units u ON u.id=f.unit_id WHERE f.run_id=? AND f.step_id IS NOT NULL ORDER BY u.unit_order,f.fragment_order LIMIT 1",run);
            if(ready!=null)state.put("firstReadableFragmentId",ready.get("id"));
            append(user,"run:"+run,run,1,kind,payload,state,false);
        });
    }

    public void open(long user,String stream,String kind,String entity,String room,String operation,int attempt,String run,Integer revision,Map<String,Object> state) {
        tx.executeWithoutResult(status -> {
            var store=repository.store(); store.lockUser(user);
            var prior=store.row("SELECT * FROM studyroom_event_streams WHERE stream_id=? FOR UPDATE",stream);
            if(prior!=null) {
                repository.owned(user,stream,false);
                if(operation.equals(prior.get("operation_id")) && attempt==JdbcClassroomEventRepository.number(prior,"attempt")) return;
                OutlineJson.require(attempt>JdbcClassroomEventRepository.number(prior,"attempt") || !operation.equals(prior.get("operation_id")),"STREAM_STALE_ATTEMPT","生成尝试已更新");
                store.db().update("UPDATE studyroom_event_streams SET operation_id=?,attempt=?,run_id=?,revision=?,snapshot_json=?,state_version=state_version+1,terminal=FALSE,terminal_at=NULL,floor_sequence=last_sequence WHERE stream_id=?",operation,attempt,run,revision,repository.json().write(state),stream);
            } else {
                store.db().update("INSERT INTO studyroom_event_streams(stream_id,user_id,room_id,entity_kind,entity_id,operation_id,attempt,run_id,revision,snapshot_json) VALUES(?,?,?,?,?,?,?,?,?,?)",stream,user,room,kind,entity,operation,attempt,run,revision,repository.json().write(state));
                repository.owned(user,stream,false);
            }
        });
    }

    public ClassroomEvent append(long user,String stream,String operation,int attempt,String kind,Map<String,Object> payload,Map<String,Object> state,boolean terminal) {
        String encoded=repository.json().write(payload);
        if(!KINDS.contains(kind) || encoded.getBytes(StandardCharsets.UTF_8).length>16384)
            throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_CLASSROOM_EVENT","课堂事件无效或过大");
        return tx.execute(status -> {
            var store=repository.store(); store.lockUser(user);
            var lease=GenerationLease.current();
            if(lease!=null) {
                var active=store.row("SELECT id FROM studyroom_generation_jobs WHERE id=? AND user_id=? AND status='RUNNING' AND attempt=? AND lease_token=? AND lease_until>CURRENT_TIMESTAMP FOR UPDATE",lease.get("id"),user,lease.get("attempt"),lease.get("lease_token"));
                OutlineJson.require(active!=null,"JOB_LEASE_LOST","生成任务租约已失效");
            }
            var row=repository.owned(user,stream,true);
            OutlineJson.require(operation.equals(row.get("operation_id")) && attempt==JdbcClassroomEventRepository.number(row,"attempt"),"STREAM_STALE_ATTEMPT","生成尝试已更新");
            OutlineJson.require(!Boolean.TRUE.equals(row.get("terminal")),"STREAM_TERMINAL","课堂任务已结束");
            long sequence=JdbcClassroomEventRepository.number(row,"last_sequence")+1;
            long version=JdbcClassroomEventRepository.number(row,"state_version")+1;
            store.db().update("INSERT INTO studyroom_events(stream_id,sequence,kind,operation_id,attempt,state_version,run_id,revision,payload_json) VALUES(?,?,?,?,?,?,?,?,?)",stream,sequence,kind,operation,attempt,version,row.get("run_id"),row.get("revision"),encoded);
            store.db().update("UPDATE studyroom_event_streams SET last_sequence=?,state_version=?,snapshot_json=?,terminal=?,terminal_at=? WHERE stream_id=?",sequence,version,repository.json().write(state),terminal,terminal?Timestamp.from(Instant.now()):null,stream);
            return new ClassroomEvent(stream,operation,attempt,(String)row.get("run_id"),row.get("revision")==null?null:(int)JdbcClassroomEventRepository.number(row,"revision"),sequence,version,kind,payload);
        });
    }

    public ClassroomEvent.Batch read(long user,String stream,long since) {
        if(since<0) throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_EVENT_CURSOR","进度游标无效");
        return tx.execute(status -> {
            if(stream.startsWith("run:") && repository.store().row("SELECT stream_id FROM studyroom_event_streams WHERE stream_id=?",stream)==null) {
                repository.store().lockUser(user);ensureRun(user,stream.substring(4));
            }
            if(stream.startsWith("outline:") && repository.store().row("SELECT stream_id FROM studyroom_event_streams WHERE stream_id=?",stream)==null) {
                repository.store().lockUser(user);
                var draft=repository.store().owned(user,stream.substring(8),false);
                var job=draft.get("pending_job_id")==null?null:repository.store().row("SELECT * FROM studyroom_generation_jobs WHERE id=? AND user_id=?",draft.get("pending_job_id"),user);
                if(job!=null)beginOutline(job);
                else {
                    open(user,stream,"OUTLINE",(String)draft.get("id"),null,(String)draft.get("id"),1,null,((Number)draft.get("latest_revision")).intValue(),Map.of("status",draft.get("status"),"units",List.of()));
                    repository.store().db().update("UPDATE studyroom_event_streams SET terminal=TRUE,terminal_at=CURRENT_TIMESTAMP WHERE stream_id=?",stream);
                }
            }
            var row=repository.owned(user,stream,true);
            var snapshot=repository.snapshot(row);
            boolean reset=since<snapshot.floor() || since>snapshot.cursor();
            if(reset) return new ClassroomEvent.Batch(snapshot,List.of(),true);
            var events=repository.store().db().queryForList("SELECT * FROM studyroom_events WHERE stream_id=? AND sequence>? AND sequence<=? ORDER BY sequence LIMIT 100",stream,since,snapshot.cursor()).stream().map(event -> new ClassroomEvent(stream,(String)event.get("operation_id"),(int)JdbcClassroomEventRepository.number(event,"attempt"),(String)event.get("run_id"),event.get("revision")==null?null:(int)JdbcClassroomEventRepository.number(event,"revision"),JdbcClassroomEventRepository.number(event,"sequence"),JdbcClassroomEventRepository.number(event,"state_version"), (String)event.get("kind"),repository.json().object((String)event.get("payload_json")))).toList();
            return new ClassroomEvent.Batch(snapshot,events,false);
        });
    }

    public void cleanup() {
        for(var candidate:repository.store().db().queryForList("SELECT stream_id,user_id FROM studyroom_event_streams WHERE terminal=TRUE AND terminal_at<?",Timestamp.from(Instant.now().minusSeconds(86400)))) {
            tx.executeWithoutResult(status -> {
                var store=repository.store(); store.lockUser(((Number)candidate.get("user_id")).longValue());
                var row=store.row("SELECT * FROM studyroom_event_streams WHERE stream_id=? AND terminal=TRUE AND terminal_at<? FOR UPDATE",candidate.get("stream_id"),Timestamp.from(Instant.now().minusSeconds(86400)));
                if(row==null)return;
                store.db().update("DELETE FROM studyroom_events WHERE stream_id=?",candidate.get("stream_id"));
                store.db().update("UPDATE studyroom_event_streams SET floor_sequence=last_sequence WHERE stream_id=?",candidate.get("stream_id"));
            });
        }
    }
}
