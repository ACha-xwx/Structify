<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { prefetchSlideImages, prefetchSlideWindow } from "../shared/courseware/prefetch-slides";
import { useI18n } from "../shared/i18n/locale";
import type { ClassroomSlideMatch, LessonCourseware } from "../shared/types/contracts";

const props = defineProps<{
  courseware: LessonCourseware | null;
  activeSlideId: string | null;
  match: ClassroomSlideMatch | null;
  loading: boolean;
  error: string;
}>();
const emit = defineEmits<{ (event: "openBrowser"): void; (event: "pin", slideId: string): void }>();

const { t } = useI18n();
const index = ref(0);
const imageFailed = ref(false);
const picking = ref(false);
const choice = ref("");

/** Deck order is the reading order: sort by page number so 上一页/下一页 always step through the PPT in sequence. */
const slides = computed(() => (props.courseware?.slides ?? []).slice().sort((a, b) => a.slideNumber - b.slideNumber));
const current = computed(() => slides.value[index.value] ?? null);
const position = computed(() => slides.value.length ? `${index.value + 1}/${slides.value.length}` : "0/0");
/** The prepared step decided this page; the pane only reports what it decided. */
const following = computed(() => props.match?.source === "script" || props.match?.source === "auto");
/** Only the states that actually need a word on screen get a badge; a directly matched page says nothing. */
const badge = computed(() => {
  const match = props.match;
  if (!match) return "";
  if (match.source === "override") return t("slides.pinnedByTeacher");
  if (match.kind === "CONTINUITY") return match.reason === "scope-only" ? t("slides.followSource") : t("slides.followPrevious");
  return "";
});

watch(() => props.activeSlideId, (id) => applyActive(id), { immediate: true });
watch(() => props.courseware, () => {
  index.value = 0;
  imageFailed.value = false;
  applyActive(props.activeSlideId);
});
/** Warm the pages around this one, so following the lesson never waits on the network. */
watch([index, slides], () => prefetchSlideWindow(slides.value, index.value), { immediate: true });

/** Jump to the page the lesson asks for, including when the panel mounts mid-lesson. */
function applyActive(id: string | null) {
  if (!id) return;
  const found = slides.value.findIndex((slide) => slide.id === id);
  if (found >= 0) select(found);
}

function select(next: number) {
  if (next < 0 || next >= slides.value.length) return;
  if (next !== index.value) imageFailed.value = false;
  index.value = next;
}

function previous() { select(index.value - 1); }
function next() { select(index.value + 1); }

function openPicker() {
  choice.value = current.value?.id ?? "";
  picking.value = true;
}

function confirmPin() {
  if (!choice.value) return;
  emit("pin", choice.value);
  picking.value = false;
  choice.value = "";
}
</script>

<template>
  <aside class="slides" :aria-label="t('slides.pane')">
    <header class="slides__bar">
      <p class="slides__title">{{ current?.deckTitle || courseware?.title || t("slides.title") }}</p>
      <p class="slides__position" aria-live="polite">{{ position }}</p>
    </header>

    <p v-if="badge" class="slides__badge" :class="{ 'slides__badge--gap': match?.kind === 'CONTINUITY' }">{{ badge }}</p>

    <p v-if="loading" class="slides__hint">{{ t("slides.loading") }}</p>
    <p v-else-if="error" class="slides__hint slides__hint--error" role="alert">{{ error }}</p>
    <p v-else-if="!slides.length" class="slides__hint">{{ t("slides.empty") }}</p>

    <template v-else>
      <div class="slides__stage">
        <img
          v-if="current && !imageFailed"
          :key="current.id"
          class="slides__image"
          :src="current.imageUrl"
          :alt="current.title || current.semanticSummary || t('slides.position', { page: current.slideNumber })"
          @error="imageFailed = true"
        >
        <p v-else class="slides__hint slides__hint--error">{{ t("slides.imageFailed") }}</p>
      </div>

      <div class="slides__controls">
        <button class="slides__button" type="button" :disabled="index <= 0" @click="previous">{{ t("slides.previous") }}</button>
        <p class="slides__caption">{{ current?.title || current?.semanticSummary }}</p>
        <button class="slides__button" type="button" :disabled="index >= slides.length - 1" @click="next">{{ t("slides.next") }}</button>
      </div>

      <div v-if="picking" class="slides__picker">
        <select v-model="choice" class="slides__select" :aria-label="t('slides.pickAction')">
          <option value="">{{ t("slides.pickPage") }}</option>
          <option v-for="slide in slides" :key="slide.id" :value="slide.id">
            {{ slide.slideNumber }} · {{ slide.title || slide.semanticSummary || slide.id }}
          </option>
        </select>
        <button class="slides__button" type="button" :disabled="!choice" @click="confirmPin">{{ t("common.confirm") }}</button>
        <button class="slides__button" type="button" @click="picking = false">{{ t("common.cancel") }}</button>
      </div>
      <button v-else class="slides__link" type="button" @click="openPicker">
        {{ following ? t("slides.useThis") : t("slides.useOther") }}
      </button>

      <button class="slides__link" type="button" @click="emit('openBrowser')">{{ t("slides.browseAll") }}</button>
    </template>
  </aside>
</template>

<style scoped>
/* Same card as the lesson beside it: hairline, glass fill, short shadow. */
.slides {
  display: grid;
  align-content: start;
  gap: 10px;
  min-width: 0;
  padding: 18px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 26px;
  background: color-mix(in srgb, var(--surface) 58%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 10%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
  color: var(--text);
}

.slides__bar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.slides__title,
.slides__position,
.slides__caption,
.slides__hint,
.slides__badge {
  margin: 0;
}

.slides__title {
  overflow: hidden;
  font-size: 16px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slides__position { color: var(--text-muted); font-size: 14px; }

.slides__badge {
  display: inline-block;
  justify-self: start;
  padding: 4px 12px;
  border: 1px solid color-mix(in srgb, var(--text) 22%, transparent);
  border-radius: 999px;
  font-size: 13px;
}

.slides__badge--gap { border-color: color-mix(in srgb, var(--text) 55%, transparent); }

.slides__hint {
  align-self: center;
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1.5;
  text-align: center;
}

.slides__hint--error { color: var(--text); }

.slides__stage {
  display: grid;
  min-height: 260px;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--text) 10%, transparent);
  border-radius: 20px;
  background: color-mix(in srgb, var(--surface) 74%, transparent);
}

.slides__image {
  max-width: 100%;
  max-height: min(52dvh, 620px);
  border-radius: 14px;
  object-fit: contain;
}

.slides__controls {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
}

.slides__caption {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 14px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slides__button,
.slides__link,
.slides__select {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  cursor: pointer;
  font: inherit;
}

.slides__button { padding: 8px 16px; font-size: 15px; }

.slides__button:hover:not(:disabled) { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }

.slides__button:disabled { cursor: default; opacity: .38; }

.slides__picker {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 8px;
}

.slides__select {
  min-width: 0;
  padding: 8px 14px;
  font-size: 14px;
}

.slides__link {
  padding: 9px;
  border-color: transparent;
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 620;
}

.slides__link:hover { color: var(--text); text-decoration: underline; text-underline-offset: 4px; }

@media (max-width: 1024px) {
  .slides__stage { min-height: 220px; }
}

@media (prefers-reduced-transparency: reduce) {
  .slides { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
</style>
