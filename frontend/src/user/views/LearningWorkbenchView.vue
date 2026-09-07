<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { auth } from "../../app/providers/runtime";
import ThemeToggle from "../../shared/design/ThemeToggle.vue";
import { useLocale } from "../../shared/i18n/locale";
import { createLearningWorkbenchPreview, loadLearningWorkbench, type LearningWorkbenchSnapshot } from "../adapters/learning-workbench";
import { launchPublishedChapterStage, liveStagePointerIndex, liveStageValue, nodesAtLiveWorkbenchStep, type WorkbenchLiveStage } from "../adapters/workbench-stage-launch";
import { courseItemNavigationContext, courseItemRouteId, flattenCourseGroups, learningWorkbenchFixture, localizeCourseCapability, nodesAtWorkbenchStep, type WorkbenchCourseCapability, type WorkbenchCourseItem, type WorkbenchCourseGroup } from "../fixtures/learning-workbench";
import { coursePreviewFixture, hasPreviewPresentation, previewPresentationLessonId } from "../fixtures/course-preview";
import { isLearningGlobalNavigationActive, localizedLearningGlobalNavigation, localizedLearningNavigation, localizedLearningTools } from "../workbench-navigation";
import { createLoginTarget } from "../login-target";
import { userApi } from "../runtime";
import { presentUserError, type UserErrorPresentation } from "../errors";

const router = useRouter();
const route = useRoute();
const loginTarget = computed(() => createLoginTarget(route.fullPath));
const snapshot = ref<LearningWorkbenchSnapshot | null>(null);
const liveStage = ref<WorkbenchLiveStage | null>(null);
const launchingLiveStage = ref(false);
const synchronizing = ref(false);
const stepIndex = ref(0);
const isPlaying = ref(false);
const searchOpen = ref(false);
const searchQuery = ref("");
const searchInput = ref<HTMLInputElement | null>(null);
const searchTrigger = ref<HTMLButtonElement | null>(null);
const stageHeading = ref<HTMLHeadingElement | null>(null);
const openCourseGroupIds = ref<Set<string>>(new Set());
const sidebarOpen = ref(true);
let playTimer: number | undefined;

const scene = computed(() => snapshot.value?.scene ?? learningWorkbenchFixture);
const { locale } = useLocale();
const isEnglish = computed(() => locale.value === "en-US");
const copy = computed(() => isEnglish.value ? {
  brand: "Back to Structify product home",
  signIn: auth.state.user ? "Account" : "Sign in",
  navLabel: "Course outline",
  courseOutline: "Course outline",
  currentLesson: "Current lesson",
  refresh: "Refresh",
  syncing: "Syncing",
  liveNote: "Course context loaded",
  fixtureA11yNote: "This interactive teaching example is not linked to a personal learning record.",
  loadingContext: "Loading your course context…",
  loadingContextDetail: "Your course outline is loading. The teaching stage will stay local until a renderable lesson is available.",
  retryContext: "Retry context",
  stageTitle: "Insert a new node",
  stageStatusPlaying: "Playing",
  stageStatusPaused: "Paused",
  arrayState: "Current sequential-list state",
  controls: "Algorithm playback controls",
  previous: "Previous step",
  play: "Play trace",
  pause: "Pause trace",
  next: "Next step",
  reset: "Reset demo",
  localTrace: "Teaching trace",
  openStage: "Open full stage",
  goal: "Current goal",
  source: "Sources",
  nextAction: "Next step",
  continueAction: "Continue learning",
  taskFlow: "Your learning flow",
  viewSources: "View course sources",
  enterQAndA: "Open Q&A",
  railLabel: "Learning context",
  stepStatus: "Current step",
  railSummary: "Sources and next",
  tools: "Open learning tools",
  directTools: "Direct learning tools",
  review: "Review",
  reviewMeta: "ACTIVITY LOG",
  search: "Search course materials",
  closeSearch: "Close search",
  submitSearch: "Submit search",
  account: "Open account",
  contextHeading: "Learning context",
  activeGoal: "ACTIVE GOAL",
  stageOverline: "ALGORITHM STAGE",
  insertValue: "INSERT VALUE",
  step: "STEP",
  pointerLabel: "writeIndex",
  trace: "TRACE",
  traceSteps: "STEPS",
  catalogScope: "CATALOG SCOPE",
  chapterCount: "CHAPTERS",
  topicCount: "TOPICS",
  unitCount: "UNITS",
  chapterContext: "CHAPTER CONTEXT",
  noRenderableTitle: "This chapter is ready to explore",
  noRenderableDetail: "This chapter has no built-in renderable steps yet. Request a live interactive stage when you are ready to inspect an operation.",
  launchStage: "Generate interactive stage",
  launchingStage: "Preparing stage",
  retryStage: "Try stage again",
  liveStage: "LIVE STAGE",
  stageValue: "ACTIVE VALUE",
  liveStageDetail: "The current steps were returned by the algorithm service for this chapter.",
  liveStageNextAction: "Explain this step in Q&A",
  liveStageNextDetail: "Use the returned stage as the shared context for your next question.",
  noCurrentChapter: "No active chapter yet",
  noCurrentChapterDetail: "Your course map is ready. Choose a chapter to start; there is no saved learning task to resume.",
  openChapter: "Open chapter",
  openSources: "View sources",
  openClassroom: "Open classroom",
  chapterOverview: "Chapter overview",
  chapterAvailability: "Available now",
  chapterStagePending: "Stage content pending",
  chapterUnits: "Chapter units",
  chapterActions: "Suggested path",
  chapterActionsDetail: "Use the chapter, classroom, Q&A, and source tools in one continuous flow.",
  chapterFallbackUnit: "Continue from this chapter",
  catalogMetadata: "Curriculum map",
  catalogOverview: "Complete course map",
  catalogAvailability: "Choose a topic to open its chapter and continue learning.",
  courseTools: "Course tools",
  workspace: "Data Structures Lab",
  workspaceMeta: "Structify learning workspace",
  workspaceMenu: "Workspace destinations",
  primaryNav: "Primary learning navigation",
  secondaryNav: "Learning modules",
  sidebarToggle: "Toggle course navigation",
  collapseSidebar: "Collapse course navigation",
  expandSidebar: "Expand course navigation",
  catalogLink: "Browse full curriculum",
  accountLink: "Open learner account",
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
  brand: "返回 Structify 产品首页",
  signIn: auth.state.user ? "账户" : "登录",
  navLabel: "课程目录",
  courseOutline: "课程目录",
  currentLesson: "当前单元",
  refresh: "刷新",
  syncing: "同步中",
  liveNote: "已读取真实课程上下文",
  fixtureA11yNote: "当前内容是互动教学示例，不会关联个人学习记录。",
  loadingContext: "正在读取你的课程上下文…",
  loadingContextDetail: "正在读取课程目录；可播放舞台会在获得可渲染课程内容后切换。",
  retryContext: "重试读取",
  stageTitle: "插入一个新节点",
  stageStatusPlaying: "正在播放",
  stageStatusPaused: "已暂停",
  arrayState: "顺序表当前状态",
  controls: "算法播放控制",
  previous: "上一步",
  play: "播放轨迹",
  pause: "暂停轨迹",
  next: "下一步",
  reset: "重置演示",
  localTrace: "教学轨迹",
  openStage: "完整舞台",
  goal: "当前目标",
  source: "资料与来源",
  nextAction: "下一步",
  continueAction: "继续学习",
  taskFlow: "本次学习流程",
  viewSources: "查看课程资料",
  enterQAndA: "进入问答",
  railLabel: "学习上下文",
  stepStatus: "当前步骤",
  railSummary: "资料与下一步",
  tools: "打开学习工具",
  directTools: "直接学习工具",
  review: "学习复盘",
  reviewMeta: "活动记录",
  search: "检索课程资料",
  closeSearch: "关闭检索",
  submitSearch: "提交检索",
  account: "打开账户",
  contextHeading: "学习上下文",
  activeGoal: "当前目标",
  stageOverline: "算法舞台",
  insertValue: "插入值",
  step: "步骤",
  pointerLabel: "写入位置",
  trace: "轨迹",
  traceSteps: "步",
  catalogScope: "课程范围",
  chapterCount: "章",
  topicCount: "主题",
  unitCount: "单元",
  chapterContext: "章节上下文",
  noRenderableTitle: "本章已就绪，等待内容展开",
  noRenderableDetail: "当前章节还没有内置的可渲染步骤；需要查看操作时，可以主动请求一次实时交互演示。",
  launchStage: "生成交互演示",
  launchingStage: "正在准备演示",
  retryStage: "重试生成",
  liveStage: "实时演示",
  stageValue: "当前值",
  liveStageDetail: "当前步骤由本章节的算法服务响应返回。",
  liveStageNextAction: "在问答中解释这一步",
  liveStageNextDetail: "将当前演示作为下一次提问的共同上下文。",
  noCurrentChapter: "还没有当前学习章节",
  noCurrentChapterDetail: "课程地图已准备好，请先选择一个章节开始；当前没有可恢复的学习任务。",
  openChapter: "打开本章",
  openSources: "查看资料",
  openClassroom: "进入课堂",
  chapterOverview: "章节概览",
  chapterAvailability: "当前可用",
  chapterStagePending: "算法舞台待发布",
  chapterUnits: "本章单元",
  chapterActions: "建议路径",
  chapterActionsDetail: "把章节、课堂、问答和资料放在同一条连续学习路径里。",
  chapterFallbackUnit: "从本章继续",
  catalogMetadata: "课程地图",
  catalogOverview: "完整课程图谱",
  catalogAvailability: "选择一个主题，打开对应章节继续学习。",
  courseTools: "课程工具",
  workspace: "数据结构学习台",
  workspaceMeta: "Structify 学习工作区",
  workspaceMenu: "工作区入口",
  primaryNav: "主学习导航",
  secondaryNav: "学习模块",
  sidebarToggle: "切换课程导航",
  collapseSidebar: "收起课程导航",
  expandSidebar: "展开课程导航",
  catalogLink: "浏览完整课程图谱",
  accountLink: "打开学习账户",
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

const localizedScene = computed(() => {
  const current = scene.value;
  if (!isEnglish.value) return current;

  const stepCopy: Record<string, { label: string; detail: string }> = {
    inspect: { label: "Locate empty slot", detail: "23 belongs between 18 and 27." },
    compare: { label: "Compare 27", detail: "27 is greater than 23, so it must yield one slot." },
    shift: { label: "Shift 27 right", detail: "Move from the tail to avoid overwriting unread values." },
    insert: { label: "Write 23", detail: "Give the empty slot to the new node and keep the list ordered." },
  };

  return {
    ...current,
    courseLabel: "Data Structures & Algorithms",
    topic: current.topic === learningWorkbenchFixture.topic ? "Insertion in a sequential list" : current.topic,
    topicSummary: current.topicSummary === learningWorkbenchFixture.topicSummary
      ? "Place a new element in order: make room, then hand the pointer to it."
      : current.topicSummary,
    structureLabel: current.structureLabel === learningWorkbenchFixture.structureLabel ? "ArrayList" : current.structureLabel,
    goal: current.goal === learningWorkbenchFixture.goal ? "Understand the shift boundary" : current.goal,
    goalDetail: current.goalDetail === learningWorkbenchFixture.goalDetail
      ? "Point out the last element that moves right and explain why the scan starts at the tail."
      : current.goalDetail,
    source: current.source === learningWorkbenchFixture.source ? "Linear list · Section 3" : current.source,
    sourceDetail: current.sourceDetail === learningWorkbenchFixture.sourceDetail
      ? "Local teaching source; it is not a published course record."
      : current.sourceDetail,
    nextAction: current.nextAction === learningWorkbenchFixture.nextAction ? "Explain the complexity in one sentence" : current.nextAction,
    nextDetail: current.nextDetail === learningWorkbenchFixture.nextDetail
      ? "After the stage, open Q&A and explain why the operation is O(n)."
      : current.nextDetail,
    steps: current.steps.map((step) => ({ ...step, ...(stepCopy[step.id] ?? {}) })),
    courseOutline: current.courseOutline.map((item) => ({
      ...item,
      label: item.labelEn ?? item.label,
      meta: item.metaEn ?? item.meta,
    })),
    courseGroups: (current.courseGroups ?? []).map((group) => ({
      ...group,
      label: group.labelEn,
      description: group.descriptionEn,
      items: group.items.map((item) => {
        const localizeItem = (entry: WorkbenchCourseItem): WorkbenchCourseItem => ({
          ...entry,
          label: entry.labelEn ?? entry.label,
          meta: entry.metaEn ?? entry.meta,
          summary: entry.summaryEn ?? entry.summary,
          children: entry.children?.map(localizeItem),
        });
        return localizeItem(item);
      }),
    })),
  };
});

const stageSteps = computed(() => liveStage.value?.steps ?? localizedScene.value.steps);
const totalSteps = computed(() => Math.max(stageSteps.value.length, 1));
const activeStep = computed(() => stageSteps.value[Math.min(stepIndex.value, totalSteps.value - 1)] ?? {
  id: "waiting",
  label: isEnglish.value ? "Waiting for a trace" : "等待演示",
  detail: isEnglish.value ? "There are no playable steps in this preview." : "当前没有可播放的步骤。",
});
const stageAnnouncement = computed(() => `${copy.value.step} ${String(stepIndex.value + 1).padStart(2, "0")}: ${activeStep.value.label}. ${activeStep.value.detail}`);
const nodes = computed(() => liveStage.value
  ? nodesAtLiveWorkbenchStep(liveStage.value, stepIndex.value)
  : nodesAtWorkbenchStep(stepIndex.value));
const contextMode = computed(() => snapshot.value?.contextMode ?? snapshot.value?.mode ?? "fixture");
const isLiveContext = computed(() => contextMode.value === "live");
const isAuthenticated = computed(() => Boolean(auth.state.user));
const isGuestFixture = computed(() => !isAuthenticated.value && contextMode.value === "fixture");
// The authenticated API currently supplies course context only. Until it
// returns a renderable goal scene, the central algorithm stage is not mounted.
const stageIsFixture = computed(() => !snapshot.value?.scene || snapshot.value?.mode === "fixture");
const hasRenderableScene = computed(() => {
  if (liveStage.value && isAuthenticated.value) return true;
  if (!snapshot.value) return !auth.state.user;
  // A fixture scene is only a guest teaching preview. Authenticated snapshots
  // without a server-owned renderable scene must stay in the chapter handoff.
  return isAuthenticated.value
    ? snapshot.value.mode === "live" && Boolean(snapshot.value.scene)
    : Boolean(snapshot.value.scene);
});
const goalState = computed(() => liveStage.value ? "active" : (isLiveContext.value && stageIsFixture.value ? "preview" : localizedScene.value.goalStatus));
const goalKicker = computed(() => liveStage.value ? copy.value.liveStage : copy.value.activeGoal);
const contextError = ref<UserErrorPresentation | null>(null);
const stageError = ref<UserErrorPresentation | null>(null);
const stageGoalId = computed(() => liveStage.value ? "server-animation" : localizedScene.value.goalId);
const stageGoal = computed(() => liveStage.value?.title ?? localizedScene.value.goal);
const stageGoalDetail = computed(() => liveStage.value?.description ?? localizedScene.value.goalDetail);
const stageTitle = computed(() => liveStage.value?.title ?? copy.value.stageTitle);
const stageStructureLabel = computed(() => liveStage.value?.structureLabel ?? localizedScene.value.structureLabel);
const stageValueLabel = computed(() => liveStage.value ? copy.value.stageValue : copy.value.insertValue);
const stageValue = computed(() => liveStage.value
  ? liveStageValue(liveStage.value, stepIndex.value)
  : localizedScene.value.insertionValue);
const stagePointerIndex = computed(() => {
  if (liveStage.value) return liveStagePointerIndex(liveStage.value, stepIndex.value, nodes.value.length);
  return stepIndex.value >= totalSteps.value - 1
    ? localizedScene.value.insertionIndex
    : localizedScene.value.insertionIndex + 1;
});
const stageSource = computed(() => liveStage.value ? displayTopic.value : localizedScene.value.source);
const stageSourceDetail = computed(() => liveStage.value ? copy.value.liveStageDetail : localizedScene.value.sourceDetail);
const stageNextAction = computed(() => liveStage.value ? copy.value.liveStageNextAction : localizedScene.value.nextAction);
const stageNextDetail = computed(() => liveStage.value ? copy.value.liveStageNextDetail : localizedScene.value.nextDetail);
const canLaunchLiveStage = computed(() => (
  !hasRenderableScene.value
  && isAuthenticated.value
  && snapshot.value?.mode === "live"
  && Boolean(snapshot.value.currentChapter?.id.trim())
));
const currentChapterCatalogItem = computed(() => {
  const chapterId = snapshot.value?.currentChapter?.id;
  if (!chapterId) return null;
  return flattenCourseGroups(localizedScene.value.courseGroups ?? []).find((item) => (
    item.id === chapterId || item.routeId === chapterId || item.chapterId === chapterId
  )) ?? null;
});
const displayTopic = computed(() => currentChapterCatalogItem.value?.label
  ?? snapshot.value?.currentChapter?.title
  ?? (isAuthenticated.value ? copy.value.noCurrentChapter : localizedScene.value.topic));
const displayTopicSummary = computed(() => currentChapterCatalogItem.value?.summary
  ?? (snapshot.value?.currentChapter
    ? snapshot.value.currentChapter.summary || copy.value.liveNote
    : (isAuthenticated.value ? copy.value.noCurrentChapterDetail : localizedScene.value.topicSummary)));
const contextSource = computed(() => snapshot.value?.currentChapter
  ? displayTopic.value
  : (isAuthenticated.value ? copy.value.catalogMetadata : localizedScene.value.source));
const contextSourceDetail = computed(() => snapshot.value?.currentChapter
  ? copy.value.liveNote
  : (isAuthenticated.value ? copy.value.noCurrentChapterDetail : localizedScene.value.sourceDetail));
const isLoadingAuthenticated = computed(() => Boolean(auth.state.user) && synchronizing.value && !snapshot.value);
const accountLabel = computed(() => auth.state.user?.username || auth.state.user?.email || copy.value.signIn);
function localizeContextError(current: UserErrorPresentation): UserErrorPresentation {
  const messages = current.kind === "permission" && current.title === "当前账号没有权限"
    ? copy.value.accessDenied
    : copy.value.errors[current.kind];
  return { ...current, ...messages };
}
const displayedContextError = computed<UserErrorPresentation | null>(() => contextError.value ? localizeContextError(contextError.value) : null);
const displayedStageError = computed<UserErrorPresentation | null>(() => stageError.value ? localizeContextError(stageError.value) : null);
const fixtureContextDetail = computed(() => copy.value.fixtureA11yNote);
const activeChapterId = computed(() => {
  if (snapshot.value?.currentChapter?.id) return snapshot.value.currentChapter.id;
  if (isAuthenticated.value) return "";
  const activeItem = flattenCourseGroups(localizedScene.value.courseGroups ?? []).find((item) => item.current)
    ?? localizedScene.value.courseOutline.find((item) => item.current);
  return activeItem ? courseItemRouteId(activeItem) : "sequential-list";
});
const activeCourseItem = computed<WorkbenchCourseItem | null>(() => {
  const id = activeChapterId.value.trim();
  if (!id) return null;
  const items = flattenCourseGroups(localizedScene.value.courseGroups ?? []);
  return items.find((item) => item.id === id)
    ?? items.find((item) => item.kind === "chapter" && (item.routeId === id || item.chapterId === id))
    ?? null;
});
const activeLessonId = computed(() => activeCourseItem.value?.kind === "lesson" ? activeCourseItem.value.id : "");
const activeContextChapterId = computed(() => {
  const item = activeCourseItem.value;
  if (item?.kind === "lesson") return item.parentId ?? item.chapterId ?? activeChapterId.value;
  return item?.chapterId ?? activeChapterId.value;
});
const activeChapterCatalogItem = computed<WorkbenchCourseItem | null>(() => {
  const contextId = activeContextChapterId.value.trim();
  if (!contextId) return null;
  const items = flattenCourseGroups(courseGroups.value);
  return items.find((item) => (
    item.kind === "chapter" && (
      item.id === contextId
      || item.routeId === contextId
      || item.chapterId === contextId
    )
  )) ?? null;
});
function groupHasItem(items: readonly WorkbenchCourseItem[], targetId: string): boolean {
  return items.some((item) => (
    item.id === targetId
    || item.routeId === targetId
    || item.chapterId === targetId
    || Boolean(item.children?.length && groupHasItem(item.children, targetId))
  ));
}
const activeCourseGroup = computed<WorkbenchCourseGroup | null>(() => {
  const chapterId = activeChapterCatalogItem.value?.id;
  if (!chapterId) return null;
  return courseGroups.value.find((group) => groupHasItem(group.items, chapterId)) ?? null;
});
const chapterDeskLessons = computed(() => {
  const chapterItem = activeChapterCatalogItem.value;
  if (chapterItem?.children?.length) return chapterItem.children;
  if (activeCourseItem.value?.kind === "lesson" && chapterItem?.children?.length) return chapterItem.children;
  if (activeCourseItem.value) return [activeCourseItem.value];
  return [] as WorkbenchCourseItem[];
});
const activeChapter = computed(() => {
  const id = activeChapterId.value;
  const contextChapterId = activeContextChapterId.value;
  return snapshot.value?.chapters.find((chapter) => chapter.id === id)
    ?? snapshot.value?.chapters.find((chapter) => chapter.id === contextChapterId)
    ?? coursePreviewFixture.chapters.find((chapter) => chapter.id === id)
    ?? (id === "sequential-list" ? { id, chapterNumber: 2, title: "顺序表的插入", summary: "" } : null);
});
const chapterQuery = computed(() => (activeContextChapterId.value || activeChapterId.value)
  ? ({
      chapterId: activeContextChapterId.value || activeChapterId.value,
      ...(activeLessonId.value ? { lessonId: activeLessonId.value } : {}),
      from: "workbench",
    })
  : ({ from: "workbench" }));
const stageTarget = computed(() => ({ path: "/user/animation", query: chapterQuery.value }));
const chapterTarget = computed(() => (activeContextChapterId.value || activeChapterId.value)
  ? ({ path: "/user/chapters", query: chapterQuery.value })
  : ({ path: "/user/chapters", query: { from: "workbench" } }));
const classroomTarget = computed(() => ({ path: "/user/classroom", query: chapterQuery.value }));
const codeTarget = computed(() => ({ path: "/user/code", query: chapterQuery.value }));
const coachTarget = computed(() => ({ path: "/user/coach", query: chapterQuery.value }));
const knowledgeTarget = computed(() => ({ path: "/user/knowledge", query: chapterQuery.value }));
const reviewTarget = computed(() => ({ path: "/user/progress", query: chapterQuery.value }));
function countItemsByKind(items: readonly WorkbenchCourseItem[], kind: "chapter" | "lesson"): number {
  return items.reduce((total, item) => total + (item.kind === kind ? 1 : 0) + countItemsByKind(item.children ?? [], kind), 0);
}

const courseGroups = computed(() => {
  const currentChapterId = snapshot.value?.currentChapter?.id ?? "";
  // The curriculum map is content metadata, so it stays complete even when
  // the authenticated API only returns the current chapter slice. Only the
  // active pip is overlaid from the server response.
  return (localizedScene.value.courseGroups ?? []).map((group) => ({
    ...group,
    open: Boolean(group.open),
    items: group.items.map((item) => {
      const markChildren = (entry: WorkbenchCourseItem): WorkbenchCourseItem => {
        const children = entry.children?.map(markChildren);
        const childIsCurrent = Boolean(children?.some((child) => child.current || child.children?.some(itemHasCurrent)));
        // A chapter-level API id selects the chapter row. A lesson-level id
        // selects only that lesson, leaving its parent as context rather than
        // rendering two competing active pips.
        const directCurrent = currentChapterId
          ? entry.id === currentChapterId
            || (entry.kind === "chapter" && (entry.routeId === currentChapterId || entry.chapterId === currentChapterId))
          : isGuestFixture.value && Boolean(entry.current);
        const isPublishedByApi = isLiveContext.value && Boolean(snapshot.value?.chapters.some((chapter) => (
          chapter.id === entry.id
          || chapter.id === entry.routeId
          || chapter.id === entry.chapterId
        )));
        return {
          ...entry,
          capability: isPublishedByApi ? "api-published" as WorkbenchCourseCapability : entry.capability,
          current: directCurrent && !childIsCurrent,
          children,
        };
      };
      return markChildren(item);
    }),
  }));
});
const courseOutline = computed(() => courseGroups.value.flatMap((group) => group.items));
const courseCatalogStats = computed(() => ({
  chapters: courseGroups.value.reduce((total, group) => total + countItemsByKind(group.items, "chapter"), 0),
  topics: courseGroups.value.length,
  units: courseGroups.value.reduce((total, group) => total + countItemsByKind(group.items, "lesson"), 0),
}));

function isCourseGroupOpen(group: WorkbenchCourseGroup): boolean {
  return openCourseGroupIds.value.has(group.id);
}

function updateCourseGroupOpen(groupId: string, event: Event) {
  const target = event.currentTarget as HTMLDetailsElement;
  const next = new Set(openCourseGroupIds.value);
  if (target.open) next.add(groupId);
  else next.delete(groupId);
  openCourseGroupIds.value = next;
}

function groupUnitCount(group: WorkbenchCourseGroup): number {
  return countItemsByKind(group.items, "lesson");
}

function courseCapability(item: Pick<WorkbenchCourseItem, "capability">) {
  return localizeCourseCapability(item.capability, locale.value);
}

function itemHasCurrent(item: WorkbenchCourseItem): boolean {
  return Boolean(item.current || item.children?.some(itemHasCurrent));
}

function itemTarget(item: WorkbenchCourseItem) {
  const context = courseItemNavigationContext(item);
  return {
    path: "/user/chapters",
    query: {
      chapterId: context.chapterId,
      ...(context.lessonId ? { lessonId: context.lessonId } : {}),
      from: "workbench",
    },
  };
}
const directTools = computed(() => {
  const chapter = activeChapter.value;
  return localizedLearningTools(locale.value, {
    chapterId: activeContextChapterId.value || activeChapterId.value || undefined,
    lessonId: activeLessonId.value || undefined,
    coursewareLessonId: chapter && hasPreviewPresentation(chapter.id) ? previewPresentationLessonId(chapter) : undefined,
    from: "workbench",
  });
});
const navigationContext = computed(() => ({
  chapterId: activeContextChapterId.value || activeChapterId.value || undefined,
  lessonId: activeLessonId.value || undefined,
  coursewareLessonId: activeChapter.value && hasPreviewPresentation(activeChapter.value.id)
    ? previewPresentationLessonId(activeChapter.value)
    : undefined,
  from: "workbench",
}));
const primaryNavigation = computed(() => localizedLearningGlobalNavigation(locale.value, navigationContext.value));
const moduleNavigation = computed(() => localizedLearningNavigation(locale.value, navigationContext.value));
const chapterActionCards = computed(() => {
  const classroom = moduleNavigation.value.find((item) => item.id === "classroom");
  const coach = moduleNavigation.value.find((item) => item.id === "coach");
  const library = moduleNavigation.value.find((item) => item.id === "library");
  return [
    {
      id: "chapter",
      kicker: copy.value.currentLesson,
      label: copy.value.openChapter,
      detail: displayTopicSummary.value,
      to: chapterTarget.value,
    },
    {
      id: "classroom",
      kicker: classroom?.label ?? copy.value.openClassroom,
      label: copy.value.openClassroom,
      detail: classroom?.caption ?? copy.value.chapterActionsDetail,
      to: classroomTarget.value,
    },
    {
      id: "coach",
      kicker: coach?.label ?? copy.value.enterQAndA,
      label: copy.value.enterQAndA,
      detail: coach?.caption ?? copy.value.chapterActionsDetail,
      to: coachTarget.value,
    },
    {
      id: "library",
      kicker: library?.label ?? copy.value.openSources,
      label: copy.value.openSources,
      detail: contextSourceDetail.value,
      to: knowledgeTarget.value,
    },
  ];
});
const chapterDeskSummaryCards = computed(() => {
  const lessonCount = chapterDeskLessons.value.length || 1;
  const toolLabels = [
    ...directTools.value.map((tool) => tool.label),
    copy.value.openClassroom,
    copy.value.enterQAndA,
    copy.value.openSources,
  ].slice(0, 4);

  return [
    {
      id: "overview",
      kicker: copy.value.chapterOverview,
      title: displayTopic.value,
      detail: displayTopicSummary.value,
      points: [
        activeCourseGroup.value?.label ?? copy.value.courseOutline,
        `${lessonCount} ${copy.value.unitCount}`,
      ],
    },
    {
      id: "availability",
      kicker: copy.value.chapterAvailability,
      title: copy.value.directTools,
      detail: copy.value.chapterStagePending,
      points: toolLabels,
    },
  ];
});

function isPrimaryNavigationActive(id: (typeof primaryNavigation.value)[number]["id"]): boolean {
  return isLearningGlobalNavigationActive(id, route.path);
}

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value;
}

function stopPlayback() {
  isPlaying.value = false;
  if (playTimer !== undefined) {
    window.clearInterval(playTimer);
    playTimer = undefined;
  }
}

function togglePlayback() {
  if (isPlaying.value) {
    stopPlayback();
    return;
  }

  if (stepIndex.value >= totalSteps.value - 1) stepIndex.value = 0;
  isPlaying.value = true;
  playTimer = window.setInterval(() => {
    if (stepIndex.value >= totalSteps.value - 1) {
      stopPlayback();
      return;
    }
    stepIndex.value += 1;
  }, 1120);
}

function previousStep() {
  stopPlayback();
  stepIndex.value = Math.max(0, stepIndex.value - 1);
}

function nextStep() {
  stopPlayback();
  stepIndex.value = Math.min(totalSteps.value - 1, stepIndex.value + 1);
}

function resetStage() {
  stopPlayback();
  stepIndex.value = 0;
}

async function openSearch() {
  searchOpen.value = true;
  await nextTick();
  searchInput.value?.focus();
}

async function closeSearch() {
  if (!searchOpen.value) return;
  searchOpen.value = false;
  searchQuery.value = "";
  await nextTick();
  searchTrigger.value?.focus();
}

async function submitSearch() {
  const query = searchQuery.value.trim();
  if (!query) return;
  await router.push({ path: "/user/knowledge", query: { q: query, chapterId: activeChapterId.value, from: "workbench" } });
}

async function launchLiveStage() {
  const chapter = snapshot.value?.currentChapter;
  const userId = auth.state.user?.id;
  if (launchingLiveStage.value || !chapter?.id.trim() || !userId || snapshot.value?.mode !== "live") return;

  launchingLiveStage.value = true;
  stageError.value = null;
  try {
    const next = await launchPublishedChapterStage(userApi, chapter);
    // The explicit response only belongs in the currently authenticated,
    // still-selected server chapter. Do not leak it across a context change.
    if (auth.state.user?.id !== userId || snapshot.value?.currentChapter?.id !== chapter.id) return;
    stopPlayback();
    stepIndex.value = 0;
    liveStage.value = next;
    await nextTick();
    stageHeading.value?.focus();
  } catch (cause) {
    if (auth.state.user?.id === userId && snapshot.value?.currentChapter?.id === chapter.id) {
      stageError.value = presentUserError(cause);
    }
  } finally {
    launchingLiveStage.value = false;
  }
}

function handleEscape(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    void openSearch();
    return;
  }

  if (event.key !== "Escape") return;

  if (searchOpen.value) {
    event.preventDefault();
    void closeSearch();
    return;
  }

}

async function loadContext() {
  synchronizing.value = true;
  contextError.value = null;
  stageError.value = null;
  if (!auth.state.user) {
    stopPlayback();
    liveStage.value = null;
    stageError.value = null;
  }
  try {
    // The adapter keeps the guest teaching fixture separate from authenticated
    // course context and can restore an already persisted stage without a new
    // simulation request.
    // Anonymous entry is intentionally side-effect free: readiness, progress
    // and chapter endpoints can require a session and should not create noisy
    // failed requests before a learner explicitly signs in.
    const next = auth.state.user
      ? await loadLearningWorkbench(userApi)
      : createLearningWorkbenchPreview();
    snapshot.value = next;
    if (next.persistedStage) {
      if (liveStage.value?.animationRecordId !== next.persistedStage.animationRecordId) {
        stopPlayback();
        stepIndex.value = 0;
      }
      // Resume quietly on initial load. Focus handoff remains reserved for the
      // learner's explicit “generate interactive stage” action.
      liveStage.value = next.persistedStage;
    } else if (isAuthenticated.value && liveStage.value) {
      stopPlayback();
      liveStage.value = null;
    }
  } catch (cause) {
    contextError.value = presentUserError(cause);
    snapshot.value = createLearningWorkbenchPreview({ reason: "api-unavailable" });
    if (liveStage.value) {
      stopPlayback();
      liveStage.value = null;
    }
  } finally {
    synchronizing.value = false;
  }
}

watch(() => auth.state.user?.id, (nextUserId, previousUserId) => {
  if (nextUserId !== previousUserId) {
    stopPlayback();
    liveStage.value = null;
    stageError.value = null;
  }
  void loadContext();
});
onMounted(() => {
  window.addEventListener("keydown", handleEscape);
  void loadContext();
});
onBeforeUnmount(() => {
  stopPlayback();
  window.removeEventListener("keydown", handleEscape);
});
</script>

<template>
  <section class="learning-workbench-v2" data-visual-contract="dashboard-sidebar" :data-locale="locale" :data-context-mode="contextMode" aria-labelledby="workbench-title">
    <svg class="workbench-sprite" aria-hidden="true" focusable="false">
      <symbol id="wb-route" viewBox="0 0 24 24"><path d="M5 18.5V8.8a3.8 3.8 0 0 1 3.8-3.8h9.7" /><path d="m15 2.5 3.5 2.5-3.5 2.5" /><circle cx="5" cy="18.5" r="2" /></symbol>
      <symbol id="wb-chat" viewBox="0 0 24 24"><path d="M5 5.5h14v10H9l-4 3v-13Z" /><path d="M8 9.5h8M8 12.5h5" /></symbol>
      <symbol id="wb-classroom" viewBox="0 0 24 24"><path d="M4 5.5h16v10H4z" /><path d="M8 19h8M12 15.5V19" /><path d="m8 9 2.5 2L16 8" /></symbol>
      <symbol id="wb-stage" viewBox="0 0 24 24"><path d="m4 17 5-5 3 3 7-8" /><path d="M16 7h3v3" /><path d="M4 20h16" /></symbol>
      <symbol id="wb-library" viewBox="0 0 24 24"><path d="M5 4.5h11a2 2 0 0 1 2 2V19H7a2 2 0 0 1-2-2V4.5Z" /><path d="M8 8h7M8 11h7M8 14h4" /><path d="M18 19h1a1 1 0 0 0 1-1V7" /></symbol>
      <symbol id="wb-code" viewBox="0 0 24 24"><path d="m8.5 7-4 5 4 5M15.5 7l4 5-4 5M13.5 4l-3 16" /></symbol>
      <symbol id="wb-search" viewBox="0 0 24 24"><circle cx="10.8" cy="10.8" r="5.8" /><path d="m15.3 15.3 4.2 4.2" /></symbol>
      <symbol id="wb-grid" viewBox="0 0 24 24"><path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z" /></symbol>
      <symbol id="wb-panel-close" viewBox="0 0 24 24"><rect x="4" y="5" width="16" height="14" rx="1" /><path d="M9 5v14M14 9l-2 3 2 3" /></symbol>
      <symbol id="wb-panel-open" viewBox="0 0 24 24"><rect x="4" y="5" width="16" height="14" rx="1" /><path d="M9 5v14M12 9l2 3-2 3" /></symbol>
      <symbol id="wb-arrow" viewBox="0 0 24 24"><path d="M5 12h13M13 6l6 6-6 6" /></symbol>
      <symbol id="wb-play" viewBox="0 0 24 24"><path d="m8 5 11 7-11 7Z" fill="currentColor" stroke="none" /></symbol>
      <symbol id="wb-check" viewBox="0 0 24 24"><path d="m5 12.5 4.2 4.2L19 7" /></symbol>
      <symbol id="wb-reset" viewBox="0 0 24 24"><path d="M19 8V4m0 0h-4m4 0-3 3.2A8 8 0 1 0 20 13" /></symbol>
    </svg>

    <a class="workbench-skip-link" href="#workbench-content">{{ isEnglish ? "Skip to learning content" : "跳到学习内容" }}</a>

    <div class="workbench-layout" :class="{ 'is-sidebar-collapsed': !sidebarOpen }" data-shell="dashboard-sidebar" data-region="workbench">
      <aside class="workbench-sidebar" :class="{ 'is-collapsed': !sidebarOpen }" :aria-label="copy.navLabel">
        <div class="workbench-sidebar__top">
          <details class="workbench-workspace-switcher">
            <summary>
              <span class="workbench-workspace-mark" aria-hidden="true">DS</span>
              <span class="workbench-workspace-copy"><strong>{{ copy.workspace }}</strong><small>{{ copy.workspaceMeta }}</small></span>
              <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
            </summary>
            <div class="workbench-workspace-switcher__menu" :aria-label="copy.workspaceMenu">
              <RouterLink to="/user/home"><span>{{ copy.courseOutline }}</span><small>{{ copy.catalogOverview }}</small></RouterLink>
              <RouterLink to="/user/chapters"><span>{{ copy.catalogLink }}</span><small>{{ courseCatalogStats.topics }} {{ copy.topicCount }} · {{ courseCatalogStats.chapters }} {{ copy.chapterCount }}</small></RouterLink>
              <RouterLink :to="auth.state.user ? '/user/profile' : loginTarget"><span>{{ copy.accountLink }}</span><small>{{ accountLabel }}</small></RouterLink>
            </div>
          </details>

          <nav class="workbench-primary-nav" :aria-label="copy.primaryNav">
            <button class="workbench-nav-row" type="button" @click="openSearch">
              <span class="workbench-nav-row__lead"><svg aria-hidden="true"><use href="#wb-search" /></svg><span>{{ copy.search }}</span></span>
              <kbd>⌘K</kbd>
            </button>
            <RouterLink v-for="item in primaryNavigation" :key="`primary-${item.id}`" class="workbench-nav-row" :class="{ 'is-active': isPrimaryNavigationActive(item.id) }" :to="item.to" :aria-current="isPrimaryNavigationActive(item.id) ? 'page' : undefined">
              <span class="workbench-nav-row__lead"><svg aria-hidden="true"><use :href="item.id === 'overview' ? '#wb-grid' : item.id === 'course' ? '#wb-route' : item.id === 'lab' ? '#wb-stage' : '#wb-library'" /></svg><span>{{ item.label }}</span></span>
              <span v-if="isPrimaryNavigationActive(item.id)" class="workbench-nav-row__pip" aria-hidden="true"></span>
            </RouterLink>
          </nav>
        </div>

        <div class="workbench-sidebar__divider" aria-hidden="true"></div>

        <nav class="workbench-course-outline" :aria-label="copy.courseOutline">
          <div class="workbench-course-outline__identity">
            <div class="workbench-course-outline__identity-head">
              <span class="workbench-section-label">{{ copy.courseOutline }}</span>
              <button class="workbench-refresh" type="button" :disabled="synchronizing" :aria-label="synchronizing ? copy.syncing : copy.refresh" :title="synchronizing ? copy.syncing : copy.refresh" @click="loadContext">
                <svg aria-hidden="true"><use href="#wb-reset" /></svg>
              </button>
            </div>
            <p class="workbench-course-outline__title">{{ localizedScene.courseLabel }}</p>
            <div class="workbench-course-outline__scope" :aria-label="copy.catalogScope">
              <span><strong>{{ courseCatalogStats.topics }}</strong> {{ copy.topicCount }}</span>
              <span><strong>{{ courseCatalogStats.chapters }}</strong> {{ copy.chapterCount }}</span>
              <span><strong>{{ courseCatalogStats.units }}</strong> {{ copy.unitCount }}</span>
            </div>
          </div>
          <div class="workbench-course-groups">
             <details
               v-for="group in courseGroups"
               :key="group.id"
               class="workbench-course-group"
               :data-group-id="group.id"
               :open="isCourseGroupOpen(group)"
               @toggle="updateCourseGroupOpen(group.id, $event)"
            >
              <summary class="workbench-course-group__summary">
                <span class="workbench-course-group__number">{{ String(courseGroups.indexOf(group) + 1).padStart(2, '0') }}</span>
                <span class="workbench-course-group__copy"><strong>{{ group.label }}</strong><small>{{ group.description }}</small></span>
                <span class="workbench-course-group__count">{{ countItemsByKind(group.items, 'chapter') }} {{ copy.chapterCount }} · {{ groupUnitCount(group) }} {{ copy.unitCount }}</span>
                <span v-if="group.items.some(itemHasCurrent)" class="workbench-course-group__pip" :aria-label="copy.currentLesson"></span>
                <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
              </summary>
              <div class="workbench-course-group__items">
                <template v-for="item in group.items" :key="item.id">
                  <RouterLink
                    class="workbench-course-item"
                    :class="{ 'is-active': item.current }"
                    :to="item.current ? chapterTarget : itemTarget(item)"
                    :aria-current="item.current ? 'page' : undefined"
                    :data-course-id="item.id"
                  >
                    <span class="workbench-course-item__index">{{ String(group.items.indexOf(item) + 1).padStart(2, '0') }}</span>
                    <span class="workbench-course-item__copy"><strong>{{ item.label }}</strong><small>{{ item.summary }}</small></span>
                    <span class="workbench-course-item__status" :data-capability="item.capability" :title="courseCapability(item).detail">{{ courseCapability(item).label }}</span>
                    <span v-if="item.current" class="workbench-course-item__pip" :aria-label="copy.currentLesson"></span>
                  </RouterLink>
                  <RouterLink
                    v-for="(lesson, lessonIndex) in (item.children ?? [])"
                    :key="lesson.id"
                    class="workbench-course-item workbench-course-item--lesson"
                    :class="{ 'is-active': lesson.current }"
                    :to="lesson.current ? chapterTarget : itemTarget(lesson)"
                    :aria-current="lesson.current ? 'page' : undefined"
                    :data-course-id="lesson.id"
                  >
                    <span class="workbench-course-item__index">{{ String(lessonIndex + 1).padStart(2, '0') }}</span>
                    <span class="workbench-course-item__copy"><strong>{{ lesson.label }}</strong><small>{{ lesson.summary }}</small></span>
                    <span class="workbench-course-item__status" :data-capability="lesson.capability" :title="courseCapability(lesson).detail">{{ courseCapability(lesson).label }}</span>
                    <span v-if="lesson.current" class="workbench-course-item__pip" :aria-label="copy.currentLesson"></span>
                  </RouterLink>
                </template>
              </div>
            </details>
          </div>
        </nav>

        <details class="workbench-sidebar__modules">
          <summary>
            <span class="workbench-section-label">{{ copy.secondaryNav }}</span>
            <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
          </summary>
          <div class="workbench-sidebar__modules-content">
            <RouterLink v-for="item in moduleNavigation" :key="`module-${item.id}`" class="workbench-nav-row workbench-nav-row--module" :class="{ 'is-active': isLearningGlobalNavigationActive(item.id === 'path' ? 'course' : item.id === 'practice' ? 'lab' : item.id === 'library' ? 'library' : 'overview', route.path) }" :to="item.to">
              <span class="workbench-nav-row__lead"><svg aria-hidden="true"><use :href="`#wb-${item.icon}`" /></svg><span>{{ item.label }}</span></span>
              <small>{{ item.caption }}</small>
            </RouterLink>
          </div>
        </details>

        <footer class="workbench-sidebar__footer">
          <RouterLink :to="chapterTarget"><svg aria-hidden="true"><use href="#wb-arrow" /></svg><span>{{ copy.catalogLink }}</span></RouterLink>
          <RouterLink :to="auth.state.user ? '/user/profile' : loginTarget"><svg aria-hidden="true"><use href="#wb-arrow" /></svg><span>{{ copy.accountLink }}</span></RouterLink>
        </footer>
      </aside>

      <main id="workbench-content" class="workbench-main">
        <div class="workbench-context-bar">
          <div class="workbench-context-bar__leading">
            <button class="workbench-sidebar-toggle" type="button" :aria-label="sidebarOpen ? copy.collapseSidebar : copy.expandSidebar" :title="sidebarOpen ? copy.collapseSidebar : copy.expandSidebar" @click="toggleSidebar">
              <svg aria-hidden="true"><use :href="sidebarOpen ? '#wb-panel-close' : '#wb-panel-open'" /></svg>
            </button>
            <span class="workbench-context-bar__crumb">{{ copy.workspace }}</span>
            <span class="workbench-context-bar__separator" aria-hidden="true">/</span>
            <strong>{{ displayTopic }}</strong>
          </div>
          <div class="workbench-context-bar__meta">
            <span class="workbench-context-bar__step">{{ String(stepIndex + 1).padStart(2, '0') }} / {{ String(totalSteps).padStart(2, '0') }}</span>
          </div>
          <div class="workbench-context-bar__utility">
            <ThemeToggle />
            <button ref="searchTrigger" class="workbench-tool-button workbench-command-trigger workbench-search-pill" type="button" :aria-label="copy.search" :title="copy.search" @click="openSearch">
              <svg aria-hidden="true"><use href="#wb-search" /></svg>
              <span>{{ copy.search }}</span>
              <kbd>⌘K</kbd>
            </button>
            <RouterLink v-if="auth.state.user" class="workbench-account" to="/user/profile" :aria-label="copy.account"><span class="workbench-account__avatar">{{ accountLabel.slice(0, 1).toUpperCase() }}</span><span class="workbench-account__name">{{ accountLabel }}</span></RouterLink>
            <RouterLink v-else class="workbench-sign-in" :to="loginTarget" :aria-label="copy.signIn">{{ copy.signIn }}</RouterLink>
          </div>
        </div>
        <div class="workbench-learning-content">
        <details class="workbench-mobile-outline">
          <summary>
            <span>{{ copy.courseOutline }}</span>
            <span class="workbench-mobile-outline__meta">{{ courseCatalogStats.topics }} {{ copy.topicCount }} · {{ courseCatalogStats.chapters }} {{ copy.chapterCount }} · {{ courseCatalogStats.units }} {{ copy.unitCount }}</span>
            <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
          </summary>
           <div class="workbench-mobile-outline__groups">
            <details
              v-for="group in courseGroups"
               :key="`mobile-${group.id}`"
               class="workbench-mobile-outline__group"
               :open="isCourseGroupOpen(group)"
               @toggle="updateCourseGroupOpen(group.id, $event)"
            >
              <summary>
                <span>{{ group.label }}</span>
                <small>{{ countItemsByKind(group.items, 'chapter') }} {{ copy.chapterCount }} · {{ groupUnitCount(group) }} {{ copy.unitCount }}</small>
                <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
              </summary>
              <nav :aria-label="group.label">
                <template v-for="item in group.items" :key="`mobile-${group.id}-${item.id}`">
                  <RouterLink
                    :to="item.current ? chapterTarget : itemTarget(item)"
                    :class="{ 'is-active': item.current }"
                    :aria-current="item.current ? 'page' : undefined"
                    :data-mobile-course-id="item.id"
                  >
                    <span>{{ item.label }}</span><small>{{ courseCapability(item).label }}</small>
                  </RouterLink>
                  <RouterLink
                    v-for="lesson in (item.children ?? [])"
                    :key="`mobile-${group.id}-${lesson.id}`"
                    :to="lesson.current ? chapterTarget : itemTarget(lesson)"
                    class="workbench-mobile-outline__lesson"
                    :class="{ 'is-active': lesson.current }"
                    :aria-current="lesson.current ? 'page' : undefined"
                    :data-mobile-course-id="lesson.id"
                  ><span>{{ lesson.label }}</span><small>{{ courseCapability(lesson).label }}</small></RouterLink>
                </template>
              </nav>
             </details>
           </div>
         </details>
        <header class="workbench-main__heading">
          <div>
            <h1 id="workbench-title">{{ isLoadingAuthenticated ? copy.loadingContext : displayTopic }}</h1>
            <p>{{ isLoadingAuthenticated ? copy.loadingContextDetail : displayTopicSummary }}</p>
          </div>
        </header>

        <div class="workbench-overview-grid">
          <section v-if="hasRenderableScene" class="workbench-current-task" :data-goal-state="goalState" :data-goal-id="stageGoalId" :data-context-mode="contextMode" aria-labelledby="current-task-title">
            <div class="workbench-current-task__copy">
              <p class="workbench-kicker">{{ goalKicker }}</p>
              <h2 id="current-task-title">{{ stageGoal }}</h2>
              <p>{{ stageGoalDetail }}</p>
            </div>
            <div class="workbench-current-task__actions">
              <RouterLink class="workbench-primary-action" :to="chapterTarget">{{ copy.continueAction }}<svg aria-hidden="true"><use href="#wb-arrow" /></svg></RouterLink>
            </div>
            <p class="sr-only" :data-context-mode="contextMode">{{ isLiveContext ? copy.liveNote : fixtureContextDetail }}</p>
          </section>

          <nav class="workbench-main-tools workbench-course-tools" :aria-label="copy.courseTools">
            <RouterLink v-for="tool in directTools" :key="`main-tool-${tool.id}`" :to="tool.to">
              <span class="workbench-course-tools__icon"><svg aria-hidden="true"><use :href="`#wb-${tool.icon}`" /></svg></span>
              <span class="workbench-main-tools__copy"><strong>{{ tool.label }}</strong><small>{{ tool.meta }}</small></span>
              <svg aria-hidden="true"><use href="#wb-arrow" /></svg>
            </RouterLink>
          </nav>
        </div>

        <section v-if="!hasRenderableScene" class="workbench-context-handoff" aria-labelledby="chapter-context-title">
          <div class="workbench-context-handoff__copy">
            <p class="workbench-kicker">{{ copy.chapterContext }}</p>
            <h2 id="chapter-context-title">{{ copy.noRenderableTitle }}</h2>
            <p>{{ copy.noRenderableDetail }}</p>
            <p class="workbench-context-handoff__chapter"><strong>{{ displayTopic }}</strong><span>{{ displayTopicSummary }}</span></p>
          </div>
          <div class="workbench-context-handoff__actions">
            <RouterLink class="workbench-primary-action" :to="chapterTarget">{{ copy.openChapter }}<svg aria-hidden="true"><use href="#wb-arrow" /></svg></RouterLink>
            <button
              v-if="canLaunchLiveStage"
              data-testid="workbench-launch-stage"
              class="workbench-primary-action"
              type="button"
              :disabled="launchingLiveStage"
              @click="launchLiveStage"
            >
              {{ launchingLiveStage ? copy.launchingStage : copy.launchStage }}<svg aria-hidden="true"><use href="#wb-play" /></svg>
            </button>
            <RouterLink class="workbench-secondary-action" :to="knowledgeTarget">{{ copy.openSources }}<svg aria-hidden="true"><use href="#wb-arrow" /></svg></RouterLink>
          </div>
          <div class="workbench-context-handoff__desk">
            <div class="workbench-context-handoff__overview">
              <article
                v-for="card in chapterDeskSummaryCards"
                :key="card.id"
                class="workbench-context-handoff__summary-card"
              >
                <p class="workbench-kicker">{{ card.kicker }}</p>
                <strong>{{ card.title }}</strong>
                <p>{{ card.detail }}</p>
                <ul class="workbench-context-handoff__summary-points">
                  <li v-for="point in card.points" :key="`${card.id}-${point}`">{{ point }}</li>
                </ul>
              </article>
            </div>

            <div class="workbench-context-handoff__workspace">
              <section class="workbench-context-handoff__panel" :aria-label="copy.chapterUnits">
                <header class="workbench-context-handoff__panel-head">
                  <div>
                    <p class="workbench-kicker">{{ copy.chapterUnits }}</p>
                    <h3>{{ activeCourseGroup?.label ?? displayTopic }}</h3>
                  </div>
                  <span>{{ chapterDeskLessons.length || 1 }} {{ copy.unitCount }}</span>
                </header>
                <nav class="workbench-context-handoff__lesson-list" :aria-label="copy.chapterUnits">
                  <RouterLink
                    v-for="(lesson, index) in chapterDeskLessons"
                    :key="`handoff-lesson-${lesson.id}`"
                    :to="itemTarget(lesson)"
                    class="workbench-context-handoff__lesson"
                    :class="{ 'is-active': lesson.current }"
                    :aria-current="lesson.current ? 'page' : undefined"
                  >
                    <span class="workbench-context-handoff__lesson-index">{{ String(index + 1).padStart(2, '0') }}</span>
                    <span class="workbench-context-handoff__lesson-copy">
                      <strong>{{ lesson.label }}</strong>
                      <small>{{ lesson.summary || lesson.meta || copy.chapterFallbackUnit }}</small>
                    </span>
                    <svg aria-hidden="true"><use href="#wb-arrow" /></svg>
                  </RouterLink>
                  <RouterLink
                    v-if="chapterDeskLessons.length === 0"
                    :to="chapterTarget"
                    class="workbench-context-handoff__lesson is-active"
                  >
                    <span class="workbench-context-handoff__lesson-index">01</span>
                    <span class="workbench-context-handoff__lesson-copy">
                      <strong>{{ displayTopic }}</strong>
                      <small>{{ copy.chapterFallbackUnit }}</small>
                    </span>
                    <svg aria-hidden="true"><use href="#wb-arrow" /></svg>
                  </RouterLink>
                </nav>
              </section>

              <section class="workbench-context-handoff__panel" :aria-label="copy.chapterActions">
                <header class="workbench-context-handoff__panel-head">
                  <div>
                    <p class="workbench-kicker">{{ copy.chapterActions }}</p>
                    <h3>{{ copy.chapterActionsDetail }}</h3>
                  </div>
                </header>
                <div class="workbench-context-handoff__action-list">
                  <RouterLink
                    v-for="action in chapterActionCards"
                    :key="action.id"
                    :to="action.to"
                    class="workbench-context-handoff__action"
                  >
                    <span class="workbench-context-handoff__action-kicker">{{ action.kicker }}</span>
                    <strong>{{ action.label }}</strong>
                    <small>{{ action.detail }}</small>
                    <svg aria-hidden="true"><use href="#wb-arrow" /></svg>
                  </RouterLink>
                </div>
              </section>
            </div>
          </div>
        </section>

        <p v-if="displayedContextError" class="workbench-context-error" data-context-status="error" role="alert">
          <strong>{{ displayedContextError.title }}</strong>
          <span>{{ displayedContextError.message }}</span>
          <button type="button" @click="loadContext">{{ copy.retryContext }}</button>
        </p>
        <p v-if="displayedStageError" class="workbench-context-error" data-context-status="stage-error" role="alert">
          <strong>{{ displayedStageError.title }}</strong>
          <span>{{ displayedStageError.message }}</span>
          <button type="button" :disabled="launchingLiveStage" @click="launchLiveStage">{{ copy.retryStage }}</button>
        </p>
        <section v-if="hasRenderableScene" class="algorithm-stage" :data-stage-mode="liveStage?.mode ?? 'fixture'" aria-labelledby="stage-title">
          <div class="algorithm-stage__media" aria-hidden="true">
            <span class="algorithm-stage__grid"></span>
            <svg class="algorithm-stage__trace" viewBox="0 0 720 200" preserveAspectRatio="none"><path pathLength="1" d="M24 156C121 154 146 89 222 96s104 72 181 31c61-33 104-94 293-78" /><path class="algorithm-stage__trace-fill" pathLength="1" d="M24 156C121 154 146 89 222 96s104 72 181 31c61-33 104-94 293-78L696 190H24Z" /></svg>
          </div>

          <header class="algorithm-stage__header">
            <div>
            <p class="algorithm-stage__overline">{{ copy.stageOverline }} / {{ stageStructureLabel }}</p>
              <h2 id="stage-title" ref="stageHeading" tabindex="-1">{{ stageTitle }}</h2>
            </div>
            <div class="algorithm-stage__status">
              <span><i aria-hidden="true"></i>{{ isPlaying ? copy.stageStatusPlaying : copy.stageStatusPaused }}</span>
              <strong>{{ String(stepIndex + 1).padStart(2, '0') }} / {{ String(totalSteps).padStart(2, '0') }}</strong>
            </div>
          </header>
          <p class="sr-only algorithm-stage__announcement" data-testid="workbench-stage-announcement" role="status" aria-live="polite" aria-atomic="true">{{ stageAnnouncement }}</p>

          <div class="algorithm-stage__canvas">
            <div class="algorithm-stage__annotation algorithm-stage__annotation--top">
              <span>{{ stageValueLabel }}</span>
              <strong>{{ stageValue }}</strong>
            </div>
            <div class="algorithm-array" :aria-label="copy.arrayState">
              <span v-for="(node, index) in nodes" :key="`${node.value}-${index}`" class="algorithm-node" :data-state="node.state">
                <small>{{ String(index).padStart(2, '0') }}</small>
                <strong>{{ node.value }}</strong>
                <i v-if="node.state === 'inserted'" class="algorithm-node__spark" aria-hidden="true"></i>
              </span>
            </div>
            <div class="algorithm-pointer" :class="{ 'is-final': stepIndex >= totalSteps - 1 }" :style="{ '--pointer-index': String(stagePointerIndex) }">
              <span class="algorithm-pointer__stem"></span><span class="algorithm-pointer__label">{{ copy.pointerLabel }}</span>
            </div>
            <div class="algorithm-stage__annotation algorithm-stage__annotation--bottom">
              <span>{{ copy.step }} {{ String(stepIndex + 1).padStart(2, '0') }}</span>
              <strong>{{ activeStep.label }}</strong>
              <p>{{ activeStep.detail }}</p>
            </div>
          </div>

          <footer class="algorithm-stage__toolbar">
              <div class="algorithm-stage__controls" role="group" :aria-label="copy.controls">
              <button type="button" :aria-label="copy.previous" :title="copy.previous" :disabled="stepIndex === 0" @click="previousStep"><svg class="is-flip" aria-hidden="true"><use href="#wb-arrow" /></svg></button>
              <button data-testid="workbench-play" class="algorithm-stage__play" type="button" :aria-label="isPlaying ? copy.pause : copy.play" :title="isPlaying ? copy.pause : copy.play" @click="togglePlayback"><span v-if="isPlaying" class="pause-glyph" aria-hidden="true"></span><svg v-else aria-hidden="true"><use href="#wb-play" /></svg></button>
              <button type="button" :aria-label="copy.next" :title="copy.next" :disabled="stepIndex >= totalSteps - 1" @click="nextStep"><svg aria-hidden="true"><use href="#wb-arrow" /></svg></button>
              <button type="button" :aria-label="copy.reset" :title="copy.reset" @click="resetStage"><svg aria-hidden="true"><use href="#wb-reset" /></svg></button>
            </div>
            <RouterLink class="algorithm-stage__open" :to="stageTarget">{{ copy.openStage }}<svg aria-hidden="true"><use href="#wb-arrow" /></svg></RouterLink>
          </footer>
        </section>

        <details v-if="hasRenderableScene" class="workbench-timeline" aria-labelledby="timeline-title">
          <summary class="workbench-timeline__heading">
            <span class="workbench-kicker">{{ copy.trace }} / {{ String(totalSteps).padStart(2, '0') }} {{ copy.traceSteps }}</span>
            <span class="workbench-timeline__title"><span id="timeline-title" class="workbench-timeline__title-text">{{ copy.taskFlow }}</span><i class="workbench-disclosure__chevron" aria-hidden="true"></i></span>
          </summary>
          <ol class="workbench-timeline__items">
            <li v-for="(step, index) in stageSteps" :key="step.id" class="workbench-timeline__listitem">
              <button class="workbench-timeline__item" :class="{ 'is-active': index === stepIndex, 'is-complete': index < stepIndex }" type="button" @click="stopPlayback(); stepIndex = index">
                <span class="workbench-timeline__index">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="workbench-timeline__line" aria-hidden="true"></span>
                <span class="workbench-timeline__copy"><strong>{{ step.label }}</strong><small>{{ step.detail }}</small></span>
                <svg v-if="index < stepIndex" aria-hidden="true"><use href="#wb-check" /></svg>
              </button>
            </li>
          </ol>
        </details>
        <details class="workbench-context-drawer" data-testid="workbench-context-drawer" :open="!hasRenderableScene">
          <summary>
            <span>
              <span class="workbench-kicker">{{ copy.contextHeading }}</span>
              <strong>{{ copy.railSummary }}</strong>
            </span>
            <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
          </summary>
          <aside class="workbench-rail" :aria-label="copy.railLabel">
        <header class="workbench-rail__head">
            <p class="workbench-kicker">{{ copy.contextHeading }}</p>
            <strong>{{ copy.railSummary }}</strong>
        </header>
            <details v-if="hasRenderableScene" class="workbench-rail__section">
              <summary>
                <span class="workbench-rail__summary"><span>01</span> {{ copy.source }}</span>
                <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
              </summary>
              <div class="workbench-rail__body">
                <h3>{{ stageSource }}</h3>
                <p>{{ stageSourceDetail }}</p>
                <RouterLink class="workbench-rail__link" :to="knowledgeTarget">{{ copy.viewSources }}<svg aria-hidden="true"><use href="#wb-arrow" /></svg></RouterLink>
              </div>
            </details>

            <details v-if="hasRenderableScene" class="workbench-rail__section workbench-rail__section--next" open>
              <summary>
                <span class="workbench-rail__summary"><span>02</span> {{ copy.nextAction }}</span>
                <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
              </summary>
              <div class="workbench-rail__body">
                <h3>{{ stageNextAction }}</h3>
                <p>{{ stageNextDetail }}</p>
                <RouterLink class="workbench-rail__action" :to="coachTarget">{{ copy.enterQAndA }}<svg aria-hidden="true"><use href="#wb-arrow" /></svg></RouterLink>
                <RouterLink class="workbench-rail__review" :to="reviewTarget">
                  <span>{{ copy.review }}</span>
                  <small>{{ copy.reviewMeta }}</small>
                  <svg aria-hidden="true"><use href="#wb-arrow" /></svg>
                </RouterLink>
              </div>
            </details>

            <template v-if="!hasRenderableScene">
              <details class="workbench-rail__section" open>
                <summary>
                  <span class="workbench-rail__summary"><span>01</span> {{ copy.chapterContext }}</span>
                  <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
                </summary>
                <div class="workbench-rail__body">
                  <h2>{{ displayTopic }}</h2>
                  <p>{{ displayTopicSummary }}</p>
                </div>
              </details>
              <details class="workbench-rail__section" open>
                <summary>
                  <span class="workbench-rail__summary"><span>02</span> {{ copy.directTools }}</span>
                  <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
                </summary>
                <div class="workbench-rail__body">
                  <h3>{{ copy.catalogMetadata }}</h3>
                  <p>{{ courseCatalogStats.topics }} {{ copy.topicCount }} · {{ courseCatalogStats.chapters }} {{ copy.chapterCount }} · {{ courseCatalogStats.units }} {{ copy.unitCount }}</p>
                  <RouterLink class="workbench-rail__link" :to="chapterTarget">{{ copy.openChapter }}<svg aria-hidden="true"><use href="#wb-arrow" /></svg></RouterLink>
                  <RouterLink class="workbench-rail__review" :to="reviewTarget">
                    <span>{{ copy.review }}</span>
                    <small>{{ copy.reviewMeta }}</small>
                    <svg aria-hidden="true"><use href="#wb-arrow" /></svg>
                  </RouterLink>
                </div>
              </details>
              <details class="workbench-rail__section workbench-rail__section--next" open>
                <summary>
                  <span class="workbench-rail__summary"><span>03</span> {{ copy.source }}</span>
                  <i class="workbench-disclosure__chevron" aria-hidden="true"></i>
                </summary>
                <div class="workbench-rail__body">
                  <h3>{{ contextSource }}</h3>
                  <p>{{ contextSourceDetail }}</p>
                  <RouterLink class="workbench-rail__action" :to="knowledgeTarget">{{ copy.openSources }}<svg aria-hidden="true"><use href="#wb-arrow" /></svg></RouterLink>
                </div>
              </details>
            </template>
          </aside>
        </details>
        </div>
      </main>
    </div>

    <div v-if="searchOpen" class="workbench-command-layer" @mousedown.self="closeSearch">
      <section class="workbench-command-palette" role="dialog" aria-modal="true" :aria-label="copy.search">
        <form class="workbench-command-search" role="search" @submit.prevent="submitSearch">
          <svg aria-hidden="true"><use href="#wb-search" /></svg>
          <input ref="searchInput" v-model="searchQuery" :aria-label="copy.search" :placeholder="copy.search" />
          <kbd>ESC</kbd>
          <button class="workbench-command-search__submit" type="submit" :aria-label="copy.submitSearch" :title="copy.submitSearch"><svg aria-hidden="true"><use href="#wb-arrow" /></svg></button>
          <button type="button" :aria-label="copy.closeSearch" @click="closeSearch">×</button>
        </form>
        <div class="workbench-command-empty" aria-live="polite">
          <svg aria-hidden="true"><use href="#wb-grid" /></svg>
          <p>{{ searchQuery.trim() ? (isEnglish ? "Press Enter to search course materials" : "按 Enter 检索课程资料") : (isEnglish ? "Type a topic, algorithm, or action" : "输入知识点、算法或操作") }}</p>
        </div>
      </section>
    </div>

  </section>
</template>


<style scoped src="./LearningWorkbenchStrict.css"></style>
