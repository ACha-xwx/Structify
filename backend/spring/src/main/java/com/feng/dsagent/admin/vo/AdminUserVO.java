package com.feng.dsagent.admin.vo;

import java.time.Instant;
import java.util.List;

public record AdminUserVO(
    long id,
    String email,
    String username,
    String status,
    String disabledReason,
    Instant disabledAt,
    List<String> roles,
    Instant createdAt,
    Instant updatedAt
) {
}
