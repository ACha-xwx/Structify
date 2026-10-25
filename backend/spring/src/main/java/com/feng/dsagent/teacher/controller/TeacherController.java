package com.feng.dsagent.teacher.controller;

import com.feng.dsagent.teacher.service.TeacherService;

import com.feng.dsagent.common.RequestIdFilter;
import com.feng.dsagent.learning.service.LearningMapService;
import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
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
public class TeacherController {
    private final TeacherService teacher;

    public TeacherController(TeacherService teacher) {
        this.teacher = teacher;
    }

    /** Classes the caller teaches (an administrator sees every class). */
    @GetMapping("/classes")
    public List<TeacherService.ClassSummary> classes(@AuthenticationPrincipal AuthenticatedUser user) {
        return teacher.classes(user.userId(), user.hasRole("ADMIN"));
    }

    /** Students of one class with their last activity and how much classroom work they have done. */
    @GetMapping("/classes/{classId}/students")
    public List<TeacherService.StudentRow> students(
        @PathVariable String classId,
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest servletRequest
    ) {
        return teacher.students(classId, user.userId(), user.hasRole("ADMIN"), requestId(servletRequest));
    }

    /**
     * The learning map of one student.
     *
     * <p>The shape is the same one the student sees on their own page, because it is the same calculation - the
     * teacher is not looking at a different number, just at somebody else's.
     */
    @GetMapping("/students/{studentId}/learning-map")
    public LearningMapService.LearningMap studentMap(
        @PathVariable long studentId,
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest servletRequest
    ) {
        return teacher.studentMap(studentId, user.userId(), user.hasRole("ADMIN"), requestId(servletRequest));
    }

    /**
     * One student's whole footprint - courses with progress, classroom work, bookmarks, and the mastery map -
     * in a single audited read. Spend and generation jobs are deliberately not part of this view.
     */
    @GetMapping("/students/{studentId}/footprint")
    public TeacherService.StudentFootprint studentFootprint(
        @PathVariable long studentId,
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest servletRequest
    ) {
        return teacher.studentFootprint(studentId, user.userId(), user.hasRole("ADMIN"), requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
