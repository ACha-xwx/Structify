package com.feng.dsagent.knowledge;

import com.feng.dsagent.knowledge.service.KnowledgeSearchService;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.feng.dsagent.classroom.ClassroomPreparation;
import com.feng.dsagent.presentation.service.PresentationService;

/** Local end-to-end resource check. Private original assets are intentionally absent in public CI. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:original-release;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("dev")
class OriginalCoursewareIntegrationTest {
    @Autowired KnowledgeCorpus corpus;
    @Autowired KnowledgeSearchService search;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClassroomPreparation classroom;
    @Autowired PresentationService presentations;

    @Test void originalReleaseIsSearchableAndEveryLessonHasItsRealWebpPages() {
        Assumptions.assumeTrue(Files.isRegularFile(Path.of("../../知识库/转换清单.json")));
        assertThat(corpus.stats().rejections()).isEmpty();
        assertThat(corpus.stats().lessonFiles()).isEqualTo(22);
        assertThat(corpus.chunks()).hasSizeGreaterThanOrEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunks WHERE source_path LIKE 'dev-mock/%'", Integer.class)).isZero();
        assertThat(search.search("栈的入栈操作", "03-stack-queue", 4, KnowledgeAudience.STUDENT))
            .isNotEmpty().allSatisfy(result -> {
                assertThat(result.chunk().source()).startsWith("courseware/");
                assertThat(result.chunk().pageLabel()).startsWith("PPT第 ");
                assertThat(result.chunk().content()).doesNotContain("开发模拟", "查看 WebP 原图");
            });
        assertThat(presentations.meta().slideCount()).isEqualTo(1210);
        assertThat(presentations.decks()).hasSize(22);
        var lessons = classroom.lessons(null);
        assertThat(lessons).hasSize(22);
        int pages = 0;
        for (var lesson : lessons) {
            var courseware = presentations.forLesson(lesson.id());
            assertThat(courseware.slides()).isNotEmpty();
            for (var slide : courseware.slides()) {
                assertThat(presentations.imageFile(slide.id()).toString()).endsWith(".webp");
                assertThat(slide.rawText()).doesNotContain("查看 WebP 原图");
            }
            pages += courseware.slides().size();
        }
        assertThat(pages).isEqualTo(1210);
        assertThat(classroom.lessons("04-string")).isEmpty();
        assertThat(classroom.lessons("10-external-sort")).isEmpty();
    }
}
