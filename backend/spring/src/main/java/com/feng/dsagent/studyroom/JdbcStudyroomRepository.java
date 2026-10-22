package com.feng.dsagent.studyroom;
import com.feng.dsagent.common.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
class JdbcStudyroomRepository implements StudyroomRepository {
    private final JdbcTemplate jdbc;
    JdbcStudyroomRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public String currentRun(String room,long user){var rows=jdbc.queryForList("SELECT current_run_id FROM study_rooms WHERE id=? AND user_id=? AND deleted_at IS NULL",room,user);return rows.isEmpty()?null:(String)rows.getFirst().get("current_run_id");}
    @Override @Transactional
    public void appendExchange(String room,long user,List<StudyroomRecords.Message> messages,String expectedRun){
        var rows=jdbc.queryForList("SELECT current_run_id FROM study_rooms WHERE id=? AND user_id=? AND deleted_at IS NULL FOR UPDATE",room,user);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"STUDYROOM_NOT_FOUND","问答课堂不存在");
        if(expectedRun==null||!java.util.Objects.equals(rows.getFirst().get("current_run_id"),expectedRun))
            throw new ApiException(HttpStatus.CONFLICT,"RUN_SUPERSEDED","学习路线已更新，请在当前课堂重新提问");
        for(var message:messages)if(message.stepId()!=null&&!stepExists(room,message.stepId()))
            throw new ApiException(HttpStatus.CONFLICT,"STUDYROOM_CONTEXT_INVALID","追问上下文已改变");
        jdbc.update("UPDATE study_rooms SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",room);
        int next=jdbc.queryForObject("SELECT COALESCE(MAX(message_order),-1)+1 FROM study_room_messages WHERE study_room_id=?",Integer.class,room);
        insertMessages(room,messages,next);
    }
    @Override
    public Optional<StudyroomRecords.Aggregate> findOwned(String studyroomId, long userId) {
        Optional<StudyroomRecords.Room> room = jdbc.query(
            "SELECT id, user_id, chapter_id, title, initial_question, summary, current_step_id, version, created_at, updated_at "
                + "FROM study_rooms WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
            (row, index) -> new StudyroomRecords.Room(
                row.getString("id"), row.getLong("user_id"), row.getString("chapter_id"), row.getString("title"),
                row.getString("initial_question"), row.getString("summary"), row.getString("current_step_id"), row.getInt("version"),
                instant(row.getTimestamp("created_at")), instant(row.getTimestamp("updated_at"))
            ),
            studyroomId,
            userId
        ).stream().findFirst();
        if (room.isEmpty()) {
            return Optional.empty();
        }
        List<StudyroomRecords.Step> steps = jdbc.query(
            "SELECT id, step_key, step_type, title, explanation, evidence_json, resources_json, step_order, supplemental, parent_step_id, created_at, orchestration_json "
                + "FROM study_room_steps WHERE study_room_id = ? AND run_id=(SELECT current_run_id FROM study_rooms WHERE id=study_room_id) ORDER BY step_order, id",
            (row, index) -> new StudyroomRecords.Step(
                row.getString("id"), row.getString("step_key"), row.getString("step_type"), row.getString("title"),
                row.getString("explanation"), row.getString("evidence_json"), row.getString("resources_json"), row.getInt("step_order"),
                row.getBoolean("supplemental"), row.getString("parent_step_id"), instant(row.getTimestamp("created_at")), row.getString("orchestration_json")
            ),
            studyroomId
        );
        List<StudyroomRecords.Message> messages = jdbc.query(
            "SELECT id, role, content, sources_json, step_id, created_at FROM study_room_messages "
                + "WHERE study_room_id = ? AND run_id=(SELECT current_run_id FROM study_rooms WHERE id=study_room_id) ORDER BY message_order",
            (row, index) -> new StudyroomRecords.Message(
                row.getString("id"), row.getString("role"), row.getString("content"), row.getString("sources_json"),
                row.getString("step_id"), instant(row.getTimestamp("created_at"))
            ),
            studyroomId
        );
        return Optional.of(new StudyroomRecords.Aggregate(room.orElseThrow(), steps, messages));
    }

    @Override
    @Transactional
    public Optional<StudyroomRecords.Room> updateProgress(long userId, String studyroomId, String stepId) {
        int updated;
        if (stepId == null) {
            updated = jdbc.update(
                "UPDATE study_rooms SET current_step_id = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                studyroomId,
                userId
            );
        } else {
            updated = jdbc.update(
                "UPDATE study_rooms SET current_step_id = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP "
                    + "WHERE id = ? AND user_id = ? AND EXISTS (SELECT 1 FROM study_room_steps WHERE id = ? AND study_room_id = ? AND run_id=current_run_id)",
                stepId,
                studyroomId,
                userId,
                stepId,
                studyroomId
            );
        }
        if (updated == 0) {
            return Optional.empty();
        }
        return jdbc.query(
            "SELECT id, user_id, chapter_id, title, initial_question, summary, current_step_id, version, created_at, updated_at "
                + "FROM study_rooms WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
            (row, index) -> new StudyroomRecords.Room(
                row.getString("id"), row.getLong("user_id"), row.getString("chapter_id"), row.getString("title"),
                row.getString("initial_question"), row.getString("summary"), row.getString("current_step_id"), row.getInt("version"),
                instant(row.getTimestamp("created_at")), instant(row.getTimestamp("updated_at"))
            ),
            studyroomId,
            userId
        ).stream().findFirst();
    }

    private void insertMessages(String studyroomId, List<StudyroomRecords.Message> messages, int startingOrder) {
        int messageOrder = startingOrder;
        for (StudyroomRecords.Message message : messages) {
            jdbc.update(
                "INSERT INTO study_room_messages (id, study_room_id, message_order, role, content, sources_json, step_id, created_at, run_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, (SELECT current_run_id FROM study_rooms WHERE id=?))",
                message.id(), studyroomId, messageOrder++, message.role(), message.content(), message.sourcesJson(), message.stepId(),
                timestamp(message.createdAt()), studyroomId
            );
        }
    }

    private boolean stepExists(String studyroomId, String stepId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM study_room_steps WHERE study_room_id = ? AND id = ? AND run_id=(SELECT current_run_id FROM study_rooms WHERE id=study_room_id)",
            Integer.class,
            studyroomId,
            stepId
        );
        return count != null && count > 0;
    }

    private void requireOwnedRoom(int updated) {
        if (updated != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "STUDYROOM_NOT_FOUND", "问答课堂不存在");
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
