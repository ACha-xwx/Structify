<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { auth } from "../../app/providers/runtime";
import { userApi } from "../runtime";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import type { Chapter, Resource } from "../../shared/types/contracts";
import { hasPreviewPresentation, previewChapter, previewPresentationLessonId, previewResources } from "../fixtures/course-preview";
import { flattenCourseGroups, localizedCourseGroups } from "../fixtures/learning-workbench";
import { useLocale } from "../../shared/i18n/locale";

const route = useRoute();
const { locale, isEnglish } = useLocale();
const chapterId = computed(() => String(route.params.chapterId || ""));
const chapter = ref<Chapter | null>(null);
const resources = ref<Resource[]>([]);
const loading = ref(true);
const error = ref<UserErrorPresentation | null>(null);
const isFixture = computed(() => !auth.state.user);
let loadVersion = 0;

const copy = computed(() => isEnglish.value ? {
  currentChapter: "Current chapter",
  chapterDetail: "Chapter detail",
  backToMap: "Back to course map",
  fixture: "Local preview",
  loadingTitle: "Loading chapter materials",
  loadingDetail: "Checking this chapter and its available learning materials.",
  reload: "Reload",
  chapterGoal: "Chapter goal",
  sequentialGoal: "First observe insertion into a sequential list, then explain the shift boundary.",
  generalGoal: "This chapter is ready to explore. Courseware, Q&A, code practice, and personal records become available with their respective capabilities.",
  openCourseware: "Open courseware",
  coursewarePending: "Courseware not available yet",
  openCoach: "Ask about this chapter",
  openClassroom: "Open chapter classroom",
  openStage: "Open algorithm stage",
  stagePending: "Algorithm stage not available yet",
  openCode: "Run code in this chapter",
  openKnowledge: "Search chapter knowledge",
  fixtureEntry: "Preview learning entry",
  contentBoundary: "Chapter content",
  fixtureEntryDetail: "Courseware and algorithm interaction can be opened directly.",
  fixtureBoundaryDetail: "This chapter is on the course map, but it has no local materials or algorithm scene yet.",
  publishedBoundaryDetail: "Only course materials available to this account are shown.",
  noResourcesTitle: "No accessible materials in this chapter",
  noResourcesDetail: "The chapter may be empty, or its materials may not be available yet.",
  fixtureDetail: "This local preview is available to browse without creating a course record, permission grant, or learning record.",
  noResourceSummary: "This material has no summary.",
  fixtureStatus: "Preview course content",
  publishedStatus: "Published course chapter",
  learningPosition: "Learning position",
  notSelected: "No chapter selected",
  chapterPrefix: "Chapter",
  errors: {
    permission: { title: "Sign in to unlock this feature", message: "Guests can browse public learning content. Sign in here to use model services or save personal learning records." },
    "not-found": { title: "Chapter unavailable", message: "Return to the course map and choose an available chapter." },
    conflict: { title: "The current state has changed", message: "Reload before continuing so the latest learning state is not overwritten." },
    limited: { title: "Too many requests", message: "The service is temporarily limiting requests. Try again shortly." },
    timeout: { title: "Request timed out", message: "An upstream service did not respond in time." },
    service: { title: "Learning service unavailable", message: "This page position has been kept. Try again when the service recovers." },
    network: { title: "Network connection unavailable", message: "Check the connection and try again. Your current learning position will not be lost." },
    validation: { title: "Unable to process this request", message: "Review the submitted information and try again." },
    unknown: { title: "Action not completed", message: "The service returned an unexpected result." },
  },
  accessDenied: { title: "Account access denied", message: "The service denied access to this learning resource or action." },
  licenses: { PUBLIC: "Public material", TEAM_ONLY: "Course material", CLASSROOM_ONLY: "Classroom material" },
} : {
  currentChapter: "当前章节",
  chapterDetail: "章节详情",
  backToMap: "返回目录",
  fixture: "示例预览",
  loadingTitle: "正在加载章节资料",
  loadingDetail: "正在读取章节与资源可见性。",
  reload: "重新加载",
  chapterGoal: "章节目标",
  sequentialGoal: "先观察顺序表的插入轨迹，再把移动边界说清楚。",
  generalGoal: "本章目录已经建立；课件可按页查看，模型问答、代码执行和个人记录会在对应能力接入后解锁。",
  openCourseware: "播放本章课件",
  coursewarePending: "本章课件待接入",
  openCoach: "在本章提问",
  openClassroom: "进入本章课堂",
  openStage: "演示本章算法",
  stagePending: "本章算法舞台待接入",
  openCode: "在本章运行代码",
  openKnowledge: "检索本章知识",
  fixtureEntry: "示例学习入口",
  contentBoundary: "章节内容边界",
  fixtureEntryDetail: "课件播放和算法操作可以直接打开。",
  fixtureBoundaryDetail: "本章已进入课程地图；暂未绑定本地资源或算法场景。",
  publishedBoundaryDetail: "只显示当前账号可以使用的课程资料。",
  noResourcesTitle: "本章暂无可访问资源",
  noResourcesDetail: "这可能表示章节为空，或资源仍未开放。",
  fixtureDetail: "仅用于未登录浏览，不代表已发布课程目录、账号权限或学习记录。",
  noResourceSummary: "该资源没有提供摘要。",
  fixtureStatus: "示例课程内容",
  publishedStatus: "已开放课程章节",
  learningPosition: "学习位置",
  notSelected: "未选择章节",
  chapterPrefix: "第",
  errors: {
    permission: { title: "登录后解锁此功能", message: "游客可以继续浏览公开学习内容；调用模型服务或保存个人学习记录时，请在此处登录后再试。" },
    "not-found": { title: "暂时找不到这个章节", message: "请回到课程目录，重新选择一个可学习的章节。" },
    conflict: { title: "当前状态已变化", message: "请刷新后继续操作，避免覆盖服务器中的最新学习状态。" },
    limited: { title: "请求过于频繁", message: "服务暂时限制了请求，请稍后重试。" },
    timeout: { title: "请求超时", message: "上游服务未在规定时间内响应。" },
    service: { title: "学习服务暂不可用", message: "当前页面位置已保留，服务恢复后可再次尝试。" },
    network: { title: "网络连接不可用", message: "请检查网络后重试，当前学习位置不会丢失。" },
    validation: { title: "提交内容无法处理", message: "请检查输入内容后重试。" },
    unknown: { title: "操作未完成", message: "服务返回了未预期的结果。" },
  },
  accessDenied: { title: "当前账号没有权限", message: "服务已拒绝此学习资源或操作。" },
  licenses: { PUBLIC: "公开资料", TEAM_ONLY: "课程资料", CLASSROOM_ONLY: "课堂资料" },
});

const localizedCourseItems = computed(() => flattenCourseGroups(localizedCourseGroups(locale.value)));
const localizedChapterItem = computed(() => {
  const id = chapter.value?.id;
  if (!id) return null;
  return localizedCourseItems.value.find((item) => item.id === id || item.routeId === id || item.chapterId === id) ?? null;
});
const displayChapterTitle = computed(() => localizedChapterItem.value?.label ?? chapter.value?.title ?? "");
const displayChapterSummary = computed(() => localizedChapterItem.value?.summary ?? chapter.value?.summary ?? "");
const chapterLocation = computed(() => {
  if (!chapter.value) return copy.value.notSelected;
  return isEnglish.value
    ? `${copy.value.chapterPrefix} ${chapter.value.chapterNumber}: ${displayChapterTitle.value}`
    : `${copy.value.chapterPrefix} ${chapter.value.chapterNumber} 章 ${displayChapterTitle.value}`;
});
function localizeError(current: UserErrorPresentation): UserErrorPresentation {
  const messages = current.kind === "permission" && current.title === "当前账号没有权限"
    ? copy.value.accessDenied
    : copy.value.errors[current.kind];
  return { ...current, ...messages };
}
const displayedError = computed<UserErrorPresentation | null>(() => error.value ? localizeError(error.value) : null);
function displayResourceTitle(resource: Resource): string {
  if (isEnglish.value && resource.id === "preview-sequential-list-handout") return "Sequential-list insertion steps";
  return resource.title;
}
function displayResourceDescription(resource: Resource): string {
  if (isEnglish.value && resource.id === "preview-sequential-list-handout") return "A visual guide to shifting from the tail and writing the new value.";
  return resource.description || copy.value.noResourceSummary;
}
function displayResourceType(resource: Resource): string {
  if (isEnglish.value && resource.id === "preview-sequential-list-handout") return "Illustrated handout";
  return resource.type;
}
const hasSequentialListPreview = computed(() => chapter.value?.id === "sequential-list" || chapter.value?.id === "02-linear-list");
const stageChapterId = computed(() => hasSequentialListPreview.value ? "sequential-list" : chapter.value?.id ?? "");
const presentationLessonId = computed(() => chapter.value ? previewPresentationLessonId(chapter.value) : "");
const hasPreviewCourseware = computed(() => !isFixture.value || Boolean(chapter.value && hasPreviewPresentation(chapter.value.id)));

async function load() {
  const version = ++loadVersion;
  loading.value = true;
  error.value = null;
  chapter.value = null;
  resources.value = [];
  if (!auth.state.user) {
    chapter.value = previewChapter(chapterId.value);
    resources.value = previewResources(chapterId.value);
    if (!chapter.value) error.value = { kind: "not-found", title: "", message: "", retryable: false };
    loading.value = false;
    return;
  }
  try {
    const chapters = await userApi.listChapters();
    if (version !== loadVersion) return;
    chapter.value = chapters.find((item) => item.id === chapterId.value) ?? null;
    if (!chapter.value) { error.value = { kind: "not-found", title: "", message: "", retryable: false }; return; }
    const nextResources = await userApi.listResources(chapterId.value);
    if (version === loadVersion) resources.value = nextResources;
  } catch (cause) {
    if (version === loadVersion) error.value = presentUserError(cause);
  } finally {
    if (version === loadVersion) loading.value = false;
  }
}
watch(chapterId, () => { void load(); }, { immediate: true });
watch(() => auth.state.user?.id, () => { void load(); });
</script>

<template>
  <UserFrame shell="course">
    <section class="user-page" aria-labelledby="chapter-detail-title">
      <header class="user-page__heading"><div><p class="user-page__eyebrow">{{ copy.currentChapter }}</p><h1 id="chapter-detail-title">{{ chapter ? chapterLocation : copy.chapterDetail }}</h1><p class="user-page__intro">{{ displayChapterSummary || (isFixture ? copy.fixtureDetail : copy.publishedBoundaryDetail) }}</p></div><div class="user-page__actions"><span v-if="isFixture" class="user-preview-chip">{{ copy.fixture }}</span><RouterLink class="user-action" to="/user/chapters">{{ copy.backToMap }}</RouterLink></div></header>
      <UserState v-if="loading" mode="loading" :title="copy.loadingTitle" :message="copy.loadingDetail" />
      <UserState v-else-if="displayedError" :mode="displayedError.kind === 'permission' ? 'permission' : 'error'" :title="displayedError.title" :message="displayedError.message" :retry-label="displayedError.retryable ? copy.reload : undefined" @retry="load" />
      <template v-else-if="chapter">
        <section class="user-panel"><h2>{{ copy.chapterGoal }}</h2><p>{{ hasSequentialListPreview ? copy.sequentialGoal : copy.generalGoal }}</p><div class="user-page__actions"><RouterLink v-if="hasPreviewCourseware" class="user-action user-action--primary" :to="{ path: '/user/presentation', query: { lessonId: presentationLessonId, chapterId, from: 'chapter' } }">{{ copy.openCourseware }}</RouterLink><span v-else class="user-action user-action--disabled" aria-disabled="true">{{ copy.coursewarePending }}</span><RouterLink class="user-action" :to="{ path: '/user/coach', query: { chapterId } }">{{ copy.openCoach }}</RouterLink><RouterLink class="user-action" :to="{ path: '/user/classroom', query: { chapterId } }">{{ copy.openClassroom }}</RouterLink><RouterLink v-if="hasSequentialListPreview || !isFixture" class="user-action" :to="{ path: '/user/animation', query: { chapterId: stageChapterId, from: 'chapter' } }">{{ copy.openStage }}</RouterLink><span v-else class="user-action user-action--disabled" aria-disabled="true">{{ copy.stagePending }}</span><RouterLink class="user-action" :to="{ path: '/user/code', query: { chapterId } }">{{ copy.openCode }}</RouterLink><RouterLink class="user-action" :to="{ path: '/user/knowledge', query: { chapterId } }">{{ copy.openKnowledge }}</RouterLink></div></section>
        <section class="user-page__section"><header><h2>{{ isFixture && hasSequentialListPreview ? copy.fixtureEntry : copy.contentBoundary }}</h2><p>{{ isFixture && hasSequentialListPreview ? copy.fixtureEntryDetail : (isFixture ? copy.fixtureBoundaryDetail : copy.publishedBoundaryDetail) }}</p></header><UserState v-if="!resources.length && !isFixture" mode="empty" :title="copy.noResourcesTitle" :message="copy.noResourcesDetail" /><p v-if="isFixture" class="inline-notice inline-notice--warning">{{ copy.fixtureDetail }}</p><div v-else-if="resources.length" class="user-list"><RouterLink v-for="resource in resources" :key="resource.id" class="user-list__row" :to="`/user/resources/${resource.id}`"><div><h3>{{ displayResourceTitle(resource) }}</h3><p>{{ displayResourceDescription(resource) }}</p></div><span class="user-list__meta">{{ displayResourceType(resource) }} · {{ copy.licenses[resource.licenseScope] }}</span></RouterLink></div></section>
      </template>
    </section>
    <template #rail><div class="user-rail-list"><strong>{{ copy.contentBoundary }}</strong><p>{{ isFixture ? copy.fixtureStatus : copy.publishedStatus }}</p><strong>{{ copy.learningPosition }}</strong><p>{{ chapterLocation }}</p></div></template>
  </UserFrame>
</template>
