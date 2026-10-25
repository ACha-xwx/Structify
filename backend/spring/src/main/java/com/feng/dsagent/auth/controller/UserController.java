package com.feng.dsagent.auth.controller;

import com.feng.dsagent.auth.UserView;

import com.feng.dsagent.auth.service.AuthService;

import com.feng.dsagent.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService auth;

    public UserController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthenticatedUser user) {
        return auth.user(user.userId());
    }
}
