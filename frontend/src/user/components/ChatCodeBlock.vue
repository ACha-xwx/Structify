<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { Check, Copy } from "@lucide/vue";
import { highlightChatCode } from "../chat-code";
import { useI18n } from "../../shared/i18n/locale";

const props = defineProps<{ code: string; language: string }>();
const { t } = useI18n();
const highlighted = ref("");
const copied = ref(false);
const labels: Record<string, string> = { c: "C", cpp: "C++", js: "JavaScript", javascript: "JavaScript", ts: "TypeScript", typescript: "TypeScript", py: "Python", python: "Python", text: "Text", plaintext: "Text", bash: "Bash", sh: "Shell", json: "JSON", html: "HTML", css: "CSS", sql: "SQL" };
const languageLabel = computed(() => labels[props.language.toLowerCase()] ?? props.language);
let version = 0;
let copyTimer: ReturnType<typeof setTimeout> | undefined;

watch(() => [props.code, props.language], async () => {
  const current = ++version;
  highlighted.value = "";
  copied.value = false;
  if (copyTimer !== undefined) clearTimeout(copyTimer);
  try {
    const html = await highlightChatCode(props.code, props.language);
    if (current === version) highlighted.value = html;
  } catch {
    // Keep the escaped plain-code fallback when a language or highlighter cannot load.
  }
}, { immediate: true });

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
  if (copyTimer !== undefined) clearTimeout(copyTimer);
});
</script>

<template>
  <section class="code-block">
    <header class="code-block__header">
      <div class="code-block__metadata">
        <span class="code-block__language">{{ languageLabel }}</span>
      </div>
      <button class="code-block__copy" type="button" :title="copied ? t('chat.copied') : t('chat.copy')" :aria-label="copied ? t('chat.copied') : t('chat.copy')" @click="copy">
        <Check v-if="copied" :size="18" aria-hidden="true" />
        <Copy v-else :size="18" aria-hidden="true" />
      </button>
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
.code-block__copy:hover { background: var(--code-badge); }
.code-block__copy:focus-visible { outline: 2px solid var(--code-fg); outline-offset: 2px; }
.code-block__content { overflow-x: auto; background: var(--code-bg); }
.code-block__content :deep(pre) { margin: 0; padding: 16px; background: var(--code-bg); color: var(--code-fg); white-space: pre; word-break: normal; }
.code-block__content :deep(code) { padding: 0; border-radius: 0; background: transparent; font-family: var(--font-mono); font-size: 15px; line-height: 1.65; }
.code-block__content :deep(.shiki span) { color: var(--shiki-light); }
[data-theme="dark"] .code-block { --code-bg: #24292e; --code-fg: #e1e4e8; --code-line: #41464d; --code-header: #202428; --code-badge: #34393f; }
[data-theme="dark"] .code-block__content :deep(.shiki span) { color: var(--shiki-dark); }
</style>
