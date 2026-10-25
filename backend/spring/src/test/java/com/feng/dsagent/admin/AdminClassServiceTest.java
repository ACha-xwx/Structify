package com.feng.dsagent.admin;

import com.feng.dsagent.admin.service.AdminClassService;

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
 *
 * <p>Two promises are pinned down here because they are easy to lose in a refactor: enrolling a teacher also grants
 * the platform TEACHER role (otherwise the teacher console would 403 and nobody would know why), and every change
 * lands in the audit ledger with the acting administrator.
 */
@SpringBootTest
@Transactional
class AdminClassServiceTest {

    @Autowired
    private AdminClassService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;
    private long studentId;
    private long teacherId;
    private String studentEmail;

    @BeforeEach
    void seed() {
        adminId = user("admin-" + UUID.randomUUID() + "@example.test", "admin-one");
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

    private long auditRows(String action, String targetId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_events WHERE action = ? AND target_id = ? AND actor_user_id = ?",
            Long.class, action, targetId, adminId
        );
    }

    @Test
    @DisplayName("a new class starts empty and can be found again")
    void createsAClass() {
        AdminClassService.ClassRow created = service.create("  数据结构 1 班  ", " 2026 秋 ", adminId, "req-create");

        assertThat(created.name()).isEqualTo("数据结构 1 班");
        assertThat(created.term()).isEqualTo("2026 秋");
        assertThat(created.students()).isZero();
        assertThat(created.teachers()).isEmpty();

        assertThat(service.classes())
            .anySatisfy(row -> assertThat(row.id()).isEqualTo(created.id()));
        assertThat(service.members(created.id())).isEmpty();
        assertThat(auditRows("CLASS_CREATE", created.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("a blank class name is refused before anything is written")
    void refusesABlankName() {
        assertThatThrownBy(() -> service.create("   ", null, adminId, ""))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("CLASS_NAME_REQUIRED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM classes", Long.class)).isZero();
    }

    @Test
    @DisplayName("a person is added by email or by username, as a student or as the teacher")
    void addsMembersByIdentifier() {
        String classId = service.create("数据结构 1 班", null, adminId, "").id();

        AdminClassService.AddMemberResult byEmail = service.addMember(classId, studentEmail, "STUDENT", adminId, "");
        AdminClassService.AddMemberResult byUsername = service.addMember(classId, "TEACHER-ONE", "TEACHER", adminId, "req-add");

        assertThat(byEmail.member().userId()).isEqualTo(studentId);
        assertThat(byEmail.member().roleInClass()).isEqualTo("STUDENT");
        assertThat(byEmail.teacherRoleGranted()).isFalse();
        assertThat(byUsername.member().userId()).isEqualTo(teacherId);
        assertThat(byUsername.member().roleInClass()).isEqualTo("TEACHER");
        assertThat(service.members(classId)).hasSize(2);
        assertThat(service.classes())
            .filteredOn(row -> row.id().equals(classId))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.students()).isEqualTo(1);
                assertThat(row.teachers()).containsExactly("teacher-one");
            });
        assertThat(auditRows("CLASS_ADD_MEMBER", classId)).isEqualTo(2);
    }

    @Test
    @DisplayName("enrolling a teacher grants the platform TEACHER role, exactly once")
    void enrollingATeacherGrantsTheRole() {
        String classId = service.create("数据结构 1 班", null, adminId, "").id();

        AdminClassService.AddMemberResult first = service.addMember(classId, "teacher-one", "TEACHER", adminId, "");

        assertThat(first.teacherRoleGranted()).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role = 'TEACHER'", Long.class, teacherId
        )).isEqualTo(1);

        // A second class with the same teacher: the role is already there, so nothing new is granted.
        String secondClass = service.create("数据结构 2 班", null, adminId, "").id();
        AdminClassService.AddMemberResult again = service.addMember(secondClass, "teacher-one", "TEACHER", adminId, "");
        assertThat(again.teacherRoleGranted()).isFalse();

        // A student enrolment never touches platform roles.
        service.addMember(classId, studentEmail, "STUDENT", adminId, "");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_roles WHERE user_id = ?", Long.class, studentId
        )).isZero();
    }

    @Test
    @DisplayName("an unknown identifier, a wrong role, and a repeat are each named")
    void namesEachFailure() {
        String classId = service.create("数据结构 1 班", null, adminId, "").id();
        service.addMember(classId, studentEmail, "STUDENT", adminId, "");

        assertThatThrownBy(() -> service.addMember(classId, "nobody@example.test", "STUDENT", adminId, ""))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("USER_NOT_FOUND"));
        assertThatThrownBy(() -> service.addMember(classId, studentEmail, "ADMIN", adminId, ""))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ROLE_IN_CLASS_INVALID"));
        assertThatThrownBy(() -> service.addMember(classId, "student-one", "STUDENT", adminId, ""))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ALREADY_ENROLLED"));
        assertThatThrownBy(() -> service.members("no-such-class"))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("CLASS_NOT_FOUND"));

        // A refused write must not leave an audit row that claims success.
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_events WHERE action = 'CLASS_ADD_MEMBER' AND target_id = ? AND result = 'SUCCESS'",
            Long.class, classId
        )).isEqualTo(1);
    }

    @Test
    @DisplayName("removing a member, or the whole class, takes the enrolment with it and says so in the audit")
    void removesMembersAndClasses() {
        String classId = service.create("数据结构 1 班", null, adminId, "").id();
        service.addMember(classId, studentEmail, "STUDENT", adminId, "");
        service.addMember(classId, "teacher-one", "TEACHER", adminId, "");

        service.removeMember(classId, studentId, adminId, "req-remove");
        assertThat(service.members(classId)).extracting(AdminClassService.MemberRow::userId).containsExactly(teacherId);
        assertThat(auditRows("CLASS_REMOVE_MEMBER", classId)).isEqualTo(1);

        // Removing the teacher from the class does not strip the platform role: it belongs to the account.
        service.removeMember(classId, teacherId, adminId, "");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role = 'TEACHER'", Long.class, teacherId
        )).isEqualTo(1);

        service.delete(classId, adminId, "");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM classes", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_enrollments WHERE class_id = ?", Long.class, classId))
            .isZero();
        assertThat(auditRows("CLASS_DELETE", classId)).isEqualTo(1);
    }
}
