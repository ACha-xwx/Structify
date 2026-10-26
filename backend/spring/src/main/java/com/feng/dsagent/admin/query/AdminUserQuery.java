package com.feng.dsagent.admin.query;

import com.feng.dsagent.admin.domain.UserStatus;
import com.feng.dsagent.common.query.PagedQuery;

public record AdminUserQuery(int page, int size, String search, UserStatus status, String role)
    implements PagedQuery {
}
