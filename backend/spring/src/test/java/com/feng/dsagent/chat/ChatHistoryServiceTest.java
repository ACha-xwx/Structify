package com.feng.dsagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.chat.service.ChatHistoryService;
import com.feng.dsagent.chat.service.ChatPersistenceService;
import com.feng.dsagent.chat.vo.ChatMessageVO;
import com.feng.dsagent.chat.vo.ChatSessionSummaryVO;
import com.feng.dsagent.chat.vo.ChatSessionVO;
import com.feng.dsagent.common.exception.BizException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ChatHistoryServiceTest {

    @Test
    void listsGetsAndDeletesOnlyTheAuthenticatedUsersSessions() {
        FakeHistoryRepository repository = new FakeHistoryRepository();
        ChatSessionVO owned = new ChatSessionVO(
            "session-1",
            "03-stack-queue",
            "解释栈的入栈操作",
            Instant.parse("2026-07-22T10:00:00Z"),
            List.of(new ChatMessageVO(
                1L,
                "user",
                "什么是入栈？",
                List.of(),
                Instant.parse("2026-07-22T09:59:00Z")
            ))
        );
        repository.add(7L, owned);
        repository.add(8L, new ChatSessionVO(
            "session-other", null, "别人的会话", Instant.parse("2026-07-22T11:00:00Z"), List.of()
        ));
        ChatHistoryService service = new ChatHistoryService(repository);

        assertThat(service.sessions(7L)).extracting(ChatSessionSummaryVO::id).containsExactly("session-1");
        assertThat(service.session(7L, "session-1").messages()).singleElement()
            .extracting(ChatMessageVO::content).isEqualTo("什么是入栈？");
        assertThat(service.delete(7L, "session-1")).isTrue();
        assertThat(service.sessions(7L)).isEmpty();
        assertThat(repository.sessionsFor(8L)).containsKey("session-other");
    }

    @Test
    void treatsAnotherUsersSessionAsNotFound() {
        FakeHistoryRepository repository = new FakeHistoryRepository();
        repository.add(8L, new ChatSessionVO(
            "session-other", null, "别人的会话", Instant.parse("2026-07-22T11:00:00Z"), List.of()
        ));
        ChatHistoryService service = new ChatHistoryService(repository);

        assertThatThrownBy(() -> service.session(7L, "session-other"))
            .isInstanceOfSatisfying(BizException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(error.code()).isEqualTo("CHAT_SESSION_NOT_FOUND");
            });
        assertThatThrownBy(() -> service.delete(7L, "session-other"))
            .isInstanceOfSatisfying(BizException.class, error ->
                assertThat(error.code()).isEqualTo("CHAT_SESSION_NOT_FOUND")
            );
    }

    private static final class FakeHistoryRepository extends ChatPersistenceService {
        private final Map<Long, Map<String, ChatSessionVO>> sessions = new LinkedHashMap<>();

        void add(long userId, ChatSessionVO session) {
            sessionsFor(userId).put(session.id(), session);
        }

        Map<String, ChatSessionVO> sessionsFor(long userId) {
            return sessions.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
        }

        @Override
        public List<ChatSessionSummaryVO> findSessions(long userId) {
            return sessionsFor(userId).values().stream()
                .map(view -> new ChatSessionSummaryVO(
                    view.id(), view.chapterId(), view.title(), view.updatedAt(), view.messages().size()
                ))
                .toList();
        }

        @Override
        public Optional<ChatSessionVO> findSession(long userId, String sessionId) {
            return Optional.ofNullable(sessionsFor(userId).get(sessionId));
        }

        @Override
        public boolean updateSession(long userId, String sessionId, String title, Boolean pinned) {
            ChatSessionVO current = sessionsFor(userId).get(sessionId);
            if (current == null) return false;
            sessionsFor(userId).put(sessionId, new ChatSessionVO(
                current.id(), current.chapterId(), title == null ? current.title() : title,
                current.updatedAt(), current.messages(), pinned == null ? current.pinned() : pinned
            ));
            return true;
        }

        @Override
        public boolean deleteSession(long userId, String sessionId) {
            return sessionsFor(userId).remove(sessionId) != null;
        }
    }
}
