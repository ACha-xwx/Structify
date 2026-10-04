<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { FileCode, FilePlus, RotateCcw } from "@lucide/vue";
import BrandStage from "../shared/components/BrandStage.vue";
import LiquidMetalButton from "../admin/components/LiquidMetalButton.vue";
import homeIcon from "../assets/classroom/home.svg";
import downloadIcon from "../assets/compiler/download.svg";
import { useI18n } from "../shared/i18n/locale";
import { userApi } from "../user/runtime";
import type { CodeRunResponse, TextbookCodeChapter } from "../shared/types/contracts";
import { runCodeWithBusyRetry } from "../shared/compiler/run-with-retry";
import { createInteractiveConsole } from "../shared/compiler/interactive-console";
import { ApiClientError } from "../shared/api";
import { compilerTemplates, type CompilerTemplate } from "./templates";
import CodeLibraryMenu from "./CodeLibraryMenu.vue";
import CCodeEditor from "./CCodeEditor.vue";
import { buildLibraryGroups, classifyCode, type LibraryEntry } from "./library-catalog";
import { cFileName, cSourceFileName, decodeCSource } from "./code-files";

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
const MAX_CODE_LENGTH = 20000;

const { t, locale } = useI18n();
const entries = ref<LibraryEntry[]>([]);
const loading = ref(true);
const loadError = ref("");
const search = ref("");
const selectedId = ref("");
const code = ref("");
const documentKey = ref(0);
const fileInput = ref<HTMLInputElement | null>(null);
const importedName = ref("");
const fileError = ref("");
let fileRevision = 0;
let importRequest = 0;
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
const fileName = computed(() => importedName.value || cFileName(current.value?.sourceFile || "main.c"));
const exportFileName = computed(() => importedName.value || cSourceFileName(current.value?.sourceFile || "main.c"));
const groups = computed(() => buildLibraryGroups(entries.value, search.value, locale.value));
const visibleCount = computed(() => groups.value.reduce((total, group) => total + group.count, 0));
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
    sourceFile: template.file,
    chapterId: "",
    topicId: "",
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
  importedName.value = "";
  fileError.value = "";
  documentKey.value++;
  selectedId.value = entry.id;
  code.value = entry.code;
  // A classroom sample carries the input it was verified with; a listing starts empty and gets one
  // when its example is loaded.
  stdin.value = entry.stdin;
  dirty.value = false;
  reset();
}

function selectById(id: string) {
  const entry = entries.value.find((item) => item.id === id);
  if (entry) select(entry);
}

/** Start an untitled document without changing the selected code library entry. */
function newBlank() {
  importedName.value = "";
  fileError.value = "";
  documentKey.value++;
  selectedId.value = "";
  code.value = "";
  stdin.value = "";
  dirty.value = false;
  reset();
}

function chooseFile() {
  fileInput.value?.click();
}

async function importCode(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  const request = ++importRequest;
  const revision = fileRevision;
  fileError.value = "";
  if (!/\.(c|h)$/i.test(file.name)) {
    fileError.value = t("compiler.invalidFile");
    return;
  }
  try {
    const source = decodeCSource(await file.arrayBuffer());
    if (request !== importRequest) return;
    if (revision !== fileRevision) {
      fileError.value = t("compiler.editChanged");
      return;
    }
    newBlank();
    importedName.value = file.name;
    code.value = source;
  } catch {
    if (request === importRequest) fileError.value = t("compiler.importFailed");
  }
}

function exportCode() {
  fileError.value = "";
  let url: string | undefined;
  const link = document.createElement("a");
  try {
    url = URL.createObjectURL(new Blob([code.value], { type: "text/x-c;charset=utf-8" }));
    link.href = url;
    link.download = exportFileName.value;
    document.body.append(link);
    link.click();
  } catch {
    fileError.value = t("compiler.exportFailed");
  } finally {
    link.remove();
    // Allow the browser to start reading the download before releasing its URL.
    if (url) {
      const downloadUrl = url;
      setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000);
    }
  }
}

watch([documentKey, code], () => { fileRevision++; }, { flush: "sync" });

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
  fileError.value = "";
  dirty.value = true;
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
  importRequest++;
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
        sourceFile: sample.sourceFile,
        ...classifyCode(lesson.chapterId, sample.sourceFile, sample.title, sample.id),
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
        sourceFile: fragment.file,
        ...classifyCode(chapter.chapter, fragment.file, fragment.title, fragment.id),
        code: fragment.code,
        stdin: "",
        kind: fragment.kind === "type" ? ("type" as const) : ("algorithm" as const),
        example: fragment.example,
        blocked: fragment.blocked,
      })),
    );
    entries.value = [...sampleEntries, ...exampleEntries, ...compilerTemplates.map(fromTemplate)];
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
  <BrandStage wide fixed>
    <section class="library" aria-labelledby="library-title">
      <h1 id="library-title" class="workbench-title">{{ t("compiler.title") }}</h1>

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
            <div class="library__catalog">
              <p v-if="!visibleCount" class="library__hint">{{ t("compiler.noMatch") }}</p>
              <section v-for="group in groups" :key="group.key" class="library__group" :data-library-group="group.key" :aria-label="group.label">
                <h2 class="library__group-label">{{ group.label }}</h2>
                <CodeLibraryMenu
                  v-for="menu in group.menus"
                  :key="menu.id"
                  :data-chapter="menu.id"
                  :label="menu.label"
                  :items="menu.items"
                  :count="menu.count"
                  :selected-id="selectedId"
                  @select="selectById"
                />
              </section>
            </div>
          </div>

          <div class="library__work">
            <section class="library__editor" :aria-label="t('compiler.code')">
              <header class="library__editor-bar">
                <FileCode :size="20" class="library__file-icon" aria-hidden="true" />
                <div class="library__document">
                  <p class="library__document-title" :title="current?.title || fileName">{{ current?.title || fileName }}</p>
                  <p v-if="current" class="library__origin" :title="current.origin">{{ current.origin }}</p>
                </div>
                <input ref="fileInput" class="library__file-input" type="file" accept=".c,.h,text/x-c,text/x-chdr" :aria-label="t('compiler.importCode')" hidden @change="importCode">
                <button class="library__import library__glass library__glass--icon" type="button" :aria-label="t('compiler.importCode')" :title="t('compiler.importCode')" @click="chooseFile">
                  <img :src="downloadIcon" class="library__upload-icon" alt="" aria-hidden="true">
                </button>
                <button class="library__export library__glass library__glass--icon" type="button" :aria-label="t('compiler.exportCode')" :title="t('compiler.exportCode')" @click="exportCode">
                  <img :src="downloadIcon" alt="" aria-hidden="true">
                </button>
                <button class="library__new library__glass library__glass--icon" type="button" :aria-label="t('compiler.newFile')" :title="t('compiler.newFile')" @click="newBlank">
                  <FilePlus :size="20" aria-hidden="true" />
                </button>
                <RouterLink class="library__home library__glass library__glass--icon" to="/" :aria-label="t('common.backHome')" :title="t('common.backHome')">
                  <img :src="homeIcon" alt="" aria-hidden="true">
                </RouterLink>
              </header>
              <CCodeEditor class="library__code" v-model="code" :document-key="documentKey" @update:model-value="onCodeInput" @run="onPrimaryAction" @import="chooseFile" @export="exportCode" />
            </section>

            <p v-if="fileError" class="library__failure" role="alert">{{ fileError }}</p>

            <div v-if="hint || exampleNote" class="library__notes">
              <p v-if="hint" class="library__hint">{{ hint }}</p>
              <p v-if="exampleNote" class="library__hint">{{ t("compiler.exampleNote") }}：{{ exampleNote }}</p>
            </div>

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
              <button v-if="canLoadExample" class="library__chip library__glass" type="button" @click="loadExample">
                {{ t("compiler.addExample") }}
              </button>
              <button v-if="dirty && current" class="library__restore library__glass library__glass--icon" type="button" :aria-label="t('experiment.restore')" :title="t('experiment.restore')" @click="restore"><RotateCcw :size="20" aria-hidden="true" /></button>
              <LiquidMetalButton class="library__run" :disabled="running && !awaitingInput" @click="onPrimaryAction">{{ primaryLabel }}</LiquidMetalButton>
            </div>
          </div>
        </div>
      </div>

    </section>
  </BrandStage>
</template>

<style scoped>
.library { display: grid; grid-template-rows: auto minmax(0, 1fr); width: 100%; min-width: 0; min-height: 0; height: 100%; gap: 16px; }

.library__panel {
  display: grid;
  min-height: 0;
  min-width: 0;
  color: var(--text);
}

/* Two panes: the library on the left, the code the learner is looking at on the right. */
.library__grid { display: grid; grid-template-columns: minmax(320px, min(28%, 420px)) minmax(0, 1fr); min-width: 0; min-height: 0; gap: 22px; }

.library__list { display: grid; grid-template-rows: auto minmax(0, 1fr); gap: 16px; min-width: 0; min-height: 0; padding: 2px 0; }
.library__catalog { display: grid; align-content: start; gap: 18px; min-height: 0; overflow-y: auto; overflow-x: hidden; overscroll-behavior: contain; padding-right: 8px; scrollbar-gutter: stable; }
.library__search {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--text) 18%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  color: var(--text);
  font: inherit;
  font-size: 17px;
}
.library__search:focus-visible { outline: 2px solid var(--text-muted); outline-offset: 2px; }
.library__list :deep(.code-menu__trigger) { min-height: 42px; font-size: 15px; }
.library__group { display: grid; min-width: 0; gap: 6px; }
.library__group + .library__group { border-top: 1px solid var(--line-strong); padding-top: 16px; }
.library__group-label { margin: 0 0 4px; font-size: 16px; font-weight: 620; line-height: 24px; }

.library__work { display: flex; flex-direction: column; gap: 10px; min-width: 0; min-height: 0; }
.library__editor { display: flex; flex-direction: column; flex: 1; min-height: 180px; overflow: hidden; border: 1px solid var(--line-strong); border-radius: 8px; background: var(--surface); }
.library__editor-bar { display: flex; align-items: center; flex: none; gap: 10px; min-height: 66px; padding: 8px 12px 8px 18px; border-bottom: 1px solid var(--line-strong); background: color-mix(in srgb, var(--surface) 88%, var(--bg)); }
.library__file-icon { flex: none; color: #008c9e; }
.library__document { flex: 1; min-width: 0; }
.library__document-title { overflow: hidden; margin: 0; color: var(--text); font-size: 16px; font-weight: 650; line-height: 24px; text-overflow: ellipsis; white-space: nowrap; }
.library__origin { overflow: hidden; margin: 0; color: var(--text-muted); font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }
.library__code { flex: 1; min-height: 0; }
.library__notes { flex: none; max-height: 70px; overflow-y: auto; }
.library__hint { margin: 0; font-size: 14px; line-height: 1.5; }
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
.library__failure { flex: none; max-height: 48px; overflow-y: auto; margin: 0; color: #df3549; font-size: 14px; font-weight: 620; }

/* The console: one panel, terminal-shaped - the program's output on top, your input at the prompt. */
.console {
  display: flex;
  flex-direction: column;
  flex: none;
  gap: 8px;
  height: clamp(140px, 23dvh, 240px);
  min-height: 0;
  padding: 12px 16px;
  border: 1px solid color-mix(in srgb, var(--text) 18%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface) 74%, transparent);
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
}
.console__bar { display: flex; flex: none; align-items: baseline; justify-content: space-between; gap: 12px; }
.console__label { font-family: var(--font-ui); font-size: 15px; font-weight: 620; }
.console__status { font-family: var(--font-ui); font-size: 13px; font-weight: 620; color: var(--text-muted); }
.console__screen {
  margin: 0;
  flex: 1;
  min-height: 0;
  overflow: auto;
  color: var(--text);
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.console__screen--error { color: color-mix(in srgb, #c0392b 85%, var(--text)); }
.console__screen--idle { font-family: var(--font-ui); font-size: 14px; color: var(--text-muted); }
.console__verdict { flex: none; margin: 0; font-family: var(--font-ui); font-size: 13px; font-weight: 620; }
.console__expected {
  margin: 0;
  max-height: 64px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px dashed color-mix(in srgb, var(--text) 22%, transparent);
  border-radius: 12px;
  color: var(--text-muted);
  font-size: 18px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.console__prompt { display: flex; flex: none; gap: 8px; align-items: flex-start; border-top: 1px solid color-mix(in srgb, var(--text) 14%, transparent); padding-top: 8px; }
.console__caret { color: var(--text-muted); font-size: 18px; line-height: 1.6; }
.console__input {
  flex: 1 1 auto;
  min-width: 0;
  height: 44px;
  min-height: 34px;
  max-height: 64px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 14px;
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
  font-size: 15px;
  font-weight: 620;
}
.console__send:hover { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }

.library__actions { display: flex; flex: none; flex-wrap: wrap; align-items: center; gap: 12px; padding-bottom: 4px; }
.library__run {
  --liquid-width: 168px;
  --liquid-height: 50px;
  flex: none;
  margin-left: auto;
}
.library__run :deep(.liquid-metal-button__content-layer) { font-size: 18px; font-weight: 700; }
.library__chip {
  padding: 11px 20px;
  min-height: 50px;
  font-size: 17px;
  font-weight: 620;
}
.library__glass { display: inline-flex; flex: none; align-items: center; justify-content: center; border: 1px solid color-mix(in srgb, var(--text) 10%, transparent); border-radius: 999px; background: color-mix(in srgb, var(--surface) 38%, transparent); color: var(--text); box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset 0 0 2px color-mix(in srgb, var(--text) 32%, transparent), 0 4px 8px color-mix(in srgb, var(--text) 17%, transparent); -webkit-backdrop-filter: blur(7px) saturate(1.14); backdrop-filter: blur(7px) saturate(1.14); cursor: pointer; font-family: var(--font-ui); text-decoration: none; transition: transform 180ms ease, filter 180ms ease; }
.library__glass--icon { display: inline-grid; width: 50px; height: 50px; padding: 0; place-items: center; border-radius: 50%; }
.library__glass img { display: block; width: 24px; height: 24px; }
.library__upload-icon { transform: rotate(180deg); }
.library__glass:hover { transform: translateY(-1px) scale(1.04); filter: brightness(1.06); }
.library__glass:focus-visible { outline: none; border-color: var(--text); box-shadow: var(--focus-ring); }
:global([data-theme="dark"]) .library__glass img { filter: invert(1); }

@media (max-width: 900px) {
  .library__grid { grid-template-columns: minmax(0, 1fr); grid-template-rows: 220px minmax(540px, 1fr); overflow-y: auto; gap: 18px; }
  .library__list { gap: 10px; }
  .library__catalog { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
  .library__group + .library__group { border-top: 0; border-left: 1px solid var(--line-strong); padding-top: 0; padding-left: 12px; }
}
@media (max-width: 520px) {
  .library__catalog { grid-template-columns: minmax(0, 1fr); }
  .library__group + .library__group { border-left: 0; border-top: 1px solid var(--line-strong); padding: 12px 0 0; }
  .library__run { --liquid-width: 142px; }
  .library__chip { padding-inline: 12px; }
  .library__actions { gap: 8px; }
}
@media (max-height: 740px) and (min-width: 901px) {
  .library__work { overflow-y: auto; }
}
@media (prefers-reduced-transparency: reduce) {
  .library__glass { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
</style>
