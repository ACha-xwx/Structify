package com.feng.dsagent.auth;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
final class DevTestAccountRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevTestAccountRunner.class);

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String username;
    private final String password;

    DevTestAccountRunner(
        UserRepository users,
        PasswordEncoder passwords,
        @Value("${app.dev-test-account.email}") String email,
        @Value("${app.dev-test-account.username}") String username,
        @Value("${app.dev-test-account.password}") String password
    ) {
        this.users = users;
        this.passwords = passwords;
        this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.findByEmail(email).isPresent()) {
            log.info("Development test account already exists: {}", email);
            return;
        }
        users.create(email, username, passwords.encode(password), Set.of("STUDENT"));
        log.info("Provisioned development test student account: {}", email);
    }
}
