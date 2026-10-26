package com.feng.dsagent.chat.converter;

import com.feng.dsagent.chat.domain.ChatAttachment;
import com.feng.dsagent.chat.domain.ChatSource;
import com.feng.dsagent.chat.mapper.ChatMapper;
import com.feng.dsagent.chat.support.ChatEvidenceFingerprint;
import com.feng.dsagent.chat.vo.ChatMessageVO;
import com.feng.dsagent.chat.vo.ChatSessionSummaryVO;
import com.feng.dsagent.chat.vo.ChatSessionVO;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 职责：将对话消息与会话查询行转换为对外 VO（含 JSON 字段解析）。
 */
@Mapper(componentModel = "spring")
public abstract class ChatMessageConverter {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "sources", source = "row.sourcesJson", qualifiedByName = "parseSources")
    @Mapping(target = "createdAt", source = "row.createdAt", qualifiedByName = "toInstant")
    @Mapping(target = "reasoning", source = "row.reasoningContent")
    @Mapping(target = "attachments", source = "attachments")
    public abstract ChatMessageVO toView(ChatMapper.SessionMessageRow row, List<ChatAttachment> attachments);

    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "toInstant")
    @Mapping(target = "messageCount", expression = "java(row.messageCount() == null ? 0L : row.messageCount())")
    @Mapping(target = "pinned", expression = "java(Boolean.TRUE.equals(row.pinned()))")
    public abstract ChatSessionSummaryVO toSummaryView(ChatMapper.SessionSummaryRow row);

    @Mapping(target = "updatedAt", source = "session.updatedAt", qualifiedByName = "toInstant")
    @Mapping(target = "id", source = "session.id")
    @Mapping(target = "chapterId", source = "session.chapterId")
    @Mapping(target = "title", source = "session.title")
    @Mapping(target = "pinned", expression = "java(Boolean.TRUE.equals(session.pinned()))")
    @Mapping(target = "messages", source = "messages")
    public abstract ChatSessionVO toSessionView(ChatMapper.SessionSummaryRow session, List<ChatMessageVO> messages);

    @Named("toInstant")
    protected Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @Named("parseSources")
    protected List<ChatSource> parseSources(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!root.isArray()) {
                return List.of();
            }
            List<ChatSource> sources = new ArrayList<>();
            for (JsonNode node : root) {
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

    private String nullableText(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }

    private String evidenceHash(JsonNode node) {
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
}
