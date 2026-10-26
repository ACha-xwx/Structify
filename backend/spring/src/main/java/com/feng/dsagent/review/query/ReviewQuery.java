package com.feng.dsagent.review.query;

import com.feng.dsagent.common.query.PagedQuery;
import com.feng.dsagent.review.domain.ReviewType;
import java.util.List;

public record ReviewQuery(int page, int size, List<ReviewType> types, String status, String search)
    implements PagedQuery {
}
