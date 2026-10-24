package com.feng.dsagent.aiquota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.feng.dsagent.auth.UserAccount;
import com.feng.dsagent.auth.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DevTestAccountQuotaPolicyTest {
    @Test
    void onlyConfiguredTestAccountInDevelopmentIsUnlimited() {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(7)).thenReturn(Optional.of(new UserAccount(
            7, "student@structify.test", "Student", "hash", Set.of("STUDENT"))));
        when(users.findById(8)).thenReturn(Optional.of(new UserAccount(
            8, "other@example.com", "Other", "hash", Set.of("STUDENT"))));
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        assertThat(new DevTestAccountQuotaPolicy(users, env, true, "student@structify.test").isUnlimited(7)).isTrue();
        assertThat(new DevTestAccountQuotaPolicy(users, env, true, "student@structify.test").isUnlimited(8)).isFalse();
        assertThat(new DevTestAccountQuotaPolicy(users, env, false, "student@structify.test").isUnlimited(7)).isFalse();
        env.setActiveProfiles("prod");
        assertThat(new DevTestAccountQuotaPolicy(users, env, true, "student@structify.test").isUnlimited(7)).isFalse();
    }
}
