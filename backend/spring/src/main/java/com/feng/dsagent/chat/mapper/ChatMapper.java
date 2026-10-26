package com.feng.dsagent.chat.mapper;

import com.feng.dsagent.chat.entity.ChatUserMessageEntity;
import com.feng.dsagent.chat.entity.StoredChatAttachmentEntity;
import java.sql.Timestamp;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMapper {

    int countPublishedChapter(@Param("chapterId") String chapterId);

    int countOwnedSession(@Param("sessionId") String sessionId, @Param("userId") long userId);

    List<HistoryMessageRow> selectRecentMessages(@Param("sessionId") String sessionId, @Param("limit") int limit);

    int touchSession(@Param("sessionId") String sessionId, @Param("userId") long userId);

    Long selectMaxMessageId(@Param("sessionId") String sessionId);

    int deleteMessagesInRange(
        @Param("sessionId") String sessionId,
        @Param("fromId") long fromId,
        @Param("toIdExclusive") long toIdExclusive
    );

    int completeUserMessage(@Param("messageId") long messageId, @Param("sessionId") String sessionId);

    int insertAssistantMessage(
        @Param("sessionId") String sessionId,
        @Param("content") String content,
        @Param("sourcesJson") String sourcesJson,
        @Param("reasoning") String reasoning
    );

    int insertAssistantMessageLegacy(
        @Param("sessionId") String sessionId,
        @Param("content") String content,
        @Param("sourcesJson") String sourcesJson,
        @Param("reasoning") String reasoning
    );

    int markMessageFailed(
        @Param("failureCode") String failureCode,
        @Param("messageId") long messageId,
        @Param("sessionId") String sessionId
    );

    int markMessageStopped(@Param("messageId") long messageId, @Param("sessionId") String sessionId);

    List<RetryCommandRow> selectRetryCommand(
        @Param("sessionId") String sessionId,
        @Param("userId") long userId,
        @Param("messageId") long messageId
    );

    List<HistoryMessageRow> selectHistoryBefore(
        @Param("sessionId") String sessionId,
        @Param("messageId") long messageId,
        @Param("limit") int limit
    );

    int deleteMessagesFrom(@Param("sessionId") String sessionId, @Param("fromId") long fromId);

    List<SessionSummaryRow> selectSessions(@Param("userId") long userId, @Param("limit") int limit);

    List<SessionSummaryRow> selectSession(@Param("sessionId") String sessionId, @Param("userId") long userId);

    List<SessionMessageRow> selectSessionMessages(@Param("sessionId") String sessionId, @Param("limit") int limit);

    int updateSessionTitleAndPinned(
        @Param("title") String title,
        @Param("pinned") Boolean pinned,
        @Param("sessionId") String sessionId,
        @Param("userId") long userId
    );

    int updateSessionTitle(
        @Param("title") String title,
        @Param("sessionId") String sessionId,
        @Param("userId") long userId
    );

    int updateSessionPinned(
        @Param("pinned") Boolean pinned,
        @Param("sessionId") String sessionId,
        @Param("userId") long userId
    );

    List<StoredChatAttachmentEntity> selectAttachmentsForSession(
        @Param("sessionId") String sessionId,
        @Param("userId") long userId
    );

    int deleteSession(@Param("sessionId") String sessionId, @Param("userId") long userId);

    int countAttachmentsByStorageKey(@Param("storageKey") String storageKey);

    StoredChatAttachmentEntity selectAttachment(@Param("attachmentId") String attachmentId, @Param("userId") long userId);

    int insertSession(
        @Param("id") String id,
        @Param("userId") long userId,
        @Param("chapterId") String chapterId,
        @Param("title") String title
    );

    int insertUserMessage(ChatUserMessageEntity message);

    int updateMessageAttachments(@Param("attachmentsJson") String attachmentsJson, @Param("messageId") long messageId);

    int insertAttachment(StoredChatAttachmentEntity attachment);

    record HistoryMessageRow(long id, String role, String content, String attachmentsJson) {
    }

    record RetryCommandRow(
        long id,
        String content,
        String attachmentsJson,
        String chapterId,
        Boolean thinkingEnabled,
        String reasoningEffort,
        String sessionChapter
    ) {
    }

    record SessionSummaryRow(
        String id,
        String chapterId,
        String title,
        Timestamp updatedAt,
        Long messageCount,
        Boolean pinned
    ) {
    }

    record SessionMessageRow(
        long id,
        String role,
        String content,
        String sourcesJson,
        Timestamp createdAt,
        String attachmentsJson,
        String reasoningContent,
        String chapterId,
        Boolean thinkingEnabled,
        String reasoningEffort,
        String generationStatus,
        String failureCode
    ) {
    }
}
