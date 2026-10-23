package com.feng.dsagent.teacher;

import com.feng.dsagent.admin.AdminAuditService;
import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.learning.LearningMapService;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * What a teacher may see about their own class, and nothing else.
 *
 * <p>Reading another person's learning profile is the most sensitive read in the product, so the rule lives here
 * rather than in the page: a request succeeds only when the caller teaches a class the student is enrolled in.
 * Administrators bypass that check on purpose. Either way the read is written to the audit ledger - the promise
 * that "somebody looked at this learner" must be answerable from data, not from trust.
 */
@Service
public class TeacherService {

    /** One class the caller teaches, with how many students are in it. */
    public record ClassSummary(String id, String name, String term, long students) {}

    /**
     * One student in a class.
     *
     * <p>Only cheap aggregates are returned here: a class can hold a whole cohort, and building a mastery map for
     * every row would read each learner's entire history before the page could render. The map is fetched for one
     * student when somebody asks for it.
     */
    public record StudentRow(long id, String username, String email, String status, String lastActivityAt,
                             long classroomSteps) {}

    private final JdbcTemplate jdbc;
    private final LearningMapService learningMap;
    private final AdminAuditService audit;

    TeacherService(JdbcTemplate jdbc, LearningMapService learningMap, AdminAuditService audit) {
        this.jdbc = jdbc;
        this.learningMap = learningMap;
        this.audit = audit;
    }

    /** Classes the caller teaches; an administrator sees every class. */
    public List<ClassSummary> classes(long callerId, boolean admin) {
        String select = "SELECT c.id, c.name, c.term,"
            + " (SELECT COUNT(*) FROM class_enrollments e WHERE e.class_id = c.id AND e.role_in_class = 'STUDENT') AS students"
            + " FROM classes c";
        if (admin) {
            return jdbc.query(select + " ORDER BY c.created_at DESC", (row, index) -> new ClassSummary(
                row.getString("id"), row.getString("name"), row.getString("term"), row.getLong("students")
            ));
        }
        return jdbc.query(
            select + " JOIN class_enrollments t ON t.class_id = c.id AND t.user_id = ? AND t.role_in_class = 'TEACHER'"
                + " ORDER BY c.created_at DESC",
            (row, index) -> new ClassSummary(
                row.getString("id"), row.getString("name"), row.getString("term"), row.getLong("students")
            ),
            callerId
        );
    }

    /** Students of one class, most recently active first. The read is audited: a roster is still learner data. */
    public List<StudentRow> students(String classId, long callerId, boolean admin, String requestId) {
        requireTeaching(classId, callerId, admin);
        audit.recordRead(callerId, "TEACHER_LIST_STUDENTS", "CLASS", classId, requestId);
        // Ordering is done outside a derived table: NULL ordering differs between MySQL and the in-memory database
        // the tests use, and "never started" has to sort last in both.
        return jdbc.query(
            "SELECT * FROM ("
                + " SELECT u.id, u.username, u.email, u.status,"
                + "   (SELECT MAX(s.updated_at) FROM classroom_sessions s WHERE s.user_id = u.id) AS last_activity,"
                + "   (SELECT COUNT(*) FROM classroom_events e JOIN classroom_sessions s ON s.id = e.session_id"
                + "     WHERE s.user_id = u.id) AS steps"
                + " FROM class_enrollments e JOIN users u ON u.id = e.user_id"
                + " WHERE e.class_id = ? AND e.role_in_class = 'STUDENT'"
                + ") t"
                + " ORDER BY CASE WHEN t.last_activity IS NULL THEN 1 ELSE 0 END, t.last_activity DESC, t.id",
            (row, index) -> new StudentRow(
                row.getLong("id"),
                row.getString("username"),
                row.getString("email"),
                row.getString("status"),
                timestamp(row.getTimestamp("last_activity")),
                row.getLong("steps")
            ),
            classId
        );
    }

    /** The learning map of one student, which the caller must share a class with. The read is audited. */
    public LearningMapService.LearningMap studentMap(long studentId, long callerId, boolean admin, String requestId) {
        if (!admin && !teachesStudent(studentId, callerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STUDENT_NOT_IN_YOUR_CLASS", "该学生不在你带的班里");
        }
        audit.recordRead(callerId, admin ? "ADMIN_VIEW_LEARNING_MAP" : "TEACHER_VIEW_LEARNING_MAP",
            "USER", String.valueOf(studentId), requestId);
        return learningMap.forUser(studentId);
    }

    private void requireTeaching(String classId, long callerId, boolean admin) {
        if (admin) return;
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM class_enrollments WHERE class_id = ? AND user_id = ? AND role_in_class = 'TEACHER'",
            Long.class, classId, callerId
        );
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLASS_NOT_YOURS", "这个班不在你名下");
        }
    }

    private boolean teachesStudent(long studentId, long callerId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM class_enrollments t JOIN class_enrollments s ON s.class_id = t.class_id"
                + " WHERE t.user_id = ? AND t.role_in_class = 'TEACHER'"
                + "   AND s.user_id = ? AND s.role_in_class = 'STUDENT'",
            Long.class, callerId, studentId
        );
        return count != null && count > 0;
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
