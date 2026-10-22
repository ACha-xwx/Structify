package com.feng.dsagent.studyroom.events;

import com.feng.dsagent.studyroom.draft.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcClassroomEventRepository {
    final JdbcOutlineRepository store;
    final OutlineJson json;
    public JdbcClassroomEventRepository(JdbcOutlineRepository store, OutlineJson json) {
        this.store=store; this.json=json;
    }
    public JdbcOutlineRepository store() { return store; }
    public OutlineJson json() { return json; }

    Map<String,Object> owned(long user, String stream, boolean lock) {
        var row=store.row("SELECT * FROM studyroom_event_streams WHERE stream_id=? AND user_id=?" + (lock?" FOR UPDATE":""),stream,user);
        if(row==null) throw OutlineJson.error(HttpStatus.NOT_FOUND,"STREAM_NOT_FOUND","课堂进度不存在");
        String kind=(String)row.get("entity_kind");
        boolean accessible;
        if("OUTLINE".equals(kind)) accessible=store.row("SELECT id FROM study_outline_drafts WHERE id=? AND user_id=? AND deleted_at IS NULL",row.get("entity_id"),user)!=null;
        else accessible=store.row("SELECT r.id FROM studyroom_runs r JOIN study_rooms s ON s.current_run_id=r.id JOIN study_outline_drafts d ON d.id=r.draft_id WHERE r.id=? AND r.user_id=? AND s.deleted_at IS NULL AND d.deleted_at IS NULL",row.get("run_id"),user)!=null;
        if(!accessible) throw OutlineJson.error(HttpStatus.NOT_FOUND,"STREAM_NOT_FOUND","课堂进度不存在");
        return row;
    }

    static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    ClassroomEvent.Snapshot snapshot(Map<String,Object> row) {
        return new ClassroomEvent.Snapshot((String)row.get("stream_id"),(String)row.get("operation_id"),
            (int)number(row,"attempt"),(String)row.get("run_id"),row.get("revision")==null?null:(int)number(row,"revision"),
            number(row,"last_sequence"),number(row,"floor_sequence"),Boolean.TRUE.equals(row.get("terminal")),
            number(row,"state_version"),json.object((String)row.get("snapshot_json")));
    }
}
