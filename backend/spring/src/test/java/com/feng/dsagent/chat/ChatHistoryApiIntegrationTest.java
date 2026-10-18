package com.feng.dsagent.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.security.JwtTokenService;
import java.sql.Timestamp;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
class ChatHistoryApiIntegrationTest {

    private static final long OWNER = 8501L;
    private static final long OTHER = 8502L;

    @TempDir
    static Path attachmentDirectory;

    @DynamicPropertySource
    static void configurePrivateStorage(DynamicPropertyRegistry properties) {
        properties.add("app.chat.attachments.directory", () -> attachmentDirectory.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtTokenService tokens;

    @Autowired
    private ChatRepository chats;

    @BeforeEach
    void prepareData() {
        jdbc.update("DELETE FROM chat_messages WHERE session_id LIKE 'history-api-%'");
        jdbc.update("DELETE FROM chat_sessions WHERE id LIKE 'history-api-%'");
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (?, ?)", OWNER, OTHER);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", OWNER, OTHER);
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)", OWNER, "owner-history@example.com", "hash");
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)", OTHER, "other-history@example.com", "hash");
        jdbc.update(
            "INSERT INTO chat_sessions (id, user_id, chapter_id, title) VALUES (?, ?, ?, ?)",
            "history-api-owned", OWNER, "03-stack-queue", "栈的操作"
        );
        jdbc.update(
            "INSERT INTO chat_messages (session_id, role, content) VALUES (?, ?, ?)",
            "history-api-owned", "user", "什么是入栈？"
        );
    }

    @Test
    void exposesAndDeletesOnlyOwnedChatSessions() throws Exception {
        String ownerToken = token(OWNER, "owner-history@example.com");
        String otherToken = token(OTHER, "other-history@example.com");

        mockMvc.perform(get("/api/v1/chat/sessions").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("history-api-owned"));

        mockMvc.perform(get("/api/v1/chat/sessions/history-api-owned").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].content").value("什么是入栈？"));

        mockMvc.perform(get("/api/v1/chat/sessions/history-api-owned").header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CHAT_SESSION_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/chat/sessions/history-api-owned").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void returnsOnlyTheFiftyMostRecentlyUpdatedSessions() throws Exception {
        Instant base = Instant.parse("2030-01-01T00:00:00Z");
        for (int index = 0; index < 55; index++) {
            jdbc.update(
                "INSERT INTO chat_sessions (id, user_id, title, updated_at) VALUES (?, ?, ?, ?)",
                "history-api-list-%02d".formatted(index),
                OWNER,
                "会话 " + index,
                Timestamp.from(base.plusSeconds(index))
            );
        }

        mockMvc.perform(get("/api/v1/chat/sessions").header("Authorization", "Bearer " + token(OWNER, "owner-history@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(50))
            .andExpect(jsonPath("$[0].id").value("history-api-list-54"))
            .andExpect(jsonPath("$[49].id").value("history-api-list-05"));
    }

    @Test
    void renamesAndPinsOnlyTheOwnedSession() throws Exception {
        String ownerToken = token(OWNER, "owner-history@example.com");
        String otherToken = token(OTHER, "other-history@example.com");

        mockMvc.perform(patch("/api/v1/chat/sessions/history-api-owned")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content("{\"title\":\"新的标题\",\"pinned\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("新的标题"))
            .andExpect(jsonPath("$.pinned").value(true));

        mockMvc.perform(get("/api/v1/chat/sessions").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("history-api-owned"))
            .andExpect(jsonPath("$[0].pinned").value(true));

        mockMvc.perform(patch("/api/v1/chat/sessions/history-api-owned")
                .header("Authorization", "Bearer " + otherToken)
                .contentType("application/json")
                .content("{\"pinned\":false}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CHAT_SESSION_NOT_FOUND"));
    }

    @Test
    void returnsOnlyTheLatestTwoHundredMessagesInChronologicalOrder() throws Exception {
        for (int index = 1; index <= 205; index++) {
            jdbc.update(
                "INSERT INTO chat_messages (session_id, role, content) VALUES (?, 'user', ?)",
                "history-api-owned",
                "消息-%03d".formatted(index)
            );
        }

        mockMvc.perform(get("/api/v1/chat/sessions/history-api-owned")
                .header("Authorization", "Bearer " + token(OWNER, "owner-history@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(200))
            .andExpect(jsonPath("$.messages[0].content").value("消息-006"))
            .andExpect(jsonPath("$.messages[199].content").value("消息-205"));
    }

    @Test
    void rejectsWritingToADeletedOrForeignSessionBeforeMessagesAreInserted() {
        jdbc.update(
            "INSERT INTO chat_sessions (id, user_id, title) VALUES (?, ?, ?)",
            "history-api-foreign", OTHER, "其他用户的会话"
        );

        assertThatThrownBy(() -> chats.saveExchange(
            OWNER,
            "history-api-foreign",
            null,
            "越权问题",
            "不应保存的回答",
            List.of()
        )).isInstanceOfSatisfying(ApiException.class, error -> {
            org.assertj.core.api.Assertions.assertThat(error.status().value()).isEqualTo(404);
            org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("CHAT_SESSION_NOT_FOUND");
        });

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM chat_messages WHERE session_id = ?",
            Integer.class,
            "history-api-foreign"
        )).isZero();
    }

    @Test
    void keepsOriginalFileDownloadableAndReusableWhenTheAnswerFailsOrStops() throws Exception {
        byte[] original = "\ufeff// 哈夫曼树\r\nint main(void) { return 0; }\r\n".getBytes(StandardCharsets.UTF_16LE);
        ChatAttachment file = new ChatAttachment("HuffmanTree.c", "file", "text/plain",
            "// 哈夫曼树\r\nint main(void) { return 0; }\r\n", Base64.getEncoder().encodeToString(original),
            "utf-16le", (long) original.length, null, null);
        for (boolean stopped : List.of(false, true)) {
            PendingChat pending = chats.savePending(OWNER,
                new ChatCommand("我这个写得怎么样？", null, "history-api-owned", List.of(), true, "max", List.of(file)), null);
            if (stopped) chats.markStopped(OWNER, pending);
            else chats.markFailed(OWNER, pending, "MODEL_UPSTREAM_ERROR");

            String attachmentId = jdbc.queryForObject("SELECT id FROM chat_attachments WHERE message_id = ?",
                String.class, pending.messageId());
            mockMvc.perform(get("/api/v1/chat/attachments/" + attachmentId)
                    .header("Authorization", "Bearer " + token(OWNER, "owner-history@example.com")))
                .andExpect(status().isOk()).andExpect(content().bytes(original));
            mockMvc.perform(get("/api/v1/chat/attachments/" + attachmentId)
                    .header("Authorization", "Bearer " + token(OTHER, "other-history@example.com")))
                .andExpect(status().isNotFound());
            ChatAttachment replay = chats.prepareRetry(OWNER, pending.sessionId(), pending.messageId(), 12)
                .orElseThrow().command().attachments().get(0);
            assertThat(replay.content()).contains("哈夫曼树", "int main(void)");
            assertThat(replay.encoding()).isEqualTo("utf-16le");
            assertThat(replay.attachmentId()).isEqualTo(attachmentId);
        }
    }

    @Test
    void persistsOriginalRequestAndReasoningAndReplacesOnlyTheSelectedRoundAndLaterMessages() throws Exception {
        String session = "history-api-owned";
        chats.saveExchange(OWNER, session, null, "earlier question", "earlier answer", List.of());
        ChatAttachment file = new ChatAttachment("stack.c", "file", "text/plain", "int top = -1;");
        ChatCommand original = new ChatCommand("retry question", null, session, List.of(), true, "max", List.of(file));
        chats.saveExchange(OWNER, original, "old answer", List.of(), "old reasoning");
        chats.saveExchange(OWNER, session, null, "later question", "later answer", List.of());
        long message = questionId("retry question");

        ChatRetry retry = chats.prepareRetry(OWNER, session, message, 12).orElseThrow();
        assertRetryCommand(original, retry.command());
        assertThat(retry.history()).extracting(ChatTurn::content)
            .containsExactly("什么是入栈？", "earlier question", "earlier answer");
        assertThat(chats.prepareRetry(OTHER, session, message, 12)).isEmpty();
        assertThat(chats.prepareRetry(OWNER, session, message + 1, 12)).isEmpty();

        mockMvc.perform(get("/api/v1/chat/sessions/" + session)
                .header("Authorization", "Bearer " + token(OWNER, "owner-history@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[3].attachments[0].name").value("stack.c"))
            .andExpect(jsonPath("$.messages[3].attachments[0].content").value(""))
            .andExpect(jsonPath("$.messages[3].thinkingEnabled").value(true))
            .andExpect(jsonPath("$.messages[3].reasoningEffort").value("max"))
            .andExpect(jsonPath("$.messages[4].reasoning").value("old reasoning"));

        ChatCommand edited = new ChatCommand("edited stack question", null, session, List.of(), false, "low", List.of());
        chats.replaceExchange(OWNER, retry, edited, "new answer", List.of(), "new reasoning");
        assertThat(contents()).containsExactly("什么是入栈？", "earlier question", "earlier answer", "edited stack question", "new answer");
        ChatCommand replaced = chats.prepareRetry(OWNER, session, questionId("edited stack question"), 12).orElseThrow().command();
        assertThat(replaced.prompt()).isEqualTo("edited stack question");
        assertThat(replaced.thinkingEnabled()).isFalse();
        assertThat(replaced.reasoningEffort()).isEqualTo("low");
        assertThat(replaced.attachments()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT reasoning_content FROM chat_messages WHERE session_id = ? AND role = 'assistant' ORDER BY id DESC LIMIT 1",
            String.class, session)).isEqualTo("new reasoning");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file", "image"})
    void loadsAttachmentMetadataWithoutReadingTheFileAndHydratesOnlyForReplay(String type) throws Exception {
        byte[] image = new byte[256000];
        image[0] = (byte) 0x89;
        image[1] = 'P';
        image[2] = 'N';
        image[3] = 'G';
        ChatAttachment file = "image".equals(type)
            ? new ChatAttachment("large.png", "image", "image/png", "data:image/png;base64," + Base64.getEncoder().encodeToString(image))
            : new ChatAttachment("large.c", "file", "text/plain", "int top = -1;\n".repeat(20000));
        PendingChat pending = chats.savePending(OWNER,
            new ChatCommand("解释文件里的栈", null, "history-api-owned", List.of(), false, null, List.of(file)), null);
        String attachmentId = jdbc.queryForObject("SELECT id FROM chat_attachments WHERE message_id = ?",
            String.class, pending.messageId());
        ChatAttachment metadata = new ChatAttachment(file.name(), file.type(), file.mimeType(), "", null,
            "utf-8", (long) file.content().length(), attachmentId, "/api/v1/chat/attachments/" + attachmentId);
        assertThat(chats.hydrateAttachments(OWNER, List.of(metadata)).getFirst().content()).isEqualTo(file.content());
        assertThat(chats.prepareRetry(OWNER, pending.sessionId(), pending.messageId(), 12)
            .orElseThrow().command().attachments().getFirst().content()).isEqualTo(file.content());
        assertThatThrownBy(() -> chats.hydrateAttachments(OTHER, List.of(metadata)))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("CHAT_ATTACHMENT_NOT_FOUND"));

        String storageKey = jdbc.queryForObject("SELECT storage_key FROM chat_attachments WHERE id = ?",
            String.class, attachmentId);
        Path storedFile = attachmentDirectory.resolve(storageKey);
        Path parkedFile = storedFile.resolveSibling(storedFile.getFileName() + ".parked");
        java.nio.file.Files.move(storedFile, parkedFile);
        try {
            String response = mockMvc.perform(get("/api/v1/chat/sessions/history-api-owned")
                    .header("Authorization", "Bearer " + token(OWNER, "owner-history@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[1].attachments[0].content").value(""))
                .andExpect(jsonPath("$.messages[1].attachments[0].attachmentId").value(attachmentId))
                .andExpect(jsonPath("$.messages[1].attachments[0].downloadUrl").value(metadata.downloadUrl()))
                .andReturn().getResponse().getContentAsString();
            assertThat(response.length()).isLessThan(5000);
        } finally {
            java.nio.file.Files.move(parkedFile, storedFile);
        }
        mockMvc.perform(get(metadata.downloadUrl())
                .header("Authorization", "Bearer " + token(OWNER, "owner-history@example.com")))
            .andExpect(status().isOk())
            .andExpect(content().bytes("image".equals(type) ? image : file.content().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void concurrentAppendAndForeignReplacementPreserveTheExistingConversation() {
        chats.saveExchange(OWNER, "history-api-owned", null, "retry question", "old answer", List.of());
        ChatRetry retry = chats.prepareRetry(OWNER, "history-api-owned", questionId("retry question"), 12).orElseThrow();
        assertThatThrownBy(() -> chats.replaceExchange(OTHER, retry, "foreign answer", List.of(), null))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("CHAT_SESSION_NOT_FOUND"));
        chats.saveExchange(OWNER, "history-api-owned", null, "concurrent question", "concurrent answer", List.of());
        List<String> before = contents();
        assertThatThrownBy(() -> chats.replaceExchange(OWNER, retry, "new answer", List.of(), null))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("CHAT_RETRY_CONFLICT"));
        assertThat(contents()).isEqualTo(before);
    }

    @Test
    void failedReplacementInsertRollsBackTheDeletionAndPartialInsert() {
        chats.saveExchange(OWNER, "history-api-owned", null, "retry question", "old answer", List.of());
        ChatRetry retry = chats.prepareRetry(OWNER, "history-api-owned", questionId("retry question"), 12).orElseThrow();
        List<String> before = contents();
        assertThatThrownBy(() -> chats.replaceExchange(OWNER, retry, null, List.of(), null))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(contents()).isEqualTo(before);
    }

    private void assertRetryCommand(ChatCommand expected, ChatCommand actual) {
        assertThat(actual.prompt()).isEqualTo(expected.prompt());
        assertThat(actual.chapterId()).isEqualTo(expected.chapterId());
        assertThat(actual.sessionId()).isEqualTo(expected.sessionId());
        assertThat(actual.thinkingEnabled()).isEqualTo(expected.thinkingEnabled());
        assertThat(actual.reasoningEffort()).isEqualTo(expected.reasoningEffort());
        assertThat(actual.attachments()).hasSize(1);
        ChatAttachment expectedAttachment = expected.attachments().get(0);
        ChatAttachment actualAttachment = actual.attachments().get(0);
        assertThat(actualAttachment.name()).isEqualTo(expectedAttachment.name());
        assertThat(actualAttachment.type()).isEqualTo(expectedAttachment.type());
        assertThat(actualAttachment.mimeType()).isEqualTo(expectedAttachment.mimeType());
        assertThat(actualAttachment.content()).isEqualTo(expectedAttachment.content());
        assertThat(actualAttachment.encoding()).isEqualTo(expectedAttachment.encoding());
        assertThat(actualAttachment.byteSize()).isEqualTo((long) expectedAttachment.content().getBytes(StandardCharsets.UTF_8).length);
        assertThat(actualAttachment.attachmentId()).isNotBlank();
        assertThat(actualAttachment.downloadUrl()).isEqualTo("/api/v1/chat/attachments/" + actualAttachment.attachmentId());
    }

    private long questionId(String content) {
        return jdbc.queryForObject("SELECT id FROM chat_messages WHERE session_id = 'history-api-owned' AND role = 'user' AND content = ?",
            Long.class, content);
    }

    private List<String> contents() {
        return jdbc.queryForList("SELECT content FROM chat_messages WHERE session_id = 'history-api-owned' ORDER BY id", String.class);
    }

    private String token(long userId, String email) {
        return tokens.issue(userId, email, Set.of("STUDENT"));
    }
}
