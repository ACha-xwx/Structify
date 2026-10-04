package com.feng.dsagent.chat;

import java.util.List;
import java.util.Optional;

interface ChatRepository {

    boolean isPublishedChapter(String chapterId);

    Optional<List<ChatTurn>> recentHistory(long userId, String sessionId, int limit);

    String saveExchange(
        long userId,
        String sessionId,
        String chapterId,
        String prompt,
        String answer,
        List<ChatSource> sources
    );

    default String saveExchange(long userId, String sessionId, String chapterId, String prompt,
                                String answer, List<ChatSource> sources, List<ChatAttachment> attachments) {
        return saveExchange(userId, sessionId, chapterId, prompt, answer, sources);
    }

    default String saveExchange(long userId, ChatCommand command, String answer,
                                List<ChatSource> sources, String reasoning) {
        return saveExchange(userId, command.sessionId(), command.chapterId(), command.prompt(), answer,
            sources, command.attachments());
    }

    default Optional<ChatRetry> prepareRetry(long userId, String sessionId, long messageId, int limit) {
        return Optional.empty();
    }

    default String replaceExchange(long userId, ChatRetry retry, String answer,
                                   List<ChatSource> sources, String reasoning) {
        throw new IllegalStateException("Chat retry is not supported by this repository");
    }

    /** Replaces an exchange with the edited learner command after the model succeeds. */
    default String replaceExchange(long userId, ChatRetry retry, ChatCommand command, String answer,
                                   List<ChatSource> sources, String reasoning) {
        return replaceExchange(userId, retry, answer, sources, reasoning);
    }

    /** Stores the learner's turn before any model request starts. */
    default PendingChat savePending(long userId, ChatCommand command, ChatRetry retry) {
        return new PendingChat(command.sessionId(), -1L, command, retry);
    }

    /** Marks a persisted turn whose model request failed without deleting the learner's input. */
    default void markFailed(long userId, PendingChat pending, String failureCode) {
    }

    /** Marks a persisted turn whose stream was closed by the client or transport. */
    default void markStopped(long userId, PendingChat pending) {
    }

    /** Loads private attachment bytes for model replay; metadata-only repositories may leave this unchanged. */
    default List<ChatAttachment> hydrateAttachments(long userId, List<ChatAttachment> attachments) {
        return attachments == null ? List.of() : attachments;
    }

    default String completePending(long userId, PendingChat pending, String answer,
                                   List<ChatSource> sources, String reasoning) {
        if (pending.retry() != null) {
            return replaceExchange(userId, pending.retry(), pending.command(), answer, sources, reasoning);
        }
        return saveExchange(userId, pending.command(), answer, sources, reasoning);
    }

    default Optional<StoredChatAttachment> findAttachment(long userId, String attachmentId) {
        return Optional.empty();
    }

    default Optional<ChatAttachmentFile> attachmentFile(long userId, String attachmentId) {
        return Optional.empty();
    }
}
