<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { Brain, ChevronDown, Database } from "@lucide/vue";
import type { ChatSource } from "../../shared/types";
import { useI18n } from "../../shared/i18n/locale";

const props = defineProps<{
  reasoning: string;
  working: boolean;
  reasoningActive: boolean;
  retrieved: boolean;
  sources: ChatSource[];
  seconds?: number;
  reasoningSeconds?: number;
}>();
const emit = defineEmits<{ resize: [] }>();
const { t } = useI18n();
const manualOpen = ref<boolean | null>(null);
const thinkingOpen = ref<boolean | null>(null);
const sourcesOpen = ref(false);
const displayReasoning = computed(() => props.reasoning.replace(/\r\n?/g, "\n").replace(/\n(?:[ \t]*\n)+/g, "\n"));
const shown = ref(props.working ? "" : displayReasoning.value);
const viewport = ref<HTMLElement | null>(null);
const catchingUp = computed(() => shown.value.length < displayReasoning.value.length);
const expanded = computed(() => manualOpen.value ?? (props.working || catchingUp.value));
const thinkingExpanded = computed(() => thinkingOpen.value ?? (props.reasoningActive || catchingUp.value));
let timer: ReturnType<typeof setInterval> | undefined;
let target: string[] = Array.from(displayReasoning.value);
let position = props.working ? 0 : target.length;

function clearTimer() {
  if (timer !== undefined) clearInterval(timer);
  timer = undefined;
}

watch(displayReasoning, (text) => {
  target = Array.from(text);
  if (!text.startsWith(shown.value)) {
    position = 0;
    shown.value = "";
  }
  if (!catchingUp.value || timer !== undefined) return;
  timer = setInterval(async () => {
    // Keep live deltas readable without accumulating minutes of delay on a long thought.
    position = Math.min(target.length, position + Math.max(1, Math.ceil((target.length - position) / 70)));
    shown.value = target.slice(0, position).join("");
    await nextTick();
    if (viewport.value && props.reasoningActive) viewport.value.scrollTop = viewport.value.scrollHeight;
    emit("resize");
    if (position >= target.length) clearTimer();
  }, 18);
}, { immediate: true });

onBeforeUnmount(clearTimer);
</script>

<template>
  <div class="reasoning">
    <button class="reasoning__master" type="button" :aria-expanded="expanded" @click="manualOpen = !expanded">
      <span v-if="working" class="reasoning__pixels" aria-hidden="true">
        <i v-for="pixel in 9" :key="pixel" :style="{ animationDelay: `${(pixel * 137) % 800}ms` }" />
      </span>
      <span :class="{ shimmer: working }">{{ working ? t('chat.working') : seconds == null ? t('chat.workComplete') : t('chat.worked', { seconds: seconds.toFixed(1) }) }}</span>
      <ChevronDown :size="13" :class="{ 'is-open': expanded }" aria-hidden="true" />
    </button>
    <div class="reasoning__channel" :class="{ 'reasoning__channel--open': expanded }" :inert="!expanded || undefined">
      <div class="reasoning__clip">
        <div class="reasoning__timeline">
          <button class="reasoning__row" type="button" :aria-expanded="sourcesOpen" @click="sourcesOpen = !sourcesOpen">
            <Database :size="16" aria-hidden="true" />
            <span :class="{ shimmer: !retrieved && working }">{{ retrieved ? t('chat.retrieved') : t('chat.thinking') }}</span>
            <span v-if="retrieved" class="reasoning__count">{{ sources.length }}</span>
            <ChevronDown v-if="retrieved" :size="12" :class="{ 'is-open': sourcesOpen }" aria-hidden="true" />
          </button>
          <div v-if="retrieved" class="reasoning__channel reasoning__sources-channel" :class="{ 'reasoning__channel--open': sourcesOpen }" :inert="!sourcesOpen || undefined">
            <div class="reasoning__clip">
              <ul v-if="sources.length" class="reasoning__sources">
                <li v-for="source in sources" :key="source.id">{{ source.title }}</li>
              </ul>
              <p v-else class="reasoning__source-note">{{ t('chat.noSources') }}</p>
            </div>
          </div>

          <template v-if="reasoning">
            <button class="reasoning__row reasoning__thought" type="button" :aria-expanded="thinkingExpanded" @click="thinkingOpen = !thinkingExpanded">
              <Brain :size="16" aria-hidden="true" />
              <span :class="{ shimmer: reasoningActive }">{{ reasoningActive ? t('chat.reasoning') : reasoningSeconds == null ? t('chat.reasoningComplete') : t('chat.reasoned', { seconds: reasoningSeconds.toFixed(1) }) }}</span>
              <ChevronDown :size="12" :class="{ 'is-open': thinkingExpanded }" aria-hidden="true" />
            </button>
            <div class="reasoning__channel" :class="{ 'reasoning__channel--open': thinkingExpanded }" :inert="!thinkingExpanded || undefined">
              <div class="reasoning__clip">
                <div ref="viewport" class="reasoning__text" :class="{ 'reasoning__text--live': reasoningActive }">
                  {{ shown }}<span v-if="reasoningActive || catchingUp" class="reasoning__cursor" aria-hidden="true" />
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.reasoning { color: var(--text-muted); font-family: var(--font-ui); font-size: 15px; line-height: 1.6; }
.reasoning button { display: flex; align-items: center; gap: 7px; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; }
.reasoning button:focus-visible { outline: 2px solid var(--text); outline-offset: 3px; }
.reasoning button:hover { color: var(--text); }
.reasoning__master { min-height: 25px; }
.reasoning__master > svg { opacity: .5; }
.reasoning svg { flex-shrink: 0; transition: transform 250ms ease; }
.reasoning svg.is-open { transform: rotate(180deg); }
.reasoning__pixels { display: grid; flex-shrink: 0; width: 14px; height: 14px; grid-template-columns: repeat(3, 1fr); gap: 2px; }
.reasoning__pixels i { background: currentColor; animation: pixel-on 1.1s ease-in-out infinite; }
.shimmer { background: linear-gradient(90deg, var(--text-muted) 35%, var(--text) 50%, var(--text-muted) 65%); background-size: 200% 100%; background-clip: text; color: transparent; animation: thought-shimmer 1.8s linear infinite; }
.reasoning__channel { display: grid; grid-template-rows: 0fr; opacity: 0; transition: grid-template-rows 300ms ease, opacity 300ms ease; }
.reasoning__channel--open { grid-template-rows: 1fr; opacity: 1; }
.reasoning__clip { min-height: 0; overflow: hidden; }
.reasoning__timeline { display: grid; gap: 3px; margin: 5px 0 2px 7px; padding: 2px 0 3px 10px; border-left: 1px solid color-mix(in srgb, var(--text) 14%, transparent); }
.reasoning .reasoning__row { min-height: 30px; padding: 0 5px; border-radius: 5px; font-size: 16px; }
.reasoning__count { padding: 0 5px; border-radius: 4px; background: color-mix(in srgb, var(--text) 6%, transparent); font-variant-numeric: tabular-nums; }
.reasoning__sources { margin: 0 0 5px 24px; padding: 0; list-style: none; font-size: 16px; }
.reasoning__source-note { margin: 0 0 5px 24px; font-size: 16px; }
.reasoning__text { margin: 3px 0 5px 12px; padding: 3px 10px 5px; border-left: 1px solid color-mix(in srgb, var(--text) 14%, transparent); max-height: 260px; overflow-y: auto; font-size: 16px; line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; scrollbar-width: thin; }
.reasoning__text--live { max-height: 160px; scrollbar-width: none; mask-image: linear-gradient(transparent, #000 12px, #000 calc(100% - 8px), transparent); }
.reasoning__cursor { display: inline-block; width: 2px; height: 1em; margin-left: 3px; background: var(--text); vertical-align: -.12em; animation: thought-cursor 1s step-end infinite; }
@keyframes pixel-on { 0%, 100% { opacity: .15; transform: scale(.9); } 50% { opacity: .95; transform: scale(1.1); } }
@keyframes thought-shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }
@keyframes thought-cursor { 50% { opacity: 0; } }
@media (prefers-reduced-motion: reduce) { .reasoning * { animation: none; transition: none; } }
</style>
