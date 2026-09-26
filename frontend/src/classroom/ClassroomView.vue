<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import BrandStage from "../shared/components/BrandStage.vue";
import AnimationPlayer from "../animation/AnimationPlayer.vue";
import type { ClassroomAction, ClassroomSession, ClassroomSlideMatch, DsvpRequest, DsvpSimulationResponse, LessonCourseware } from "../shared/types/contracts";
import { useI18n } from "../shared/i18n/locale";
import { userApi } from "../user/runtime";
import type { ClassroomLesson, ClassroomPreparationStatus } from "../user/api";
import { preparationPollDelayMs } from "./preparation-poll";
import SlidePanel from "./SlidePanel.vue";

/**
 * The most recent session, kept after leaving so the entry page (and this picker) can offer to
 * continue it. Nothing here resumes a lesson on its own: opening the classroom shows the picker
 * unless a session was asked for by id, because a learner who has not chosen a lesson yet should
 * not land in the middle of one.
 */
const LAST_KEY = "structify.classroom.last";
const { t } = useI18n();
const lessons = ref<ClassroomLesson[]>([]);
const lessonId = ref("");
const session = ref<ClassroomSession | null>(null);
const lastSessionId = ref("");
const input = ref("");
const busy = ref(false);
const preparing = ref(false);
const error = ref("");
const animation = ref<DsvpSimulationResponse | null>(null);
const courseware = ref<LessonCourseware | null>(null);
const coursewareLoading = ref(false);
const coursewareError = ref("");
const route = useRoute();
const router = useRouter();
let pollTimer: number | null = null;
/**
 * How many preparation polls this attempt has already fired. The delay grows with it, so a long
 * preparation (a whole deck of slides) costs a handful of round trips instead of one per second -
 * each of those round trips crosses the same slow link the lesson content does.
 */
let pollCount = 0;

const stage = computed<Record<string, unknown>>(() => session.value?.stage ?? {});
const response = computed<Record<string, unknown> | null>(() => record(stage.value.teacherResponse));
const responseAnswered = computed(() => response.value?.answered === true);
/**
 * The lesson is over only when the cursor has run past the last step. A summary-typed step is a teaching
 * beat - a deck's 小结 page can be followed by the next sub-lesson - so keying "finished" on the state
 * alone would end the lesson there and hide the rest of the courseware.
 */
const finished = computed(() => session.value?.state === "SUMMARY"
  && Number(stage.value.stepIndex ?? -1) >= Number(stage.value.stepCount ?? 0));
/**
 * A question stays open until it has been answered correctly. A wrong answer is not an answer: the
 * classroom keeps the question on screen and the learner answers again instead of walking past it -
 * the model's verdict decides that, and it can never end the lesson by itself.
 */
const questionOpen = computed(() => session.value?.state === "WAITING" && !responseAnswered.value);
/** A failed attempt, still shown with the question so the learner can correct it right away. */
const failedAttempt = computed(() => response.value?.kind === "answer" && !responseAnswered.value);
/**
 * An interruption the learner asked for, on a step that is not itself waiting for an answer. On a question
 * step the input stays open instead: asking the teacher for help must not take away the box the learner
 * needs to answer in, or the question could only be escaped by walking away from it.
 */
const interruption = computed(() => response.value?.kind === "question" && !responseAnswered.value
  && session.value?.state !== "WAITING");
const attempts = computed(() => typeof response.value?.attempts === "number" ? response.value?.attempts as number : 0);
/**
 * A question the learner cannot answer has two honest ways out, and neither pretends to be an answer:
 * a hint first (free, once per question - it points at the idea without giving it away), then the
 * explained skip, which the lesson budgets. Asking for help is never hidden, and the pane never offers a
 * button the server would refuse.
 */
const hinted = computed(() => response.value?.hinted === true);
const skipsUsed = computed(() => typeof stage.value.skipsUsed === "number" ? stage.value.skipsUsed as number : 0);
const skipLimit = computed(() => typeof stage.value.skipLimit === "number" ? stage.value.skipLimit as number : 0);
const skipsLeft = computed(() => Math.max(0, skipLimit.value - skipsUsed.value));
const canHint = computed(() => canAnswer.value && !hinted.value);
const canSkip = computed(() => canAnswer.value && hinted.value && skipsLeft.value > 0);
const skipNote = computed(() => t("classroom.skipsLeft", { count: skipsLeft.value }));
const canType = computed(() => !finished.value && !responseAnswered.value && !interruption.value);
const canAnswer = computed(() => questionOpen.value && !interruption.value);
const placeholder = computed(() => (canAnswer.value ? t("classroom.placeholderAnswer") : t("classroom.placeholderAsk")));
const continueLabel = computed(() => session.value?.state === "OPENING" ? t("classroom.start") : t("classroom.next"));
const revision = computed(() => typeof stage.value.revision === "number" ? stage.value.revision : undefined);
const slideRefs = computed(() => stringList(stage.value.slideRefs));
/** The API decides which page belongs to the step (human correction > model > alignment) and says why. */
const slideMatch = computed<ClassroomSlideMatch | null>(() => {
  const value = record(stage.value.slideMatch);
  if (!value || typeof value.slideId !== "string") return null;
  return {
    slideId: value.slideId as string,
    slideTitle: typeof value.slideTitle === "string" ? value.slideTitle : undefined,
    kind: (value.kind as ClassroomSlideMatch["kind"]) ?? "DIRECT",
    score: typeof value.score === "number" ? value.score : 0,
    source: (value.source as ClassroomSlideMatch["source"]) ?? "auto",
    reason: typeof value.reason === "string" ? (value.reason as ClassroomSlideMatch["reason"]) : undefined,
    subLessonId: typeof value.subLessonId === "string" ? value.subLessonId : undefined,
    subLessonTitle: typeof value.subLessonTitle === "string" ? value.subLessonTitle : undefined,
    scene: typeof value.scene === "string" ? value.scene : undefined,
  };
});
const showSlides = computed(() => Boolean(session.value) || coursewareLoading.value || (courseware.value?.slides.length ?? 0) > 0);
/**
 * The prepared step decides the page. The pane never invents a page of its own: when a step has none,
 * the API keeps the previous page and reports it, so the courseware cannot drift away from the lecture.
 */
const activeSlideId = computed(() => slideRefs.value[0] ?? slideMatch.value?.slideId ?? null);
/**
 * The animation is whatever the prepared step asked for, executed by the local engine. The classroom
 * hands the trace to the shared player, so a step demonstrated in class and the same operation opened
 * in the lab are drawn by the same renderer.
 */
const animationRequest = computed(() => {
  const reference = record(stage.value.animationRef);
  if (!reference) return null;
  return record(reference.request) ? reference.request as unknown as DsvpRequest : null;
});
const message = computed(() => {
  const reply = text(response.value?.feedback);
  if (reply) return reply;
  const prompt = text(stage.value.prompt);
  if (prompt) return prompt;
  const content = text(stage.value.content);
  if (content) return content;
  return session.value?.summary || t("classroom.ready");
});
/** The lesson card header: which class this is and how far along it is. */
const lessonTitle = computed(() => {
  const fromCourseware = lessonName(text(courseware.value?.title));
  if (fromCourseware) return fromCourseware;
  const id = text(stage.value.lessonId) || lessonId.value;
  return lessonName(lessons.value.find((lesson) => lesson.id === id)?.title ?? "");
});
const progressLabel = computed(() => {
  const total = Number(stage.value.stepCount ?? 0);
  if (!Number.isFinite(total) || total <= 0) {
    return session.value?.state === "OPENING" ? t("classroom.opening") : "";
  }
  const index = Number(stage.value.stepIndex ?? 0);
  if (index < 0) return t("classroom.opening");
  if (session.value?.state === "SUMMARY" && index >= total) return t("classroom.finishedAll", { total });
  return t("classroom.step", { index: Math.min(index + 1, total), total });
});
/** The lab opens scoped to the lesson's own lesson id so the picker starts where the class is. */
const labQuery = computed(() => {
  const id = text(stage.value.lessonId) || lessonId.value;
  return id ? { lessonId: id } : undefined;
});
const labRoute = computed(() => ({ path: "/animation", query: labQuery.value }));

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string" && Boolean(item.trim())) : [];
}

function failureMessage(cause: unknown): string {
  return cause instanceof Error && cause.message ? cause.message : t("common.failed");
}

/**
 * The lesson picker shows the lesson's name only. Reviewed sources carry an audit note in their title
 * ("查找-二叉排序树（已核验教材第 271–278 页选段）") - provenance the review pipeline needs, but which
 * a learner picking a lesson does not.
 */
function lessonName(title: string): string {
  return title.replace(/[（(]已核验[^）)]*[）)]/g, "").trim();
}

function remember(next: ClassroomSession) {
  session.value = next;
  localStorage.setItem(LAST_KEY, next.id);
  lastSessionId.value = next.id;
}

function recallLastSessionId() {
  lastSessionId.value = localStorage.getItem(LAST_KEY) ?? "";
}

async function loadLessons() {
  busy.value = true;
  error.value = "";
  try {
    lessons.value = await userApi.listClassroomLessons();
    lessonId.value ||= lessons.value[0]?.id ?? "";
  } catch (cause) {
    error.value = failureMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function loadCourseware(id: string) {
  if (!id) return;
  coursewareLoading.value = true;
  coursewareError.value = "";
  try {
    courseware.value = await userApi.getLessonCourseware(id);
  } catch (cause) {
    courseware.value = null;
    coursewareError.value = failureMessage(cause);
  } finally {
    coursewareLoading.value = false;
  }
}

/**
 * Pick one session back up. This only ever runs because somebody asked for it - the entry page's
 * "继续上次课堂" link (`/classroom?session=<id>`), the picker's own button, or a bookmark carrying the
 * id. A session the server has forgotten is dropped instead of being retried forever.
 */
async function resume(id: string, quiet: boolean): Promise<boolean> {
  if (!id || busy.value) return false;
  busy.value = true;
  error.value = "";
  try {
    const restored = await userApi.getClassroomSession(id);
    remember(restored);
    void loadCourseware(text(restored.stage?.lessonId));
    return true;
  } catch (cause) {
    localStorage.removeItem(LAST_KEY);
    lastSessionId.value = "";
    if (!quiet) error.value = failureMessage(cause);
    return false;
  } finally {
    busy.value = false;
  }
}

/**
 * Opening the classroom shows the lesson picker. A stored session is only a pointer for the
 * "继续上次课堂" button - it is never opened implicitly, so entering the classroom cannot start a
 * lesson the learner has not picked.
 */
async function bootstrap() {
  recallLastSessionId();
  await loadLessons();
  const requested = typeof route.query.session === "string" ? route.query.session.trim() : "";
  if (requested) await resume(requested, true);
}

/** Continue the session left behind by 退出课堂; a session the server forgot is simply dropped. */
async function restoreLast() {
  if (!lastSessionId.value || busy.value) return;
  await resume(lastSessionId.value, false);
}

function schedulePoll(id: string) {
  const delay = preparationPollDelayMs(pollCount);
  pollCount += 1;
  pollTimer = window.setTimeout(() => void pollPreparation(id), delay);
}

function acceptPreparation(status: ClassroomPreparationStatus) {
  if (status.state === "ready" && status.session) {
    preparing.value = false;
    remember(status.session);
    return;
  }
  if (status.state === "failed") {
    preparing.value = false;
    error.value = status.error || t("classroom.prepareFailed");
    return;
  }
  preparing.value = true;
  schedulePoll(status.id);
}

async function pollPreparation(id: string) {
  try {
    acceptPreparation(await userApi.getClassroomPreparation(id));
  } catch (cause) {
    preparing.value = false;
    error.value = failureMessage(cause);
  }
}

async function startLesson() {
  if (!lessonId.value || busy.value || preparing.value) return;
  busy.value = true;
  error.value = "";
  pollCount = 0;
  try {
    acceptPreparation(await userApi.prepareClassroom(lessonId.value));
  } catch (cause) {
    error.value = failureMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function apply(action: ClassroomAction, content?: string) {
  if (!session.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    remember(await userApi.actInClassroom(session.value.id, { action, content, expectedRevision: revision.value }));
    input.value = "";
    animation.value = null;
  } catch (cause) {
    error.value = failureMessage(cause);
    try { remember(await userApi.getClassroomSession(session.value.id)); } catch { /* keep the visible state */ }
  } finally {
    busy.value = false;
  }
}

async function submit(action: "ASK" | "ANSWER") {
  const content = input.value.trim();
  if (!content) return;
  await apply(action, content);
}

async function openAnimation() {
  if (!animationRequest.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    animation.value = await userApi.simulateAnimation(animationRequest.value);
  } catch (cause) {
    error.value = failureMessage(cause);
  } finally {
    busy.value = false;
  }
}

function resetClassroom() {
  session.value = null;
  animation.value = null;
  courseware.value = null;
  coursewareError.value = "";
  input.value = "";
  error.value = "";
  recallLastSessionId();
  void loadLessons();
}

/** Leaving mid-lesson keeps the session recallable from the picker instead of dropping it. */
function exitClassroom() {
  if (session.value) {
    localStorage.setItem(LAST_KEY, session.value.id);
    lastSessionId.value = session.value.id;
  }
  resetClassroom();
}

/** Back to the entry page without forgetting the lesson: the session stays under 继续上次课堂. */
function leaveToHome() {
  if (session.value) localStorage.setItem(LAST_KEY, session.value.id);
  void router.push("/");
}

watch(lessonId, (id) => {
  if (id && id !== courseware.value?.lessonId) void loadCourseware(id);
});
watch(() => text(stage.value.lessonId), (id) => {
  if (id && id !== courseware.value?.lessonId) void loadCourseware(id);
});
onMounted(() => void bootstrap());
onBeforeUnmount(() => {
  if (pollTimer !== null) window.clearTimeout(pollTimer);
});
</script>

<template>
  <BrandStage :wide="Boolean(session)">
    <div class="classroom" :class="{ 'classroom--split': showSlides }" aria-live="polite">
      <div class="classroom__conversation" :class="{ 'classroom__conversation--active': session }">
        <header v-if="session" class="classroom__topbar">
          <div class="classroom__topbar-text">
            <p class="classroom__topbar-title" :aria-label="t('classroom.currentLesson')">{{ lessonTitle || t("classroom.inSession") }}</p>
            <p v-if="progressLabel" class="classroom__topbar-meta">{{ progressLabel }}</p>
          </div>
          <div class="classroom__topbar-actions">
            <button class="classroom__button classroom__button--exit" type="button" @click="leaveToHome">{{ t("common.backHome") }}</button>
            <button class="classroom__button classroom__button--exit" type="button" @click="exitClassroom">{{ t("classroom.exit") }}</button>
          </div>
        </header>

        <section v-if="!session" class="classroom__start">
          <p v-if="preparing" class="classroom__status">{{ t("classroom.preparing") }}</p>
          <template v-else>
            <p class="classroom__eyebrow">{{ t("classroom.eyebrow") }}</p>
            <select v-model="lessonId" class="classroom__select" :aria-label="t('classroom.pickLesson')" :disabled="busy || lessons.length === 0">
              <option v-for="lesson in lessons" :key="lesson.id" :value="lesson.id">{{ lessonName(lesson.title) }}</option>
            </select>
            <div class="classroom__actions classroom__actions--start">
              <button class="classroom__button classroom__button--primary" type="button" :disabled="busy || !lessonId" @click="startLesson">{{ t("classroom.start") }}</button>
              <button class="classroom__button" type="button" @click="router.push(labRoute)">{{ t("classroom.lab") }}</button>
              <button v-if="lastSessionId" class="classroom__button" type="button" :disabled="busy" @click="restoreLast">{{ t("home.resume") }}</button>
              <button class="classroom__button" type="button" @click="router.push('/')">{{ t("common.backHome") }}</button>
            </div>
          </template>
        </section>

        <section v-else-if="animation" class="classroom__lesson classroom__lesson--animation">
          <AnimationPlayer :definition="animation.animationData" :trace="animation.trace" compact />
          <div class="classroom__actions">
            <button class="classroom__button" type="button" @click="animation = null">{{ t("classroom.backToLesson") }}</button>
            <button class="classroom__button classroom__button--primary" type="button" @click="router.push(labRoute)">{{ t("classroom.toLab") }}</button>
          </div>
        </section>

        <section v-else class="classroom__lesson">
          <p class="classroom__speech">{{ message }}</p>
          <p v-if="failedAttempt" class="classroom__verdict" :aria-label="t('classroom.verdict')">{{ t("classroom.answerWrong") }}</p>

          <div class="classroom__composer">
            <textarea v-if="canType" v-model="input" class="classroom__input" :placeholder="placeholder" :aria-label="t('classroom.input')" rows="1" :disabled="busy" />

            <div class="classroom__actions classroom__actions--end">
              <button v-if="animationRequest" class="classroom__button" type="button" :disabled="busy" @click="openAnimation">{{ t("classroom.demo") }}</button>
              <button class="classroom__button" type="button" @click="router.push(labRoute)">{{ t("classroom.lab") }}</button>
              <button v-if="canType" class="classroom__button" type="button" :disabled="busy || !input.trim()" @click="submit('ASK')">{{ t("classroom.ask") }}</button>
              <button v-if="interruption" class="classroom__button classroom__button--primary" type="button" :disabled="busy" @click="apply('CONTINUE')">{{ t("classroom.continue") }}</button>
              <template v-else-if="canAnswer">
                <button v-if="canHint" class="classroom__button" type="button" :disabled="busy" @click="apply('HINT')">{{ t("classroom.hint") }}</button>
                <button v-else-if="canSkip" class="classroom__button" type="button" :disabled="busy" :title="skipNote" @click="apply('SKIP')">{{ t("classroom.skipQuestion") }}</button>
                <button class="classroom__button classroom__button--primary" type="button" :disabled="busy || !input.trim()" @click="submit('ANSWER')">{{ attempts > 0 ? t("classroom.answerAgain") : t("classroom.answer") }}</button>
              </template>
              <button v-else-if="!finished" class="classroom__button classroom__button--primary" type="button" :disabled="busy" @click="apply('CONTINUE')">{{ continueLabel }}</button>
              <button v-else class="classroom__button classroom__button--primary" type="button" @click="exitClassroom">{{ t("classroom.changeLesson") }}</button>
            </div>
          </div>
        </section>

        <p v-if="error" class="classroom__error" role="alert">{{ error }}</p>
      </div>

      <SlidePanel
        v-if="showSlides"
        :courseware="courseware"
        :active-slide-id="activeSlideId"
        :match="slideMatch"
        :loading="coursewareLoading"
        :error="coursewareError"
        @open-browser="router.push('/courseware')"
      />
    </div>
  </BrandStage>
</template>

<style scoped>
/* The lesson stands on the same paper as the sign-in screen. Every surface below is one quiet card
   drawn with the same hairline, glass fill and short shadow the sign-in fields use. */
.classroom {
  --panel-line: color-mix(in srgb, var(--text) 15%, transparent);
  --panel-face: color-mix(in srgb, var(--surface) 58%, transparent);
  --panel-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 10%, transparent);
  display: grid;
  width: min(940px, 100%);
  margin: 0 auto;
  place-items: center;
  color: var(--text);
}

.classroom--split {
  width: min(1560px, 100%);
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr);
  /* Stretch, not center: centering let the left column shrink-wrap to its content and left a dead
     gutter between it and the courseware pane, with a different width in every state. */
  align-items: stretch;
  justify-items: stretch;
  gap: 24px;
}

.classroom__conversation {
  display: grid;
  align-content: center;
  min-width: 0;
  min-height: 0;
}

/* In class the column is a rail: the header row, then the teaching card filling what is left. */
.classroom__conversation--active {
  grid-template-rows: auto minmax(0, 1fr);
  align-content: stretch;
  gap: 14px;
}

@media (max-width: 1024px) {
  .classroom--split {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto auto;
  }
}

/* The start card: the same quiet surface as the teaching card, so the landing no longer reads as
   loose controls floating on the backdrop. */
.classroom__start {
  display: grid;
  width: 100%;
  max-width: 520px;
  gap: 16px;
  justify-self: center;
  padding: clamp(24px, 3vw, 34px);
  border: 1px double var(--panel-line);
  border-radius: 28px;
  background: var(--panel-face);
  box-shadow: var(--panel-shadow);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
}

.classroom__eyebrow {
  margin: 0;
  color: var(--text-muted);
  font-size: 15px;
  font-weight: 650;
  letter-spacing: .16em;
}

.classroom__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding: 8px 8px 8px 20px;
  border: 1px double var(--panel-line);
  border-radius: 999px;
  background: var(--panel-face);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
}

.classroom__topbar-text { min-width: 0; }

/* Two exits sit together: back to the entry page, or end the lesson here. */
.classroom__topbar-actions { display: inline-flex; flex: none; align-items: center; gap: 8px; }

.classroom__topbar-title {
  margin: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 16px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.classroom__topbar-meta {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: 14px;
  letter-spacing: .02em;
}

/**
 * The teaching card: one quiet surface carrying the step, instead of loose controls floating on the
 * backdrop. Everything the learner reads or touches sits inside it.
 */
.classroom__lesson {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto auto;
  gap: 16px;
  width: 100%;
  min-height: 0;
  padding: clamp(22px, 3vw, 32px);
  border: 1px double var(--panel-line);
  border-radius: 28px;
  background: var(--panel-face);
  box-shadow: var(--panel-shadow);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
}

/* Beside the courseware pane the card matches its height; the speech scrolls, the controls stay put. */
.classroom--split .classroom__lesson { height: 100%; }

/* Without a pane to pair with, the card is content-sized but the speech still cannot run away. */
.classroom:not(.classroom--split) .classroom__speech { max-height: 56dvh; }

.classroom__select,
.classroom__input,
.classroom__button {
  min-height: 48px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  font: inherit;
  font-size: 16px;
}

.classroom__select {
  width: 100%;
  min-height: 54px;
  padding: 12px 22px;
  font-size: 17px;
}

.classroom__input {
  width: 100%;
  min-height: 54px;
  padding: 14px 22px;
  border-radius: 26px;
  resize: none;
}

.classroom__input::placeholder { color: var(--text-muted); }
.classroom__select:focus-visible,
.classroom__input:focus-visible,
.classroom__button:focus-visible { border-color: var(--text); box-shadow: var(--focus-ring); }

/* Composer: the answer box gets the full row, its controls line up underneath. */
.classroom__composer {
  display: grid;
  gap: 12px;
}

/* One register for the narration, at a fixed size: the copy used to switch between a large centred
   variant and a smaller reading variant depending on how long the text was, and the size itself was
   viewport-relative - so the same step looked different from one window to the next. */
.classroom__speech,
.classroom__status {
  margin: 0;
  color: var(--text);
  font-size: 19px;
  font-weight: 400;
  letter-spacing: .01em;
  line-height: 1.85;
  text-align: left;
  text-wrap: pretty;
}

.classroom__speech {
  min-height: 0;
  overflow-y: auto;
  padding-right: 8px;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}

.classroom__error {
  position: fixed;
  right: 24px;
  bottom: 24px;
  left: 24px;
  margin: 0;
  color: var(--text);
  font-size: 19px;
  text-align: center;
}

.classroom__verdict {
  margin: 0;
  padding: 8px 18px;
  border: 1px solid var(--panel-line);
  border-radius: 999px;
  background: color-mix(in srgb, var(--text) 8%, transparent);
  color: var(--text);
  font-size: 15px;
  letter-spacing: .02em;
}

.classroom__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

.classroom__actions--start .classroom__button--primary { flex: 1; }

.classroom__actions--end { justify-content: flex-end; }

/* The player sizes itself; only its action row shares the card, so no 1fr track here. */
.classroom__lesson--animation {
  grid-template-rows: none;
  align-content: start;
  overflow-y: auto;
}

.classroom__button {
  padding: 11px 22px;
  cursor: pointer;
  white-space: nowrap;
  font-size: 19px;
  background: transparent;
  transition: background .18s ease, border-color .18s ease, color .18s ease;
}

.classroom__button:hover:not(:disabled) { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }

.classroom__button--primary {
  min-width: 116px;
  border-color: transparent;
  background: var(--text);
  color: var(--surface);
  font-weight: 620;
}

.classroom__button--primary:hover:not(:disabled) { background: var(--accent-strong); border-color: transparent; }

.classroom__button:disabled {
  cursor: default;
  opacity: .38;
}

.classroom__button--exit {
  flex: none;
  min-height: 42px;
  padding: 8px 18px;
  font-size: 15px;
}

@media (max-width: 640px) {
  .classroom__lesson { gap: 14px; padding: 20px 18px; border-radius: 24px; }
  .classroom__start { gap: 14px; padding: 22px 18px; border-radius: 24px; }
  .classroom__topbar { flex-wrap: wrap; gap: 10px; padding: 10px 10px 10px 16px; border-radius: 24px; }
  .classroom__topbar-actions { gap: 6px; }
  .classroom__button--exit { padding: 8px 12px; }
  .classroom__actions { width: 100%; }
  .classroom__actions .classroom__button { flex: 1 1 120px; }
  .classroom__actions--end .classroom__button { flex: 1 1 104px; }
}

@media (max-height: 760px) {
  .classroom__select,
  .classroom__input,
  .classroom__button { min-height: 46px; }
  .classroom__lesson { gap: 14px; padding: 20px 22px; }
}

@media (prefers-reduced-transparency: reduce) {
  .classroom__start,
  .classroom__topbar,
  .classroom__lesson { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
</style>
