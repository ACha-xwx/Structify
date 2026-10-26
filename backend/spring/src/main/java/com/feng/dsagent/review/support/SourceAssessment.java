package com.feng.dsagent.review.support;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import com.feng.dsagent.review.vo.ReviewSourceVO;

public record SourceAssessment(List<ReviewSourceVO> chain, boolean complete) {
}
