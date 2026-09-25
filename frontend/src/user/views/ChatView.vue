<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import BrandStage from "../../shared/components/BrandStage.vue";
import NoticeDialog from "../../shared/components/NoticeDialog.vue";
import ConfirmDialog from "../../shared/components/ConfirmDialog.vue";
import { useI18n } from "../../shared/i18n/locale";
import AnimationPlayer from "../../animation/AnimationPlayer.vue";
import type { DsvpSimulationResponse } from "../../shared/types/animation";
import type { Chapter, ChatResponse, ChatSessionSummary, ChatSource } from "../../shared/types";
import { auth } from "../../app/providers/runtime";
import { userApi } from "../runtime";
import { chatErrorKey, deltaOf, doneOf, errorOf, sourcesOf } from "../chat-stream";

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
}

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const chapters = ref<Chapter[]>([]);
const chapterId = ref("");
const prompt = ref("");
const messages = ref<ConversationMessage[]>([]);
const sessions = ref<ChatSessionSummary[]>([]);
const activeSessionId = ref<string | null>(null);
const phase = ref<"idle" | "streaming">("idle");
const alert = ref<{ title: string; message: string } | null>(null);
const pendingDelete = ref<ChatSessionSummary | null>(null);
const sessionsFailed = ref(false);
const threadRef = ref<HTMLElement | null>(null);
/** One animation per reply, keyed by the reply it belongs to. */
const animations = ref<Record<number, DsvpSimulationResponse>>({});
const animationBusy = ref(false);

const ALL_CHAPTERS = "";
const MAX_PROMPT = 4000;
/** Short replies that mean "yes, show me" to an offer the model just made. */
const YES = /^(好的|好|好啊|要|想要|想看|看看|看一下|看|来一个|来|演示|演示一下|演示一下吧|可以|行|嗯|是的|当然|ok|okay|yes|yeah|yep|sure|go ahead|show me|do it|please)$/i;

let sequence = 0;
let controller: AbortController | null = null;

const streaming = computed(() => phase.value === "streaming");
const canSend = computed(() => prompt.value.trim().length > 0 && !streaming.value);
const tooLong = computed(() => prompt.value.trim().length > MAX_PROMPT);
const signedIn = computed(() => Boolean(auth.state.user));

function push(
  role: "user" | "assistant",
  content: string,
  state: MessageState = "complete",
  question?: string,
): number {
  const id = ++sequence;
  messages.value = [...messages.value, { id, role, content, sources: [], state, question }];
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

function failureMessage(cause: unknown): string {
  return cause instanceof Error && cause.message ? cause.message : t("common.failed");
}

function raise(title: string, message: string) {
  alert.value = { title, message };
}

async function send() {
  const question = prompt.value.trim();
  if (!question || streaming.value || tooLong.value) return;

  // "好的" after an offer is not a new question - it is the learner taking the model up on the demo
  // it just proposed. Answering it with another paragraph would spend quota and delay the animation.
  if (isYes(question) && lastReplyOffersAnimation()) {
    prompt.value = "";
    push("user", question);
    const offer = lastOfferingReply();
    await runAnimation(animationPrompt(offer!), offer!.id);
    return;
  }

  const history = messages.value
    .filter((item) => item.state === "complete" && item.content)
    .slice(-12)
    .map((item) => ({ role: item.role, content: item.content }));

  prompt.value = "";
  push("user", question);
  const replyId = push("assistant", "", "streaming", question);
  phase.value = "streaming";
  controller = new AbortController();
  const signal = controller.signal;
  await scrollToLatest();

  try {
    const response = await userApi.streamChat(
      { prompt: question, chapterId: chapterId.value || undefined, sessionId: activeSessionId.value ?? undefined, history },
      signal,
    );
    let answer = "";
    let sources: ChatSource[] = [];
    for await (const event of response.events) {
      // A stop has to land even when the next event is slow to arrive.
      if (signal.aborted) break;
      if (event.event === "sources") sources = sourcesOf(event);
      else if (event.event === "delta") {
        answer += deltaOf(event);
        update(replyId, { content: answer, sources });
        await scrollToLatest();
      } else if (event.event === "done") {
        const done = doneOf(event) as ChatResponse | null;
        answer = done?.answer ?? answer;
        sources = done?.sources?.length ? done.sources : sources;
        update(replyId, { content: answer, sources, state: "complete" });
        if (done?.sessionId) {
          activeSessionId.value = done.sessionId;
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
    // would present a half-answer as the whole one.
    if (reply?.state === "streaming") {
      update(replyId, { state: signal.aborted || !reply.content ? "stopped" : "complete" });
    }
  } catch (cause) {
    const stopped = signal.aborted;
    if (stopped) {
      const reply = messages.value.find((item) => item.id === replyId);
      update(replyId, { state: reply?.content ? "complete" : "stopped" });
    } else {
      messages.value = messages.value.filter((item) => item.id !== replyId);
      raise(t("common.failed"), failureMessage(cause));
    }
  } finally {
    phase.value = "idle";
    controller = null;
    await scrollToLatest();
  }
}

function stop() {
  controller?.abort();
}

function isYes(text: string): boolean {
  return YES.test(text.replace(/[\s，。！？,.!?~～]/g, ""));
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
 * Builds the animation this answer offers and plays it under the answer.
 *
 * The sentence goes to the same interpret endpoint the animation lab uses, so the model only picks a
 * capability and the local engine computes the frames - the demo can be the wrong one but never an
 * invented one.
 */
async function runAnimation(question: string, replyId: number) {
  const chapter = chapterId.value || chapters.value[0]?.id || "";
  if (!chapter || !question) {
    raise(t("chat.animationFailedTitle"), t("chat.animationUnavailable"));
    return;
  }
  animationBusy.value = true;
  try {
    // confirmed: the learner already said yes, so the interpreter must pick a capability rather than
    // re-judge whether the topic deserves a demo.
    const request = await userApi.interpretAnimation({ chapterId: chapter, prompt: question, confirmed: true });
    const data = await userApi.simulateAnimation(request);
    animations.value = { ...animations.value, [replyId]: data };
    await scrollToLatest();
  } catch (cause) {
    raise(t("chat.animationFailedTitle"), animationFailure(cause));
  } finally {
    animationBusy.value = false;
  }
}

/** The engine refuses in plain Chinese; anything that still reads like machinery gets replaced. */
function animationFailure(cause: unknown): string {
  const message = cause instanceof Error ? cause.message : "";
  if (!message || /未匹配|capability|DSVP|status|ANIMATION_|resolve/i.test(message)) {
    return t("chat.animationUnavailable");
  }
  return message;
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
  activeSessionId.value = null;
  prompt.value = "";
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
    animations.value = {};
    messages.value = detail.messages.map((item) => ({
      id: ++sequence,
      role: item.role,
      content: item.content,
      sources: item.sources ?? [],
      state: "complete" as MessageState,
    }));
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

onBeforeUnmount(() => controller?.abort());

/**
 * The model answers in light markdown; the page renders the three shapes it actually uses and nothing
 * else. Escaping runs first, so every tag below is one this function wrote - the answer's own angle
 * brackets can never become markup.
 */
function renderAnswer(raw: string): string {
  const escaped = raw.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  return escaped
    .replace(/\*\*([^*\n]+)\*\*/g, "<strong>$1</strong>")
    .replace(/^#{1,4}\s+(.+)$/gm, '<strong class="answer__heading">$1</strong>')
    .replace(/^\s*[-*]\s+/gm, "• ");
}
</script>

<template>
  <BrandStage wide>
    <div class="chat">
      <header class="chat__head">
        <h1 class="chat__title">{{ t("chat.title") }}</h1>
        <button class="chat__link" type="button" @click="router.push('/')">{{ t("common.backHome") }}</button>
      </header>

      <div class="chat__grid">
        <section class="panel" :aria-label="t('chat.sessions')">
          <button class="button button--primary" type="button" :disabled="streaming" @click="newConversation">
            {{ t("chat.newChat") }}
          </button>

          <p v-if="!signedIn" class="panel__note">{{ t("chat.signInToKeep") }}</p>
          <p v-else-if="sessionsFailed" class="panel__note">{{ t("chat.sessionsFailed") }}</p>
          <p v-else-if="!sessions.length" class="panel__note">{{ t("chat.noSessions") }}</p>

          <ul v-else class="sessions">
            <li v-for="session in sessions" :key="session.id" class="session">
              <button
                class="session__open"
                type="button"
                :class="{ 'session__open--active': session.id === activeSessionId }"
                @click="openSession(session)"
              >
                {{ session.title }}
              </button>
              <button class="session__delete" type="button" :aria-label="t('chat.delete')" @click="pendingDelete = session">
                ×
              </button>
            </li>
          </ul>
        </section>

        <section class="panel panel--thread" :aria-label="t('chat.title')">
          <div ref="threadRef" class="thread">
            <p v-if="!messages.length" class="thread__empty">{{ t("chat.empty") }}</p>

            <article
              v-for="message in messages"
              :key="message.id"
              class="message"
              :class="`message--${message.role}`"
            >
              <p v-if="message.role === 'assistant'" class="message__body" v-html="renderAnswer(message.content)" />
              <p v-else class="message__body">{{ message.content }}</p>
              <p v-if="message.state === 'streaming' && !message.content" class="message__note">{{ t("chat.thinking") }}</p>
              <p v-else-if="message.state === 'stopped'" class="message__note">{{ t("chat.stopped") }}</p>

              <template v-if="message.role === 'assistant'">
                <AnimationPlayer
                  v-if="animationOf(message.id)"
                  class="message__animation"
                  :definition="definitionOf(message.id)"
                  :trace="animationOf(message.id)?.trace ?? null"
                  :placeholder="t('chat.animationPlaceholder')"
                />

                <button
                  v-if="message.state === 'complete'"
                  class="message__action"
                  type="button"
                  :disabled="animationBusy"
                  @click="runAnimation(animationPrompt(message), message.id)"
                >
                  {{ animationBusy ? t("chat.animationBusy") : t("chat.watchAnimation") }}
                </button>
              </template>
            </article>
          </div>

          <div class="compose">
            <select v-model="chapterId" class="field__control" :aria-label="t('chat.scope')">
              <option :value="ALL_CHAPTERS">{{ t("chat.allChapters") }}</option>
              <option v-for="chapter in chapters" :key="chapter.id" :value="chapter.id">{{ chapter.title }}</option>
            </select>

            <textarea
              v-model="prompt"
              class="field__control field__control--area"
              rows="3"
              :placeholder="t('chat.placeholder')"
              :aria-label="t('chat.question')"
              @keydown.enter.exact.prevent="send"
            />

            <p v-if="tooLong" class="panel__note">{{ t("chat.error.tooLong") }}</p>

            <div class="compose__actions">
              <button v-if="streaming" class="button" type="button" @click="stop">{{ t("chat.stop") }}</button>
              <button v-else class="button button--primary" type="button" :disabled="!canSend || tooLong" @click="send">
                {{ t("chat.send") }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>

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
.chat { display: grid; width: min(1320px, 100%); gap: 22px; margin: 0 auto; color: var(--text); }

.chat__head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 14px; }
.chat__title { margin: 0; color: var(--text); font-family: var(--font-ui); font-size: clamp(30px, 3.4vw, 46px); font-weight: 400; letter-spacing: 0; line-height: 1.06; }

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

.chat__grid { display: grid; grid-template-columns: minmax(0, 300px) minmax(0, 1fr); gap: 20px; align-items: start; }

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
.panel--thread { display: flex; flex-direction: column; gap: 14px; height: clamp(560px, calc(100dvh - 250px), 900px); }

/* Nothing on this page drops below the body size: a refusal or an evidence line the learner skims is
   exactly the line that has to be read. */
.panel__note { margin: 0; color: var(--text); font-size: 19px; font-weight: 620; line-height: 1.55; }

.sessions { display: grid; grid-template-columns: minmax(0, 1fr); gap: 8px; margin: 0; padding: 0; list-style: none; min-width: 0; }
.session { display: flex; align-items: flex-start; gap: 6px; min-width: 0; }

.session__open {
  flex: 1 1 auto;
  min-width: 0;
  padding: 9px 14px;
  border: 1px solid transparent;
  border-radius: 18px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 620;
  line-height: 1.35;
  text-align: left;
  transition: background-color 160ms ease, border-color 160ms ease;
}

.session__open:hover { border-color: color-mix(in srgb, var(--text) 16%, transparent); background: color-mix(in srgb, var(--text) 6%, transparent); }
.session__open--active { border-color: var(--text); background: color-mix(in srgb, var(--text) 9%, transparent); }

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

.thread { flex: 1 1 auto; min-height: 0; display: grid; gap: 16px; align-content: start; padding: 4px 8px 4px 2px; overflow-y: auto; }
.thread__empty { margin: 0; padding: 24px 0; color: var(--text); font-size: 19px; font-weight: 620; line-height: 1.6; text-align: center; }

.message { display: grid; gap: 10px; max-width: min(88%, 820px); padding: 16px 20px; border-radius: 22px; }
.message--user { justify-self: end; border: 1px double color-mix(in srgb, var(--text) 18%, transparent); background: color-mix(in srgb, var(--text) 9%, transparent); }
.message--assistant { justify-self: start; border: 1px double color-mix(in srgb, var(--text) 15%, transparent); background: color-mix(in srgb, var(--surface) 72%, transparent); }

.message__body { margin: 0; color: var(--text); font-size: 19px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.message__body :deep(strong) { font-weight: 700; }
.message__body :deep(.answer__heading) { display: block; margin: 16px 0 6px; font-weight: 700; }
.message__note { margin: 0; color: var(--text-muted); font-size: 19px; font-weight: 620; }

/* The demo the answer just offered, drawn by the same player the animation lab uses. */
.message__animation {
  width: 100%;
  margin-top: 4px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--text) 14%, transparent);
  border-radius: 18px;
  background: color-mix(in srgb, var(--surface) 62%, transparent);
}

.message__action {
  justify-self: start;
  min-height: 44px;
  padding: 9px 20px;
  border: 1px solid color-mix(in srgb, var(--text) 20%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 650;
  transition: border-color .16s ease, background-color .16s ease;
}

.message__action:hover:not(:disabled) { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.message__action:disabled { cursor: default; opacity: .5; }

.compose { flex: 0 0 auto; display: grid; gap: 12px; padding-top: 14px; border-top: 1px solid var(--line); }

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

@media (max-width: 900px) {
  .chat__grid { grid-template-columns: minmax(0, 1fr); }
  .panel--thread { height: clamp(520px, calc(100dvh - 220px), 900px); }
}

@media (prefers-reduced-motion: reduce) { .chat * { transition-duration: 1ms !important; } }
</style>
