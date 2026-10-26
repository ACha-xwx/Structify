package com.feng.dsagent.common.query;

import com.feng.dsagent.common.support.PageParams;

/**
 * 职责：带分页的查询对象统一提供预计算 offset，避免各 Query 复制 {@code page * size}。
 */
public interface PagedQuery {

    int page();

    int size();

    default int offset() {
        return PageParams.offset(page(), size());
    }
}
