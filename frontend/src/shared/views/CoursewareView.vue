<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import BrandStage from "../components/BrandStage.vue";
import { prefetchSlideImages, prefetchSlideWindow } from "../courseware/prefetch-slides";
import { useI18n } from "../i18n/locale";
import type { PresentationDeck, PresentationSlide } from "../types/contracts";
import { userApi } from "../../user/runtime";

/**
 * The whole deck, page by page. It stands on the same paper as the classroom it is reached from, so
 * stepping out of a lesson to look at the source slides does not feel like leaving the product.
 */
const { t } = useI18n();
const decks = ref<PresentationDeck[]>([]);
const slides = ref<PresentationSlide[]>([]);
const activeDeck = ref("");
const index = ref(0);
const loading = ref(true);
const error = ref("");
const imageFailed = ref(false);

const current = computed(() => slides.value[index.value] ?? null);
const position = computed(() => slides.value.length ? `${index.value + 1}/${slides.value.length}` : "0/0");

function failureMessage(cause: unknown): string {
  return cause instanceof Error && cause.message ? cause.message : t("common.failed");
}

async function openDeck(deckId: string) {
  if (!deckId) return;
  activeDeck.value = deckId;
  error.value = "";
  try {
    slides.value = await userApi.listDeckSlides(deckId);
    index.value = 0;
    imageFailed.value = false;
  } catch (cause) {
    slides.value = [];
    error.value = failureMessage(cause);
  }
}

function select(next: number) {
  if (next < 0 || next >= slides.value.length) return;
  if (next !== index.value) imageFailed.value = false;
  index.value = next;
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === "ArrowLeft") select(index.value - 1);
  if (event.key === "ArrowRight") select(index.value + 1);
}

/** Warm the pages around this one, so 下一页 is a lookup rather than a round trip through the edge. */
watch([index, slides], () => prefetchSlideWindow(slides.value, index.value));

onMounted(async () => {
  window.addEventListener("keydown", onKeydown);
  try {
    decks.value = (await userApi.listPresentationDecks()).slice()
      .sort((a, b) => a.chapter.localeCompare(b.chapter, "zh-Hans-CN", { numeric: true }) || a.title.localeCompare(b.title, "zh-Hans-CN"));
    await openDeck(decks.value[0]?.deckId ?? "");
  } catch (cause) {
    error.value = failureMessage(cause);
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => window.removeEventListener("keydown", onKeydown));
</script>

<template>
  <BrandStage wide>
    <div class="courseware" aria-live="polite">
      <aside class="courseware__decks" :aria-label="t('courseware.list')">
        <p class="courseware__heading">{{ t("slides.title") }}</p>
        <button
          v-for="deck in decks"
          :key="deck.deckId"
          class="courseware__deck"
          :class="{ 'courseware__deck--active': deck.deckId === activeDeck }"
          type="button"
          :title="t('courseware.deckMeta', { chapter: deck.chapter, count: deck.slideCount })"
          @click="openDeck(deck.deckId)"
        >
          <span class="courseware__deckTitle">{{ deck.title }}</span>
        </button>
        <p v-if="!loading && !decks.length" class="courseware__hint">{{ t("courseware.empty") }}</p>
        <RouterLink class="courseware__back" to="/classroom">{{ t("common.backToClassroom") }}</RouterLink>
      </aside>

      <section class="courseware__viewer">
        <p v-if="loading" class="courseware__hint">{{ t("courseware.loading") }}</p>
        <p v-else-if="error" class="courseware__hint courseware__hint--error" role="alert">{{ error }}</p>
        <template v-else-if="current">
          <div class="courseware__stage">
            <img
              v-if="!imageFailed"
              :key="current.id"
              class="courseware__image"
              :src="current.imageUrl"
              :alt="current.title || current.semanticSummary || t('slides.position', { page: current.slideNumber })"
              @error="imageFailed = true"
            >
            <p v-else class="courseware__hint courseware__hint--error">{{ t("courseware.imageFailed") }}</p>
          </div>
          <header class="courseware__bar">
            <p class="courseware__caption">{{ current.title || current.semanticSummary }}</p>
            <p class="courseware__position" aria-live="polite">{{ position }}</p>
          </header>
          <div class="courseware__controls">
            <button class="courseware__button" type="button" :disabled="index <= 0" @click="select(index - 1)">{{ t("courseware.previous") }}</button>
            <button class="courseware__button" type="button" :disabled="index >= slides.length - 1" @click="select(index + 1)">{{ t("courseware.next") }}</button>
          </div>
        </template>
        <p v-else class="courseware__hint">{{ decks.length ? t("courseware.pickDeck") : t("courseware.empty") }}</p>
      </section>
    </div>
  </BrandStage>
</template>

<style scoped>
.courseware {
  display: grid;
  width: min(1560px, 100%);
  margin: 0 auto;
  grid-template-columns: minmax(260px, 360px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  color: var(--text);
}

.courseware__decks {
  display: grid;
  align-content: start;
  gap: 8px;
  max-height: calc(100dvh - 130px);
  padding: 16px;
  overflow-y: auto;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 26px;
  background: color-mix(in srgb, var(--surface) 58%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 10%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
}

.courseware__heading {
  margin: 0 0 6px;
  font-size: 19px;
  font-weight: 620;
}

.courseware__deck {
  display: grid;
  gap: 3px;
  padding: 11px 16px;
  border: 1px solid color-mix(in srgb, var(--text) 14%, transparent);
  border-radius: 16px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color .16s ease, background-color .16s ease, transform .16s ease;
}

.courseware__deck:hover { border-color: var(--text); background: color-mix(in srgb, var(--text) 6%, transparent); transform: translateY(-1px); }
.courseware__deck--active { border-color: var(--text); background: color-mix(in srgb, var(--text) 10%, transparent); }

.courseware__deckTitle { overflow: hidden; font-size: 19px; font-weight: 620; text-overflow: ellipsis; white-space: nowrap; }

.courseware__back {
  margin-top: 10px;
  color: var(--text-muted);
  font-size: 19px;
  font-weight: 620;
  text-decoration: none;
}

.courseware__back:hover { color: var(--text); text-decoration: underline; text-underline-offset: 4px; }

.courseware__viewer {
  display: grid;
  align-content: center;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 26px;
  background: color-mix(in srgb, var(--surface) 58%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 10%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
}

.courseware__stage {
  display: grid;
  min-height: 320px;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--text) 10%, transparent);
  border-radius: 20px;
  background: color-mix(in srgb, var(--surface) 74%, transparent);
}

.courseware__image {
  max-width: 100%;
  max-height: min(72dvh, 900px);
  border-radius: 14px;
  object-fit: contain;
}

/* One heading row plus one chip row, same reading order as the classroom pane. */
.courseware__bar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.courseware__caption {
  flex: 1 1 auto;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-size: 19px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.courseware__position { flex: 0 0 auto; margin: 0; color: var(--text-muted); font-size: 19px; font-weight: 620; }

.courseware__controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.courseware__button {
  padding: 9px 18px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  font-weight: 620;
  text-decoration: none;
}

.courseware__button:hover:not(:disabled) { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.courseware__button:disabled { cursor: default; opacity: .38; }

.courseware__hint {
  margin: 0;
  color: var(--text-muted);
  font-size: 19px;
  line-height: 1.5;
  text-align: center;
}

.courseware__hint--error { color: var(--text); }

@media (max-width: 1024px) {
  .courseware { grid-template-columns: minmax(0, 1fr); }
  .courseware__decks { max-height: none; }
}

@media (prefers-reduced-transparency: reduce) {
  .courseware__decks,
  .courseware__viewer { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
</style>
