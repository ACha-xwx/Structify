package com.feng.dsagent.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A cache between the browser and the origin fills itself with no credentials at all, so the signed page
 * url has to be reachable without a session - while the session-only path beside it stays behind one.
 * These two rules are the whole point of the signed form, and both live in the security chain, which is
 * exactly the kind of wiring that breaks silently.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SlideImageAccessIntegrationTest {

    private static final String SLIDE = "ch03-deck01-3caa7e8-s003";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aSignedUrlShapeReachesTheHandlerWithoutASession() throws Exception {
        // 403 rather than 401 is the assertion: the chain let the request through to the handler, which
        // then refused the signature. A cache-fill request has to get that far or nothing can be stored.
        mockMvc.perform(get("/api/v1/presentation/slides/" + SLIDE + "/0000000000000000000000.png"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/presentation/slides/" + SLIDE + "/0000000000000000000000.webp"))
            .andExpect(status().isForbidden());
    }

    @Test
    void theSessionOnlyPathStillNeedsASession() throws Exception {
        mockMvc.perform(get("/api/v1/presentation/slides/" + SLIDE + "/image"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void aPageWithoutAPathVariableSignatureIsNotAPageEither() throws Exception {
        mockMvc.perform(get("/api/v1/presentation/slides/" + SLIDE + "/image.png"))
            .andExpect(status().isForbidden());
    }
}
