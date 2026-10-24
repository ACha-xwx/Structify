package com.feng.dsagent.aiquota;

import com.feng.dsagent.auth.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Only the explicitly configured development account may bypass the daily token cap. */
@Component
public final class DevTestAccountQuotaPolicy {
    private final UserRepository users;
    private final boolean enabled;
    private final String email;

    public DevTestAccountQuotaPolicy(
        UserRepository users,
        Environment environment,
        @Value("${app.dev-test-account.unlimited-ai-quota:false}") boolean enabled,
        @Value("${app.dev-test-account.email:}") String email
    ) {
        this.users = users;
        this.enabled = enabled && environment.acceptsProfiles(Profiles.of("dev"));
        this.email = email.trim();
    }

    public boolean isUnlimited(long userId) {
        return enabled && !email.isEmpty() && users.findById(userId)
            .map(user -> email.equalsIgnoreCase(user.email())).orElse(false);
    }
}
