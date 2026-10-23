package com.feng.dsagent.admin;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.learning.LearningMapService;
import com.feng.dsagent.studyroom.library.CourseLibraryService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Everything an administrator needs to understand one learner, in a single read.
 *
 * <p>The users page could always say who somebody is; it could not say what they have done. This view gathers the
 * learning side of one account - courses, bookmarks, classroom work, tokens spent, recent generation jobs and the
 * mastery map itself - so a question like "is this student actually learning" does not take five screens to answer.
 *
 * <p>The map is the same computation the student and their teacher see, and the read is audited under
 * {@code ADMIN_VIEW_LEARNING_MAP}: looking at somebody's learning profile is a privileged act whoever does it.
 */
@Service
public class AdminUserLearningService {

    /** One recent studyroom generation job of the user, with what it cost. */
    public record RecentJob(String id, String type, String status, String createdAt, long tokens) {}

    /** The learning footprint of one account. */
    public record AdminUserLearningView(
        long userId,
        String email,
        String username,
        String status,
        List<CourseLibraryService.Course> courses,
        long bookmarks,
        long classroomSessions,
        long classroomEvents,
        String lastActivityAt,
        long tokensLast30Days,
        long callsLast30Days,
        List<RecentJob> recentJobs,
        LearningMapService.LearningMap learningMap
    ) {}

    private static final int RECENT_JOBS = 5;

    private final JdbcTemplate jdbc;
    private final LearningMapService learningMap;
    private final CourseLibraryService courses;
    private final AdminAuditService audit;

    AdminUserLearningService(
        JdbcTemplate jdbc,
        LearningMapService learningMap,
        CourseLibraryService courses,
        AdminAuditService audit
    ) {
        this.jdbc = jdbc;
        this.learningMap = learningMap;
        this.courses = courses;
        this.audit = audit;
    }

    public AdminUserLearningView learning(long userId, long actorUserId, String requestId) {
        UserRow user = requireUser(userId);
        audit.recordRead(actorUserId, "ADMIN_VIEW_LEARNING_MAP", "USER", String.valueOf(userId), requestId);

        LocalDate since = LocalDate.now().minusDays(29);
        List<RecentJob> jobs = jdbc.query(
            "SELECT j.id, j.job_type, j.status, j.created_at,"
                + " (SELECT COALESCE(SUM(COALESCE(q.actual_tokens, q.estimated_tokens)), 0)"
                + "    FROM studyroom_generation_calls c"
                + "    JOIN ai_quota_reservations q ON q.request_id = c.quota_request_id"
                + "   WHERE c.job_id = j.id) AS tokens"
                + " FROM studyroom_generation_jobs j WHERE j.user_id = ?"
                + " ORDER BY j.created_at DESC LIMIT ?",
            (row, index) -> new RecentJob(
                row.getString("id"),
                row.getString("job_type"),
                row.getString("status"),
                timestamp(row.getTimestamp("created_at")),
                row.getLong("tokens")
            ),
            userId, RECENT_JOBS
        );

        return new AdminUserLearningView(
            user.id(),
            user.email(),
            user.username(),
            user.status(),
            courses.list(userId),
            count("SELECT COUNT(*) FROM classroom_bookmarks WHERE user_id = ?", userId),
            count("SELECT COUNT(*) FROM classroom_sessions WHERE user_id = ?", userId),
            count("SELECT COUNT(*) FROM classroom_events e JOIN classroom_sessions s ON s.id = e.session_id WHERE s.user_id = ?", userId),
            lastActivity(userId),
            count("SELECT COALESCE(SUM(COALESCE(actual_tokens, estimated_tokens)), 0) FROM ai_quota_reservations"
                + " WHERE user_id = ? AND completed_at IS NOT NULL AND quota_date >= ?", userId, since),
            count("SELECT COUNT(*) FROM ai_quota_reservations"
                + " WHERE user_id = ? AND completed_at IS NOT NULL AND quota_date >= ?", userId, since),
            jobs,
            learningMap.forUser(userId)
        );
    }

    private record UserRow(long id, String email, String username, String status) {}

    private UserRow requireUser(long userId) {
        List<UserRow> rows = jdbc.query(
            "SELECT id, email, username, status FROM users WHERE id = ?",
            (row, index) -> new UserRow(
                row.getLong("id"), row.getString("email"), row.getString("username"), row.getString("status")
            ),
            userId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "没有这个用户。");
        }
        return rows.get(0);
    }

    private String lastActivity(long userId) {
        List<Timestamp> rows = jdbc.query(
            "SELECT MAX(updated_at) FROM classroom_sessions WHERE user_id = ?",
            (row, index) -> row.getTimestamp(1),
            userId
        );
        return rows.isEmpty() ? null : timestamp(rows.get(0));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
