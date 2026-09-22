package com.feng.dsagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * An anonymous caller must be refused the same way on both chat entry points, and must never be able
 * to provoke a server error.
 *
 * <p>The production smoke found the streaming endpoint doing neither: the refusal was thrown as an
 * {@code ApiException} that the advice could not render for a client accepting only
 * {@code text/event-stream}, so it escaped the servlet, the container logged an ERROR stack trace per
 * logged-out attempt, and the browser saw the entry point's {@code AUTH_REQUIRED} instead of the
 * documented quota code. Asking for JSON from the same endpoint answered {@code 500 INTERNAL_ERROR}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatStreamUnauthenticatedContractTest {

    private static final String BODY = "{\"prompt\":\"什么是栈\",\"chapterId\":\"03-stack-queue\",\"history\":[]}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void completeRefusesAnonymousCallers() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AI_QUOTA_AUTHENTICATION_REQUIRED"));
    }

    /**
     * The browser client asks for {@code text/event-stream} and nothing else, so this is the shape of
     * every logged-out streaming attempt. MockMvc rethrows an exception that escapes the servlet,
     * which means simply reaching the assertions proves the refusal now stays inside the API.
     */
    @Test
    void streamRefusesAnonymousSseClientsWithTheDocumentedEnvelope() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content(BODY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AI_QUOTA_AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void streamAnswersAClientErrorWhenTheClientCannotAcceptSse() throws Exception {
        mockMvc.perform(post("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isNotAcceptable())
            .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }
}
