package com.feng.dsagent.security.support;

import com.feng.dsagent.auth.entity.UserAccountEntity;
import com.feng.dsagent.auth.service.UserAccountService;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.feng.dsagent.security.exception.InvalidTokenException;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import com.feng.dsagent.security.domain.NodeCompatibilityToken;
@Component
public class NodeCompatibilityUserResolver {

    private final UserAccountService users;
    private final PasswordEncoder passwords;

    public NodeCompatibilityUserResolver(UserAccountService users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public AuthenticatedUser resolve(NodeCompatibilityToken token) {
        String email = token.email().trim().toLowerCase(Locale.ROOT);
        UserAccountEntity account = users.findByEmail(email).orElseGet(() -> createBridgeAccount(email));
        return new AuthenticatedUser(account.id(), account.email(), account.roles());
    }

    private UserAccountEntity createBridgeAccount(String email) {
        try {
            // Node remains the password authority for this compatibility path.
            // The random hash makes the mirrored Spring account non-loginable
            // until an explicit, audited identity migration is introduced.
            // Environment bootstrap roles are never granted by implicit mirroring.
            return users.create(email, passwords.encode(UUID.randomUUID().toString()), Set.of("STUDENT"));
        } catch (DuplicateKeyException race) {
            return users.findByEmail(email)
                .orElseThrow(() -> new InvalidTokenException("Node compatibility identity is unavailable", race));
        }
    }
}
