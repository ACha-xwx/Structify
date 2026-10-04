<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import BrandStage from "../shared/components/BrandStage.vue";
import AiTitle from "../shared/components/AiTitle.vue";
import AnimationPlayer from "../animation/AnimationPlayer.vue";
import type { ClassroomAction, ClassroomSession, ClassroomSlideMatch, DsvpRequest, DsvpSimulationResponse, LessonCourseware } from "../shared/types/contracts";
import { useI18n } from "../shared/i18n/locale";
import { userApi } from "../user/runtime";
import type { ClassroomLesson, ClassroomPreparationStatus } from "../user/api";
import { preparationPollDelayMs } from "./preparation-poll";
import SlidePanel from "./SlidePanel.vue";
import { LOCAL_DEMO_LESSON_ID, localDemoCourseware, localDemoLesson } from "./demo-courseware";
import RuntimeSelect, { type RuntimeSelectOption } from "../shared/components/RuntimeSelect.vue";
import homeIcon from "../assets/classroom/home.svg";
import exitIcon from "../assets/classroom/exit.svg";
import LiquidMetalButton from "../admin/components/LiquidMetalButton.vue";

/**
 * The most recent session, kept after leaving so the entry page (and this picker) can offer to
 * continue it. Nothing here resumes a lesson on its own: opening the classroom shows the picker
 * unless a session was asked for by id, because a learner who has not chosen a lesson yet should
 * not land in the middle of one.
 */
const LAST_KEY = "structify.classroom.last";
const SELECTED_COURSEWARE_KEY = "structify.courseware.selected";
type SelectedCourseware = { deckId: string; title: string; chapter: string; lessonIds: string[] };
const { t } = useI18n();
const lessons = ref<ClassroomLesson[]>([]);
const lessonsLoading = ref(true);
const lessonId = ref("");
const session = ref<ClassroomSession | null>(null);
const lastSessionId = ref("");
const input = ref("");
const displayedMessage = ref("");
const messageTyping = ref(false);
const busy = ref(false);
const preparing = ref(false);
const error = ref("");
const animation = ref<DsvpSimulationResponse | null>(null);
const courseware = ref<LessonCourseware | null>(null);
const coursewareLoading = ref(false);
const coursewareError = ref("");
const selectedCourseware = ref<SelectedCourseware | null>(null);
const route = useRoute();
const router = useRouter();
const localDemoEnabled = import.meta.env.DEV;
let pollTimer: number | null = null;
let messageTypingTimer: number | undefined;
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
/**
 * Keep the courseware stage visible even when the offline presentation bundle is unavailable. The
 * classroom layout is still useful for UI work, and the slide pane owns the honest empty state.
 */
const showSlides = computed(() => true);
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
const canStartLesson = computed(() => Boolean(lessonId.value) && lessonId.value !== LOCAL_DEMO_LESSON_ID);
const lessonGroups = computed(() => {
  const groups = new Map<string, { value: string; label: string; options: RuntimeSelectOption[] }>();
  for (const lesson of lessons.value) {
    const { chapter, topic } = splitLessonTitle(lesson.title);
    const key = lesson.chapterId || chapter;
    let group = groups.get(key);
    if (!group) {
      group = { value: key, label: chapter, options: [] };
      groups.set(key, group);
    }
    group.options.push({ value: lesson.id, label: topic });
  }
  return [...groups.values()];
});
const chapterOptions = computed<RuntimeSelectOption[]>(() => lessonGroups.value.map(({ value, label }) => ({ value, label })));
// Derive the chapter from the real lesson id so saved courseware and picker changes stay in sync.
const selectedChapterId = computed({
  get: () => lessonGroups.value.find((group) => group.options.some((option) => option.value === lessonId.value))?.value ?? "",
  set: (value: string | number) => {
    const firstTopic = lessonGroups.value.find((group) => group.value === value)?.options[0];
    if (firstTopic) lessonId.value = String(firstTopic.value);
  },
});
const lessonOptions = computed<RuntimeSelectOption[]>(() =>
  lessonGroups.value.find((group) => group.value === selectedChapterId.value)?.options ?? [],
);

function displayMessageCharacterByCharacter(next: string) {
  if (messageTypingTimer !== undefined) window.clearTimeout(messageTypingTimer);
  messageTypingTimer = undefined;
  displayedMessage.value = "";

  if (!next) {
    messageTyping.value = false;
    return;
  }

  const reduceMotion = typeof window === "undefined"
    || window.matchMedia?.("(prefers-reduced-motion: reduce)").matches === true;
  if (reduceMotion || import.meta.env.MODE === "test") {
    displayedMessage.value = next;
    messageTyping.value = false;
    return;
  }

  const characters = Array.from(next);
  let index = 0;
  messageTyping.value = true;

  const revealNextCharacter = () => {
    displayedMessage.value += characters[index] ?? "";
    index += 1;
    if (index >= characters.length) {
      messageTyping.value = false;
      messageTypingTimer = undefined;
      return;
    }
    messageTypingTimer = window.setTimeout(revealNextCharacter, 24);
  };

  messageTypingTimer = window.setTimeout(revealNextCharacter, 120);
}

watch(
  () => [message.value, session.value?.id, revision.value] as const,
  ([next]) => displayMessageCharacterByCharacter(next),
  { immediate: true },
);

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string" && Boolean(item.trim())) : [];
}

/** Return a stable chapter number from API ids, numeric metadata, or Chinese deck titles. */
function chapterNumber(value: unknown): number | null {
  if (typeof value !== "string") return null;
  const textValue = value.trim();
  const chinese = textValue.match(/第([零〇一二两三四五六七八九十百]+)章/);
  if (chinese) {
    const digits: Record<string, number> = { 零: 0, 〇: 0, 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 };
    let total = 0;
    let section = 0;
    for (const character of chinese[1]) {
      if (character === "十" || character === "百") {
        const unit = character === "百" ? 100 : 10;
        section = (section || 1) * unit;
        total += section;
        section = 0;
      } else {
        section = digits[character] ?? 0;
      }
    }
    return total + section;
  }
  const numeric = textValue.match(/(?:^|[^0-9])0*(\d{1,3})(?=[^0-9]|$)/);
  return numeric ? Number(numeric[1]) : null;
}

function findCoursewareLessonId(selection: SelectedCourseware | null, availableLessons: ClassroomLesson[]): string {
  if (!selection) return "";
  const directMatch = selection.lessonIds.find((id) => availableLessons.some((lesson) => lesson.id === id));
  if (directMatch) return directMatch;

  const selectedChapter = chapterNumber(selection.chapter) ?? chapterNumber(selection.title) ?? chapterNumber(selection.deckId);
  if (selectedChapter === null) return "";
  return availableLessons.find((lesson) => chapterNumber(lesson.chapterId) === selectedChapter)?.id ?? "";
}

function readSelectedCourseware(): SelectedCourseware | null {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(SELECTED_COURSEWARE_KEY) ?? "null");
    const recordValue = record(value);
    if (!recordValue || typeof recordValue.deckId !== "string" || typeof recordValue.title !== "string") return null;
    return {
      deckId: recordValue.deckId,
      title: recordValue.title,
      chapter: text(recordValue.chapter),
      lessonIds: stringList(recordValue.lessonIds),
    };
  } catch {
    return null;
  }
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

function splitLessonTitle(title: string): { chapter: string; topic: string } {
  const name = lessonName(title);
  const parts = name.match(/^(.+?)\s*[-—–－]\s*(.+)$/);
  return parts
    ? { chapter: parts[1].trim(), topic: parts[2].trim() }
    : { chapter: name, topic: name };
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
  lessonsLoading.value = true;
  busy.value = true;
  error.value = "";
  try {
    selectedCourseware.value = readSelectedCourseware();
    lessons.value = await userApi.listClassroomLessons();
    if (!lessons.value.length && localDemoEnabled) {
      lessons.value = [localDemoLesson];
      lessonId.value = LOCAL_DEMO_LESSON_ID;
      courseware.value = localDemoCourseware;
    }
    const preferredLessonId = findCoursewareLessonId(selectedCourseware.value, lessons.value);
    lessonId.value = preferredLessonId || lessonId.value || lessons.value[0]?.id || "";
  } catch (cause) {
    error.value = failureMessage(cause);
  } finally {
    lessonsLoading.value = false;
    busy.value = false;
  }
}

async function loadCourseware(id: string) {
  if (!id) return;
  coursewareLoading.value = true;
  coursewareError.value = "";
  try {
    courseware.value = await userApi.getLessonCourseware(id);
    if (localDemoEnabled && (id === LOCAL_DEMO_LESSON_ID || !courseware.value.ready)) {
      courseware.value = localDemoCourseware;
    }
  } catch (cause) {
    if (localDemoEnabled && id === LOCAL_DEMO_LESSON_ID) {
      courseware.value = localDemoCourseware;
    } else {
      courseware.value = null;
      coursewareError.value = failureMessage(cause);
    }
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
  void router.push("/begin");
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
  if (messageTypingTimer !== undefined) window.clearTimeout(messageTypingTimer);
});
</script>

<template>
  <BrandStage wide fixed>
    <div class="classroom-page">
      <AiTitle><h1 class="workbench-title">{{ t("classroom.title") }}</h1></AiTitle>
    <div class="classroom" :class="{ 'classroom--split': showSlides && session, 'classroom--courseware': showSlides && !session }" aria-live="polite">
      <div v-if="session || !showSlides" class="classroom__conversation" :class="{ 'classroom__conversation--active': session }">
        <header v-if="session" class="classroom__topbar">
          <div class="classroom__topbar-text">
            <p class="classroom__topbar-title" :aria-label="t('classroom.currentLesson')">{{ lessonTitle || t("classroom.inSession") }}</p>
            <p v-if="progressLabel" class="classroom__topbar-meta">{{ progressLabel }}</p>
          </div>
        </header>

        <section v-if="!session" class="classroom__start">
          <p v-if="preparing" class="classroom__status">{{ t("classroom.preparing") }}</p>
          <template v-else>
            <div class="classroom__lesson-pickers">
              <RuntimeSelect
                v-model="selectedChapterId"
                class-name="classroom__chapter-select"
                variant="reference"
                :options="chapterOptions"
                :ariaLabel="t('classroom.pickChapter')"
                :disabled="busy || lessons.length === 0"
              />
              <RuntimeSelect
                v-model="lessonId"
                class-name="classroom__lesson-select"
                variant="reference"
                :options="lessonOptions"
                :ariaLabel="t('classroom.pickLesson')"
                :disabled="busy || lessons.length === 0"
              />
            </div>
            <div class="classroom__actions classroom__actions--start">
              <LiquidMetalButton class="classroom__silver-start" :disabled="busy || !canStartLesson" :aria-label="t('classroom.start')" @click="startLesson">{{ t("classroom.start") }}</LiquidMetalButton>
              <LiquidMetalButton v-if="lastSessionId" class="classroom__silver-start classroom__silver-resume" :disabled="busy" :aria-label="t('home.resume')" @click="restoreLast">{{ t("home.resume") }}</LiquidMetalButton>
              <LiquidMetalButton class="classroom__silver-icon" view-mode="icon" :aria-label="t('common.backHome')" :title="t('common.backHome')" @click="router.push('/begin')">
                <template #icon><img class="classroom__icon" :src="homeIcon" alt="" aria-hidden="true"></template>
                <span class="classroom__icon-label">{{ t("common.backHome") }}</span>
              </LiquidMetalButton>
            </div>
          </template>
        </section>

        <section v-else-if="animation" class="classroom__lesson classroom__lesson--animation">
          <AnimationPlayer :definition="animation.animationData" :trace="animation.trace" compact />
          <div class="classroom__actions">
            <LiquidMetalButton class="classroom__silver-action" @click="animation = null">{{ t("classroom.backToLesson") }}</LiquidMetalButton>
          </div>
        </section>

        <section v-else class="classroom__lesson">
          <p class="classroom__speech" aria-hidden="true">
            {{ displayedMessage }}<span v-if="messageTyping" class="classroom__typing-cursor" aria-hidden="true"></span>
          </p>
          <p class="classroom__speech-announcement" role="status">{{ messageTyping ? "" : message }}</p>
          <p v-if="failedAttempt" class="classroom__verdict" :aria-label="t('classroom.verdict')">{{ t("classroom.answerWrong") }}</p>

          <div class="classroom__composer">
            <textarea v-if="canType" v-model="input" class="classroom__input" :placeholder="placeholder" :aria-label="t('classroom.input')" rows="1" :disabled="busy" />

            <div class="classroom__actions classroom__actions--end">
              <LiquidMetalButton v-if="animationRequest" class="classroom__silver-action" :disabled="busy" @click="openAnimation">{{ t("classroom.demo") }}</LiquidMetalButton>
              <LiquidMetalButton v-if="canType" class="classroom__silver-action" :disabled="busy || !input.trim()" :aria-label="t('classroom.ask')" @click="submit('ASK')">{{ t("classroom.ask") }}</LiquidMetalButton>
              <LiquidMetalButton v-if="interruption" class="classroom__silver-action" :disabled="busy" @click="apply('CONTINUE')">{{ t("classroom.continue") }}</LiquidMetalButton>
              <template v-else-if="canAnswer">
                <LiquidMetalButton v-if="canHint" class="classroom__silver-action" :disabled="busy" @click="apply('HINT')">{{ t("classroom.hint") }}</LiquidMetalButton>
                <LiquidMetalButton v-else-if="canSkip" class="classroom__silver-action" :disabled="busy" :title="skipNote" @click="apply('SKIP')">{{ t("classroom.skipQuestion") }}</LiquidMetalButton>
                <LiquidMetalButton class="classroom__silver-action" :disabled="busy || !input.trim()" @click="submit('ANSWER')">{{ attempts > 0 ? t("classroom.answerAgain") : t("classroom.answer") }}</LiquidMetalButton>
              </template>
              <LiquidMetalButton v-else-if="!finished && session?.state === 'OPENING'" class="classroom__silver-action classroom__silver-start" :disabled="busy" :aria-label="continueLabel" @click="apply('CONTINUE')">{{ continueLabel }}</LiquidMetalButton>
              <LiquidMetalButton v-else-if="!finished" class="classroom__silver-action" :disabled="busy" @click="apply('CONTINUE')">{{ continueLabel }}</LiquidMetalButton>
              <LiquidMetalButton v-else class="classroom__silver-action" @click="exitClassroom">{{ t("classroom.changeLesson") }}</LiquidMetalButton>
            </div>
          </div>
        </section>

        <p v-if="error" class="classroom__error" role="alert">{{ error }}</p>
      </div>

      <SlidePanel
        v-if="showSlides"
        fit
        :class="{ 'slides--courseware': !session }"
        :courseware="courseware"
        :active-slide-id="activeSlideId"
        :match="slideMatch"
        :loading="lessonsLoading || coursewareLoading"
        :error="coursewareError"
        @open-browser="router.push('/courseware')"
      >
        <template #headerActions>
          <template v-if="session">
            <button class="classroom__icon-button classroom__icon-button--glass" type="button" :aria-label="t('classroom.exit')" :title="t('classroom.exit')" @click="exitClassroom">
              <img class="classroom__icon" :src="exitIcon" alt="" aria-hidden="true">
              <span class="classroom__icon-label">{{ t("classroom.exit") }}</span>
            </button>
          </template>
          <button class="classroom__icon-button classroom__icon-button--glass" type="button" :aria-label="t('common.backHome')" :title="t('common.backHome')" @click="leaveToHome">
            <img class="classroom__icon" :src="homeIcon" alt="" aria-hidden="true">
            <span class="classroom__icon-label">{{ t("common.backHome") }}</span>
          </button>
        </template>
        <template v-if="!session" #controls>
          <div class="classroom__courseware-controls">
            <div class="classroom__lesson-pickers">
              <RuntimeSelect
                v-model="selectedChapterId"
                class-name="classroom__chapter-select"
                variant="reference"
                :options="chapterOptions"
                :ariaLabel="t('classroom.pickChapter')"
                :disabled="busy || preparing || lessons.length === 0"
              />
              <RuntimeSelect
                v-model="lessonId"
                class-name="classroom__lesson-select"
                variant="reference"
                :options="lessonOptions"
                :ariaLabel="t('classroom.pickLesson')"
                :disabled="busy || preparing || lessons.length === 0"
              />
            </div>
          </div>
        </template>
        <template #footerActions>
          <LiquidMetalButton v-if="!session && lastSessionId" class="classroom__silver-start classroom__silver-resume" :disabled="busy" :aria-label="t('home.resume')" @click="restoreLast">{{ t("home.resume") }}</LiquidMetalButton>
          <LiquidMetalButton v-if="!session" class="classroom__silver-start" :disabled="busy || preparing || !canStartLesson" :aria-label="t('classroom.start')" @click="startLesson">{{ t("classroom.start") }}</LiquidMetalButton>
        </template>
      </SlidePanel>
      <div v-if="preparing" class="classroom__preparing-overlay" role="dialog" aria-modal="true" :aria-label="t('classroom.preparing')">
        <div class="classroom__preparing-dialog">
          <span class="classroom__preparing-title">{{ t("classroom.preparing") }}</span>
        </div>
      </div>
    </div>
    </div>
  </BrandStage>
</template>

<style scoped>
/* The lesson stands on the same paper as the sign-in screen. Every surface below is one quiet card
   drawn with the same hairline, glass fill and short shadow the sign-in fields use. */
.classroom-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  margin: 0 auto;
  gap: 22px;
}

.classroom {
  --panel-line: color-mix(in srgb, var(--text) 15%, transparent);
  --panel-face: color-mix(in srgb, var(--surface) 58%, transparent);
  --panel-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 10%, transparent);
  display: grid;
  width: 100%;
  height: 100%;
  min-height: 0;
  margin: 0 auto;
  align-items: stretch;
  justify-items: stretch;
  color: var(--text);
}

.classroom--split {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.12fr);
  /* Stretch, not center: centering let the left column shrink-wrap to its content and left a dead
     gutter between it and the courseware pane, with a different width in every state. */
  align-items: stretch;
  justify-items: stretch;
  gap: 20px;
}

.classroom--courseware {
  align-items: stretch;
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

.classroom__courseware-controls {
  display: grid;
  gap: 12px;
  padding: 2px 0 4px;
}

.classroom--courseware .classroom__courseware-controls {
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
}

.classroom--courseware .classroom__courseware-controls .classroom__actions--start {
  grid-column: 1 / -1;
  justify-content: flex-start;
}

@media (max-width: 1024px) {
  .classroom--split {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  }

  .classroom--courseware .classroom__courseware-controls {
    grid-template-columns: 1fr;
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
.classroom__input {
  min-height: 48px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  font: inherit;
  font-size: 16px;
}

.classroom__lesson-pickers {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  gap: 12px;
  min-width: 0;
}

.classroom__chapter-select,
.classroom__lesson-select { width: 100%; min-width: 0; }

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
.classroom__input:focus-visible { border-color: var(--text); box-shadow: var(--focus-ring); }

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

.classroom__typing-cursor {
  display: inline-block;
  width: 2px;
  height: 1.05em;
  margin-left: 3px;
  vertical-align: -0.12em;
  background: currentColor;
  animation: classroom-typing-cursor 820ms steps(1, end) infinite;
}

.classroom__speech-announcement {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}

@keyframes classroom-typing-cursor {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0; }
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

.classroom__actions--end { justify-content: flex-end; }

/* The player sizes itself; only its action row shares the card, so no 1fr track here. */
.classroom__lesson--animation {
  grid-template-rows: none;
  align-content: start;
  overflow-y: auto;
}

/* The start action uses the complete liquid-metal component from the silver-button specification. */
.classroom__silver-start {
  --liquid-width: 142px;
  --liquid-height: 50px;
  flex: 0 0 auto;
}

.classroom__silver-resume { --liquid-width: 190px; }

.classroom__silver-icon {
  --liquid-width: 50px;
  --liquid-height: 50px;
  flex: 0 0 50px;
}

.classroom__silver-action {
  --liquid-width: 142px;
  --liquid-height: 50px;
  flex: 0 0 auto;
}

.classroom__silver-start :deep(.liquid-metal-button__content-layer) {
  font-size: 18px;
  font-weight: 700;
}

.classroom__silver-action :deep(.liquid-metal-button__content-layer) {
  font-size: 18px;
  font-weight: 700;
}

.classroom__icon-button {
  display: inline-grid;
  width: 50px;
  height: 50px;
  min-width: 50px;
  min-height: 50px;
  padding: 0;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 50%;
  cursor: pointer;
  font: inherit;
  transition: transform 180ms ease, background 180ms ease, border-color 180ms ease, box-shadow 180ms ease, filter 180ms ease;
}

.classroom__icon-button--glass {
  border-color: color-mix(in srgb, var(--text) 10%, transparent);
  background: color-mix(in srgb, var(--surface) 38%, transparent);
  box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset 0 0 2px color-mix(in srgb, var(--text) 32%, transparent), 0 4px 8px color-mix(in srgb, var(--text) 17%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.14);
  backdrop-filter: blur(7px) saturate(1.14);
}

.classroom__icon-button:hover:not(:disabled) { transform: translateY(-1px) scale(1.04); filter: brightness(1.06); }
.classroom__icon-button:focus-visible { border-color: var(--text); box-shadow: var(--focus-ring); }
.classroom__icon-button:disabled { cursor: default; opacity: .38; }
.classroom__icon { width: 24px; height: 24px; display: block; }
.classroom__icon-label {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

:global([data-theme="dark"]) .classroom__icon { filter: invert(1); }

.classroom__preparing-overlay {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: color-mix(in srgb, var(--text) 18%, transparent);
  -webkit-backdrop-filter: blur(3px);
  backdrop-filter: blur(3px);
}

.classroom__preparing-dialog {
  display: grid;
  min-width: min(300px, 100%);
  min-height: 132px;
  place-items: center;
  padding: 30px 42px;
  border: 1px double var(--panel-line);
  border-radius: 24px;
  background: color-mix(in srgb, var(--surface) 84%, transparent);
  box-shadow: var(--panel-shadow);
  -webkit-backdrop-filter: blur(14px) saturate(1.12);
  backdrop-filter: blur(14px) saturate(1.12);
}

.classroom__preparing-title {
  color: var(--text);
  font-size: 22px;
  font-weight: 700;
}

@media (max-width: 640px) {
  .classroom__lesson-pickers { grid-template-columns: minmax(0, 1fr); }
  .classroom__lesson { gap: 14px; padding: 20px 18px; border-radius: 24px; }
  .classroom__start { gap: 14px; padding: 22px 18px; border-radius: 24px; }
  .classroom__topbar { flex-wrap: wrap; gap: 10px; padding: 10px 10px 10px 16px; border-radius: 24px; }
  .classroom__topbar-actions { gap: 6px; }
  .classroom__actions { width: 100%; }
}

@media (max-height: 760px) {
  .classroom__select,
  .classroom__input { min-height: 46px; }
  .classroom__lesson { gap: 14px; padding: 20px 22px; }
}

@media (prefers-reduced-transparency: reduce) {
  .classroom__start,
  .classroom__topbar,
  .classroom__lesson { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}

@media (prefers-reduced-motion: reduce) {
  .classroom__typing-cursor { animation: none; }
}
</style>
