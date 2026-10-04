<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from "vue";
import { useRouter } from "vue-router";
import { BookOpen, Check, ChevronDown } from "@lucide/vue";
import BrandStage from "../components/BrandStage.vue";
import { describeDeck } from "../courseware/deck-labels";
import { prefetchSlideWindow } from "../courseware/prefetch-slides";
import { useSlidePaging } from "../courseware/use-slide-paging";
import { useI18n } from "../i18n/locale";
import type { PresentationDeck, PresentationSlide } from "../types/contracts";
import { userApi } from "../../user/runtime";
import leftArrowIcon from "../../assets/classroom/left-arrow.svg";
import rightArrowIcon from "../../assets/classroom/right-arrow.svg";
import exitIcon from "../../assets/classroom/exit.svg";
import LiquidMetalButton from "../../admin/components/LiquidMetalButton.vue";

/**
 * The whole deck, page by page. It stands on the same paper as the classroom it is reached from, so
 * stepping out of a lesson to look at the source slides does not feel like leaving the product.
 */
const { t } = useI18n();
const router = useRouter();
const decks = ref<PresentationDeck[]>([]);
const slides = ref<PresentationSlide[]>([]);
const expandedChapter = ref<string | null>(null);
const activeDeck = ref("");
const index = ref(0);
const loading = ref(true);
const error = ref("");
const imageFailed = ref(false);
const selectedCoursewareDeckId = ref("");
const SELECTED_COURSEWARE_KEY = "structify.courseware.selected";
let deckRequest = 0;
const chapterListId = `courseware-chapters-${useId()}`;

const current = computed(() => slides.value[index.value] ?? null);
const displayedDecks = computed(() => decks.value.map((deck) => ({ deck, ...describeDeck(deck) }))
  .sort((a, b) => a.chapterKey.localeCompare(b.chapterKey, "zh-Hans-CN", { numeric: true })
    || a.title.localeCompare(b.title, "zh-Hans-CN", { numeric: true })));
const chapters = computed(() => [...new Map(displayedDecks.value.map((item) => [item.chapterKey, {
  key: item.chapterKey,
  title: item.chapterName || t("courseware.otherChapter"),
}])).values()].map((chapter) => ({
  ...chapter,
  decks: displayedDecks.value.filter((item) => item.chapterKey === chapter.key),
})));

function readSelectedDeckId(): string {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(SELECTED_COURSEWARE_KEY) ?? "null");
    return value && typeof value === "object" && !Array.isArray(value) && typeof (value as { deckId?: unknown }).deckId === "string"
      ? (value as { deckId: string }).deckId
      : "";
  } catch {
    return "";
  }
}

function failureMessage(cause: unknown): string {
  return cause instanceof Error && cause.message ? cause.message : t("common.failed");
}

async function openDeck(deckId: string) {
  const deck = displayedDecks.value.find((item) => item.deck.deckId === deckId);
  if (!deck) return;
  const request = ++deckRequest;
  expandedChapter.value = deck.chapterKey;
  activeDeck.value = deckId;
  loading.value = true;
  slides.value = [];
  index.value = 0;
  imageFailed.value = false;
  error.value = "";
  try {
    const result = await userApi.listDeckSlides(deckId);
    if (request !== deckRequest) return;
    slides.value = result;
  } catch (cause) {
    if (request !== deckRequest) return;
    error.value = failureMessage(cause);
  } finally {
    if (request === deckRequest) loading.value = false;
  }
}

function toggleChapter(key: string) {
  expandedChapter.value = expandedChapter.value === key ? null : key;
}

function onChapterKeydown(event: KeyboardEvent) {
  if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;
  const trigger = event.currentTarget as HTMLButtonElement;
  const triggers = Array.from(trigger.closest(".courseware__chapters")!.querySelectorAll<HTMLButtonElement>(".courseware__chapter-trigger"));
  const currentIndex = triggers.indexOf(trigger);
  const nextIndex = event.key === "Home" ? 0 : event.key === "End" ? triggers.length - 1
    : (currentIndex + (event.key === "ArrowDown" ? 1 : -1) + triggers.length) % triggers.length;
  event.preventDefault();
  triggers[nextIndex]?.focus();
}

function select(next: number) {
  if (next < 0 || next >= slides.value.length) return;
  if (next !== index.value) imageFailed.value = false;
  index.value = next;
}

useSlidePaging(
  () => !loading.value && !error.value && Boolean(current.value),
  (direction) => select(index.value + direction),
);

function selectCurrentCourseware() {
  const deck = decks.value.find((item) => item.deckId === activeDeck.value);
  if (loading.value || !deck || !slides.value.length) return;
  const lessonIds = [...new Set(slides.value.flatMap((slide) => slide.lessonIds).filter((id) => Boolean(id)))];
  localStorage.setItem(SELECTED_COURSEWARE_KEY, JSON.stringify({
    deckId: deck.deckId,
    title: deck.title,
    chapter: deck.chapter || slides.value[0]?.chapter || "",
    lessonIds,
  }));
  selectedCoursewareDeckId.value = deck.deckId;
  void router.push("/classroom");
}

/** Warm the pages around this one, so 下一页 is a lookup rather than a round trip through the edge. */
watch([index, slides], () => prefetchSlideWindow(slides.value, index.value));

onMounted(async () => {
  selectedCoursewareDeckId.value = readSelectedDeckId();
  try {
    decks.value = await userApi.listPresentationDecks();
    if (decks.value.length) await openDeck(displayedDecks.value[0].deck.deckId);
    else loading.value = false;
  } catch (cause) {
    error.value = failureMessage(cause);
    loading.value = false;
  }
});

onBeforeUnmount(() => {
  deckRequest += 1;
});
</script>

<template>
  <BrandStage wide>
    <div class="courseware-page">
      <h1 class="workbench-title">{{ t("courseware.title") }}</h1>
    <div class="courseware" aria-live="polite">
      <aside class="courseware__decks" :aria-label="t('courseware.list')">
        <h2 class="courseware__heading">{{ t("slides.title") }}</h2>
        <div class="courseware__chapters" @keydown.left.stop @keydown.right.stop>
          <div v-for="chapter in chapters" :key="chapter.key" class="courseware__chapter">
            <h3 class="courseware__chapter-heading">
              <button
                :id="`${chapterListId}-trigger-${chapter.key}`"
                class="courseware__chapter-trigger"
                type="button"
                :data-chapter-key="chapter.key"
                :aria-expanded="expandedChapter === chapter.key"
                :aria-controls="`${chapterListId}-panel-${chapter.key}`"
                @click="toggleChapter(chapter.key)"
                @keydown="onChapterKeydown"
              >
                <span class="courseware__chapter-label">
                  <BookOpen class="courseware__chapter-icon" aria-hidden="true" />
                  <span>{{ chapter.title }}</span>
                </span>
                <ChevronDown class="courseware__chapter-chevron" aria-hidden="true" />
              </button>
            </h3>
            <div
              :id="`${chapterListId}-panel-${chapter.key}`"
              class="courseware__chapter-panel"
              :class="{ 'courseware__chapter-panel--open': expandedChapter === chapter.key }"
              role="region"
              :aria-labelledby="`${chapterListId}-trigger-${chapter.key}`"
              :aria-hidden="expandedChapter !== chapter.key"
              :inert="expandedChapter !== chapter.key"
            >
              <div class="courseware__chapter-panel-inner">
                <ul class="courseware__deck-list">
                  <li v-for="item in chapter.decks" :key="item.deck.deckId">
                    <button
                      class="courseware__deck-option"
                      type="button"
                      :data-deck-id="item.deck.deckId"
                      :aria-pressed="activeDeck === item.deck.deckId"
                      @click="openDeck(item.deck.deckId)"
                    >
                      <span>{{ item.title }}</span>
                      <Check v-if="activeDeck === item.deck.deckId" class="courseware__deck-check" aria-hidden="true" />
                    </button>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>
        <p v-if="!loading && !decks.length" class="courseware__hint">{{ t("courseware.empty") }}</p>
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
          </header>
          <div class="courseware__controls">
            <LiquidMetalButton
              class="courseware__silver-control"
              view-mode="icon"
              :disabled="index <= 0"
              :aria-label="t('courseware.previous')"
              :title="t('courseware.previous')"
              @click="select(index - 1)"
            >
              <template #icon><img class="courseware__icon" :src="leftArrowIcon" alt="" aria-hidden="true"></template>
              <span class="courseware__icon-label">{{ t("courseware.previous") }}</span>
            </LiquidMetalButton>
            <LiquidMetalButton
              class="courseware__silver-control"
              view-mode="icon"
              :disabled="index >= slides.length - 1"
              :aria-label="t('courseware.next')"
              :title="t('courseware.next')"
              @click="select(index + 1)"
            >
              <template #icon><img class="courseware__icon" :src="rightArrowIcon" alt="" aria-hidden="true"></template>
              <span class="courseware__icon-label">{{ t("courseware.next") }}</span>
            </LiquidMetalButton>
            <RouterLink class="courseware__back courseware__icon-button courseware__icon-button--glass" to="/classroom" :aria-label="t('common.backToClassroom')" :title="t('common.backToClassroom')">
              <img class="courseware__icon" :src="exitIcon" alt="" aria-hidden="true">
              <span class="courseware__icon-label">{{ t("common.backToClassroom") }}</span>
            </RouterLink>
            <LiquidMetalButton
              class="courseware__select-current"
              :disabled="!current"
              :aria-label="t('courseware.selectCurrent')"
              :aria-pressed="selectedCoursewareDeckId === activeDeck"
              :title="t('courseware.selectCurrent')"
              @click="selectCurrentCourseware"
            >
              {{ t("courseware.selectCurrent") }}
            </LiquidMetalButton>
          </div>
        </template>
        <p v-else class="courseware__hint">{{ decks.length ? t("courseware.pickDeck") : t("courseware.empty") }}</p>
      </section>
    </div>
    </div>
  </BrandStage>
</template>

<style scoped>
.courseware-page { display: grid; width: min(1560px, 100%); margin: 0 auto; gap: 22px; }
.courseware {
  display: grid;
  width: 100%;
  margin: 0 auto;
  grid-template-columns: minmax(260px, 360px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  color: var(--text);
}

.courseware__decks {
  display: grid;
  align-content: start;
  gap: 16px;
  min-width: 0;
  padding: 16px;
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

.courseware__chapters {
  width: 100%;
  min-width: 0;
}

.courseware__chapter {
  border-bottom: 1px solid color-mix(in srgb, var(--text) 15%, transparent);
}

.courseware__chapter-heading { margin: 0; }

.courseware__chapter-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 60px;
  padding: 16px 0;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-family: var(--font-ui);
  font-size: 18px;
  font-weight: 500;
  line-height: 28px;
  text-align: left;
  cursor: pointer;
}

.courseware__chapter-label {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  overflow-wrap: anywhere;
}

.courseware__chapter-icon { flex: none; width: 20px; height: 20px; color: var(--text-muted); }
.courseware__chapter-chevron { flex: none; width: 16px; height: 16px; transition: transform 200ms ease; }
.courseware__chapter-trigger[aria-expanded="true"] .courseware__chapter-chevron { transform: rotate(180deg); }

.courseware__chapter-panel {
  display: grid;
  grid-template-rows: 0fr;
  visibility: hidden;
  transition: grid-template-rows 200ms ease, visibility 200ms ease;
}

.courseware__chapter-panel--open { grid-template-rows: 1fr; visibility: visible; }
.courseware__chapter-panel-inner { min-height: 0; overflow: hidden; }

.courseware__deck-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 0;
  padding: 8px 0 16px;
  list-style: none;
}

.courseware__deck-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 44px;
  padding: 8px 12px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-family: var(--font-ui);
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
  transition: background 150ms ease;
}

.courseware__deck-option:hover,
.courseware__deck-option[aria-pressed="true"] { background: color-mix(in srgb, var(--text) 6%, transparent); }
.courseware__deck-check { flex: none; width: 18px; height: 18px; }
.courseware__chapter-trigger:focus-visible,
.courseware__deck-option:focus-visible { outline: none; box-shadow: var(--focus-ring); border-radius: 4px; }

@media (prefers-reduced-motion: reduce) {
  .courseware__chapter-panel,
  .courseware__chapter-chevron,
  .courseware__deck-option { transition: none; }
}

.courseware__back {
  display: inline-grid;
  flex: 0 0 50px;
  width: 50px;
  height: 50px;
  min-width: 50px;
  min-height: 50px;
  padding: 0;
  place-items: center;
}

.courseware__back:hover { text-decoration: none; }

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

.courseware__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.courseware__select-current {
  --liquid-width: 180px;
  --liquid-height: 50px;
  margin-left: auto;
}

.courseware__select-current :deep(.liquid-metal-button__content-layer) {
  font-size: 16px;
  font-weight: 700;
}

.courseware__icon-button {
  border: 1px solid color-mix(in srgb, var(--text) 10%, transparent);
  border-radius: 50%;
  background: color-mix(in srgb, var(--surface) 38%, transparent);
  box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset 0 0 2px color-mix(in srgb, var(--text) 32%, transparent), 0 4px 8px color-mix(in srgb, var(--text) 17%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.14);
  backdrop-filter: blur(7px) saturate(1.14);
  color: var(--text);
  cursor: pointer;
  transition: transform 180ms ease, filter 180ms ease, box-shadow 180ms ease;
}

.courseware__icon-button:hover { transform: translateY(-1px) scale(1.04); filter: brightness(1.06); }
.courseware__icon-button:focus-visible { outline: none; box-shadow: var(--focus-ring); }

.courseware__silver-control {
  --liquid-width: 50px;
  --liquid-height: 50px;
  flex: 0 0 50px;
}

.courseware__icon { display: block; width: 24px; height: 24px; }

.courseware__icon-label {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

:global([data-theme="dark"]) .courseware__icon { filter: invert(1); }

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
}

@media (max-width: 640px) {
  .courseware__select-current { margin-left: 0; }
}

@media (prefers-reduced-transparency: reduce) {
  .courseware__decks,
  .courseware__viewer { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
</style>
