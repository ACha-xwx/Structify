<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type { RouteLocationRaw } from "vue-router";
import { useRoute } from "vue-router";
import { auth } from "../../app/providers/runtime";
import { useLocale } from "../../shared/i18n/locale";
import { userApi } from "../runtime";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import type { Chapter } from "../../shared/types/course";
import { coursePreviewFixture, hasPreviewPresentation, previewChapter, previewPresentationLessonId } from "../fixtures/course-preview";
import { algorithmStageFixtures } from "../fixtures/algorithm-stage";
import { classroomPreviewScriptsForChapter } from "../fixtures/classroom-preview";
import {
  flattenCourseGroups,
  groupChaptersByCourseTopic,
  localizedCourseGroups,
  type WorkbenchCourseGroup,
  type WorkbenchCourseItem,
} from "../fixtures/learning-workbench";
import { localizedLearningTools } from "../workbench-navigation";

const chapters = ref<Chapter[]>([]);
const loading = ref(true);
const error = ref<UserErrorPresentation | null>(null);
const isFixture = computed(() => !auth.state.user);
const route = useRoute();
const { locale, isEnglish } = useLocale();

function readQueryValue(value: unknown): string {
  const candidates = Array.isArray(value) ? value : [value];
  return candidates.find((candidate): candidate is string => typeof candidate === "string" && Boolean(candidate.trim()))?.trim() ?? "";
}

const copy = computed(() => isEnglish.value ? {
  courseMap: "Course map",
  courseTitle: "Data Structures & Algorithms",
  courseDetail: "Choose a topic, then open a chapter or learning unit. The map shows the available curriculum without implying recorded progress.",
  topics: "topics",
  chapters: "chapters",
  units: "units",
  refresh: "Refresh map",
  fixture: "Local preview",
  published: "Published course",
  currentChapter: "Current chapter",
  nextSteps: "Learning actions",
  moreActions: "More learning actions",
  localPreviewNote: "The catalog is available for browsing. Each mapped lesson includes a local interactive example and does not create a learning record.",
  publishedNote: "Visible chapters come from the course service. Available actions keep the current chapter context.",
  openChapter: "Open chapter",
  courseTools: "Course tools",
  directTools: "Direct tools",
  source: "Sources",
  chapterContext: "Chapter context",
  availability: "Availability",
  loadingTitle: "Loading course map",
  loadingDetail: "Checking the course chapters available to this account.",
  retry: "Retry",
  emptyTitle: "No accessible chapters",
  emptyDetail: "The course service has not returned any chapters to browse yet.",
  chooseTitle: "Choose a chapter",
  chooseDetail: "The course map is ready. Open a chapter from a topic on the left.",
  localAvailable: "Available locally",
  chapterScope: "Chapter scope",
  byCourseAccess: "Course access",
  catalogReady: "Catalog ready",
  openCourseware: "Open courseware",
  openCoursewareDetail: "Read the course content for this chapter one page at a time.",
  coursewarePending: "Courseware not available yet",
  coursewarePendingDetail: "The course map is ready, but this chapter has no bundled courseware yet.",
  openStage: "Open algorithm stage",
  stagePending: "Algorithm stage not available yet",
  stageDetail: "Open a pausable operation trace for this chapter.",
  stagePendingDetail: "The course map is ready, but this chapter has no bundled algorithm scene yet.",
  openKnowledge: "Search chapter materials",
  knowledgeDetail: "Open reviewed knowledge evidence for this chapter.",
  openClassroom: "Open classroom",
  classroomPending: "Classroom not available yet",
  classroomDetail: "Move through practice with the classroom script.",
  classroomPendingDetail: "The course map is ready, but this chapter has no bundled classroom script yet.",
  openCode: "Run code practice",
  codeDetail: "Edit and verify code in this chapter context.",
  openCoach: "Ask about this chapter",
  coachDetail: "Open the Q&A coach with this chapter context.",
  modelSignIn: "Model actions require sign-in",
  chapterPrefix: "Chapter",
  errors: {
    permission: { title: "Sign in to unlock this feature", message: "Guests can browse public learning content. Sign in here to use model services or save personal learning records." },
    "not-found": { title: "Resource unavailable", message: "This resource may not be published, may have been removed, or may be outside the current account's access scope." },
    conflict: { title: "The current state has changed", message: "Refresh before continuing so the latest learning state is not overwritten." },
    limited: { title: "Too many requests", message: "The service is temporarily limiting requests. Try again shortly." },
    timeout: { title: "Request timed out", message: "An upstream service did not respond in time." },
    service: { title: "Learning service unavailable", message: "This page position has been kept. Try again when the service recovers." },
    network: { title: "Network connection unavailable", message: "Check the connection and try again. Your current learning position will not be lost." },
    validation: { title: "Unable to process this request", message: "Review the submitted information and try again." },
    unknown: { title: "Action not completed", message: "The service returned an unexpected result." },
  },
  accessDenied: { title: "Account access denied", message: "The service denied access to this learning resource or action." },
} : {
  courseMap: "课程地图",
  courseTitle: "数据结构与算法",
  courseDetail: "先选择主题，再展开章节或学习单元。课程地图展示可浏览的范围，不代表已经产生学习记录。",
  topics: "主题",
  chapters: "章",
  units: "单元",
  refresh: "刷新目录",
  fixture: "本地预览",
  published: "已发布课程",
  currentChapter: "当前章节",
  nextSteps: "学习入口",
  moreActions: "更多学习入口",
  localPreviewNote: "课程目录可直接浏览；每个已映射学习单元都提供本地交互示例，不会创建学习记录。",
  publishedNote: "当前可见章节来自课程服务，打开学习入口时会保留本章上下文。",
  openChapter: "打开章节",
  courseTools: "课程工具",
  directTools: "直接工具",
  source: "资料来源",
  chapterContext: "章节上下文",
  availability: "内容状态",
  loadingTitle: "正在读取章节目录",
  loadingDetail: "正在确认可访问的课程章节。",
  retry: "重试",
  emptyTitle: "暂无可访问章节",
  emptyDetail: "课程服务暂时没有返回可浏览的章节。",
  chooseTitle: "请选择一个章节",
  chooseDetail: "课程地图已加载，请从左侧主题中打开一个章节。",
  localAvailable: "本地可用",
  chapterScope: "章节范围",
  byCourseAccess: "按课程权限",
  catalogReady: "目录已建立",
  openCourseware: "播放本章课件",
  openCoursewareDetail: "按页查看当前章节的课程内容。",
  coursewarePending: "本章课件待接入",
  coursewarePendingDetail: "课程地图已建立，当前尚未绑定本地课件。",
  openStage: "打开算法舞台",
  stagePending: "本章算法舞台待接入",
  stageDetail: "进入当前章节的可暂停操作轨迹。",
  stagePendingDetail: "课程地图已建立，当前尚未绑定本地算法场景。",
  openKnowledge: "检索本章资料",
  knowledgeDetail: "从当前章节范围打开已审核的知识证据。",
  openClassroom: "进入课堂",
  classroomPending: "本章课堂待接入",
  classroomDetail: "按课堂脚本逐步推进练习。",
  classroomPendingDetail: "课程地图已建立，当前尚未绑定本地课堂脚本。",
  openCode: "运行代码实验",
  codeDetail: "在当前章节上下文中编辑并验证代码。",
  openCoach: "围绕本章提问",
  coachDetail: "从当前章节进入问答陪练。",
  modelSignIn: "模型动作需登录",
  chapterPrefix: "第",
  errors: {
    permission: { title: "登录后解锁此功能", message: "游客可以继续浏览公开学习内容；调用模型服务或保存个人学习记录时，请在此处登录后再试。" },
    "not-found": { title: "资源不可访问", message: "该资源可能未发布、已移除，或当前账号没有访问范围。" },
    conflict: { title: "当前状态已变化", message: "请刷新后继续操作，避免覆盖服务器中的最新学习状态。" },
    limited: { title: "请求过于频繁", message: "服务暂时限制了请求，请稍后重试。" },
    timeout: { title: "请求超时", message: "上游服务未在规定时间内响应。" },
    service: { title: "学习服务暂不可用", message: "当前页面位置已保留，服务恢复后可再次尝试。" },
    network: { title: "网络连接不可用", message: "请检查网络后重试，当前学习位置不会丢失。" },
    validation: { title: "提交内容无法处理", message: "请检查输入内容后重试。" },
    unknown: { title: "操作未完成", message: "服务返回了未预期的结果。" },
  },
  accessDenied: { title: "当前账号没有权限", message: "服务已拒绝此学习资源或操作。" },
});

const displayCourseHeading = computed(() => (
  !isFixture.value && chapters.value[0]?.title
    ? chapters.value[0].title
    : copy.value.courseTitle
));

interface ChapterAction {
  id: string;
  index: string;
  title: string;
  detail: string;
  meta: string;
  to?: RouteLocationRaw;
  disabled?: boolean;
}

// The chapter map is canonical, but the guest preview has one real playable
// lesson. Keep the map complete while the continuation rail points at that
// actual scene instead of implying that the first index has local materials.
const requestedChapterId = computed(() => {
  return readQueryValue(route.query.chapterId);
});
const requestedLessonId = computed(() => {
  return readQueryValue(route.query.lessonId);
});

function normalizeChapterId(chapterId: string): string {
  return chapterId.trim().toLocaleLowerCase();
}

function chapterForId(chapterId: string): Chapter | null {
  if (!chapterId) return null;
  const normalized = normalizeChapterId(chapterId);
  const directMatch = chapters.value.find((chapter) => normalizeChapterId(chapter.id) === normalized);
  if (directMatch) return directMatch;
  // The API publishes chapter 02 under its canonical id, while the workbench
  // uses the lesson-level id for the verified insertion preview. Resolve that
  // alias only for authenticated data; guests must keep the lesson fixture.
  if (!isFixture.value && normalized === "sequential-list") {
    return chapters.value.find((chapter) => normalizeChapterId(chapter.id) === "02-linear-list") ?? null;
  }
  return isFixture.value ? previewChapter(normalized) : null;
}

/**
 * A lesson row carries its parent chapter for API/resource scope and its own
 * id for the algorithm/classroom scene. Resolve either form without making a
 * lesson look like a second chapter in the main panel.
 */
const requestedLessonItem = computed<WorkbenchCourseItem | null>(() => {
  const id = normalizeChapterId(requestedLessonId.value || requestedChapterId.value);
  if (!id) return null;
  return flattenCourseGroups(localizedCourseGroups(locale.value)).find((item) => {
    if (item.kind !== "lesson") return false;
    // Child routeIds may intentionally reuse the parent chapter route. The
    // lesson id itself is the only unambiguous selector for a lesson scene.
    return normalizeChapterId(item.id) === id;
  }) ?? null;
});
const activeLessonItem = computed<WorkbenchCourseItem | null>(() => {
  if (requestedLessonItem.value) return requestedLessonItem.value;
  if (!requestedChapterId.value && isFixture.value) {
    return flattenCourseGroups(localizedCourseGroups(locale.value)).find((item) => item.kind === "lesson" && item.id === "sequential-list") ?? null;
  }
  return null;
});
const chapterLookupId = computed(() => {
  if (requestedLessonId.value) {
    if (requestedChapterId.value && chapters.value.some((chapter) => normalizeChapterId(chapter.id) === normalizeChapterId(requestedChapterId.value))) {
      return requestedChapterId.value;
    }
    return requestedLessonItem.value?.parentId || requestedLessonItem.value?.chapterId || requestedChapterId.value || "";
  }
  if (requestedLessonItem.value) {
    if (requestedChapterId.value && chapters.value.some((chapter) => normalizeChapterId(chapter.id) === normalizeChapterId(requestedChapterId.value))) {
      return requestedChapterId.value;
    }
    return requestedLessonItem.value.parentId || requestedLessonItem.value.chapterId || requestedChapterId.value;
  }
  return requestedChapterId.value;
});

const primaryChapter = computed(() => {
  if (isFixture.value && activeLessonItem.value) {
    const lessonPreview = previewChapter(activeLessonItem.value.id);
    if (lessonPreview) return lessonPreview;
  }
  const requested = chapterForId(chapterLookupId.value);
  if (requested) return requested;
  if (chapterLookupId.value) return null;
  if (isFixture.value) return previewChapter("sequential-list");
  return chapters.value[0] ?? null;
});

// The playable sequential-list lesson is nested in chapter 02. Keep the
// chapter map focused on its canonical chapter row even when the route carries
// the lesson-level id from the workbench.
const selectedChapterId = computed(() => {
  const id = normalizeChapterId(primaryChapter.value?.id ?? "");
  return id === "sequential-list" ? "02-linear-list" : id;
});

const courseGroups = computed<WorkbenchCourseGroup[]>(() => {
  const groups = isFixture.value
    ? localizedCourseGroups(locale.value)
    : localizedCourseGroups(locale.value, groupChaptersByCourseTopic(chapters.value, selectedChapterId.value));
  return groups.filter((group) => group.items.length > 0);
});

const courseCatalogStats = computed(() => {
  const items = flattenCourseGroups(courseGroups.value);
  return {
    topics: courseGroups.value.length,
    chapters: items.filter((item) => item.kind === "chapter").length,
    units: items.filter((item) => item.kind === "lesson").length,
  };
});
const primaryChapterItem = computed(() => {
  const selectedLesson = activeLessonItem.value;
  if (selectedLesson) {
    const matchingLesson = flattenCourseGroups(courseGroups.value).find((item) => (
      item.kind === "lesson"
      && normalizeChapterId(item.id) === normalizeChapterId(selectedLesson.id)
    ));
    if (matchingLesson) return matchingLesson;
    // The live chapter endpoint may return only chapter rows. The catalog
    // lesson still provides the learner-facing title and explanation while
    // the API-owned chapter remains the navigation scope.
    return selectedLesson;
  }
  const chapter = primaryChapter.value;
  if (!chapter) return null;
  const normalized = normalizeChapterId(chapter.id);
  return flattenCourseGroups(courseGroups.value).find((item) => (
    normalizeChapterId(item.id) === normalized
    || normalizeChapterId(item.routeId ?? "") === normalized
    || normalizeChapterId(item.chapterId ?? "") === normalized
  )) ?? null;
});
const displayPrimaryChapterTitle = computed(() => primaryChapterItem.value?.label ?? primaryChapter.value?.title ?? "");
const displayPrimaryChapterSummary = computed(() => primaryChapterItem.value?.summary ?? primaryChapter.value?.summary ?? "");
const primaryChapterLocation = computed(() => {
  const chapter = primaryChapter.value;
  if (!chapter) return copy.value.courseTitle;
  return isEnglish.value
    ? `${copy.value.chapterPrefix} ${chapter.chapterNumber}: ${displayPrimaryChapterTitle.value}`
    : `${copy.value.chapterPrefix} ${chapter.chapterNumber} 章 ${displayPrimaryChapterTitle.value}`;
});
function localizeError(current: UserErrorPresentation): UserErrorPresentation {
  const messages = current.kind === "permission" && current.title === "当前账号没有权限"
    ? copy.value.accessDenied
    : copy.value.errors[current.kind];
  return { ...current, ...messages };
}
const displayedError = computed<UserErrorPresentation | null>(() => error.value ? localizeError(error.value) : null);

const previewResource = computed(() => {
  const chapterId = activeLessonItem.value?.id || primaryChapter.value?.id;
  if (!isFixture.value || !chapterId) return null;
  return coursePreviewFixture.resourcesByChapter[chapterId]?.[0] ?? null;
});
const previewKnowledge = computed(() => {
  const chapterId = activeLessonItem.value?.id || primaryChapter.value?.id;
  if (!isFixture.value || !chapterId) return null;
  return coursePreviewFixture.knowledge.find((result) => result.chapterId === chapterId) ?? null;
});
function displayPreviewResourceTitle(): string {
  if (previewResource.value?.id === "preview-sequential-list-handout" && isEnglish.value) return "Sequential-list insertion steps";
  return previewResource.value?.title ?? copy.value.openKnowledge;
}
function displayPreviewResourceDetail(): string {
  if (previewResource.value?.id === "preview-sequential-list-handout" && isEnglish.value) return "A visual guide to shifting from the tail and writing the new value.";
  return previewResource.value?.description ?? copy.value.knowledgeDetail;
}
function displayPreviewKnowledgeTitle(): string {
  if (previewKnowledge.value?.id === "preview-sequential-list-insert" && isEnglish.value) return "Why shift from the tail?";
  return previewKnowledge.value?.title ?? copy.value.coachDetail;
}

const hasLocalAlgorithmScene = computed(() => {
  const id = activeLessonItem.value?.id || primaryChapter.value?.id;
  return isFixture.value && Boolean(id && algorithmStageFixtures[id]);
});
const hasLocalClassroom = computed(() => {
  const id = activeLessonItem.value?.id || primaryChapter.value?.id;
  return isFixture.value && Boolean(id && classroomPreviewScriptsForChapter(id).length);
});

const selectedLessonId = computed(() => {
  if (requestedLessonId.value) return requestedLessonId.value;
  if (activeLessonItem.value) return activeLessonItem.value.id;
  return "";
});

const contextChapterId = computed(() => {
  const chapter = primaryChapter.value;
  if (!chapter) return "";
  if (activeLessonItem.value) {
    return activeLessonItem.value.parentId || activeLessonItem.value.chapterId || chapter.id;
  }
  return chapter.id;
});

function chapterActionQuery(chapter: Chapter): Record<string, string> {
  return {
    chapterId: contextChapterId.value || chapter.id,
    ...(selectedLessonId.value ? { lessonId: selectedLessonId.value } : {}),
    from: "chapter",
  };
}

function presentationLessonId(chapter: Chapter): string {
  return previewPresentationLessonId(chapter);
}

const directTools = computed(() => {
  const chapter = primaryChapter.value;
  return localizedLearningTools(locale.value, {
    chapterId: contextChapterId.value || chapter?.id,
    lessonId: selectedLessonId.value || undefined,
    coursewareLessonId: chapter && hasPreviewPresentation(chapter.id) ? presentationLessonId(chapter) : undefined,
    from: "chapter",
  });
});

const primaryActions = computed<ChapterAction[]>(() => {
  const chapter = primaryChapter.value;
  if (!chapter) return [];
  const coursewareAvailable = !isFixture.value || hasPreviewPresentation(chapter.id);
  const sourceMeta = isFixture.value ? (previewResource.value ? copy.value.localAvailable : copy.value.chapterScope) : copy.value.byCourseAccess;
  const coursewareAction: ChapterAction = coursewareAvailable
    ? {
      id: "presentation",
      index: "01",
      title: copy.value.openCourseware,
      detail: copy.value.openCoursewareDetail,
      meta: isFixture.value ? copy.value.localAvailable : copy.value.byCourseAccess,
       to: { path: "/user/presentation", query: { lessonId: presentationLessonId(chapter), chapterId: contextChapterId.value || chapter.id, from: "chapter" } },
    }
    : {
      id: "presentation",
      index: "01",
      title: copy.value.coursewarePending,
      detail: copy.value.coursewarePendingDetail,
      meta: copy.value.catalogReady,
      disabled: true,
    };
  const sourceAction = previewResource.value
    ? {
      id: "resource",
      title: displayPreviewResourceTitle(),
      detail: displayPreviewResourceDetail(),
      to: `/user/resources/${previewResource.value.id}`,
    }
    : {
      id: "knowledge",
      title: copy.value.openKnowledge,
      detail: copy.value.knowledgeDetail,
       to: { path: "/user/knowledge", query: chapterActionQuery(chapter) },
    };
  return [
    coursewareAction,
    {
      id: "animation",
      index: "02",
      title: hasLocalAlgorithmScene.value || !isFixture.value ? copy.value.openStage : copy.value.stagePending,
      detail: hasLocalAlgorithmScene.value || !isFixture.value
        ? (isEnglish.value ? `Open a pausable operation trace for ${displayPrimaryChapterTitle.value}.` : `进入“${displayPrimaryChapterTitle.value}”的可暂停操作轨迹。`)
        : copy.value.stagePendingDetail,
      meta: hasLocalAlgorithmScene.value || !isFixture.value ? (isFixture.value ? copy.value.localAvailable : copy.value.byCourseAccess) : copy.value.catalogReady,
      ...(hasLocalAlgorithmScene.value || !isFixture.value
          ? { to: { path: "/user/animation", query: chapterActionQuery(chapter) } }
        : { disabled: true }),
    },
    {
      id: sourceAction.id,
      index: "03",
      title: sourceAction.title,
      detail: sourceAction.detail,
      meta: sourceMeta,
      to: sourceAction.to,
    },
  ];
});

const secondaryActions = computed<ChapterAction[]>(() => {
  const chapter = primaryChapter.value;
  if (!chapter) return [];
  return [
    {
      id: "classroom",
      index: "04",
      title: hasLocalClassroom.value || !isFixture.value ? copy.value.openClassroom : copy.value.classroomPending,
      detail: hasLocalClassroom.value || !isFixture.value
        ? copy.value.classroomDetail
        : copy.value.classroomPendingDetail,
      meta: hasLocalClassroom.value || !isFixture.value ? (isFixture.value ? copy.value.localAvailable : copy.value.byCourseAccess) : copy.value.catalogReady,
      ...(hasLocalClassroom.value || !isFixture.value
          ? { to: { path: "/user/classroom", query: chapterActionQuery(chapter) } }
        : { disabled: true }),
    },
    {
      id: "code",
      index: "05",
      title: copy.value.openCode,
      detail: copy.value.codeDetail,
      meta: copy.value.modelSignIn,
      to: { path: "/user/code", query: chapterActionQuery(chapter) },
    },
    {
      id: "coach",
      index: "06",
      title: copy.value.openCoach,
      detail: displayPreviewKnowledgeTitle(),
      meta: copy.value.modelSignIn,
      to: { path: "/user/coach", query: chapterActionQuery(chapter) },
    },
  ];
});

async function load() {
  loading.value = true;
  error.value = null;
  if (!auth.state.user) {
    chapters.value = coursePreviewFixture.chapters;
    loading.value = false;
    return;
  }
  try { chapters.value = await userApi.listChapters(); } catch (cause) { error.value = presentUserError(cause); } finally { loading.value = false; }
}
onMounted(load);
watch(() => auth.state.user?.id, () => { void load(); });
</script>

<template>
  <UserFrame shell="course">
    <section class="chapters-runtime" aria-labelledby="chapters-title">
      <header class="chapters-runtime__heading">
        <div>
          <p class="chapters-runtime__eyebrow">{{ copy.courseMap }}</p>
          <h1 id="chapters-title">{{ displayCourseHeading }}</h1>
          <p>{{ copy.courseDetail }}</p>
          <div class="chapters-runtime__scope" :aria-label="`${courseCatalogStats.topics} ${copy.topics} · ${courseCatalogStats.chapters} ${copy.chapters} · ${courseCatalogStats.units} ${copy.units}`">
            <span><strong>{{ courseCatalogStats.topics }}</strong>{{ copy.topics }}</span>
            <span><strong>{{ courseCatalogStats.chapters }}</strong>{{ copy.chapters }}</span>
            <span><strong>{{ courseCatalogStats.units }}</strong>{{ copy.units }}</span>
          </div>
        </div>
        <div class="chapters-runtime__heading-actions">
          <span class="chapters-runtime__status" :data-source="isFixture ? 'fixture' : 'published'">{{ isFixture ? copy.fixture : copy.published }}</span>
          <button type="button" :disabled="loading" @click="load">{{ copy.refresh }}</button>
        </div>
      </header>

      <UserState v-if="loading" mode="loading" :title="copy.loadingTitle" :message="copy.loadingDetail" />
      <UserState v-else-if="displayedError" :mode="displayedError.kind === 'permission' ? 'permission' : 'error'" :title="displayedError.title" :message="displayedError.message" :retry-label="copy.retry" @retry="load" />
      <UserState v-else-if="!chapters.length" mode="empty" :title="copy.emptyTitle" :message="copy.emptyDetail" />

      <div v-else class="chapters-runtime__surface">
        <main class="chapters-runtime__main">
          <section v-if="primaryChapter" class="chapters-runtime__chapter" :data-chapter-id="primaryChapter.id" aria-labelledby="current-chapter-title">
            <header>
              <p>{{ copy.currentChapter }} / {{ String(primaryChapter.chapterNumber).padStart(2, '0') }}</p>
              <span>{{ isFixture ? copy.fixture : copy.published }}</span>
            </header>
            <h2 id="current-chapter-title">{{ displayPrimaryChapterTitle }}</h2>
            <p class="chapters-runtime__chapter-summary">{{ displayPrimaryChapterSummary }}</p>
            <p class="chapters-runtime__boundary">{{ isFixture ? copy.localPreviewNote : copy.publishedNote }}</p>

            <section class="chapters-runtime__actions" :aria-label="copy.nextSteps">
              <header><p>{{ copy.nextSteps }}</p><span>{{ String(primaryChapter.chapterNumber).padStart(2, '0') }}</span></header>
              <template v-for="action in primaryActions" :key="action.id">
                <RouterLink v-if="action.to" class="chapters-runtime__action" :data-action-id="action.id" :to="action.to">
                  <span>{{ action.index }}</span>
                  <span><strong>{{ action.title }}</strong><small>{{ action.detail }}</small></span>
                  <small>{{ action.meta }}</small>
                  <b aria-hidden="true">&rarr;</b>
                </RouterLink>
                <span v-else class="chapters-runtime__action is-disabled" :data-action-id="action.id" aria-disabled="true">
                  <span>{{ action.index }}</span>
                  <span><strong>{{ action.title }}</strong><small>{{ action.detail }}</small></span>
                  <small>{{ action.meta }}</small>
                  <b aria-hidden="true">-</b>
                </span>
              </template>
            </section>

            <details class="chapters-runtime__more">
              <summary><span>{{ copy.moreActions }}</span><span>{{ secondaryActions.map((action) => action.title).join(' · ') }}</span><i aria-hidden="true"></i></summary>
              <div>
                <template v-for="action in secondaryActions" :key="action.id">
                  <RouterLink v-if="action.to" class="chapters-runtime__action" :data-action-id="action.id" :to="action.to">
                    <span>{{ action.index }}</span>
                    <span><strong>{{ action.title }}</strong><small>{{ action.detail }}</small></span>
                    <small>{{ action.meta }}</small>
                    <b aria-hidden="true">&rarr;</b>
                  </RouterLink>
                  <span v-else class="chapters-runtime__action is-disabled" :data-action-id="action.id" aria-disabled="true">
                    <span>{{ action.index }}</span>
                    <span><strong>{{ action.title }}</strong><small>{{ action.detail }}</small></span>
                    <small>{{ action.meta }}</small>
                    <b aria-hidden="true">-</b>
                  </span>
                </template>
              </div>
            </details>
          </section>
          <UserState v-else mode="empty" :title="copy.chooseTitle" :message="copy.chooseDetail" />
        </main>
      </div>
    </section>
    <template #rail>
      <aside class="chapters-runtime__rail" :aria-label="copy.chapterContext">
        <details open>
          <summary><span>01</span>{{ copy.chapterContext }}<i aria-hidden="true"></i></summary>
          <div><strong>{{ primaryChapterLocation }}</strong><p>{{ displayPrimaryChapterSummary || copy.courseDetail }}</p></div>
        </details>
        <details open>
          <summary><span>02</span>{{ copy.availability }}<i aria-hidden="true"></i></summary>
          <div><strong>{{ isFixture ? copy.fixture : copy.published }}</strong><p>{{ isFixture ? copy.localPreviewNote : copy.publishedNote }}</p></div>
        </details>
        <section class="chapters-runtime__tools" :aria-label="copy.directTools">
          <p>{{ copy.courseTools }}</p>
          <RouterLink v-for="tool in directTools" :key="tool.id" :to="tool.to"><span>{{ tool.label }}</span><small>{{ tool.meta }}</small><b aria-hidden="true">&rarr;</b></RouterLink>
        </section>
      </aside>
    </template>
  </UserFrame>
</template>

<style scoped>
.chapters-map,
.chapters-next { display: grid; gap: .9rem; min-width: 0; }
.chapters-map { padding-top: .15rem; }
.chapters-map__header,
.chapters-next__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; }
.chapters-map__header h2,
.chapters-next__header h2 { margin: 0; color: var(--text); font-size: 1.04rem; letter-spacing: 0; }
.chapters-map__code { margin: 0 0 .3rem; color: var(--text-muted); font-family: var(--font-mono); font-size: .62rem; letter-spacing: .1em; }
.chapters-map__source,
.chapters-next__header > p { margin: 0; color: var(--text-muted); font-family: var(--font-mono); font-size: .62rem; letter-spacing: .05em; text-align: right; }
.chapters-map__track { position: relative; display: grid; gap: .65rem; padding: .15rem 0 .1rem 1.25rem; }
.chapters-map__track::before { position: absolute; top: 1rem; bottom: 1.1rem; left: .32rem; width: 1px; background: var(--line-strong); content: ""; }
.chapters-map__chapter { position: relative; z-index: 1; display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: .8rem; align-items: center; min-width: 0; padding: 1rem .9rem .95rem .7rem; border: 1px solid color-mix(in srgb, var(--line) 90%, transparent); border-radius: 8px; background: color-mix(in srgb, var(--surface) 82%, transparent); color: var(--text); text-decoration: none; transition: transform 160ms cubic-bezier(.16, 1, .3, 1), border-color 160ms ease, background-color 160ms ease; }
.chapters-map__chapter::before { position: absolute; top: 1.25rem; left: -1.22rem; width: .54rem; height: .54rem; border: 2px solid var(--surface); border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 1px var(--line-strong); content: ""; }
.chapters-map__chapter.is-selected { border-color: var(--line-strong); background: var(--surface); box-shadow: inset 3px 0 0 var(--accent), 0 8px 22px color-mix(in srgb, var(--text) 9%, transparent); }
.chapters-map__chapter.is-selected::before { width: .68rem; height: .68rem; background: var(--accent-strong); box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 18%, transparent); }
.chapters-map__chapter.is-selected .chapters-map__index { border-color: var(--text); background: var(--accent-soft); color: var(--text); }
.chapters-map__chapter:focus-visible,
.chapters-next__action:focus-visible,
.chapters-next__more summary:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.chapters-map__chapter:hover { transform: translateX(3px); border-color: var(--line-strong); background: var(--surface); }
.chapters-map__index,
.chapters-next__action-index { display: grid; width: 2.25rem; height: 2.25rem; place-items: center; border: 1px solid var(--line-strong); color: var(--text-muted); font-family: var(--font-mono); font-size: .68rem; }
.chapters-map__copy,
.chapters-next__action-copy { display: grid; min-width: 0; gap: .22rem; }
.chapters-map__copy strong { overflow: hidden; font-size: .95rem; text-overflow: ellipsis; white-space: nowrap; }
.chapters-map__copy span,
.chapters-next__action-copy span { color: var(--text-muted); font-size: .77rem; line-height: 1.5; }
.chapters-map__open { display: inline-flex; align-items: center; gap: .32rem; color: var(--text-muted); font-family: var(--font-mono); font-size: .67rem; white-space: nowrap; }
.chapters-map__open span { color: var(--text); font-size: .9rem; }
.chapters-map__handoff { display: flex; align-items: center; gap: .6rem; margin: .1rem 0 0; color: var(--text-muted); font-size: .75rem; }
.chapters-map__handoff > span:first-child { display: grid; width: 1.15rem; height: 1.15rem; place-items: center; color: var(--text); font-family: var(--font-mono); }
.chapters-next { padding-top: 1.1rem; border-top: 1px solid color-mix(in srgb, var(--line) 78%, transparent); }
.chapters-next__grid,
.chapters-next__more-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .55rem; }
.chapters-next__action { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: .65rem; align-items: center; min-width: 0; padding: .72rem .68rem; border: 1px solid color-mix(in srgb, var(--line) 86%, transparent); border-radius: 6px; background: color-mix(in srgb, var(--surface) 68%, transparent); color: var(--text); text-decoration: none; transition: transform 150ms cubic-bezier(.16, 1, .3, 1), border-color 150ms ease, background-color 150ms ease; }
.chapters-next__action:hover { transform: translateY(-2px); border-color: var(--line-strong); background: var(--surface); }
.chapters-next__action--disabled { cursor: default; opacity: .68; }
.chapters-next__action-index { width: 1.7rem; height: 1.7rem; border: 0; color: var(--text-muted); font-size: .6rem; }
.chapters-next__action-copy strong { overflow: hidden; font-size: .8rem; text-overflow: ellipsis; white-space: nowrap; }
.chapters-next__action-copy span { display: -webkit-box; overflow: hidden; font-size: .7rem; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.chapters-next__action-meta { color: var(--text-muted); font-family: var(--font-mono); font-size: .58rem; text-align: right; white-space: nowrap; }
.chapters-next__action-arrow { align-self: start; color: var(--text-muted); font-size: .95rem; line-height: 1; }
.chapters-next__more { border-top: 1px solid var(--line); }
.chapters-next__more summary { display: flex; min-height: 2.8rem; align-items: center; gap: .65rem; color: var(--text); cursor: pointer; list-style: none; }
.chapters-next__more summary::-webkit-details-marker { display: none; }
.chapters-next__more summary > span:first-child { font-size: .78rem; font-weight: 650; }
.chapters-next__more summary > span:nth-child(2) { flex: 1; color: var(--text-muted); font-family: var(--font-mono); font-size: .6rem; letter-spacing: .04em; }
.chapters-next__more summary > span:last-child { color: var(--text-muted); font-size: 1.1rem; transition: transform 150ms ease; }
.chapters-next__more[open] summary > span:last-child { transform: rotate(45deg); }
.chapters-next__more-grid { padding: .1rem 0 .75rem; }
.chapters-next__boundary { margin: 0; color: var(--text-muted); font-size: .7rem; line-height: 1.55; }

@media (max-width: 760px) {
  .chapters-map__header,
  .chapters-next__header { align-items: flex-start; flex-direction: column; gap: .4rem; }
  .chapters-map__source,
  .chapters-next__header > p { text-align: left; }
  .chapters-map__chapter { grid-template-columns: auto minmax(0, 1fr); }
  .chapters-map__open { grid-column: 2; }
  .chapters-next__grid,
  .chapters-next__more-grid { grid-template-columns: 1fr; }
}

@media (max-width: 430px) {
  .chapters-map__track { padding-left: 1rem; }
  .chapters-map__track::before { left: .2rem; }
  .chapters-map__chapter::before { left: -1rem; }
  .chapters-map__chapter { gap: .6rem; padding-right: .65rem; padding-left: .6rem; }
  .chapters-map__copy strong { white-space: normal; }
  .chapters-next__action { grid-template-columns: auto minmax(0, 1fr) auto; }
  .chapters-next__action-meta { grid-column: 2 / -1; grid-row: 2; justify-self: start; text-align: left; }
}

@media (prefers-reduced-motion: reduce) {
  .chapters-map__chapter,
  .chapters-next__action,
  .chapters-next__more summary > span:last-child { transition: none; }
}

@media (prefers-reduced-transparency: reduce) {
  .chapters-map__chapter,
  .chapters-next__action { background: var(--surface); }
}
</style>

<style scoped src="./ChaptersRuntime.css"></style>
