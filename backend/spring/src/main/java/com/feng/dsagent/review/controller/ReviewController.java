package com.feng.dsagent.review.controller;

import com.feng.dsagent.review.vo.ReviewHistoryVO;
import com.feng.dsagent.review.vo.ReviewDetailVO;
import com.feng.dsagent.review.vo.ReviewItemVO;
import com.feng.dsagent.review.service.ReviewService;
import com.feng.dsagent.common.RequestIdFilter;
import com.feng.dsagent.common.vo.PageVO;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.feng.dsagent.review.dto.ReviewStatusDTO;

/**
 * 职责：管理端内容审核列表与状态变更的 HTTP 入口。
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public PageVO<ReviewItemVO> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String search
    ) {
        return reviews.list(page, size, type, status, search);
    }

    @GetMapping("/{type}/{id}")
    public ReviewDetailVO detail(@PathVariable String type, @PathVariable String id) {
        return reviews.detail(type, id);
    }

    @PatchMapping("/{type}/{id}/status")
    public ReviewItemVO updateStatus(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @PathVariable String type,
        @PathVariable String id,
        @Valid @RequestBody ReviewStatusDTO request,
        HttpServletRequest servletRequest
    ) {
        return reviews.updateStatus(actor, type, id, request.status(), request.note(), requestId(servletRequest));
    }

    @GetMapping("/{type}/{id}/history")
    public java.util.List<ReviewHistoryVO> history(@PathVariable String type, @PathVariable String id) {
        return reviews.history(type, id);
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }

}
