package com.feng.dsagent.studyroom.draft;

import com.feng.dsagent.security.AuthenticatedUser;
import com.feng.dsagent.studyroom.fragment.FragmentReadService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class OutlineController {
    private final OutlineService outlines;
    private final FragmentReadService units;

    public OutlineController(OutlineService outlines, FragmentReadService units) {
        this.outlines = outlines;
        this.units = units;
    }

    @PostMapping("/study-outlines")
    ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Create body) {
        return ResponseEntity.accepted()
                .body(
                        outlines.create(
                                user.userId(),
                                body.question(),
                                body.depth(),
                                body.chapterId(),
                                body.sourceRoomId(),
                                key));
    }

    @GetMapping("/study-outlines/{id}")
    Map<String, Object> get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return outlines.get(user.userId(), id);
    }

    @PostMapping("/study-outlines/{id}/revisions")
    ResponseEntity<Map<String, Object>> revise(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Revision body) {
        return ResponseEntity.accepted()
                .body(
                        outlines.revise(
                                user.userId(),
                                id,
                                body.baseRevision(),
                                body.feedback(),
                                body.depth(),
                                key));
    }

    @PostMapping("/study-outlines/{id}/manual-revisions")
    Map<String, Object> manualRevision(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ManualRevision body) {
        return outlines.manualRevision(user.userId(), id, body.baseRevision(), body.units(), key);
    }

    public record ManualRevision(@Min(1) int baseRevision,
            @NotEmpty @Size(max=24) List<@NotNull @Valid ManualUnit> units) {}
    public record ManualUnit(
            @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,79}") String id,
            @NotBlank @Size(max=160) String title,
            @NotBlank @Size(max=400) String objective) {}

    @PostMapping("/study-outlines/{id}/repairs")
    Map<String, Object> repair(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Repair body) {
        return outlines.repair(user.userId(), id, body.baseRevision(), body.fixes(), key);
    }

    public record Repair(@Min(1) int baseRevision,
            @NotEmpty @Size(max=24) List<@NotNull @Valid RepairFix> fixes) {}
    public record RepairFix(
            @NotBlank @Size(max=64) String code,
            @Size(max=80) String unitId) {}

    @PostMapping("/study-outlines/{id}/confirm")
    Map<String, Object> confirm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Confirm body) {
        return outlines.confirm(user.userId(), id, body.revision(), key, body.planHash());
    }

    @PostMapping("/study-outlines/{id}/restore")
    Map<String, Object> restore(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Restore body) {
        return outlines.restore(user.userId(), id, body.baseRevision(), body.targetRevision(), key);
    }

    @PostMapping("/study-outlines/{id}/retry")
    ResponseEntity<Map<String, Object>> retry(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted().body(outlines.retry(user.userId(), id, key));
    }

    @GetMapping("/study-rooms/{id}/run")
    Map<String, Object> current(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return units.current(user.userId(), id);
    }

    @GetMapping("/studyroom-runs/{id}")
    Map<String, Object> run(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return units.get(user.userId(), id);
    }

    public record Create(
            @NotBlank @Size(max = 4000) String question,
            @NotNull LearningDepth depth,
            @Size(max = 64) String chapterId,
            @Size(max = 36) String sourceRoomId) {}

    public record Revision(
            @Min(1) int baseRevision,
            @NotBlank @Size(max = 4000) String feedback,
            LearningDepth depth) {}

    public record Restore(@Min(1) int baseRevision, @Min(1) int targetRevision) {}

    public record Confirm(@Min(1) int revision, @NotBlank @Size(max=64) String planHash) {}
}
