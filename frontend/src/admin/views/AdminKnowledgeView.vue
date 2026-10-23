<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import AdminPageFrame from "../components/AdminPageFrame.vue";
import { adminApi, adminErrorMessage, formatDate } from "../api";
import type {
  AdminKnowledgeChunkDetail,
  AdminKnowledgeChunkRow,
  KnowledgeLicenseScope,
  KnowledgeOverview,
  KnowledgeReviewStatus,
  KnowledgeSettingsView,
  KnowledgeTestSearchResult,
} from "../../shared/types";
import type { Chapter } from "../../shared/types/contracts";

/**
 * The knowledge console: what the AI may quote, and how loosely it matches.
 *
 * Two things an administrator actually does here. One is tuning the matcher - threshold, result cap, title
 * weights - with a try-it-first search that runs against the live snapshot without saving anything. The other
 * is chunk maintenance: fix a bad title, reject a wrong passage, or add a manual note. Manual chunks live
 * under admin/ so the next textbook import cannot wipe them; edits to import-owned rows are allowed but the
 * page says plainly that the next import wins.
 */
const overview = ref<KnowledgeOverview | null>(null);
const settings = ref<KnowledgeSettingsView | null>(null);
const chapters = ref<Chapter[]>([]);

const settingsForm = ref({ minimumScore: 0, searchLimit: 6, titleWeightLong: 5, titleWeightShort: 3 });

const testQuery = ref("");
const testChapterId = ref("");
const testAudience = ref<"GUEST" | "STUDENT" | "TEAM">("STUDENT");
const testResult = ref<KnowledgeTestSearchResult | null>(null);
const testing = ref(false);

const items = ref<AdminKnowledgeChunkRow[]>([]);
const page = ref(0);
const size = 20;
const total = ref(0);
const filterStatus = ref("");
const filterSource = ref("");
const filterChapter = ref("");
const filterText = ref("");

const selected = ref<AdminKnowledgeChunkDetail | null>(null);
const editForm = ref({
  title: "",
  chapterId: "",
  pageLabel: "",
  content: "",
  reviewStatus: "VERIFIED" as string,
  licenseScope: "CLASSROOM_ONLY" as string,
});

const showCreate = ref(false);
const createForm = ref({
  title: "",
  chapterId: "",
  pageLabel: "",
  content: "",
  reviewStatus: "VERIFIED" as string,
  licenseScope: "CLASSROOM_ONLY" as string,
});

const loading = ref(true);
const loadingChunks = ref(false);
const busy = ref(false);
const error = ref("");
const notice = ref("");

const statusLabels: Record<string, string> = { DRAFT: "草稿", PENDING: "待审核", VERIFIED: "已核验", REJECTED: "已退回" };
const scopeLabels: Record<string, string> = { PUBLIC: "公开", CLASSROOM_ONLY: "课堂内", TEAM_ONLY: "团队内" };
const sourceLabels: Record<string, string> = { textbook: "教材", courseware: "课件", admin: "手工维护", "dev-mock": "开发样例", other: "其他" };

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)));
const sourceSummary = computed(() => {
  if (!overview.value) return "";
  return Object.entries(overview.value.bySource)
    .map(([key, count]) => `${sourceLabels[key] ?? key} ${count}`)
    .join(" · ");
});

function chapterTitle(chapterId: string | null | undefined): string {
  if (!chapterId) return "—";
  const chapter = chapters.value.find((entry) => entry.id === chapterId);
  return chapter ? `第 ${chapter.chapterNumber} 章 · ${chapter.title}` : chapterId;
}

async function loadOverview(): Promise<void> {
  overview.value = await adminApi.knowledgeOverview();
}

async function loadSettings(): Promise<void> {
  const view = await adminApi.knowledgeSettings();
  settings.value = view;
  settingsForm.value = {
    minimumScore: view.minimumScore,
    searchLimit: view.searchLimit,
    titleWeightLong: view.titleWeightLong,
    titleWeightShort: view.titleWeightShort,
  };
}

async function loadChunks(): Promise<void> {
  loadingChunks.value = true;
  try {
    const result = await adminApi.knowledgeChunks({
      page: page.value,
      size,
      status: filterStatus.value || undefined,
      sourcePrefix: filterSource.value || undefined,
      chapterId: filterChapter.value || undefined,
      q: filterText.value.trim() || undefined,
    });
    items.value = result.items;
    total.value = result.total;
  } catch (failure) {
    error.value = adminErrorMessage(failure, "读取知识条目");
  } finally {
    loadingChunks.value = false;
  }
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const results = await Promise.allSettled([loadOverview(), loadSettings(), adminApi.knowledgeChapters(), loadChunks()]);
    const chaptersResult = results[2];
    if (chaptersResult.status === "fulfilled") chapters.value = chaptersResult.value;
    const failed = results.find((result) => result.status === "rejected") as PromiseRejectedResult | undefined;
    if (failed) error.value = adminErrorMessage(failed.reason, "读取知识库");
  } finally {
    loading.value = false;
  }
}

function applyFilters(): void {
  page.value = 0;
  notice.value = "";
  void loadChunks();
}

function turnPage(delta: number): void {
  const next = page.value + delta;
  if (next < 0 || next >= pageCount.value) return;
  page.value = next;
  void loadChunks();
}

async function saveSettings(): Promise<void> {
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const view = await adminApi.saveKnowledgeSettings({ ...settingsForm.value });
    settings.value = view;
    notice.value = "匹配方式已保存，下一次检索就按新参数来。";
    await loadOverview();
  } catch (failure) {
    error.value = adminErrorMessage(failure, "保存匹配方式");
  } finally {
    busy.value = false;
  }
}

async function resetSettings(): Promise<void> {
  if (!window.confirm("恢复默认匹配方式？手工调节的参数会被清除。")) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const view = await adminApi.resetKnowledgeSettings();
    settings.value = view;
    settingsForm.value = {
      minimumScore: view.minimumScore,
      searchLimit: view.searchLimit,
      titleWeightLong: view.titleWeightLong,
      titleWeightShort: view.titleWeightShort,
    };
    notice.value = "已恢复默认匹配方式。";
    await loadOverview();
  } catch (failure) {
    error.value = adminErrorMessage(failure, "恢复默认匹配方式");
  } finally {
    busy.value = false;
  }
}

async function runTest(): Promise<void> {
  const query = testQuery.value.trim();
  if (query === "") {
    error.value = "先输入一个检索词再试搜。";
    return;
  }
  testing.value = true;
  error.value = "";
  try {
    testResult.value = await adminApi.testKnowledgeSearch({
      query,
      chapterId: testChapterId.value || undefined,
      audience: testAudience.value,
      // The form values are the point of the preview: try these knobs before saving them.
      ...settingsForm.value,
    });
  } catch (failure) {
    testResult.value = null;
    error.value = adminErrorMessage(failure, "试搜");
  } finally {
    testing.value = false;
  }
}

async function pick(chunkId: string): Promise<void> {
  showCreate.value = false;
  busy.value = true;
  error.value = "";
  try {
    const detail = await adminApi.knowledgeChunk(chunkId);
    selected.value = detail;
    editForm.value = {
      title: detail.title,
      chapterId: detail.chapterId ?? "",
      pageLabel: detail.pageLabel ?? "",
      content: detail.content,
      reviewStatus: detail.reviewStatus,
      licenseScope: detail.licenseScope,
    };
  } catch (failure) {
    error.value = adminErrorMessage(failure, "读取知识条目");
  } finally {
    busy.value = false;
  }
}

async function saveChunk(): Promise<void> {
  const current = selected.value;
  if (!current) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const updated = await adminApi.updateKnowledgeChunk(current.id, {
      title: editForm.value.title.trim(),
      chapterId: editForm.value.chapterId || undefined,
      pageLabel: editForm.value.pageLabel.trim() || undefined,
      content: editForm.value.content.trim(),
      reviewStatus: editForm.value.reviewStatus as KnowledgeReviewStatus,
      licenseScope: editForm.value.licenseScope as KnowledgeLicenseScope,
    });
    selected.value = updated;
    notice.value = updated.retrievable
      ? "已保存，这条现在可以被检索到。"
      : "已保存。注意：按现在的状态它不会被检索到。";
    await Promise.all([loadChunks(), loadOverview()]);
  } catch (failure) {
    error.value = adminErrorMessage(failure, "保存知识条目");
  } finally {
    busy.value = false;
  }
}

async function removeChunk(): Promise<void> {
  const current = selected.value;
  if (!current) return;
  if (!window.confirm(`删除「${current.title}」？删除后检索立刻不再引用它。`)) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await adminApi.deleteKnowledgeChunk(current.id);
    selected.value = null;
    notice.value = "已删除。";
    await Promise.all([loadChunks(), loadOverview()]);
  } catch (failure) {
    error.value = adminErrorMessage(failure, "删除知识条目");
  } finally {
    busy.value = false;
  }
}

async function createChunk(): Promise<void> {
  if (createForm.value.title.trim() === "" || createForm.value.content.trim() === "") {
    error.value = "标题和正文都要填。";
    return;
  }
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const created = await adminApi.createKnowledgeChunk({
      title: createForm.value.title.trim(),
      chapterId: createForm.value.chapterId || undefined,
      pageLabel: createForm.value.pageLabel.trim() || undefined,
      content: createForm.value.content.trim(),
      reviewStatus: createForm.value.reviewStatus as KnowledgeReviewStatus,
      licenseScope: createForm.value.licenseScope as KnowledgeLicenseScope,
    });
    showCreate.value = false;
    createForm.value = { title: "", chapterId: "", pageLabel: "", content: "", reviewStatus: "VERIFIED", licenseScope: "CLASSROOM_ONLY" };
    notice.value = "已建好，归在「手工维护」来源下，教材导入不会冲掉它。";
    await Promise.all([loadChunks(), loadOverview()]);
    await pick(created.id);
  } catch (failure) {
    error.value = adminErrorMessage(failure, "新建知识条目");
  } finally {
    busy.value = false;
  }
}

onMounted(load);
</script>

<template>
  <AdminPageFrame
    title="知识库"
    description="RAG 检索用的知识条目和匹配方式。匹配参数保存后立刻生效；条目改动在保存那一刻就重建检索快照。"
  >
    <template #actions>
      <button class="admin-primary" type="button" :disabled="loading" @click="load">刷新</button>
    </template>

    <p v-if="notice" class="admin-muted kb__notice">{{ notice }}</p>
    <p v-if="error" class="admin-danger">{{ error }}</p>

    <section class="admin-panel kb">
      <div class="signal-strip kb__strip">
        <div class="signal-strip__item">
          <span class="signal-strip__label">知识条目</span>
          <strong>{{ overview ? overview.totalChunks : "—" }}</strong>
        </div>
        <div class="signal-strip__item">
          <span class="signal-strip__label">检索快照</span>
          <strong>{{ overview ? overview.snapshotChunks : "—" }}</strong>
        </div>
        <div class="signal-strip__item">
          <span class="signal-strip__label">匹配方式</span>
          <strong>{{ settings ? (settings.customized ? "手工调节" : "默认参数") : "—" }}</strong>
        </div>
        <div class="signal-strip__item">
          <span class="signal-strip__label">来源分布</span>
          <strong class="kb__sources">{{ sourceSummary || "—" }}</strong>
        </div>
      </div>
    </section>

    <section class="admin-panel kb">
      <header class="admin-section-head">
        <h2>匹配方式</h2>
        <p class="admin-muted">
          检索是纯文本打分：标题命中按权重加分，再叠正文分，低于门槛的条目不参与回答。
          保存前可以用下面的「试搜」看效果。
        </p>
      </header>
      <div class="admin-toolbar kb__settings">
        <label class="admin-field">
          <span>最低分门槛（0-100）</span>
          <input v-model.number="settingsForm.minimumScore" type="number" min="0" max="100" step="0.5" />
        </label>
        <label class="admin-field">
          <span>最多返回条数（1-12）</span>
          <input v-model.number="settingsForm.searchLimit" type="number" min="1" max="12" step="1" />
        </label>
        <label class="admin-field">
          <span>标题权重 · 长词</span>
          <input v-model.number="settingsForm.titleWeightLong" type="number" min="0.5" max="20" step="0.5" />
        </label>
        <label class="admin-field">
          <span>标题权重 · 短词</span>
          <input v-model.number="settingsForm.titleWeightShort" type="number" min="0.5" max="20" step="0.5" />
        </label>
        <button type="button" class="admin-primary" data-testid="save-settings" :disabled="busy" @click="saveSettings">保存</button>
        <button
          v-if="settings?.customized"
          type="button"
          class="kb__link"
          data-testid="reset-settings"
          :disabled="busy"
          @click="resetSettings"
        >恢复默认</button>
      </div>
      <p v-if="settings" class="admin-muted kb__defaults">
        默认值：门槛 {{ settings.defaults.minimumScore }} · 条数 {{ settings.defaults.searchLimit }}
        · 标题权重 {{ settings.defaults.titleWeightLong }}/{{ settings.defaults.titleWeightShort }}
      </p>

      <div class="admin-toolbar kb__test">
        <label class="admin-field admin-field--wide">
          <span>试搜</span>
          <input v-model="testQuery" placeholder="如：栈的入栈和出栈" @keyup.enter="runTest" />
        </label>
        <label class="admin-field">
          <span>限定章节</span>
          <select v-model="testChapterId">
            <option value="">全部章节</option>
            <option v-for="chapter in chapters" :key="chapter.id" :value="chapter.id">
              第 {{ chapter.chapterNumber }} 章 · {{ chapter.title }}
            </option>
          </select>
        </label>
        <label class="admin-field">
          <span>以谁的身份</span>
          <select v-model="testAudience">
            <option value="STUDENT">学生</option>
            <option value="TEAM">教师/管理员</option>
            <option value="GUEST">访客</option>
          </select>
        </label>
        <button type="button" class="admin-primary" data-testid="run-test" :disabled="testing" @click="runTest">
          {{ testing ? "检索中…" : "用上面的参数试搜" }}
        </button>
      </div>
      <div v-if="testResult" class="kb__hits">
        <p v-if="testResult.hits.length === 0" class="admin-muted">
          没有命中。可以调低门槛，或换一个更贴近条目标题的词。
        </p>
        <ul v-else>
          <li v-for="hit in testResult.hits" :key="hit.chunkId">
            <strong>{{ hit.score.toFixed(1) }}</strong>
            <span>{{ hit.title }}</span>
            <span class="admin-muted">{{ hit.sourcePath }}<template v-if="hit.pageLabel"> · {{ hit.pageLabel }}</template></span>
          </li>
        </ul>
      </div>
    </section>

    <section class="admin-panel kb">
      <header class="admin-section-head">
        <h2>知识条目</h2>
        <p class="admin-muted">共 {{ total }} 条 · 只有「已核验」且章节已发布的条目才会被检索</p>
      </header>

      <div class="admin-toolbar">
        <label class="admin-field admin-field--wide">
          <span>搜索</span>
          <input v-model="filterText" placeholder="按标题或正文搜索" @keyup.enter="applyFilters" />
        </label>
        <label class="admin-field">
          <span>状态</span>
          <select v-model="filterStatus" @change="applyFilters">
            <option value="">全部状态</option>
            <option value="VERIFIED">已核验</option>
            <option value="PENDING">待审核</option>
            <option value="DRAFT">草稿</option>
            <option value="REJECTED">已退回</option>
          </select>
        </label>
        <label class="admin-field">
          <span>来源</span>
          <select v-model="filterSource" @change="applyFilters">
            <option value="">全部来源</option>
            <option value="textbook">教材</option>
            <option value="courseware">课件</option>
            <option value="admin">手工维护</option>
            <option value="dev-mock">开发样例</option>
          </select>
        </label>
        <label class="admin-field">
          <span>章节</span>
          <select v-model="filterChapter" @change="applyFilters">
            <option value="">全部章节</option>
            <option v-for="chapter in chapters" :key="chapter.id" :value="chapter.id">
              第 {{ chapter.chapterNumber }} 章 · {{ chapter.title }}
            </option>
          </select>
        </label>
        <button type="button" class="admin-primary" :disabled="loadingChunks" @click="applyFilters">筛选</button>
        <button type="button" class="kb__link" data-testid="toggle-create" :disabled="busy" @click="showCreate = !showCreate; selected = null">
          {{ showCreate ? "收起新建" : "新建条目" }}
        </button>
      </div>

      <div class="admin-data-surface">
        <table class="kb__table">
          <thead>
            <tr>
              <th>标题</th>
              <th>章节</th>
              <th>状态</th>
              <th>授权</th>
              <th>可检索</th>
              <th>来源</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in items"
              :key="row.id"
              :class="{ 'kb__row--active': selected?.id === row.id }"
              @click="pick(row.id)"
            >
              <td>{{ row.title }}</td>
              <td class="admin-muted admin-nowrap">{{ chapterTitle(row.chapterId) }}</td>
              <td>{{ statusLabels[row.reviewStatus] ?? row.reviewStatus }}</td>
              <td class="admin-muted">{{ scopeLabels[row.licenseScope] ?? row.licenseScope }}</td>
              <td>
                <span :class="row.retrievable ? 'kb__flag kb__flag--on' : 'kb__flag'">
                  {{ row.retrievable ? "可检索" : "不可检索" }}
                </span>
              </td>
              <td class="admin-muted admin-nowrap">
                {{ sourceLabels[row.sourcePath.split("/")[0]] ?? row.sourcePath.split("/")[0] }}<template v-if="row.importOwned">（导入管理）</template>
              </td>
              <td class="admin-muted admin-nowrap">{{ formatDate(row.createdAt) }}</td>
            </tr>
            <tr v-if="!loadingChunks && items.length === 0">
              <td colspan="7" class="admin-muted">这个筛选条件下没有条目。</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="kb__pager">
        <button type="button" class="kb__link" :disabled="page === 0 || loadingChunks" @click="turnPage(-1)">上一页</button>
        <span class="admin-muted">第 {{ page + 1 }} / {{ pageCount }} 页 · 共 {{ total }} 条</span>
        <button type="button" class="kb__link" :disabled="page + 1 >= pageCount || loadingChunks" @click="turnPage(1)">下一页</button>
      </div>
    </section>

    <section v-if="showCreate" class="admin-panel kb">
      <header class="admin-section-head">
        <h2>新建条目</h2>
        <p class="admin-muted">手工条目归在「手工维护」来源下，默认已核验、课堂内可见，保存后立即可检索。</p>
      </header>
      <div class="admin-toolbar">
        <label class="admin-field admin-field--wide">
          <span>标题</span>
          <input v-model="createForm.title" placeholder="检索时权重最高的部分，写清主题" />
        </label>
        <label class="admin-field">
          <span>章节</span>
          <select v-model="createForm.chapterId">
            <option value="">不限章节</option>
            <option v-for="chapter in chapters" :key="chapter.id" :value="chapter.id">
              第 {{ chapter.chapterNumber }} 章 · {{ chapter.title }}
            </option>
          </select>
        </label>
        <label class="admin-field">
          <span>状态</span>
          <select v-model="createForm.reviewStatus">
            <option value="VERIFIED">已核验</option>
            <option value="PENDING">待审核</option>
            <option value="DRAFT">草稿</option>
          </select>
        </label>
        <label class="admin-field">
          <span>授权范围</span>
          <select v-model="createForm.licenseScope">
            <option value="CLASSROOM_ONLY">课堂内</option>
            <option value="TEAM_ONLY">团队内</option>
            <option value="PUBLIC">公开</option>
          </select>
        </label>
        <label class="admin-field">
          <span>页签</span>
          <input v-model="createForm.pageLabel" placeholder="如：第 45 页" />
        </label>
      </div>
      <label class="admin-field kb__content">
        <span>正文</span>
        <textarea v-model="createForm.content" rows="6" placeholder="这条知识的内容，检索时按正文打分"></textarea>
      </label>
      <div class="kb__actions">
        <button type="button" class="admin-primary" data-testid="create-chunk" :disabled="busy" @click="createChunk">创建</button>
      </div>
    </section>

    <section v-if="selected" class="admin-panel kb">
      <header class="admin-section-head">
        <h2>编辑：{{ selected.title }}</h2>
        <p class="admin-muted">
          {{ selected.sourcePath }}
          <template v-if="selected.importOwned">
            · 这条来自教材/课件导入，<strong>下次导入会覆盖这里的修改</strong>；要长期保留就另建一条手工条目。
          </template>
        </p>
      </header>
      <p class="admin-muted kb__retrievable">
        当前{{ selected.retrievable ? "可以被检索" : "不会被检索" }}。
        <template v-if="!selected.retrievable">要让它可检索：状态改为「已核验」，且所属章节保持已发布。</template>
      </p>
      <div class="admin-toolbar">
        <label class="admin-field admin-field--wide">
          <span>标题</span>
          <input v-model="editForm.title" />
        </label>
        <label class="admin-field">
          <span>章节</span>
          <select v-model="editForm.chapterId">
            <option value="">不限章节</option>
            <option v-for="chapter in chapters" :key="chapter.id" :value="chapter.id">
              第 {{ chapter.chapterNumber }} 章 · {{ chapter.title }}
            </option>
          </select>
        </label>
        <label class="admin-field">
          <span>状态</span>
          <select v-model="editForm.reviewStatus">
            <option value="VERIFIED">已核验</option>
            <option value="PENDING">待审核</option>
            <option value="DRAFT">草稿</option>
            <option value="REJECTED">已退回</option>
          </select>
        </label>
        <label class="admin-field">
          <span>授权范围</span>
          <select v-model="editForm.licenseScope">
            <option value="CLASSROOM_ONLY">课堂内</option>
            <option value="TEAM_ONLY">团队内</option>
            <option value="PUBLIC">公开</option>
          </select>
        </label>
        <label class="admin-field">
          <span>页签</span>
          <input v-model="editForm.pageLabel" />
        </label>
      </div>
      <label class="admin-field kb__content">
        <span>正文</span>
        <textarea v-model="editForm.content" rows="8"></textarea>
      </label>
      <div class="kb__actions">
        <button type="button" class="admin-primary" data-testid="save-chunk" :disabled="busy" @click="saveChunk">保存修改</button>
        <button type="button" class="kb__link kb__link--danger" data-testid="delete-chunk" :disabled="busy" @click="removeChunk">删除</button>
      </div>
    </section>
  </AdminPageFrame>
</template>

<style scoped>
.kb + .kb {
  margin-top: 18px;
}

.kb__strip {
  margin: 0;
}

.kb__sources {
  font-size: 14px;
  font-weight: 600;
}

.kb__notice {
  margin: 0 0 8px;
}

.kb__settings,
.kb__test {
  margin-bottom: 4px;
}

.kb__defaults {
  margin: 4px 0 14px;
}

.kb__hits {
  margin-top: 10px;
}

.kb__hits ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.kb__hits li {
  display: flex;
  gap: 10px;
  align-items: baseline;
  padding: 8px 10px;
  border: 1px solid rgba(12, 44, 52, 0.12);
  border-radius: 10px;
  font-size: 14px;
}

.kb__hits li strong {
  min-width: 44px;
  color: var(--admin-accent, #08766f);
}

.kb__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.kb__table th,
.kb__table td {
  padding: 9px 10px;
  text-align: left;
  border-bottom: 1px solid rgba(12, 44, 52, 0.1);
}

.kb__table th {
  font-weight: 600;
  color: var(--admin-muted);
  white-space: nowrap;
}

.kb__table tbody tr {
  cursor: pointer;
}

.kb__row--active {
  background: rgba(8, 118, 111, 0.06);
}

.kb__flag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(12, 44, 52, 0.08);
}

.kb__flag--on {
  background: rgba(8, 118, 111, 0.14);
  color: var(--admin-accent, #08766f);
  font-weight: 600;
}

.kb__pager {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 10px;
}

.kb__link {
  border: 1px solid rgba(8, 118, 111, 0.4);
  background: transparent;
  color: inherit;
  border-radius: 8px;
  padding: 4px 10px;
  cursor: pointer;
  font-size: 13px;
}

.kb__link:hover:not(:disabled) {
  background: rgba(8, 118, 111, 0.1);
}

.kb__link:disabled {
  cursor: default;
  opacity: 0.4;
}

.kb__link--danger {
  border-color: rgba(178, 58, 58, 0.5);
}

.kb__link--danger:hover:not(:disabled) {
  background: rgba(178, 58, 58, 0.1);
}

.kb__content {
  display: block;
  margin-top: 10px;
}

.kb__content textarea {
  width: 100%;
  box-sizing: border-box;
  margin-top: 6px;
  padding: 10px;
  border: 1px solid rgba(12, 44, 52, 0.16);
  border-radius: 10px;
  font: inherit;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
}

.kb__retrievable {
  margin: 0 0 10px;
}

.kb__actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
</style>
