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

    JdbcStudyroomRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String currentRun(String roomId,long userId) { var rows=jdbc.queryForList("SELECT current_run_id FROM study_rooms WHERE id=? AND user_id=?",roomId,userId); return rows.isEmpty()?null:(String)rows.getFirst().get("current_run_id"); }

    @Override
    @Transactional
    public void appendExchange(String roomId,long userId,List<StudyroomRecords.Message> messages,List<StudyroomRecords.Step> steps,String expectedRun) {
        var rows=jdbc.queryForList("SELECT current_run_id FROM study_rooms WHERE id=? AND user_id=? FOR UPDATE",roomId,userId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"STUDYROOM_NOT_FOUND","问答课堂不存在");
        if(!java.util.Objects.equals(rows.getFirst().get("current_run_id"),expectedRun))throw new ApiException(HttpStatus.CONFLICT,"RUN_SUPERSEDED","学习路线已更新，请在当前课堂重新提问");
        appendExchange(roomId,userId,messages,steps);
    }

    @Override
    public boolean hasReviewedRun(String roomId,long userId) { return jdbc.queryForObject("SELECT COUNT(*) FROM study_rooms WHERE id=? AND user_id=? AND current_run_id IS NOT NULL",Integer.class,roomId,userId)>0; }

    @Override
    public boolean isPublishedChapter(String chapterId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chapters WHERE id = ? AND status = 'PUBLISHED'",
            Integer.class,
            chapterId
        );
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void create(StudyroomRecords.Room room, List<StudyroomRecords.Step> steps, List<StudyroomRecords.Message> messages) {
        jdbc.update(
            "INSERT INTO study_rooms (id, user_id, chapter_id, title, initial_question, summary, current_step_id, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            room.id(), room.userId(), room.chapterId(), room.title(), room.initialQuestion(), room.summary(), null,
            room.version(), timestamp(room.createdAt()), timestamp(room.updatedAt())
        );
        int stepOrder = 0;
        for (StudyroomRecords.Step step : steps) {
            insertStep(room.id(), step, stepOrder++);
        }
        if (room.currentStepId() != null) {
            if (!stepExists(room.id(), room.currentStepId())) {
                throw new IllegalArgumentException("Initial step does not belong to the studyroom");
            }
            jdbc.update("UPDATE study_rooms SET current_step_id = ? WHERE id = ? AND user_id = ?", room.currentStepId(), room.id(), room.userId());
        }
        insertMessages(room.id(), messages, 0);
    }

    @Override
    public Optional<StudyroomRecords.Aggregate> findOwned(String studyroomId, long userId) {
        Optional<StudyroomRecords.Room> room = jdbc.query(
            "SELECT id, user_id, chapter_id, title, initial_question, summary, current_step_id, version, created_at, updated_at "
                + "FROM study_rooms WHERE id = ? AND user_id = ?",
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
                + "FROM study_room_steps WHERE study_room_id = ? AND ((run_id IS NULL AND (SELECT current_run_id FROM study_rooms WHERE id=study_room_id) IS NULL) OR run_id=(SELECT current_run_id FROM study_rooms WHERE id=study_room_id)) ORDER BY step_order, id",
            (row, index) -> new StudyroomRecords.Step(
                row.getString("id"), row.getString("step_key"), row.getString("step_type"), row.getString("title"),
                row.getString("explanation"), row.getString("evidence_json"), row.getString("resources_json"), row.getInt("step_order"),
                row.getBoolean("supplemental"), row.getString("parent_step_id"), instant(row.getTimestamp("created_at")), row.getString("orchestration_json")
            ),
            studyroomId
        );
        List<StudyroomRecords.Message> messages = jdbc.query(
            "SELECT id, role, content, sources_json, step_id, created_at FROM study_room_messages "
                + "WHERE study_room_id = ? AND ((run_id IS NULL AND (SELECT current_run_id FROM study_rooms WHERE id=study_room_id) IS NULL) OR run_id=(SELECT current_run_id FROM study_rooms WHERE id=study_room_id)) ORDER BY message_order",
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
    public void appendExchange(
        String studyroomId,
        long userId,
        List<StudyroomRecords.Message> messages,
        List<StudyroomRecords.Step> supplementalSteps
    ) {
        requireOwnedRoom(jdbc.update(
            "UPDATE study_rooms SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
            studyroomId,
            userId
        ));
        if (!supplementalSteps.isEmpty() && jdbc.queryForObject("SELECT COUNT(*) FROM study_rooms WHERE id=? AND current_run_id IS NOT NULL",Integer.class,studyroomId)>0)
            throw new ApiException(HttpStatus.CONFLICT,"STUDYROOM_OUTLINE_REVISION_REQUIRED","请先修改学习大纲，确认后再生成新的学习目标。");
        int nextStepOrder = jdbc.queryForObject(
            "SELECT COALESCE(MAX(step_order), -1) + 1 FROM study_room_steps WHERE study_room_id = ?",
            Integer.class,
            studyroomId
        );
        for (StudyroomRecords.Step step : supplementalSteps) {
            if (step.parentStepId() != null && !stepExists(studyroomId, step.parentStepId())) {
                throw new IllegalArgumentException("Supplement parent step does not belong to the studyroom");
            }
            insertStep(studyroomId, step, nextStepOrder++);
        }
        int nextMessageOrder = jdbc.queryForObject(
            "SELECT COALESCE(MAX(message_order), -1) + 1 FROM study_room_messages WHERE study_room_id = ?",
            Integer.class,
            studyroomId
        );
        insertMessages(studyroomId, messages, nextMessageOrder);
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
                    + "WHERE id = ? AND user_id = ? AND EXISTS (SELECT 1 FROM study_room_steps WHERE id = ? AND study_room_id = ? AND ((run_id IS NULL AND current_run_id IS NULL) OR run_id=current_run_id))",
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
                + "FROM study_rooms WHERE id = ? AND user_id = ?",
            (row, index) -> new StudyroomRecords.Room(
                row.getString("id"), row.getLong("user_id"), row.getString("chapter_id"), row.getString("title"),
                row.getString("initial_question"), row.getString("summary"), row.getString("current_step_id"), row.getInt("version"),
                instant(row.getTimestamp("created_at")), instant(row.getTimestamp("updated_at"))
            ),
            studyroomId,
            userId
        ).stream().findFirst();
    }

    private void insertStep(String studyroomId, StudyroomRecords.Step step, int stepOrder) {
        jdbc.update(
            "INSERT INTO study_room_steps (id, study_room_id, step_key, step_type, title, explanation, evidence_json, resources_json, "
                + "step_order, supplemental, parent_step_id, created_at, orchestration_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            step.id(), studyroomId, step.stepKey(), step.type(), step.title(), step.explanation(), step.evidenceJson(), step.resourcesJson(),
            stepOrder, step.supplemental(), step.parentStepId(), timestamp(step.createdAt()), step.orchestrationJson()
        );
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
            "SELECT COUNT(*) FROM study_room_steps WHERE study_room_id = ? AND id = ?",
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
