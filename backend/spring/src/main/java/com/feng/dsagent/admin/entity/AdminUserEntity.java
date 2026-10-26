package com.feng.dsagent.admin.entity;

import com.feng.dsagent.admin.domain.UserStatus;
import java.time.Instant;
import java.util.Set;

public record AdminUserEntity(
    long id,
    String email,
    String username,
    UserStatus status,
    String disabledReason,
    Instant disabledAt,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt
) {
    public AdminUserEntity {
        roles = Set.copyOf(roles);
    }
}
