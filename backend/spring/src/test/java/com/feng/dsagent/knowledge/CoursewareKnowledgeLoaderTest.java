package com.feng.dsagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class CoursewareKnowledgeLoaderTest {
    @TempDir Path root;

    @Test void loadsTheNewStringChapter() throws Exception {
        Files.writeString(root.resolve("串.md"), "## 第 1 页：串\n串是有限字符序列。\n");
        Files.writeString(root.resolve("转换清单.json"), """
            {"decks":[{"chapter":"第四章串","source":"第四章串/串.pptx","slides":1,"markdown":"串.md"}]}
            """);
        var corpus = new KnowledgeCorpusLoader(JsonMapper.builder().build()).load(root, 1100);
        assertThat(corpus.stats().available()).isTrue();
        assertThat(corpus.chunks()).hasSize(1);
        assertThat(corpus.chunks().getFirst().chapterId()).isEqualTo("04-string");
    }

    @Test void loadsOriginalPagesWithSlideProvenanceInsteadOfMockText() throws Exception {
        Files.createDirectories(root.resolve("课件/第三章限定性线性表"));
        Files.writeString(root.resolve("课件/第三章限定性线性表/栈01.md"), """
            # 栈01
            <a id="slide-001"></a>
            ## 第 1 页：3.1 栈
            栈遵循后进先出。
            [查看 WebP 原图](../../../WebP/栈01/slide-001.webp)
            ![第 1 页](../../../WebP/栈01/slide-001.webp)
            <a id="slide-002"></a>
            ## 第 2 页：入栈
            入栈操作修改栈顶指针。
            """);
        Files.writeString(root.resolve("转换清单.json"), """
            {"ppt_count":1,"slide_count":2,"decks":[{"chapter":"第三章限定性线性表","source":"第三章限定性线性表/栈01.pptx","slides":2,"markdown":"知识库/课件/第三章限定性线性表/栈01.md"}]}
            """);
        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(JsonMapper.builder().build()).load(root, 1100);
        assertThat(corpus.stats().available()).isTrue();
        assertThat(corpus.chunks()).hasSize(2).allSatisfy(chunk -> {
            assertThat(chunk.chapterId()).isEqualTo("03-stack-queue");
            assertThat(chunk.source()).isEqualTo("courseware/lessons/03-01-栈01.md");
            assertThat(chunk.pageLabel()).startsWith("PPT第 ");
            assertThat(chunk.content()).doesNotContain("WebP", "开发模拟", "<a id");
        });
        assertThat(corpus.chunks().getFirst().title()).contains("3.1 栈");
    }

    @Test void refusesIncompleteDeckInsteadOfPublishingPartialEvidence() throws Exception {
        Files.createDirectories(root.resolve("课件/第三章限定性线性表"));
        Files.writeString(root.resolve("课件/第三章限定性线性表/栈01.md"), "## 第 1 页：栈\n栈遵循后进先出。");
        Files.writeString(root.resolve("转换清单.json"), """
            {"decks":[{"chapter":"第三章限定性线性表","source":"第三章限定性线性表/栈01.pptx","slides":2,"markdown":"知识库/课件/第三章限定性线性表/栈01.md"}]}
            """);
        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(JsonMapper.builder().build()).load(root, 1100);
        assertThat(corpus.chunks()).isEmpty();
        assertThat(corpus.stats().rejections()).isNotEmpty();
    }
}
