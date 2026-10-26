package com.feng.dsagent.classroom.controller;

import com.feng.dsagent.classroom.vo.ClassroomScriptSummaryVO;
import com.feng.dsagent.classroom.support.ClassroomPreparation;
import com.feng.dsagent.classroom.vo.ClassroomSessionVO;
import com.feng.dsagent.classroom.domain.ClassroomAction;

import com.feng.dsagent.classroom.service.ClassroomService;

import com.feng.dsagent.classroom.error.ClassroomErrorCode;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.feng.dsagent.classroom.dto.PrepareDTO;
import com.feng.dsagent.classroom.dto.SlideDTO;
import com.feng.dsagent.classroom.dto.CreateSessionDTO;
import com.feng.dsagent.classroom.dto.ActionDTO;

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
    public List<ClassroomPreparation.Lesson> lessons(@RequestParam(required = false) String chapterId) { return preparation.lessons(chapterId); }

    @PostMapping("/preparations")
    public ClassroomPreparation.Status prepare(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PrepareDTO request) { return preparation.start(user.userId(), request.lessonId()); }

    @GetMapping("/preparations/{id}")
    public ClassroomPreparation.Status preparation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) { return preparation.status(user.userId(), id); }

    @GetMapping("/scripts")
    public List<ClassroomScriptSummaryVO> scripts(@RequestParam(required = false) String chapterId) {
        return classrooms.scripts(chapterId);
    }

    @PostMapping("/sessions")
    public ClassroomSessionVO create(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody CreateSessionDTO request
    ) {
        return classrooms.create(user.userId(), request.scriptId());
    }

    @GetMapping("/sessions/{id}")
    public ClassroomSessionVO get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return classrooms.get(user.userId(), id);
    }

    @PutMapping("/sessions/{id}/slides")
    public ClassroomSessionVO pinSlide(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable String id,
        @Valid @RequestBody SlideDTO request
    ) {
        return classrooms.pinSlide(user.userId(), id, request.stepIndex(), request.slideId());
    }

    @PostMapping("/sessions/{id}/actions")
    public ClassroomSessionVO action(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable String id,
        @Valid @RequestBody ActionDTO request
    ) {
        return classrooms.apply(user.userId(), id, action(request.action()), request.content(), request.expectedRevision());
    }

    private ClassroomAction action(String value) {
        try {
            return ClassroomAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException error) {
            throw ClassroomErrorCode.ACTION_INVALID.toException();
        }
    }

}
