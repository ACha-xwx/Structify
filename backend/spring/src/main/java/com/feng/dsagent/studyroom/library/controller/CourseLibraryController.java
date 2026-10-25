package com.feng.dsagent.studyroom.library.controller;

import com.feng.dsagent.studyroom.library.service.CourseLibraryService;

import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/courses")
public class CourseLibraryController {
    private final CourseLibraryService library;
    public CourseLibraryController(CourseLibraryService library){this.library=library;}
    @GetMapping List<CourseLibraryService.Course> list(@AuthenticationPrincipal AuthenticatedUser user){return library.list(user.userId());}
    @PatchMapping("/{id}") ResponseEntity<Void> rename(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,@Valid @RequestBody Rename body){library.rename(user.userId(),id,body.title());return ResponseEntity.noContent().build();}
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id){library.delete(user.userId(),id);return ResponseEntity.noContent().build();}
    public record Rename(@NotBlank @Size(max=160) String title){}
}
