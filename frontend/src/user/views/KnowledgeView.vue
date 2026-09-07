<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { auth } from "../../app/providers/runtime";
import { useLocale } from "../../shared/i18n/locale";
import type { Chapter, KnowledgeSearchResult } from "../../shared/types/contracts";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import { userApi } from "../runtime";
import { coursePreviewFixture, previewKnowledgeChapterId, searchPreviewKnowledge } from "../fixtures/course-preview";
import { knowledgePreviewSuggestions, type KnowledgePreviewSuggestion } from "../fixtures/knowledge-preview";
import RuntimeSelect, { type RuntimeSelectOption } from "../../shared/components/RuntimeSelect.vue";

const route = useRoute();
const router = useRouter();
const { isEnglish } = useLocale();
function queryValue(value: unknown): string {
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return typeof value[0] === "string" ? value[0] : "";
  return "";
}

const query = ref(queryValue(route.query.q));
const lessonId = ref(queryValue(route.query.lessonId));
const chapterId = ref(queryValue(route.query.chapterId) || previewKnowledgeChapterId(queryValue(route.query.lessonId)) || "");
const limit = ref(4);
const chapters = ref<Chapter[]>([]);
const results = ref<KnowledgeSearchResult[]>([]);
const searchedQuery = ref("");
const loading = ref(false);
const loadingChapters = ref(true);
const chaptersError = ref<UserErrorPresentation | null>(null);
const error = ref<UserErrorPresentation | null>(null);
const previewFallbackActive = ref(false);
const scopeExpandedFromChapterId = ref("");
type ValidationKey = "query-required" | "query-too-long";

const validationKey = ref<ValidationKey | null>(null);
const isFixture = computed(() => !auth.state.user);

const copy = computed(() => isEnglish.value ? {
  eyebrow: "Course materials",
  title: "Knowledge search",
  fixtureIntro: "Browse example course material without signing in. It does not represent published access or a personal learning record.",
  authenticatedIntro: "Only course material that is available to the current account is returned.",
  fixtureBadge: "Example preview",
  searchLabel: "Search for a concept",
  searchPlaceholder: "For example: the condition for pushing onto a sequential stack",
  chapterScope: "Chapter scope",
  allVisibleChapters: "All available chapters",
  chapterLabel: (chapter: Chapter) => `Chapter ${chapter.chapterNumber}: ${chapter.title}`,
  resultLimit: "Results to return",
  resultCount: (count: number) => `${count} result${count === 1 ? "" : "s"}`,
  search: "Search knowledge",
  searching: "Searching...",
  searchHint: "Queries can contain up to 500 characters, with at most 6 results.",
  fixtureHint: "Example content. Queries can contain up to 500 characters, with at most 6 results.",
  reloadChapters: "Reload chapters",
  reloadSearch: "Search again",
  loadingTitle: "Searching course knowledge",
  loadingDetail: "Matching reviewed course material.",
  emptyResultsTitle: "No available results found",
  emptyResultsDetail: (value: string) => `No reviewed material available to this account matches “${value}”.`,
  authenticatedEmptyHint: "If this stays empty, the course material still needs chapter mapping, source authorization, review, and publication. Example previews never replace the real course library.",
  resultsTitle: "Search results",
  reviewedSources: (count: number) => `${count} reviewed source${count === 1 ? "" : "s"}`,
  previewResults: (count: number) => `${count} example result${count === 1 ? "" : "s"}`,
  previewSource: "Course preview",
  openChapter: "Open chapter",
  initialEmptyTitle: "Search for a concept to begin",
  initialEmptyDetail: "You can limit the search to a chapter. Unreviewed, unpublished, or inaccessible material is not shown.",
  previewSuggestionsTitle: "Try an example topic",
  previewSuggestionsDetail: "The guest preview covers the main course themes. Choosing one narrows the search to its chapter.",
  previewSuggestionAria: (value: string) => `Search example: ${value}`,
  materialScope: "Material scope",
  fixtureScope: "Example course material",
  authenticatedScope: "Course material available to the current account.",
  searchRecord: "Search record",
  fixtureRecord: "Guest previews do not save a search record.",
  authenticatedRecord: "Signing in can preserve this learning activity.",
  previewFallbackTitle: "Example material is shown separately",
  previewFallbackDetail: "The account search returned no reviewed material. The course library may still need chapter mapping, source authorization, review, and publication. These local examples are for checking the search path only and do not represent account access or a saved learning record.",
  previewFallbackReset: "Back to account results",
  scopeExpandedTitle: "Search was broadened to all chapters",
  scopeExpandedDetail: (chapter: string, query: string) => `No visible result for “${query}” was found inside ${chapter}, so the search expanded to the whole course.`,
  scopeExpandedReset: "Restore chapter scope",
  exampleContent: "Example content",
  reviewed: "Reviewed",
  validation: {
    "query-required": "Enter a concept to search for.",
    "query-too-long": "A search query cannot exceed 500 characters.",
  },
  errors: {
    permission: { title: "Sign in to view this material", message: "Sign in to access this course material." },
    "not-found": { title: "Course material is unavailable", message: "This learning item may be unavailable or outside this account's access." },
    conflict: { title: "This page has changed", message: "Refresh and try again to use the latest learning state." },
    limited: { title: "Please wait before trying again", message: "The learning service is temporarily limiting requests." },
    timeout: { title: "The request timed out", message: "The learning service did not respond in time." },
    service: { title: "Learning services are temporarily unavailable", message: "This page remains available. Try again when the service recovers." },
    network: { title: "Network connection is unavailable", message: "Check the connection and try again. Your current page remains available." },
    validation: { title: "This search cannot be processed", message: "Check the search text and try again." },
    unknown: { title: "This action was not completed", message: "The learning service returned an unexpected result. Try again." },
  },
} : {
  eyebrow: "课程资料",
  title: "知识检索",
  fixtureIntro: "仅用于未登录浏览，不代表已发布课程目录、账号权限或学习记录。",
  authenticatedIntro: "只返回已开放且当前账号可见的课程资料。",
  fixtureBadge: "示例预览",
  searchLabel: "检索内容",
  searchPlaceholder: "例如：顺序栈的入栈条件",
  chapterScope: "章节范围",
  allVisibleChapters: "全部可见章节",
  chapterLabel: (chapter: Chapter) => `第 ${chapter.chapterNumber} 章 ${chapter.title}`,
  resultLimit: "返回数量",
  resultCount: (count: number) => `${count} 条`,
  search: "检索知识",
  searching: "正在检索…",
  searchHint: "查询最长 500 字，结果最多 6 条",
  fixtureHint: "示例内容 · 查询最长 500 字，结果最多 6 条",
  reloadChapters: "重新加载章节",
  reloadSearch: "重新检索",
  loadingTitle: "正在检索课程知识",
  loadingDetail: "正在匹配已审核来源。",
  emptyResultsTitle: "没有找到可见结果",
  emptyResultsDetail: (value: string) => `“${value}”没有匹配到已审核且当前账号可见的知识。`,
  authenticatedEmptyHint: "如果这里一直为空，通常表示课程资料还没有完成章节绑定、来源授权、审核和发布。示例预览不会替代真实课程库。",
  resultsTitle: "检索结果",
  reviewedSources: (count: number) => `${count} 条已审核来源`,
  previewResults: (count: number) => `${count} 条示例内容`,
  previewSource: "课程预览",
  openChapter: "打开章节",
  initialEmptyTitle: "输入知识点开始检索",
  initialEmptyDetail: "可以限定到当前章节；未审核、未发布或无访问权限的材料不会出现。",
  previewSuggestionsTitle: "先试试这些示例主题",
  previewSuggestionsDetail: "游客示例覆盖课程主要主题，选择后会限定到对应章节。",
  previewSuggestionAria: (value: string) => `检索示例：${value}`,
  materialScope: "资料范围",
  fixtureScope: "示例课程内容",
  authenticatedScope: "已开放且对当前账户可见的资料。",
  searchRecord: "搜索记录",
  fixtureRecord: "游客预览不会写入搜索记录。",
  authenticatedRecord: "登录后可保留本次学习活动。",
  previewFallbackTitle: "以下为独立的本地示例",
  previewFallbackDetail: "当前账号的真实课程资料没有返回结果，通常表示课程资料还没有完成章节绑定、来源授权、审核和发布。下面的内容只用于验证检索路径，不代表账号权限，也不会写入学习记录。",
  previewFallbackReset: "返回账号资料结果",
  scopeExpandedTitle: "已扩展到全部章节检索",
  scopeExpandedDetail: (chapter: string, query: string) => `当前章节“${chapter}”里没有找到“${query}”，因此已经自动扩展到全课程范围继续检索。`,
  scopeExpandedReset: "恢复原章节范围",
  exampleContent: "示例内容",
  reviewed: "已审核",
  validation: {
    "query-required": "请输入要检索的知识点。",
    "query-too-long": "检索内容不能超过 500 个字符。",
  },
  errors: {
    permission: { title: "登录后解锁此功能", message: "游客可以继续浏览公开学习内容；登录后可访问此课程资料。" },
    "not-found": { title: "资源不可访问", message: "该学习内容可能未发布、已移除，或当前账号没有访问范围。" },
    conflict: { title: "当前状态已变化", message: "请刷新后继续操作，避免覆盖最新学习状态。" },
    limited: { title: "请求过于频繁", message: "学习服务暂时限制了请求，请稍后重试。" },
    timeout: { title: "请求超时", message: "学习服务未在规定时间内响应。" },
    service: { title: "学习服务暂不可用", message: "当前页面位置已保留，服务恢复后可再次尝试。" },
    network: { title: "网络连接不可用", message: "请检查网络后重试，当前学习位置不会丢失。" },
    validation: { title: "检索内容无法处理", message: "请检查输入内容后重试。" },
    unknown: { title: "操作未完成", message: "服务返回了未预期的结果，请重试。" },
  },
});

const validation = computed(() => validationKey.value ? copy.value.validation[validationKey.value] : "");
const displayedChaptersError = computed(() => chaptersError.value ? localizedUserError(chaptersError.value) : null);
const displayedSearchError = computed(() => error.value ? localizedUserError(error.value) : null);
const chapterOptions = computed<RuntimeSelectOption[]>(() => [
  { value: "", label: copy.value.allVisibleChapters },
  ...chapters.value.map((chapter) => ({ value: chapter.id, label: copy.value.chapterLabel(chapter) })),
]);
const resultLimitOptions = computed<RuntimeSelectOption[]>(() => Array.from({ length: 6 }, (_, index) => {
  const count = index + 1;
  return { value: count, label: copy.value.resultCount(count) };
}));
const previewSuggestions = computed(() => knowledgePreviewSuggestions.map((suggestion) => ({
  ...suggestion,
  label: isEnglish.value ? suggestion.labelEn : suggestion.label,
})));
const scopedPreviewFallbackResults = computed(() => searchedQuery.value
  ? searchPreviewKnowledge(searchedQuery.value, chapterId.value, limit.value)
  : []);
const broaderPreviewFallbackResults = computed(() => searchedQuery.value
  ? searchPreviewKnowledge(searchedQuery.value, "", limit.value)
  : []);
const isPreviewResultSet = computed(() => isFixture.value || previewFallbackActive.value);
const showPreviewSuggestions = computed(() => isFixture.value
  && !loading.value
  && !loadingChapters.value
  && !displayedSearchError.value
  && !results.value.length);
const scopeExpandedActive = computed(() => Boolean(scopeExpandedFromChapterId.value));
const scopeExpandedChapterLabel = computed(() => {
  if (!scopeExpandedFromChapterId.value) return copy.value.allVisibleChapters;
  const matched = chapters.value.find((chapter) => chapter.id === scopeExpandedFromChapterId.value);
  return matched ? copy.value.chapterLabel(matched) : scopeExpandedFromChapterId.value;
});

function learnerSourceLabel(sourceLabel: string): string {
  return sourceLabel
    .replace(/本地\s*(?:fixture|示例)/gi, copy.value.previewSource)
    .replace(/fixture/gi, copy.value.previewSource);
}

function learnerReviewLabel(reviewStatus: string): string {
  if (isPreviewResultSet.value && /预览内容/i.test(reviewStatus)) return copy.value.exampleContent;
  return reviewStatus === "PUBLISHED" ? copy.value.reviewed : reviewStatus;
}

function localizedUserError(value: UserErrorPresentation): UserErrorPresentation {
  const localized = copy.value.errors[value.kind];
  return { ...value, ...localized };
}

async function loadChapters() {
  loadingChapters.value = true;
  chaptersError.value = null;
  if (!auth.state.user) {
    chapters.value = coursePreviewFixture.chapters;
    loadingChapters.value = false;
    return;
  }
  try { chapters.value = await userApi.listChapters(); } catch (cause) { chapters.value = []; chaptersError.value = presentUserError(cause); } finally { loadingChapters.value = false; }
}

async function search() {
  validationKey.value = null;
  error.value = null;
  const normalized = query.value.trim().replace(/\s+/g, " ");
  if (!normalized) { validationKey.value = "query-required"; return; }
  if (normalized.length > 500) { validationKey.value = "query-too-long"; return; }
  previewFallbackActive.value = false;
  scopeExpandedFromChapterId.value = "";
  loading.value = true;
  try {
    const submittedChapterId = chapterId.value;
    if (!auth.state.user) {
      // Guest knowledge search is chapter-scoped. Keep any lessonId in the URL
      // for navigation context, but do not let an old lesson filter hide
      // chapter-level knowledge such as 链表 when the current chapter is
      // 02-linear-list.
      let previewResults = searchPreviewKnowledge(normalized, submittedChapterId, limit.value);
      if (!previewResults.length && submittedChapterId) {
        const broaderResults = searchPreviewKnowledge(normalized, "", limit.value);
        if (broaderResults.length) {
          previewResults = broaderResults;
          scopeExpandedFromChapterId.value = submittedChapterId;
          chapterId.value = "";
        }
      }
      results.value = previewResults;
      searchedQuery.value = normalized;
    } else {
      const response = await userApi.searchKnowledge({ query: normalized, chapterId: submittedChapterId || undefined, limit: limit.value });
      results.value = response.results;
      // Keep the visible query anchored to the submitted text. A proxy or
      // older API implementation may echo a stale/normalized `query`; using
      // the request value keeps empty-state and example fallback labels honest.
      searchedQuery.value = normalized;
      // Keep the server response authoritative, but do not strand a learner
      // on an empty surface while the reviewed index is still being seeded.
      // Local matches are explicitly marked as examples below and never
      // become account records or server-visible results.
      if (!results.value.length) {
        const scopedPreview = searchPreviewKnowledge(normalized, submittedChapterId, limit.value);
        const broaderPreview = submittedChapterId ? searchPreviewKnowledge(normalized, "", limit.value) : scopedPreview;
        const fallbackResults = scopedPreview.length ? scopedPreview : broaderPreview;
        if (fallbackResults.length) {
          if (!scopedPreview.length && submittedChapterId) {
            scopeExpandedFromChapterId.value = submittedChapterId;
            chapterId.value = "";
          }
          previewFallbackActive.value = true;
          results.value = fallbackResults;
        }
      }
      if (!results.value.length && scopedPreviewFallbackResults.value.length) {
        previewFallbackActive.value = true;
        results.value = scopedPreviewFallbackResults.value;
      }
    }
    const preservedContext = ["from", "sessionId"].reduce<Record<string, string>>((context, key) => {
      const value = queryValue(route.query[key]);
      if (value) context[key] = value;
      return context;
    }, {});
    await router.replace({ query: {
      ...preservedContext,
      ...(normalized ? { q: normalized } : {}),
      ...(chapterId.value ? { chapterId: chapterId.value } : {}),
      ...(lessonId.value ? { lessonId: lessonId.value } : {}),
    } });
  } catch (cause) {
    results.value = [];
    error.value = presentUserError(cause);
  } finally {
    loading.value = false;
  }
}

function resetPreviewFallback(): void {
  previewFallbackActive.value = false;
  scopeExpandedFromChapterId.value = "";
  results.value = [];
}

function restoreExpandedScope(): void {
  if (!scopeExpandedFromChapterId.value) return;
  chapterId.value = scopeExpandedFromChapterId.value;
  scopeExpandedFromChapterId.value = "";
  previewFallbackActive.value = false;
  results.value = [];
  searchedQuery.value = "";
  error.value = null;
  validationKey.value = null;
  const preservedContext = ["from", "sessionId"].reduce<Record<string, string>>((context, key) => {
    const value = queryValue(route.query[key]);
    if (value) context[key] = value;
    return context;
  }, {});
  void router.replace({ query: {
    ...preservedContext,
    ...(query.value.trim() ? { q: query.value.trim() } : {}),
    ...(chapterId.value ? { chapterId: chapterId.value } : {}),
    ...(lessonId.value ? { lessonId: lessonId.value } : {}),
  } });
}

function runPreviewSuggestion(suggestion: KnowledgePreviewSuggestion): void {
  query.value = suggestion.query;
  chapterId.value = suggestion.chapterId;
  // Suggestions are chapter-level examples. Do not keep a previous lesson
  // filter from a contextual entry and accidentally hide the new result.
  lessonId.value = "";
  void search();
}

watch(
  () => route.query.chapterId,
  (value) => {
    const nextChapterId = queryValue(value);
    if (nextChapterId === chapterId.value) return;
    chapterId.value = nextChapterId;
    results.value = [];
    searchedQuery.value = "";
    error.value = null;
    validationKey.value = null;
  },
);

watch(
  () => route.query.lessonId,
  (value) => {
    const nextLessonId = queryValue(value);
    if (nextLessonId === lessonId.value) return;
    lessonId.value = nextLessonId;
    if (!chapterId.value && nextLessonId) chapterId.value = previewKnowledgeChapterId(nextLessonId) ?? "";
    results.value = [];
    searchedQuery.value = "";
    error.value = null;
    validationKey.value = null;
  },
);

watch(
  () => route.query.q,
  (value) => {
    const nextQuery = queryValue(value);
    if (nextQuery === query.value) return;
    query.value = nextQuery;
    if (nextQuery.trim()) void search();
    else {
      results.value = [];
      searchedQuery.value = "";
    }
  },
);

onMounted(async () => { await loadChapters(); if (query.value.trim()) await search(); });
watch(() => auth.state.user?.id, async () => { await loadChapters(); if (query.value.trim()) await search(); });
</script>

<template>
  <UserFrame shell="course">
    <section class="user-page" aria-labelledby="knowledge-title">
      <header class="user-page__heading"><div><p class="user-page__eyebrow">{{ copy.eyebrow }}</p><h1 id="knowledge-title">{{ copy.title }}</h1><p class="user-page__intro">{{ isFixture ? copy.fixtureIntro : copy.authenticatedIntro }}</p></div><span v-if="isFixture" class="user-preview-chip">{{ copy.fixtureBadge }}</span></header>
      <form class="user-form user-panel" role="search" @submit.prevent="search">
        <label>{{ copy.searchLabel }}<input v-model="query" maxlength="501" :placeholder="copy.searchPlaceholder" autocomplete="off" /><span v-if="validation" class="user-field-error" role="alert">{{ validation }}</span></label>
        <div class="user-form__split">
          <label>{{ copy.chapterScope }}<RuntimeSelect v-model="chapterId" :options="chapterOptions" :ariaLabel="copy.chapterScope" :disabled="loadingChapters || Boolean(chaptersError)" test-id="knowledge-chapter-scope" /></label>
          <label>{{ copy.resultLimit }}<RuntimeSelect v-model="limit" :options="resultLimitOptions" :ariaLabel="copy.resultLimit" test-id="knowledge-result-limit" /></label>
        </div>
        <div class="user-page__actions"><button class="user-action user-action--primary" type="submit" :disabled="loading">{{ loading ? copy.searching : copy.search }}</button><span class="user-list__meta">{{ isFixture ? copy.fixtureHint : copy.searchHint }}</span></div>
      </form>

      <UserState v-if="displayedChaptersError" :mode="displayedChaptersError.kind === 'permission' ? 'permission' : 'error'" :title="displayedChaptersError.title" :message="displayedChaptersError.message" :retry-label="displayedChaptersError.retryable ? copy.reloadChapters : undefined" @retry="loadChapters" />

      <UserState v-if="loading" mode="loading" :title="copy.loadingTitle" :message="copy.loadingDetail" />
      <UserState v-else-if="displayedSearchError" :mode="displayedSearchError.kind === 'permission' ? 'permission' : 'error'" :title="displayedSearchError.title" :message="displayedSearchError.message" :retry-label="displayedSearchError.retryable ? copy.reloadSearch : undefined" @retry="search" />
      <UserState v-else-if="searchedQuery && !results.length" mode="empty" :title="copy.emptyResultsTitle" :message="copy.emptyResultsDetail(searchedQuery)" />
      <section v-else-if="results.length" class="user-page__section" aria-live="polite">
        <div v-if="previewFallbackActive || scopeExpandedActive" class="knowledge-preview-fallback" role="status">
          <template v-if="scopeExpandedActive">
            <strong>{{ copy.scopeExpandedTitle }}</strong>
            <p>{{ copy.scopeExpandedDetail(scopeExpandedChapterLabel, searchedQuery) }}</p>
            <button class="user-action" type="button" @click="restoreExpandedScope">{{ copy.scopeExpandedReset }}</button>
          </template>
          <template v-if="previewFallbackActive">
          <strong>{{ copy.emptyResultsTitle }}</strong>
          <p>{{ copy.emptyResultsDetail(searchedQuery) }}</p>
          <strong>{{ copy.previewFallbackTitle }}</strong>
          <p>{{ copy.previewFallbackDetail }}</p>
          <button class="user-action" type="button" @click="resetPreviewFallback">{{ copy.previewFallbackReset }}</button>
          </template>
        </div>
        <header><h2>{{ copy.resultsTitle }}</h2><p>{{ isPreviewResultSet ? copy.previewResults(results.length) : copy.reviewedSources(results.length) }}</p></header><div class="user-list">
        <article v-for="result in results" :key="result.id" class="user-list__row user-list__row--stacked"><div><h3>{{ result.title }}</h3><p>{{ result.excerpt }}</p><p class="user-list__meta user-list__meta--left">{{ learnerSourceLabel(result.sourceLabel) }} · {{ result.locationLabel }} · {{ learnerReviewLabel(result.reviewStatus) }}</p></div><RouterLink v-if="result.chapterId" class="user-action" :to="{ path: `/user/chapters/${result.chapterId}`, query: { ...(result.lessonId ? { lessonId: result.lessonId } : {}), from: 'knowledge' } }">{{ copy.openChapter }}</RouterLink></article>
      </div></section>
      <UserState v-else mode="empty" :title="copy.initialEmptyTitle" :message="copy.initialEmptyDetail" />
      <section v-if="showPreviewSuggestions" class="knowledge-preview-suggestions" aria-labelledby="knowledge-preview-suggestions-title">
        <header class="knowledge-preview-suggestions__head">
          <div><h2 id="knowledge-preview-suggestions-title">{{ copy.previewSuggestionsTitle }}</h2><p>{{ copy.previewSuggestionsDetail }}</p></div>
        </header>
        <div class="knowledge-preview-suggestions__list" role="list">
          <button v-for="suggestion in previewSuggestions" :key="suggestion.query" class="knowledge-preview-suggestions__button" type="button" :aria-label="copy.previewSuggestionAria(suggestion.label)" @click="runPreviewSuggestion(suggestion)">{{ suggestion.label }}</button>
        </div>
      </section>
      <p v-if="!isFixture && searchedQuery && !results.length && !loading && !displayedSearchError" class="knowledge-index-note">{{ copy.authenticatedEmptyHint }}</p>
    </section>
    <template #rail><div class="user-rail-list"><strong>{{ copy.materialScope }}</strong><p>{{ isFixture ? copy.fixtureScope : copy.authenticatedScope }}</p><strong>{{ copy.searchRecord }}</strong><p>{{ isFixture ? copy.fixtureRecord : copy.authenticatedRecord }}</p></div></template>
  </UserFrame>
</template>
