package com.feng.dsagent.auth.support;

import com.feng.dsagent.auth.error.AuthErrorCode;
import com.feng.dsagent.common.exception.BizException;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.feng.dsagent.auth.entity.UserAccountEntity;
import com.feng.dsagent.auth.service.UserAccountService;
/**
 * Internal-only provisioning seam for a one-time server administrator bootstrap.
 * It intentionally has no controller endpoint.
 */
@Service
public class AdminAccountProvisioner {

    private static final Set<String> ADMIN_ROLES = Set.of("STUDENT", "TEACHER", "ADMIN");

    private final UserAccountService users;
    private final PasswordEncoder passwords;

    public AdminAccountProvisioner(UserAccountService users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public UserAccountEntity createAdministrator(String email, String username, String password) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedUsername = UsernamePolicy.normalizeRequired(username);
        validatePassword(password);

        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw emailConflict();
        }
        if (users.findByUsername(UsernamePolicy.lookupKey(normalizedUsername)).isPresent()) {
            throw usernameConflict();
        }
        try {
            return users.create(normalizedEmail, normalizedUsername, passwords.encode(password), ADMIN_ROLES);
        } catch (DuplicateKeyException error) {
            if (users.findByUsername(UsernamePolicy.lookupKey(normalizedUsername)).isPresent()) {
                throw usernameConflict();
            }
            throw emailConflict();
        }
    }

    /**
     * Provision a bootstrap administrator, optionally repairing exactly one
     * already-existing account. Callers must gate reconciliation explicitly.
     */
    @Transactional
    public UserAccountEntity provisionAdministrator(
        String email,
        String username,
        String password,
        boolean reconcileExisting
    ) {
        if (!reconcileExisting) {
            return createAdministrator(email, username, password);
        }

        String normalizedEmail = normalizeEmail(email);
        String normalizedUsername = UsernamePolicy.normalizeRequired(username);
        validatePassword(password);
        String usernameKey = UsernamePolicy.lookupKey(normalizedUsername);

        UserAccountEntity emailMatch = users.findAnyByEmail(normalizedEmail).orElse(null);
        UserAccountEntity usernameMatch = users.findAnyByUsername(usernameKey).orElse(null);
        if (emailMatch == null && usernameMatch == null) {
            try {
                return users.create(normalizedEmail, normalizedUsername, passwords.encode(password), ADMIN_ROLES);
            } catch (DuplicateKeyException error) {
                throw provisionConflict();
            }
        }
        if (emailMatch != null && usernameMatch != null && emailMatch.id() != usernameMatch.id()) {
            throw targetMismatch();
        }

        long targetUserId = emailMatch != null ? emailMatch.id() : usernameMatch.id();
        try {
            users.reconcileAdministrator(
                targetUserId,
                normalizedEmail,
                normalizedUsername,
                passwords.encode(password),
                ADMIN_ROLES
            );
        } catch (DuplicateKeyException error) {
            throw provisionConflict();
        }
        return users.findById(targetUserId)
            .orElseThrow(() -> new IllegalStateException("Administrator account is unavailable after reconciliation"));
    }

    public static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw AuthErrorCode.AUTH_EMAIL_INVALID.toException();
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 128) {
            throw AuthErrorCode.AUTH_PASSWORD_INVALID.toException();
        }
    }

    private BizException targetMismatch() {
        return AuthErrorCode.AUTH_ADMIN_PROVISION_TARGET_MISMATCH.toException();
    }

    private BizException provisionConflict() {
        return AuthErrorCode.AUTH_ADMIN_PROVISION_CONFLICT.toException();
    }

    private BizException emailConflict() {
        return AuthErrorCode.AUTH_EMAIL_REGISTERED.toException();
    }

    private BizException usernameConflict() {
        return AuthErrorCode.AUTH_USERNAME_REGISTERED.toException();
    }
}
