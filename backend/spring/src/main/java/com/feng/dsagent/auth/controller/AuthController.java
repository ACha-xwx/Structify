package com.feng.dsagent.auth.controller;

import com.feng.dsagent.auth.support.AuthRequestRateLimiter;
import com.feng.dsagent.auth.vo.AuthSessionVO;
import com.feng.dsagent.auth.vo.UserVO;

import com.feng.dsagent.auth.service.AuthService;

import com.feng.dsagent.auth.error.AuthErrorCode;
import com.feng.dsagent.security.config.SecurityProperties;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import com.feng.dsagent.security.support.NodeCompatibilityTokenIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.feng.dsagent.auth.dto.RequestCodeDTO;
import com.feng.dsagent.auth.dto.RegisterDTO;
import com.feng.dsagent.auth.dto.LoginDTO;
import com.feng.dsagent.auth.dto.ResetPasswordDTO;
import com.feng.dsagent.auth.vo.AuthVO;
import com.feng.dsagent.auth.vo.NodeCompatibilityTokenVO;
import com.feng.dsagent.auth.vo.VerificationCodeVO;

/**
 * 职责：注册登录、验证码与会话相关的认证 HTTP 入口。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final SecurityProperties security;
    private final AuthRequestRateLimiter rateLimiter;
    private final NodeCompatibilityTokenIssuer nodeCompatibilityTokens;

    public AuthController(
        AuthService auth,
        SecurityProperties security,
        AuthRequestRateLimiter rateLimiter,
        NodeCompatibilityTokenIssuer nodeCompatibilityTokens
    ) {
        this.auth = auth;
        this.security = security;
        this.rateLimiter = rateLimiter;
        this.nodeCompatibilityTokens = nodeCompatibilityTokens;
    }

    @PostMapping("/request-code")
    public VerificationCodeVO requestCode(
        @Valid @RequestBody RequestCodeDTO request,
        HttpServletRequest servletRequest
    ) {
        rateLimiter.checkCodeRequest(request.email(), servletRequest.getRemoteAddr());
        // Keep the delivery result (and any test-only in-memory code) inside the
        // service boundary. Verification codes must never cross the HTTP boundary.
        return new VerificationCodeVO(
            auth.requestCode(request.email(), request.purpose()).message()
        );
    }

    @PostMapping("/register")
    public ResponseEntity<AuthVO> register(
        @Valid @RequestBody RegisterDTO request,
        HttpServletRequest servletRequest
    ) {
        rateLimiter.checkCodeAttempt(request.email(), servletRequest.getRemoteAddr());
        return authenticated(auth.register(request.email(), request.username(), request.code(), request.password()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthVO> login(
        @Valid @RequestBody LoginDTO request,
        HttpServletRequest servletRequest
    ) {
        String identity = request.identifier();
        rateLimiter.checkLogin(identity, servletRequest.getRemoteAddr());
        return authenticated(auth.login(identity, request.password()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthVO> resetPassword(
        @Valid @RequestBody ResetPasswordDTO request,
        HttpServletRequest servletRequest
    ) {
        rateLimiter.checkCodeAttempt(request.email(), servletRequest.getRemoteAddr());
        return authenticated(auth.resetPassword(request.email(), request.code(), request.password()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie.from(security.cookieName(), "")
            .httpOnly(true)
            .secure(security.cookieSecure())
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/node-compat-token")
    public NodeCompatibilityTokenVO nodeCompatibilityToken(@AuthenticationPrincipal AuthenticatedUser user) {
        if (!nodeCompatibilityTokens.enabled()) {
            throw AuthErrorCode.NODE_COMPAT_DISABLED.toException();
        }
        var issued = nodeCompatibilityTokens.issue(user);
        return new NodeCompatibilityTokenVO(issued.token(), issued.ttl().toSeconds());
    }

    private ResponseEntity<AuthVO> authenticated(AuthSessionVO session) {
        ResponseCookie cookie = ResponseCookie.from(security.cookieName(), session.token())
            .httpOnly(true)
            .secure(security.cookieSecure())
            .sameSite("Strict")
            .path("/")
            .maxAge(session.tokenTtl())
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(new AuthVO(session.token(), session.user()));
    }

}
