package com.feng.dsagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class KnowledgeCorpusLoaderTest {

    private static final String REVIEWED_LABEL = "> OCR质量：已对照原始教材 PDF 核验（2026-09-16）";
    private static final String PENDING_LABEL = "> OCR质量：大模型初审需核（公式、代码、图表或局部文本须回看原始 PDF）";

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void publishesOnlyThePagesListedInTheImportManifest() throws Exception {
        String lesson = """
            # 课时编号：02-03
            # 课时标题：线性表-单链表及基本运算

            ## 1. 来源信息
            - 教材页码：第 35-42 页

            ## 3. 教材原文整理

            ### 教材页 35（PDF页 46）

            %s

            单链表头插法先让新结点的 next 指向原头结点，再更新头指针。

            ### 教材页 36（PDF页 47）

            %s

            如果先覆盖头指针，可能失去原链表的入口。操作顺序必须保持可达性。
            """.formatted(REVIEWED_LABEL, REVIEWED_LABEL);
        writeLesson("02-03-线性表-单链表.md", lesson);
        writeManifest(Map.of("02-03-线性表-单链表.md", entry("lessons/02-03-线性表-单链表.md", "第 35–36 页", 35, 46, 36, 47)));

        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 90);

        assertThat(corpus.stats().available()).isTrue();
        assertThat(corpus.stats().lessonFiles()).isEqualTo(1);
        assertThat(corpus.stats().rejections()).isEmpty();
        assertThat(corpus.chunks()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(corpus.chunks()).allSatisfy(chunk -> {
            assertThat(chunk.chapterId()).isEqualTo("02-linear-list");
            assertThat(chunk.title()).isEqualTo("线性表-单链表及基本运算");
            assertThat(chunk.source()).isEqualTo("textbook/lessons/02-03-线性表-单链表.md");
            assertThat(chunk.pageLabel()).matches("第 (35|36) 页");
            assertThat(chunk.licenseScope()).isEqualTo("CLASSROOM_ONLY");
        });
        // Chunks are page-scoped: each one names the single page it came from, so a classroom step can
        // cite the exact page it drew on instead of the whole lesson range.
        assertThat(corpus.chunks()).extracting(KnowledgeChunk::pageLabel)
            .contains("第 35 页", "第 36 页");
        assertThat(corpus.chunks()).anySatisfy(chunk -> assertThat(chunk.content()).contains("头插法"));
    }

    @Test
    void keepsAnUnlistedLessonUnpublishedAndReportsTheReason() throws Exception {
        writeLesson("02-03-线性表-单链表.md", reviewedLesson("02-03", "线性表-单链表及基本运算", 35, 46, 36, 47));
        writeManifest(Map.of());

        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 90);

        assertThat(corpus.chunks()).isEmpty();
        assertThat(corpus.stats().available()).isFalse();
        assertThat(corpus.stats().rejections())
            .singleElement()
            .satisfies(rejection -> {
                assertThat(rejection.fileName()).isEqualTo("02-03-线性表-单链表.md");
                assertThat(rejection.reason()).isEqualTo("NOT_LISTED_IN_IMPORT_MANIFEST");
            });
    }

    @Test
    void refusesALessonWhoseBytesNoLongerMatchTheRecordedHash() throws Exception {
        writeLesson("02-03-线性表-单链表.md", reviewedLesson("02-03", "线性表-单链表及基本运算", 35, 46, 36, 47));
        Map<String, Map<String, Object>> accepted = new LinkedHashMap<>();
        accepted.put("02-03-线性表-单链表.md", entry("lessons/02-03-线性表-单链表.md", "第 35–36 页", 35, 46, 36, 47));
        accepted.get("02-03-线性表-单链表.md").put("targetSha256", "0".repeat(64));
        writeManifest(accepted);

        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 90);

        assertThat(corpus.chunks()).isEmpty();
        assertThat(corpus.stats().rejections())
            .extracting(KnowledgeImportManifest.Rejection::reason)
            .contains("TARGET_HASH_MISMATCH");
    }

    @Test
    void refusesALessonThatDropsAPageTheManifestStillClaims() throws Exception {
        // Only page 35 is present even though the manifest approved 35 and 36.
        writeLesson("02-03-线性表-单链表.md", """
            # 课时编号：02-03
            # 课时标题：线性表-单链表及基本运算

            ### 教材页 35（PDF页 46）

            %s

            单链表头插法先让新结点的 next 指向原头结点。
            """.formatted(REVIEWED_LABEL));
        writeManifest(Map.of("02-03-线性表-单链表.md", entry("lessons/02-03-线性表-单链表.md", "第 35–36 页", 35, 46, 36, 47)));

        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 90);

        assertThat(corpus.chunks()).isEmpty();
        assertThat(corpus.stats().rejections())
            .extracting(KnowledgeImportManifest.Rejection::reason)
            .contains("PAGE_SET_MISMATCH");
    }

    @Test
    void refusesALessonWhosePdfPageContradictsTheManifest() throws Exception {
        writeLesson("02-03-线性表-单链表.md", reviewedLesson("02-03", "线性表-单链表及基本运算", 35, 99, 36, 47));
        writeManifest(Map.of("02-03-线性表-单链表.md", entry("lessons/02-03-线性表-单链表.md", "第 35–36 页", 35, 46, 36, 47)));

        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 90);

        assertThat(corpus.chunks()).isEmpty();
        assertThat(corpus.stats().rejections())
            .extracting(KnowledgeImportManifest.Rejection::reason)
            .contains("PAGE_35_PDF_PAGE_MISMATCH");
    }

    @Test
    void refusesAPageThatStillCarriesAnUnreviewedOcrLabel() throws Exception {
        writeLesson("02-03-线性表-单链表.md", """
            # 课时编号：02-03
            # 课时标题：线性表-单链表及基本运算

            ### 教材页 35（PDF页 46）

            %s

            单链表头插法先让新结点的 next 指向原头结点。

            ### 教材页 36（PDF页 47）

            %s

            如果先覆盖头指针，可能失去原链表的入口。
            """.formatted(REVIEWED_LABEL, PENDING_LABEL));
        writeManifest(Map.of("02-03-线性表-单链表.md", entry("lessons/02-03-线性表-单链表.md", "第 35–36 页", 35, 46, 36, 47)));

        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 90);

        assertThat(corpus.chunks()).isEmpty();
        assertThat(corpus.stats().rejections())
            .extracting(KnowledgeImportManifest.Rejection::reason)
            .contains("PAGE_36_LABEL_NOT_REVIEWED");
    }

    @Test
    void refusesALessonWhenTheManifestIsMissingEntirely() throws Exception {
        writeLesson("02-03-线性表-单链表.md", reviewedLesson("02-03", "线性表-单链表及基本运算", 35, 46, 36, 47));

        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 90);

        assertThat(corpus.chunks()).isEmpty();
        assertThat(corpus.stats().available()).isFalse();
        assertThat(corpus.stats().rejections())
            .extracting(KnowledgeImportManifest.Rejection::reason)
            .containsExactly("IMPORT_MANIFEST_MISSING");
    }

    @Test
    void stillLoadsTheApprovedLessonsWhenAnotherLessonInTheSameDirectoryIsRejected() throws Exception {
        writeLesson("02-03-线性表-单链表.md", reviewedLesson("02-03", "线性表-单链表及基本运算", 35, 46, 36, 47));
        writeLesson("06-03-树与二叉树-遍历.md", reviewedLesson("06-03", "树与二叉树-遍历", 168, 179, 169, 180));
        writeManifest(Map.of("02-03-线性表-单链表.md", entry("lessons/02-03-线性表-单链表.md", "第 35–36 页", 35, 46, 36, 47)));

        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 400);

        assertThat(corpus.stats().lessonFiles()).isEqualTo(1);
        assertThat(corpus.chunks()).allSatisfy(chunk -> assertThat(chunk.chapterId()).isEqualTo("02-linear-list"));
        assertThat(corpus.stats().rejections())
            .extracting(KnowledgeImportManifest.Rejection::fileName)
            .containsExactly("06-03-树与二叉树-遍历.md");
    }

    @Test
    void ignoresTheLessonIndexAndKeepsChunkIdentityStableAcrossLoads() throws Exception {
        Path lessons = Files.createDirectories(temporaryDirectory.resolve("lessons"));
        Files.writeString(lessons.resolve("00-lesson-index.md"), "# 目录\n不应进入知识库", StandardCharsets.UTF_8);
        writeLesson("02-03-线性表-单链表.md", reviewedLesson("02-03", "线性表-单链表及基本运算", 35, 46, 36, 47));
        writeManifest(Map.of("02-03-线性表-单链表.md", entry("lessons/02-03-线性表-单链表.md", "第 35–36 页", 35, 46, 36, 47)));

        KnowledgeCorpus first = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 400);
        KnowledgeCorpus second = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 400);

        assertThat(first.stats().lessonFiles()).isEqualTo(1);
        assertThat(first.chunks()).extracting(KnowledgeChunk::id)
            .isEqualTo(second.chunks().stream().map(KnowledgeChunk::id).toList());
    }

    @Test
    void returnsAnEmptyCorpusWhenPrivateMaterialIsUnavailable() {
        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory.resolve("missing"), 200);

        assertThat(corpus.chunks()).isEmpty();
        assertThat(corpus.stats().lessonFiles()).isZero();
        assertThat(corpus.stats().available()).isFalse();
    }

    @Test
    void treatsAnEmptyLessonsDirectoryAsUnavailableSoPersistedKnowledgeIsPreserved() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("lessons"));

        KnowledgeCorpus corpus = new KnowledgeCorpusLoader(mapper).load(temporaryDirectory, 200);

        assertThat(corpus.chunks()).isEmpty();
        assertThat(corpus.stats().available()).isFalse();
    }

    @Test
    void pageBlocksKeepTheirExactTextSoLaterReviewPagesCanBeAdded() {
        String lesson = reviewedLesson("02-03", "线性表-单链表及基本运算", 35, 46, 36, 47);

        List<KnowledgeImportManifest.LessonPageBlock> blocks = KnowledgeCorpusLoader.pageBlocks(lesson);

        assertThat(blocks).extracting(KnowledgeImportManifest.LessonPageBlock::page).containsExactly(35, 36);
        assertThat(blocks).extracting(KnowledgeImportManifest.LessonPageBlock::pdfPage).containsExactly(46, 47);
        assertThat(blocks.get(0).label()).isEqualTo(REVIEWED_LABEL);
        assertThat(blocks.get(1).text())
            .startsWith("### 教材页 36（PDF页 47）")
            .contains("第 36 页的核验正文");
    }

    private void writeLesson(String name, String body) throws Exception {
        Path lessons = Files.createDirectories(temporaryDirectory.resolve("lessons"));
        Files.writeString(lessons.resolve(name), body, StandardCharsets.UTF_8);
    }

    private void writeManifest(Map<String, Map<String, Object>> accepted) throws Exception {
        StringBuilder json = new StringBuilder("{\"version\":2,\"accepted\":[");
        boolean firstEntry = true;
        for (Map<String, Object> entry : accepted.values()) {
            if (!firstEntry) {
                json.append(',');
            }
            firstEntry = false;
            json.append('{');
            json.append("\"source\":\"").append(entry.get("source")).append("\",");
            json.append("\"targetSha256\":\"").append(entry.get("targetSha256")).append("\",");
            json.append("\"target\":\"").append(entry.get("target")).append("\",");
            json.append("\"pageRange\":\"").append(entry.get("pageRange")).append("\",");
            json.append("\"pages\":[");
            @SuppressWarnings("unchecked")
            Map<Integer, Integer> pages = (Map<Integer, Integer>) entry.get("pages");
            boolean firstPage = true;
            for (Map.Entry<Integer, Integer> page : pages.entrySet()) {
                if (!firstPage) {
                    json.append(',');
                }
                firstPage = false;
                json.append("{\"page\":").append(page.getKey()).append(",\"pdfPage\":").append(page.getValue()).append('}');
            }
            json.append("]}");
        }
        json.append("]}");
        Files.writeString(temporaryDirectory.resolve("import-manifest.json"), json.toString(), StandardCharsets.UTF_8);
    }

    private Map<String, Object> entry(String target, String pageRange, int... pagePairs) throws Exception {
        String fileName = target.substring(target.lastIndexOf('/') + 1);
        byte[] bytes = Files.readAllBytes(temporaryDirectory.resolve("lessons").resolve(fileName));
        Map<Integer, Integer> pages = new LinkedHashMap<>();
        for (int index = 0; index < pagePairs.length; index += 2) {
            pages.put(pagePairs[index], pagePairs[index + 1]);
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("source", fileName);
        entry.put("targetSha256", KnowledgeCorpusLoader.sha256(bytes));
        entry.put("target", target);
        entry.put("pageRange", pageRange);
        entry.put("pages", pages);
        return entry;
    }

    private static String reviewedLesson(String lessonId, String title, int... pagePairs) {
        StringBuilder body = new StringBuilder()
            .append("# 课时编号：").append(lessonId).append('\n')
            .append("# 课时标题：").append(title).append("\n\n")
            .append("## 1. 来源信息\n- 教材页码：见各页块\n\n")
            .append("## 3. 教材原文整理\n\n");
        for (int index = 0; index < pagePairs.length; index += 2) {
            int page = pagePairs[index];
            int pdfPage = pagePairs[index + 1];
            body.append("### 教材页 ").append(page).append("（PDF页 ").append(pdfPage).append("）\n\n")
                .append(REVIEWED_LABEL).append("\n\n")
                .append("第 ").append(page).append(" 页的核验正文，用于分块与页码断言。\n\n");
        }
        return body.toString();
    }
}
