package com.feng.dsagent.teacher;

import com.feng.dsagent.learning.LearningMapService;
import com.feng.dsagent.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The teacher's window on their own classes: who is in them, and how each of those students is doing.
 *
 * <p>Every method resolves "mine" from the caller, never from the path alone; the class and student ids are only
 * ever looked up after the caller's membership has been checked.
 */
@RestController
@RequestMapping("/api/v1/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
class TeacherController {

    private final TeacherService teacher;

    TeacherController(TeacherService teacher) {
        this.teacher = teacher;
    }

    /** Classes the caller teaches (an administrator sees every class). */
    @GetMapping("/classes")
    List<TeacherService.ClassSummary> classes(@AuthenticationPrincipal AuthenticatedUser user) {
        return teacher.classes(user.userId(), user.hasRole("ADMIN"));
    }

    /** Students of one class with their last activity and how much classroom work they have done. */
    @GetMapping("/classes/{classId}/students")
    List<TeacherService.StudentRow> students(
        @PathVariable String classId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return teacher.students(classId, user.userId(), user.hasRole("ADMIN"));
    }

    /**
     * The learning map of one student.
     *
     * <p>The shape is the same one the student sees on their own page, because it is the same calculation - the
     * teacher is not looking at a different number, just at somebody else's.
     */
    @GetMapping("/students/{studentId}/learning-map")
    LearningMapService.LearningMap studentMap(
        @PathVariable long studentId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return teacher.studentMap(studentId, user.userId(), user.hasRole("ADMIN"));
    }
}
