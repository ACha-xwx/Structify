package com.feng.dsagent.auth;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class DevTestAccountRunnerTest {

    @Test
    void provisionsTheConfiguredStudentOnlyWhenMissing() throws Exception {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(users.findByEmail("student@structify.test")).thenReturn(Optional.empty());
        when(passwords.encode("StructifyTest123!")).thenReturn("encoded-password");
        DevTestAccountRunner runner = new DevTestAccountRunner(
            users, passwords, "student@structify.test", "StructifyTest", "StructifyTest123!"
        );

        runner.run(new DefaultApplicationArguments());

        verify(users).create("student@structify.test", "StructifyTest", "encoded-password", Set.of("STUDENT"));
    }

    @Test
    void doesNotResetAnExistingAccountPassword() throws Exception {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(users.findByEmail("student@structify.test"))
            .thenReturn(Optional.of(new UserAccount(1, "student@structify.test", "StructifyTest", "existing-hash", Set.of("STUDENT"))));
        DevTestAccountRunner runner = new DevTestAccountRunner(
            users, passwords, "student@structify.test", "StructifyTest", "StructifyTest123!"
        );

        runner.run(new DefaultApplicationArguments());

        verify(users, never()).create(anyString(), anyString(), anyString(), anySet());
        verify(passwords, never()).encode("StructifyTest123!");
    }
}
