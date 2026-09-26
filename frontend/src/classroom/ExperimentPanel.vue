<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "../shared/i18n/locale";
import { userApi } from "../user/runtime";
import { runCodeWithBusyRetry } from "../shared/compiler/run-with-retry";
import { createInteractiveConsole } from "../shared/compiler/interactive-console";
import type { ClassroomCodeLesson, ClassroomCodeSample, ClassroomCodeSamplesResponse, CodeRunResponse } from "../shared/types/contracts";

/**
 * Hands-on pane for the classroom: the teacher can open the textbook code that matches the page on
 * screen, let learners edit it, run it in the sandbox and restore the original listing at any time.
 *
 * Page matching is a best effort and never blocks the pane: the current slide's textbook section is
 * compared with each sample's section, then with the sample's narrower targets (3.1.2 against 3.1).
 * When nothing matches - a cover page, a summary page - the lesson's first sample is offered instead.
 */
const props = defineProps<{ coursewareKey: string; pageSection: string }>();

const { t } = useI18n();
const DRAFT_PREFIX = "structify.experiment.";

const open = ref(false);
const loaded = ref(false);
const loading = ref(false);
const loadError = ref("");
const lessons = ref<ClassroomCodeLesson[]>([]);
const selectedId = ref("");
const code = ref("");
const stdin = ref("");
const dirty = ref(false);
const running = ref(false);
const busyRetrying = ref(false);
const result = ref<CodeRunResponse | null>(null);
const failure = ref("");

/** The lesson's console runs the sample live when it can, and answers it while it runs. */
const live = createInteractiveConsole(
  {
    start: (input) => userApi.startCodeSession(input),
    stream: (sessionId, signal) => userApi.streamCodeSession(sessionId, signal),
    send: (sessionId, text) => userApi.typeInCodeSession(sessionId, text),
    stop: (sessionId) => userApi.stopCodeSession(sessionId),
  },
  t("compiler.statusNetwork"),
);
const { phase, output, errors, exitCode, awaitingInput } = live;
/** Which console is on screen: the live one, or the one-shot result of a single run. */
const mode = ref<"none" | "live" | "batch">("none");
const typed = ref("");
const promptRef = ref<HTMLInputElement | null>(null);

const pairs = computed(() => lessons.value.flatMap((lesson) => lesson.samples.map((sample) => ({ lesson, sample }))));
const lessonPairs = computed(() => pairs.value.filter((item) => item.lesson.coursewareKey === props.coursewareKey));
const otherPairs = computed(() => pairs.value.filter((item) => item.lesson.coursewareKey !== props.coursewareKey));
const current = computed(() => pairs.value.find((item) => item.sample.id === selectedId.value) ?? null);
const matched = computed(() => Boolean(current.value) && matches(current.value!.sample, props.pageSection));
const hasSamples = computed(() => pairs.value.length > 0);

/** What the console is showing: the live program's output, or the single run's result. */
const consoleText = computed(() => {
  if (mode.value === "live") {
    return output.value + (errors.value ? `\n${errors.value}` : "");
  }
  return result.value?.stdout || result.value?.stderr || "";
});

const statusText = computed(() => {
  if (mode.value === "live") {
    if (phase.value === "starting" || phase.value === "live") return t("compiler.statusLive");
    if (phase.value === "failed") return errors.value ? t("compiler.statusCompile") : t("compiler.statusRuntime");
    if (phase.value === "done") {
      return exitCode.value === 0 || exitCode.value === null
        ? t("compiler.statusSuccess")
        : t("compiler.statusRuntime");
    }
    return "";
  }
  const status = result.value?.status;
  if (!status) return "";
  if (status === "success") return t("compiler.statusSuccess");
  if (status === "compile_error") return t("compiler.statusCompile");
  return t("compiler.statusRuntime");
});

/** The button follows the console: start a program, or end the one that is running. */
const primaryLabel = computed(() => {
  if (phase.value === "live") return t("compiler.stop");
  if (running.value) return busyRetrying.value ? t("compiler.busyRetry") : t("compiler.running");
  return t("compiler.run");
});

function matches(sample: ClassroomCodeSample, section: string): boolean {
  if (!section) return false;
  if (sample.sections.includes(section)) return true;
  return sample.targets.some((target) => target && section.startsWith(target));
}

/** Longest target wins, so 3.1.2 prefers a 3.1.2 sample over a chapter-wide 3.1 one. */
function bestForSection(section: string): ClassroomCodeSample | null {
  if (!section) return null;
  const exact = lessonPairs.value.find((item) => item.sample.sections.includes(section));
  if (exact) return exact.sample;
  let best: { length: number; sample: ClassroomCodeSample } | null = null;
  for (const item of lessonPairs.value) {
    for (const target of item.sample.targets) {
      if (target && section.startsWith(target) && (!best || target.length > best.length)) {
        best = { length: target.length, sample: item.sample };
      }
    }
  }
  return best?.sample ?? null;
}

function draftKey(sampleId: string) {
  return `${DRAFT_PREFIX}${props.coursewareKey}.${sampleId}`;
}

function applySample(sample: ClassroomCodeSample, keepEdits: boolean) {
  selectedId.value = sample.id;
  code.value = keepEdits ? sample.code : readDraft(sample);
  stdin.value = sample.stdin;
  void live.stop();
  mode.value = "none";
  typed.value = "";
  result.value = null;
  failure.value = "";
  dirty.value = false;
}

function readDraft(sample: ClassroomCodeSample): string {
  try {
    const stored = window.localStorage.getItem(draftKey(sample.id));
    return stored && stored.trim() ? stored : sample.code;
  } catch {
    return sample.code;
  }
}

function writeDraft() {
  const sample = current.value?.sample;
  if (!sample) return;
  try {
    window.localStorage.setItem(draftKey(sample.id), code.value);
  } catch {
    /* a full or disabled store must not break the lesson */
  }
}

/** Follow the lesson: only a page change that the learner has not edited away from may switch samples. */
function followPage(section: string) {
  if (dirty.value || !lessonPairs.value.length) return;
  const wanted = bestForSection(section) ?? lessonPairs.value[0].sample;
  if (wanted.id !== selectedId.value) applySample(wanted, false);
}

function choose(id: string) {
  const found = pairs.value.find((item) => item.sample.id === id);
  if (!found) return;
  applySample(found.sample, false);
}

function restore() {
  const sample = current.value?.sample;
  if (!sample) return;
  code.value = sample.code;
  stdin.value = sample.stdin;
  dirty.value = false;
  void live.stop();
  mode.value = "none";
  typed.value = "";
  result.value = null;
  failure.value = "";
  try {
    window.localStorage.removeItem(draftKey(sample.id));
  } catch {
    /* ignore */
  }
}

function onCodeInput() {
  dirty.value = true;
  writeDraft();
}

async function run() {
  if (running.value) return;
  failure.value = "";
  const sample = current.value?.sample;
  if (!code.value.trim()) {
    failure.value = t("compiler.emptyCode");
    return;
  }
  running.value = true;
  result.value = null;
  try {
    // The live console comes first: a program that asks questions can only be answered while it
    // runs. Without a runner to talk to, the old one-shot run is still a working console.
    const startedLive = await live.start("c", code.value);
    if (startedLive && phase.value !== "idle") {
      mode.value = "live";
      return;
    }
    mode.value = "batch";
    result.value = await runCodeWithBusyRetry(
      (input) => userApi.runCode(input),
      { language: "c", code: code.value, stdin: stdin.value },
      { onRetry: () => { busyRetrying.value = true; } },
    );
  } catch (error) {
    failure.value = error instanceof Error ? `${t("compiler.statusNetwork")}：${error.message}` : t("compiler.statusNetwork");
  } finally {
    running.value = false;
    busyRetrying.value = false;
    if (sample) writeDraft();
  }
}

/** One button: start the program, or end the one that is already running. */
async function onPrimaryAction() {
  if (phase.value === "live") {
    await live.stop();
    return;
  }
  await run();
}

/** Enter sends the line the way a terminal does; the program reads it and carries on. */
async function sendInput() {
  if (!awaitingInput.value) return;
  const text = typed.value;
  typed.value = "";
  await live.send(text);
}

// A program that is waiting deserves the caret: put it where the learner is about to type.
watch(awaitingInput, async (waiting) => {
  if (!waiting) return;
  await nextTick();
  promptRef.value?.focus();
});

onBeforeUnmount(() => {
  void live.stop();
});

function onKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
    event.preventDefault();
    run();
  }
}

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const response: ClassroomCodeSamplesResponse = await userApi.listCodeSamples(props.coursewareKey);
    lessons.value = response.lessons;
    const wanted = bestForSection(props.pageSection) ?? lessonPairs.value[0]?.sample ?? pairs.value[0]?.sample;
    if (wanted) applySample(wanted, false);
  } catch (error) {
    // "No samples for this lesson" is the normal state for most lessons, not a failure.
    if (isMissingForLesson(error)) {
      lessons.value = [];
      return;
    }
    loadError.value = error instanceof Error ? `${t("experiment.loadFailed")}：${error.message}` : t("experiment.loadFailed");
  } finally {
    loading.value = false;
  }
}

function isMissingForLesson(error: unknown): boolean {
  const code = (error as { code?: string } | null)?.code;
  const status = (error as { status?: number } | null)?.status;
  return code === "CODE_SAMPLES_LESSON_UNKNOWN" || status === 404;
}

/** Nothing is fetched until the teacher actually opens the pane, so an unused lesson stays quiet. */
function toggle() {
  open.value = !open.value;
  if (open.value && !loaded.value && !loading.value) {
    loaded.value = true;
    load();
  }
}

onMounted(() => {
  if (open.value && !loaded.value) toggle();
});
watch(() => props.pageSection, (section) => {
  if (open.value) followPage(section);
});
watch(() => props.coursewareKey, () => {
  lessons.value = [];
  loaded.value = false;
  open.value = false;
});
</script>

<template>
  <section class="experiment">
    <button class="experiment__toggle" type="button" :aria-expanded="open" @click="toggle">
      {{ open ? t("experiment.hide") : t("experiment.show") }}
    </button>

    <div v-if="open" class="experiment__body">
      <p v-if="loading" class="experiment__hint">{{ t("common.loading") }}</p>
      <p v-else-if="loadError" class="experiment__hint experiment__hint--error" role="alert">{{ loadError }}</p>
      <p v-else-if="!hasSamples" class="experiment__hint">{{ t("experiment.none") }}</p>

      <template v-else>
        <select id="experiment-select" class="experiment__select" :aria-label="t('experiment.select')" :value="selectedId" @change="choose(($event.target as HTMLSelectElement).value)">
          <optgroup v-if="lessonPairs.length" :label="t('experiment.thisLesson')">
            <option v-for="item in lessonPairs" :key="item.sample.id" :value="item.sample.id">{{ item.sample.title }}</option>
          </optgroup>
          <optgroup v-if="otherPairs.length" :label="t('experiment.all')">
            <option v-for="item in otherPairs" :key="item.sample.id" :value="item.sample.id">
              {{ item.lesson.lessonTitle }} · {{ item.sample.title }}
            </option>
          </optgroup>
        </select>

        <p v-if="current" class="experiment__summary">
          {{ matched ? t("experiment.matched") : current.sample.summary }}
        </p>

        <textarea
          id="experiment-code"
          class="experiment__code"
          spellcheck="false"
          v-model="code"
          :aria-label="t('experiment.code')"
          :placeholder="t('experiment.codePlaceholder')"
          @input="onCodeInput"
          @keydown="onKeydown"
        ></textarea>

        <!-- While the program runs it reads what is typed, line by line, the way a terminal does. -->
        <div v-if="awaitingInput" class="experiment__prompt">
          <span class="experiment__caret" aria-hidden="true">&gt;</span>
          <input
            ref="promptRef"
            v-model="typed"
            class="experiment__stdin experiment__stdin--line"
            spellcheck="false"
            autocomplete="off"
            :aria-label="t('compiler.consoleInputLabel')"
            :placeholder="t('compiler.consoleInputLive')"
            @keydown.enter.prevent="sendInput"
          >
          <button class="experiment__send" type="button" @click="sendInput">{{ t("compiler.send") }}</button>
        </div>
        <textarea
          v-else
          id="experiment-stdin"
          class="experiment__stdin"
          spellcheck="false"
          v-model="stdin"
          :aria-label="t('experiment.stdin')"
          :placeholder="t('experiment.stdinPlaceholder')"
        ></textarea>

        <div class="experiment__actions">
          <button class="experiment__run" type="button" :disabled="running && !awaitingInput" @click="onPrimaryAction">
            {{ primaryLabel }}
          </button>
          <button class="experiment__restore" type="button" @click="restore">{{ t("experiment.restore") }}</button>
        </div>

        <p v-if="failure" class="experiment__failure" role="alert">{{ failure }}</p>

        <template v-if="consoleText">
          <p v-if="statusText" class="experiment__status">{{ statusText }}</p>
          <pre class="experiment__output" :class="{ 'experiment__output--error': mode !== 'live' && result?.stderr && !result?.stdout }">{{ consoleText }}</pre>
        </template>
        <template v-else-if="result || mode === 'live'">
          <p v-if="statusText" class="experiment__status">{{ statusText }}</p>
          <pre class="experiment__output experiment__output--empty">{{ t("compiler.noOutput") }}</pre>
        </template>
      </template>
    </div>
  </section>
</template>

<style scoped>
.experiment { display: grid; gap: 12px; }
.experiment__toggle {
  justify-self: start;
  padding: 9px 18px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 620;
}
.experiment__toggle:hover { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.experiment__body { display: grid; gap: 12px; }
.experiment__hint { margin: 0; font-size: 19px; }
.experiment__hint--error { font-weight: 620; }
.experiment__summary { margin: 0; font-size: 19px; }
.experiment__select,
.experiment__code,
.experiment__stdin {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--text) 18%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  color: var(--text);
  font: inherit;
  font-size: 19px;
}
.experiment__code,
.experiment__stdin {
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  font-size: 17px;
  line-height: 1.55;
  resize: vertical;
}
.experiment__code { min-height: 200px; }
.experiment__stdin { min-height: 56px; }
/* The live prompt is one line tall: the program answers each line as it arrives. */
.experiment__prompt { display: flex; align-items: center; gap: 8px; }
.experiment__caret { color: var(--text-muted); font-size: 18px; }
.experiment__stdin--line { flex: 1 1 auto; min-height: 34px; }
.experiment__send {
  padding: 9px 20px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 19px;
  font-weight: 620;
}
.experiment__send:hover { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.experiment__actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.experiment__run {
  padding: 11px 24px;
  border: 1px solid var(--text);
  border-radius: 999px;
  background: var(--text);
  color: var(--surface);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 620;
}
.experiment__run:disabled { cursor: default; opacity: .55; }
.experiment__restore {
  padding: 11px 20px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 620;
}
.experiment__failure,
.experiment__status { margin: 0; font-size: 19px; font-weight: 620; }
.experiment__output {
  margin: 0;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--text) 14%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  color: var(--text);
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  font-size: 18px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
}
.experiment__output--error { border-color: color-mix(in srgb, #c0392b 45%, transparent); }
.experiment__output--empty { font-family: inherit; font-size: 19px; color: var(--text-muted); }
</style>
