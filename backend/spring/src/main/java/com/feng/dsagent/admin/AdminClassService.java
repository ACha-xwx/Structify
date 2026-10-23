package com.feng.dsagent.admin;

import com.feng.dsagent.common.ApiException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Classes and who is in them, maintained by an administrator.
 *
 * <p>Nothing here decides what a teacher may read - that rule lives in {@code TeacherService}. This service only keeps
 * the roster truthful: a class has a name, and a person is in it as either a student or a teacher. Adding a teacher is
 * what grants them the right to see the learners of that class, so it is deliberately a separate, explicit step from
 * giving somebody the TEACHER role.
 */
@Service
public class AdminClassService {

    private static final int MAX_NAME = 120;
    private static final int MAX_TERM = 40;

    /** One class with the numbers an administrator needs to recognise it. */
    public record ClassRow(String id, String name, String term, long students, List<String> teachers) {}

    /** One person in a class. */
    public record MemberRow(long userId, String username, String email, String roleInClass, String joinedAt) {}

    private final JdbcTemplate jdbc;

    AdminClassService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ClassRow> classes() {
        Map<String, List<String>> teachersByClass = new LinkedHashMap<>();
        jdbc.query(
            "SELECT e.class_id, COALESCE(u.username, u.email) AS who"
                + " FROM class_enrollments e JOIN users u ON u.id = e.user_id"
                + " WHERE e.role_in_class = 'TEACHER' ORDER BY e.class_id, u.id",
            (row) -> {
                teachersByClass.computeIfAbsent(row.getString("class_id"), key -> new ArrayList<>())
                    .add(row.getString("who"));
            }
        );
        return jdbc.query(
            "SELECT c.id, c.name, c.term,"
                + " (SELECT COUNT(*) FROM class_enrollments e WHERE e.class_id = c.id AND e.role_in_class = 'STUDENT') AS students"
                + " FROM classes c ORDER BY c.created_at DESC, c.id",
            (row, index) -> new ClassRow(
                row.getString("id"),
                row.getString("name"),
                row.getString("term"),
                row.getLong("students"),
                teachersByClass.getOrDefault(row.getString("id"), List.of())
            )
        );
    }

    public ClassRow create(String name, String term) {
        String cleanName = requireText(name, MAX_NAME, "CLASS_NAME_REQUIRED", "班级名称不能为空。");
        String cleanTerm = optionalText(term, MAX_TERM, "CLASS_TERM_TOO_LONG", "学期名称太长。");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO classes (id, name, term) VALUES (?, ?, ?)", id, cleanName, cleanTerm);
        return new ClassRow(id, cleanName, cleanTerm, 0, List.of());
    }

    public List<MemberRow> members(String classId) {
        requireClass(classId);
        return jdbc.query(
            "SELECT u.id, u.username, u.email, e.role_in_class, e.joined_at"
                + " FROM class_enrollments e JOIN users u ON u.id = e.user_id"
                + " WHERE e.class_id = ? ORDER BY e.role_in_class, e.joined_at, u.id",
            (row, index) -> new MemberRow(
                row.getLong("id"),
                row.getString("username"),
                row.getString("email"),
                row.getString("role_in_class"),
                timestamp(row.getTimestamp("joined_at"))
            ),
            classId
        );
    }

    /** Adds a person found by email or username; that is what an administrator actually knows about them. */
    public MemberRow addMember(String classId, String identifier, String roleInClass) {
        requireClass(classId);
        String role = requireRole(roleInClass);
        String who = requireText(identifier, 255, "IDENTIFIER_REQUIRED", "请填写邮箱或用户名。");
        Long userId = jdbc.query(
            "SELECT id FROM users WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?) ORDER BY id",
            (row, index) -> row.getLong("id"),
            who, who
        ).stream().findFirst().orElse(null);
        if (userId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到这个邮箱或用户名对应的账号。");
        }
        try {
            jdbc.update(
                "INSERT INTO class_enrollments (class_id, user_id, role_in_class) VALUES (?, ?, ?)",
                classId, userId, role
            );
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ENROLLED", "这个账号已经在这个班里了。");
        }
        return member(classId, userId);
    }

    public void removeMember(String classId, long userId) {
        requireClass(classId);
        jdbc.update("DELETE FROM class_enrollments WHERE class_id = ? AND user_id = ?", classId, userId);
    }

    public void delete(String classId) {
        requireClass(classId);
        jdbc.update("DELETE FROM classes WHERE id = ?", classId);
    }

    private MemberRow member(String classId, long userId) {
        return jdbc.queryForObject(
            "SELECT u.id, u.username, u.email, e.role_in_class, e.joined_at"
                + " FROM class_enrollments e JOIN users u ON u.id = e.user_id"
                + " WHERE e.class_id = ? AND u.id = ?",
            (row, index) -> new MemberRow(
                row.getLong("id"),
                row.getString("username"),
                row.getString("email"),
                row.getString("role_in_class"),
                timestamp(row.getTimestamp("joined_at"))
            ),
            classId, userId
        );
    }

    private void requireClass(String classId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM classes WHERE id = ?", Long.class, classId);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "这个班级不存在。");
        }
    }

    private static String requireRole(String roleInClass) {
        if ("STUDENT".equals(roleInClass) || "TEACHER".equals(roleInClass)) return roleInClass;
        throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_IN_CLASS_INVALID", "班内角色只能是学生或教师。");
    }

    private static String requireText(String value, int max, String code, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return clean;
    }

    private static String optionalText(String value, int max, String code, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return clean.isEmpty() ? null : clean;
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
