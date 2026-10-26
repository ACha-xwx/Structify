package com.feng.dsagent.teacher.service;


import org.springframework.stereotype.Service;
import com.feng.dsagent.admin.service.AdminAuditService;
import com.feng.dsagent.learning.service.LearningMapService;
import com.feng.dsagent.studyroom.library.service.CourseLibraryService;
import com.feng.dsagent.teacher.error.TeacherErrorCode;
import com.feng.dsagent.teacher.mapper.TeacherMapper;
import java.sql.Timestamp;
import java.util.List;

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

    /**
     * One student's learning footprint as the teacher sees it: courses with their progress, classroom work,
     * bookmarks and the mastery map. Deliberately without spend or generation jobs - what a lesson costs is an
     * operations question, and the teacher console is not the place that answers it.
     */
    public record StudentFootprint(
        long studentId,
        String username,
        String email,
        List<CourseLibraryService.Course> courses,
        long bookmarks,
        long classroomSessions,
        long classroomEvents,
        String lastActivityAt,
        LearningMapService.LearningMap learningMap
    ) {}

    private final TeacherMapper teachers;
    private final LearningMapService learningMap;
    private final CourseLibraryService courseLibrary;
    private final AdminAuditService audit;

    public TeacherService(
        TeacherMapper teachers,
        LearningMapService learningMap,
        CourseLibraryService courseLibrary,
        AdminAuditService audit
    ) {
        this.teachers = teachers;
        this.learningMap = learningMap;
        this.courseLibrary = courseLibrary;
        this.audit = audit;
    }

    /** Classes the caller teaches; an administrator sees every class. */
    public List<ClassSummary> classes(long callerId, boolean admin) {
        var rows = admin ? teachers.selectAllClasses() : teachers.selectClassesForTeacher(callerId);
        return rows.stream()
            .map(row -> new ClassSummary(row.id(), row.name(), row.term(), row.students()))
            .toList();
    }

    /** Students of one class, most recently active first. The read is audited: a roster is still learner data. */
    public List<StudentRow> students(String classId, long callerId, boolean admin, String requestId) {
        requireTeaching(classId, callerId, admin);
        audit.recordRead(callerId, "TEACHER_LIST_STUDENTS", "CLASS", classId, requestId);
        return teachers.selectStudents(classId).stream()
            .map(row -> new StudentRow(
                row.id(),
                row.username(),
                row.email(),
                row.status(),
                timestamp(row.lastActivity()),
                row.steps()
            ))
            .toList();
    }

    /** The learning map of one student, which the caller must share a class with. The read is audited. */
    public LearningMapService.LearningMap studentMap(long studentId, long callerId, boolean admin, String requestId) {
        if (!admin && !teachesStudent(studentId, callerId)) {
            throw TeacherErrorCode.STUDENT_NOT_IN_YOUR_CLASS.toException();
        }
        audit.recordRead(callerId, admin ? "ADMIN_VIEW_LEARNING_MAP" : "TEACHER_VIEW_LEARNING_MAP",
            "USER", String.valueOf(studentId), requestId);
        return learningMap.forUser(studentId);
    }

    /**
     * One student's whole footprint for the teacher who teaches them. One call, one audit row: the page should not
     * have to assemble a learner from five endpoints, and the ledger should not record five reads for one look.
     */
    public StudentFootprint studentFootprint(long studentId, long callerId, boolean admin, String requestId) {
        if (!admin && !teachesStudent(studentId, callerId)) {
            throw TeacherErrorCode.STUDENT_NOT_IN_YOUR_CLASS.toException();
        }
        audit.recordRead(callerId, admin ? "ADMIN_VIEW_LEARNING_MAP" : "TEACHER_VIEW_LEARNING_FOOTPRINT",
            "USER", String.valueOf(studentId), requestId);
        List<TeacherMapper.UserIdentityRow> identity = teachers.selectUserIdentity(studentId);
        if (identity.isEmpty()) {
            throw TeacherErrorCode.USER_NOT_FOUND.toException();
        }
        TeacherMapper.UserIdentityRow parts = identity.get(0);
        return new StudentFootprint(
            studentId,
            parts.username(),
            parts.email(),
            courseLibrary.list(studentId),
            nullToZero(teachers.countBookmarks(studentId)),
            nullToZero(teachers.countClassroomSessions(studentId)),
            nullToZero(teachers.countClassroomEvents(studentId)),
            timestamp(teachers.selectLastActivity(studentId)),
            learningMap.forUser(studentId)
        );
    }

    private void requireTeaching(String classId, long callerId, boolean admin) {
        if (admin) return;
        Long count = teachers.countTeachingEnrollment(classId, callerId);
        if (count == null || count == 0) {
            throw TeacherErrorCode.CLASS_NOT_YOURS.toException();
        }
    }

    private boolean teachesStudent(long studentId, long callerId) {
        Long count = teachers.countSharedClass(callerId, studentId);
        return count != null && count > 0;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
