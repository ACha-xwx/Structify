package com.feng.dsagent.auth.service;

import com.feng.dsagent.auth.converter.UserAccountConverter;
import com.feng.dsagent.auth.domain.VerificationCodeDelivery;
import com.feng.dsagent.auth.support.VerificationCodeManager;
import com.feng.dsagent.auth.support.UsernamePolicy;
import com.feng.dsagent.auth.vo.AuthSessionVO;
import com.feng.dsagent.auth.entity.UserAccountEntity;
import com.feng.dsagent.auth.support.RolePolicy;
import com.feng.dsagent.auth.vo.UserVO;

import com.feng.dsagent.auth.error.AuthErrorCode;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.security.service.JwtTokenService;
import com.feng.dsagent.mail.service.MailConfigService;
import java.time.Duration;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 职责：编排注册登录、验证码校验与 JWT 会话签发。
 */
@Service
public class AuthService {

    private final UserAccountService users;
    private final VerificationCodeManager codes;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;
    private final RolePolicy roles;
    private final MailConfigService mailConfig;
    private final UserAccountConverter userAccountConverter;
    private final String dummyHash;

    @Autowired
    public AuthService(
        UserAccountService users,
        VerificationCodeManager codes,
        PasswordEncoder passwords,
        JwtTokenService tokens,
        RolePolicy roles,
        MailConfigService mailConfig,
        UserAccountConverter userAccountConverter
    ) {
        this.users = users;
        this.codes = codes;
        this.passwords = passwords;
        this.tokens = tokens;
        this.roles = roles;
        this.mailConfig = mailConfig;
        this.userAccountConverter = userAccountConverter;
        this.dummyHash = passwords.encode("not-a-real-password");
    }

    public AuthService(
        UserAccountService users,
        VerificationCodeManager codes,
        PasswordEncoder passwords,
        JwtTokenService tokens,
        RolePolicy roles,
        UserAccountConverter userAccountConverter
    ) {
        this(users, codes, passwords, tokens, roles, null, userAccountConverter);
    }

    public VerificationCodeDelivery requestCode(String email, String purpose) {
        return codes.request(email, purpose);
    }

    @Transactional
    public AuthSessionVO register(String email, String code, String password) {
        String normalizedEmail = normalizeEmail(email);
        codes.consume(normalizedEmail, "register", code);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw AuthErrorCode.AUTH_EMAIL_REGISTERED.toException();
        }
        try {
            UserAccountEntity user = users.create(normalizedEmail, passwords.encode(password), roles.rolesFor(normalizedEmail));
            return session(user);
        } catch (DuplicateKeyException error) {
            throw AuthErrorCode.AUTH_EMAIL_REGISTERED.toException();
        }
    }

    @Transactional
    public AuthSessionVO register(String email, String username, String code, String password) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedUsername = UsernamePolicy.normalizeOptional(username);
        codes.consume(normalizedEmail, "register", code);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw AuthErrorCode.AUTH_EMAIL_REGISTERED.toException();
        }
        if (normalizedUsername != null && users.findByUsername(UsernamePolicy.lookupKey(normalizedUsername)).isPresent()) {
            throw AuthErrorCode.AUTH_USERNAME_REGISTERED.toException();
        }
        try {
            UserAccountEntity user = users.create(
                normalizedEmail,
                normalizedUsername,
                passwords.encode(password),
                roles.rolesFor(normalizedEmail)
            );
            return session(user);
        } catch (DuplicateKeyException error) {
            if (normalizedUsername != null && users.findByUsername(UsernamePolicy.lookupKey(normalizedUsername)).isPresent()) {
                throw AuthErrorCode.AUTH_USERNAME_REGISTERED.toException();
            }
            throw AuthErrorCode.AUTH_EMAIL_REGISTERED.toException();
        }
    }

    public AuthSessionVO login(String emailOrUsername, String password) {
        UserAccountEntity user = findLoginUser(emailOrUsername);
        String storedHash = user == null ? dummyHash : user.passwordHash();
        boolean matches = passwords.matches(password == null ? "" : password, storedHash);
        if (user == null || !matches) {
            throw AuthErrorCode.AUTH_INVALID_CREDENTIALS.toException();
        }
        return session(user);
    }

    @Transactional
    public AuthSessionVO resetPassword(String email, String code, String password) {
        String normalizedEmail = normalizeEmail(email);
        codes.consume(normalizedEmail, "reset", code);
        UserAccountEntity user = users.findByEmail(normalizedEmail)
            .orElseThrow(AuthErrorCode.AUTH_RESET_INVALID::toException);
        users.updatePassword(user.id(), passwords.encode(password));
        return session(user);
    }

    public UserVO user(long userId) {
        return users.findById(userId)
            .map(userAccountConverter::toView)
            .orElseThrow(AuthErrorCode.AUTH_SESSION_INVALID::toException);
    }

    private AuthSessionVO session(UserAccountEntity user) {
        Duration ttl = mailConfig == null ? tokens.defaultTtl() : mailConfig.policy().sessionTtl();
        return new AuthSessionVO(
            tokens.issue(user.id(), user.email(), user.roles(), ttl),
            userAccountConverter.toView(user),
            ttl
        );
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw AuthErrorCode.AUTH_EMAIL_INVALID.toException();
        }
        return normalized;
    }

    private UserAccountEntity findLoginUser(String emailOrUsername) {
        String identity = emailOrUsername == null ? "" : emailOrUsername.trim();
        if (identity.contains("@")) {
            return users.findByEmail(normalizeEmail(identity)).orElse(null);
        }
        try {
            return users.findByUsername(UsernamePolicy.lookupKey(identity)).orElse(null);
        } catch (BizException invalidUsername) {
            if (!"AUTH_USERNAME_INVALID".equals(invalidUsername.code())) {
                throw invalidUsername;
            }
            return null;
        }
    }
}
