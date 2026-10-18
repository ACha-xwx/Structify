package com.feng.dsagent.chat;

import com.feng.dsagent.common.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {

    private final ChatHistoryRepository repository;

    ChatHistoryService(ChatHistoryRepository repository) {
        this.repository = repository;
    }

    public List<ChatSessionSummary> sessions(long userId) {
        return repository.findSessions(userId);
    }

    public ChatSessionView session(long userId, String sessionId) {
        return repository.findSession(userId, normalizedId(sessionId))
            .orElseThrow(this::notFound);
    }

    public ChatSessionView update(long userId, String sessionId, String title, Boolean pinned) {
        String normalized = normalizedId(sessionId);
        String nextTitle = title == null ? null : title.trim();
        if (title != null && (nextTitle.isBlank() || nextTitle.length() > 200)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHAT_SESSION_TITLE_INVALID", "对话名称不能为空且不能超过 200 个字符");
        }
        if (nextTitle == null && pinned == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHAT_SESSION_UPDATE_EMPTY", "至少需要修改名称或置顶状态");
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
        if (repository instanceof ChatRepository chat) {
            return chat.attachmentFile(userId, normalizedAttachmentId(attachmentId))
                .orElseThrow(this::notFound);
        }
        throw notFound();
    }

    private String normalizedId(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64) {
            throw notFound();
        }
        return sessionId.trim();
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "对话会话不存在");
    }

    private String normalizedAttachmentId(String id) {
        if (id == null || id.isBlank() || id.length() > 64 || !id.matches("[0-9a-fA-F-]+")) throw notFound();
        return id.trim();
    }
}
