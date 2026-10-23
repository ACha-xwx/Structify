package com.feng.dsagent.admin;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Class maintenance. Keeping this under the administrator boundary is the point: adding a teacher to a class is what
 * lets them read those learners, so it must not be reachable by the teacher themselves.
 */
@RestController
@RequestMapping("/api/v1/admin/classes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminClassController {

    public record CreateClassRequest(@NotBlank String name, String term) {}
    public record AddMemberRequest(@NotBlank String identifier, @NotBlank String roleInClass) {}

    private final AdminClassService classes;

    public AdminClassController(AdminClassService classes) {
        this.classes = classes;
    }

    @GetMapping
    public List<AdminClassService.ClassRow> classes() {
        return classes.classes();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminClassService.ClassRow create(@RequestBody CreateClassRequest request) {
        return classes.create(request.name(), request.term());
    }

    @GetMapping("/{classId}/members")
    public List<AdminClassService.MemberRow> members(@PathVariable String classId) {
        return classes.members(classId);
    }

    @PostMapping("/{classId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminClassService.MemberRow addMember(@PathVariable String classId, @RequestBody AddMemberRequest request) {
        return classes.addMember(classId, request.identifier(), request.roleInClass());
    }

    @DeleteMapping("/{classId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable String classId, @PathVariable long userId) {
        classes.removeMember(classId, userId);
    }

    @DeleteMapping("/{classId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String classId) {
        classes.delete(classId);
    }
}
