package com.feng.dsagent.studyroom.events;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.studyroom.events.service.ClassroomEventService;

import com.feng.dsagent.security.service.JwtTokenService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ClassroomEventApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenService tokens;
    @Autowired ClassroomEventService events;
    static final long USER=97401, OTHER=97402;
    String auth(long user) { return "Bearer " + tokens.issue(user,"events-api@example.com",Set.of("USER")); }
    @BeforeEach void prepare() {
        db.update("DELETE FROM users WHERE id IN (?,?)",USER,OTHER);
        db.update("INSERT INTO users(id,email,password_hash) VALUES(?, 'events-api@example.com','hash')",USER);
        db.update("INSERT INTO users(id,email,password_hash) VALUES(?, 'events-other@example.com','hash')",OTHER);
        db.update("INSERT INTO study_outline_drafts(id,user_id,question,depth,status) VALUES('events-api',?,'q','STANDARD','GENERATING')",USER);
        events.open(USER,"outline:events-api","OUTLINE","events-api",null,"operation",1,null,1,Map.of("phase","QUEUED"));
    }
    @Test void ownershipDeletionAndCursorAreCheckedBeforeSubscription() throws Exception {
        String url="/api/v1/study-outlines/events-api/events";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization",auth(OTHER))).andExpect(status().isNotFound());
        mvc.perform(get(url).param("since","-1").header("Authorization",auth(USER))).andExpect(status().isBadRequest());
        db.update("UPDATE study_outline_drafts SET deleted_at=CURRENT_TIMESTAMP WHERE id='events-api'");
        mvc.perform(get(url).header("Authorization",auth(USER))).andExpect(status().isNotFound());
    }
    @Test void terminalSubscriptionResetsOutOfRangeCursorWithoutStartingGeneration() throws Exception {
        events.append(USER,"outline:events-api","operation",1,"failed",Map.of("code","TEST"),Map.of("phase","VALIDATING"),true);
        var result=mvc.perform(get("/api/v1/study-outlines/events-api/events").param("since","99").header("Authorization",auth(USER)))
            .andExpect(status().isOk()).andReturn();
        result.getAsyncResult(3000);
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        String body=result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("event:reset"));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"cursor\":1"));
        org.junit.jupiter.api.Assertions.assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM studyroom_generation_jobs WHERE draft_id='events-api'",Integer.class));
    }

    @Test void activeSubscriptionsAreCappedAndReleasedWhenTheStreamTerminates() throws Exception {
        String url="/api/v1/study-outlines/events-api/events";
        List<MvcResult> subscriptions = new ArrayList<>();
        for (int index=0; index<4; index++) {
            var result = mvc.perform(get(url).header("Authorization",auth(USER)))
                .andExpect(status().isOk()).andReturn();
            org.junit.jupiter.api.Assertions.assertTrue(result.getRequest().isAsyncStarted());
            subscriptions.add(result);
        }

        mvc.perform(get(url).header("Authorization",auth(USER)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("CLASSROOM_EVENT_SUBSCRIPTIONS_LIMITED"));

        events.append(USER,"outline:events-api","operation",1,"failed",Map.of("code","TEST"),Map.of("phase","FAILED"),true);
        for (var subscription : subscriptions) {
            mvc.perform(asyncDispatch(subscription)).andExpect(status().isOk());
        }

        var reopened = mvc.perform(get(url).header("Authorization",auth(USER)))
            .andExpect(status().isOk()).andReturn();
        mvc.perform(asyncDispatch(reopened)).andExpect(status().isOk());
    }

    @Test void subscriptionPermitsEnforceGlobalAndPerUserCapsAndReleaseIdempotently() {
        var globalLimiter = new ClassroomEventSubscriptions.SubscriptionLimiter(2,2);
        var first = globalLimiter.acquire(USER);
        var second = globalLimiter.acquire(OTHER);
        var globalLimit = org.junit.jupiter.api.Assertions.assertThrows(
            com.feng.dsagent.common.ApiException.class,
            () -> globalLimiter.acquire(OTHER+1)
        );
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, globalLimit.status());

        first.close();
        first.close();
        try (var replacement = globalLimiter.acquire(USER)) {
            org.junit.jupiter.api.Assertions.assertThrows(
                com.feng.dsagent.common.ApiException.class,
                () -> globalLimiter.acquire(OTHER+1)
            );
        }
        second.close();

        var userLimiter = new ClassroomEventSubscriptions.SubscriptionLimiter(3,1);
        var userPermit = userLimiter.acquire(USER);
        var userLimit = org.junit.jupiter.api.Assertions.assertThrows(
            com.feng.dsagent.common.ApiException.class,
            () -> userLimiter.acquire(USER)
        );
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, userLimit.status());

        try (var other = userLimiter.acquire(OTHER)) {
            userPermit.close();
            userPermit.close();
            try (var replacement = userLimiter.acquire(USER)) {
                org.junit.jupiter.api.Assertions.assertThrows(
                    com.feng.dsagent.common.ApiException.class,
                    () -> userLimiter.acquire(USER)
                );
            }
            org.junit.jupiter.api.Assertions.assertThrows(
                com.feng.dsagent.common.ApiException.class,
                () -> userLimiter.acquire(OTHER)
            );
        }
    }
}
