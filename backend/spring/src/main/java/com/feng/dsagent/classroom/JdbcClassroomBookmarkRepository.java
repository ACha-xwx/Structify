package com.feng.dsagent.classroom;

import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcClassroomBookmarkRepository implements ClassroomBookmarkRepository {

    private static final String COLUMNS =
        "id, session_id, lesson_id, lesson_title, chapter_id, step_index, slide_id, title, created_at";

    private static final RowMapper<ClassroomBookmark> ROW = (row, index) -> new ClassroomBookmark(
        row.getLong("id"),
        row.getString("session_id"),
        row.getString("lesson_id"),
        row.getString("lesson_title"),
        row.getString("chapter_id"),
        row.getInt("step_index"),
        row.getString("slide_id"),
        row.getString("title"),
        row.getTimestamp("created_at").toInstant().toString()
    );

    private final JdbcTemplate jdbc;

    public JdbcClassroomBookmarkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Saving the same step twice keeps one row: the unique key says which step, and a second save returns
     * the bookmark that is already there instead of failing. The save is what the learner asked for either
     * way, so the answer is the same row.
     */
    @Override
    public ClassroomBookmark save(long userId, ClassroomBookmarkDraft draft) {
        try {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO classroom_bookmarks "
                        + "(user_id, session_id, lesson_id, lesson_title, chapter_id, step_index, slide_id, title) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
                statement.setLong(1, userId);
                statement.setString(2, draft.sessionId());
                statement.setString(3, draft.lessonId());
                statement.setString(4, draft.lessonTitle());
                statement.setString(5, draft.chapterId());
                statement.setInt(6, draft.stepIndex());
                statement.setString(7, draft.slideId());
                statement.setString(8, draft.title());
                return statement;
            }, keys);
            Number key = keys.getKey();
            if (key != null) {
                return byId(key.longValue());
            }
        } catch (DuplicateKeyException alreadySaved) {
            return byStep(userId, draft.sessionId(), draft.stepIndex());
        }
        return byStep(userId, draft.sessionId(), draft.stepIndex());
    }

    @Override
    public List<ClassroomBookmark> listByUser(long userId) {
        return jdbc.query(
            "SELECT " + COLUMNS + " FROM classroom_bookmarks WHERE user_id = ? AND NOT EXISTS (SELECT 1 FROM study_rooms r WHERE r.id=classroom_bookmarks.session_id AND r.user_id=classroom_bookmarks.user_id AND r.deleted_at IS NOT NULL) ORDER BY created_at DESC, id DESC",
            ROW, userId);
    }

    @Override
    public boolean delete(long userId, long id) {
        return jdbc.update("DELETE FROM classroom_bookmarks WHERE id = ? AND user_id = ?", id, userId) == 1;
    }

    private ClassroomBookmark byId(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM classroom_bookmarks WHERE id = ?", ROW, id)
            .stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("bookmark " + id + " vanished after insert"));
    }

    private ClassroomBookmark byStep(long userId, String sessionId, int stepIndex) {
        return jdbc.query(
            "SELECT " + COLUMNS + " FROM classroom_bookmarks WHERE user_id = ? AND session_id = ? AND step_index = ?",
            ROW, userId, sessionId, stepIndex)
            .stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("bookmark row missing after a duplicate save"));
    }
}
