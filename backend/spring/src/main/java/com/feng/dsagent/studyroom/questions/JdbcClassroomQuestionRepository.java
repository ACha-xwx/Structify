package com.feng.dsagent.studyroom.questions;

import com.feng.dsagent.studyroom.draft.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcClassroomQuestionRepository {
    private final JdbcOutlineRepository store;
    private final OutlineJson json;
    public JdbcClassroomQuestionRepository(JdbcOutlineRepository store,OutlineJson json) {this.store=store;this.json=json;}
    public JdbcOutlineRepository store() {return store;}
    public OutlineJson json() {return json;}
    public Map<String,Object> owned(long user,String room,String id,boolean lock) {
        var question=store.row("SELECT q.* FROM studyroom_questions q JOIN studyroom_runs r ON r.id=q.run_id JOIN study_rooms s ON s.id=q.room_id AND s.current_run_id=q.run_id JOIN study_outline_drafts d ON d.id=r.draft_id WHERE q.id=? AND q.room_id=? AND q.user_id=? AND s.deleted_at IS NULL AND d.deleted_at IS NULL"+(lock?" FOR UPDATE":""),id,room,user);
        if(question==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"QUESTION_NOT_FOUND","课堂问题不存在");
        return question;
    }
}
