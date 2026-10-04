<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Check, Copy, MoreHorizontal, Pencil, Pin, PinOff, RefreshCcw, Search, PanelLeftClose, PanelLeftOpen, X } from "@lucide/vue";
import BrandStage from "../../shared/components/BrandStage.vue";
import NoticeDialog from "../../shared/components/NoticeDialog.vue";
import ConfirmDialog from "../../shared/components/ConfirmDialog.vue";
import AnimationDialog from "../components/AnimationDialog.vue";
import LiquidMetalButton from "../../admin/components/LiquidMetalButton.vue";
import ChatComposer from "../components/ChatComposer.vue";
import ChatReasoning from "../components/ChatReasoning.vue";
import ChatAnswer from "../components/ChatAnswer.vue";
import homeIcon from "../../assets/classroom/home.svg";
import { attachmentPayload, type ComposerAttachment } from "../chat-attachments";
import { useI18n } from "../../shared/i18n/locale";
import type { MessageKey } from "../../shared/i18n/messages";
import type { DsvpSimulationResponse } from "../../shared/types/animation";
import type { Chapter, ChatAttachment, ChatReasoningEffort, ChatResponse, ChatSessionSummary, ChatSource, ChatTurn } from "../../shared/types";
import { auth } from "../../app/providers/runtime";
import { userApi } from "../runtime";
import { ApiClientError } from "../../shared/api/client";
import { chatErrorKey, deltaOf, doneOf, errorOf, pendingOf, sourcesOf, type ChatWireEvent } from "../chat-stream";

/**
 * Asking the course a question.
 *
 * The backend half of this never went away - `/api/v1/chat` still answers from the reviewed textbook
 * and still names the pages it used - but the page was dropped during the stage refactor, so the only
 * way to reach the model was through a prepared lesson. This puts the direct question back: type a
 * question, get an answer streamed from the same evidence the classroom quotes.
 *
 * Two things are deliberately absent. The answer is never invented: when retrieval finds nothing the
 * server refuses with `CHAT_EVIDENCE_UNAVAILABLE`, and that refusal is shown as written rather than
 * papered over with a plausible paragraph. And nothing here is small print - every line a learner
 * reads sits at the site's body size.
 */
type MessageState = "complete" | "streaming" | "stopped";

interface ConversationMessage {
  id: number;
  role: "user" | "assistant";
  content: string;
  sources: ChatSource[];
  state: MessageState;
  /** The question this reply answers - what an animation for it is built from. */
  question?: string;
  attachments?: ChatAttachment[];
  storedId?: number;
  persisted?: boolean;
  questionId?: number;
  request?: QuestionRequest;
  reasoning?: string;
  retrieved?: boolean;
  seconds?: number;
  reasoningSeconds?: number;
  createdAt?: string;
}

interface QuestionRequest {
  question: string;
  chapterId: string;
  uploads: ChatAttachment[];
  thinking: boolean;
  effort: ChatReasoningEffort;
  localDemo?: boolean;
}

const route = useRoute();
const router = useRouter();
const { t, locale } = useI18n();

const chapters = ref<Chapter[]>([]);
const chapterId = ref("");
const prompt = ref("");
const thinkingEnabled = ref(false);
const reasoningEffort = ref<ChatReasoningEffort>("high");
const attachments = ref<ComposerAttachment[]>([]);
const attachmentsReading = ref(false);
const composerContext = ref(0);
const messages = ref<ConversationMessage[]>([]);
const sessions = ref<ChatSessionSummary[]>([]);
const activeSessionId = ref<string | null>(null);
const phase = ref<"idle" | "streaming">("idle");
const preparingRetry = ref(false);
const alert = ref<{ title: string; message: string } | null>(null);
const pendingDelete = ref<ChatSessionSummary | null>(null);
const sessionsFailed = ref(false);
const sessionSearch = ref("");
const sidebarCollapsed = ref(false);
const sessionMenuId = ref<string | null>(null);
const editingSessionId = ref<string | null>(null);
const editingTitle = ref("");
const sessionUpdatingId = ref<string | null>(null);
const sessionSearchRef = ref<HTMLInputElement | null>(null);
const threadRef = ref<HTMLElement | null>(null);
/** One animation per reply, keyed by the reply it belongs to. */
const animations = ref<Record<number, DsvpSimulationResponse>>({});
const animationBusy = ref(false);
/** The reply whose demo is being built, so only its button says so. */
const animationPendingFor = ref<number | null>(null);
/** The demo on screen; null means the dialog is closed. */
const openAnimationFor = ref<number | null>(null);
/** On a phone the conversation list is a sheet; on a desktop it is the sidebar and this is ignored. */
const sessionsOpen = ref(false);
const copiedId = ref<number | null>(null);
let copyTimer: ReturnType<typeof setTimeout> | undefined;

const ALL_CHAPTERS = "";
const MAX_PROMPT = 4000;
/**
 * How long a reply may be and still be worth reading as an answer to an offer.
 *
 * This is a cost gate, not a verdict: whether the words agree is a question of meaning and the server
 * asks the model. Nothing a learner can type is compared against a list here, because "包的",
 * "o而k之" and next month's coinage are all the same yes, and no closed list holds them.
 */
const OFFER_REPLY_MAX = 40;

let sequence = 0;
let controller: AbortController | null = null;

const streaming = computed(() => phase.value === "streaming" || preparingRetry.value);
const tooLong = computed(() => prompt.value.trim().length > MAX_PROMPT);
const signedIn = computed(() => Boolean(auth.state.user));
type SessionGroupKey = "today" | "yesterday" | "lastSevenDays" | "lastThirtyDays" | "earlier";
const sessionGroupOrder: SessionGroupKey[] = ["today", "yesterday", "lastSevenDays", "lastThirtyDays", "earlier"];
const sessionGroupLabel: Record<SessionGroupKey, MessageKey> = {
  today: "chat.today",
  yesterday: "chat.yesterday",
  lastSevenDays: "chat.lastSevenDays",
  lastThirtyDays: "chat.lastThirtyDays",
  earlier: "chat.earlier",
};
const filteredSessions = computed(() => {
  const query = sessionSearch.value.trim().toLocaleLowerCase(locale.value);
  if (!query) return sessions.value;
  return sessions.value.filter((session) => session.title.toLocaleLowerCase(locale.value).includes(query));
});
const pinnedSessions = computed(() => filteredSessions.value.filter((session) => session.pinned));
const groupedSessions = computed(() => {
  const groups = Object.fromEntries(sessionGroupOrder.map((key) => [key, [] as ChatSessionSummary[]])) as Record<SessionGroupKey, ChatSessionSummary[]>;
  for (const session of filteredSessions.value) {
    if (session.pinned) continue;
    const updated = new Date(session.updatedAt);
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const startOfUpdated = new Date(updated.getFullYear(), updated.getMonth(), updated.getDate()).getTime();
    const days = Number.isNaN(updated.getTime()) ? 31 : Math.max(0, Math.floor((startOfToday - startOfUpdated) / 86_400_000));
    const key: SessionGroupKey = days === 0 ? "today" : days === 1 ? "yesterday" : days <= 7 ? "lastSevenDays" : days <= 30 ? "lastThirtyDays" : "earlier";
    groups[key].push(session);
  }
  return groups;
});
const hasVisibleSessions = computed(() => pinnedSessions.value.length > 0 || sessionGroupOrder.some((key) => groupedSessions.value[key].length > 0));
const messageTimeFormatter = computed(() => new Intl.DateTimeFormat(locale.value, {
  year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
}));

function formatMessageTime(value?: string): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  if (locale.value === "zh-CN") {
    const hours = String(date.getHours()).padStart(2, "0");
    const minutes = String(date.getMinutes()).padStart(2, "0");
    return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${hours}:${minutes}`;
  }
  return messageTimeFormatter.value.format(date);
}

function push(
  role: "user" | "assistant",
  content: string,
  state: MessageState = "complete",
  question?: string,
): number {
  const id = ++sequence;
  messages.value = [...messages.value, { id, role, content, sources: [], state, question,
    createdAt: role === "user" ? new Date().toISOString() : undefined }];
  return id;
}

function update(id: number, patch: Partial<ConversationMessage>) {
  messages.value = messages.value.map((item) => (item.id === id ? { ...item, ...patch } : item));
}

async function scrollToLatest() {
  await nextTick();
  const thread = threadRef.value;
  if (thread) thread.scrollTop = thread.scrollHeight;
}

function isTimeout(cause: unknown): boolean {
  return cause instanceof ApiClientError && cause.code === "NETWORK_TIMEOUT";
}

function failureMessage(cause: unknown): string {
  if (isTimeout(cause)) return t("chat.error.timeout");
  return cause instanceof Error && cause.message ? cause.message : t("common.failed");
}

function raise(title: string, message: string) {
  alert.value = { title, message };
}

function isLocalDemo(question: string): boolean {
  return import.meta.env.DEV && ["localhost", "127.0.0.1", "[::1]", "::1"].includes(location.hostname) && question === "测试";
}

async function send() {
  const question = prompt.value.trim();
  // animationBusy covers the moment the server is reading a reply: sending into it would race it.
  if (!question || streaming.value || animationBusy.value || attachmentsReading.value || tooLong.value) return;
  const uploads = attachmentPayload(attachments.value);
  const thinking = thinkingEnabled.value;
  const effort = reasoningEffort.value;
  const request = { question, chapterId: chapterId.value, uploads, thinking, effort, localDemo: isLocalDemo(question) };

  // An offer was just made, so this reply may be taking it up. Whether it does is read by the model
  // on the server; a reply that is not an agreement comes back declined and is answered normally.
  const offer = !request.localDemo && !uploads.length && lastReplyOffersAnimation() && question.length <= OFFER_REPLY_MAX ? lastOfferingReply() : null;

  // Taken before the question joins the thread, or the question would be sent twice.
  const history = activeSessionId.value ? [] : messages.value
    .filter((item) => item.state === "complete" && item.content && !item.request?.localDemo)
    .slice(-12)
    .map((item) => ({ role: item.role, content: item.content.slice(0, 4000), attachments: item.attachments }));

  prompt.value = "";
  attachments.value = [];
  const questionId = push("user", question);
  update(questionId, { attachments: uploads, request });
  await scrollToLatest();

  if (offer && (await runAnimation(animationPrompt(offer), offer.id, question))) return;
  await ask(request, history, questionId);
}

async function ask(request: QuestionRequest, history: ChatTurn[], questionId: number, retryMessageId?: number): Promise<boolean> {
  const { question, uploads, thinking, effort } = request;
  const replyId = push("assistant", "", "streaming", question);
  update(replyId, { questionId, request, reasoning: "" });
  phase.value = "streaming";
  controller = new AbortController();
  const signal = controller.signal;
  const started = Date.now();
  const clock = setInterval(() => update(replyId, { seconds: (Date.now() - started) / 1000 }), 250);
  let reasoningStarted: number | undefined;
  let reasoningEnded: number | undefined;
  let completed = false;
  await scrollToLatest();

  try {
    let events: AsyncIterable<ChatWireEvent>;
    if (import.meta.env.DEV && request.localDemo && isLocalDemo(question)) {
      const { streamLocalDemo } = await import("../chat-local-demo");
      events = streamLocalDemo(signal);
    } else {
      const response = await userApi.streamChat(
        { prompt: question, chapterId: request.chapterId || undefined, sessionId: activeSessionId.value ?? undefined, history,
          thinkingEnabled: thinking, reasoningEffort: thinking ? effort : undefined, attachments: uploads, retryMessageId },
        signal,
      );
      events = response.events;
    }
    let answer = "";
    let reasoning = "";
    let sources: ChatSource[] = [];
    for await (const event of events) {
      // A stop has to land even when the next event is slow to arrive.
      if (signal.aborted) break;
      if (event.event === "pending") {
        const pending = pendingOf(event);
        if (pending) {
          activeSessionId.value = pending.sessionId;
          update(questionId, { storedId: pending.messageId, persisted: true });
          void loadSessions();
        }
      } else if (event.event === "sources") {
        sources = sourcesOf(event);
        update(replyId, { sources, retrieved: true });
      } else if (event.event === "reasoning") {
        const text = deltaOf(event);
        if (text) reasoningStarted ??= Date.now();
        reasoning += text;
        update(replyId, { reasoning });
        await scrollToLatest();
      }
      else if (event.event === "delta") {
        if (reasoningStarted !== undefined) reasoningEnded ??= Date.now();
        answer += deltaOf(event);
        update(replyId, { content: answer, sources, reasoningSeconds: reasoningStarted === undefined ? undefined : ((reasoningEnded ?? Date.now()) - reasoningStarted) / 1000 });
        await scrollToLatest();
      } else if (event.event === "done") {
        const done = doneOf(event) as ChatResponse | null;
        answer = done?.answer ?? answer;
        sources = done?.sources?.length ? done.sources : sources;
        reasoning = done?.reasoning ?? reasoning;
        update(replyId, { content: answer, sources, reasoning, state: "complete", persisted: done?.persisted,
          createdAt: new Date().toISOString(),
          retrieved: true, seconds: (Date.now() - started) / 1000,
          reasoningSeconds: reasoningStarted === undefined ? undefined : ((reasoningEnded ?? Date.now()) - reasoningStarted) / 1000 });
        completed = true;
        if (done?.sessionId) {
          activeSessionId.value = done.sessionId;
          await syncStoredIds();
          await loadSessions();
        }
        break;
      } else if (event.event === "error") {
        const failure = errorOf(event);
        messages.value = messages.value.filter((item) => item.id !== replyId);
        raise(t("common.failed"), failure ? t(chatErrorKey(failure.code)) : t("chat.error.failed"));
        break;
      }
    }
    const reply = messages.value.find((item) => item.id === replyId);
    // A stream that was cut short reads as stopped even when it already had text: finishing it here
    // would present a half-answer as the whole one. One that closed with nothing at all - no done,
    // no error, the server just went quiet - says so, instead of leaving the learner staring at the
    // searching note until they give up.
    if (reply?.state === "streaming") {
      update(replyId, { state: "stopped", seconds: (Date.now() - started) / 1000, createdAt: new Date().toISOString() });
      if (!signal.aborted && !reply.content) {
        raise(t("common.failed"), t("chat.error.timeout"));
      }
    }
  } catch (cause) {
    const stopped = signal.aborted;
    if (stopped) {
      update(replyId, { state: "stopped", seconds: (Date.now() - started) / 1000, createdAt: new Date().toISOString() });
    } else {
      messages.value = messages.value.filter((item) => item.id !== replyId);
      raise(t("common.failed"), failureMessage(cause));
    }
  } finally {
    clearInterval(clock);
    phase.value = "idle";
    controller = null;
    await scrollToLatest();
  }
  return completed;
}

async function syncStoredIds() {
  if (!activeSessionId.value) return;
  try {
    const detail = await userApi.getChatSession(activeSessionId.value);
    let cursor = detail.messages.length - 1;
    // Align from the end so repeated identical questions receive their own database IDs.
    for (let index = messages.value.length - 1; index >= 0 && cursor >= 0; index--) {
      const local = messages.value[index];
      const stored = detail.messages[cursor];
      if (local.role === stored.role && local.content === stored.content) {
        update(local.id, { storedId: stored.id, createdAt: local.createdAt || stored.createdAt });
        cursor--;
      }
    }
  } catch {
    // Saving already succeeded; a failed history refresh must not turn the answer into an error.
  }
}

async function retry(message: ConversationMessage) {
  if (streaming.value || animationBusy.value) return;
  const index = messages.value.findIndex((item) => item.id === message.questionId);
  if (index < 0) return;
  preparingRetry.value = true;
  try {
    if (message.persisted && !messages.value[index].storedId) await syncStoredIds();
    const original = messages.value[index];
    if (message.persisted && !original.storedId) {
      raise(t("common.failed"), t("chat.error.sessionGone"));
      return;
    }
    const request = original.request ?? { question: original.content, chapterId: chapterId.value,
      uploads: original.attachments ?? [], thinking: false, effort: "high" as ChatReasoningEffort };
    const history = messages.value.slice(0, index).filter((item) => item.state === "complete" && item.content && !item.request?.localDemo)
      .slice(-12).map((item) => ({ role: item.role, content: item.content.slice(0, 4000), attachments: item.attachments }));
    const previous = messages.value;
    const previousAnimations = animations.value;
    messages.value = previous.slice(0, index + 1);
    animations.value = Object.fromEntries(Object.entries(previousAnimations).filter(([id]) => messages.value.some((item) => item.id === Number(id))));
    openAnimationFor.value = null;
    if (!await ask(request, activeSessionId.value ? [] : history, original.id, original.storedId)) {
      messages.value = previous;
      animations.value = previousAnimations;
    }
  } finally {
    preparingRetry.value = false;
  }
}

async function copyMessage(message: ConversationMessage) {
  try {
    await navigator.clipboard.writeText(message.content);
    copiedId.value = message.id;
    if (copyTimer !== undefined) clearTimeout(copyTimer);
    copyTimer = setTimeout(() => { copiedId.value = null; }, 1800);
  } catch {
    raise(t("common.failed"), t("chat.copyFailed"));
  }
}

function stop() {
  controller?.abort();
}

/** The reply that ended with an offer to show the animation. */
function lastOfferingReply(): ConversationMessage | null {
  for (let index = messages.value.length - 1; index >= 0; index--) {
    const message = messages.value[index];
    if (message.role !== "assistant") continue;
    return /动画|演示|animation/i.test(message.content) ? message : null;
  }
  return null;
}

function lastReplyOffersAnimation(): boolean {
  return lastOfferingReply() !== null;
}

/**
 * What the interpret endpoint should read for this reply's demo.
 *
 * The bare question can be a concept comparison ("栈和队列有什么区别？") that rightly refuses a
 * frame-by-frame demo, while the offer the model just made names the concrete process ("栈的进栈出栈
 * 过程"). Question plus offer reads as one request, and the engine decides.
 */
function animationPrompt(message: ConversationMessage): string {
  const question = message.question ?? "";
  const sentences = message.content.split(/(?<=[。！？!?])/).map((item) => item.trim()).filter(Boolean);
  const offer = [...sentences].reverse().find((item) => /动画|演示/.test(item));
  return offer ? `${question} ${offer}`.trim() : question || message.content;
}

/**
 * Builds the animation this answer offers, then opens it over the conversation.
 *
 * The sentence goes to the same interpret endpoint the animation lab uses, so the model only picks a
 * capability and the local engine computes the frames - the demo can be the wrong one but never an
 * invented one.
 *
 * With `reply` the server also reads the learner's own words: it answers with the demo when they were
 * taking the offer up, and with ANIMATION_DECLINED when they were not. False means "this was not an
 * agreement, answer it as the question it is"; true means the turn is spent, one way or the other.
 */
async function runAnimation(question: string, replyId: number, reply?: string): Promise<boolean> {
  // No chapter selected means the whole textbook, and the request says exactly that. Falling back to the
  // first chapter used to narrow it silently, and most demos then came back as "not implemented".
  const chapter = chapterId.value || undefined;
  if (!question) {
    raise(t("chat.animationFailedTitle"), t("chat.animationUnavailable"));
    return true;
  }
  animationBusy.value = true;
  animationPendingFor.value = replyId;
  try {
    const original = messages.value.find((item) => item.id === replyId)?.request;
    if (import.meta.env.DEV && original?.localDemo && isLocalDemo(original.question)) {
      const { localDemoAnimation } = await import("../chat-local-demo");
      animations.value = { ...animations.value, [replyId]: localDemoAnimation() };
      openAnimationFor.value = replyId;
      return true;
    }
    // Without a reply the learner pressed the button, so the yes is already given and the interpreter
    // must pick a capability rather than re-judge whether the topic deserves a demo.
    const request = await userApi.interpretAnimation(reply
      ? { chapterId: chapter, prompt: question, reply }
      : { chapterId: chapter, prompt: question, confirmed: true });
    const data = await userApi.simulateAnimation(request);
    animations.value = { ...animations.value, [replyId]: data };
    openAnimationFor.value = replyId;
    return true;
  } catch (cause) {
    // Not an agreement is the one failure that is not a failure: the words were a question.
    if (isDeclined(cause)) return false;
    raise(t("chat.animationFailedTitle"), animationFailure(cause));
    return true;
  } finally {
    animationBusy.value = false;
    animationPendingFor.value = null;
  }
}

/**
 * The answer's own button. A demo already built is shown again without asking the server for it: a second
 * request would spend quota to re-decide something already decided, and it could even come back different.
 */
function showAnimation(message: ConversationMessage) {
  if (animationOf(message.id)) {
    openAnimationFor.value = message.id;
    return;
  }
  void runAnimation(animationPrompt(message), message.id);
}

/** The server read the reply and found no agreement in it. */
function isDeclined(cause: unknown): boolean {
  return cause instanceof ApiClientError && cause.code === "ANIMATION_DECLINED";
}

/**
 * A demo that could not be built is the engine's business, not the learner's. Its explanations name
 * capabilities and operations - "tree 仅支持 highlight/traverse/visit", "linked_list 仅支持
 * append/delete/find/insert" - which is machinery, and reads as a broken product rather than a missing
 * one. Every refusal becomes the same short line; only waiting too long says so in its own words.
 */
function animationFailure(cause: unknown): string {
  return isTimeout(cause) ? t("chat.error.timeout") : t("chat.animationUnavailable");
}

function animationOf(id: number): DsvpSimulationResponse | null {
  return animations.value[id] ?? null;
}

function definitionOf(id: number) {
  return animationOf(id)?.animationData ?? null;
}

function newConversation() {
  if (streaming.value) return;
  messages.value = [];
  animations.value = {};
  openAnimationFor.value = null;
  activeSessionId.value = null;
  prompt.value = "";
  attachments.value = [];
  composerContext.value++;
  sessionsOpen.value = false;
  sessionMenuId.value = null;
  editingSessionId.value = null;
}

function toggleSessionMenu(sessionId: string) {
  if (editingSessionId.value === sessionId) return;
  sessionMenuId.value = sessionMenuId.value === sessionId ? null : sessionId;
}

function beginRename(session: ChatSessionSummary) {
  editingSessionId.value = session.id;
  editingTitle.value = session.title;
  sessionMenuId.value = null;
  void nextTick(() => document.querySelector<HTMLInputElement>(`[data-session-edit="${CSS.escape(session.id)}"]`)?.focus());
}

function cancelRename() {
  editingSessionId.value = null;
  editingTitle.value = "";
}

async function saveRename(session: ChatSessionSummary) {
  const title = editingTitle.value.trim();
  if (!title || title === session.title || sessionUpdatingId.value === session.id) {
    cancelRename();
    return;
  }
  sessionUpdatingId.value = session.id;
  try {
    const updated = await userApi.updateChatSession(session.id, { title });
    sessions.value = sessions.value.map((item) => item.id === session.id ? { ...item, title: updated.title, updatedAt: updated.updatedAt, pinned: updated.pinned } : item);
    cancelRename();
  } catch (cause) {
    raise(t("common.failed"), failureMessage(cause));
  } finally {
    sessionUpdatingId.value = null;
  }
}

async function togglePinned(session: ChatSessionSummary) {
  if (sessionUpdatingId.value === session.id) return;
  sessionMenuId.value = null;
  sessionUpdatingId.value = session.id;
  try {
    const updated = await userApi.updateChatSession(session.id, { pinned: !session.pinned });
    sessions.value = sessions.value.map((item) => item.id === session.id ? { ...item, pinned: updated.pinned, updatedAt: updated.updatedAt } : item);
  } catch (cause) {
    raise(t("common.failed"), failureMessage(cause));
  } finally {
    sessionUpdatingId.value = null;
  }
}

function openSessionSearch() {
  if (sidebarCollapsed.value) sidebarCollapsed.value = false;
  sessionsOpen.value = true;
  void nextTick(() => sessionSearchRef.value?.focus());
}

function handleKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    openSessionSearch();
  }
  if (event.key === "Escape") {
    sessionMenuId.value = null;
    if (editingSessionId.value) cancelRename();
  }
}

async function loadSessions() {
  if (!signedIn.value) return;
  try {
    sessions.value = await userApi.listChatSessions();
    sessionsFailed.value = false;
  } catch {
    // A past conversation that cannot be listed is not a reason to block the question box.
    sessionsFailed.value = true;
  }
}

async function openSession(session: ChatSessionSummary) {
  if (streaming.value) return;
  try {
    const detail = await userApi.getChatSession(session.id);
    activeSessionId.value = detail.id;
    prompt.value = "";
    attachments.value = [];
    composerContext.value++;
    animations.value = {};
    openAnimationFor.value = null;
    sessionsOpen.value = false;
    let lastQuestion: ConversationMessage | undefined;
    messages.value = detail.messages.map((item) => {
      const message: ConversationMessage = {
      id: ++sequence,
      storedId: item.id,
      persisted: true,
      role: item.role,
      content: item.content,
      sources: item.sources ?? [],
      state: "complete" as MessageState,
      attachments: item.attachments ?? [],
      reasoning: item.reasoning ?? "",
      retrieved: true,
      createdAt: item.createdAt,
      };
      if (item.role === "user") {
        message.request = { question: item.content,
          chapterId: item.thinkingEnabled == null ? detail.chapterId ?? "" : item.chapterId ?? "",
          uploads: item.attachments ?? [], thinking: item.thinkingEnabled ?? false, effort: item.reasoningEffort ?? "high" };
        lastQuestion = message;
      } else {
        message.question = lastQuestion?.content;
        message.questionId = lastQuestion?.id;
        message.request = lastQuestion?.request;
      }
      return message;
    });
    await scrollToLatest();
  } catch (cause) {
    raise(t("common.failed"), failureMessage(cause));
  }
}

async function removeSession() {
  const session = pendingDelete.value;
  pendingDelete.value = null;
  if (!session) return;
  try {
    await userApi.deleteChatSession(session.id);
    if (activeSessionId.value === session.id) newConversation();
    await loadSessions();
  } catch (cause) {
    raise(t("common.failed"), failureMessage(cause));
  }
}

onMounted(async () => {
  window.addEventListener("keydown", handleKeydown);
  try {
    chapters.value = await userApi.listChapters();
    const fromQuery = typeof route.query.chapterId === "string" ? route.query.chapterId : "";
    chapterId.value = chapters.value.some((item) => item.id === fromQuery) ? fromQuery : ALL_CHAPTERS;
  } catch {
    // The scope picker is optional: without the chapter list every question just spans the whole book.
    chapters.value = [];
  }
  await loadSessions();
});

onBeforeUnmount(() => {
  window.removeEventListener("keydown", handleKeydown);
  controller?.abort();
  if (copyTimer !== undefined) clearTimeout(copyTimer);
});
</script>

<template>
  <BrandStage wide fixed>
    <div class="chat">
      <header class="chat__head">
        <h1 class="chat__title">{{ t("chat.title") }}</h1>
        <div class="chat__head-actions">
          <!-- Phones reach the past conversations through this; on a desktop the sidebar is always there. -->
          <button class="chat__link chat__link--history" type="button" @click="sessionsOpen = true">
            {{ t("chat.sessions") }}
          </button>
          <button class="chat__home" type="button" :title="t('common.backHome')" :aria-label="t('common.backHome')" @click="router.push('/')">
            <img class="chat__home-icon" :src="homeIcon" alt="" aria-hidden="true" />
          </button>
        </div>
      </header>

      <div class="chat__grid" :class="{ 'chat__grid--sidebar-collapsed': sidebarCollapsed }">
        <div v-if="sessionsOpen" class="sessions-scrim" @click="sessionsOpen = false" />

        <section
          class="panel panel--sessions"
          :class="{ 'panel--sessions-open': sessionsOpen }"
          :aria-label="t('chat.sessions')"
        >
          <button
            class="sidebar-collapsed-toggle"
            type="button"
            :title="t('chat.expandSidebar')"
            :aria-label="t('chat.expandSidebar')"
            @click="sidebarCollapsed = false"
          ><PanelLeftOpen :size="19" aria-hidden="true" /></button>
          <header class="sessions__head">
            <div class="sessions__heading">
              <span class="sessions__mark" aria-hidden="true">S</span>
              <h2 class="sessions__title">{{ t("chat.sessions") }}</h2>
            </div>
            <div class="sessions__head-actions">
              <button class="sidebar-icon" type="button" :title="t('chat.collapseSidebar')" :aria-label="t('chat.collapseSidebar')" @click="sidebarCollapsed = true"><PanelLeftClose :size="18" aria-hidden="true" /></button>
              <button class="sessions__close" type="button" :aria-label="t('common.close')" @click="sessionsOpen = false"><X :size="18" aria-hidden="true" /></button>
            </div>
          </header>

          <div class="sessions__toolbar">
            <LiquidMetalButton
              class="sessions__new"
              variant="quiet"
              view-mode="text"
              type="button"
              :disabled="streaming"
              @click="newConversation"
            >
              <span class="sessions__new-icon" aria-hidden="true">+</span>
              <span class="sessions__new-label">{{ t("chat.newChat") }}</span>
            </LiquidMetalButton>
            <button class="sidebar-icon sessions__collapse" type="button" :title="t('chat.collapseSidebar')" :aria-label="t('chat.collapseSidebar')" @click="sidebarCollapsed = true"><PanelLeftClose :size="18" aria-hidden="true" /></button>
            <button class="sidebar-icon" type="button" :title="t('chat.searchShortcut')" :aria-label="t('chat.searchSessions')" @click="openSessionSearch"><Search :size="18" aria-hidden="true" /></button>
          </div>

          <div class="sessions__search">
            <Search :size="16" aria-hidden="true" />
            <input ref="sessionSearchRef" v-model="sessionSearch" type="search" :placeholder="t('chat.searchSessions')" :aria-label="t('chat.searchSessions')" />
            <button v-if="sessionSearch" class="sessions__search-clear" type="button" :aria-label="t('common.clear')" @click="sessionSearch = ''"><X :size="15" aria-hidden="true" /></button>
          </div>

          <p v-if="!signedIn" class="panel__note">{{ t("chat.signInToKeep") }}</p>
          <p v-else-if="sessionsFailed" class="panel__note">{{ t("chat.sessionsFailed") }}</p>
          <p v-else-if="!hasVisibleSessions" class="sessions__empty">{{ sessionSearch ? t("chat.noSearchResults") : t("chat.noSessions") }}</p>

          <div v-else class="sessions">
            <section v-if="pinnedSessions.length" class="session-group session-group--pinned">
              <h3 class="session-group__title"><Pin :size="14" aria-hidden="true" />{{ t("chat.pin") }}</h3>
              <ul class="session-group__list">
                <li v-for="session in pinnedSessions" :key="session.id" class="session" @mouseleave="sessionMenuId = sessionMenuId === session.id ? null : sessionMenuId">
                  <template v-if="editingSessionId === session.id">
                    <input :data-session-edit="session.id" v-model="editingTitle" class="session__edit" type="text" :aria-label="t('chat.rename')" @keydown.enter="saveRename(session)" @keydown.esc="cancelRename" />
                    <button class="session__save" type="button" :aria-label="t('chat.saveRename')" @click="saveRename(session)"><Check :size="16" aria-hidden="true" /></button>
                  </template>
                  <template v-else>
                    <button class="session__open" type="button" :class="{ 'session__open--active': session.id === activeSessionId }" @click="openSession(session)">{{ session.title }}</button>
                    <button class="session__more" type="button" :aria-label="t('chat.moreActions')" @click.stop="toggleSessionMenu(session.id)"><MoreHorizontal :size="18" aria-hidden="true" /></button>
                    <div v-if="sessionMenuId === session.id" class="session__menu">
                      <button type="button" @click="beginRename(session)"><Pencil :size="15" aria-hidden="true" />{{ t("chat.rename") }}</button>
                      <button type="button" @click="togglePinned(session)"><PinOff :size="15" aria-hidden="true" />{{ t("chat.unpin") }}</button>
                      <button class="session__delete" type="button" :aria-label="t('chat.delete')" @click="pendingDelete = session; sessionMenuId = null"><X :size="15" aria-hidden="true" />{{ t("chat.delete") }}</button>
                    </div>
                  </template>
                </li>
              </ul>
            </section>
            <section v-for="key in sessionGroupOrder" :key="key" v-show="groupedSessions[key].length" class="session-group">
              <h3 class="session-group__title">{{ t(sessionGroupLabel[key]) }}</h3>
              <ul class="session-group__list">
                <li v-for="session in groupedSessions[key]" :key="session.id" class="session" @mouseleave="sessionMenuId = sessionMenuId === session.id ? null : sessionMenuId">
                  <template v-if="editingSessionId === session.id">
                    <input :data-session-edit="session.id" v-model="editingTitle" class="session__edit" type="text" :aria-label="t('chat.rename')" @keydown.enter="saveRename(session)" @keydown.esc="cancelRename" />
                    <button class="session__save" type="button" :aria-label="t('chat.saveRename')" @click="saveRename(session)"><Check :size="16" aria-hidden="true" /></button>
                  </template>
                  <template v-else>
                    <button class="session__open" type="button" :class="{ 'session__open--active': session.id === activeSessionId }" @click="openSession(session)">{{ session.title }}</button>
                    <button class="session__more" type="button" :aria-label="t('chat.moreActions')" @click.stop="toggleSessionMenu(session.id)"><MoreHorizontal :size="18" aria-hidden="true" /></button>
                    <div v-if="sessionMenuId === session.id" class="session__menu">
                      <button type="button" @click="beginRename(session)"><Pencil :size="15" aria-hidden="true" />{{ t("chat.rename") }}</button>
                      <button type="button" @click="togglePinned(session)"><Pin :size="15" aria-hidden="true" />{{ t("chat.pin") }}</button>
                      <button class="session__delete" type="button" :aria-label="t('chat.delete')" @click="pendingDelete = session; sessionMenuId = null"><X :size="15" aria-hidden="true" />{{ t("chat.delete") }}</button>
                    </div>
                  </template>
                </li>
              </ul>
            </section>
          </div>
        </section>

        <section class="panel panel--thread" :aria-label="t('chat.title')">
          <div ref="threadRef" class="thread" :class="{ 'thread--empty': !messages.length }">
            <p v-if="!messages.length" class="thread__empty">{{ t("chat.empty") }}</p>

            <article
              v-for="message in messages"
              :key="message.id"
              class="message"
              :class="`message--${message.role}`"
            >
              <div class="message__content" :class="{ 'message__bubble': message.role === 'user' }">
                <ChatReasoning
                  v-if="message.role === 'assistant' && (message.state === 'streaming' || message.retrieved || message.reasoning)"
                  :reasoning="message.reasoning ?? ''"
                  :working="message.state === 'streaming'"
                  :reasoning-active="message.state === 'streaming' && !message.content"
                  :retrieved="message.retrieved ?? false"
                  :sources="message.sources"
                  :seconds="message.seconds"
                  :reasoning-seconds="message.reasoningSeconds"
                  @resize="scrollToLatest"
                />
                <ChatAnswer v-if="message.role === 'assistant' && message.content" class="message__body message__body--answer" :text="message.content" />
                <p v-else class="message__body">{{ message.content }}</p>
                <div v-if="message.attachments?.length" class="message__attachments">
                  <div v-for="(item, index) in message.attachments" :key="index" class="message__attachment">
                    <img v-if="item.type === 'image'" :src="item.content || item.downloadUrl" :alt="item.name" />
                    <a v-if="item.downloadUrl" :href="item.downloadUrl" download class="message__attachment-link">{{ item.name }}</a>
                    <span v-else>{{ item.name }}</span>
                  </div>
                </div>
                <p v-if="message.state === 'stopped'" class="message__note">{{ t("chat.stopped") }}</p>
              </div>

              <div v-if="message.content && message.state !== 'streaming'" class="message__tools">
                <time v-if="message.role === 'user' && formatMessageTime(message.createdAt)" class="message__timestamp" :datetime="message.createdAt">{{ formatMessageTime(message.createdAt) }}</time>
                <button
                  v-if="message.role === 'assistant' && message.questionId"
                  class="message__tool message__retry"
                  type="button"
                  :disabled="streaming || animationBusy"
                  :title="t('chat.retry')"
                  :aria-label="t('chat.retry')"
                  @click="retry(message)"
                ><RefreshCcw :size="17" aria-hidden="true" /></button>
                <button
                  class="message__tool message__copy"
                  type="button"
                  :title="copiedId === message.id ? t('chat.copied') : t('chat.copy')"
                  :aria-label="copiedId === message.id ? t('chat.copied') : t('chat.copy')"
                  @click="copyMessage(message)"
                ><Check v-if="copiedId === message.id" :size="17" aria-hidden="true" /><Copy v-else :size="17" aria-hidden="true" /></button>
                <time v-if="message.role === 'assistant' && formatMessageTime(message.createdAt)" class="message__timestamp" :datetime="message.createdAt">{{ formatMessageTime(message.createdAt) }}</time>
              </div>

              <template v-if="message.role === 'assistant'">
                <button
                  v-if="message.state === 'complete'"
                  class="message__action"
                  type="button"
                  :disabled="animationBusy || streaming"
                  @click="showAnimation(message)"
                >
                  <span class="message__glass-veil" aria-hidden="true" />
                  <span class="message__glass-light" aria-hidden="true" />
                  <span class="message__glass-rim" aria-hidden="true" />
                  <span class="message__glass-label">{{ animationPendingFor === message.id ? t("chat.animationBusy") : t("chat.watchAnimation") }}</span>
                </button>
              </template>
            </article>
          </div>

          <div class="compose">
            <ChatComposer
              v-model="prompt"
              v-model:chapter-id="chapterId"
              :chapter-options="[{ value: ALL_CHAPTERS, label: t('chat.allChapters') }, ...chapters.map(chapter => ({ value: chapter.id, label: chapter.title }))]"
              v-model:thinking-enabled="thinkingEnabled"
              v-model:reasoning-effort="reasoningEffort"
              v-model:attachments="attachments"
              :streaming="streaming"
              :disabled="animationBusy"
              :context-key="composerContext"
              @reading="attachmentsReading = $event"
              @send="send"
              @stop="stop"
            />
          </div>
        </section>
      </div>
    </div>

    <AnimationDialog
      :open="openAnimationFor !== null"
      :title="openAnimationFor === null ? '' : (definitionOf(openAnimationFor)?.title ?? t('chat.watchAnimation'))"
      :definition="openAnimationFor === null ? null : definitionOf(openAnimationFor)"
      :trace="openAnimationFor === null ? null : (animationOf(openAnimationFor)?.trace ?? null)"
      :placeholder="t('chat.animationPlaceholder')"
      :close-label="t('common.close')"
      @close="openAnimationFor = null"
    />

    <NoticeDialog
      :open="alert !== null"
      :title="alert?.title ?? ''"
      :message="alert?.message ?? ''"
      :close-label="t('common.gotIt')"
      @close="alert = null"
    />

    <ConfirmDialog
      :open="pendingDelete !== null"
      :title="t('chat.deleteTitle')"
      :message="t('chat.deleteMessage')"
      :confirm-label="t('chat.delete')"
      :cancel-label="t('common.cancel')"
      @confirm="removeSession"
      @cancel="pendingDelete = null"
    />
  </BrandStage>
</template>

<style scoped>
/* The same paper and the same quiet card as the animation lab, so a question asked here and a demo
   opened there are visibly the same product. The thread panel is the page: it gets the height, the
   composer just sits at its foot. */
.chat { display: grid; width: 100%; height: 100%; min-height: 0; grid-template-rows: auto minmax(0, 1fr); gap: 18px; margin: 0 auto; color: var(--text); }

.chat__head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 14px; }
.chat__title { margin: 0; color: var(--text); font-family: var(--font-ui); font-size: clamp(30px, 3.4vw, 46px); font-weight: 400; letter-spacing: 0; line-height: 1.06; }
.chat__head-actions { display: flex; align-items: center; gap: 10px; }
.chat__link--history { display: none; }

.chat__link {
  min-height: 42px;
  padding: 0 20px;
  border: 1px solid color-mix(in srgb, var(--text) 16%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 24%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 5px 12px color-mix(in srgb, var(--text) 10%, transparent);
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 650;
  transition: transform 160ms cubic-bezier(.25, 1, .5, 1), border-color 160ms ease, background-color 160ms ease;
}

.chat__link:hover { border-color: var(--text); background: color-mix(in srgb, var(--surface) 46%, transparent); transform: translateY(-1px); }

.chat__home {
  display: inline-grid;
  width: 50px;
  height: 50px;
  flex: none;
  padding: 0;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--text) 10%, transparent);
  border-radius: 50%;
  background: color-mix(in srgb, var(--surface) 38%, transparent);
  box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset 0 0 2px color-mix(in srgb, var(--text) 32%, transparent), 0 4px 8px color-mix(in srgb, var(--text) 17%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.14);
  backdrop-filter: blur(7px) saturate(1.14);
  cursor: pointer;
  transition: transform 180ms ease, filter 180ms ease;
}
.chat__home:hover { transform: translateY(-1px) scale(1.04); filter: brightness(1.06); }
.chat__home:focus-visible { outline: none; border-color: var(--text); box-shadow: var(--focus-ring); }
.chat__home-icon { display: block; width: 24px; height: 24px; }
[data-theme="dark"] .chat__home-icon { filter: invert(1); }

.chat__grid { display: grid; min-height: 0; grid-template-columns: clamp(240px, 20vw, 320px) minmax(0, 1fr); gap: 20px; align-items: stretch; transition: grid-template-columns 220ms cubic-bezier(.25, 1, .5, 1); }
.chat__grid--sidebar-collapsed { grid-template-columns: 58px minmax(0, 1fr); }

.panel {
  display: grid;
  gap: 14px;
  align-content: start;
  min-width: 0;
  padding: 20px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 24px;
  background: color-mix(in srgb, var(--surface) 58%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 10%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
}

/* The conversation owns the vertical space: the thread scrolls, the composer stays put. */
.panel--thread { display: flex; flex-direction: column; gap: 14px; height: 100%; min-height: 0; }
.panel--sessions { display: flex; flex-direction: column; min-height: 0; overflow-y: auto; }

/* Nothing on this page drops below the body size: a refusal or an evidence line the learner skims is
   exactly the line that has to be read. */
.panel__note { margin: 0; color: var(--text); font-size: 19px; font-weight: 620; line-height: 1.55; }

.sessions__head { display: none; }
.sessions__heading { display: flex; align-items: center; min-width: 0; gap: 10px; }
.sessions__head-actions { display: flex; align-items: center; gap: 6px; }
.sessions__mark { display: grid; width: 28px; height: 28px; flex: none; place-items: center; border: 1px solid color-mix(in srgb, var(--text) 14%, transparent); border-radius: 9px; background: color-mix(in srgb, var(--text) 7%, transparent); font-size: 14px; font-weight: 750; }
.sessions__toolbar { display: flex; align-items: center; gap: 8px; min-width: 0; }
.sessions__new { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-width: 0; flex: 1 1 auto; min-height: 42px; padding: 8px 14px; font-size: 17px; }
.sessions__new-icon { font-size: 22px; font-weight: 400; line-height: 1; }
.sidebar-icon, .sessions__search-clear, .session__more, .session__save { display: grid; width: 38px; height: 38px; flex: 0 0 38px; place-items: center; padding: 0; border: 1px solid transparent; border-radius: 50%; background: transparent; color: var(--text-muted); cursor: pointer; transition: background-color 160ms ease, border-color 160ms ease, color 160ms ease, transform 160ms ease; }
.sidebar-icon:hover, .sessions__search-clear:hover, .session__more:hover, .session__save:hover { border-color: color-mix(in srgb, var(--text) 16%, transparent); background: color-mix(in srgb, var(--text) 8%, transparent); color: var(--text); transform: translateY(-1px); }
.sidebar-icon:focus-visible, .sessions__search-clear:focus-visible, .session__more:focus-visible, .session__save:focus-visible, .session__open:focus-visible, .session__edit:focus-visible, .session__menu button:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.sessions__search { display: flex; align-items: center; gap: 8px; min-height: 40px; padding: 0 12px; border: 1px solid color-mix(in srgb, var(--text) 12%, transparent); border-radius: 14px; background: color-mix(in srgb, var(--surface) 38%, transparent); color: var(--text-muted); }
.sessions__search input { width: 100%; min-width: 0; padding: 0; border: 0; outline: 0; background: transparent; color: var(--text); font: inherit; font-size: 16px; }
.sessions__search input::placeholder { color: var(--text-muted); }
.sessions__empty { display: grid; flex: 1 1 auto; min-height: 180px; margin: 0; place-items: center; color: var(--text); font-size: 32px; font-weight: 600; line-height: 1.4; text-align: center; }
.sessions { display: grid; grid-template-columns: minmax(0, 1fr); gap: 18px; margin: 0; padding: 0; list-style: none; min-width: 0; }
.session-group { display: grid; gap: 7px; min-width: 0; }
.session-group__title { display: flex; align-items: center; gap: 6px; margin: 0; padding: 0 8px; color: var(--text-muted); font-size: 13px; font-weight: 700; letter-spacing: .06em; line-height: 1.3; text-transform: uppercase; }
.session-group__list { display: grid; gap: 2px; margin: 0; padding: 0; list-style: none; }
.session { position: relative; display: flex; align-items: center; gap: 4px; min-width: 0; }

/* The conversation list is a sidebar on a desktop and a sheet on a phone, so the sheet-only parts stay
   out of the desktop layout entirely. */
.sessions-scrim { display: none; }

.sidebar-collapsed-toggle { display: none; }

.session__open {
  flex: 1 1 auto;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 17px;
  font-weight: 560;
  line-height: 1.35;
  text-align: left;
  transition: background-color 160ms ease, border-color 160ms ease;
}

.session__open:hover { border-color: color-mix(in srgb, var(--text) 10%, transparent); background: color-mix(in srgb, var(--text) 7%, transparent); }
.session__open--active { border-color: color-mix(in srgb, var(--text) 18%, transparent); background: color-mix(in srgb, var(--text) 10%, transparent); font-weight: 650; }

.session__more { width: 32px; height: 32px; flex-basis: 32px; opacity: 0; }
.session:hover .session__more, .session__more:focus-visible { opacity: 1; }
.session__menu { position: absolute; top: calc(100% - 2px); right: 4px; z-index: 20; display: grid; min-width: 150px; gap: 2px; padding: 6px; border: 1px solid color-mix(in srgb, var(--text) 16%, transparent); border-radius: 13px; background: color-mix(in srgb, var(--surface) 92%, transparent); box-shadow: 0 12px 28px color-mix(in srgb, var(--text) 18%, transparent); -webkit-backdrop-filter: blur(13px) saturate(1.2); backdrop-filter: blur(13px) saturate(1.2); }
.session__menu button { display: flex; align-items: center; gap: 9px; width: 100%; min-height: 34px; padding: 7px 9px; border: 0; border-radius: 8px; background: transparent; color: var(--text); cursor: pointer; font: inherit; font-size: 15px; text-align: left; }
.session__menu button:hover { background: color-mix(in srgb, var(--text) 8%, transparent); }
.session__menu .session__delete { width: 100%; height: auto; min-height: 34px; justify-content: flex-start; padding: 7px 9px; border-radius: 8px; color: var(--text); font-size: 15px; }
.session__menu .session__delete:hover { color: var(--text); }
.session__edit { min-width: 0; flex: 1 1 auto; height: 36px; padding: 6px 10px; border: 1px solid var(--line-strong); border-radius: 9px; outline: none; background: color-mix(in srgb, var(--surface) 70%, transparent); color: var(--text); font: inherit; font-size: 16px; }
.session__edit:focus { border-color: var(--text); box-shadow: var(--focus-ring); }
.session__save { width: 32px; height: 32px; flex-basis: 32px; }

.session__delete {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font: inherit;
  font-size: 22px;
  line-height: 1;
  transition: background-color 160ms ease, color 160ms ease;
}

.session__delete:hover { background: color-mix(in srgb, var(--text) 10%, transparent); color: var(--text); }

.chat__grid--sidebar-collapsed .panel--sessions { align-items: center; padding: 10px 8px; overflow: hidden; }
.chat__grid--sidebar-collapsed .panel--sessions > :not(.sidebar-collapsed-toggle) { display: none; }
.chat__grid--sidebar-collapsed .sidebar-collapsed-toggle { display: grid; width: 40px; height: 40px; flex: 0 0 40px; place-items: center; padding: 0; border: 1px solid color-mix(in srgb, var(--text) 14%, transparent); border-radius: 50%; background: color-mix(in srgb, var(--surface) 34%, transparent); color: var(--text); cursor: pointer; box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 90%, transparent), 0 5px 12px color-mix(in srgb, var(--text) 10%, transparent); }
.chat__grid--sidebar-collapsed .sidebar-collapsed-toggle:hover { transform: translateY(-1px); }

.thread { flex: 1 1 auto; min-height: 0; display: grid; gap: 16px; align-content: start; padding: 4px 8px 4px 2px; overflow-y: auto; }
.thread--empty { place-content: center; }
.thread__empty { margin: 0; padding: 24px 0; color: var(--text); font-size: 32px; font-weight: 600; line-height: 1.4; text-align: center; overflow-wrap: anywhere; }

/* The answer is not boxed: a bubble inside a bordered panel inside a page border is three frames around
   one paragraph, and on a phone it leaves a column too narrow to read. The answer is text on the page,
   the question is the only bubble - the shape every chat the learner already uses has. */
.message { display: grid; gap: 10px; min-width: 0; max-width: 100%; }
.message--user {
  justify-self: end;
  max-width: min(86%, 640px);
}
.message__content { display: grid; gap: 10px; min-width: 0; max-width: 100%; }
.message__bubble {
  justify-self: end;
  padding: 12px 18px;
  border: 1px solid color-mix(in srgb, var(--text) 16%, transparent);
  border-radius: 20px;
  background: color-mix(in srgb, var(--text) 9%, transparent);
}
.message--assistant { justify-self: stretch; padding: 0; }

.message__body { margin: 0; color: var(--text); font-size: 19px; line-height: 1.75; white-space: pre-wrap; word-break: break-word; }
.message__body :deep(strong) { font-weight: 700; }
.message__body--answer { white-space: normal; }
.message__body--answer :deep(p) { margin: 0 0 12px; }
.message__body--answer :deep(:last-child) { margin-bottom: 0; }
.message__body--answer :deep(h1), .message__body--answer :deep(h2), .message__body--answer :deep(h3),
.message__body--answer :deep(h4), .message__body--answer :deep(h5), .message__body--answer :deep(h6) { margin: 18px 0 8px; font-size: 20px; font-weight: 700; line-height: 1.4; }
.message__body--answer :deep(ul), .message__body--answer :deep(ol) { margin: 10px 0; padding-left: 26px; }
.message__body--answer :deep(li) { margin: 4px 0; }
.message__body--answer :deep(:not(pre) > code) { padding: 2px 5px; border-radius: 4px; background: color-mix(in srgb, var(--text) 7%, transparent); font-family: var(--font-mono, monospace); font-size: 15px; }
.message__body--answer :deep(blockquote) { margin: 12px 0; padding-left: 16px; border-left: 2px solid var(--line-strong); color: var(--text-muted); }
.message__body--answer :deep(table) { display: block; width: fit-content; max-width: 100%; margin: 14px 0; overflow-x: auto; border-collapse: collapse; }
.message__body--answer :deep(th), .message__body--answer :deep(td) { padding: 7px 12px; border: 1px solid var(--line-strong); text-align: left; }
.message__body--answer :deep(a) { color: var(--text); text-decoration: underline; text-underline-offset: 3px; }
.message__note { margin: 0; color: var(--text-muted); font-size: 19px; font-weight: 620; }

.message__tools { display: flex; align-items: center; gap: 3px; min-width: 0; max-width: 100%; }
.message--user .message__tools { justify-content: flex-end; }
.message__timestamp { min-width: 0; margin: 0 7px; color: var(--text-muted); font-size: 15px; line-height: 1.4; overflow-wrap: anywhere; opacity: 0; visibility: hidden; }
.message:hover .message__timestamp { opacity: 1; visibility: visible; }
.message--user .message__timestamp { text-align: right; }
.message__tool { display: grid; width: 32px; height: 32px; flex: 0 0 32px; padding: 0; place-items: center; border: 0; border-radius: 6px; background: transparent; color: var(--text-muted); cursor: pointer; transition: color 150ms ease, background-color 150ms ease; }
.message__tool:hover:not(:disabled) { color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.message__tool:disabled { opacity: .4; cursor: default; }
.message__tool:focus-visible, .message__action:focus-visible { outline: 2px solid var(--text); outline-offset: 3px; }

.message__action {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  justify-self: start;
  min-height: 48px;
  max-width: 100%;
  padding: 10px 24px;
  overflow: hidden;
  border: 1px double rgba(51, 51, 51, .08);
  border-radius: 999px;
  background: rgba(255, 255, 255, .08);
  box-shadow: inset 2px -2px 1px -1px rgba(255,255,255,.9), inset -2px 2px 1px -1px rgba(255,255,255,.9), inset 6px -6px 1px -6px rgba(255,255,255,.55), inset -6px 6px 1px -6px rgba(255,255,255,.55), inset 0 0 2px rgba(0,0,0,.18), 0 3px 7px rgba(0,0,0,.1);
  backdrop-filter: blur(5px);
  filter: brightness(1.05);
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 650;
  transition: transform 250ms linear, background-color 250ms linear, box-shadow 250ms linear, filter 250ms linear;
}

.message__action:hover:not(:disabled) { transform: scale(1.02); background: transparent; filter: brightness(1.1); box-shadow: inset 2px -2px 1px -1px rgba(255,255,255,.95), inset -2px 2px 1px -1px rgba(255,255,255,.95), inset 6px -6px 1px -6px rgba(255,255,255,.65), inset -6px 6px 1px -6px rgba(255,255,255,.65), inset 0 0 2px rgba(0,0,0,.14), 0 5px 10px rgba(0,0,0,.12); }
.message__action:active:not(:disabled) { transform: scale(1); }
.message__action:disabled { cursor: default; opacity: .5; }
.message__glass-veil, .message__glass-light, .message__glass-rim { position: absolute; pointer-events: none; border-radius: inherit; transition: transform 250ms linear, opacity 250ms linear, filter 250ms linear, border-color 250ms linear; }
.message__glass-veil { top: 35%; left: 8px; width: calc(100% - 16px); height: calc(100% - 16px); border: 1px solid rgba(0,0,0,.16); filter: blur(4px); }
.message__glass-light { z-index: 1; inset: 0; background: linear-gradient(45deg, rgba(255,255,255,.8), transparent 15%, transparent 85%, rgba(255,255,255,.8)); opacity: .8; filter: blur(7px); }
.message__glass-rim { z-index: 2; inset: 4.5px; border: 1px solid rgba(255,255,255,.2); filter: blur(1px); }
.message__glass-label { position: relative; z-index: 3; transition: transform 250ms linear; }
.message__action:hover:not(:disabled) .message__glass-veil { opacity: .8; filter: blur(8px); }
.message__action:hover:not(:disabled) .message__glass-light { transform: scale(1.05); opacity: 1; filter: blur(9px); }
.message__action:hover:not(:disabled) .message__glass-rim { border-color: rgba(255,255,255,.45); }
.message__action:hover:not(:disabled) .message__glass-label { transform: translateY(-.5px); }

.compose { flex: 0 0 auto; display: grid; gap: 10px; }
.compose > select { width: min(340px, 100%); min-height: 36px; padding: 6px 14px; font-size: 14px; }
.message__attachments { display: flex; flex-wrap: wrap; gap: 8px; }
.message__attachment { display: flex; flex-direction: column; gap: 4px; padding: 8px; border: 1px solid var(--line-strong); border-radius: 10px; max-width: 180px; font-size: 13px; overflow-wrap: anywhere; }
.message__attachment img { max-width: 160px; max-height: 120px; object-fit: contain; border-radius: 6px; }
.message__attachment-link { color: var(--text); text-decoration: underline; text-underline-offset: 3px; }

.field__control {
  width: 100%;
  min-height: 46px;
  padding: 10px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  font: inherit;
  font-size: 19px;
}

.field__control--area { min-height: 88px; border-radius: 20px; resize: vertical; line-height: 1.6; }
.field__control:focus-visible { outline: none; border-color: var(--text); box-shadow: var(--focus-ring); }

.compose__actions { display: flex; justify-content: flex-end; }

.button {
  min-height: 48px;
  padding: 10px 26px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 650;
  transition: border-color .16s ease, background-color .16s ease, transform .16s ease;
}

.button:hover:not(:disabled) { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.button:disabled { cursor: default; opacity: .42; }

/* The primary action is the one inverted pill the design system uses for "go". */
.button--primary { border-color: var(--text); background: var(--text); color: var(--surface); }
.button--primary:hover:not(:disabled) { background: color-mix(in srgb, var(--text) 88%, var(--surface)); }

@media (max-width: 720px) {
  .chat__grid { grid-template-columns: minmax(0, 1fr); }
  .panel--thread { height: 100%; }
}

/* ---------------------------------------------------------------- phones
   Two things change shape here rather than shrink. The conversation gets the page to itself - the past
   conversations move into a sheet behind one button - and the composer stops being the foot of a fixed
   height panel and becomes a bar pinned to the bottom of the viewport, which is the only place a thumb
   expects it. Desktop keeps the sidebar and the inner scroll: neither layout is the other one stretched. */
@media (max-width: 720px) {
  .chat { gap: 12px; }
  .chat__head { gap: 10px; }
  .chat__title { font-size: 24px; line-height: 1.2; }
  .thread__empty { font-size: 26px; }
  .chat__link { min-height: 38px; padding: 0 14px; font-size: 17px; }
  .chat__link--history { display: inline-flex; align-items: center; }

  .chat__grid { gap: 12px; }

  /* The list: out of the flow, over the conversation, dismissed by the scrim or the × . */
  .sessions-scrim { display: block; position: fixed; inset: 0; z-index: 55; background: rgba(20, 28, 28, .38); }

  .panel--sessions {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 56;
    max-height: 76dvh;
    padding: 8px 16px calc(18px + env(safe-area-inset-bottom));
    border-width: 1px 0 0;
    border-radius: 22px 22px 0 0;
    overflow-y: auto;
    transform: translateY(102%);
    visibility: hidden;
    transition: transform 220ms cubic-bezier(.25, 1, .5, 1), visibility 220ms;
  }

  .panel--sessions-open { transform: translateY(0); visibility: visible; }

  .sessions__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .sessions__title { margin: 0; font: inherit; font-size: 19px; font-weight: 650; }
  .sessions__collapse, .sessions__head-actions .sidebar-icon { display: none; }

  .sessions__close {
    display: grid;
    width: 40px;
    height: 40px;
    place-items: center;
    border: 1px solid color-mix(in srgb, var(--text) 18%, transparent);
    border-radius: 50%;
    background: transparent;
    color: var(--text);
    cursor: pointer;
    font: inherit;
    font-size: 22px;
    line-height: 1;
  }

  /* The conversation scrolls with the page; the composer rides at the bottom of the viewport. */
  .panel--thread {
    height: 100%;
    min-height: 0;
    padding: 0;
    border: 0;
    border-radius: 0;
    background: transparent;
    box-shadow: none;
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
  }

  .thread { overflow-y: auto; gap: 18px; padding: 2px 2px 8px; }

  .message--user { max-width: 88%; }
  .message__bubble { padding: 10px 16px; }

  .compose {
    position: relative;
    z-index: 5;
    gap: 10px;
    margin-top: 4px;
    padding: 0 0 env(safe-area-inset-bottom);
  }

  .field__control { min-height: 44px; font-size: 18px; }
  .field__control--area { min-height: 76px; }
  .button { min-height: 46px; padding: 10px 22px; }
  .compose__actions .button { width: 100%; }
}

@media (prefers-reduced-motion: reduce) { .chat * { transition-duration: 1ms !important; } }
</style>
