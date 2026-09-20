package com.feng.dsagent.classroom;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/classroom")
public class ClassroomController {

    private final ClassroomService classrooms;
    private final ClassroomPreparation preparation;

    public ClassroomController(ClassroomService classrooms, ClassroomPreparation preparation) {
        this.classrooms = classrooms;
        this.preparation = preparation;
    }

    @GetMapping("/lessons")
    List<ClassroomPreparation.Lesson> lessons(@RequestParam(required = false) String chapterId) { return preparation.lessons(chapterId); }

    @PostMapping("/preparations")
    ClassroomPreparation.Status prepare(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PrepareRequest request) { return preparation.start(user.userId(), request.lessonId()); }

    @GetMapping("/preparations/{id}")
    ClassroomPreparation.Status preparation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) { return preparation.status(user.userId(), id); }

    public record PrepareRequest(@NotBlank String lessonId) {}

    @GetMapping("/scripts")
    List<ClassroomScriptSummary> scripts(@RequestParam(required = false) String chapterId) {
        return classrooms.scripts(chapterId);
    }

    @PostMapping("/sessions")
    ClassroomSessionView create(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody CreateSessionRequest request
    ) {
        return classrooms.create(user.userId(), request.scriptId());
    }

    @GetMapping("/sessions/{id}")
    ClassroomSessionView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return classrooms.get(user.userId(), id);
    }

    @PutMapping("/sessions/{id}/slides")
    ClassroomSessionView pinSlide(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable String id,
        @Valid @RequestBody SlideRequest request
    ) {
        return classrooms.pinSlide(user.userId(), id, request.stepIndex(), request.slideId());
    }

    public record SlideRequest(@PositiveOrZero int stepIndex, @NotBlank @Size(max = 160) String slideId) {
    }

    @PostMapping("/sessions/{id}/actions")
    ClassroomSessionView action(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable String id,
        @Valid @RequestBody ActionRequest request
    ) {
        return classrooms.apply(user.userId(), id, action(request.action()), request.content(), request.expectedRevision());
    }

    private ClassroomAction action(String value) {
        try {
            return ClassroomAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLASSROOM_ACTION_INVALID", "课堂操作无效");
        }
    }

    public record CreateSessionRequest(@NotBlank String scriptId) {
    }

    public record ActionRequest(@NotBlank String action, @Size(max = 4000) String content, @PositiveOrZero Integer expectedRevision) {
    }
}
