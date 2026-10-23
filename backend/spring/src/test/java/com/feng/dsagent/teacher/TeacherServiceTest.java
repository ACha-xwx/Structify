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
    @DisplayName("the class roster lists its students and nobody else")
    void rosterIsScopedToTheClass() {
        assertThat(service.students(classId, teacherId, false))
            .extracting(TeacherService.StudentRow::id)
            .containsExactly(studentId);
    }

    @Test
    @DisplayName("a teacher who does not teach the class is refused the roster")
    void otherTeacherCannotReadTheRoster() {
        String foreignClass = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO classes (id, name) VALUES (?, ?)", foreignClass, "不属于他");
        enroll(foreignClass, studentId, "STUDENT");

        assertThatThrownBy(() -> service.students(foreignClass, teacherId, false))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("这个班不在你名下");
    }

    @Test
    @DisplayName("a student's map opens for the teacher of that class and for nobody else")
    void mapRequiresSharedClass() {
        assertThat(service.studentMap(studentId, teacherId, false)).isNotNull();

        assertThatThrownBy(() -> service.studentMap(studentId, outsiderId, false))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不在你带的班");
    }
}
