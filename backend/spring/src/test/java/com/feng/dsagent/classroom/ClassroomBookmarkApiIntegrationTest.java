package com.feng.dsagent.classroom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.feng.dsagent.security.JwtTokenService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A learner's saved steps, over HTTP: who may see them, what the server decides a step is, and what happens
 * when the step or the session is not theirs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClassroomBookmarkApiIntegrationTest {

    private static final long USER = 8941L;
    private static final long OTHER = 8942L;
    private static final String LESSON_TITLE = "栈与队列基础";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenService tokens;
    @Autowired private ClassroomRepository repository;

    private String token;
    private String otherToken;
    private String sessionId;

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO users (id,email,password_hash) VALUES (?,?, 'hash')", USER, "bookmark@example.com");
        jdbc.update("INSERT INTO users (id,email,password_hash) VALUES (?,?, 'hash')", OTHER, "other-bookmark@example.com");
        jdbc.update("""
            INSERT INTO classroom_scripts (id,chapter_id,title,review_status,script_json) VALUES
            (?, '03-stack-queue', ?, 'PUBLISHED', ?)
            """, "bookmark-test", LESSON_TITLE, """
            {"lessonId":"lesson-bookmark","title":"栈与队列基础","steps":[
            {"type":"explain","role":"teacher","content":"栈只在顶端进出，最后放进去的元素最先出来。"},
            {"type":"question","prompt":"入栈顺序是 A B C，出栈会是什么？","expected":["C B A"]}]}
            """);
        sessionId = openSession(USER);
        token = tokens.issue(USER, "bookmark@example.com", Set.of("STUDENT"));
        otherToken = tokens.issue(OTHER, "other-bookmark@example.com", Set.of("STUDENT"));
    }

    private String openSession(long userId) {
        ClassroomScript script = repository.findPublishedScript("bookmark-test").orElseThrow();
        return repository.createSession(userId, script, new ClassroomStatus(ClassroomState.OPENING, false)).id();
    }

    private String saveBody(String session, int stepIndex) {
        return "{\"sessionId\":\"" + session + "\",\"stepIndex\":" + stepIndex + "}";
    }

    @Test
    void savesTheStepAsTheServerSeesIt() throws Exception {
        mockMvc.perform(post("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(saveBody(sessionId, 0)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andExpect(jsonPath("$.lessonTitle").value(LESSON_TITLE))
            .andExpect(jsonPath("$.chapterId").value("03-stack-queue"))
            .andExpect(jsonPath("$.stepIndex").value(0))
            // The lesson has no courseware under this test's id, so the step is described by its own words.
            .andExpect(jsonPath("$.title").value("栈只在顶端进出，最后放进去的元素最先出来。"));

        mockMvc.perform(get("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].stepIndex").value(0));
    }

    @Test
    void savingTheSameStepTwiceKeepsOneBookmark() throws Exception {
        String first = mockMvc.perform(post("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(saveBody(sessionId, 1)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(saveBody(sessionId, 1)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(idOf(second)).isEqualTo(idOf(first));
        mockMvc.perform(get("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listsOnlyTheCallersOwnBookmarks() throws Exception {
        jdbc.update("""
            INSERT INTO classroom_bookmarks (user_id, session_id, lesson_id, step_index, title)
            VALUES (?, ?, 'someone-else', 0, '别人的收藏')
            """, OTHER, openSession(OTHER));

        mockMvc.perform(post("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(saveBody(sessionId, 0)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].lessonId").value("lesson-bookmark"));
    }

    @Test
    void refusesAStepThatIsNotInTheLesson() throws Exception {
        mockMvc.perform(post("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(saveBody(sessionId, 9)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CLASSROOM_STEP_OUT_OF_RANGE"));
    }

    @Test
    void refusesALessonTheCallerIsNotIn() throws Exception {
        String foreignSession = openSession(OTHER);
        mockMvc.perform(post("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(saveBody(foreignSession, 0)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CLASSROOM_SESSION_NOT_FOUND"));
    }

    @Test
    void deletesOnlyItsOwnBookmark() throws Exception {
        String mine = mockMvc.perform(post("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(saveBody(sessionId, 0)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        jdbc.update("""
            INSERT INTO classroom_bookmarks (user_id, session_id, lesson_id, step_index, title)
            VALUES (?, ?, 'someone-else', 0, '别人的收藏')
            """, OTHER, openSession(OTHER));
        long theirs = jdbc.queryForObject("SELECT id FROM classroom_bookmarks WHERE user_id = ?", Long.class, OTHER);

        mockMvc.perform(delete("/api/v1/users/bookmarks/" + theirs).header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/users/bookmarks/" + idOf(mine)).header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/bookmarks").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.length()").value(0));
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM classroom_bookmarks WHERE user_id = ?", Integer.class, OTHER))
            .isEqualTo(1);
    }

    @Test
    void requiresALogin() throws Exception {
        mockMvc.perform(post("/api/v1/users/bookmarks").contentType(MediaType.APPLICATION_JSON)
                .content(saveBody(sessionId, 0)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/bookmarks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/bookmarks").header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isOk());
    }

    private static long idOf(String json) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("no bookmark id in " + json);
        }
        return Long.parseLong(matcher.group(1));
    }
}
