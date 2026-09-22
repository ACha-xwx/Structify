<script setup lang="ts">
import { onMounted, ref } from "vue";
import BrandStage from "../shared/components/BrandStage.vue";
import { useI18n } from "../shared/i18n/locale";
import { userApi } from "../user/runtime";
import type { CodeRunResponse } from "../shared/types/contracts";
import { compilerTemplates, type CompilerTemplate } from "./templates";

/**
 * The C editor from the original single-page app, restored on the shared stage. Same five
 * starter templates, same draft persistence, same Ctrl+Enter to run - now against the
 * Spring sandbox (`/api/v1/code/runs`) instead of the legacy Node route.
 */
const DRAFT_KEY = "structify.compiler.draft";
const MAX_CODE_LENGTH = 20000;

const { t } = useI18n();
const activeTemplateId = ref(compilerTemplates[0].id);
const code = ref(compilerTemplates[0].code);
const stdin = ref(compilerTemplates[0].stdin);
const running = ref(false);
const result = ref<CodeRunResponse | null>(null);
const failure = ref("");

const activeTemplate = () => compilerTemplates.find((item) => item.id === activeTemplateId.value) ?? compilerTemplates[0];

function applyTemplate(id: string) {
  const template = compilerTemplates.find((item) => item.id === id);
  if (!template) return;
  activeTemplateId.value = template.id;
  code.value = template.code;
  stdin.value = template.stdin;
  result.value = null;
  failure.value = "";
  saveDraft();
}

function saveDraft() {
  try {
    localStorage.setItem(DRAFT_KEY, JSON.stringify({ templateId: activeTemplateId.value, code: code.value, stdin: stdin.value }));
  } catch {}
}

const scheduleSave = (() => {
  let timer: ReturnType<typeof setTimeout> | undefined;
  return () => {
    clearTimeout(timer);
    timer = setTimeout(saveDraft, 350);
  };
})();

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
    result.value = await userApi.runCode({ language: "c", code: code.value, stdin: stdin.value });
  } catch (error) {
    failure.value = error instanceof Error ? `${t("compiler.statusNetwork")}：${error.message}` : t("compiler.statusNetwork");
  } finally {
    running.value = false;
    saveDraft();
  }
}

function onCodeKeydown(event: KeyboardEvent) {
  const target = event.target as HTMLTextAreaElement;
  if (event.key === "Tab") {
    event.preventDefault();
    const { selectionStart: start, selectionEnd: end, value } = target;
    target.value = `${value.slice(0, start)}  ${value.slice(end)}`;
    target.selectionStart = target.selectionEnd = start + 2;
    code.value = target.value;
    saveDraft();
  }
  if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
    event.preventDefault();
    run();
  }
}

const statusText = () => {
  const status = result.value?.status;
  if (failure.value || !status) return "";
  if (status === "success") return t("compiler.statusSuccess");
  if (status === "compile_error") return t("compiler.statusCompile");
  return t("compiler.statusRuntime");
};

onMounted(() => {
  try {
    const raw = localStorage.getItem(DRAFT_KEY);
    if (!raw) return;
    const draft = JSON.parse(raw) as { templateId?: string; code?: string; stdin?: string };
    const template = compilerTemplates.find((item) => item.id === draft.templateId);
    if (template) activeTemplateId.value = template.id;
    code.value = typeof draft.code === "string" && draft.code.trim() ? draft.code : activeTemplate().code;
    stdin.value = typeof draft.stdin === "string" ? draft.stdin : activeTemplate().stdin;
  } catch {}
});
</script>

<template>
  <BrandStage>
    <section class="editor-flow" aria-labelledby="editor-title">
      <h1 id="editor-title" class="editor__title">{{ t("compiler.title") }}</h1>

      <div class="editor__panel">
        <div class="editor__templates" role="group" :aria-label="t('compiler.templates')">
          <button
            v-for="template in compilerTemplates"
            :key="template.id"
            class="editor__chip"
            :class="{ 'editor__chip--active': template.id === activeTemplateId }"
            type="button"
            :aria-pressed="template.id === activeTemplateId"
            @click="applyTemplate(template.id)"
          >{{ t(template.labelKey) }}</button>
        </div>

        <label class="editor__label" for="compiler-code">{{ t("compiler.code") }} · {{ activeTemplate().file }}</label>
        <textarea
          id="compiler-code"
          v-model="code"
          class="editor__code"
          spellcheck="false"
          :aria-label="t('compiler.code')"
          @input="scheduleSave"
          @keydown="onCodeKeydown"
        ></textarea>

        <label class="editor__label" for="compiler-stdin">{{ t("compiler.stdin") }}</label>
        <textarea
          id="compiler-stdin"
          v-model="stdin"
          class="editor__stdin"
          spellcheck="false"
          :aria-label="t('compiler.stdin')"
          @input="scheduleSave"
        ></textarea>

        <div class="editor__actions">
          <button class="editor__run" type="button" :disabled="running" @click="run">
            {{ running ? t("compiler.running") : t("compiler.run") }}
          </button>
          <p v-if="failure" class="editor__failure" role="alert">{{ failure }}</p>
        </div>

        <template v-if="result">
          <p v-if="statusText()" class="editor__headline">{{ statusText() }}</p>
          <pre v-if="result.stdout" class="editor__output">{{ result.stdout }}</pre>
          <pre v-else-if="result.stderr" class="editor__output editor__output--error">{{ result.stderr }}</pre>
          <p v-else class="editor__output editor__output--empty">{{ t("compiler.noOutput") }}</p>
        </template>
      </div>

      <nav class="editor__links">
        <RouterLink class="editor__home" to="/">{{ t("common.backHome") }}</RouterLink>
      </nav>
    </section>
  </BrandStage>
</template>

<style scoped>
.editor-flow { display: grid; width: min(100%, 860px); gap: 20px; }
.editor__title { margin: 0; color: var(--text); font-family: var(--font-ui); font-size: clamp(34px, 4vw, 46px); font-weight: 400; line-height: 1.06; }

.editor__panel {
  display: grid;
  gap: 12px;
  padding: 20px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 26px;
  background: color-mix(in srgb, var(--surface) 58%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 10%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
  color: var(--text);
}

.editor__templates { display: flex; flex-wrap: wrap; gap: 8px; }

.editor__chip {
  padding: 9px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 17px;
  font-weight: 620;
}

.editor__chip:hover { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.editor__chip--active { background: var(--text); border-color: var(--text); color: var(--surface); }

.editor__label { font-size: 19px; font-weight: 620; }

.editor__code,
.editor__stdin {
  width: 100%;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--text) 18%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  color: var(--text);
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  font-size: 16px;
  line-height: 1.55;
  resize: vertical;
}

.editor__code { min-height: 320px; }
.editor__stdin { min-height: 84px; }

.editor__actions { display: flex; align-items: center; gap: 14px; }

.editor__run {
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

.editor__run:disabled { cursor: default; opacity: .55; }
.editor__run:hover:not(:disabled) { box-shadow: 0 8px 18px color-mix(in srgb, var(--text) 22%, transparent); }

.editor__failure { margin: 0; color: var(--text); font-size: 19px; font-weight: 620; }

.editor__headline { margin: 4px 0 0; font-size: 19px; font-weight: 600; }
.editor__headline:after { content: ""; }

.editor__output {
  margin: 0;
  padding: 16px;
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

.editor__output--error { border-color: color-mix(in srgb, #c0392b 45%, transparent); }
.editor__output--empty { color: var(--text-muted); font-family: inherit; font-size: 19px; }

.editor__links { display: flex; justify-content: center; }
.editor__home { color: var(--text-muted); font-size: 19px; font-weight: 620; text-decoration: none; }
.editor__home:hover { color: var(--text); text-decoration: underline; text-underline-offset: 4px; }

@media (prefers-reduced-transparency: reduce) {
  .editor__panel { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
</style>
