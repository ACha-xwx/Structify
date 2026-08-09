package com.feng.dsagent.security;

import com.feng.dsagent.auth.RolePolicy;
import com.feng.dsagent.auth.UserAccount;
import com.feng.dsagent.auth.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NodeCompatibilityUserResolver {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RolePolicy roles;

    public NodeCompatibilityUserResolver(UserRepository users, PasswordEncoder passwords, RolePolicy roles) {
        this.users = users;
        this.passwords = passwords;
        this.roles = roles;
    }

    @Transactional
    public AuthenticatedUser resolve(NodeCompatibilityToken token) {
        String email = token.email().trim().toLowerCase(Locale.ROOT);
        UserAccount account = users.findByEmail(email).orElseGet(() -> createBridgeAccount(email));
        return new AuthenticatedUser(account.id(), account.email(), account.roles());
    }

    private UserAccount createBridgeAccount(String email) {
        try {
            // Node remains the password authority for this compatibility path.
            // The random hash makes the mirrored Spring account non-loginable
            // until an explicit, audited identity migration is introduced.
            return users.create(email, passwords.encode(UUID.randomUUID().toString()), roles.rolesFor(email));
        } catch (DuplicateKeyException race) {
            return users.findByEmail(email)
                .orElseThrow(() -> new InvalidTokenException("Node compatibility identity is unavailable", race));
        }
    }
}
