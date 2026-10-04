<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { prefetchSlideWindow } from "../shared/courseware/prefetch-slides";
import { useSlidePaging } from "../shared/courseware/use-slide-paging";
import { useI18n } from "../shared/i18n/locale";
import type { ClassroomSlideMatch, LessonCourseware } from "../shared/types/contracts";
import leftArrowIcon from "../assets/classroom/left-arrow.svg";
import rightArrowIcon from "../assets/classroom/right-arrow.svg";
import LiquidMetalButton from "../admin/components/LiquidMetalButton.vue";

const props = defineProps<{
  courseware: LessonCourseware | null;
  activeSlideId: string | null;
  match: ClassroomSlideMatch | null;
  loading: boolean;
  error: string;
  fit?: boolean;
}>();
const emit = defineEmits<{ (event: "openBrowser"): void }>();

const { t } = useI18n();
const index = ref(0);
const imageFailed = ref(false);

/** Deck order is the reading order: sort by page number so 上一页/下一页 always step through the PPT in sequence. */
const slides = computed(() => (props.courseware?.slides ?? []).slice().sort((a, b) => a.slideNumber - b.slideNumber));
const current = computed(() => slides.value[index.value] ?? null);
const emptyMessage = computed(() => props.courseware?.ready === true ? t("slides.empty") : t("slides.unavailable"));
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

useSlidePaging(
  () => !props.loading && !props.error && Boolean(current.value),
  (direction) => select(index.value + direction),
);

</script>

<template>
  <aside class="slides" :class="{ 'slides--fit': fit }" :aria-label="t('slides.pane')">
    <header class="slides__bar">
      <p class="slides__title" :title="current?.deckTitle || courseware?.title || ''">
        {{ current?.deckTitle || courseware?.title || t("slides.title") }}
      </p>
      <div v-if="$slots.headerActions" class="slides__header-actions">
        <slot name="headerActions" />
      </div>
    </header>

    <p v-if="badge" class="slides__badge" :class="{ 'slides__badge--gap': match?.kind === 'CONTINUITY' }">{{ badge }}</p>

    <div v-if="$slots.controls" class="slides__controls">
      <slot name="controls" />
    </div>

    <div v-if="loading || error || !slides.length" class="slides__stage slides__stage--empty">
      <p v-if="loading" class="slides__hint">{{ t("slides.loading") }}</p>
      <p v-else-if="error" class="slides__hint slides__hint--error" role="alert">{{ error }}</p>
      <p v-else class="slides__hint">{{ emptyMessage }}</p>
    </div>

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
    </template>

    <footer class="slides__footer">
      <div class="slides__tools" aria-label="Slide controls">
        <LiquidMetalButton
          class="slides__silver-control"
          view-mode="icon"
          :disabled="!slides.length || index <= 0"
          :aria-label="t('slides.previous')"
          :title="t('slides.previous')"
          @click="previous"
        >
          <template #icon><img class="slides__icon" :src="leftArrowIcon" alt="" aria-hidden="true"></template>
          <span class="slides__icon-label">{{ t("slides.previous") }}</span>
        </LiquidMetalButton>
        <LiquidMetalButton
          class="slides__silver-control"
          view-mode="icon"
          :disabled="!slides.length || index >= slides.length - 1"
          :aria-label="t('slides.next')"
          :title="t('slides.next')"
          @click="next"
        >
          <template #icon><img class="slides__icon" :src="rightArrowIcon" alt="" aria-hidden="true"></template>
          <span class="slides__icon-label">{{ t("slides.next") }}</span>
        </LiquidMetalButton>
        <button class="slides__chip slides__chip--glass" type="button" @click="emit('openBrowser')">{{ t("slides.browseAll") }}</button>
      </div>
      <div v-if="$slots.footerActions" class="slides__footer-actions">
        <slot name="footerActions" />
      </div>
    </footer>
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
  min-width: 0;
}

.slides__header-actions {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 8px;
}

.slides__title,
.slides__hint,
.slides__badge {
  margin: 0;
}

.slides__title {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 19px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slides__badge {
  display: inline-block;
  justify-self: start;
  padding: 6px 14px;
  border: 1px solid color-mix(in srgb, var(--text) 22%, transparent);
  border-radius: 999px;
  font-size: 19px;
}

.slides__badge--gap { border-color: color-mix(in srgb, var(--text) 55%, transparent); }

.slides__controls {
  min-width: 0;
}

.slides--courseware {
  min-height: min(80dvh, 860px);
  padding: clamp(20px, 2.4vw, 34px);
  gap: 14px;
}

.slides:not(.slides--courseware) {
  min-height: min(72dvh, 820px);
}

.slides--courseware .slides__stage {
  min-height: min(62dvh, 680px);
}

.slides--courseware .slides__image {
  max-height: min(64dvh, 760px);
}

.slides__hint {
  align-self: center;
  color: var(--text-muted);
  font-size: 19px;
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

.slides__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  min-width: 0;
}

.slides__tools {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.slides__chip {
  min-height: 48px;
  padding: 11px 22px;
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  font: inherit;
  font-family: var(--font-ui);
  font-size: 16px;
  font-weight: 620;
  transition: transform 180ms ease, background 180ms ease, border-color 180ms ease, box-shadow 180ms ease, filter 180ms ease;
}

.slides__chip--glass {
  border-color: color-mix(in srgb, var(--text) 10%, transparent);
  background: color-mix(in srgb, var(--surface) 38%, transparent);
  box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset 0 0 2px color-mix(in srgb, var(--text) 32%, transparent), 0 4px 8px color-mix(in srgb, var(--text) 17%, transparent);
  color: color-mix(in srgb, var(--text) 84%, #000 16%);
  -webkit-backdrop-filter: blur(7px) saturate(1.14);
  backdrop-filter: blur(7px) saturate(1.14);
}

.slides__icon { width: 24px; height: 24px; display: block; }

.slides__silver-control {
  --liquid-width: 50px;
  --liquid-height: 50px;
  flex: 0 0 50px;
}

.slides__icon-label {
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

.slides__chip:hover:not(:disabled) { transform: translateY(-1px) scale(1.02); filter: brightness(1.06); }

.slides__chip:disabled { cursor: default; opacity: .38; }

.slides__footer-actions { display: flex; flex: none; align-items: center; justify-content: flex-end; gap: 14px; margin-left: auto; }

:global([data-theme="dark"]) .slides__icon { filter: invert(1); }

@media (max-width: 1024px) {
  .slides__stage { min-height: 220px; }

  .slides__footer { align-items: stretch; flex-direction: column; }

  .slides__footer-actions { justify-content: flex-end; }

  .slides__header-actions { gap: 6px; }

  .slides--courseware {
    min-height: auto;
  }

  .slides--courseware .slides__stage {
    min-height: min(58dvh, 560px);
  }
}

@media (prefers-reduced-transparency: reduce) {
  .slides { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}

.slides.slides--fit {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 18px;
  gap: 10px;
}
.slides--fit .slides__bar,
.slides--fit .slides__badge,
.slides--fit .slides__controls,
.slides--fit .slides__footer { flex: none; }
.slides--fit .slides__stage { flex: 1 1 0; min-height: 0; overflow: hidden; }
.slides--fit .slides__image { width: 100%; height: 100%; min-height: 0; max-height: 100%; object-fit: contain; }

@media (max-width: 640px), (max-height: 640px) {
  .slides.slides--fit { padding: 10px; gap: 6px; }
  .slides--fit .slides__footer { gap: 8px; }
}
</style>
