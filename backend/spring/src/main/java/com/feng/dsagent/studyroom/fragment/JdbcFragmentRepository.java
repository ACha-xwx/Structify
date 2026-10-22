package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.draft.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFragmentRepository {
    private final JdbcOutlineRepository store;
    private final OutlineJson json;
    public JdbcFragmentRepository(JdbcOutlineRepository store, OutlineJson json) {
        this.store = store;
        this.json = json;
    }
    public JdbcOutlineRepository store() { return store; }

    public Map<String,Object> ownedRun(long user, String run) {
        var row = store.row("SELECT r.* FROM studyroom_runs r JOIN study_rooms s ON s.id=r.room_id WHERE r.id=? AND r.user_id=? AND s.deleted_at IS NULL",run,user);
        if (row == null) throw OutlineJson.error(HttpStatus.NOT_FOUND,"RUN_NOT_FOUND","学习路线不存在");
        return row;
    }
    public Map<String,Object> currentRun(long user,String run) {
        var row = ownedRun(user,run);
        OutlineJson.require(store.row("SELECT id FROM study_rooms WHERE id=? AND current_run_id=?",
            row.get("room_id"),run) != null,"RUN_SUPERSEDED","学习路线已更新");
        return row;
    }
    public Map<String,Object> ownedFragment(long user,String run,String id,boolean lock) {
        ownedRun(user,run);
        var row=store.row("SELECT * FROM studyroom_fragments WHERE id=? AND run_id=?"+(lock?" FOR UPDATE":""),id,run);
        if(row==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"FRAGMENT_NOT_FOUND","学习片段不存在");
        return row;
    }
    public void touch(String run) {
        store.db().update("UPDATE studyroom_runs SET state_version=state_version+1 WHERE id=?",run);
    }
    public void insert(String run,String unit,FragmentPlan plan) {
        store.db().update("INSERT INTO studyroom_fragments(id,run_id,unit_id,fragment_key,fragment_order,plan_json,plan_hash,status) VALUES(?,?,?,?,?,?,?,'PENDING')",
            OutlineJson.id(),run,unit,plan.key(),plan.order(),json.write(plan),OutlineJson.hash(json.write(plan)));
    }
}
