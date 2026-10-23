package com.feng.dsagent.admin;

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
 * An administrator maintains the roster from a name or an email address, and that maintenance is what decides who may
 * read whom. These tests keep the useful cases honest (create, add by email, add by username) and make sure the
 * failures say which one went wrong instead of failing with a database error.
 */
@SpringBootTest
@Transactional
class AdminClassServiceTest {

    @Autowired
    private AdminClassService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long studentId;
    private long teacherId;
    private String studentEmail;

    @BeforeEach
    void seed() {
        studentEmail = "learner-" + UUID.randomUUID() + "@example.test";
        studentId = user(studentEmail, "student-one");
        teacherId = user("instructor-" + UUID.randomUUID() + "@example.test", "teacher-one");
    }

    private long user(String email, String username) {
        jdbc.update(
            "INSERT INTO users (email, password_hash, username, status) VALUES (?, ?, ?, 'ACTIVE')",
            email, "x", username
        );
        return jdbc.queryForObject("SELECT MAX(id) FROM users", Long.class);
    }

    @Test
    @DisplayName("a new class starts empty and can be found again")
    void createsAClass() {
        AdminClassService.ClassRow created = service.create("  数据结构 1 班  ", " 2026 秋 ");

        assertThat(created.name()).isEqualTo("数据结构 1 班");
        assertThat(created.term()).isEqualTo("2026 秋");
        assertThat(created.students()).isZero();
        assertThat(created.teachers()).isEmpty();

        assertThat(service.classes())
            .anySatisfy(row -> assertThat(row.id()).isEqualTo(created.id()));
        assertThat(service.members(created.id())).isEmpty();
    }

    @Test
    @DisplayName("a blank class name is refused before anything is written")
    void refusesABlankName() {
        assertThatThrownBy(() -> service.create("   ", null))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("CLASS_NAME_REQUIRED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM classes", Long.class)).isZero();
    }

    @Test
    @DisplayName("a person is added by email or by username, as a student or as the teacher")
    void addsMembersByIdentifier() {
        String classId = service.create("数据结构 1 班", null).id();

        AdminClassService.MemberRow byEmail = service.addMember(classId, studentEmail, "STUDENT");
        AdminClassService.MemberRow byUsername = service.addMember(classId, "TEACHER-ONE", "TEACHER");

        assertThat(byEmail.userId()).isEqualTo(studentId);
        assertThat(byEmail.roleInClass()).isEqualTo("STUDENT");
        assertThat(byUsername.userId()).isEqualTo(teacherId);
        assertThat(byUsername.roleInClass()).isEqualTo("TEACHER");
        assertThat(service.members(classId)).hasSize(2);
        assertThat(service.classes())
            .filteredOn(row -> row.id().equals(classId))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.students()).isEqualTo(1);
                assertThat(row.teachers()).containsExactly("teacher-one");
            });
    }

    @Test
    @DisplayName("an unknown identifier, a wrong role, and a repeat are each named")
    void namesEachFailure() {
        String classId = service.create("数据结构 1 班", null).id();
        service.addMember(classId, studentEmail, "STUDENT");

        assertThatThrownBy(() -> service.addMember(classId, "nobody@example.test", "STUDENT"))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("USER_NOT_FOUND"));
        assertThatThrownBy(() -> service.addMember(classId, studentEmail, "ADMIN"))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ROLE_IN_CLASS_INVALID"));
        assertThatThrownBy(() -> service.addMember(classId, "student-one", "STUDENT"))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ALREADY_ENROLLED"));
        assertThatThrownBy(() -> service.members("no-such-class"))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("CLASS_NOT_FOUND"));
    }

    @Test
    @DisplayName("removing a member, or the whole class, takes the enrolment with it")
    void removesMembersAndClasses() {
        String classId = service.create("数据结构 1 班", null).id();
        service.addMember(classId, studentEmail, "STUDENT");
        service.addMember(classId, "teacher-one", "TEACHER");

        service.removeMember(classId, studentId);
        assertThat(service.members(classId)).extracting(AdminClassService.MemberRow::userId).containsExactly(teacherId);

        service.delete(classId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM classes", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_enrollments WHERE class_id = ?", Long.class, classId))
            .isZero();
    }
}
