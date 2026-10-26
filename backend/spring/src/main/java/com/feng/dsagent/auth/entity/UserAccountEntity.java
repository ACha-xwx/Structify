package com.feng.dsagent.auth.entity;

import java.util.Set;

public record UserAccountEntity(long id, String email, String username, String passwordHash, Set<String> roles) {

    public UserAccountEntity {
        roles = Set.copyOf(roles);
    }
}
