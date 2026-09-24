<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import BrandStage from "../shared/components/BrandStage.vue";
import { useI18n } from "../shared/i18n/locale";
import { userApi } from "../user/runtime";
import type { CodeRunResponse, TextbookCodeChapter, TextbookCodeExample } from "../shared/types/contracts";
import { runCodeWithBusyRetry } from "../shared/compiler/run-with-retry";
import { createInteractiveConsole } from "../shared/compiler/interactive-console";
import { ApiClientError } from "../shared/api";
import { compilerTemplates, type CompilerTemplate } from "./templates";

/**
 * The C editor, built as a code library: everything a learner can run lives on one page, grouped by
 * where it comes from - the runnable classroom samples, the class listings, and the five starter
 * examples the legacy editor shipped with.
 *
 * A listing is a fragment: it has no `main`, and often no types either. Loading its runnable example
 * fills all of that in and brings the input it was verified with, so pressing run really does
 * produce the expected output. Listings that cannot run at all say so instead of offering a button.
 *
 * Output and input share one console: you type what the program reads at the prompt, run, and read
 * the result in the same panel.
 */
interface LibraryEntry {
  id: string;
  title: string;
  group: "samples" | "examples" | "templates";
  origin: string;
  code: string;
  stdin: string;
  kind: "sample" | "algorithm" | "type" | "template";
  example: TextbookCodeExample | null;
  blocked: string;
}

const MAX_CODE_LENGTH = 20000;

const { t } = useI18n();
const entries = ref<LibraryEntry[]>([]);
const loading = ref(true);
const loadError = ref("");
const search = ref("");
const selectedId = ref("");
const code = ref("");
const stdin = ref("");
const dirty = ref(false);
const exampleLoaded = ref(false);
const expected = ref("");
const running = ref(false);
const busyRetrying = ref(false);
const result = ref<CodeRunResponse | null>(null);
const failure = ref("");

/**
 * The console the learner actually sees. When the interactive runner is available the program runs
 * live and answers as it is typed at; when it is not, the old one-shot run still works, so the
 * editor never ends up with a console that cannot run anything.
 */
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

const current = computed(() => entries.value.find((entry) => entry.id === selectedId.value) ?? null);
const query = computed(() => search.value.trim().toLowerCase());

const groups = computed(() => {
  const wanted = query.value;
  const matches = (entry: LibraryEntry) =>
    !wanted || `${entry.title} ${entry.origin} ${entry.id}`.toLowerCase().includes(wanted);
  return [
    { key: "samples" as const, label: t("compiler.group.samples"), items: entries.value.filter((e) => e.group === "samples" && matches(e)) },
    { key: "examples" as const, label: t("compiler.group.examples"), items: entries.value.filter((e) => e.group === "examples" && matches(e)) },
    { key: "templates" as const, label: t("compiler.group.templates"), items: entries.value.filter((e) => e.group === "templates" && matches(e)) },
  ].filter((group) => group.items.length);
});

const visibleCount = computed(() => groups.value.reduce((total, group) => total + group.items.length, 0));
const canLoadExample = computed(() => Boolean(current.value?.example) && !exampleLoaded.value);

/** One line of explanation above the code: why it cannot run, or what the example will add. */
const hint = computed(() => {
  const entry = current.value;
  if (!entry) return "";
  if (entry.blocked) return entry.blocked;
  if (entry.example && !exampleLoaded.value) return t("compiler.addExampleHint");
  return "";
});

const exampleNote = computed(() => (exampleLoaded.value ? current.value?.example?.note ?? "" : ""));

/** What the console is showing: the live program's output, or the single run's result. */
const consoleText = computed(() => {
  if (mode.value === "live") {
    const shown = output.value + (errors.value ? `\n${errors.value}` : "");
    return shown;
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

/** Did the run print exactly what the example was verified to print? */
const verdict = computed(() => {
  if (!result.value || !expected.value) return "";
  return result.value.stdout === expected.value ? t("compiler.exampleMatch") : t("compiler.exampleMismatch");
});

function fromTemplate(template: CompilerTemplate): LibraryEntry {
  return {
    id: `template-${template.id}`,
    title: t(template.labelKey),
    group: "templates",
    origin: template.file,
    code: template.code,
    stdin: template.stdin,
    kind: "template",
    example: null,
    blocked: "",
  };
}

function reset() {
  void live.stop();
  mode.value = "none";
  typed.value = "";
  result.value = null;
  failure.value = "";
  expected.value = "";
  exampleLoaded.value = false;
}

function select(entry: LibraryEntry) {
  selectedId.value = entry.id;
  code.value = entry.code;
  // A classroom sample carries the input it was verified with; a listing starts empty and gets one
  // when its example is loaded.
  stdin.value = entry.stdin;
  dirty.value = false;
  reset();
}

/** Swap the fragment for the complete program that was built and verified around it. */
function loadExample() {
  const entry = current.value;
  if (!entry?.example) return;
  code.value = entry.example.code;
  stdin.value = entry.example.stdin;
  expected.value = entry.example.expectedStdout;
  exampleLoaded.value = true;
  dirty.value = true;
  result.value = null;
  failure.value = "";
}

function restore() {
  if (!current.value) return;
  select(current.value);
}

function onCodeInput() {
  dirty.value = true;
}

function onKeydown(event: KeyboardEvent) {
  const target = event.target as HTMLTextAreaElement;
  if (event.key === "Tab") {
    event.preventDefault();
    const { selectionStart: start, selectionEnd: end, value } = target;
    target.value = `${value.slice(0, start)}  ${value.slice(end)}`;
    target.selectionStart = target.selectionEnd = start + 2;
    code.value = target.value;
  }
  if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
    event.preventDefault();
    run();
  }
}

async function run() {
  if (running.value) return;
  failure.value = "";
  if (!code.value.trim()) {
    failure.value = t("compiler.emptyCode");
    return;
  }
  if (code.value.length > MAX_CODE_LENGTH) {
    failure.value = t("compiler.tooLong");
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

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const [samples, library] = await Promise.all([userApi.listCodeSamples(), userApi.getCodeLibrary()]);
    const sampleEntries: LibraryEntry[] = samples.lessons.flatMap((lesson) =>
      lesson.samples.map((sample) => ({
        id: sample.id,
        title: sample.title,
        group: "samples" as const,
        origin: lesson.lessonTitle,
        code: sample.code,
        stdin: sample.stdin,
        kind: "sample" as const,
        example: null,
        blocked: "",
      })),
    );
    const exampleEntries: LibraryEntry[] = library.chapters.flatMap((chapter: TextbookCodeChapter) =>
      chapter.fragments.map((fragment) => ({
        id: fragment.id,
        title: fragment.title,
        group: "examples" as const,
        origin: `${chapter.title} · ${fragment.file}`,
        code: fragment.code,
        stdin: "",
        kind: fragment.kind === "type" ? ("type" as const) : ("algorithm" as const),
        example: fragment.example,
        blocked: fragment.blocked,
      })),
    );
    entries.value = [...sampleEntries, ...exampleEntries, ...compilerTemplates.map(fromTemplate)];
    const first = sampleEntries[0] ?? exampleEntries[0] ?? entries.value[0];
    if (first) select(first);
  } catch (error) {
    if (error instanceof ApiClientError && error.code === "NETWORK_TIMEOUT") {
      loadError.value = t("compiler.loadTimeout");
    } else {
      loadError.value = error instanceof Error ? `${t("compiler.loadFailed")}：${error.message}` : t("compiler.loadFailed");
    }
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <BrandStage>
    <section class="library" aria-labelledby="library-title">
      <h1 id="library-title" class="library__title">{{ t("compiler.title") }}</h1>

      <div class="library__panel">
        <p v-if="loading" class="library__hint">{{ t("common.loading") }}</p>
        <div v-else-if="loadError" class="library__failed">
          <p class="library__hint library__hint--error" role="alert">{{ loadError }}</p>
          <button class="library__retry" type="button" @click="load">{{ t("common.reload") }}</button>
        </div>

        <div v-else class="library__grid">
          <div class="library__list" role="group" :aria-label="t('compiler.search')">
            <input
              v-model="search"
              class="library__search"
              type="search"
              spellcheck="false"
              :aria-label="t('compiler.search')"
              :placeholder="t('compiler.search')"
            >
            <p v-if="!visibleCount" class="library__hint">{{ t("compiler.noMatch") }}</p>
            <div v-for="group in groups" :key="group.key" class="library__group">
              <p class="library__group-label">{{ group.label }}</p>
              <button
                v-for="entry in group.items"
                :key="entry.id"
                class="library__entry"
                :class="{ 'library__entry--active': entry.id === selectedId }"
                type="button"
                :aria-pressed="entry.id === selectedId"
                :title="entry.origin"
                @click="select(entry)"
              >
                <span class="library__entry-title">{{ entry.title }}</span>
              </button>
            </div>
          </div>

          <div class="library__work">
            <p v-if="current" class="library__origin">{{ current.origin }}</p>

            <textarea
              class="library__code"
              spellcheck="false"
              v-model="code"
              :aria-label="t('compiler.code')"
              @input="onCodeInput"
              @keydown="onKeydown"
            ></textarea>

            <p v-if="hint" class="library__hint">{{ hint }}</p>
            <p v-if="exampleNote" class="library__hint">{{ t("compiler.exampleNote") }}：{{ exampleNote }}</p>

            <!-- Input and output live in one console, the way a terminal reads: type at the prompt,
                 run, and the program's answer appears above it. -->
            <section class="console" :aria-label="t('compiler.console')">
              <div class="console__bar">
                <span class="console__label">{{ t("compiler.console") }}</span>
                <span v-if="statusText" class="console__status">{{ statusText }}</span>
              </div>
              <pre v-if="consoleText" class="console__screen" :class="{ 'console__screen--error': mode !== 'live' && result?.stderr && !result?.stdout }">{{ consoleText }}</pre>
              <p v-else class="console__screen console__screen--idle">{{ t("compiler.consoleIdle") }}</p>
              <p v-if="verdict" class="console__verdict">{{ verdict }}</p>
              <pre v-if="verdict && expected && result?.stdout !== expected" class="console__expected">{{ expected }}</pre>

              <!-- While the program runs it reads what is typed, line by line, the way a terminal does. -->
              <div v-if="awaitingInput" class="console__prompt">
                <span class="console__caret" aria-hidden="true">&gt;</span>
                <input
                  ref="promptRef"
                  v-model="typed"
                  class="console__input console__input--line"
                  spellcheck="false"
                  autocomplete="off"
                  :aria-label="t('compiler.consoleInputLabel')"
                  :placeholder="t('compiler.consoleInputLive')"
                  @keydown.enter.prevent="sendInput"
                >
                <button class="console__send" type="button" @click="sendInput">{{ t("compiler.send") }}</button>
              </div>
              <div v-else class="console__prompt">
                <span class="console__caret" aria-hidden="true">&gt;</span>
                <textarea
                  v-model="stdin"
                  class="console__input"
                  spellcheck="false"
                  :aria-label="t('compiler.consoleInputLabel')"
                  :placeholder="t('compiler.consoleInput')"
                ></textarea>
              </div>
            </section>

            <p v-if="failure" class="library__failure" role="alert">{{ failure }}</p>

            <div class="library__actions">
              <button class="library__run" type="button" :disabled="running && !awaitingInput" @click="onPrimaryAction">
                {{ primaryLabel }}
              </button>
              <button v-if="canLoadExample" class="library__chip" type="button" @click="loadExample">
                {{ t("compiler.addExample") }}
              </button>
              <button v-if="dirty" class="library__chip" type="button" @click="restore">{{ t("experiment.restore") }}</button>
            </div>
          </div>
        </div>
      </div>

      <nav class="library__links">
        <RouterLink class="library__home" to="/">{{ t("common.backHome") }}</RouterLink>
      </nav>
    </section>
  </BrandStage>
</template>

<style scoped>
.library { display: grid; width: min(100%, 1180px); gap: 20px; }
.library__title { margin: 0; color: var(--text); font-family: var(--font-ui); font-size: clamp(34px, 4vw, 46px); font-weight: 400; line-height: 1.06; }

.library__panel {
  display: grid;
  gap: 14px;
  padding: 20px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 26px;
  background: color-mix(in srgb, var(--surface) 58%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 10%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
  color: var(--text);
}

/* Two panes: the library on the left, the code the learner is looking at on the right. */
.library__grid { display: grid; grid-template-columns: minmax(240px, 320px) minmax(0, 1fr); gap: 18px; align-items: start; }

.library__list { display: grid; gap: 8px; align-content: start; max-height: min(64dvh, 640px); overflow-y: auto; padding-right: 4px; }
.library__search,
.library__code {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--text) 18%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  color: var(--text);
  font: inherit;
  font-size: 19px;
}
.library__group { display: grid; gap: 6px; }
.library__group-label { margin: 6px 0 0; font-size: 19px; font-weight: 620; }
.library__entry {
  padding: 10px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  text-align: left;
}
.library__entry:hover { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.library__entry--active { background: var(--text); border-color: var(--text); color: var(--surface); }
.library__entry-title { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.library__work { display: grid; gap: 12px; min-width: 0; }
.library__origin { margin: 0; font-size: 19px; font-weight: 620; }
.library__hint { margin: 0; font-size: 19px; line-height: 1.5; }
.library__hint--error { font-weight: 620; }
.library__failed { display: grid; gap: 14px; justify-items: start; }
.library__retry {
  padding: 9px 20px;
  border: 1px solid var(--text);
  border-radius: 999px;
  background: var(--text);
  color: var(--stage, #0d0d0c);
  font: inherit;
  font-size: 17px;
  font-weight: 620;
  cursor: pointer;
}
.library__retry:hover { opacity: .88; }
.library__code { min-height: 300px; font-family: var(--font-mono, ui-monospace, Consolas, monospace); font-size: 17px; line-height: 1.55; resize: vertical; }
.library__failure { margin: 0; font-size: 19px; font-weight: 620; }

/* The console: one panel, terminal-shaped - the program's output on top, your input at the prompt. */
.console {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, var(--text) 18%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--surface) 74%, transparent);
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
}
.console__bar { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.console__label { font-family: var(--font-ui); font-size: 19px; font-weight: 620; }
.console__status { font-family: var(--font-ui); font-size: 19px; font-weight: 620; color: var(--text-muted); }
.console__screen {
  margin: 0;
  min-height: 88px;
  max-height: min(40dvh, 420px);
  overflow: auto;
  color: var(--text);
  font-size: 18px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.console__screen--error { color: color-mix(in srgb, #c0392b 85%, var(--text)); }
.console__screen--idle { font-family: var(--font-ui); font-size: 19px; color: var(--text-muted); }
.console__verdict { margin: 0; font-family: var(--font-ui); font-size: 19px; font-weight: 620; }
.console__expected {
  margin: 0;
  padding: 10px 12px;
  border: 1px dashed color-mix(in srgb, var(--text) 22%, transparent);
  border-radius: 12px;
  color: var(--text-muted);
  font-size: 18px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.console__prompt { display: flex; gap: 8px; align-items: flex-start; border-top: 1px solid color-mix(in srgb, var(--text) 14%, transparent); padding-top: 10px; }
.console__caret { color: var(--text-muted); font-size: 18px; line-height: 1.6; }
.console__input {
  flex: 1 1 auto;
  min-height: 56px;
  max-height: 180px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 18px;
  line-height: 1.6;
  resize: vertical;
}
.console__input:focus { outline: none; }
.console__input::placeholder { color: var(--text-muted); }
/* The live prompt is one line tall: the program answers each line as it arrives. */
.console__input--line { flex: 1 1 auto; min-height: 34px; max-height: none; align-self: center; resize: none; }
.console__send {
  align-self: center;
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
.console__send:hover { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }

.library__actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.library__run {
  padding: 12px 26px;
  border: 1px solid var(--text);
  border-radius: 999px;
  background: var(--text);
  color: var(--surface);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 620;
}
.library__run:disabled { cursor: default; opacity: .55; }
.library__chip {
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
.library__chip:disabled { cursor: default; opacity: .38; }
.library__chip:hover:not(:disabled) { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }

.library__links { display: flex; justify-content: center; }
.library__home { color: var(--text-muted); font-size: 19px; font-weight: 620; text-decoration: none; }
.library__home:hover { color: var(--text); text-decoration: underline; text-underline-offset: 4px; }

@media (max-width: 900px) {
  .library__grid { grid-template-columns: minmax(0, 1fr); }
  .library__list { max-height: 300px; }
}

@media (prefers-reduced-transparency: reduce) {
  .library__panel { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
</style>
