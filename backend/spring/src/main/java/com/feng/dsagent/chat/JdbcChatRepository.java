package com.feng.dsagent.chat;

import com.feng.dsagent.common.ApiException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
class JdbcChatRepository implements ChatRepository, ChatHistoryRepository {

    private static final int MAX_SESSION_RESULTS = 50;
    private static final int MAX_SESSION_MESSAGES = 200;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ChatAttachmentStorage attachmentStorage;

    JdbcChatRepository(JdbcTemplate jdbc, ObjectMapper objectMapper, ChatAttachmentStorage attachmentStorage) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.attachmentStorage = attachmentStorage;
    }

    @Override
    public boolean isPublishedChapter(String chapterId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chapters WHERE id = ? AND status = 'PUBLISHED'",
            Integer.class,
            chapterId
        );
        return count != null && count > 0;
    }

    @Override
    public Optional<List<ChatTurn>> recentHistory(long userId, String sessionId, int limit) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chat_sessions WHERE id = ? AND user_id = ?",
            Integer.class,
            sessionId,
            userId
        );
        if (count == null || count == 0) {
            return Optional.empty();
        }
        List<ChatTurn> newestFirst = jdbc.query(
            "SELECT id, role, content, attachments_json FROM chat_messages WHERE session_id = ? ORDER BY id DESC LIMIT ?",
            (row, index) -> new ChatTurn(row.getString("role"), row.getString("content"),
                attachmentsForMessage(row.getLong("id"), userId, row.getString("attachments_json"))),
            sessionId,
            Math.max(1, limit)
        );
        List<ChatTurn> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return Optional.of(List.copyOf(chronological));
    }

    @Override
    @Transactional
    public String saveExchange(
        long userId,
        String sessionId,
        String chapterId,
        String prompt,
        String answer,
        List<ChatSource> sources
    ) {
        return saveExchange(userId, sessionId, chapterId, prompt, answer, sources, List.of());
    }

    @Override
    @Transactional
    public String saveExchange(long userId, String sessionId, String chapterId, String prompt,
                               String answer, List<ChatSource> sources, List<ChatAttachment> attachments) {
        return saveExchange(userId, new ChatCommand(prompt, chapterId, sessionId, List.of(), null, null, attachments),
            answer, sources, null);
    }

    @Override
    @Transactional
    public String saveExchange(long userId, ChatCommand command, String answer,
                               List<ChatSource> sources, String reasoning) {
        PendingChat pending = savePendingInternal(userId, command, null);
        return completePendingInternal(userId, pending, answer, sources, reasoning);
    }

    @Override
    @Transactional
    public PendingChat savePending(long userId, ChatCommand command, ChatRetry retry) {
        return savePendingInternal(userId, command, retry);
    }

    private PendingChat savePendingInternal(long userId, ChatCommand command, ChatRetry retry) {
        String resolvedSessionId = ensureSession(userId, command);
        ChatCommand persisted = command.sessionId() == null || !resolvedSessionId.equals(command.sessionId())
            ? new ChatCommand(command.prompt(), command.chapterId(), resolvedSessionId, command.history(),
                command.thinkingEnabled(), command.reasoningEffort(), command.attachments(), command.retryMessageId())
            : command;
        long messageId = insertUserMessage(userId, resolvedSessionId, persisted, "PENDING");
        return new PendingChat(resolvedSessionId, messageId, persisted, retry);
    }

    @Override
    @Transactional
    public String completePending(long userId, PendingChat pending, String answer,
                                 List<ChatSource> sources, String reasoning) {
        return completePendingInternal(userId, pending, answer, sources, reasoning);
    }

    private String completePendingInternal(long userId, PendingChat pending, String answer,
                                           List<ChatSource> sources, String reasoning) {
        if (pending.retry() != null) {
            int updated = jdbc.update("UPDATE chat_sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                pending.sessionId(), userId);
            if (updated != 1) throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "对话会话不存在");
            Long last = jdbc.queryForObject("SELECT MAX(id) FROM chat_messages WHERE session_id = ?", Long.class, pending.sessionId());
            if (last == null || last != pending.messageId()) {
                throw new ApiException(HttpStatus.CONFLICT, "CHAT_RETRY_CONFLICT", "对话已更新，请重新打开会话后重试");
            }
            jdbc.update("DELETE FROM chat_messages WHERE session_id = ? AND id >= ? AND id < ?",
                pending.sessionId(), pending.retry().messageId(), pending.messageId());
        }
        int updated = jdbc.update("UPDATE chat_messages SET generation_status = 'COMPLETED', failure_code = NULL WHERE id = ? AND session_id = ? AND role = 'user'",
            pending.messageId(), pending.sessionId());
        if (updated != 1) throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "对话消息不存在");
        jdbc.update("INSERT INTO chat_messages (session_id, role, content, sources_json, reasoning_content, generation_status) VALUES (?, 'assistant', ?, ?, ?, 'COMPLETED')",
            pending.sessionId(), answer, sourceJson(sources), reasoning);
        return pending.sessionId();
    }

    @Override
    @Transactional
    public void markFailed(long userId, PendingChat pending, String failureCode) {
        jdbc.update("UPDATE chat_messages SET generation_status = 'FAILED', failure_code = ? WHERE id = ? AND session_id = ? AND role = 'user'",
            truncateFailure(failureCode), pending.messageId(), pending.sessionId());
    }

    @Override
    @Transactional
    public void markStopped(long userId, PendingChat pending) {
        jdbc.update("UPDATE chat_messages SET generation_status = 'STOPPED', failure_code = NULL WHERE id = ? AND session_id = ? AND role = 'user'",
            pending.messageId(), pending.sessionId());
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Optional<ChatRetry> prepareRetry(long userId, String sessionId, long messageId, int limit) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        List<ChatCommand> commands = jdbc.query(
            """
            SELECT m.id, m.content, m.attachments_json, m.chapter_id, m.thinking_enabled, m.reasoning_effort,
                   s.chapter_id AS session_chapter
            FROM chat_messages m JOIN chat_sessions s ON s.id = m.session_id
            WHERE s.id = ? AND s.user_id = ? AND m.id = ? AND m.role = 'user'
            """,
            (row, index) -> new ChatCommand(row.getString("content"),
                row.getObject("thinking_enabled") == null ? row.getString("session_chapter") : row.getString("chapter_id"),
                sessionId, List.of(), (Boolean) row.getObject("thinking_enabled"), row.getString("reasoning_effort"),
                modelAttachments(row.getLong("id"), userId, row.getString("attachments_json"))), sessionId, userId, messageId);
        if (commands.isEmpty()) return Optional.empty();
        List<ChatTurn> history = new ArrayList<>(jdbc.query(
            "SELECT id, role, content, attachments_json FROM chat_messages WHERE session_id = ? AND id < ? ORDER BY id DESC LIMIT ?",
            (row, index) -> new ChatTurn(row.getString("role"), row.getString("content"),
                attachmentsForMessage(row.getLong("id"), userId, row.getString("attachments_json"))),
            sessionId, messageId, Math.max(1, limit)));
        Collections.reverse(history);
        Long last = jdbc.queryForObject("SELECT MAX(id) FROM chat_messages WHERE session_id = ?", Long.class, sessionId);
        return Optional.of(new ChatRetry(messageId, last, commands.getFirst(), List.copyOf(history)));
    }

    @Override
    @Transactional
    public String replaceExchange(long userId, ChatRetry retry, String answer, List<ChatSource> sources, String reasoning) {
        String sessionId = retry.command().sessionId();
        Map<String, StoredChatAttachment> preservedAttachments = new java.util.HashMap<>();
        for (ChatAttachment attachment : retry.command().attachments()) {
            if (attachment.attachmentId() == null || attachment.attachmentId().isBlank()) continue;
            StoredChatAttachment stored = findAttachment(userId, attachment.attachmentId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CHAT_ATTACHMENT_NOT_FOUND", "对话附件不存在"));
            preservedAttachments.put(attachment.attachmentId(), stored);
        }
        // Lock the session before checking its tail; ordinary appends take the same row lock.
        int updated = jdbc.update("UPDATE chat_sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
            sessionId, userId);
        if (updated != 1) throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "对话会话不存在");
        Long last = jdbc.queryForObject("SELECT MAX(id) FROM chat_messages WHERE session_id = ?", Long.class, sessionId);
        if (last == null || last != retry.lastMessageId()) {
            throw new ApiException(HttpStatus.CONFLICT, "CHAT_RETRY_CONFLICT", "对话已更新，请重新打开会话后重试");
        }
        jdbc.update("DELETE FROM chat_messages WHERE session_id = ? AND id >= ?", sessionId, retry.messageId());
        insertExchange(userId, sessionId, retry.command(), answer, sources, reasoning, preservedAttachments);
        return sessionId;
    }

    private void insertExchange(long userId, String sessionId, ChatCommand command, String answer,
                                List<ChatSource> sources, String reasoning,
                                Map<String, StoredChatAttachment> preservedAttachments) {
        ChatCommand withoutAttachments = new ChatCommand(command.prompt(), command.chapterId(), sessionId, command.history(),
            command.thinkingEnabled(), command.reasoningEffort(), List.of(), command.retryMessageId());
        long messageId = insertUserMessage(userId, sessionId, withoutAttachments, "COMPLETED");
        List<StoredChatAttachment> created = new ArrayList<>();
        try {
            List<ChatAttachment> metadata = new ArrayList<>();
            for (ChatAttachment attachment : ChatAttachment.validated(command.attachments())) {
                StoredChatAttachment stored;
                if (attachment.attachmentId() != null && !attachment.attachmentId().isBlank()) {
                    StoredChatAttachment source = preservedAttachments.get(attachment.attachmentId());
                    if (source == null) throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_ATTACHMENT_NOT_FOUND", "对话附件不存在");
                    stored = attachmentStorage.copy(userId, messageId, source);
                } else {
                    stored = attachmentStorage.store(userId, messageId, attachment);
                    created.add(stored);
                }
                insertAttachment(stored);
                metadata.add(new ChatAttachment(stored.name(), stored.type(), stored.mimeType(), null, null,
                    stored.encoding(), stored.byteSize(), stored.id(), "/api/v1/chat/attachments/" + stored.id()));
            }
            jdbc.update("UPDATE chat_messages SET attachments_json = ? WHERE id = ?", attachmentJson(metadata), messageId);
        } catch (RuntimeException error) {
            for (StoredChatAttachment stored : created) {
                try { attachmentStorage.delete(stored); } catch (RuntimeException ignored) { }
            }
            throw error;
        }
        jdbc.update("""
            INSERT INTO chat_messages (session_id, role, content, sources_json, reasoning_content)
            VALUES (?, 'assistant', ?, ?, ?)
            """, sessionId, answer, sourceJson(sources), reasoning);
    }

    @Override
    public List<ChatSessionSummary> findSessions(long userId) {
        return jdbc.query(
            """
            SELECT s.id, s.chapter_id, s.title, s.updated_at,
                s.pinned,
                (SELECT COUNT(*) FROM chat_messages m WHERE m.session_id = s.id) AS message_count
            FROM chat_sessions s
            WHERE s.user_id = ?
            ORDER BY s.pinned DESC, s.updated_at DESC, s.id DESC
            LIMIT ?
            """,
            (row, index) -> new ChatSessionSummary(
                row.getString("id"),
                row.getString("chapter_id"),
                row.getString("title"),
                instant(row.getTimestamp("updated_at")),
                row.getLong("message_count"),
                Boolean.TRUE.equals(row.getObject("pinned"))
            ),
            userId,
            MAX_SESSION_RESULTS
        );
    }

    @Override
    public Optional<ChatSessionView> findSession(long userId, String sessionId) {
        List<ChatSessionSummary> sessions = jdbc.query(
            "SELECT id, chapter_id, title, updated_at, pinned FROM chat_sessions WHERE id = ? AND user_id = ?",
            (row, index) -> new ChatSessionSummary(
                row.getString("id"),
                row.getString("chapter_id"),
                row.getString("title"),
                instant(row.getTimestamp("updated_at")),
                0,
                Boolean.TRUE.equals(row.getObject("pinned"))
            ),
            sessionId,
            userId
        );
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        ChatSessionSummary session = sessions.getFirst();
        List<ChatMessageView> newestFirst = jdbc.query(
            """
            SELECT id, role, content, sources_json, created_at, attachments_json,
                    reasoning_content, chapter_id, thinking_enabled, reasoning_effort, generation_status, failure_code
            FROM chat_messages
            WHERE session_id = ?
            ORDER BY id DESC
            LIMIT ?
            """,
            (row, index) -> new ChatMessageView(
                row.getLong("id"),
                row.getString("role"),
                row.getString("content"),
                sources(row.getString("sources_json")),
                instant(row.getTimestamp("created_at")),
                attachmentsForMessage(row.getLong("id"), userId, row.getString("attachments_json")),
                row.getString("reasoning_content"),
                row.getString("chapter_id"),
                (Boolean) row.getObject("thinking_enabled"),
                row.getString("reasoning_effort"),
                row.getString("generation_status"),
                row.getString("failure_code")
            ),
            sessionId,
            MAX_SESSION_MESSAGES
        );
        List<ChatMessageView> messages = new ArrayList<>(newestFirst);
        Collections.reverse(messages);
        return Optional.of(new ChatSessionView(
            session.id(), session.chapterId(), session.title(), session.updatedAt(), List.copyOf(messages), session.pinned()
        ));
    }

    @Override
    public boolean updateSession(long userId, String sessionId, String title, Boolean pinned) {
        int updated;
        if (title != null && pinned != null) {
            updated = jdbc.update("UPDATE chat_sessions SET title = ?, pinned = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                title, pinned, sessionId, userId);
        } else if (title != null) {
            updated = jdbc.update("UPDATE chat_sessions SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                title, sessionId, userId);
        } else {
            updated = jdbc.update("UPDATE chat_sessions SET pinned = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                pinned, sessionId, userId);
        }
        return updated == 1;
    }

    @Override
    public boolean deleteSession(long userId, String sessionId) {
        List<StoredChatAttachment> stored = jdbc.query(
            "SELECT a.id, a.user_id, a.message_id, a.original_name, a.attachment_type, a.mime_type, a.encoding, a.byte_size, a.sha256, a.storage_key, a.created_at "
                + "FROM chat_attachments a JOIN chat_messages m ON m.id = a.message_id WHERE m.session_id = ? AND a.user_id = ?",
            (row, index) -> storedAttachment(row), sessionId, userId);
        boolean deleted = jdbc.update("DELETE FROM chat_sessions WHERE id = ? AND user_id = ?", sessionId, userId) == 1;
        if (deleted) {
            for (StoredChatAttachment item : stored) {
                Integer references = jdbc.queryForObject("SELECT COUNT(*) FROM chat_attachments WHERE storage_key = ?", Integer.class, item.storageKey());
                if (references == null || references == 0) {
                    try { attachmentStorage.delete(item); } catch (RuntimeException ignored) { }
                }
            }
        }
        return deleted;
    }

    @Override
    public List<ChatAttachment> hydrateAttachments(long userId, List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        return attachments.stream().map(item -> {
            if (item.attachmentId() == null || item.attachmentId().isBlank()) return item;
            StoredChatAttachment stored = findAttachment(userId, item.attachmentId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CHAT_ATTACHMENT_NOT_FOUND", "对话附件不存在"));
            return attachmentStorage.hydrateForModel(stored);
        }).toList();
    }

    @Override
    public Optional<StoredChatAttachment> findAttachment(long userId, String attachmentId) {
        return jdbc.query(
            "SELECT id, user_id, message_id, original_name, attachment_type, mime_type, encoding, byte_size, sha256, storage_key, created_at FROM chat_attachments WHERE id = ? AND user_id = ?",
            (row, index) -> storedAttachment(row), attachmentId, userId).stream().findFirst();
    }

    @Override
    public Optional<ChatAttachmentFile> attachmentFile(long userId, String attachmentId) {
        return findAttachment(userId, attachmentId).map(item ->
            new ChatAttachmentFile(item.name(), item.mimeType(), attachmentStorage.read(item)));
    }

    private String ensureSession(long userId, ChatCommand command) {
        String sessionId = command.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO chat_sessions (id, user_id, chapter_id, title) VALUES (?, ?, ?, ?)",
                sessionId, userId, blankToNull(command.chapterId()), title(command.prompt()));
            return sessionId;
        }
        int updated = jdbc.update("UPDATE chat_sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
            sessionId, userId);
        if (updated != 1) throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "对话会话不存在");
        return sessionId;
    }

    private long insertUserMessage(long userId, String sessionId, ChatCommand command, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO chat_messages (session_id, role, content, chapter_id, thinking_enabled, reasoning_effort, generation_status) VALUES (?, 'user', ?, ?, ?, ?, ?)",
                new String[] {"id"});
            statement.setString(1, sessionId);
            statement.setString(2, command.prompt());
            statement.setString(3, blankToNull(command.chapterId()));
            statement.setObject(4, command.thinkingEnabled());
            statement.setString(5, command.reasoningEffort());
            statement.setString(6, status);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Unable to obtain chat message id");
        long messageId = key.longValue();
        List<StoredChatAttachment> created = new ArrayList<>();
        try {
            List<ChatAttachment> metadata = new ArrayList<>();
            for (ChatAttachment attachment : ChatAttachment.validated(command.attachments())) {
                StoredChatAttachment stored;
                if (attachment.attachmentId() != null && !attachment.attachmentId().isBlank()) {
                    stored = findAttachment(userId, attachment.attachmentId()).orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "CHAT_ATTACHMENT_NOT_FOUND", "对话附件不存在"));
                    stored = attachmentStorage.copy(userId, messageId, stored);
                    insertAttachment(stored);
                } else {
                    stored = attachmentStorage.store(userId, messageId, attachment);
                    insertAttachment(stored);
                    created.add(stored);
                }
                metadata.add(new ChatAttachment(stored.name(), stored.type(), stored.mimeType(), null, null,
                    stored.encoding(), stored.byteSize(), stored.id(), "/api/v1/chat/attachments/" + stored.id()));
            }
            jdbc.update("UPDATE chat_messages SET attachments_json = ? WHERE id = ?", attachmentJson(metadata), messageId);
            return messageId;
        } catch (RuntimeException error) {
            for (StoredChatAttachment stored : created) {
                try { attachmentStorage.delete(stored); } catch (RuntimeException ignored) { }
            }
            throw error;
        }
    }

    private void insertAttachment(StoredChatAttachment stored) {
        jdbc.update("INSERT INTO chat_attachments (id, message_id, user_id, original_name, attachment_type, mime_type, encoding, byte_size, sha256, storage_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            stored.id(), stored.messageId(), stored.userId(), stored.name(), stored.type(), stored.mimeType(), stored.encoding(),
            stored.byteSize(), stored.sha256(), stored.storageKey());
    }

    private List<ChatAttachment> attachmentsForMessage(long messageId, long userId, String value) {
        return attachments(value).stream().map(item -> {
            if (item.attachmentId() == null || item.attachmentId().isBlank()) return item;
            StoredChatAttachment stored = findAttachment(userId, item.attachmentId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CHAT_ATTACHMENT_NOT_FOUND", "对话附件不存在"));
            if (stored.messageId() != messageId) throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_ATTACHMENT_NOT_FOUND", "对话附件不存在");
            return attachmentStorage.hydrate(stored);
        }).toList();
    }

    private List<ChatAttachment> modelAttachments(long messageId, long userId, String value) {
        return attachmentsForMessage(messageId, userId, value);
    }

    private StoredChatAttachment storedAttachment(java.sql.ResultSet row) throws java.sql.SQLException {
        return new StoredChatAttachment(row.getString("id"), row.getLong("user_id"), row.getLong("message_id"),
            row.getString("original_name"), row.getString("attachment_type"), row.getString("mime_type"),
            row.getString("encoding"), row.getLong("byte_size"), row.getString("sha256"), row.getString("storage_key"),
            row.getTimestamp("created_at").toInstant());
    }

    private String truncateFailure(String value) {
        String normalized = value == null || value.isBlank() ? "CHAT_STREAM_FAILED" : value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private List<ChatSource> sources(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            tools.jackson.databind.JsonNode root = objectMapper.readTree(value);
            if (!root.isArray()) {
                return List.of();
            }
            List<ChatSource> sources = new ArrayList<>();
            for (tools.jackson.databind.JsonNode node : root) {
                if (!node.isObject()) {
                    continue;
                }
                sources.add(new ChatSource(
                    node.path("id").asText(),
                    nullableText(node, "chapterId"),
                    node.path("title").asText(),
                    node.path("content").asText(),
                    node.path("source").asText(),
                    nullableText(node, "pageLabel"),
                    node.path("score").asDouble(0),
                    evidenceHash(node)
                ));
            }
            return List.copyOf(sources);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<ChatAttachment> attachments(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return List.of(objectMapper.readValue(value, ChatAttachment[].class));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read chat attachments", error);
        }
    }

    private String attachmentJson(List<ChatAttachment> attachments) {
        try { return objectMapper.writeValueAsString(attachments); }
        catch (Exception error) { throw new IllegalStateException("Unable to serialize chat attachments", error); }
    }

    private String nullableText(tools.jackson.databind.JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }

    private String evidenceHash(tools.jackson.databind.JsonNode node) {
        String stored = nullableText(node, "evidenceHash");
        if (stored != null && stored.matches("[a-fA-F0-9]{64}")) {
            return stored.toLowerCase(java.util.Locale.ROOT);
        }
        return ChatEvidenceFingerprint.hash(
            node.path("title").asText(),
            node.path("content").asText(),
            node.path("source").asText(),
            nullableText(node, "pageLabel")
        );
    }

    private java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String sourceJson(List<ChatSource> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to serialize chat sources", error);
        }
    }

    private String title(String prompt) {
        String normalized = prompt.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 60 ? normalized : normalized.substring(0, 60);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
