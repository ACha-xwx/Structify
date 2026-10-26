package com.feng.dsagent.studyroom.checks.controller;

import com.feng.dsagent.studyroom.checks.service.ClassroomCheckService;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/studyroom-runs/{run}")
public class ClassroomCheckController {
    private final ClassroomCheckService checks;
    public ClassroomCheckController(ClassroomCheckService checks){this.checks=checks;}
    @GetMapping("/knowledge-points/{unit}/checks")
    public Map<String,Object> get(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,@PathVariable String unit){return checks.get(user.userId(),run,unit);}
    @PostMapping("/knowledge-points/{unit}/checks")
    public ResponseEntity<Map<String,Object>> prepare(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,@PathVariable String unit,@RequestHeader("Idempotency-Key") String key){return ResponseEntity.accepted().body(checks.prepare(user.userId(),run,unit,key));}
    @PostMapping("/checks/{check}/attempts")
    public Map<String,Object> attempt(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,@PathVariable String check,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Attempt body){return checks.attempt(user.userId(),run,check,body.selectedOption(),key);}
    public record Attempt(@NotBlank @Size(max=32) String selectedOption){}
}
