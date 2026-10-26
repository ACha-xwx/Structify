package com.feng.dsagent.chat.service;

import com.feng.dsagent.chat.domain.ChatAttachmentFile;
import com.feng.dsagent.chat.vo.ChatSessionSummaryVO;
import com.feng.dsagent.chat.vo.ChatSessionVO;

import com.feng.dsagent.chat.error.ChatErrorCode;
import com.feng.dsagent.common.exception.BizException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {

    private final ChatPersistenceService repository;

    public ChatHistoryService(ChatPersistenceService repository) {
        this.repository = repository;
    }

    public List<ChatSessionSummaryVO> sessions(long userId) {
        return repository.findSessions(userId);
    }

    public ChatSessionVO session(long userId, String sessionId) {
        return repository.findSession(userId, normalizedId(sessionId))
            .orElseThrow(this::notFound);
    }

    public ChatSessionVO update(long userId, String sessionId, String title, Boolean pinned) {
        String normalized = normalizedId(sessionId);
        String nextTitle = title == null ? null : title.trim();
        if (title != null && (nextTitle.isBlank() || nextTitle.length() > 200)) {
            throw ChatErrorCode.SESSION_TITLE_INVALID.toException();
        }
        if (nextTitle == null && pinned == null) {
            throw ChatErrorCode.SESSION_UPDATE_EMPTY.toException();
        }
        if (!repository.updateSession(userId, normalized, nextTitle, pinned)) {
            throw notFound();
        }
        return repository.findSession(userId, normalized).orElseThrow(this::notFound);
    }

    public boolean delete(long userId, String sessionId) {
        if (!repository.deleteSession(userId, normalizedId(sessionId))) {
            throw notFound();
        }
        return true;
    }

    public ChatAttachmentFile attachment(long userId, String attachmentId) {
        return repository.attachmentFile(userId, normalizedAttachmentId(attachmentId))
            .orElseThrow(this::notFound);
    }

    private String normalizedId(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64) {
            throw notFound();
        }
        return sessionId.trim();
    }

    private BizException notFound() {
        return ChatErrorCode.SESSION_NOT_FOUND.toException();
    }

    private String normalizedAttachmentId(String id) {
        if (id == null || id.isBlank() || id.length() > 64 || !id.matches("[0-9a-fA-F-]+")) throw notFound();
        return id.trim();
    }
}
