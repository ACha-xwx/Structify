package com.feng.dsagent.chat;

import java.util.List;
import java.util.Optional;

interface ChatHistoryRepository {

    List<ChatSessionSummary> findSessions(long userId);

    Optional<ChatSessionView> findSession(long userId, String sessionId);

    boolean updateSession(long userId, String sessionId, String title, Boolean pinned);

    boolean deleteSession(long userId, String sessionId);
}
