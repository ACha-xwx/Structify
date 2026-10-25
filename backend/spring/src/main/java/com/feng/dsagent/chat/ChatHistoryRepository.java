package com.feng.dsagent.chat;

import java.util.List;
import java.util.Optional;

public interface ChatHistoryRepository {

    public List<ChatSessionSummary> findSessions(long userId);

    public Optional<ChatSessionView> findSession(long userId, String sessionId);

    public boolean updateSession(long userId, String sessionId, String title, Boolean pinned);

    public boolean deleteSession(long userId, String sessionId);
}
