package com.feng.dsagent.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.common.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * A teacher may read a student's profile only when the two of them share a class. That rule is the whole point
 * of this service, so it is tested from the outside: the same call succeeds for one caller and is refused for
 * another, with nothing else different.
 *
 * <p>The second promise pinned here: a successful read of somebody else's data is always written to the audit
 * ledger, by the teacher and by an administrator alike.
 */
@SpringBootTest
@Transactional
class TeacherServiceTest {

    @Autowired
    private TeacherService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long teacherId;
    private long otherTeacherId;
    private long studentId;
    private long outsiderId;
    private String classId;

    @BeforeEach
    void seed() {
        teacherId = user("teacher");
        otherTeacherId = user("other-teacher");
        studentId = user("student");
        outsiderId = user("outsider");

        classId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO classes (id, name, term) VALUES (?, ?, ?)", classId, "数据结构 1 班", "2026 秋");
        enroll(classId, teacherId, "TEACHER");
        enroll(classId, otherTeacherId, "TEACHER");
        enroll(classId, studentId, "STUDENT");
    }

    private long user(String name) {
        jdbc.update(
            "INSERT INTO users (email, password_hash, username, status) VALUES (?, ?, ?, 'ACTIVE')",
            name + "-" + UUID.randomUUID() + "@example.test", "x", name
        );
        return jdbc.queryForObject("SELECT MAX(id) FROM users", Long.class);
    }

    private void enroll(String classId, long userId, String role) {
        jdbc.update(
            "INSERT INTO class_enrollments (class_id, user_id, role_in_class) VALUES (?, ?, ?)",
            classId, userId, role
        );
    }

    private long auditRows(String action, String targetId, long actorId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_events WHERE action = ? AND target_id = ? AND actor_user_id = ?",
            Long.class, action, targetId, actorId
        );
    }

    @Test
    @DisplayName("a teacher sees only the classes they teach, an administrator sees all")
    void classesFollowTheTeacher() {
        assertThat(service.classes(teacherId, false))
            .anySatisfy(summary -> {
                if (summary.id().equals(classId)) {
                    assertThat(summary.students()).isEqualTo(1);
                }
            });

        String otherClass = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO classes (id, name) VALUES (?, ?)", otherClass, "别人的班");
        assertThat(service.classes(teacherId, false)).extracting(TeacherService.ClassSummary::id)
            .doesNotContain(otherClass);
        assertThat(service.classes(0L, true)).extracting(TeacherService.ClassSummary::id)
            .contains(otherClass);
    }

    @Test
    @DisplayName("the class roster lists its students and nobody else, and the read is audited")
    void rosterIsScopedToTheClass() {
        assertThat(service.students(classId, teacherId, false, "req-roster"))
            .extracting(TeacherService.StudentRow::id)
            .containsExactly(studentId);
        assertThat(auditRows("TEACHER_LIST_STUDENTS", classId, teacherId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a teacher who does not teach the class is refused the roster, and nothing is audited")
    void otherTeacherCannotReadTheRoster() {
        String foreignClass = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO classes (id, name) VALUES (?, ?)", foreignClass, "不属于他");
        enroll(foreignClass, studentId, "STUDENT");

        assertThatThrownBy(() -> service.students(foreignClass, teacherId, false, ""))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("这个班不在你名下");
        assertThat(auditRows("TEACHER_LIST_STUDENTS", foreignClass, teacherId)).isZero();
    }

    @Test
    @DisplayName("a student's map opens for the teacher of that class and for nobody else")
    void mapRequiresSharedClass() {
        assertThat(service.studentMap(studentId, teacherId, false, "req-map")).isNotNull();
        assertThat(auditRows("TEACHER_VIEW_LEARNING_MAP", String.valueOf(studentId), teacherId)).isEqualTo(1);

        assertThatThrownBy(() -> service.studentMap(studentId, outsiderId, false, ""))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不在你带的班");
        assertThat(auditRows("TEACHER_VIEW_LEARNING_MAP", String.valueOf(studentId), outsiderId)).isZero();
    }

    @Test
    @DisplayName("an administrator bypasses the class check, and that bypass is audited under its own action")
    void adminBypassIsAudited() {
        long adminId = user("admin");
        assertThat(service.studentMap(studentId, adminId, true, "req-admin")).isNotNull();
        assertThat(auditRows("ADMIN_VIEW_LEARNING_MAP", String.valueOf(studentId), adminId)).isEqualTo(1);
    }

    @Test
    @DisplayName("the footprint gathers classroom work and the map in one audited read, and refuses outsiders")
    void footprintIsOneAuditedRead() {
        String chapterId = "ch-" + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO chapters (id, chapter_number, title) VALUES (?, ?, ?)",
            chapterId, (int) (System.currentTimeMillis() % 900000) + 200000, "足迹测试章"
        );
        String scriptId = "script-" + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO classroom_scripts (id, chapter_id, title, script_json) VALUES (?, ?, ?, '{}')",
            scriptId, chapterId, "足迹测试脚本"
        );
        String sessionId = "session-" + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO classroom_sessions (id, user_id, script_id, state) VALUES (?, ?, ?, 'STEP')",
            sessionId, studentId, scriptId
        );
        jdbc.update(
            "INSERT INTO classroom_events (session_id, action, from_state, to_state) VALUES (?, 'ADVANCE', 'A', 'B')",
            sessionId
        );
        jdbc.update(
            "INSERT INTO classroom_bookmarks (user_id, session_id, step_index, title) VALUES (?, ?, 0, '回来看这里')",
            studentId, sessionId
        );

        TeacherService.StudentFootprint footprint = service.studentFootprint(studentId, teacherId, false, "req-foot");

        assertThat(footprint.studentId()).isEqualTo(studentId);
        assertThat(footprint.email()).contains("@example.test");
        assertThat(footprint.classroomSessions()).isEqualTo(1);
        assertThat(footprint.classroomEvents()).isEqualTo(1);
        assertThat(footprint.bookmarks()).isEqualTo(1);
        assertThat(footprint.lastActivityAt()).isNotNull();
        assertThat(footprint.courses()).isEmpty();
        assertThat(footprint.learningMap()).isNotNull();
        assertThat(auditRows("TEACHER_VIEW_LEARNING_FOOTPRINT", String.valueOf(studentId), teacherId)).isEqualTo(1);

        assertThatThrownBy(() -> service.studentFootprint(studentId, outsiderId, false, ""))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不在你带的班");
        assertThat(auditRows("TEACHER_VIEW_LEARNING_FOOTPRINT", String.valueOf(studentId), outsiderId)).isZero();
    }
}
