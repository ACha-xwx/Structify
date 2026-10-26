package com.feng.dsagent.admin.service;

import com.feng.dsagent.admin.error.AdminErrorCode;
import com.feng.dsagent.teacher.service.TeacherService;
import com.feng.dsagent.admin.service.AdminAuditService;
import com.feng.dsagent.admin.mapper.AdminClassMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Classes and who is in them, maintained by an administrator.
 *
 * <p>Nothing here decides what a teacher may read - that rule lives in {@code TeacherService}. This service only keeps
 * the roster truthful: a class has a name, and a person is in it as either a student or a teacher.
 *
 * <p>Adding somebody as a teacher of a class is meant to be the whole job, so it also ensures their account carries
 * the TEACHER role: an administrator who enrols a teacher expects that person to open the teacher console, not to
 * hit a 403 until somebody thinks of a second switch on the users page. Removing a member only withdraws the class;
 * the platform role stays, because the same person may teach other classes. Every change is written to the audit
 * ledger with the acting administrator.
 */
@Service
public class AdminClassService {

    private static final int MAX_NAME = 120;
    private static final int MAX_TERM = 40;

    /** One class with the numbers an administrator needs to recognise it. */
    public record ClassRow(String id, String name, String term, long students, List<String> teachers) {}

    /** One person in a class. */
    public record MemberRow(long userId, String username, String email, String roleInClass, String joinedAt) {}

    /**
     * The enrolment that just happened, plus whether this call also granted the platform TEACHER role -
     * the console says so explicitly, because "can see the class" depends on both sides.
     */
    public record AddMemberResult(MemberRow member, boolean teacherRoleGranted) {}

    private final AdminClassMapper classes;
    private final AdminAuditService audit;

    public AdminClassService(AdminClassMapper classes, AdminAuditService audit) {
        this.classes = classes;
        this.audit = audit;
    }

    public List<ClassRow> classes() {
        Map<String, List<String>> teachersByClass = new LinkedHashMap<>();
        for (AdminClassMapper.TeacherNameRow row : classes.selectTeacherNames()) {
            teachersByClass.computeIfAbsent(row.classId(), key -> new ArrayList<>()).add(row.who());
        }
        return classes.selectClasses().stream()
            .map(row -> new ClassRow(
                row.id(),
                row.name(),
                row.term(),
                row.students(),
                teachersByClass.getOrDefault(row.id(), List.of())
            ))
            .toList();
    }

    public ClassRow create(String name, String term, long actorUserId, String requestId) {
        String cleanName = requireText(name, MAX_NAME, AdminErrorCode.CLASS_NAME_REQUIRED);
        String cleanTerm = optionalText(term, MAX_TERM, AdminErrorCode.CLASS_TERM_TOO_LONG);
        String id = UUID.randomUUID().toString();
        classes.insertClass(id, cleanName, cleanTerm);
        audit.record(actorUserId, "CLASS_CREATE", "CLASS", id, "SUCCESS", requestId,
            null, "name=" + cleanName + (cleanTerm == null ? "" : " term=" + cleanTerm));
        return new ClassRow(id, cleanName, cleanTerm, 0, List.of());
    }

    public List<MemberRow> members(String classId) {
        requireClass(classId);
        return classes.selectMembers(classId).stream()
            .map(AdminClassService::toMember)
            .toList();
    }

    /** Adds a person found by email or username; that is what an administrator actually knows about them. */
    public AddMemberResult addMember(String classId, String identifier, String roleInClass, long actorUserId, String requestId) {
        requireClass(classId);
        String role = requireRole(roleInClass);
        String who = requireText(identifier, 255, AdminErrorCode.IDENTIFIER_REQUIRED);
        Long userId = classes.findUserIdsByIdentifier(who).stream().findFirst().orElse(null);
        if (userId == null) {
            throw AdminErrorCode.CLASS_USER_NOT_FOUND.toException();
        }
        try {
            classes.insertEnrollment(classId, userId, role);
        } catch (DuplicateKeyException duplicate) {
            throw AdminErrorCode.ALREADY_ENROLLED.toException();
        }
        boolean granted = "TEACHER".equals(role) && ensureTeacherRole(userId);
        audit.record(actorUserId, "CLASS_ADD_MEMBER", "CLASS", classId, "SUCCESS", requestId,
            null, "userId=" + userId + " role=" + role + (granted ? " teacherRoleGranted" : ""));
        return new AddMemberResult(member(classId, userId), granted);
    }

    public void removeMember(String classId, long userId, long actorUserId, String requestId) {
        requireClass(classId);
        int removed = classes.deleteEnrollment(classId, userId);
        if (removed > 0) {
            audit.record(actorUserId, "CLASS_REMOVE_MEMBER", "CLASS", classId, "SUCCESS", requestId,
                "userId=" + userId, null);
        }
    }

    public void delete(String classId, long actorUserId, String requestId) {
        requireClass(classId);
        classes.deleteClass(classId);
        audit.record(actorUserId, "CLASS_DELETE", "CLASS", classId, "SUCCESS", requestId, null, null);
    }

    /**
     * Makes sure the account carries the TEACHER role; returns true when this call actually granted it.
     * The role is deliberately not taken away on removal - it belongs to the account, not to one class.
     */
    private boolean ensureTeacherRole(long userId) {
        try {
            classes.insertTeacherRole(userId);
            return true;
        } catch (DuplicateKeyException alreadyHasIt) {
            return false;
        }
    }

    private MemberRow member(String classId, long userId) {
        return toMember(classes.selectMember(classId, userId));
    }

    private void requireClass(String classId) {
        Long count = classes.countClass(classId);
        if (count == null || count == 0) {
            throw AdminErrorCode.CLASS_NOT_FOUND.toException();
        }
    }

    private static MemberRow toMember(AdminClassMapper.MemberRowData row) {
        return new MemberRow(
            row.id(),
            row.username(),
            row.email(),
            row.roleInClass(),
            timestamp(row.joinedAt())
        );
    }

    private static String requireRole(String roleInClass) {
        if ("STUDENT".equals(roleInClass) || "TEACHER".equals(roleInClass)) return roleInClass;
        throw AdminErrorCode.ROLE_IN_CLASS_INVALID.toException();
    }

    private static String requireText(String value, int max, AdminErrorCode error) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > max) {
            throw error.toException();
        }
        return clean;
    }

    private static String optionalText(String value, int max, AdminErrorCode error) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > max) {
            throw error.toException();
        }
        return clean.isEmpty() ? null : clean;
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
