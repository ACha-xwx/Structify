package com.feng.dsagent.classroom.controller;

import com.feng.dsagent.classroom.ClassroomBookmark;

import com.feng.dsagent.classroom.service.ClassroomBookmarkService;

import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The learner's own saved steps. Authentication is required by the global rule; ownership is enforced per row. */
@RestController
@RequestMapping("/api/v1/users/bookmarks")
public class ClassroomBookmarkController {
    private final ClassroomBookmarkService bookmarks;

    public ClassroomBookmarkController(ClassroomBookmarkService bookmarks) {
        this.bookmarks = bookmarks;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassroomBookmark save(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody BookmarkRequest request) {
        return bookmarks.save(user.userId(), request.sessionId(), request.stepIndex());
    }

    @GetMapping
    public List<ClassroomBookmark> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return bookmarks.list(user.userId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        bookmarks.delete(user.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** What the learner points at: which lesson they were in, and which step of it. */
    public record BookmarkRequest(@NotBlank @Size(max = 64) String sessionId, @PositiveOrZero int stepIndex) {
    }
}
