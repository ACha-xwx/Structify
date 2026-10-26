package com.feng.dsagent.auth;

import com.feng.dsagent.auth.converter.UserAccountConverterImpl;
import com.feng.dsagent.auth.service.AuthService;
import com.feng.dsagent.auth.service.UserAccountService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.security.service.JwtTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import com.feng.dsagent.auth.domain.VerificationCodeDelivery;
import com.feng.dsagent.auth.entity.UserAccountEntity;
import com.feng.dsagent.auth.mapper.UserMapper;
import com.feng.dsagent.auth.support.RolePolicy;
import com.feng.dsagent.auth.support.UsernamePolicy;
import com.feng.dsagent.auth.support.VerificationCodeManager;
import com.feng.dsagent.auth.vo.AuthSessionVO;
class AuthServiceTest {

    private final FakeUserAccountService users = new FakeUserAccountService();
    private final FakeVerificationCodeManager codes = new FakeVerificationCodeManager();
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final JwtTokenService tokens = new JwtTokenService(
        "test-secret-with-at-least-thirty-two-characters",
        Duration.ofHours(2),
        "ds-agent-test",
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-07-20T04:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void registersVerifiedUserAsStudent() {
        AuthService service = service(new RolePolicy("", ""));
        codes.allow("student@example.com", "register", "123456");

        AuthSessionVO session = service.register("student@example.com", "123456", "correct-horse");

        assertThat(session.user().roles()).containsExactly("STUDENT");
        assertThat(passwords.matches("correct-horse", users.findByEmail("student@example.com").orElseThrow().passwordHash()))
            .isTrue();
        assertThat(tokens.verify(session.token()).email()).isEqualTo("student@example.com");
    }

    @Test
    void assignsConfiguredTeacherAndAdminRoles() {
        AuthService service = service(new RolePolicy("admin@example.com", "teacher@example.com,admin@example.com"));
        codes.allow("admin@example.com", "register", "654321");

        AuthSessionVO session = service.register("ADMIN@example.com", "654321", "correct-horse");

        assertThat(session.user().roles()).containsExactlyInAnyOrder("STUDENT", "TEACHER", "ADMIN");
    }

    @Test
    void rejectsUnknownEmailAndWrongPasswordWithSameError() {
        AuthService service = service(new RolePolicy("", ""));
        codes.allow("student@example.com", "register", "123456");
        service.register("student@example.com", "123456", "correct-horse");

        assertInvalidCredentials(() -> service.login("student@example.com", "wrong-password"));
        assertInvalidCredentials(() -> service.login("missing@example.com", "wrong-password"));
    }

    @Test
    void resetsPasswordAfterConsumingAResetCode() {
        AuthService service = service(new RolePolicy("", ""));
        codes.allow("student@example.com", "register", "123456");
        service.register("student@example.com", "123456", "old-password");
        codes.allow("student@example.com", "reset", "654321");

        AuthSessionVO session = service.resetPassword("student@example.com", "654321", "new-password");

        assertThat(session.user().email()).isEqualTo("student@example.com");
        assertInvalidCredentials(() -> service.login("student@example.com", "old-password"));
        assertThat(service.login("student@example.com", "new-password").user().email())
            .isEqualTo("student@example.com");
    }

    @Test
    void logsInWithAUsernameWhileKeepingEmailLoginCompatible() {
        AuthService service = service(new RolePolicy("", ""));
        codes.allow("username@example.com", "register", "123456");

        AuthSessionVO registration = service.register("username@example.com", "ACha_", "123456", "correct-horse");

        assertThat(registration.user().username()).isEqualTo("ACha_");
        assertThat(service.login("acha_", "correct-horse").user().email()).isEqualTo("username@example.com");
        assertThat(service.login("username@example.com", "correct-horse").user().username()).isEqualTo("ACha_");
    }

    @Test
    void rejectsUsernameConflictsCaseInsensitively() {
        AuthService service = service(new RolePolicy("", ""));
        codes.allow("first@example.com", "register", "123456");
        service.register("first@example.com", "ACha_", "123456", "correct-horse");
        codes.allow("second@example.com", "register", "654321");

        assertThatThrownBy(() -> service.register("second@example.com", "acha_", "654321", "correct-horse"))
            .isInstanceOfSatisfying(BizException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(error.code()).isEqualTo("AUTH_USERNAME_REGISTERED");
            });
    }

    private void assertInvalidCredentials(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BizException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(error.code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
                assertThat(error.getMessage()).isEqualTo("邮箱或密码错误");
            });
    }

    private AuthService service(RolePolicy roles) {
        return new AuthService(users, codes, passwords, tokens, roles, new UserAccountConverterImpl());
    }

    private static final class FakeVerificationCodeManager implements VerificationCodeManager {
        private final Map<String, String> allowed = new LinkedHashMap<>();

        void allow(String email, String purpose, String code) {
            allowed.put(email.toLowerCase() + ":" + purpose, code);
        }

        @Override
        public VerificationCodeDelivery request(String email, String purpose) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void consume(String email, String purpose, String code) {
            String expected = allowed.remove(email.toLowerCase() + ":" + purpose);
            if (!code.equals(expected)) {
                throw new BizException(HttpStatus.UNAUTHORIZED, "AUTH_CODE_INVALID", "验证码无效或已过期");
            }
        }
    }

    private static final class FakeUserAccountService extends UserAccountService {
        private final Map<String, UserAccountEntity> byEmail = new LinkedHashMap<>();
        private final Map<String, UserAccountEntity> byUsername = new LinkedHashMap<>();
        private long nextId = 1;

        private FakeUserAccountService() {
            super(mock(UserMapper.class));
        }

        @Override
        public Optional<UserAccountEntity> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email.toLowerCase()));
        }

        @Override
        public Optional<UserAccountEntity> findByUsername(String normalizedUsername) {
            return Optional.ofNullable(byUsername.get(normalizedUsername));
        }

        @Override
        public Optional<UserAccountEntity> findById(long id) {
            return byEmail.values().stream().filter(user -> user.id() == id).findFirst();
        }

        @Override
        public UserAccountEntity create(String email, String username, String passwordHash, Set<String> roles) {
            UserAccountEntity account = new UserAccountEntity(nextId++, email.toLowerCase(), username, passwordHash, roles);
            byEmail.put(account.email(), account);
            if (username != null) {
                byUsername.put(UsernamePolicy.lookupKey(username), account);
            }
            return account;
        }

        @Override
        public void updatePassword(long userId, String passwordHash) {
            UserAccountEntity current = byEmail.values().stream()
                .filter(user -> user.id() == userId)
                .findFirst()
                .orElseThrow();
            byEmail.put(current.email(), new UserAccountEntity(
                current.id(), current.email(), current.username(), passwordHash, current.roles()
            ));
            if (current.username() != null) {
                byUsername.put(UsernamePolicy.lookupKey(current.username()), byEmail.get(current.email()));
            }
        }
    }
}
