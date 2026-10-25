package com.feng.dsagent.admin;

import com.feng.dsagent.admin.service.AdminKnowledgeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.knowledge.KnowledgeChunk;
import com.feng.dsagent.knowledge.service.KnowledgeSearchService;
import com.feng.dsagent.knowledge.service.KnowledgeSettingsService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The knowledge console, end to end at service level: the tuning is a single row that overrides the
 * configuration defaults, and chunk maintenance is audited and gated by the same eligibility chain the
 * retrieval path uses. The most valuable promises pinned down here: a manual chunk lands under {@code admin/}
 * (so the next textbook import cannot wipe it), a rejected chunk immediately reads as not retrievable, and
 * the test-search preview honours exactly the tuning it is given.
 */
@SpringBootTest
@Transactional
class AdminKnowledgeServiceTest {

    @Autowired
    private AdminKnowledgeService service;

    @Autowired
    private KnowledgeSettingsService settings;

    @Autowired
    private KnowledgeSearchService search;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;
    private String chapterId;

    @BeforeEach
    void seed() {
        jdbc.update(
            "INSERT INTO users (email, password_hash, username, status) VALUES (?, ?, ?, 'ACTIVE')",
            "kb-admin-" + UUID.randomUUID() + "@example.test", "x", "kb-admin"
        );
        adminId = jdbc.queryForObject("SELECT MAX(id) FROM users", Long.class);
        chapterId = "ch-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO chapters (id, chapter_number, title, status) VALUES (?, ?, ?, 'PUBLISHED')",
            chapterId, Math.abs(UUID.randomUUID().hashCode() % 1_000_000_000), "测试章节"
        );
    }

    @AfterEach
    void clearSnapshot() {
        // The test profile starts with an empty snapshot; leave it that way for the next class.
        search.replace(List.of());
    }

    private long auditRows(String action, String targetId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_events WHERE action = ? AND target_id = ? AND actor_user_id = ?",
            Long.class, action, targetId, adminId
        );
    }

    // ----- settings ---------------------------------------------------------------

    @Test
    @DisplayName("with no saved row the configuration defaults are reported as the settings in effect")
    void defaultsUntilSaved() {
        AdminKnowledgeService.KnowledgeSettingsView view = service.settingsView();

        assertThat(view.customized()).isFalse();
        assertThat(view.minimumScore()).isEqualTo(view.defaults().minimumScore());
        assertThat(view.searchLimit()).isEqualTo(view.defaults().searchLimit());
        assertThat(view.titleWeightLong()).isEqualTo(5.0);
        assertThat(view.titleWeightShort()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("saving writes the single row, is audited, and a reset returns to the defaults")
    void savesAndResets() {
        AdminKnowledgeService.KnowledgeSettingsView saved = service.saveSettings(12.5, 8, 7.0, 2.0, adminId, "req-save");

        assertThat(saved.customized()).isTrue();
        assertThat(saved.minimumScore()).isEqualTo(12.5);
        assertThat(saved.searchLimit()).isEqualTo(8);
        assertThat(settings.current().minimumScore()).isEqualTo(12.5);
        assertThat(auditRows("KNOWLEDGE_SETTINGS_UPDATED", "1")).isEqualTo(1);

        AdminKnowledgeService.KnowledgeSettingsView reset = service.resetSettings(adminId, "req-reset");
        assertThat(reset.customized()).isFalse();
        assertThat(reset.minimumScore()).isEqualTo(reset.defaults().minimumScore());
        assertThat(auditRows("KNOWLEDGE_SETTINGS_RESET", "1")).isEqualTo(1);
    }

    @Test
    @DisplayName("out-of-range or incomplete tuning is refused with a named error and writes nothing")
    void refusesBadSettings() {
        assertThatThrownBy(() -> service.saveSettings(-1.0, 6, 5.0, 3.0, adminId, ""))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ADMIN_KNOWLEDGE_SETTINGS_INVALID"));
        assertThatThrownBy(() -> service.saveSettings(4.0, 99, 5.0, 3.0, adminId, ""))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ADMIN_KNOWLEDGE_SETTINGS_INVALID"));
        assertThatThrownBy(() -> service.saveSettings(4.0, null, 5.0, 3.0, adminId, ""))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ADMIN_KNOWLEDGE_SETTINGS_INCOMPLETE"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_settings", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_events WHERE action LIKE 'KNOWLEDGE_SETTINGS%'", Long.class
        )).isZero();
    }

    // ----- chunks -------------------------------------------------------------------

    @Test
    @DisplayName("a manual chunk is retrievable immediately, lives under admin/, and is audited")
    void createsAManualChunk() {
        AdminKnowledgeService.ChunkDetail created = service.createChunk(
            chapterId, "栈的定义", "栈是后进先出的线性表……", "第 45 页", null, null, adminId, "req-create"
        );

        assertThat(created.id()).startsWith("admin-");
        assertThat(created.sourcePath()).startsWith("admin/");
        assertThat(created.reviewStatus()).isEqualTo("VERIFIED");
        assertThat(created.licenseScope()).isEqualTo("CLASSROOM_ONLY");
        assertThat(created.retrievable()).isTrue();
        assertThat(created.importOwned()).isFalse();
        assertThat(auditRows("KNOWLEDGE_CHUNK_CREATED", created.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("edits are applied and audited; rejecting a chunk makes it not retrievable")
    void updatesAChunk() {
        AdminKnowledgeService.ChunkDetail created = service.createChunk(
            chapterId, "队列", "队列是先进先出……", null, null, null, adminId, ""
        );

        AdminKnowledgeService.ChunkDetail updated = service.updateChunk(
            created.id(), null, "队列（修订）", null, null, "REJECTED", "TEAM_ONLY", adminId, "req-edit"
        );

        assertThat(updated.title()).isEqualTo("队列（修订）");
        assertThat(updated.reviewStatus()).isEqualTo("REJECTED");
        assertThat(updated.licenseScope()).isEqualTo("TEAM_ONLY");
        assertThat(updated.retrievable()).isFalse();
        assertThat(auditRows("KNOWLEDGE_CHUNK_UPDATED", created.id())).isEqualTo(1);

        // A patch that changes nothing is a NO_CHANGE audit, not a success claim.
        service.updateChunk(created.id(), null, null, null, null, null, null, adminId, "");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_events WHERE action = 'KNOWLEDGE_CHUNK_UPDATED' AND target_id = ? AND result = 'NO_CHANGE'",
            Long.class, created.id()
        )).isEqualTo(1);
    }

    @Test
    @DisplayName("deleting removes the row and is audited; unknown chunks and chapters are named errors")
    void deletesAndNamesFailures() {
        AdminKnowledgeService.ChunkDetail created = service.createChunk(
            null, "图", "图的邻接表……", null, null, null, adminId, ""
        );

        service.deleteChunk(created.id(), adminId, "req-delete");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM knowledge_chunks WHERE id = ?", Long.class, created.id()
        )).isZero();
        assertThat(auditRows("KNOWLEDGE_CHUNK_DELETED", created.id())).isEqualTo(1);

        assertThatThrownBy(() -> service.chunk(created.id()))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ADMIN_KNOWLEDGE_CHUNK_NOT_FOUND"));
        assertThatThrownBy(() -> service.createChunk(
            "no-such-chapter", "x", "y", null, null, null, adminId, ""
        ))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ADMIN_KNOWLEDGE_CHAPTER_NOT_FOUND"));
        assertThatThrownBy(() -> service.createChunk(null, "x", "y", null, "NOPE", null, adminId, ""))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ADMIN_KNOWLEDGE_REVIEW_STATUS_INVALID"));
    }

    @Test
    @DisplayName("the list filters by status, source prefix and free text, and pages")
    void filtersAndPages() {
        service.createChunk(chapterId, "哈夫曼树", "带权路径长度……", null, "VERIFIED", null, adminId, "");
        service.createChunk(null, "并查集", "按秩合并……", null, "PENDING", null, adminId, "");

        AdminPage<AdminKnowledgeService.ChunkRow> all = service.chunks(0, 20, null, null, null, null);
        assertThat(all.total()).isGreaterThanOrEqualTo(2);

        AdminPage<AdminKnowledgeService.ChunkRow> pending = service.chunks(0, 20, null, "PENDING", null, null);
        assertThat(pending.items()).allSatisfy(row -> assertThat(row.reviewStatus()).isEqualTo("PENDING"));
        assertThat(pending.items()).anySatisfy(row -> assertThat(row.title()).isEqualTo("并查集"));

        AdminPage<AdminKnowledgeService.ChunkRow> manual = service.chunks(0, 20, null, null, "admin", null);
        assertThat(manual.items()).allSatisfy(row -> assertThat(row.sourcePath()).startsWith("admin/"));

        AdminPage<AdminKnowledgeService.ChunkRow> search = service.chunks(0, 20, null, null, null, "哈夫曼");
        assertThat(search.items()).hasSize(1);
        assertThat(search.items().get(0).retrievable()).isTrue();

        assertThatThrownBy(() -> service.chunks(-1, 20, null, null, null, null))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ADMIN_PAGE_INVALID"));
    }

    // ----- test search --------------------------------------------------------------

    @Test
    @DisplayName("the preview searches with exactly the tuning it is given, against the live snapshot")
    void previewsWithOverrides() {
        AdminKnowledgeService.ChunkDetail created = service.createChunk(
            chapterId, "二叉树的遍历", "先序遍历先访问根结点……", null, null, null, adminId, ""
        );
        // The snapshot refreshes after commit in production; in a rolled-back test we replace it by hand.
        search.replace(List.of(new KnowledgeChunk(
            created.id(), chapterId, created.title(), created.content(),
            created.sourcePath(), created.pageLabel(), created.licenseScope()
        )));

        AdminKnowledgeService.TestSearchResult hits = service.testSearch(
            "二叉树", null, "STUDENT", null, null, null, null
        );
        assertThat(hits.hits()).hasSize(1);
        assertThat(hits.hits().get(0).chunkId()).isEqualTo(created.id());
        assertThat(hits.hits().get(0).score()).isGreaterThan(0);
        assertThat(hits.effectiveSettings().minimumScore()).isEqualTo(settings.current().minimumScore());

        // A punishing threshold proves the override is really the one being applied.
        AdminKnowledgeService.TestSearchResult none = service.testSearch(
            "二叉树", null, null, 100.0, null, null, null
        );
        assertThat(none.hits()).isEmpty();
        assertThat(none.effectiveSettings().minimumScore()).isEqualTo(100.0);

        // A GUEST sees nothing: the manual chunk is CLASSROOM_ONLY.
        assertThat(service.testSearch("二叉树", null, "GUEST", null, null, null, null).hits()).isEmpty();

        assertThatThrownBy(() -> service.testSearch(" ", null, null, null, null, null, null))
            .isInstanceOf(ApiException.class)
            .satisfies(failure -> assertThat(((ApiException) failure).code()).isEqualTo("ADMIN_KNOWLEDGE_QUERY_REQUIRED"));
    }

    @Test
    @DisplayName("the overview counts chunks by status and source and reports the snapshot size")
    void overviews() {
        service.createChunk(chapterId, "排序", "快速排序……", null, "VERIFIED", null, adminId, "");
        service.createChunk(null, "查找", "二分查找……", null, "REJECTED", null, adminId, "");

        AdminKnowledgeService.KnowledgeOverview overview = service.overview();

        assertThat(overview.totalChunks()).isGreaterThanOrEqualTo(2);
        assertThat(overview.bySource().getOrDefault("admin", 0L)).isGreaterThanOrEqualTo(2);
        assertThat(overview.byReviewStatus().getOrDefault("VERIFIED", 0L)).isGreaterThanOrEqualTo(1);
        assertThat(overview.byReviewStatus().getOrDefault("REJECTED", 0L)).isGreaterThanOrEqualTo(1);
        assertThat(overview.snapshotChunks()).isZero();
    }
}
