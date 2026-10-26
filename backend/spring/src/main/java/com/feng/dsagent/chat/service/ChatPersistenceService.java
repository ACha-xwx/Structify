package com.feng.dsagent.chat.service;

import com.feng.dsagent.chat.converter.ChatMessageConverter;
import com.feng.dsagent.chat.domain.ChatAttachment;
import com.feng.dsagent.chat.domain.ChatAttachmentFile;
import com.feng.dsagent.chat.domain.ChatRetry;
import com.feng.dsagent.chat.domain.ChatSource;
import com.feng.dsagent.chat.domain.ChatTurn;
import com.feng.dsagent.chat.domain.ChatCommand;
import com.feng.dsagent.chat.entity.ChatUserMessageEntity;
import com.feng.dsagent.chat.entity.StoredChatAttachmentEntity;
import com.feng.dsagent.chat.mapper.ChatMapper;
import com.feng.dsagent.chat.support.ChatAttachmentStorage;
import com.feng.dsagent.chat.support.PendingChat;
import com.feng.dsagent.chat.vo.ChatMessageVO;
import com.feng.dsagent.chat.vo.ChatSessionSummaryVO;
import com.feng.dsagent.chat.vo.ChatSessionVO;
import com.feng.dsagent.chat.error.ChatErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChatPersistenceService {

    private static final int MAX_SESSION_RESULTS = 50;
    private static final int MAX_SESSION_MESSAGES = 200;

    private final ChatMapper mapper;
    private final ObjectMapper objectMapper;
    private final ChatAttachmentStorage attachmentStorage;
    private final ChatMessageConverter chatMessageConverter;

    public ChatPersistenceService(
        ChatMapper mapper,
        ObjectMapper objectMapper,
        ChatAttachmentStorage attachmentStorage,
        ChatMessageConverter chatMessageConverter
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.attachmentStorage = attachmentStorage;
        this.chatMessageConverter = chatMessageConverter;
    }

    /** Test double constructor. */
    protected ChatPersistenceService() {
        this.mapper = null;
        this.objectMapper = null;
        this.attachmentStorage = null;
        this.chatMessageConverter = null;
    }

    public boolean isPublishedChapter(String chapterId) {
        return mapper.countPublishedChapter(chapterId) > 0;
    }

    public Optional<List<ChatTurn>> recentHistory(long userId, String sessionId, int limit) {
        if (mapper.countOwnedSession(sessionId, userId) == 0) {
            return Optional.empty();
        }
        List<ChatTurn> newestFirst = mapper.selectRecentMessages(sessionId, Math.max(1, limit)).stream()
            .map(row -> new ChatTurn(row.role(), row.content(),
                attachmentsForMessage(row.id(), userId, row.attachmentsJson())))
            .toList();
        List<ChatTurn> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return Optional.of(List.copyOf(chronological));
    }

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

    @Transactional
    public String saveExchange(long userId, String sessionId, String chapterId, String prompt,
                               String answer, List<ChatSource> sources, List<ChatAttachment> attachments) {
        return saveExchange(userId, new ChatCommand(prompt, chapterId, sessionId, List.of(), null, null, attachments),
            answer, sources, null);
    }

    @Transactional
    public String saveExchange(long userId, ChatCommand command, String answer,
                               List<ChatSource> sources, String reasoning) {
        PendingChat pending = savePendingInternal(userId, command, null);
        return completePendingInternal(userId, pending, answer, sources, reasoning);
    }

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

    @Transactional
    public String completePending(long userId, PendingChat pending, String answer,
                                 List<ChatSource> sources, String reasoning) {
        return completePendingInternal(userId, pending, answer, sources, reasoning);
    }

    private String completePendingInternal(long userId, PendingChat pending, String answer,
                                           List<ChatSource> sources, String reasoning) {
        if (pending.retry() != null) {
            int updated = mapper.touchSession(pending.sessionId(), userId);
            if (updated != 1) throw ChatErrorCode.SESSION_NOT_FOUND.toException();
            Long last = mapper.selectMaxMessageId(pending.sessionId());
            if (last == null || last != pending.messageId()) {
                throw ChatErrorCode.RETRY_CONFLICT.toException();
            }
            mapper.deleteMessagesInRange(pending.sessionId(), pending.retry().messageId(), pending.messageId());
        }
        int updated = mapper.completeUserMessage(pending.messageId(), pending.sessionId());
        if (updated != 1) throw ChatErrorCode.SESSION_NOT_FOUND.toException("对话消息不存在");
        mapper.insertAssistantMessage(pending.sessionId(), answer, sourceJson(sources), reasoning);
        return pending.sessionId();
    }

    @Transactional
    public void markFailed(long userId, PendingChat pending, String failureCode) {
        mapper.markMessageFailed(truncateFailure(failureCode), pending.messageId(), pending.sessionId());
    }

    @Transactional
    public void markStopped(long userId, PendingChat pending) {
        mapper.markMessageStopped(pending.messageId(), pending.sessionId());
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Optional<ChatRetry> prepareRetry(long userId, String sessionId, long messageId, int limit) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        List<ChatMapper.RetryCommandRow> rows = mapper.selectRetryCommand(sessionId, userId, messageId);
        if (rows.isEmpty()) return Optional.empty();
        ChatMapper.RetryCommandRow row = rows.getFirst();
        ChatCommand command = new ChatCommand(
            row.content(),
            row.thinkingEnabled() == null ? row.sessionChapter() : row.chapterId(),
            sessionId,
            List.of(),
            row.thinkingEnabled(),
            row.reasoningEffort(),
            modelAttachments(row.id(), userId, row.attachmentsJson())
        );
        List<ChatTurn> history = new ArrayList<>(mapper.selectHistoryBefore(sessionId, messageId, Math.max(1, limit)).stream()
            .map(item -> new ChatTurn(item.role(), item.content(),
                attachmentsForMessage(item.id(), userId, item.attachmentsJson())))
            .toList());
        Collections.reverse(history);
        Long last = mapper.selectMaxMessageId(sessionId);
        return Optional.of(new ChatRetry(messageId, last, command, List.copyOf(history)));
    }

    @Transactional
    public String replaceExchange(long userId, ChatRetry retry, String answer, List<ChatSource> sources, String reasoning) {
        return replaceExchange(userId, retry, retry.command(), answer, sources, reasoning);
    }

    @Transactional
    public String replaceExchange(long userId, ChatRetry retry, ChatCommand command,
                                  String answer, List<ChatSource> sources, String reasoning) {
        String sessionId = retry.command().sessionId();
        Map<String, StoredChatAttachmentEntity> preservedAttachments = new HashMap<>();
        for (ChatAttachment attachment : command.attachments()) {
            if (attachment.attachmentId() == null || attachment.attachmentId().isBlank()) continue;
            StoredChatAttachmentEntity stored = findAttachment(userId, attachment.attachmentId()).orElseThrow(() ->
                ChatErrorCode.ATTACHMENT_NOT_FOUND.toException());
            preservedAttachments.put(attachment.attachmentId(), stored);
        }
        int updated = mapper.touchSession(sessionId, userId);
        if (updated != 1) throw ChatErrorCode.SESSION_NOT_FOUND.toException();
        Long last = mapper.selectMaxMessageId(sessionId);
        if (last == null || last != retry.lastMessageId()) {
            throw ChatErrorCode.RETRY_CONFLICT.toException();
        }
        mapper.deleteMessagesFrom(sessionId, retry.messageId());
        insertExchange(userId, sessionId, command, answer, sources, reasoning, preservedAttachments);
        return sessionId;
    }

    private void insertExchange(long userId, String sessionId, ChatCommand command, String answer,
                                List<ChatSource> sources, String reasoning,
                                Map<String, StoredChatAttachmentEntity> preservedAttachments) {
        ChatCommand withoutAttachments = new ChatCommand(command.prompt(), command.chapterId(), sessionId, command.history(),
            command.thinkingEnabled(), command.reasoningEffort(), List.of(), command.retryMessageId());
        long messageId = insertUserMessage(userId, sessionId, withoutAttachments, "COMPLETED");
        List<StoredChatAttachmentEntity> created = new ArrayList<>();
        try {
            List<ChatAttachment> metadata = new ArrayList<>();
            for (ChatAttachment attachment : ChatAttachment.validated(command.attachments())) {
                StoredChatAttachmentEntity stored;
                if (attachment.attachmentId() != null && !attachment.attachmentId().isBlank()) {
                    StoredChatAttachmentEntity source = preservedAttachments.get(attachment.attachmentId());
                    if (source == null) throw ChatErrorCode.ATTACHMENT_NOT_FOUND.toException();
                    stored = attachmentStorage.copy(userId, messageId, source);
                } else {
                    stored = attachmentStorage.store(userId, messageId, attachment);
                    created.add(stored);
                }
                insertAttachment(stored);
                metadata.add(new ChatAttachment(stored.name(), stored.type(), stored.mimeType(), null, null,
                    stored.encoding(), stored.byteSize(), stored.id(), "/api/v1/chat/attachments/" + stored.id()));
            }
            mapper.updateMessageAttachments(attachmentJson(metadata), messageId);
        } catch (RuntimeException error) {
            for (StoredChatAttachmentEntity stored : created) {
                try { attachmentStorage.delete(stored); } catch (RuntimeException ignored) { }
            }
            throw error;
        }
        mapper.insertAssistantMessageLegacy(sessionId, answer, sourceJson(sources), reasoning);
    }

    public List<ChatSessionSummaryVO> findSessions(long userId) {
        return mapper.selectSessions(userId, MAX_SESSION_RESULTS).stream()
            .map(chatMessageConverter::toSummaryView)
            .toList();
    }

    public Optional<ChatSessionVO> findSession(long userId, String sessionId) {
        List<ChatMapper.SessionSummaryRow> sessions = mapper.selectSession(sessionId, userId);
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        ChatMapper.SessionSummaryRow session = sessions.getFirst();
        List<ChatMessageVO> newestFirst = mapper.selectSessionMessages(sessionId, MAX_SESSION_MESSAGES).stream()
            .map(row -> chatMessageConverter.toView(
                row,
                attachmentsForMessage(row.id(), userId, row.attachmentsJson(), true)
            ))
            .toList();
        List<ChatMessageVO> messages = new ArrayList<>(newestFirst);
        Collections.reverse(messages);
        return Optional.of(chatMessageConverter.toSessionView(session, List.copyOf(messages)));
    }

    public boolean updateSession(long userId, String sessionId, String title, Boolean pinned) {
        int updated;
        if (title != null && pinned != null) {
            updated = mapper.updateSessionTitleAndPinned(title, pinned, sessionId, userId);
        } else if (title != null) {
            updated = mapper.updateSessionTitle(title, sessionId, userId);
        } else {
            updated = mapper.updateSessionPinned(pinned, sessionId, userId);
        }
        return updated == 1;
    }

    public boolean deleteSession(long userId, String sessionId) {
        List<StoredChatAttachmentEntity> stored = mapper.selectAttachmentsForSession(sessionId, userId);
        boolean deleted = mapper.deleteSession(sessionId, userId) == 1;
        if (deleted) {
            for (StoredChatAttachmentEntity item : stored) {
                int references = mapper.countAttachmentsByStorageKey(item.storageKey());
                if (references == 0) {
                    try { attachmentStorage.delete(item); } catch (RuntimeException ignored) { }
                }
            }
        }
        return deleted;
    }

    public List<ChatAttachment> hydrateAttachments(long userId, List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        return attachments.stream().map(item -> {
            if (item.attachmentId() == null || item.attachmentId().isBlank()) return item;
            StoredChatAttachmentEntity stored = findAttachment(userId, item.attachmentId()).orElseThrow(() ->
                ChatErrorCode.ATTACHMENT_NOT_FOUND.toException());
            return attachmentStorage.hydrateForModel(stored);
        }).toList();
    }

    public Optional<StoredChatAttachmentEntity> findAttachment(long userId, String attachmentId) {
        return Optional.ofNullable(mapper.selectAttachment(attachmentId, userId));
    }

    public Optional<ChatAttachmentFile> attachmentFile(long userId, String attachmentId) {
        return findAttachment(userId, attachmentId).map(item ->
            new ChatAttachmentFile(item.name(), item.mimeType(), attachmentStorage.read(item)));
    }

    private String ensureSession(long userId, ChatCommand command) {
        String sessionId = command.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            mapper.insertSession(sessionId, userId, blankToNull(command.chapterId()), title(command.prompt()));
            return sessionId;
        }
        int updated = mapper.touchSession(sessionId, userId);
        if (updated != 1) throw ChatErrorCode.SESSION_NOT_FOUND.toException();
        return sessionId;
    }

    private long insertUserMessage(long userId, String sessionId, ChatCommand command, String status) {
        ChatUserMessageEntity message = new ChatUserMessageEntity();
        message.setSessionId(sessionId);
        message.setContent(command.prompt());
        message.setChapterId(blankToNull(command.chapterId()));
        message.setThinkingEnabled(command.thinkingEnabled());
        message.setReasoningEffort(command.reasoningEffort());
        message.setGenerationStatus(status);
        mapper.insertUserMessage(message);
        if (message.getId() == null) throw new IllegalStateException("Unable to obtain chat message id");
        long messageId = message.getId();
        List<StoredChatAttachmentEntity> created = new ArrayList<>();
        try {
            List<ChatAttachment> metadata = new ArrayList<>();
            for (ChatAttachment attachment : ChatAttachment.validated(command.attachments())) {
                StoredChatAttachmentEntity stored;
                if (attachment.attachmentId() != null && !attachment.attachmentId().isBlank()) {
                    stored = findAttachment(userId, attachment.attachmentId()).orElseThrow(() ->
                        ChatErrorCode.ATTACHMENT_NOT_FOUND.toException());
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
            mapper.updateMessageAttachments(attachmentJson(metadata), messageId);
            return messageId;
        } catch (RuntimeException error) {
            for (StoredChatAttachmentEntity stored : created) {
                try { attachmentStorage.delete(stored); } catch (RuntimeException ignored) { }
            }
            throw error;
        }
    }

    private void insertAttachment(StoredChatAttachmentEntity stored) {
        mapper.insertAttachment(stored);
    }

    private List<ChatAttachment> attachmentsForMessage(long messageId, long userId, String value) {
        return attachmentsForMessage(messageId, userId, value, false);
    }

    private List<ChatAttachment> attachmentsForMessage(long messageId, long userId, String value, boolean forDisplay) {
        return attachments(value).stream().map(item -> {
            if (item.attachmentId() == null || item.attachmentId().isBlank()) return item;
            if (forDisplay) {
                return new ChatAttachment(item.name(), item.type(), item.mimeType(), "", null,
                    item.encoding(), item.byteSize(), item.attachmentId(), item.downloadUrl());
            }
            StoredChatAttachmentEntity stored = findAttachment(userId, item.attachmentId()).orElseThrow(() ->
                ChatErrorCode.ATTACHMENT_NOT_FOUND.toException());
            if (stored.messageId() != messageId) throw ChatErrorCode.ATTACHMENT_NOT_FOUND.toException();
            return attachmentStorage.hydrate(stored);
        }).toList();
    }

    private List<ChatAttachment> modelAttachments(long messageId, long userId, String value) {
        return attachmentsForMessage(messageId, userId, value);
    }

    private String truncateFailure(String value) {
        String normalized = value == null || value.isBlank() ? "CHAT_STREAM_FAILED" : value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
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
