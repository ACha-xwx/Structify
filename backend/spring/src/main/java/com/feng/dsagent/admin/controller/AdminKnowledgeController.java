package com.feng.dsagent.admin.controller;

import com.feng.dsagent.common.vo.PageVO;

import com.feng.dsagent.admin.service.AdminKnowledgeService;

import com.feng.dsagent.common.RequestIdFilter;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.feng.dsagent.admin.dto.SettingsDTO;
import com.feng.dsagent.admin.dto.TestSearchDTO;
import com.feng.dsagent.admin.dto.CreateChunkDTO;
import com.feng.dsagent.admin.dto.PatchChunkDTO;
import com.feng.dsagent.admin.vo.KnowledgeSettingsVO;

/**
 * Knowledge-base maintenance: the retrieval tuning plus chunk CRUD. The console is where an administrator
 * answers "what can the AI quote right now, and how loosely does it match"; every change is audited and every
 * write rebuilds the retrieval snapshot before the response returns.
 */
@RestController
@RequestMapping("/api/v1/admin/knowledge")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeController {

    private final AdminKnowledgeService knowledge;

    public AdminKnowledgeController(AdminKnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping("/overview")
    public AdminKnowledgeService.KnowledgeOverview overview() {
        return knowledge.overview();
    }

    @GetMapping("/settings")
    public KnowledgeSettingsVO settings() {
        return knowledge.settingsView();
    }

    @PutMapping("/settings")
    public KnowledgeSettingsVO saveSettings(
        @RequestBody SettingsDTO request,
        @AuthenticationPrincipal AuthenticatedUser actor,
        HttpServletRequest servletRequest
    ) {
        return knowledge.saveSettings(
            request.minimumScore(),
            request.searchLimit(),
            request.titleWeightLong(),
            request.titleWeightShort(),
            actor.userId(),
            requestId(servletRequest)
        );
    }

    @DeleteMapping("/settings")
    public KnowledgeSettingsVO resetSettings(
        @AuthenticationPrincipal AuthenticatedUser actor,
        HttpServletRequest servletRequest
    ) {
        return knowledge.resetSettings(actor.userId(), requestId(servletRequest));
    }

    @PostMapping("/test-search")
    public AdminKnowledgeService.TestSearchResult testSearch(@RequestBody TestSearchDTO request) {
        return knowledge.testSearch(
            request.query(),
            request.chapterId(),
            request.audience(),
            request.minimumScore(),
            request.searchLimit(),
            request.titleWeightLong(),
            request.titleWeightShort()
        );
    }

    @GetMapping("/chunks")
    public PageVO<AdminKnowledgeService.ChunkRow> chunks(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String chapterId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String sourcePrefix,
        @RequestParam(required = false) String q
    ) {
        return knowledge.chunks(page, size, chapterId, status, sourcePrefix, q);
    }

    @GetMapping("/chunks/{chunkId}")
    public AdminKnowledgeService.ChunkDetail chunk(@PathVariable String chunkId) {
        return knowledge.chunk(chunkId);
    }

    @PostMapping("/chunks")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminKnowledgeService.ChunkDetail createChunk(
        @RequestBody CreateChunkDTO request,
        @AuthenticationPrincipal AuthenticatedUser actor,
        HttpServletRequest servletRequest
    ) {
        return knowledge.createChunk(
            request.chapterId(),
            request.title(),
            request.content(),
            request.pageLabel(),
            request.reviewStatus(),
            request.licenseScope(),
            actor.userId(),
            requestId(servletRequest)
        );
    }

    @PatchMapping("/chunks/{chunkId}")
    public AdminKnowledgeService.ChunkDetail updateChunk(
        @PathVariable String chunkId,
        @RequestBody PatchChunkDTO request,
        @AuthenticationPrincipal AuthenticatedUser actor,
        HttpServletRequest servletRequest
    ) {
        return knowledge.updateChunk(
            chunkId,
            request.chapterId(),
            request.title(),
            request.content(),
            request.pageLabel(),
            request.reviewStatus(),
            request.licenseScope(),
            actor.userId(),
            requestId(servletRequest)
        );
    }

    @DeleteMapping("/chunks/{chunkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChunk(
        @PathVariable String chunkId,
        @AuthenticationPrincipal AuthenticatedUser actor,
        HttpServletRequest servletRequest
    ) {
        knowledge.deleteChunk(chunkId, actor.userId(), requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
