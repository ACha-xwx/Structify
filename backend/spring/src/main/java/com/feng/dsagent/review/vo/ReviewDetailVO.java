package com.feng.dsagent.review.vo;

import java.time.Instant;
import java.util.List;

public record ReviewDetailVO(ReviewItemVO item, List<ReviewSourceVO> sourceChain) {
}
