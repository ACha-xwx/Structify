package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.feng.dsagent.common.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class ClassroomCodeControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ClassroomCodeController controller = new ClassroomCodeController(
            new ClassroomCodeCatalog(objectMapper),
            new TextbookCodeLibrary(objectMapper)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void servesTheTextbookLibraryForLessonsWithoutASample() throws Exception {
        mockMvc.perform(get("/api/v1/code/library"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fragmentCount").value(99))
            .andExpect(jsonPath("$.chapters.length()").value(8))
            .andExpect(jsonPath("$.chapters[0].title").value("第 2 章 线性表"))
            .andExpect(jsonPath("$.chapters[0].fragments[0].code").isNotEmpty())
            // Every listing ships with the runnable example built around it, output included.
            .andExpect(jsonPath("$.chapters[0].fragments[0].example.code").isNotEmpty())
            .andExpect(jsonPath("$.chapters[0].fragments[0].example.expectedStdout").isNotEmpty());
    }

    @Test
    void listsEverySampleWhenNoLessonIsRequested() throws Exception {
        mockMvc.perform(get("/api/v1/code/samples"))
            .andExpect(status().isOk())
            // One lesson used to be all there was; the map now carries most of the book.
            .andExpect(jsonPath("$.sampleCount").value(greaterThan(100)))
            .andExpect(jsonPath("$.lessons[0].coursewareKey").value("02-01"))
            .andExpect(jsonPath("$.lessons[0].samples[0].id").value("02-01-s1"))
            .andExpect(jsonPath("$.lessons[0].samples[0].code").isNotEmpty())
            .andExpect(jsonPath("$.lessons[0].samples[0].expectedStdout").isNotEmpty());
    }

    @Test
    void filtersToOneLesson() throws Exception {
        mockMvc.perform(get("/api/v1/code/samples").param("coursewareKey", "03-01"))
            .andExpect(status().isOk())
            // Four hand-written pilots plus the storage variants the map adds.
            .andExpect(jsonPath("$.sampleCount").value(9))
            .andExpect(jsonPath("$.lessons.length()").value(1));
    }

    @Test
    void reportsAMissingLessonInsteadOfAnEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/code/samples").param("coursewareKey", "99-99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CODE_SAMPLES_LESSON_UNKNOWN"));
    }

    @Test
    void keepsTheSampleRouteAnonymous() throws Exception {
        assertThat(ClassroomCodeController.class.getAnnotations()).isNotEmpty();
        mockMvc.perform(get("/api/v1/code/samples").param("coursewareKey", "03-01"))
            .andExpect(status().isOk());
    }
}
