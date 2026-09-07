<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import type { SseEvent } from "../../shared/api";
import type { AiReadiness, ChatResponse, ChatSession, ChatSessionSummary, ChatSource } from "../../shared/types/contracts";
import { useLocale } from "../../shared/i18n/locale";
import { auth } from "../../app/providers/runtime";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import { userApi } from "../runtime";
import { createLoginTarget } from "../login-target";
import { findCourseItem, flattenCourseGroups, localizedCourseGroups } from "../fixtures/learning-workbench";
import { previewChapter } from "../fixtures/course-preview";
import RuntimeSelect, { type RuntimeSelectOption } from "../../shared/components/RuntimeSelect.vue";

type MessageState = "complete" | "streaming" | "stopped" | "error";

interface ConversationMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  sources: ChatSource[];
  state: MessageState;
  createdAt?: string;
  errorCode?: string;
}

interface ChatAttempt {
  prompt: string;
  chapterId?: string;
  sessionId?: string;
}

interface ChatError extends UserErrorPresentation {
  code?: string;
}

const route = useRoute();
const { isEnglish } = useLocale();
const loginTarget = computed(() => createLoginTarget(route.fullPath));
const prompt = ref("");
const chapterId = ref(readQueryString(route.query.chapterId));
const messages = ref<ConversationMessage[]>([]);
const sessions = ref<ChatSessionSummary[]>([]);
const activeSessionId = ref<string | null>(null);
const readiness = ref<AiReadiness | null>(null);
const readinessLoading = ref(false);
const readinessError = ref<UserErrorPresentation | null>(null);
const sessionsLoading = ref(false);
const sessionsError = ref<UserErrorPresentation | null>(null);
const sessionLoading = ref(false);
const sessionError = ref<UserErrorPresentation | null>(null);
const chatError = ref<ChatError | null>(null);
const deletingSessionId = ref<string | null>(null);
const deleteConfirmationId = ref<string | null>(null);
const retryAttempt = ref<ChatAttempt | null>(null);
const sendPhase = ref<"idle" | "checking" | "streaming">("idle");
const newConversationButton = ref<HTMLButtonElement | null>(null);
const sessionButtons = new Map<string, HTMLButtonElement>();

let messageSequence = 0;
let activeController: AbortController | null = null;
let sessionLoadVersion = 0;
let readinessLoadVersion = 0;

const isBusy = computed(() => sendPhase.value !== "idle");
const isStreaming = computed(() => sendPhase.value === "streaming");
const canSubmit = computed(() => Boolean(prompt.value.trim()) && !isBusy.value);
const isAuthenticated = computed(() => Boolean(auth.state.user));
const copy = computed(() => isEnglish.value ? {
  currentCourse: "Current course",
  allAuthorizedChapters: "All available chapters",
  unknownCourseContext: "Current course context",
  introAuthenticated: "Ask about the current chapter. The system checks course evidence and availability before generating a response.",
  introGuest: "Use the current chapter to explore concepts and steps. Sign in here when you are ready to ask the model.",
  readinessGuest: "Sign in to start course Q&A",
  readinessChecking: "Checking current Q&A conditions",
  readinessUnavailable: "Q&A conditions have not been loaded",
  readinessAllowed: "Ready for course Q&A",
  readinessBlocked: "Course Q&A is not available yet",
  modelAvailable: "Model available",
  modelUnavailable: "Model unavailable",
  evidenceAvailable: "Course evidence available",
  evidenceUnavailable: "Course evidence unavailable",
  evidenceNotRequired: "Course evidence is not required",
  eyebrow: "Course coach",
  title: "Course Q&A",
  newConversation: "New conversation",
  viewAlgorithm: "View chapter algorithm",
  refresh: "Refresh",
  readiness: "Generation conditions",
  signInTitle: "Sign in to start course Q&A",
  signInMessage: "Courses, resources, and algorithm interactions remain available. Sign in only when you submit a model question.",
  signInContinue: "Sign in to continue",
  reload: "Reload",
  content: "Course Q&A content",
  loadingSessions: "Loading conversation history",
  loadingSessionsDetail: "Reading conversations for the current account.",
  emptyTitle: "Start a course conversation",
  emptyDetail: "After you enter a question, the current course evidence and model quota will be checked first.",
  generatingAnswer: "Generating answer...",
  generating: "Generating",
  stopped: "Generation stopped",
  incomplete: "Answer was not completed",
  sentAt: "Sent",
  sources: "Course evidence",
  scope: "Course scope (optional)",
  question: "Question",
  questionPlaceholder: "Ask what you want to understand using the course materials",
  checking: "Checking...",
  send: "Send question",
  stop: "Stop",
  retry: "Retry this question",
  accountSessions: "Account conversations",
  historySessions: "Conversation history",
  guestSessions: "Sign in to save and restore course conversations.",
  currentSession: "Current conversation",
  noSessions: "No course conversations have been saved.",
  delete: "Delete",
  deleting: "Deleting...",
  confirm: "Confirm",
  cancel: "Cancel",
  currentScope: "Current scope",
  quotaAvailable: "Quota available",
  quotaExhausted: "Today's quota is used",
  quotaLimited: "Processing another request",
  quotaUnconfigured: "Quota is not configured",
  evidenceMissingTitle: "No usable course evidence for this question",
  answerIncompleteTitle: "This answer was not completed",
  evidenceMissingDetail: "Adjust the question or switch to a chapter with reviewed course materials.",
  answerIncompleteDetail: "The service ended before returning a complete answer. You can retry this question.",
  modelUnavailableDetail: "The model service is currently unavailable.",
  readinessNotMet: "The current Q&A conditions are not met.",
  permissionTitle: "Sign in to unlock course Q&A",
  permissionDetail: "You can browse courses and the algorithm stage first. Sign in here when you submit a model question.",
  readinessUnknownTitle: "Unable to verify Q&A conditions",
  tryAgain: "Please try again shortly.",
  formalBlockedTitle: "Course Q&A cannot start yet",
  continueLearning: "Continue learning",
  openCurrentCourse: "Open current course",
  browseMaterials: "Browse course materials",
  resourceUnavailableTitle: "This learning item is not available",
  resourceUnavailableDetail: "It may not be published or included in this account's access.",
  stateChangedTitle: "This page has changed",
  stateChangedDetail: "Refresh and try again so the latest learning state is kept.",
  requestLimitedTitle: "Please wait before trying again",
  requestLimitedDetail: "The service is temporarily limiting requests.",
  serviceUnavailableTitle: "Learning services are temporarily unavailable",
  serviceUnavailableDetail: "Your current page is still available. Try again once the service recovers.",
  timeoutTitle: "The request timed out",
  timeoutDetail: "The service did not respond in time.",
  networkTitle: "Network connection is unavailable",
  networkDetail: "Check the connection and try again. Your current learning context will remain here.",
  validationTitle: "This question cannot be processed",
  validationDetail: "Check the question and try again.",
  unknownTitle: "This action was not completed",
  unknownDetail: "The service returned an unexpected result. Please try again.",
} : {
  currentCourse: "当前课程",
  allAuthorizedChapters: "全部已授权章节",
  unknownCourseContext: "当前课程上下文",
  introAuthenticated: "围绕当前章节提问，系统会先核验课程依据与当前可用条件。",
  introGuest: "围绕当前章节理解概念与步骤；提交模型问题时，再在当前页面登录。",
  readinessGuest: "登录后开始模型问答",
  readinessChecking: "正在核验当前问答条件",
  readinessUnavailable: "尚未读取当前问答条件",
  readinessAllowed: "当前可开始正式问答",
  readinessBlocked: "当前暂不能开始正式问答",
  modelAvailable: "模型可用",
  modelUnavailable: "模型不可用",
  evidenceAvailable: "课程依据可用",
  evidenceUnavailable: "课程依据不足",
  evidenceNotRequired: "本操作不要求课程依据",
  eyebrow: "课程陪练",
  title: "课程问答",
  newConversation: "新建对话",
  viewAlgorithm: "查看本章算法",
  refresh: "刷新",
  readiness: "生成条件",
  signInTitle: "登录后即可开始课程问答",
  signInMessage: "课程、资料和算法操作保持可用；提交模型问题时才需要登录。",
  signInContinue: "登录后继续",
  reload: "重新读取",
  content: "课程问答内容",
  loadingSessions: "正在载入历史会话",
  loadingSessionsDetail: "正在读取属于当前账户的对话记录。",
  emptyTitle: "开始一段课程问答",
  emptyDetail: "输入问题后，将先核验当前课程依据和模型配额。",
  generatingAnswer: "正在生成回答…",
  generating: "正在生成",
  stopped: "已停止生成",
  incomplete: "回答未完成",
  sentAt: "发送于",
  sources: "课程依据",
  scope: "课程范围（可选）",
  question: "问题",
  questionPlaceholder: "输入你希望结合课程资料理解的问题",
  checking: "正在核验…",
  send: "发送问题",
  stop: "停止",
  retry: "重试本次问题",
  accountSessions: "账户会话",
  historySessions: "历史会话",
  guestSessions: "登录后可保存和恢复课程问答会话。",
  currentSession: "当前会话",
  noSessions: "暂未保存课程问答会话。",
  delete: "删除",
  deleting: "删除中…",
  confirm: "确认",
  cancel: "取消",
  currentScope: "当前范围",
  quotaAvailable: "配额可用",
  quotaExhausted: "今日配额已用完",
  quotaLimited: "并发处理中",
  quotaUnconfigured: "配额未配置",
  evidenceMissingTitle: "当前问题缺少可用课程依据",
  answerIncompleteTitle: "本次回答未完成",
  evidenceMissingDetail: "请调整问题或切换到已有审核资料的章节后再试。",
  answerIncompleteDetail: "服务已结束本次回答，请稍后重试。",
  modelUnavailableDetail: "模型服务当前不可用。",
  readinessNotMet: "当前问答条件尚未满足。",
  permissionTitle: "登录后解锁课程问答",
  permissionDetail: "你可以先浏览课程和算法舞台；提交模型问题时，在当前页面登录即可继续。",
  readinessUnknownTitle: "无法确认问答条件",
  tryAgain: "请稍后重试。",
  formalBlockedTitle: "当前不能开始正式问答",
  continueLearning: "继续学习",
  openCurrentCourse: "打开当前课程",
  browseMaterials: "浏览课程资料",
  resourceUnavailableTitle: "当前学习内容不可访问",
  resourceUnavailableDetail: "该内容可能尚未发布，或不在当前账户的访问范围内。",
  stateChangedTitle: "当前页面状态已变化",
  stateChangedDetail: "请刷新后重试，以保持最新的学习状态。",
  requestLimitedTitle: "请稍后再试",
  requestLimitedDetail: "服务暂时限制了请求。",
  serviceUnavailableTitle: "学习服务暂不可用",
  serviceUnavailableDetail: "当前页面位置仍会保留，服务恢复后可以再次尝试。",
  timeoutTitle: "请求超时",
  timeoutDetail: "服务未在规定时间内响应。",
  networkTitle: "网络连接不可用",
  networkDetail: "请检查网络后重试，当前学习上下文会保留在这里。",
  validationTitle: "问题暂时无法处理",
  validationDetail: "请检查问题内容后重试。",
  unknownTitle: "本次操作未完成",
  unknownDetail: "服务返回了未预期的结果，请稍后重试。",
});
const chapterGroups = computed(() => localizedCourseGroups(isEnglish.value ? "en-US" : "zh-CN").map((group) => ({
  id: group.id,
  label: group.label,
  items: flattenCourseGroups([{ ...group, items: group.items }]),
})));
const chapterScopeOptions = computed<RuntimeSelectOption[]>(() => [
  { value: "", label: copy.value.allAuthorizedChapters },
  ...(chapterId.value.trim() && !hasKnownChapter.value
    ? [{ value: chapterId.value, label: copy.value.unknownCourseContext }]
    : []),
  ...chapterGroups.value.flatMap((group) => group.items.map((item) => ({
    value: item.routeId ?? item.id,
    label: `${group.label} · ${item.label}`,
  }))),
]);
const selectedChapter = computed(() => findCourseItem(chapterId.value) ?? previewChapter(chapterId.value));
const hasKnownChapter = computed(() => Boolean(selectedChapter.value));
const chapterScopeLabel = computed(() => {
  const selected = selectedChapter.value;
  if (!selected) return chapterId.value.trim() ? copy.value.currentCourse : copy.value.allAuthorizedChapters;
  if ("label" in selected) return isEnglish.value ? (selected.labelEn ?? selected.label) : selected.label;
  if (isEnglish.value && selected.id === "sequential-list") return "Insertion in a sequential list";
  return selected.title;
});
const chatIntro = computed(() => isAuthenticated.value
  ? copy.value.introAuthenticated
  : copy.value.introGuest);
const chapterTarget = computed(() => ({
  path: "/user/chapters",
  query: { ...(chapterId.value.trim() ? { chapterId: chapterId.value.trim() } : {}), from: "coach" },
}));
const knowledgeTarget = computed(() => ({
  path: "/user/knowledge",
  query: { ...(chapterId.value.trim() ? { chapterId: chapterId.value.trim() } : {}), from: "coach" },
}));
const activeSession = computed(() => sessions.value.find((item) => item.id === activeSessionId.value) ?? null);
const readinessSummary = computed(() => {
  if (!isAuthenticated.value) return copy.value.readinessGuest;
  if (readinessLoading.value) return copy.value.readinessChecking;
  if (readinessError.value) return readinessError.value.message;
  if (!readiness.value) return copy.value.readinessUnavailable;
  return readiness.value.allowFormalGeneration ? copy.value.readinessAllowed : copy.value.readinessBlocked;
});
const readinessTone = computed(() => {
  if (!isAuthenticated.value) return "warning";
  if (readinessLoading.value) return "neutral";
  if (readinessError.value || !readiness.value?.allowFormalGeneration) return "warning";
  return "success";
});

watch(
  () => route.query.chapterId,
  (value) => {
    if (isBusy.value) return;
    const nextChapterId = readQueryString(value);
    if (nextChapterId === chapterId.value) return;
    chapterId.value = nextChapterId;
    activeSessionId.value = null;
    messages.value = [];
    sessionError.value = null;
    chatError.value = null;
    if (isAuthenticated.value) void refreshReadiness();
  },
);

onMounted(() => {
  if (isAuthenticated.value) {
    void refreshReadiness();
    void loadSessions();
  }
});

watch(() => auth.state.user?.id, (userId, previousUserId) => {
  if (userId && userId !== previousUserId) {
    void refreshReadiness();
    void loadSessions();
  }
});

onBeforeUnmount(() => {
  activeController?.abort();
  activeController = null;
});

function readQueryString(value: unknown): string {
  if (typeof value === "string") return value.trim();
  if (Array.isArray(value) && typeof value[0] === "string") return value[0].trim();
  return "";
}

function nextMessageId(): string {
  messageSequence += 1;
  return `chat-message-${messageSequence}`;
}

function makeAttempt(value: string): ChatAttempt {
  const selectedChapterId = chapterId.value.trim();
  return {
    prompt: value,
    ...(selectedChapterId ? { chapterId: selectedChapterId } : {}),
    ...(activeSessionId.value ? { sessionId: activeSessionId.value } : {}),
  };
}

function updateMessage(id: string, update: Partial<ConversationMessage>): void {
  const message = messages.value.find((item) => item.id === id);
  if (message) Object.assign(message, update);
}

function chatErrorFrom(cause: unknown): ChatError {
  const error = cause as { code?: unknown } | null;
  return { ...localizedUserError(presentUserError(cause)), ...(typeof error?.code === "string" ? { code: error.code } : {}) };
}

function localizedUserError(error: UserErrorPresentation): UserErrorPresentation {
  if (!isEnglish.value) return error;
  const localized = {
    permission: { title: copy.value.permissionTitle, message: copy.value.permissionDetail },
    "not-found": { title: copy.value.resourceUnavailableTitle, message: copy.value.resourceUnavailableDetail },
    conflict: { title: copy.value.stateChangedTitle, message: copy.value.stateChangedDetail },
    limited: { title: copy.value.requestLimitedTitle, message: copy.value.requestLimitedDetail },
    timeout: { title: copy.value.timeoutTitle, message: copy.value.timeoutDetail },
    service: { title: copy.value.serviceUnavailableTitle, message: copy.value.serviceUnavailableDetail },
    network: { title: copy.value.networkTitle, message: copy.value.networkDetail },
    validation: { title: copy.value.validationTitle, message: copy.value.validationDetail },
    unknown: { title: copy.value.unknownTitle, message: copy.value.unknownDetail },
  }[error.kind];
  return localized ? { ...error, ...localized } : error;
}

function streamErrorFrom(value: unknown): ChatError {
  const record = asRecord(value);
  const code = typeof record?.code === "string" ? record.code : "CHAT_STREAM_ERROR";
  const serverMessage = typeof record?.message === "string" ? record.message.trim() : "";
  const evidenceUnavailable = code === "CHAT_EVIDENCE_UNAVAILABLE";
  const modelUnavailable = code === "MODEL_NOT_CONFIGURED";
  return {
    kind: evidenceUnavailable ? "validation" : "service",
    title: evidenceUnavailable ? copy.value.evidenceMissingTitle : copy.value.answerIncompleteTitle,
    message: isEnglish.value
      ? (evidenceUnavailable ? copy.value.evidenceMissingDetail : modelUnavailable ? copy.value.modelUnavailableDetail : copy.value.answerIncompleteDetail)
      : serverMessage || (evidenceUnavailable ? copy.value.evidenceMissingDetail : modelUnavailable ? copy.value.modelUnavailableDetail : copy.value.answerIncompleteDetail),
    retryable: true,
    code,
  };
}

function unfinishedStreamError(): ChatError {
  return {
    kind: "service",
    title: copy.value.answerIncompleteTitle,
    message: copy.value.answerIncompleteDetail,
    retryable: true,
    code: "CHAT_STREAM_INCOMPLETE",
  };
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function parsedEventValue(event: SseEvent<unknown>): unknown {
  if (event.parsed !== undefined) return event.parsed;
  try {
    return JSON.parse(event.data) as unknown;
  } catch {
    return event.data;
  }
}

function isChatSource(value: unknown): value is ChatSource {
  const source = asRecord(value);
  return Boolean(source
    && typeof source.id === "string"
    && typeof source.chapterId === "string"
    && typeof source.title === "string"
    && typeof source.content === "string"
    && typeof source.source === "string"
    && (typeof source.pageLabel === "string" || source.pageLabel === null)
    && typeof source.score === "number"
    && typeof source.evidenceHash === "string");
}

function sourcesFrom(value: unknown): ChatSource[] {
  const record = asRecord(value);
  const candidates = Array.isArray(value) ? value : Array.isArray(record?.sources) ? record.sources : [];
  return candidates.filter(isChatSource);
}

function deltaFrom(value: unknown): string {
  if (typeof value === "string") return value;
  const record = asRecord(value);
  if (typeof record?.content === "string") return record.content;
  if (typeof record?.delta === "string") return record.delta;
  return "";
}

function responseFrom(value: unknown): ChatResponse | null {
  const record = asRecord(value);
  if (!record || typeof record.answer !== "string") return null;
  const sessionId = typeof record.sessionId === "string" || record.sessionId === null ? record.sessionId : undefined;
  return {
    answer: record.answer,
    sessionId,
    sources: sourcesFrom(record.sources),
    persisted: record.persisted === true,
  };
}

function historyMessages(session: ChatSession): ConversationMessage[] {
  return session.messages.map((message) => ({
    id: `history-${message.id}`,
    role: message.role,
    content: message.content,
    sources: message.sources,
    state: "complete",
    createdAt: message.createdAt,
  }));
}

function readinessInput(promptValue = ""): { operation: "CHAT"; chapterId?: string; prompt?: string } {
  const selectedChapterId = chapterId.value.trim();
  const selectedPrompt = promptValue.trim();
  return {
    operation: "CHAT",
    ...(selectedChapterId ? { chapterId: selectedChapterId } : {}),
    ...(selectedPrompt ? { prompt: selectedPrompt } : {}),
  };
}

async function refreshReadiness(promptValue = ""): Promise<AiReadiness | null> {
  const version = ++readinessLoadVersion;
  if (!isAuthenticated.value) {
    readinessLoading.value = false;
    readinessError.value = null;
    readiness.value = null;
    return null;
  }
  readinessLoading.value = true;
  readinessError.value = null;
  try {
    const value = await userApi.getReadiness(readinessInput(promptValue));
    if (version !== readinessLoadVersion) return readiness.value;
    readiness.value = value;
    return value;
  } catch (cause) {
    if (version !== readinessLoadVersion) return readiness.value;
    readinessError.value = localizedUserError(presentUserError(cause));
    return null;
  } finally {
    if (version === readinessLoadVersion) readinessLoading.value = false;
  }
}

async function loadSessions(): Promise<void> {
  if (!isAuthenticated.value) {
    sessionsLoading.value = false;
    sessionsError.value = null;
    sessions.value = [];
    activeSessionId.value = null;
    return;
  }
  sessionsLoading.value = true;
  sessionsError.value = null;
  try {
    sessions.value = await userApi.listChatSessions();
    if (activeSessionId.value && !sessions.value.some((item) => item.id === activeSessionId.value)) {
      activeSessionId.value = null;
    }
  } catch (cause) {
    sessionsError.value = localizedUserError(presentUserError(cause));
  } finally {
    sessionsLoading.value = false;
  }
}

async function openSession(sessionId: string): Promise<void> {
  if (isBusy.value || !isAuthenticated.value) return;
  const version = ++sessionLoadVersion;
  activeSessionId.value = sessionId;
  sessionLoading.value = true;
  sessionError.value = null;
  chatError.value = null;
  retryAttempt.value = null;
  try {
    const session = await userApi.getChatSession(sessionId);
    if (version !== sessionLoadVersion) return;
    messages.value = historyMessages(session);
    if (session.chapterId) chapterId.value = session.chapterId;
  } catch (cause) {
    if (version !== sessionLoadVersion) return;
    sessionError.value = localizedUserError(presentUserError(cause));
  } finally {
    if (version === sessionLoadVersion) sessionLoading.value = false;
  }
}

function startNewConversation(): void {
  if (isBusy.value || !isAuthenticated.value) return;
  sessionLoadVersion += 1;
  activeSessionId.value = null;
  messages.value = [];
  sessionError.value = null;
  chatError.value = null;
  retryAttempt.value = null;
}

function requestDelete(sessionId: string): void {
  deleteConfirmationId.value = sessionId;
}

function cancelDelete(): void {
  deleteConfirmationId.value = null;
}

function setSessionButton(id: string, element: Element | null): void {
  if (element instanceof HTMLButtonElement) sessionButtons.set(id, element);
  else sessionButtons.delete(id);
}

async function deleteSession(sessionId: string): Promise<void> {
  if (isBusy.value || deletingSessionId.value || !isAuthenticated.value) return;
  deletingSessionId.value = sessionId;
  sessionsError.value = null;
  try {
    await userApi.deleteChatSession(sessionId);
    const deletedIndex = sessions.value.findIndex((item) => item.id === sessionId);
    sessions.value = sessions.value.filter((item) => item.id !== sessionId);
    if (activeSessionId.value === sessionId) startNewConversation();
    deleteConfirmationId.value = null;
    await nextTick();
    const nextSession = sessions.value[deletedIndex] ?? sessions.value[deletedIndex - 1];
    if (nextSession) sessionButtons.get(nextSession.id)?.focus();
    else newConversationButton.value?.focus();
  } catch (cause) {
    sessionsError.value = localizedUserError(presentUserError(cause));
  } finally {
    deletingSessionId.value = null;
  }
}

function readinessBlockedMessage(value: AiReadiness): string {
  const reasons = [...new Set(value.blockingReasons.map(readinessReasonMessage))].filter(Boolean);
  if (reasons.length) return reasons.join(isEnglish.value ? "; " : "；");
  if (!value.modelAvailable) return copy.value.modelUnavailableDetail;
  if (value.evidenceRequired && !value.evidenceAvailable) return copy.value.evidenceMissingDetail;
  return copy.value.readinessNotMet;
}

function readinessReasonMessage(reason: string): string {
  switch (reason) {
    case "QUESTION_EVIDENCE_UNAVAILABLE":
    case "CONTEXT_EVIDENCE_UNAVAILABLE":
      return copy.value.evidenceMissingDetail;
    case "AI_QUOTA_EXHAUSTED":
      return copy.value.quotaExhausted;
    case "AI_QUOTA_CONCURRENCY_LIMITED":
      return copy.value.quotaLimited;
    case "AI_QUOTA_NOT_CONFIGURED":
    case "PERSISTED_QUOTA_NOT_CONFIGURED":
    case "ENVIRONMENT_QUOTA_NOT_CONFIGURED":
      return copy.value.quotaUnconfigured;
    case "PERSISTED_CONFIGURATION_DISABLED":
    case "PERSISTED_CONFIGURATION_UNAVAILABLE":
    case "ENVIRONMENT_CONFIGURATION_INCOMPLETE":
    case "MODEL_CONFIG_UNAVAILABLE":
      return copy.value.modelUnavailableDetail;
    default:
      return copy.value.readinessNotMet;
  }
}

function appendSessionFromResponse(response: ChatResponse, attempt: ChatAttempt): void {
  const nextSessionId = response.sessionId ?? attempt.sessionId;
  if (nextSessionId) activeSessionId.value = nextSessionId;
  void loadSessions();
}

async function consumeStream(attempt: ChatAttempt, replyId: string, signal: AbortSignal): Promise<void> {
  const response = await userApi.streamChat(attempt, signal);
  let completed = false;
  let streamedError: ChatError | null = null;

  streamEvents: for await (const event of response.events as AsyncGenerator<SseEvent<unknown>>) {
    const value = parsedEventValue(event);
    if (event.event === "sources") {
      updateMessage(replyId, { sources: sourcesFrom(value) });
      continue;
    }
    if (event.event === "delta") {
      const delta = deltaFrom(value);
      if (delta) {
        const current = messages.value.find((item) => item.id === replyId);
        updateMessage(replyId, { content: `${current?.content ?? ""}${delta}` });
      }
      continue;
    }
    if (event.event === "done") {
      const completedResponse = responseFrom(value);
      if (!completedResponse) {
        streamedError = unfinishedStreamError();
      } else {
        updateMessage(replyId, {
          content: completedResponse.answer,
          sources: completedResponse.sources,
          state: "complete",
          errorCode: undefined,
        });
        appendSessionFromResponse(completedResponse, attempt);
        completed = true;
      }
      break streamEvents;
    }
    if (event.event === "error") {
      streamedError = streamErrorFrom(value);
      break streamEvents;
    }
  }

  if (signal.aborted) {
    updateMessage(replyId, { state: "stopped" });
    return;
  }
  if (streamedError) {
    updateMessage(replyId, { state: "error", errorCode: streamedError.code });
    chatError.value = streamedError;
    return;
  }
  if (!completed) {
    const incomplete = unfinishedStreamError();
    updateMessage(replyId, { state: "error", errorCode: incomplete.code });
    chatError.value = incomplete;
  }
}

async function runAttempt(attempt: ChatAttempt): Promise<void> {
  if (isBusy.value) return;
  if (!isAuthenticated.value) {
    chatError.value = {
      kind: "permission",
      title: copy.value.permissionTitle,
      message: copy.value.permissionDetail,
      retryable: false,
    };
    return;
  }
  retryAttempt.value = attempt;
  chatError.value = null;
  sendPhase.value = "checking";

  const currentReadiness = await refreshReadiness(attempt.prompt);
  if (!currentReadiness) {
    chatError.value = readinessError.value ? { ...readinessError.value } : {
      kind: "unknown",
      title: copy.value.readinessUnknownTitle,
      message: copy.value.tryAgain,
      retryable: true,
    };
    sendPhase.value = "idle";
    return;
  }
  if (!currentReadiness.allowFormalGeneration) {
    chatError.value = {
      kind: "validation",
      title: copy.value.formalBlockedTitle,
      message: readinessBlockedMessage(currentReadiness),
      retryable: true,
      code: "AI_READINESS_BLOCKED",
    };
    sendPhase.value = "idle";
    return;
  }

  const controller = new AbortController();
  activeController = controller;
  sendPhase.value = "streaming";
  const replyId = nextMessageId();
  messages.value.push({ id: replyId, role: "assistant", content: "", sources: [], state: "streaming" });

  try {
    await consumeStream(attempt, replyId, controller.signal);
  } catch (cause) {
    if (controller.signal.aborted || (cause as { name?: string } | null)?.name === "AbortError") {
      updateMessage(replyId, { state: "stopped" });
    } else {
      const error = chatErrorFrom(cause);
      updateMessage(replyId, { state: "error", errorCode: error.code });
      chatError.value = error;
    }
  } finally {
    if (activeController === controller) activeController = null;
    sendPhase.value = "idle";
  }
}

async function submitPrompt(): Promise<void> {
  const value = prompt.value.trim();
  if (!value || isBusy.value) return;
  if (!isAuthenticated.value) {
    chatError.value = {
      kind: "permission",
      title: copy.value.permissionTitle,
      message: copy.value.permissionDetail,
      retryable: false,
    };
    return;
  }
  const attempt = makeAttempt(value);
  messages.value.push({ id: nextMessageId(), role: "user", content: value, sources: [], state: "complete" });
  prompt.value = "";
  await runAttempt(attempt);
}

async function retryLastAttempt(): Promise<void> {
  if (!retryAttempt.value || isBusy.value) return;
  await runAttempt(retryAttempt.value);
}

function stopGeneration(): void {
  activeController?.abort();
}

function formatSessionTime(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString(isEnglish.value ? "en-US" : "zh-CN", { dateStyle: "short", timeStyle: "short" });
}

function quotaLabel(value: AiReadiness["quotaStatus"]): string {
  return {
    AVAILABLE: copy.value.quotaAvailable,
    EXHAUSTED: copy.value.quotaExhausted,
    CONCURRENCY_LIMITED: copy.value.quotaLimited,
    NOT_CONFIGURED: copy.value.quotaUnconfigured,
  }[value];
}

function messageCountLabel(value: number): string {
  if (!isEnglish.value) return `${value} 条消息`;
  return `${value} ${value === 1 ? "message" : "messages"}`;
}
</script>

<template>
  <UserFrame shell="course">
    <section class="user-page" aria-labelledby="chat-title">
      <header class="user-page__heading">
        <div>
          <p class="user-page__eyebrow">{{ copy.eyebrow }}</p>
          <h1 id="chat-title">{{ copy.title }}</h1>
          <p class="user-page__intro">{{ chatIntro }}</p>
        </div>
        <div class="user-page__actions">
          <button v-if="isAuthenticated" ref="newConversationButton" class="user-action" data-testid="chat-new-conversation" type="button" :disabled="isBusy" @click="startNewConversation">{{ copy.newConversation }}</button>
          <RouterLink v-if="chapterId.trim()" class="user-action" data-testid="chat-animation" :to="{ path: '/user/animation', query: { chapterId: chapterId.trim(), from: 'coach' } }">{{ copy.viewAlgorithm }}</RouterLink>
          <button v-if="isAuthenticated" class="user-action" data-testid="chat-refresh-readiness" type="button" :disabled="readinessLoading || isBusy" @click="refreshReadiness()">{{ copy.refresh }}</button>
        </div>
      </header>

      <section class="user-chat__readiness" aria-live="polite" data-testid="chat-readiness">
        <div v-if="isAuthenticated" class="user-panel">
          <div>
            <p class="user-page__eyebrow">{{ copy.readiness }}</p>
            <h2>{{ readinessSummary }}</h2>
          </div>
          <div v-if="readiness" class="user-chat__readiness-grid">
            <span :data-state="readinessTone">{{ readiness.modelAvailable ? copy.modelAvailable : copy.modelUnavailable }}</span>
            <span :data-state="readinessTone">{{ readiness.evidenceRequired ? (readiness.evidenceAvailable ? copy.evidenceAvailable : copy.evidenceUnavailable) : copy.evidenceNotRequired }}</span>
            <span :data-state="readinessTone">{{ quotaLabel(readiness.quotaStatus) }}</span>
          </div>
          <p v-if="readiness && !readiness.allowFormalGeneration" class="inline-notice inline-notice--warning">{{ readinessBlockedMessage(readiness) }}</p>
        </div>
        <UserState
          v-if="!isAuthenticated"
          mode="permission"
          :title="copy.signInTitle"
          :message="copy.signInMessage"
        >
          <RouterLink class="user-action user-action--primary" :to="loginTarget">{{ copy.signInContinue }}</RouterLink>
        </UserState>
        <section v-if="!isAuthenticated" class="user-chat__guest-actions" :aria-label="copy.continueLearning">
          <div>
            <p class="user-page__eyebrow">{{ copy.currentScope }}</p>
            <h2>{{ chapterScopeLabel }}</h2>
          </div>
          <div class="user-page__actions">
            <RouterLink class="user-action" data-testid="chat-course" :to="chapterTarget">{{ copy.openCurrentCourse }}</RouterLink>
            <RouterLink class="user-action" data-testid="chat-knowledge" :to="knowledgeTarget">{{ copy.browseMaterials }}</RouterLink>
          </div>
        </section>
        <UserState
          v-else-if="readinessError"
          :mode="readinessError.kind === 'permission' ? 'permission' : 'error'"
          :title="readinessError.title"
          :message="readinessError.message"
          :retry-label="readinessError.retryable ? copy.reload : undefined"
          @retry="refreshReadiness()"
        />
      </section>

      <section v-if="isAuthenticated" class="user-chat" :aria-label="copy.content">
        <UserState v-if="sessionLoading" mode="loading" :title="copy.loadingSessions" :message="copy.loadingSessionsDetail" />
        <UserState
          v-else-if="sessionError"
          :mode="sessionError.kind === 'permission' ? 'permission' : 'error'"
          :title="sessionError.title"
          :message="sessionError.message"
          :retry-label="sessionError.retryable ? copy.reload : undefined"
          @retry="activeSessionId && openSession(activeSessionId)"
        />
        <UserState v-else-if="!messages.length" mode="empty" :title="copy.emptyTitle" :message="copy.emptyDetail" />
        <div v-else class="user-chat__messages" aria-live="polite" aria-relevant="additions text">
          <article v-for="message in messages" :key="message.id" class="user-chat__message" :data-role="message.role" :data-state="message.state">
            <p>{{ message.content || (message.state === 'streaming' ? copy.generatingAnswer : '') }}</p>
            <small v-if="message.role === 'assistant' && message.state === 'streaming'">{{ copy.generating }}</small>
            <small v-else-if="message.role === 'assistant' && message.state === 'stopped'">{{ copy.stopped }}</small>
            <small v-else-if="message.role === 'assistant' && message.state === 'error'">{{ copy.incomplete }}</small>
            <time v-if="message.createdAt" class="user-chat__message-time" :datetime="message.createdAt">{{ copy.sentAt }} {{ formatSessionTime(message.createdAt) }}</time>
            <div v-if="message.sources.length" class="user-source-list" :aria-label="copy.sources">
              <details v-for="source in message.sources" :key="`${source.id}-${source.evidenceHash}`">
                <summary>{{ source.title }}<span v-if="source.pageLabel"> · {{ source.pageLabel }}</span></summary>
                <p>{{ source.content }}</p>
                <p>{{ source.source }}</p>
              </details>
            </div>
          </article>
        </div>

        <form class="user-form user-chat__form" @submit.prevent="submitPrompt">
          <label>
            {{ copy.scope }}
            <RuntimeSelect v-model="chapterId" :options="chapterScopeOptions" :ariaLabel="copy.scope" :disabled="isBusy" test-id="chat-chapter-scope" />
          </label>
          <label>
            {{ copy.question }}
            <textarea v-model="prompt" data-testid="chat-prompt" name="prompt" maxlength="4000" :disabled="isBusy" :placeholder="copy.questionPlaceholder" required />
          </label>
          <div class="user-page__actions">
            <button class="user-action user-action--primary" data-testid="chat-send" type="submit" :disabled="!canSubmit">{{ sendPhase === 'checking' ? copy.checking : copy.send }}</button>
            <button v-if="isStreaming" class="user-action" data-testid="chat-stop" type="button" @click="stopGeneration">{{ copy.stop }}</button>
            <button v-if="chatError?.retryable && retryAttempt" class="user-action" data-testid="chat-retry" type="button" :disabled="isBusy" @click="retryLastAttempt">{{ copy.retry }}</button>
          </div>
        </form>

        <UserState
          v-if="chatError"
          :mode="chatError.kind === 'permission' ? 'permission' : 'error'"
          :title="chatError.title"
          :message="chatError.message"
          :retry-label="chatError.retryable && retryAttempt ? copy.retry : undefined"
          @retry="retryLastAttempt"
        />
      </section>
    </section>

    <template #rail>
      <div class="user-rail-list">
        <div class="user-chat__rail-heading"><strong>{{ isAuthenticated ? copy.historySessions : copy.accountSessions }}</strong><button v-if="isAuthenticated" class="user-chat__quiet-action" type="button" :disabled="sessionsLoading || isBusy" @click="loadSessions">{{ copy.refresh }}</button></div>
        <p v-if="!isAuthenticated" class="user-list__meta user-list__meta--left">{{ copy.guestSessions }}</p>
        <p v-if="activeSession">{{ copy.currentSession }}: {{ activeSession.title }}</p>
        <UserState v-if="sessionsLoading" mode="loading" :title="copy.loadingSessions" message="" />
        <UserState
          v-else-if="sessionsError"
          :mode="sessionsError.kind === 'permission' ? 'permission' : 'error'"
          :title="sessionsError.title"
          :message="sessionsError.message"
          :retry-label="sessionsError.retryable ? copy.reload : undefined"
          @retry="loadSessions"
        />
        <p v-else-if="isAuthenticated && !sessions.length">{{ copy.noSessions }}</p>
        <div v-else class="user-chat__sessions">
          <article v-for="session in sessions" :key="session.id" class="user-chat__session" :data-active="session.id === activeSessionId">
            <button :ref="(element) => setSessionButton(session.id, element as Element | null)" class="user-chat__session-open" data-testid="chat-session" type="button" :disabled="isBusy || deletingSessionId === session.id" @click="openSession(session.id)">
              <strong>{{ session.title }}</strong>
              <span>{{ messageCountLabel(session.messageCount) }} · {{ formatSessionTime(session.updatedAt) }}</span>
            </button>
            <div class="user-chat__session-actions">
              <button v-if="deleteConfirmationId !== session.id" class="user-chat__quiet-action" data-testid="chat-request-delete" type="button" :disabled="isBusy || deletingSessionId === session.id" @click="requestDelete(session.id)">{{ copy.delete }}</button>
              <template v-else>
                <button class="user-chat__quiet-action user-chat__quiet-action--danger" data-testid="chat-confirm-delete" type="button" :disabled="deletingSessionId === session.id" @click="deleteSession(session.id)">{{ deletingSessionId === session.id ? copy.deleting : copy.confirm }}</button>
                <button class="user-chat__quiet-action" type="button" :disabled="deletingSessionId === session.id" @click="cancelDelete">{{ copy.cancel }}</button>
              </template>
            </div>
          </article>
        </div>
        <strong>{{ copy.currentScope }}</strong>
        <p>{{ chapterScopeLabel }}</p>
      </div>
    </template>
  </UserFrame>
</template>

<style scoped>
.user-chat__readiness { display: grid; gap: .75rem; }
.user-chat__readiness h2 { margin: 0; font-size: 1rem; }
.user-chat__readiness-grid { display: flex; flex-wrap: wrap; gap: .45rem; }
.user-chat__readiness-grid span { padding: .3rem .45rem; border: 1px solid var(--line); border-radius: var(--radius-sm); color: var(--text-muted); font-family: var(--font-mono); font-size: .74rem; }
.user-chat__readiness-grid span[data-state="success"] { border-color: color-mix(in srgb, var(--accent) 58%, var(--line)); color: var(--accent-strong); }
.user-chat__readiness-grid span[data-state="warning"] { border-color: color-mix(in srgb, var(--warning) 58%, var(--line)); color: var(--warning); }
.user-chat__form { border-top: 1px solid var(--line); padding-top: 1rem; }
.user-chat__guest-actions { display: flex; align-items: end; justify-content: space-between; gap: 1rem; padding: 1rem 0; border-bottom: 1px solid var(--line); }
.user-chat__guest-actions h2 { margin: 0; font-size: 1.1rem; }
.user-chat__message[data-state="error"] { border-color: color-mix(in srgb, var(--danger) 48%, var(--line)); }
.user-chat__message[data-state="stopped"] { border-style: dashed; }
.user-chat__message-time { color: var(--text-muted); font-size: .72rem; }
.user-chat__rail-heading { display: flex; align-items: center; justify-content: space-between; gap: .5rem; }
.user-chat__quiet-action { min-height: 2rem; padding: .25rem .45rem; border: 0; background: transparent; color: var(--text-muted); cursor: pointer; font-size: .75rem; }
.user-chat__quiet-action:hover { color: var(--text); text-decoration: underline; }
.user-chat__quiet-action:disabled { cursor: not-allowed; opacity: .55; text-decoration: none; }
.user-chat__quiet-action--danger { color: var(--danger); }
.user-chat__sessions { display: grid; border-top: 1px solid var(--line); }
.user-chat__session { display: grid; gap: .35rem; padding: .55rem 0; border-bottom: 1px solid var(--line); }
.user-chat__session[data-active="true"] { border-left: 2px solid var(--accent); padding-left: .45rem; }
.user-chat__session-open { display: grid; gap: .2rem; padding: 0; border: 0; background: transparent; color: var(--text); cursor: pointer; text-align: left; }
.user-chat__session-open:disabled { cursor: not-allowed; opacity: .55; }
.user-chat__session-open strong { font-size: .82rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-chat__session-open span { color: var(--text-muted); font-size: .72rem; }
.user-chat__session-actions { display: flex; gap: .2rem; }
@media (max-width: 760px) { .user-chat__readiness-grid { display: grid; grid-template-columns: 1fr; } .user-chat__guest-actions { display: grid; align-items: start; } }
</style>
