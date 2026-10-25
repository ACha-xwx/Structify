package com.feng.dsagent.studyroom.events.service;

import com.feng.dsagent.studyroom.events.ClassroomEvent;

import com.feng.dsagent.studyroom.draft.OutlineJson;
import com.feng.dsagent.studyroom.events.mapper.ClassroomEventMapper;
import com.feng.dsagent.studyroom.generation.GenerationLease;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClassroomEventService {
    private static final Set<String> KINDS=Set.of("phase","outline_item","outline_validated","content_published","answer_delta","answer_completed","failed","cancelled");
    private final ClassroomEventMapper events;
    private final OutlineJson json;
    private final TransactionTemplate tx;

    public ClassroomEventService(ClassroomEventMapper events, OutlineJson json, PlatformTransactionManager manager) {
        this.events=events; this.json=json; this.tx=new TransactionTemplate(manager);
    }

    private static long number(Map<String,Object> row,String key) {
        return ((Number)row.get(key)).longValue();
    }

    private Map<String,Object> owned(long user,String stream,boolean lock) {
        var row=events.selectOwnedStream(stream,user,lock);
        if(row==null) throw OutlineJson.error(HttpStatus.NOT_FOUND,"STREAM_NOT_FOUND","课堂进度不存在");
        String kind=(String)row.get("entity_kind");
        boolean accessible;
        if("OUTLINE".equals(kind)) accessible=events.selectOutlineAccessible(row.get("entity_id"),user)!=null;
        else accessible=events.selectRunAccessible(row.get("run_id"),user)!=null;
        if(!accessible) throw OutlineJson.error(HttpStatus.NOT_FOUND,"STREAM_NOT_FOUND","课堂进度不存在");
        return row;
    }

    private ClassroomEvent.Snapshot snapshot(Map<String,Object> row) {
        return new ClassroomEvent.Snapshot((String)row.get("stream_id"),(String)row.get("operation_id"),
            (int)number(row,"attempt"),(String)row.get("run_id"),row.get("revision")==null?null:(int)number(row,"revision"),
            number(row,"last_sequence"),number(row,"floor_sequence"),Boolean.TRUE.equals(row.get("terminal")),
            number(row,"state_version"),json.object((String)row.get("snapshot_json")));
    }

    public void requireRoom(long user,String stream,String room) {
        tx.executeWithoutResult(status -> {
            var row=owned(user,stream,false);
            if(!Objects.equals(room,row.get("room_id"))) throw OutlineJson.error(HttpStatus.NOT_FOUND,"STREAM_NOT_FOUND","课堂进度不存在");
        });
    }

    public void beginOutline(Map<String,Object> job) {
        long user=((Number)job.get("user_id")).longValue();
        var draft=events.selectOwnedDraft(user,(String)job.get("draft_id"));
        if(draft==null) throw OutlineJson.error(HttpStatus.NOT_FOUND,"OUTLINE_NOT_FOUND","学习大纲不存在");
        OutlineJson.require(Objects.equals(draft.get("pending_job_id"),job.get("id")),"OUTLINE_JOB_STALE","大纲生成任务已更新");
        open(user,"outline:"+draft.get("id"),"OUTLINE",(String)draft.get("id"),null,(String)job.get("id"),((Number)job.get("attempt")).intValue(),null,((Number)draft.get("latest_revision")).intValue()+1,Map.of("phase","QUEUED","units",List.of()));
    }

    public void outlineUpdate(Map<String,Object> job,String kind,Map<String,Object> payload,boolean terminal) {
        long user=((Number)job.get("user_id")).longValue();
        String stream="outline:"+job.get("draft_id");
        tx.executeWithoutResult(status -> {
            events.lockUser(user);
            var row=owned(user,stream,true);
            var state=new LinkedHashMap<>(json.object((String)row.get("snapshot_json")));
            if("outline_item".equals(kind)) {
                var units=new ArrayList<Object>((List<?>)state.getOrDefault("units",List.of()));
                units.add(payload);state.put("units",units);
            } else state.putAll(payload);
            append(user,stream,(String)job.get("id"),((Number)job.get("attempt")).intValue(),kind,payload,state,terminal);
        });
    }

    private void ensureRun(long user,String runId) {
        var run=events.selectCurrentRun(runId,user);
        if(run==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"STREAM_NOT_FOUND","课堂进度不存在");
        open(user,"run:"+runId,"RUN",runId,(String)run.get("room_id"),runId,1,runId,((Number)run.get("outline_revision")).intValue(),Map.of("phase","QUEUED","status",run.get("status")));
    }

    public void runUpdate(Map<String,Object> job,String kind,Map<String,Object> payload) {
        long user=((Number)job.get("user_id")).longValue();String run=(String)job.get("run_id");
        tx.executeWithoutResult(status -> {
            events.lockUser(user);ensureRun(user,run);
            var row=owned(user,"run:"+run,true);
            var state=new LinkedHashMap<>(json.object((String)row.get("snapshot_json")));
            state.putAll(payload);
            var ready=events.selectFirstReadableFragment(run);
            if(ready!=null)state.put("firstReadableFragmentId",ready.get("id"));
            append(user,"run:"+run,run,1,kind,payload,state,false);
        });
    }

    public void open(long user,String stream,String kind,String entity,String room,String operation,int attempt,String run,Integer revision,Map<String,Object> state) {
        tx.executeWithoutResult(status -> {
            events.lockUser(user);
            var prior=events.selectStream(stream,true);
            if(prior!=null) {
                owned(user,stream,false);
                if(operation.equals(prior.get("operation_id")) && attempt==number(prior,"attempt")) return;
                OutlineJson.require(attempt>number(prior,"attempt") || !operation.equals(prior.get("operation_id")),"STREAM_STALE_ATTEMPT","生成尝试已更新");
                events.updateStreamAttempt(operation,attempt,run,revision,json.write(state),stream);
            } else {
                events.insertStream(stream,user,room,kind,entity,operation,attempt,run,revision,json.write(state));
                owned(user,stream,false);
            }
        });
    }

    public ClassroomEvent append(long user,String stream,String operation,int attempt,String kind,Map<String,Object> payload,Map<String,Object> state,boolean terminal) {
        String encoded=json.write(payload);
        if(!KINDS.contains(kind) || encoded.getBytes(StandardCharsets.UTF_8).length>16384)
            throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_CLASSROOM_EVENT","课堂事件无效或过大");
        return tx.execute(status -> {
            events.lockUser(user);
            var lease=GenerationLease.current();
            if(lease!=null) {
                var active=events.selectActiveLease(lease.get("id"),user,lease.get("attempt"),lease.get("lease_token"));
                OutlineJson.require(active!=null,"JOB_LEASE_LOST","生成任务租约已失效");
            }
            var row=owned(user,stream,true);
            OutlineJson.require(operation.equals(row.get("operation_id")) && attempt==number(row,"attempt"),"STREAM_STALE_ATTEMPT","生成尝试已更新");
            OutlineJson.require(!Boolean.TRUE.equals(row.get("terminal")),"STREAM_TERMINAL","课堂任务已结束");
            long sequence=number(row,"last_sequence")+1;
            long version=number(row,"state_version")+1;
            events.insertEvent(stream,sequence,kind,operation,attempt,version,row.get("run_id"),row.get("revision"),encoded);
            events.updateStreamAfterAppend(sequence,version,json.write(state),terminal,terminal?Timestamp.from(Instant.now()):null,stream);
            return new ClassroomEvent(stream,operation,attempt,(String)row.get("run_id"),row.get("revision")==null?null:(int)number(row,"revision"),sequence,version,kind,payload);
        });
    }

    public ClassroomEvent.Batch read(long user,String stream,long since) {
        if(since<0) throw OutlineJson.error(HttpStatus.BAD_REQUEST,"INVALID_EVENT_CURSOR","进度游标无效");
        return tx.execute(status -> {
            if(stream.startsWith("run:") && events.selectStream(stream,false)==null) {
                events.lockUser(user);ensureRun(user,stream.substring(4));
            }
            if(stream.startsWith("outline:") && events.selectStream(stream,false)==null) {
                events.lockUser(user);
                var draft=events.selectOwnedDraft(user,stream.substring(8));
                if(draft==null) throw OutlineJson.error(HttpStatus.NOT_FOUND,"OUTLINE_NOT_FOUND","学习大纲不存在");
                var job=draft.get("pending_job_id")==null?null:events.selectJob(draft.get("pending_job_id"),user);
                if(job!=null)beginOutline(job);
                else {
                    open(user,stream,"OUTLINE",(String)draft.get("id"),null,(String)draft.get("id"),1,null,((Number)draft.get("latest_revision")).intValue(),Map.of("status",draft.get("status"),"units",List.of()));
                    events.markStreamTerminal(stream);
                }
            }
            var row=owned(user,stream,true);
            var snap=snapshot(row);
            boolean reset=since<snap.floor() || since>snap.cursor();
            if(reset) return new ClassroomEvent.Batch(snap,List.of(),true);
            var batch=events.selectEvents(stream,since,snap.cursor()).stream().map(event -> new ClassroomEvent(stream,(String)event.get("operation_id"),(int)number(event,"attempt"),(String)event.get("run_id"),event.get("revision")==null?null:(int)number(event,"revision"),number(event,"sequence"),number(event,"state_version"), (String)event.get("kind"),json.object((String)event.get("payload_json")))).toList();
            return new ClassroomEvent.Batch(snap,batch,false);
        });
    }

    public void cleanup() {
        Timestamp before=Timestamp.from(Instant.now().minusSeconds(86400));
        for(var candidate:events.selectExpiredTerminalStreams(before)) {
            tx.executeWithoutResult(status -> {
                events.lockUser(((Number)candidate.get("user_id")).longValue());
                var row=events.selectTerminalStreamForCleanup(candidate.get("stream_id"),before);
                if(row==null)return;
                events.deleteEvents(candidate.get("stream_id"));
                events.raiseFloor(candidate.get("stream_id"));
            });
        }
    }
}
