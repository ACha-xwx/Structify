package com.feng.dsagent.auth.controller;

import com.feng.dsagent.auth.vo.UserVO;

import com.feng.dsagent.auth.service.AuthService;

import com.feng.dsagent.security.domain.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 职责：当前登录用户资料的只读 HTTP 入口。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService auth;

    public UserController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/me")
    public UserVO me(@AuthenticationPrincipal AuthenticatedUser user) {
        return auth.user(user.userId());
    }
}
