package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/studyroom-runs/{run}")
public class FragmentController {
    private final FragmentLearningService learning;
    public FragmentController(FragmentLearningService learning){this.learning=learning;}
    @PostMapping("/focus")
    Map<String,Object> focus(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Focus body) {
        return learning.focus(user.userId(),run,body.unitId(),body.sequence(),key);
    }
    @PostMapping("/progress")
    Map<String,Object> progress(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Progress body) {
        return learning.progress(user.userId(),run,body.unitId(),body.fragmentId(),body.completed(),key);
    }
    @PostMapping("/fragments/{fragment}/retry")
    ResponseEntity<Map<String,Object>> retry(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,@PathVariable String fragment,
            @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted().body(learning.retry(user.userId(),run,fragment,key,null));
    }
    @PostMapping("/fragments/{fragment}/regenerate")
    ResponseEntity<Map<String,Object>> regenerate(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,@PathVariable String fragment,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Regenerate body) {
        return ResponseEntity.accepted().body(learning.retry(user.userId(),run,fragment,key,body.baseContentRevision()));
    }
    public record Focus(@NotBlank @Size(max=36) String unitId,@Min(1) long sequence){}
    public record Progress(@NotBlank @Size(max=36) String unitId,@NotBlank @Size(max=36) String fragmentId,boolean completed){}
    public record Regenerate(@NotNull @Min(0) Integer baseContentRevision){}
}
