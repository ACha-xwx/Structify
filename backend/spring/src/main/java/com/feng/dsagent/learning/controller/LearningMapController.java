package com.feng.dsagent.learning.controller;

import com.feng.dsagent.learning.service.LearningMapService;

import com.feng.dsagent.security.domain.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The learner's own map. Authentication is required by the global rule, and the map is always the caller's:
 * there is no user id in the path, so nobody can ask for somebody else's profile.
 */
@RestController
@RequestMapping("/api/v1/users/learning-map")
public class LearningMapController {
    private final LearningMapService map;

    public LearningMapController(LearningMapService map) {
        this.map = map;
    }

    @GetMapping
    public LearningMapService.LearningMap read(@AuthenticationPrincipal AuthenticatedUser user) {
        return map.forUser(user.userId());
    }
}
