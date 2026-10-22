package com.feng.dsagent.studyroom.events;

import com.feng.dsagent.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class ClassroomEventController {
    private final ClassroomEventSubscriptions subscriptions;
    public ClassroomEventController(ClassroomEventSubscriptions subscriptions) {this.subscriptions=subscriptions;}

    @GetMapping(value="/study-outlines/{id}/events",produces="text/event-stream")
    SseEmitter outline(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,@RequestParam(defaultValue="0") long since) {
        return subscriptions.subscribe(user.userId(),"outline:"+id,since);
    }

    @GetMapping(value="/studyroom-runs/{id}/events",produces="text/event-stream")
    SseEmitter run(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,@RequestParam(defaultValue="0") long since) {
        return subscriptions.subscribe(user.userId(),"run:"+id,since);
    }

    @GetMapping(value="/study-rooms/{room}/messages/{question}/events",produces="text/event-stream")
    SseEmitter question(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String room,@PathVariable String question,@RequestParam(defaultValue="0") long since) {
        subscriptions.requireRoom(user.userId(),"question:"+question,room);
        return subscriptions.subscribe(user.userId(),"question:"+question,since);
    }
}
