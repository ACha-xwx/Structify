<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Check, Copy } from "@lucide/vue";
import { highlightChatCode } from "../chat-code";
import { useI18n } from "../../shared/i18n/locale";
import downloadIcon from "../../assets/chat/download.svg";
import shareIcon from "../../assets/chat/share.svg";
import { CHAT_CODE_STATE_KEY } from "../../shared/compiler/chat-code-import";

const props = defineProps<{ code: string; language: string; streaming?: boolean }>();
const downloadStyle = { "--download-icon": `url("${downloadIcon}")` };
const shareStyle = { "--share-icon": `url("${shareIcon}")` };
const router = useRouter();
const canOpenInCompiler = computed(() => props.language.trim().toLowerCase() === "c");
const { t } = useI18n();
const highlighted = ref("");
const copied = ref(false);
const labels: Record<string, string> = { c: "C", cpp: "C++", js: "JavaScript", javascript: "JavaScript", ts: "TypeScript", typescript: "TypeScript", py: "Python", python: "Python", text: "Text", plaintext: "Text", bash: "Bash", sh: "Shell", json: "JSON", html: "HTML", css: "CSS", sql: "SQL" };
const languageLabel = computed(() => labels[props.language.toLowerCase()] ?? props.language);
let version = 0;
let copyTimer: ReturnType<typeof setTimeout> | undefined;
let highlightTimer: ReturnType<typeof setTimeout> | undefined;
let highlighting = false;
let needsHighlight = false;

async function highlight() {
  if (highlighting) { needsHighlight = true; return; }
  highlighting = true;
  needsHighlight = false;
  const current = version;
  try {
    const html = await highlightChatCode(props.code, props.language);
    if (current === version) highlighted.value = html;
  } catch {
    // Keep the escaped plain-code fallback when a language or highlighter cannot load.
  } finally {
    highlighting = false;
    if (needsHighlight) scheduleHighlight();
  }
}

function scheduleHighlight() {
  if (highlightTimer !== undefined) return;
  if (!props.streaming) { void highlight(); return; }
  highlightTimer = setTimeout(() => { highlightTimer = undefined; void highlight(); }, 250);
}

watch(() => [props.code, props.language, props.streaming], () => {
  version++;
  highlighted.value = "";
  copied.value = false;
  if (copyTimer !== undefined) clearTimeout(copyTimer);
  if (!props.streaming && highlightTimer !== undefined) { clearTimeout(highlightTimer); highlightTimer = undefined; }
  scheduleHighlight();
}, { immediate: true });

function download() {
  const extensions: Record<string, string> = { c: 'c', h: 'h', cpp: 'cpp', 'c++': 'cpp', cc: 'cpp', cxx: 'cpp',
    js: 'js', javascript: 'js', ts: 'ts', typescript: 'ts', py: 'py', python: 'py', java: 'java',
    json: 'json', html: 'html', css: 'css', sql: 'sql', bash: 'sh', sh: 'sh', shell: 'sh', xml: 'xml', yaml: 'yml', yml: 'yml' };
  const url = URL.createObjectURL(new Blob([props.code], { type: 'text/plain;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `code.${extensions[props.language.toLowerCase()] ?? 'txt'}`;
  document.body.append(link);
  try { link.click(); }
  finally { link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
}

function openInCompiler() {
  if (!canOpenInCompiler.value) return;
  void router.push({ name: "compiler", state: { [CHAT_CODE_STATE_KEY]: props.code } });
}

async function copy() {
  const current = version;
  try {
    await navigator.clipboard.writeText(props.code);
    if (current !== version) return;
    copied.value = true;
    if (copyTimer !== undefined) clearTimeout(copyTimer);
    copyTimer = setTimeout(() => { copied.value = false; }, 2000);
  } catch {
    copied.value = false;
  }
}

onBeforeUnmount(() => {
  version++;
  needsHighlight = false;
  if (highlightTimer !== undefined) clearTimeout(highlightTimer);
  if (copyTimer !== undefined) clearTimeout(copyTimer);
});
</script>

<template>
  <section class="code-block code-palette">
    <header class="code-block__header">
      <div class="code-block__metadata">
        <span class="code-block__language">{{ languageLabel }}</span>
      </div>
      <div class="code-block__actions">
      <button v-if="canOpenInCompiler" class="code-block__copy code-block__share" type="button" :title="t('chat.openInCompiler')" :aria-label="t('chat.openInCompiler')" @click="openInCompiler">
        <span class="code-block__share-icon" :style="shareStyle" aria-hidden="true" />
      </button>
      <button class="code-block__copy" type="button" :title="copied ? t('chat.copied') : t('chat.copy')" :aria-label="copied ? t('chat.copied') : t('chat.copy')" @click="copy">
        <Check v-if="copied" :size="18" aria-hidden="true" />
        <Copy v-else :size="18" aria-hidden="true" />
      </button>
      <button class="code-block__copy code-block__download" type="button" :title="t('chat.downloadCode')" :aria-label="t('chat.downloadCode')" @click="download">
        <span class="code-block__download-icon" :style="downloadStyle" aria-hidden="true" />
      </button>
      </div>
    </header>
    <div v-if="highlighted" class="code-block__content" v-html="highlighted" />
    <div v-else class="code-block__content"><pre><code :class="`language-${language}`">{{ code }}</code></pre></div>
  </section>
</template>

<style scoped>
.code-block { --code-bg: #fff; --code-fg: #24292e; --code-line: #dedee3; --code-header: #fff; --code-badge: #ebebed; width: 100%; min-width: 0; margin: 14px 0; overflow: hidden; border: 1px solid var(--code-line); border-radius: 12px; background: var(--code-bg); color: var(--code-fg); }
.code-block__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 52px; padding: 9px 16px; border-bottom: 1px solid var(--code-line); background: var(--code-header); }
.code-block__metadata { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; min-width: 0; }
.code-block__language { padding: 4px 8px; border-radius: 4px; background: var(--code-badge); font-size: 14px; font-weight: 550; line-height: 1.35; overflow-wrap: anywhere; }
.code-block__copy { display: grid; place-items: center; flex: 0 0 32px; width: 32px; height: 32px; padding: 0; border: 0; border-radius: 5px; background: transparent; color: var(--code-fg); cursor: pointer; }
.code-block__actions { display: flex; align-items: center; gap: 4px; }
.code-block__download-icon { width: 18px; height: 18px; background: currentColor; mask: var(--download-icon) center / contain no-repeat; }
.code-block__share-icon { width: 18px; height: 18px; background: currentColor; mask: var(--share-icon) center / contain no-repeat; }
.code-block__copy:hover { background: var(--code-badge); }
.code-block__copy:focus-visible { outline: 2px solid var(--code-fg); outline-offset: 2px; }
.code-block__content { overflow-x: auto; background: var(--code-bg); }
.code-block__content :deep(pre) { margin: 0; padding: 16px; background: var(--code-bg); color: var(--code-fg); white-space: pre; word-break: normal; }
.code-block__content :deep(code) { padding: 0; border-radius: 0; background: transparent; font-family: var(--font-mono); font-size: 15px; line-height: 1.65; }
.code-block__content :deep(.shiki span) { color: var(--shiki-light); }
:global([data-theme="dark"]) .code-block { --code-bg: #24292e; --code-fg: #e1e4e8; --code-line: #41464d; --code-header: #202428; --code-badge: #34393f; }
:global([data-theme="dark"]) .code-block__content :deep(.shiki span) { color: var(--shiki-dark); }
</style>
