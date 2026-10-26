package com.feng.dsagent.admin.query;

import com.feng.dsagent.common.query.PagedQuery;

public record BackgroundTaskQuery(int page, int size, String status, String taskType) implements PagedQuery {
}
