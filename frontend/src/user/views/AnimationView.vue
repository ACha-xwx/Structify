<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import type { AnimationDefinition, AnimationStep, DsvpEvidenceContext, DsvpRequest, DsvpStructure } from "../../shared/types/contracts";
import { auth } from "../../app/providers/runtime";
import { createAnimationPlayback, type AnimationPlayback } from "../animation-playback";
import { createAlgorithmStagePreview, loadAlgorithmStage, type AlgorithmStageSnapshot } from "../adapters/algorithm-stage";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import { userApi } from "../runtime";
import { useLocale } from "../../shared/i18n/locale";
import RuntimeSelect, { type RuntimeSelectOption } from "../../shared/components/RuntimeSelect.vue";

const route = useRoute();
const { locale } = useLocale();
const isEnglish = computed(() => locale.value === "en-US");
const animationSources = ["chapter", "coach", "classroom", "home", "workbench"] as const;
type AnimationSource = typeof animationSources[number];
const apiSourceRef = "api/v1/animations/simulate";
function readQueryValue(value: unknown): string {
  const candidates = Array.isArray(value) ? value : [value];
  return candidates.find((candidate): candidate is string => (
    typeof candidate === "string" && Boolean(candidate.trim())
  ))?.trim() ?? "";
}
const chapterId = computed(() => readQueryValue(route.query.chapterId));
const lessonId = computed(() => readQueryValue(route.query.lessonId));
// A lesson may be nested under a canonical chapter. Prefer it for local
// teaching scenes while retaining the chapter id for the server contract.
const sceneContextId = computed(() => lessonId.value || chapterId.value);
const sessionId = computed(() => readQueryValue(route.query.sessionId));
const source = computed<AnimationSource | null>(() => { const value = readQueryValue(route.query.from); return animationSources.includes(value as AnimationSource) ? value as AnimationSource : null; });
const hasValidChapterId = computed(() => chapterId.value.length > 0 && chapterId.value.length <= 64);
const hasValidClassroomSessionId = computed(() => sessionId.value.length > 0 && sessionId.value.length <= 160);
const hasLearningContext = computed(() => hasValidChapterId.value && source.value !== null && (source.value !== "classroom" || hasValidClassroomSessionId.value));
const returnTarget = computed(() => {
  if (source.value === "classroom") return { path: "/user/classroom", query: { ...(hasValidChapterId.value ? { chapterId: chapterId.value } : {}), ...(hasValidClassroomSessionId.value ? { sessionId: sessionId.value } : {}) } };
  if (!hasLearningContext.value || !source.value) return hasValidChapterId.value ? `/user/chapters/${encodeURIComponent(chapterId.value)}` : "/user/chapters";
  if (source.value === "home") return "/";
  if (source.value === "workbench") return "/user/home";
  if (source.value === "coach") return { path: "/user/coach", query: { chapterId: chapterId.value } };
  return `/user/chapters/${encodeURIComponent(chapterId.value)}`;
});
const returnLabel = computed(() => {
  if (isEnglish.value) return source.value === "classroom" ? "Back to classroom" : source.value === "workbench" ? "Back to workbench" : source.value === "home" ? "Back to product home" : source.value === "coach" ? "Back to Q&A" : "Back to chapter";
  return source.value === "classroom" ? "返回课堂" : source.value === "workbench" ? "返回学习台" : source.value === "home" ? "返回产品首页" : source.value === "coach" ? "返回问答" : "返回本章";
});
const learningContextError = computed(() => {
  if (source.value === "classroom" && !hasValidClassroomSessionId.value) {
    return isEnglish.value
      ? { title: "Classroom session unavailable", message: "Return to the classroom and reopen the algorithm stage from the current classroom session." }
      : { title: "课堂来源缺少有效会话", message: "请返回课堂并从当前课堂会话重新打开算法舞台。" };
  }
  return isEnglish.value
    ? { title: "Learning context unavailable", message: "The algorithm stage cannot be opened on its own. Enter from the current chapter, Q&A, classroom, or workbench context." }
    : { title: "学习上下文不可用", message: "算法舞台不能单独打开。请从章节、课程问答、课堂或学习台的当前学习位置进入。" };
});

const structure = ref<DsvpStructure>("sequential_list");
const operation = ref("insert");
const initial = ref("12, 18, 27, 31, 44");
const value = ref("23");
const stage = ref<AlgorithmStageSnapshot>(createAlgorithmStagePreview(sceneContextId.value));
const player = ref<AnimationPlayback | null>(null);
const loading = ref(false);
const error = ref<UserErrorPresentation | null>(null);
const observed = ref("");
const observationStatus = ref("");
const savingObservation = ref(false);
const reducedMotion = ref(false);
let motionQuery: MediaQueryList | null = null;

function closeCustomOperation(event: KeyboardEvent): void {
  if (event.key !== "Escape") return;
  const details = document.querySelector<HTMLDetailsElement>(".algorithm-custom-operation[open]");
  if (!details) return;
  event.preventDefault();
  details.open = false;
  details.querySelector<HTMLElement>("summary")?.focus();
}

const operations: Record<DsvpStructure, string[]> = { stack: ["push", "pop", "peek"], queue: ["enqueue", "dequeue", "peek"], sequential_list: ["insert", "delete", "merge"], linked_list: ["append", "insert", "delete", "find"], tree: ["traverse", "highlight"], graph: ["bfs", "dfs", "dijkstra", "highlight"], heap: ["insert", "extract", "peek"], hash: ["put", "get", "delete"], array: ["set", "insert", "delete", "swap", "get"] };
const speedOptions: RuntimeSelectOption[] = [
  { value: 0.5, label: "0.5×" },
  { value: 1, label: "1×" },
  { value: 1.5, label: "1.5×" },
  { value: 2, label: "2×" },
];
const structureLabels: Record<DsvpStructure, string> = { stack: "栈", queue: "队列", sequential_list: "顺序表", linked_list: "链表", tree: "树", graph: "图", heap: "堆", hash: "哈希表", array: "数组" };
const operationLabels: Record<string, string> = { push: "入栈", pop: "出栈", peek: "查看顶部", enqueue: "入队", dequeue: "出队", insert: "插入", delete: "删除", merge: "合并", append: "追加", find: "查找", traverse: "遍历", highlight: "定位", bfs: "广度优先搜索", dfs: "深度优先搜索", dijkstra: "最短路径", extract: "取出", put: "写入", get: "读取", set: "写入", swap: "交换" };
const englishStructureLabels: Record<DsvpStructure, string> = { stack: "Stack", queue: "Queue", sequential_list: "Sequential list", linked_list: "Linked list", tree: "Tree", graph: "Graph", heap: "Heap", hash: "Hash table", array: "Array" };
const englishOperationLabels: Record<string, string> = { push: "Push", pop: "Pop", peek: "Peek", enqueue: "Enqueue", dequeue: "Dequeue", insert: "Insert", delete: "Delete", merge: "Merge", append: "Append", find: "Find", traverse: "Traverse", highlight: "Highlight", bfs: "Breadth-first search", dfs: "Depth-first search", dijkstra: "Shortest path", extract: "Extract", put: "Write", get: "Read", set: "Write", swap: "Swap" };
const definition = computed(() => stage.value.definition);
const sceneVisualization = computed(() => {
  if (stage.value.scene?.visualization) return stage.value.scene.visualization;
  return ["tree", "heap"].includes(definition.value.type) ? "tree" : "linear";
});
const currentStep = computed<AnimationStep | null>(() => player.value?.state.currentStep || null);
const stageIndex = computed(() => player.value?.state.index ?? 0);
const isSequentialListFixture = computed(() => stage.value.fixture?.chapterId === "sequential-list");
const fixtureStepCopy: Record<string, Pick<AnimationStep, "label" | "note">> = {
  "定位插入位置": { label: "Locate the insertion point", note: "23 belongs between 18 and 27, so index 2 is the target empty slot." },
  "为表尾腾出一格": { label: "Make room at the tail", note: "Extend the logical length first and copy the last value, 44, into the new tail slot." },
  "右移 31": { label: "Shift 31 right", note: "Copy from the tail toward the front. 31 moves to index 4 and leaves a slot to process." },
  "右移 27": { label: "Shift 27 right", note: "27 moves to index 3, so index 2 can now be written safely." },
  "写入 23": { label: "Write 23", note: "Write the new value into the empty slot. The sequential list stays ordered with O(n) movement cost." },
};
function localizeStep(step: AnimationStep): AnimationStep {
  if (!isEnglish.value || !isSequentialListFixture.value) return step;
  return { ...step, ...(fixtureStepCopy[step.label] ?? {}) };
}
const displayDefinition = computed<AnimationDefinition>(() => {
  if (!isEnglish.value || !isSequentialListFixture.value) return definition.value;
  return {
    ...definition.value,
    title: "Insertion in a sequential list",
    description: "Write 23 at index 2. Shift one cell at a time from the tail, then hand the empty slot to the new value.",
    steps: definition.value.steps.map(localizeStep),
  };
});
const stageSteps = computed(() => displayDefinition.value.steps);
const renderedValues = computed(() => replayValues(definition.value.initial, definition.value.steps, stageIndex.value));
const activeStep = computed(() => currentStep.value ? localizeStep(currentStep.value) : stageSteps.value[0] || null);
const sceneStructure = computed<DsvpStructure>(() => stage.value.scene?.requestTemplate.structure ?? structure.value);
const currentStructureLabel = computed(() => {
  const label = displayStructureLabel(sceneStructure.value);
  return isEnglish.value ? `${label} state` : `${label}当前状态`;
});
const stageTypeLabel = computed(() => {
  if (sceneVisualization.value === "graph") return "GRAPH";
  if (sceneVisualization.value === "hash") return "HASH";
  return displayDefinition.value.type.toUpperCase();
});
const stageModeLabel = computed(() => stage.value.mode === "live" ? (isEnglish.value ? "LIVE DEMO" : "实时演示") : (isEnglish.value ? "PREVIEW" : "示例预览"));
const stageModeDetail = computed(() => {
  if (stage.value.fixtureReason === "api-unavailable") return isEnglish.value ? "offline preview" : "离线预览";
  if (stage.value.mode === "live") return isEnglish.value ? "live demo" : "实时演示";
  return isEnglish.value ? "teaching preview" : "教学预览";
});
const stageContextLabel = computed(() => stage.value.mode === "live" ? (isEnglish.value ? "CURRENT LESSON" : "当前课程") : (isEnglish.value ? "TEACHING PREVIEW" : "教学预览"));
const stageContextDetail = computed(() => {
  if (stage.value.mode === "live") {
    return stage.value.evidencePersisted
      ? (isEnglish.value ? "This demonstration can be saved to your learning record." : "这次演示可以保存到你的学习记录。")
      : (isEnglish.value ? "This demonstration is available for the current lesson." : "这次演示用于当前课程学习。");
  }
  return stage.value.fixtureReason === "api-unavailable"
    ? (isEnglish.value ? "The live service is unavailable; this teaching preview remains interactive." : "实时演示暂时不可用，先使用可操作的教学示例。")
    : (isEnglish.value ? "A fixed teaching preview for exploring the algorithm." : "固定的教学示例，用来观察算法变化。");
});
const stageSourceLabel = computed(() => stage.value.mode === "live" ? (isEnglish.value ? "Current lesson" : "当前课程内容") : (isEnglish.value ? "Example lesson" : "示例课程内容"));
const stageSourceDetail = computed(() => stage.value.mode === "live"
  ? (isEnglish.value ? "The steps and structure below reflect this demonstration." : "下面的步骤和结构反映本次演示。")
  : (isEnglish.value ? "A self-contained example; it is not a personal learning record." : "独立的教学示例，不代表个人学习记录。"));
const observationBoundaryCopy = computed(() => {
  if (stage.value.mode === "live" && stage.value.animationRecordId) {
    return isEnglish.value
      ? "This demonstration can be saved to your personal learning record."
      : "这次演示可以保存到你的学习记录。";
  }
  if (stage.value.mode === "live") {
    return isEnglish.value
      ? "This demonstration is available now, but there is no record to save yet."
      : "这次演示可以查看，但暂时没有可保存的学习记录。";
  }
  return isEnglish.value
    ? "The teaching preview does not write to personal learning records."
    : "教学示例不会写入个人学习记录。";
});
const stageCopy = computed(() => isEnglish.value ? {
  eyebrow: "ALGORITHM STAGE",
  title: "Algorithm Stage",
  playing: "Playing",
  manual: "Manual step",
  pausable: "Pausable demo",
  state: "STATE",
  step: "STEP",
  structureState: "Structure state",
  currentStructure: "Current sequence state",
  writeIndex: "writeIndex",
  empty: "The structure is empty",
  snippet: "Current algorithm snippet",
  steps: "Algorithm steps",
  controls: "Playback controls",
  speed: "Speed",
  customize: "Custom operation",
  customizeHint: "Adjust the demonstration",
  run: "Run demonstration",
  running: "Preparing demonstration",
  observation: "Observation",
  observationHint: "For example: why must shifting start at the tail?",
  save: "Save observation",
  saving: "Saving",
  context: "LEARNING POSITION",
  source: "SOURCE",
  mode: "MODE",
  previous: "Previous step",
  play: "Play demo",
  pause: "Pause demo",
  next: "Next step",
  reset: "Reset demo",
  structure: "Structure",
  operation: "Operation",
  initialData: "Initial data, comma-separated",
  operationValue: "Operation value",
  learningNote: "LEARNING NOTE",
  loadingTitle: "Preparing algorithm demonstration",
  loadingDetail: "Building the steps from the selected structure and operation.",
  rerun: "Run again",
} : {
  eyebrow: "算法舞台",
  title: "算法舞台",
  playing: "正在播放",
  manual: "手动步进",
  pausable: "可暂停演示",
  state: "状态",
  step: "步骤",
  structureState: "结构状态",
  currentStructure: "顺序表当前状态",
  writeIndex: "写入位置",
  empty: "当前结构为空",
  snippet: "当前算法片段",
  steps: "算法步骤",
  controls: "算法播放控制",
  speed: "速度",
  customize: "自定义操作",
  customizeHint: "调整演示参数",
  run: "运行演示",
  running: "正在准备演示",
  observation: "本次观察",
  observationHint: "例如：为什么移动必须从尾部开始？",
  save: "保存观察",
  saving: "正在保存",
  context: "学习位置",
  source: "来源",
  mode: "模式",
  previous: "上一步",
  play: "播放演示",
  pause: "暂停演示",
  next: "下一步",
  reset: "重置演示",
  structure: "数据结构",
  operation: "操作",
  initialData: "初始数据，逗号分隔",
  operationValue: "操作值",
  learningNote: "学习记录",
  loadingTitle: "正在生成算法演示",
  loadingDetail: "正在根据你选择的结构和操作准备步骤。",
  rerun: "重新运行",
});
const englishApiErrors: Record<UserErrorPresentation["kind"], Pick<UserErrorPresentation, "title" | "message">> = {
  permission: { title: "Sign in to unlock this feature", message: "Guests can browse public learning content. Sign in here to use model services or save personal learning records." },
  "not-found": { title: "Resource unavailable", message: "This resource may not be published, may have been removed, or may be outside the current account's access scope." },
  conflict: { title: "The current state has changed", message: "Refresh before continuing so the latest learning state is not overwritten." },
  limited: { title: "Too many requests", message: "The service is temporarily limiting requests. Try again shortly." },
  timeout: { title: "Request timed out", message: "An upstream service did not respond in time." },
  service: { title: "Learning service unavailable", message: "This page position has been kept. Try again when the service recovers." },
  network: { title: "Network connection unavailable", message: "Check the connection and try again. Your current learning position will not be lost." },
  validation: { title: "Unable to process this request", message: "Review the submitted information and try again." },
  unknown: { title: "Action not completed", message: "The service returned an unexpected result." },
};
function localizeUserError(current: UserErrorPresentation): UserErrorPresentation {
  if (!isEnglish.value) return current;
  if (current.kind === "permission" && current.title === "当前账号没有权限") {
    return { ...current, title: "Account access denied", message: "The service denied access to this learning resource or action." };
  }
  return { ...current, ...englishApiErrors[current.kind] };
}
const displayedError = computed<UserErrorPresentation | null>(() => error.value ? localizeUserError(error.value) : null);
const pointerIndex = computed(() => Math.max(0, Math.min(activeStep.value?.index ?? 2, Math.max(renderedValues.value.length - 1, 0))));
const codeLines = computed(() => stage.value.scene?.codeLines ?? (isEnglish.value ? ["// Update the structure one step at a time", "render(steps);"] : ["// 逐步更新结构状态", "render(steps);"]));
const graphCoordinates = [[50, 18], [22, 54], [78, 54], [50, 84]] as const;
const treeColumns = [4, 2, 6, 1, 3, 5, 7] as const;
function treeNodeStyle(index: number): Record<string, string> {
  return { gridColumn: String(treeColumns[index] ?? ((index % 7) + 1)), gridRow: String(Math.floor(Math.log2(index + 1)) + 1) };
}
function graphNodeStyle(index: number): Record<string, string> {
  const [x, y] = graphCoordinates[index] ?? [50, 50];
  return { "--graph-x": `${x}%`, "--graph-y": `${y}%` };
}
function displayStructureLabel(name: DsvpStructure): string {
  return isEnglish.value ? englishStructureLabels[name] : structureLabels[name];
}
function displayOperationLabel(name: string): string {
  return isEnglish.value ? (englishOperationLabels[name] ?? name) : (operationLabels[name] ?? name);
}

const structureOptions = computed<RuntimeSelectOption[]>(() => Object.keys(operations).map((name) => ({
  value: name,
  label: displayStructureLabel(name as DsvpStructure),
})));
const operationOptions = computed<RuntimeSelectOption[]>(() => operations[structure.value].map((name) => ({
  value: name,
  label: displayOperationLabel(name),
})));

function normalizeOperation() {
  if (!operations[structure.value].includes(operation.value)) {
    operation.value = operations[structure.value][0];
  }
}
function parseInitial(): unknown[] { return initial.value.split(",").map((item) => item.trim()).filter(Boolean).map((item) => /^-?\d+(\.\d+)?$/.test(item) ? Number(item) : item); }
function jsonValue(text: string): unknown { const normalized = text.trim(); if (!normalized) return ""; return /^-?\d+(\.\d+)?$/.test(normalized) ? Number(normalized) : normalized; }
function replayValues(base: unknown[], steps: AnimationStep[], count: number): unknown[] {
  const values = [...base];
  for (const step of steps.slice(0, count)) {
    const position = step.index == null ? values.length : Math.max(0, Math.min(step.index, values.length));
    if (["push", "enqueue", "append", "insert"].includes(step.op) && step.value !== undefined) values.splice(position, 0, step.value);
    else if (["pop", "dequeue", "delete", "extract"].includes(step.op)) values.splice(step.index ?? (step.op === "pop" ? values.length - 1 : 0), 1);
    else if (step.op === "set" && step.index != null) values[step.index] = step.value;
    else if (step.op === "swap" && step.i != null && step.j != null) [values[step.i], values[step.j]] = [values[step.j], values[step.i]];
  }
  return values;
}
function resetCustomOperation(next: AlgorithmStageSnapshot): void {
  const request = next.fixture?.requestTemplate;
  if (!request) return;
  structure.value = request.structure;
  operation.value = request.operation;
  initial.value = request.initial_state.data.map((item) => typeof item === "object" ? JSON.stringify(item) : String(item)).join(", ");
  value.value = request.params.value === undefined || request.params.value === null ? "" : String(request.params.value);
}
function setStage(next: AlgorithmStageSnapshot, resetOperation = false) {
  player.value?.dispose();
  stage.value = next;
  if (resetOperation) resetCustomOperation(next);
  player.value = createAnimationPlayback(next.definition.steps, 920, reducedMotion.value);
  observed.value = "";
  observationStatus.value = "";
}
function requestContext(): DsvpEvidenceContext { if (source.value === "classroom" && hasValidClassroomSessionId.value) return { chapter_id: chapterId.value, classroom_session_id: sessionId.value, source_type: "CLASSROOM", source_ref: sessionId.value }; return { chapter_id: chapterId.value, source_type: "API", source_ref: apiSourceRef }; }
function requestForCustomOperation(): DsvpRequest {
  const params: Record<string, unknown> = { capacity: 12 };
  if (["push", "enqueue", "insert", "append", "set"].includes(operation.value)) params.value = jsonValue(value.value);
  const context = requestContext();
  return { version: "1.0", structure: structure.value, operation: operation.value, params, initial_state: { data: parseInitial(), metadata: { capacity: 12 } }, context, chapterId: chapterId.value, ...(lessonId.value ? { lessonId: lessonId.value } : {}), source_ref: context.source_ref, ...(source.value === "classroom" && hasValidClassroomSessionId.value ? { classroomSessionId: sessionId.value } : {}) };
}
async function simulate() {
  if (!hasLearningContext.value || !source.value) return;
  if (!auth.state.user) {
    error.value = presentUserError(Object.assign(new Error("login required"), { status: 401 }));
    return;
  }
  loading.value = true;
  error.value = null;
  try { setStage(await loadAlgorithmStage(userApi, requestForCustomOperation(), { intent: "simulate" })); } catch (cause) { error.value = presentUserError(cause); } finally { loading.value = false; }
}
async function saveObservation() {
  if (!observed.value.trim()) return;
  if (!stage.value.animationRecordId) {
    observationStatus.value = stage.value.mode === "live"
      ? (isEnglish.value ? "This demonstration is available, but there is no learning record to save yet." : "这次演示可以查看，但暂时没有可保存的学习记录。")
      : (isEnglish.value ? "The teaching preview does not save personal observations." : "教学示例不会保存个人观察。");
    return;
  }
  savingObservation.value = true;
  try { await userApi.saveObservation(stage.value.animationRecordId, { observation: observed.value.trim() }); observationStatus.value = isEnglish.value ? "Observation saved" : "观察已保存"; } catch (cause) { observationStatus.value = localizeUserError(presentUserError(cause)).message; } finally { savingObservation.value = false; }
}
function updateMotionPreference() { const next = motionQuery?.matches === true; if (next === reducedMotion.value) return; const stateIndex = player.value?.state.index ?? 0; player.value?.dispose(); reducedMotion.value = next; player.value = createAnimationPlayback(definition.value.steps, 920, next); for (let index = 0; index < stateIndex; index += 1) player.value.next(); }
let previewLoadVersion = 0;
async function loadContextualPreview(): Promise<void> {
  const version = ++previewLoadVersion;
  const next = await loadAlgorithmStage(userApi, undefined, { chapterId: chapterId.value, lessonId: lessonId.value });
  if (version === previewLoadVersion) setStage(next, true);
}
watch(structure, normalizeOperation);
watch([chapterId, lessonId], ([nextChapterId, nextLessonId], previous) => {
  if (nextChapterId === previous?.[0] && nextLessonId === previous?.[1]) return;
  void loadContextualPreview();
});
onMounted(() => { motionQuery = window.matchMedia?.("(prefers-reduced-motion: reduce)") ?? null; reducedMotion.value = motionQuery?.matches === true; motionQuery?.addEventListener?.("change", updateMotionPreference); window.addEventListener("keydown", closeCustomOperation); void loadContextualPreview(); });
onBeforeUnmount(() => { player.value?.dispose(); motionQuery?.removeEventListener?.("change", updateMotionPreference); window.removeEventListener("keydown", closeCustomOperation); });
</script>

<template>
  <UserFrame shell="course">
    <section class="algorithm-stage-page" aria-labelledby="animation-title">
      <header class="algorithm-stage-page__heading">
        <div>
          <p class="algorithm-stage-page__eyebrow">{{ displayDefinition.title }}</p>
          <h1 id="animation-title">{{ stageCopy.title }}</h1>
        </div>
        <div class="algorithm-stage-page__actions">
          <span class="algorithm-stage-page__chip" :data-mode="stage.mode" data-testid="animation-fixture-badge"><i></i>{{ stageModeLabel }}</span>
          <RouterLink class="algorithm-stage-page__return" :data-testid="hasLearningContext ? 'animation-return' : 'animation-context-return'" :to="returnTarget">{{ returnLabel }}</RouterLink>
        </div>
      </header>
      <UserState v-if="!hasLearningContext" mode="error" :title="learningContextError.title" :message="learningContextError.message" />
      <template v-else>
        <section v-if="player" class="signal-stage" :data-mode="stage.mode" :aria-label="stageCopy.title">
          <div class="signal-stage__background" aria-hidden="true"><span class="signal-stage__grid"></span><span class="signal-stage__halo"></span><svg viewBox="0 0 900 280" preserveAspectRatio="none"><path pathLength="1" d="M0 242C118 233 130 104 245 128s151 118 249 48C602 110 683 29 900 65" /><path class="signal-stage__fill" d="M0 242C118 233 130 104 245 128s151 118 249 48C602 110 683 29 900 65V280H0Z" /></svg></div>
          <header class="signal-stage__topline"><div><span>{{ stageTypeLabel }}</span><strong>{{ displayDefinition.title }}</strong></div><span class="signal-stage__topline-status"><i></i>{{ player.state.playing ? stageCopy.playing : reducedMotion ? stageCopy.manual : stageCopy.pausable }}</span></header>
          <div class="signal-stage__layout">
            <div class="signal-stage__scene" :class="{ 'signal-stage__scene--complex': sceneVisualization !== 'linear' }" :aria-label="stageCopy.structureState">
              <div class="signal-stage__scene-label"><span>{{ stageCopy.state }}</span><strong>{{ String(stageIndex).padStart(2, '0') }} / {{ String(stageSteps.length).padStart(2, '0') }}</strong></div>
              <div v-if="sceneVisualization === 'tree'" class="signal-stage__tree" :aria-label="currentStructureLabel">
                <svg class="signal-stage__tree-links" viewBox="0 0 700 260" preserveAspectRatio="none" aria-hidden="true"><path d="M350 38L175 120M350 38L525 120M175 120L88 210M175 120L262 210M525 120L438 210M525 120L612 210" /></svg>
                <span v-for="(item, index) in renderedValues" :key="`${index}-${String(item)}`" class="signal-stage__node signal-stage__tree-node" :style="treeNodeStyle(index)" :data-current="pointerIndex === index" :data-written="currentStep?.op === 'set' && currentStep.index === index"><small>{{ String(index).padStart(2, '0') }}</small><strong>{{ typeof item === 'object' ? JSON.stringify(item) : item }}</strong></span>
              </div>
              <div v-else-if="sceneVisualization === 'graph'" class="signal-stage__graph" :aria-label="currentStructureLabel">
                <svg class="signal-stage__graph-links" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><path d="M50 18L22 54M50 18L78 54M22 54L50 84M78 54L50 84M22 54L78 54" /></svg>
                <span v-for="(item, index) in renderedValues" :key="`${index}-${String(item)}`" class="signal-stage__node signal-stage__graph-node" :style="graphNodeStyle(index)" :data-current="pointerIndex === index" :data-written="currentStep?.op === 'set' && currentStep.index === index"><small>V{{ index }}</small><strong>{{ typeof item === 'object' ? JSON.stringify(item) : item }}</strong></span>
              </div>
              <div v-else class="signal-stage__array" :class="{ 'signal-stage__array--hash': sceneVisualization === 'hash' }" :aria-label="currentStructureLabel"><span v-for="(item, index) in renderedValues" :key="`${index}-${String(item)}`" class="signal-stage__node" :data-current="pointerIndex === index" :data-written="currentStep?.op === 'set' && currentStep.index === index"><small>{{ sceneVisualization === 'hash' ? `B${index}` : String(index).padStart(2, '0') }}</small><strong>{{ typeof item === 'object' ? JSON.stringify(item) : item }}</strong></span></div>
              <div v-if="renderedValues.length && sceneVisualization === 'linear'" class="signal-stage__pointer" :style="{ '--pointer-index': String(pointerIndex), '--pointer-total': String(renderedValues.length) }"><i></i><span>{{ stageCopy.writeIndex }}</span></div>
              <p v-if="!renderedValues.length" class="signal-stage__empty">{{ stageCopy.empty }}</p>
              <div class="signal-stage__scene-code" :aria-label="stageCopy.snippet"><span v-for="(line, index) in codeLines" :key="line" :data-current="stageIndex >= Math.min(index + 1, stageSteps.length)">{{ String(index + 1).padStart(2, '0') }} {{ line }}</span></div>
            </div>
            <aside class="signal-stage__explain" aria-live="polite"><span class="signal-stage__step">{{ stageCopy.step }} {{ String(Math.max(stageIndex, 1)).padStart(2, '0') }}</span><h2>{{ activeStep?.label || stageSteps[0]?.label }}</h2><p>{{ activeStep?.note || displayDefinition.description }}</p><ol class="signal-stage__steps" :aria-label="stageCopy.steps"><li v-for="(item, index) in stageSteps" :key="`${item.label}-${index}`" :data-active="index === Math.max(stageIndex - 1, 0)" :data-complete="index < stageIndex"><span>{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ item.label }}</strong></li></ol></aside>
          </div>
          <footer class="signal-stage__statusbar"><div class="signal-stage__controls" :aria-label="stageCopy.controls"><button data-testid="animation-previous" type="button" :aria-label="stageCopy.previous" :title="stageCopy.previous" :disabled="player.state.index === 0" @click="player.previous"><span aria-hidden="true">←</span></button><button v-if="!reducedMotion && !player.state.playing" data-testid="animation-play" class="signal-stage__play" type="button" :aria-label="stageCopy.play" :title="stageCopy.play" :disabled="player.state.index >= stageSteps.length" @click="player.play"><span aria-hidden="true">▶</span></button><button v-else-if="!reducedMotion" data-testid="animation-pause" class="signal-stage__play" type="button" :aria-label="stageCopy.pause" :title="stageCopy.pause" @click="player.pause"><span class="signal-stage__pause" aria-hidden="true"></span></button><button data-testid="animation-next" type="button" :aria-label="stageCopy.next" :title="stageCopy.next" :disabled="player.state.index >= stageSteps.length" @click="player.next"><span aria-hidden="true">→</span></button><button data-testid="animation-reset" type="button" :aria-label="stageCopy.reset" :title="stageCopy.reset" @click="player.reset"><span aria-hidden="true">↻</span></button></div><p><span>{{ stageContextDetail }}</span></p><label class="signal-stage__speed">{{ stageCopy.speed }}<RuntimeSelect class-name="signal-stage__speed-select" :model-value="player.state.speed" :options="speedOptions" :ariaLabel="stageCopy.speed" @update:model-value="player.setSpeed(Number($event))" /></label></footer>
        </section>
        <UserState v-if="loading" mode="loading" :title="stageCopy.loadingTitle" :message="stageCopy.loadingDetail" /><UserState v-else-if="displayedError" :mode="displayedError.kind === 'permission' ? 'permission' : 'error'" :title="displayedError.title" :message="displayedError.message" :retry-label="displayedError.retryable ? stageCopy.rerun : undefined" @retry="simulate" />
        <details class="algorithm-custom-operation"><summary data-testid="animation-customize"><span><strong>{{ stageCopy.customize }}</strong><small>{{ stageCopy.customizeHint }}</small></span><i aria-hidden="true"></i></summary><form class="algorithm-custom-operation__form" @submit.prevent="simulate"><div class="algorithm-custom-operation__fields"><label>{{ stageCopy.structure }}<RuntimeSelect v-model="structure" :options="structureOptions" :ariaLabel="stageCopy.structure" test-id="animation-structure" /></label><label>{{ stageCopy.operation }}<RuntimeSelect v-model="operation" :options="operationOptions" :ariaLabel="stageCopy.operation" test-id="animation-operation" /></label><label>{{ stageCopy.initialData }}<input v-model="initial" data-testid="animation-initial" /></label><label>{{ stageCopy.operationValue }}<input v-model="value" data-testid="animation-value" :disabled="!['push','enqueue','insert','append','set'].includes(operation)" /></label></div><button class="algorithm-custom-operation__run" data-testid="animation-run" type="submit" :disabled="loading">{{ loading ? stageCopy.running : stageCopy.run }}</button></form></details>
        <section class="algorithm-observation"><div><span>{{ stageCopy.learningNote }}</span><h2>{{ stageCopy.observation }}</h2><p>{{ observationBoundaryCopy }}</p></div><label><span class="sr-only">{{ stageCopy.observation }}</span><textarea v-model="observed" data-testid="animation-observation" maxlength="2000" :placeholder="stageCopy.observationHint"></textarea></label><div class="algorithm-observation__actions"><button type="button" data-testid="animation-save-observation" :disabled="!observed.trim() || savingObservation || !stage.animationRecordId" @click="saveObservation">{{ savingObservation ? stageCopy.saving : stageCopy.save }}</button><span v-if="observationStatus" aria-live="polite">{{ observationStatus }}</span></div></section>
      </template>
    </section>
    <template #rail><div class="algorithm-stage-rail"><span>{{ stageCopy.context }}</span><strong>{{ hasLearningContext ? stageContextLabel : (isEnglish ? 'LEARNING POSITION UNAVAILABLE' : '学习位置不可用') }}</strong><span>{{ stageCopy.source }}</span><strong>{{ stageSourceLabel }}</strong><p>{{ stageSourceDetail }}</p></div></template>
  </UserFrame>
</template>

<style scoped>
.algorithm-stage-page { display: grid; gap: 1rem; min-width: 0; }.algorithm-stage-page__heading { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 1rem; padding-bottom: .9rem; border-bottom: 1px solid var(--line); }.algorithm-stage-page__eyebrow { margin: 0 0 .2rem; color: var(--text-muted); font-family: var(--font-mono); font-size: .64rem; letter-spacing: .1em; }.algorithm-stage-page h1 { margin: 0; color: var(--text); font-size: 1.75rem; }.algorithm-stage-page__actions { display: inline-flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: .45rem; }.algorithm-stage-page__chip { display: inline-flex; min-height: 28px; align-items: center; gap: .42rem; padding: 0 .55rem; border: 1px solid var(--line-strong); color: var(--text-muted); font-family: var(--font-mono); font-size: .58rem; letter-spacing: .07em; }.algorithm-stage-page__chip i { width: .38rem; height: .38rem; border-radius: 50%; background: currentColor; }.algorithm-stage-page__return { display: inline-flex; min-height: 32px; align-items: center; padding: 0 .68rem; border: 1px solid var(--line-strong); color: var(--text); font-size: .72rem; text-decoration: none; }.algorithm-stage-page__return:hover { background: var(--surface-subtle); }
.signal-stage { --signal-bg: #f3f2ef; --signal-bg-deep: #e5e3de; --signal-surface: rgba(255,255,255,.76); --signal-surface-strong: rgba(255,255,255,.9); --signal-ink: #242422; --signal-ink-soft: #6b6964; --signal-line: rgba(39,38,35,.18); --signal-line-strong: rgba(39,38,35,.34); --signal-grid: rgba(39,38,35,.075); --signal-path: rgba(39,38,35,.38); --signal-fill: rgba(39,38,35,.055); --signal-halo: rgba(39,38,35,.14); --signal-node-bg: linear-gradient(150deg, rgba(255,255,255,.92), rgba(211,209,203,.9)); --signal-node-ink: #252422; --signal-node-muted: rgba(39,38,35,.48); --signal-control-bg: rgba(255,255,255,.82); --signal-control-hover: rgba(226,224,219,.94); --signal-shadow: 0 20px 48px rgba(39,38,35,.14); position: relative; display: grid; min-height: 37rem; grid-template-rows: auto minmax(0, 1fr) auto; overflow: hidden; isolation: isolate; border: 1px solid var(--signal-line-strong); background: var(--signal-bg); color: var(--signal-ink); box-shadow: var(--signal-shadow); }
:global(html[data-theme="light"] .signal-stage) { --signal-bg: #f3f2ef; --signal-bg-deep: #e5e3de; --signal-surface: rgba(255,255,255,.76); --signal-surface-strong: rgba(255,255,255,.9); --signal-ink: #242422; --signal-ink-soft: #6b6964; --signal-line: rgba(39,38,35,.18); --signal-line-strong: rgba(39,38,35,.34); --signal-grid: rgba(39,38,35,.075); --signal-path: rgba(39,38,35,.38); --signal-fill: rgba(39,38,35,.055); --signal-halo: rgba(39,38,35,.14); --signal-node-bg: linear-gradient(150deg, rgba(255,255,255,.92), rgba(211,209,203,.9)); --signal-node-ink: #252422; --signal-node-muted: rgba(39,38,35,.48); --signal-control-bg: rgba(255,255,255,.82); --signal-control-hover: rgba(226,224,219,.94); --signal-shadow: 0 20px 48px rgba(39,38,35,.14); }
:global([data-theme="dark"] .signal-stage) { --signal-bg: #0b0b0a; --signal-bg-deep: #181817; --signal-surface: rgba(21,21,20,.7); --signal-surface-strong: rgba(12,12,11,.84); --signal-ink: #f0f0ed; --signal-ink-soft: #a8a8a2; --signal-line: rgba(240,240,235,.18); --signal-line-strong: #3e3e3b; --signal-grid: rgba(255,255,255,.045); --signal-path: rgba(242,242,237,.36); --signal-fill: rgba(238,238,232,.035); --signal-halo: rgba(238,238,234,.1); --signal-node-bg: linear-gradient(150deg, rgba(101,101,98,.72), rgba(27,27,26,.94)); --signal-node-ink: #f0f0eb; --signal-node-muted: rgba(241,241,235,.46); --signal-control-bg: rgba(39,39,37,.76); --signal-control-hover: rgba(76,76,72,.84); --signal-shadow: 0 20px 48px rgba(0,0,0,.24); }
.signal-stage__background { position: absolute; z-index: -1; inset: 0; overflow: hidden; background: radial-gradient(circle at 68% 38%, color-mix(in srgb, var(--signal-ink) 8%, transparent), transparent 19rem), linear-gradient(145deg, var(--signal-bg-deep) 0%, var(--signal-bg) 61%); }.signal-stage__background svg { position: absolute; right: 0; bottom: 0; left: 0; width: 100%; height: 43%; opacity: .66; }.signal-stage__background path:first-child { fill: none; stroke: var(--signal-path); stroke-dasharray: 1; stroke-dashoffset: 1; animation: signal-draw 1.1s .15s cubic-bezier(.16,1,.3,1) both; }.signal-stage__background .signal-stage__fill { fill: var(--signal-fill); clip-path: inset(0 100% 0 0); animation: signal-wipe .7s .88s cubic-bezier(.16,1,.3,1) both; }.signal-stage__grid { position: absolute; inset: 0; opacity: .44; background-image: linear-gradient(var(--signal-grid) 1px, transparent 1px), linear-gradient(90deg, var(--signal-grid) 1px, transparent 1px); background-size: 38px 38px; mask-image: linear-gradient(to bottom, black, transparent 86%); }.signal-stage__halo { position: absolute; top: 19%; right: 16%; width: 12rem; height: 12rem; border: 1px solid var(--signal-halo); border-radius: 50%; box-shadow: 0 0 0 2.6rem color-mix(in srgb, var(--signal-halo) 18%, transparent); }
.signal-stage__topline { display: flex; min-height: 64px; align-items: center; justify-content: space-between; gap: 1rem; padding: .85rem 1rem; border-bottom: 1px solid var(--signal-line); background: linear-gradient(90deg, color-mix(in srgb, var(--signal-surface-strong) 86%, transparent), color-mix(in srgb, var(--signal-surface) 38%, transparent)); }.signal-stage__topline > div { display: grid; gap: .18rem; }.signal-stage__topline span, .signal-stage__scene-label, .signal-stage__step, .algorithm-stage-rail > span { color: var(--signal-ink-soft); font-family: var(--font-mono); font-size: .58rem; letter-spacing: .1em; }.signal-stage__topline strong { font-size: .98rem; }.signal-stage__topline-status { display: inline-flex; align-items: center; gap: .45rem; color: var(--signal-ink-soft) !important; letter-spacing: .04em !important; }.signal-stage__topline-status i { width: .35rem; height: .35rem; border-radius: 50%; background: currentColor; }
.signal-stage__layout { display: grid; min-height: 0; grid-template-columns: minmax(0, 1fr) minmax(13rem, .45fr); }.signal-stage__scene { position: relative; display: grid; min-width: 0; min-height: 0; align-content: center; padding: clamp(1rem, 4vw, 3rem); border-right: 1px solid var(--signal-line); }.signal-stage__scene-label { position: absolute; top: 1rem; left: 1rem; display: flex; width: calc(100% - 2rem); justify-content: space-between; }.signal-stage__array { display: flex; justify-content: center; padding-top: 1rem; }.signal-stage__array--hash { gap: .35rem; }.signal-stage__array--hash .signal-stage__node + .signal-stage__node { margin-left: 0; }.signal-stage__tree { position: relative; display: grid; width: min(34rem, 92%); min-height: 14rem; grid-template-columns: repeat(7, minmax(0, 1fr)); grid-template-rows: repeat(3, minmax(2.8rem, 1fr)); align-items: center; justify-self: center; }.signal-stage__tree-links { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }.signal-stage__tree-links path, .signal-stage__graph-links path { fill: none; stroke: var(--signal-path); stroke-width: 1.4; vector-effect: non-scaling-stroke; }.signal-stage__tree-node { z-index: 1; justify-self: center; width: clamp(2.25rem, 6.2vw, 3.45rem); }.signal-stage__tree-node + .signal-stage__tree-node { margin-left: 0; }.signal-stage__graph { position: relative; width: min(28rem, 88%); min-height: 15rem; justify-self: center; }.signal-stage__graph-links { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }.signal-stage__graph-node { position: absolute; z-index: 1; width: clamp(2.5rem, 7vw, 3.65rem); left: var(--graph-x); top: var(--graph-y); transform: translate(-50%, -50%); }.signal-stage__graph-node + .signal-stage__graph-node { margin-left: 0; }.signal-stage__graph-node[data-current="true"] { transform: translate(-50%, calc(-50% - .55rem)); }.signal-stage__node { position: relative; display: grid; width: clamp(2.55rem, 8vw, 4.7rem); aspect-ratio: 1; flex: 0 1 4.7rem; place-items: center; border: 1px solid var(--signal-line-strong); background: var(--signal-node-bg); color: var(--signal-node-ink); box-shadow: inset 1px 1px rgba(255,255,255,.28), inset -1px -1px rgba(0,0,0,.12); font-family: var(--font-mono); transition: transform 220ms cubic-bezier(.16,1,.3,1), background-color 160ms ease, border-color 160ms ease; }.signal-stage__node + .signal-stage__node { margin-left: -1px; }.signal-stage__node small { position: absolute; top: .32rem; left: .34rem; color: var(--signal-node-muted); font-size: .48rem; }.signal-stage__node strong { font-size: clamp(.86rem, 2vw, 1.18rem); font-weight: 400; }.signal-stage__node[data-current="true"] { border-color: var(--signal-ink); transform: translateY(-.55rem); box-shadow: 0 14px 28px color-mix(in srgb, var(--signal-ink) 22%, transparent); }.signal-stage__node[data-written="true"] { background: #cfcec8; color: #121211; }.signal-stage__pointer { --pointer-space: calc(100% / var(--pointer-total)); position: absolute; bottom: 4.6rem; left: calc((var(--pointer-index) + .5) * var(--pointer-space)); display: grid; justify-items: center; gap: .22rem; color: var(--signal-ink-soft); font-family: var(--font-mono); font-size: .52rem; transform: translateX(-50%); transition: left 220ms cubic-bezier(.16,1,.3,1); }.signal-stage__pointer i { width: 1px; height: 1rem; background: currentColor; }.signal-stage__pointer i::after { display: block; width: .35rem; height: .35rem; margin: -.05rem 0 0 -.14rem; border-right: 1px solid currentColor; border-bottom: 1px solid currentColor; content: ""; transform: rotate(45deg); }.signal-stage__empty { color: var(--signal-ink-soft); text-align: center; }.signal-stage__scene-code { position: absolute; right: 1rem; bottom: 1rem; left: 1rem; display: grid; gap: .26rem; color: color-mix(in srgb, var(--signal-ink) 42%, transparent); font-family: var(--font-mono); font-size: .52rem; line-height: 1.4; }.signal-stage__scene-code span[data-current="true"] { color: color-mix(in srgb, var(--signal-ink) 88%, transparent); }.signal-stage__explain { display: grid; align-content: start; gap: .8rem; padding: 1.3rem 1.05rem; background: linear-gradient(180deg, var(--signal-surface), color-mix(in srgb, var(--signal-surface-strong) 28%, transparent)); }.signal-stage__explain h2 { margin: 0; font-size: 1.14rem; }.signal-stage__explain > p { margin: 0; color: var(--signal-ink-soft); font-size: .76rem; line-height: 1.65; }.signal-stage__steps { display: grid; gap: .12rem; margin: .4rem 0 0; padding: 0; list-style: none; }.signal-stage__steps li { display: grid; grid-template-columns: 1.45rem minmax(0, 1fr); gap: .5rem; align-items: center; min-height: 2.3rem; border-top: 1px solid var(--signal-line); color: color-mix(in srgb, var(--signal-ink) 48%, transparent); font-size: .68rem; }.signal-stage__steps li > span { font-family: var(--font-mono); font-size: .53rem; }.signal-stage__steps li[data-active="true"] { color: var(--signal-ink); }.signal-stage__steps li[data-complete="true"] { color: var(--signal-ink-soft); }
.signal-stage__scene--complex { align-content: center; }.signal-stage__scene--complex .signal-stage__scene-code { position: relative; right: auto; bottom: auto; left: auto; margin-top: .6rem; }.signal-stage__scene--complex .signal-stage__tree, .signal-stage__scene--complex .signal-stage__graph { margin-top: .25rem; }
.signal-stage__tree .signal-stage__tree-node + .signal-stage__tree-node { margin-left: 0; }.signal-stage__graph .signal-stage__graph-node { position: absolute; z-index: 1; width: clamp(2.5rem, 7vw, 3.65rem); left: var(--graph-x); top: var(--graph-y); transform: translate(-50%, -50%); }.signal-stage__graph .signal-stage__graph-node + .signal-stage__graph-node { margin-left: 0; }.signal-stage__graph .signal-stage__graph-node[data-current="true"] { transform: translate(-50%, calc(-50% - .55rem)); }
.signal-stage__statusbar { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; min-height: 66px; align-items: center; gap: 1rem; padding: .7rem 1rem; border-top: 1px solid var(--signal-line); background: var(--signal-surface-strong); -webkit-backdrop-filter: blur(14px); backdrop-filter: blur(14px); }.signal-stage__controls { display: inline-flex; gap: .35rem; }.signal-stage__controls button { display: grid; width: 38px; height: 38px; place-items: center; padding: 0; border: 1px solid var(--signal-line-strong); border-radius: 50%; background: var(--signal-control-bg); color: var(--signal-ink); cursor: pointer; transition: background-color 130ms ease, transform 110ms ease; }.signal-stage__controls button:hover:not(:disabled) { background: var(--signal-control-hover); }.signal-stage__controls button:active:not(:disabled) { transform: scale(.95); }.signal-stage__controls button:disabled { opacity: .34; }.signal-stage__controls .signal-stage__play { border-color: var(--signal-ink); background: var(--signal-ink); color: var(--signal-bg); }.signal-stage__pause { display: block; width: 10px; height: 12px; border-right: 3px solid currentColor; border-left: 3px solid currentColor; }.signal-stage__statusbar p { display: inline-flex; min-width: 0; align-items: center; justify-content: center; gap: .45rem; margin: 0; color: var(--signal-ink-soft); font-size: .62rem; text-align: center; }.signal-stage__statusbar p span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.signal-stage__statusbar p i { width: .25rem; height: .25rem; flex: 0 0 auto; border-radius: 50%; background: currentColor; }.signal-stage__speed { display: inline-flex; align-items: center; gap: .45rem; color: var(--signal-ink-soft); font-family: var(--font-mono); font-size: .58rem; }.signal-stage__speed-select { width: 4.8rem; --runtime-select-height: 2rem; --runtime-select-min-width: 4.8rem; }
.algorithm-custom-operation { border: 1px solid var(--line); background: var(--surface); }.algorithm-custom-operation > summary { display: flex; min-height: 58px; align-items: center; justify-content: space-between; padding: .65rem .85rem; cursor: pointer; list-style: none; }.algorithm-custom-operation > summary::-webkit-details-marker { display: none; }.algorithm-custom-operation summary > span { display: grid; gap: .08rem; }.algorithm-custom-operation summary strong { font-size: .82rem; }.algorithm-custom-operation summary small { color: var(--text-muted); font-family: var(--font-mono); font-size: .57rem; }.algorithm-custom-operation summary > i { width: 9px; height: 9px; border-right: 1px solid currentColor; border-bottom: 1px solid currentColor; color: var(--text-muted); transform: rotate(45deg) translateY(-2px); }.algorithm-custom-operation[open] summary > i { transform: rotate(225deg) translate(-2px, -2px); }.algorithm-custom-operation__form { display: grid; gap: .85rem; padding: .9rem; border-top: 1px solid var(--line); }.algorithm-custom-operation__fields { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .65rem; }.algorithm-custom-operation label { display: grid; gap: .34rem; min-width: 0; color: var(--text-muted); font-size: .7rem; }.algorithm-custom-operation .runtime-select, .algorithm-custom-operation input { width: 100%; min-width: 0; --runtime-select-height: 2.25rem; --runtime-select-min-width: 0; }.algorithm-custom-operation input { min-height: 36px; padding: 0 .5rem; border-color: var(--line-strong); border-radius: 0; background: var(--surface); color: var(--text); font-size: .74rem; }.algorithm-custom-operation__run { min-height: 38px; border: 1px solid var(--text); background: var(--text); color: var(--surface); cursor: pointer; font-size: .73rem; }.algorithm-custom-operation__run:disabled { opacity: .55; }
.algorithm-observation { display: grid; grid-template-columns: minmax(10rem, .48fr) minmax(0, 1fr); gap: 1rem; align-items: start; padding: 1rem; border-top: 1px solid var(--line); }.algorithm-observation > div:first-child { display: grid; gap: .35rem; }.algorithm-observation h2, .algorithm-observation p { margin: 0; }.algorithm-observation h2 { font-size: .94rem; }.algorithm-observation p { color: var(--text-muted); font-size: .72rem; line-height: 1.6; }.algorithm-observation textarea { width: 100%; min-height: 5.5rem; padding: .65rem; border: 1px solid var(--line-strong); background: var(--surface); color: var(--text); font-size: .76rem; resize: vertical; }.algorithm-observation__actions { grid-column: 2; display: flex; align-items: center; gap: .65rem; }.algorithm-observation__actions button { min-height: 34px; padding: 0 .65rem; border: 1px solid var(--line-strong); background: var(--surface); color: var(--text); cursor: pointer; font-size: .72rem; }.algorithm-observation__actions button:disabled { opacity: .55; }.algorithm-observation__actions span { color: var(--text-muted); font-size: .68rem; }.algorithm-stage-rail { display: grid; gap: .35rem; margin-top: .3rem; }.algorithm-stage-rail > strong { color: var(--text); font-size: .76rem; }.algorithm-stage-rail > p { margin: 0 0 .55rem; color: var(--text-muted); font-size: .68rem; line-height: 1.55; }
@keyframes signal-draw { to { stroke-dashoffset: 0; } } @keyframes signal-wipe { to { clip-path: inset(0 0 0 0); } }
@media (max-width: 860px) { .signal-stage__layout { grid-template-columns: 1fr; }.signal-stage__scene { min-height: 20rem; border-right: 0; border-bottom: 1px solid var(--signal-line); }.signal-stage__statusbar { grid-template-columns: auto minmax(0, 1fr); }.signal-stage__speed { grid-column: 1 / -1; justify-content: flex-end; }.algorithm-custom-operation__fields { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 620px) { .algorithm-stage-page__heading { align-items: flex-start; }.algorithm-stage-page__actions { justify-content: flex-start; }.signal-stage { min-height: auto; }.signal-stage__scene { min-height: 19rem; }.signal-stage__steps { grid-template-columns: repeat(2, minmax(0, 1fr)); }.signal-stage__statusbar { grid-template-columns: 1fr; gap: .65rem; }.signal-stage__controls, .signal-stage__statusbar p, .signal-stage__speed { justify-content: center; }.algorithm-custom-operation__fields, .algorithm-observation { grid-template-columns: 1fr; }.algorithm-observation__actions { grid-column: auto; }.signal-stage__node { width: clamp(2.35rem, 13vw, 3.55rem); }.signal-stage__array--hash { display: grid; width: min(100%, 20rem); grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .35rem; padding-inline: .25rem; }.signal-stage__array--hash .signal-stage__node { width: 100%; flex: none; }.signal-stage__scene-code { font-size: .48rem; } }
@media (prefers-reduced-motion: reduce) { .signal-stage__background path:first-child, .signal-stage__background .signal-stage__fill { animation: none; }.signal-stage__background path:first-child { stroke-dashoffset: 0; }.signal-stage__background .signal-stage__fill { clip-path: inset(0 0 0 0); }.signal-stage__node, .signal-stage__pointer, .signal-stage__controls button { transition: none; } }
@media (prefers-reduced-transparency: reduce) { .signal-stage__statusbar { background: var(--signal-surface-strong); -webkit-backdrop-filter: none; backdrop-filter: none; } }

/* AI Runtime contract: this route is the full algorithm view inside the
   shared Runtime shell. Keep the scene readable and stateful, but remove the
   former standalone Signal skin so navigation into the stage feels continuous. */
.algorithm-stage-page h1 {
  font-family: "Fusion Pixel CJK", "Fusion Pixel 12px Proportional SC", var(--font-ui);
  font-size: 1.9rem;
  font-weight: 400;
}

.signal-stage {
  border-radius: 0;
  box-shadow: 0 20px 42px rgba(29, 29, 28, .13);
}

.signal-stage__background {
  background: var(--signal-bg);
}

.signal-stage__grid { opacity: .16; }
.signal-stage__halo { opacity: .08; box-shadow: none; }
.signal-stage__topline { background: var(--signal-surface-strong); }
.signal-stage__explain { background: var(--signal-surface); }
.signal-stage__controls button { border-radius: 3px; }

@media (prefers-reduced-transparency: reduce) {
  .signal-stage__topline,
  .signal-stage__explain,
  .signal-stage__statusbar {
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
    background: var(--signal-surface-strong);
  }
}

/* Keep the stage on one neutral visual contract. The former Signal skin
   mixed fixed light values with the Runtime overrides, so a theme switch
   could leave written nodes and inset highlights in the previous theme. */
.signal-stage {
  --signal-node-written-bg: #d6d5d0;
  --signal-node-written-ink: #1f1f1d;
  --signal-inset-light: rgba(255, 255, 255, .3);
  --signal-inset-dark: rgba(0, 0, 0, .12);
  --signal-bg-gradient: radial-gradient(circle at 68% 38%, color-mix(in srgb, var(--signal-ink) 8%, transparent), transparent 19rem), linear-gradient(145deg, var(--signal-bg-deep) 0%, var(--signal-bg) 61%);
}

:global(html[data-theme="light"] .signal-stage) {
  --signal-node-written-bg: #d6d5d0;
  --signal-node-written-ink: #1f1f1d;
  --signal-inset-light: rgba(255, 255, 255, .3);
  --signal-inset-dark: rgba(0, 0, 0, .12);
}

:global(html[data-theme="dark"] .signal-stage) {
  --signal-node-written-bg: #444441;
  --signal-node-written-ink: #f0f0ed;
  --signal-inset-light: rgba(255, 255, 255, .12);
  --signal-inset-dark: rgba(0, 0, 0, .42);
}

.signal-stage__background {
  background: var(--signal-bg-gradient);
}

.signal-stage__node {
  box-shadow: inset 1px 1px var(--signal-inset-light), inset -1px -1px var(--signal-inset-dark);
}

.signal-stage__node[data-written="true"] {
  background: var(--signal-node-written-bg);
  color: var(--signal-node-written-ink);
}

@media (min-width: 1100px) and (max-width: 1439px) {
  .signal-stage__layout {
    grid-template-columns: minmax(0, 1fr) minmax(15rem, .46fr);
  }

  .signal-stage__scene {
    padding: clamp(1.5rem, 3.2vw, 2.8rem);
  }

  .signal-stage__node {
    width: clamp(3rem, 6.4vw, 4.5rem);
  }

  .signal-stage__node strong {
    font-size: clamp(.98rem, 1.8vw, 1.3rem);
  }

  .signal-stage__explain > p {
    font-size: .82rem;
  }
}

@media (min-width: 1440px) {
  .signal-stage {
    min-height: clamp(37rem, 68vh, 47rem);
  }

  .signal-stage__layout {
    grid-template-columns: minmax(0, 1fr) minmax(16rem, .42fr);
  }

  .signal-stage__topline {
    min-height: 72px;
    padding-inline: 1.35rem;
  }

  .signal-stage__topline span,
  .signal-stage__scene-label,
  .signal-stage__step,
  .algorithm-stage-rail > span {
    font-size: .66rem;
  }

  .signal-stage__topline strong {
    font-size: 1.12rem;
  }

  .signal-stage__scene {
    padding: clamp(2rem, 4vw, 4rem);
  }

  .signal-stage__node {
    width: clamp(3.5rem, 5.2vw, 5.8rem);
  }

  .signal-stage__node small {
    font-size: .58rem;
  }

  .signal-stage__node strong {
    font-size: clamp(1.05rem, 1.65vw, 1.46rem);
  }

  .signal-stage__explain {
    padding: 1.6rem 1.35rem;
  }

  .signal-stage__explain h2 {
    font-size: 1.3rem;
  }

  .signal-stage__explain > p {
    font-size: .86rem;
  }

  .signal-stage__steps li {
    min-height: 2.7rem;
    font-size: .78rem;
  }

  .signal-stage__scene-code {
    font-size: .62rem;
  }
}

@media (min-width: 1800px) {
  .signal-stage__layout {
    grid-template-columns: minmax(0, 1fr) minmax(19rem, .4fr);
  }

  .signal-stage__scene {
    padding-inline: clamp(3rem, 5vw, 5.5rem);
  }
}

@media (prefers-reduced-transparency: reduce) {
  .signal-stage__background {
    background: var(--signal-bg-deep);
  }

  .signal-stage__topline,
  .signal-stage__explain,
  .signal-stage__statusbar {
    background: var(--signal-surface-strong);
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
  }

  .signal-stage__halo {
    display: none;
  }
}
</style>
