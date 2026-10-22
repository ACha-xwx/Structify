package com.feng.dsagent.studyroom.teaching;

import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/studyroom-runs/{run}/fragments/{fragment}/lecture")
public class ClassroomLectureController {
    private final ClassroomLectureService service;
    public ClassroomLectureController(ClassroomLectureService service){this.service=service;}
    @GetMapping
    Map<String,Object> get(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,@PathVariable String fragment,@RequestParam int contentRevision){return service.get(user.userId(),run,fragment,contentRevision);}
    @PostMapping("/regions")
    LectureRegion save(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,@PathVariable String fragment,@Valid @RequestBody RegionRequest request){return service.save(user.userId(),run,fragment,request.contentRevision(),request.region());}
    @DeleteMapping("/regions/{id}")
    void remove(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String run,@PathVariable String fragment,@PathVariable String id,@RequestParam int contentRevision){service.remove(user.userId(),run,fragment,contentRevision,id);}
    public record RegionRequest(@Min(1) int contentRevision,@NotNull LectureRegion region){}
}
