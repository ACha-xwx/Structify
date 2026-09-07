<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { auth } from "../../app/providers/runtime";
import { useLocale } from "../../shared/i18n/locale";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import { presentationApi } from "../runtime";
import type { PresentationPlanResponse, PresentationScene, PresentationSlide } from "../adapters/presentation";

const route = useRoute();
const { isEnglish } = useLocale();
const presentationSources = ["home", "workbench", "chapter", "coach", "classroom"] as const;
type PresentationSource = typeof presentationSources[number];

function readQueryValue(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

const lessonId = computed(() => readQueryValue(route.query.lessonId) || "01-01A");
const from = computed<PresentationSource | null>(() => {
  const value = readQueryValue(route.query.from);
  return presentationSources.includes(value as PresentationSource) ? value as PresentationSource : null;
});
const chapterId = computed(() => readQueryValue(route.query.chapterId));
const sessionId = computed(() => readQueryValue(route.query.sessionId));
const response = ref<PresentationPlanResponse | null>(null);
const loading = ref(true);
const error = ref<UserErrorPresentation | null>(null);
const activeIndex = ref(0);
const isPlaying = ref(false);
const imageFailed = ref(false);
const reducedMotion = ref(false);
type PresentationLocalError = "no-playable-slides" | "service-not-ready";
const localErrorKey = ref<PresentationLocalError | null>(null);
let playTimer: number | undefined;
let loadVersion = 0;
let motionQuery: MediaQueryList | null = null;

const copy = computed(() => isEnglish.value ? {
  fallbackTitle: "Courseware",
  localPreviewStatus: "Courseware preview",
  serviceReadyStatus: "Courseware service connected",
  serviceUnavailableStatus: "Courseware service is not ready",
  localImageFailure: "The local preview image could not load. Reopen the courseware preview and try again.",
  remoteImageFailure: "The courseware plan is connected, but the signed image request failed. Reconnect and try again.",
  returnHome: "Back to product home",
  returnWorkbench: "Back to workbench",
  returnClassroom: "Back to classroom",
  returnCoach: "Back to course Q&A",
  returnChapter: "Back to chapter",
  returnChapters: "Back to course map",
  noPlayableSlidesTitle: "No playable courseware is available",
  noPlayableSlidesDetail: (lesson: string) => `Lesson ${lesson} has no verified slides to display.`,
  serviceNotReadyDetail: "Courseware is connected, but there is no render index available yet.",
  loadingTitle: "Opening courseware",
  loadingDetail: "Preparing slides and playback controls.",
  reconnect: "Reconnect",
  scenesAria: "Courseware scenes",
  scenesTitle: "Lesson scenes",
  sceneCount: (count: number) => `${count} scene${count === 1 ? "" : "s"}`,
  sceneNavigation: "Scene navigation",
  slideCount: (count: number) => `${count} slide${count === 1 ? "" : "s"}`,
  sceneHint: "Use the left and right arrow keys to change slides. Press Space to play or pause.",
  stageAria: "Courseware player",
  slidePosition: (current: number, total: number) => `Slide ${current} of ${total}`,
  slideAlt: (deck: string, slide: number) => `${deck}, slide ${slide}`,
  fallbackMarker: (slide: number) => `SLIDE ${String(slide).padStart(2, "0")}`,
  imageUnavailableTitle: "This slide image could not load",
  reloadSlide: "Reload this slide",
  fallbackSlideTitle: "Course slide",
  previousSlide: "Previous slide",
  playCourseware: "Play courseware",
  pausePlayback: "Pause playback",
  nextSlide: "Next slide",
  reset: "Reset",
  progressAria: "Courseware progress",
  notesAria: "Current slide details",
  slideDetails: "Slide details",
  noSupplement: "No additional explanation is available for this slide.",
  concepts: "Concepts",
  source: "Source",
  slideSource: (slide: number, chapter: string) => `Slide ${slide}, chapter ${chapter}`,
  speakerNotes: "Speaker notes",
  playbackSource: "Playback source",
  localSource: "Verified local courseware preview",
  publishedSource: "Published courseware",
  learningRecord: "Learning record",
  fixtureRecord: "Guest previews do not save a personal record.",
  authenticatedRecord: "Signing in can record courseware learning activity.",
  currentSlide: "Current slide",
  notLoaded: "Not loaded",
  localErrors: {
    "no-playable-slides": { title: "No playable courseware is available", message: "The selected lesson has no verified slides to display." },
    "service-not-ready": { title: "Courseware is not ready", message: "Courseware is connected, but there is no render index available yet." },
  },
  errors: {
    permission: { title: "Sign in to open this courseware", message: "Sign in to access this courseware." },
    "not-found": { title: "Courseware is unavailable", message: "This courseware may be unpublished, removed, or outside this account's access." },
    conflict: { title: "This page has changed", message: "Refresh and try again to use the latest learning state." },
    limited: { title: "Please wait before trying again", message: "The learning service is temporarily limiting requests." },
    timeout: { title: "The request timed out", message: "The learning service did not respond in time." },
    service: { title: "Learning services are temporarily unavailable", message: "This page remains available. Try again when the service recovers." },
    network: { title: "Network connection is unavailable", message: "Check the connection and try again. Your current page remains available." },
    validation: { title: "This request cannot be processed", message: "Check the courseware request and try again." },
    unknown: { title: "This action was not completed", message: "The learning service returned an unexpected result. Try again." },
  },
} : {
  fallbackTitle: "课程课件",
  localPreviewStatus: "本地课件预览",
  serviceReadyStatus: "已连接课件服务",
  serviceUnavailableStatus: "课件服务未就绪",
  localImageFailure: "本地预览图片暂时无法加载。请重新打开课件预览后再试。",
  remoteImageFailure: "播放计划已连接，但签名图片请求失败。请重新连接后再试。",
  returnHome: "返回产品首页",
  returnWorkbench: "返回学习台",
  returnClassroom: "返回课堂",
  returnCoach: "返回问答陪练",
  returnChapter: "返回本章",
  returnChapters: "返回章节目录",
  noPlayableSlidesTitle: "没有可播放的课件",
  noPlayableSlidesDetail: (lesson: string) => `课时 ${lesson} 没有可展示的已核验页面。`,
  serviceNotReadyDetail: "课件服务已连接，但当前没有可用的渲染索引。",
  loadingTitle: "正在打开课件",
  loadingDetail: "正在准备页面和播放控制。",
  reconnect: "重新连接",
  scenesAria: "课件场景",
  scenesTitle: "章节场景",
  sceneCount: (count: number) => `${count} 个`,
  sceneNavigation: "场景导航",
  slideCount: (count: number) => `${count} 页`,
  sceneHint: "左右方向键切换页面，空格播放或暂停。",
  stageAria: "课件播放舞台",
  slidePosition: (current: number, total: number) => `第 ${current} / ${total} 页`,
  slideAlt: (deck: string, slide: number) => `${deck} 第 ${slide} 页`,
  fallbackMarker: (slide: number) => `第 ${String(slide).padStart(2, "0")} 页`,
  imageUnavailableTitle: "课件图片暂时无法加载",
  reloadSlide: "重新加载本页",
  fallbackSlideTitle: "课程页面",
  previousSlide: "上一页",
  playCourseware: "播放课件",
  pausePlayback: "暂停播放",
  nextSlide: "下一页",
  reset: "重置",
  progressAria: "课件进度",
  notesAria: "当前页面说明",
  slideDetails: "本页说明",
  noSupplement: "服务端没有提供此页的补充讲解。",
  concepts: "相关概念",
  source: "来源",
  slideSource: (slide: number, chapter: string) => `第 ${slide} 页 · 第 ${chapter} 章`,
  speakerNotes: "讲者备注",
  playbackSource: "播放来源",
  localSource: "本地已核验课件预览",
  publishedSource: "已发布课程课件",
  learningRecord: "学习记录",
  fixtureRecord: "游客预览不会保存个人记录。",
  authenticatedRecord: "登录后可记录课件学习活动。",
  currentSlide: "当前页",
  notLoaded: "尚未载入",
  localErrors: {
    "no-playable-slides": { title: "没有可播放的课件", message: "当前课时没有可展示的已核验页面。" },
    "service-not-ready": { title: "课件服务未就绪", message: "课件服务已连接，但当前没有可用的渲染索引。" },
  },
  errors: {
    permission: { title: "登录后解锁此功能", message: "登录后可访问此课程课件。" },
    "not-found": { title: "课件不可访问", message: "该课件可能未发布、已移除，或当前账号没有访问范围。" },
    conflict: { title: "当前状态已变化", message: "请刷新后继续操作，避免覆盖最新学习状态。" },
    limited: { title: "请求过于频繁", message: "学习服务暂时限制了请求，请稍后重试。" },
    timeout: { title: "请求超时", message: "学习服务未在规定时间内响应。" },
    service: { title: "学习服务暂不可用", message: "当前页面位置已保留，服务恢复后可再次尝试。" },
    network: { title: "网络连接不可用", message: "请检查网络后重试，当前学习位置不会丢失。" },
    validation: { title: "提交内容无法处理", message: "请检查课件请求后重试。" },
    unknown: { title: "操作未完成", message: "服务返回了未预期的结果，请重试。" },
  },
});

const sceneNames = computed<Record<string, string>>(() => isEnglish.value ? {
  intro: "Introduction",
  "concept-one": "Concept one",
  "concept-two": "Concept two",
  practice: "Practice",
  transfer: "Apply",
  summary: "Summary",
} : {
  intro: "导入",
  "concept-one": "概念一",
  "concept-two": "概念二",
  practice: "练习",
  transfer: "迁移",
  summary: "小结",
});

const plan = computed(() => response.value?.plan ?? null);
const slides = computed<PresentationSlide[]>(() => {
  const current = response.value;
  const order = current?.plan?.slideOrder ?? [];
  if (!current) return [];
  return order.map((id) => current.slides[id]).filter((slide): slide is PresentationSlide => Boolean(slide?.imageUrl));
});
const activeSlide = computed(() => slides.value[activeIndex.value] ?? null);
const currentScene = computed(() => {
  const currentId = activeSlide.value?.id;
  if (!currentId || !plan.value) return null;
  return Object.entries(plan.value.scenes).find(([, scene]) => scene.slides.includes(currentId)) ?? null;
});
const sceneEntries = computed(() => {
  if (!plan.value) return [] as Array<{ id: string; scene: PresentationScene; label: string; slideCount: number; active: boolean }>;
  return Object.entries(plan.value.scenes).map(([id, scene]) => ({
    id,
    scene,
    label: sceneNames.value[id] || id,
    slideCount: scene.slides.filter((slideId) => response.value?.slides[slideId]).length,
    active: currentScene.value?.[0] === id,
  })).filter((entry) => entry.slideCount > 0);
});
const progressPercent = computed(() => slides.value.length ? ((activeIndex.value + 1) / slides.value.length) * 100 : 0);
const isLocalPreview = computed(() => response.value?.source === "local-preview");
const stageStatus = computed(() => {
  if (isLocalPreview.value) return copy.value.localPreviewStatus;
  return response.value?.ready ? copy.value.serviceReadyStatus : copy.value.serviceUnavailableStatus;
});
const imageFailureMessage = computed(() => isLocalPreview.value
  ? copy.value.localImageFailure
  : copy.value.remoteImageFailure);
const displayedError = computed(() => {
  if (localErrorKey.value) {
    return { kind: "not-found" as const, retryable: true, ...copy.value.localErrors[localErrorKey.value] };
  }
  return error.value ? localizedUserError(error.value) : null;
});
const returnTarget = computed(() => {
  if (from.value === "home") return { path: "/" };
  if (from.value === "workbench") return { path: "/user/home" };
  if (from.value === "classroom") {
    return {
      path: "/user/classroom",
      query: {
        ...(chapterId.value ? { chapterId: chapterId.value } : {}),
        ...(sessionId.value ? { sessionId: sessionId.value } : {}),
      },
    };
  }
  if (from.value === "coach") return { path: "/user/coach", query: chapterId.value ? { chapterId: chapterId.value } : undefined };
  if (chapterId.value) return { path: `/user/chapters/${encodeURIComponent(chapterId.value)}` };
  return { path: "/user/chapters" };
});
const returnLabel = computed(() => {
  if (from.value === "home") return copy.value.returnHome;
  if (from.value === "workbench") return copy.value.returnWorkbench;
  if (from.value === "classroom") return copy.value.returnClassroom;
  if (from.value === "coach") return copy.value.returnCoach;
  return chapterId.value ? copy.value.returnChapter : copy.value.returnChapters;
});

function localizedUserError(value: UserErrorPresentation): UserErrorPresentation {
  return { ...value, ...copy.value.errors[value.kind] };
}

function clearPlayback() {
  isPlaying.value = false;
  if (playTimer !== undefined) {
    window.clearInterval(playTimer);
    playTimer = undefined;
  }
}

function moveTo(index: number) {
  clearPlayback();
  activeIndex.value = Math.max(0, Math.min(slides.value.length - 1, index));
  imageFailed.value = false;
}

function previous() { moveTo(activeIndex.value - 1); }
function next() { moveTo(activeIndex.value + 1); }
function reset() { moveTo(0); }

function togglePlayback() {
  if (reducedMotion.value || !slides.value.length) return;
  if (isPlaying.value) {
    clearPlayback();
    return;
  }
  if (activeIndex.value >= slides.value.length - 1) activeIndex.value = 0;
  isPlaying.value = true;
  playTimer = window.setInterval(() => {
    if (activeIndex.value >= slides.value.length - 1) {
      clearPlayback();
      return;
    }
    activeIndex.value += 1;
    imageFailed.value = false;
  }, 4200);
}

function selectScene(id: string) {
  const entry = sceneEntries.value.find((item) => item.id === id);
  if (!entry) return;
  const targetId = entry.scene.primarySlideId || entry.scene.slides[0];
  const targetIndex = slides.value.findIndex((slide) => slide.id === targetId);
  if (targetIndex >= 0) moveTo(targetIndex);
}

function handleKeydown(event: KeyboardEvent) {
  const target = event.target;
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) return;
  if (event.key === "ArrowLeft" || event.key === "PageUp") { event.preventDefault(); previous(); }
  else if (event.key === "ArrowRight" || event.key === "PageDown") { event.preventDefault(); next(); }
  else if (event.key === "Home") { event.preventDefault(); reset(); }
  else if (event.key === "End") { event.preventDefault(); moveTo(slides.value.length - 1); }
  else if (event.key === " ") { event.preventDefault(); togglePlayback(); }
}

function updateMotionPreference() {
  reducedMotion.value = Boolean(motionQuery?.matches);
  if (reducedMotion.value) clearPlayback();
}

async function load() {
  const version = ++loadVersion;
  loading.value = true;
  error.value = null;
  localErrorKey.value = null;
  response.value = null;
  activeIndex.value = 0;
  imageFailed.value = false;
  clearPlayback();
  try {
    const nextResponse = await presentationApi.getPlan(lessonId.value, { preferLocalPreview: !auth.state.user });
    if (version !== loadVersion) return;
    response.value = nextResponse;
    if (!nextResponse.plan || !slides.value.length) {
      localErrorKey.value = nextResponse.ready ? "no-playable-slides" : "service-not-ready";
    }
  } catch (cause) {
    if (version === loadVersion) error.value = presentUserError(cause);
  } finally {
    if (version === loadVersion) loading.value = false;
  }
}

watch(lessonId, () => { void load(); }, { immediate: true });
onMounted(() => {
  motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  updateMotionPreference();
  motionQuery.addEventListener?.("change", updateMotionPreference);
  window.addEventListener("keydown", handleKeydown);
});
onBeforeUnmount(() => {
  clearPlayback();
  window.removeEventListener("keydown", handleKeydown);
  motionQuery?.removeEventListener?.("change", updateMotionPreference);
});
</script>

<template>
  <UserFrame shell="course">
    <section class="presentation-page" aria-labelledby="presentation-title">
      <header class="presentation-heading">
        <div>
          <h1 id="presentation-title">{{ plan?.title || copy.fallbackTitle }}</h1>
        </div>
        <div class="presentation-heading__actions">
          <span class="presentation-status" :data-ready="response?.ready ? 'true' : 'false'"><i></i>{{ stageStatus }}</span>
          <RouterLink class="user-action" data-testid="presentation-return" :to="returnTarget">{{ returnLabel }}</RouterLink>
        </div>
      </header>

       <UserState v-if="loading" mode="loading" :title="copy.loadingTitle" :message="copy.loadingDetail" />
      <UserState v-else-if="displayedError" :mode="displayedError.kind === 'permission' ? 'permission' : displayedError.kind === 'not-found' ? 'empty' : 'error'" :title="displayedError.title" :message="displayedError.message" :retry-label="displayedError.retryable ? copy.reconnect : undefined" @retry="load" />

      <template v-else-if="response && plan && activeSlide">
        <div class="presentation-layout">
          <details class="presentation-scenes" :aria-label="copy.scenesAria">
            <summary>
                <div class="presentation-scenes__head"><span>{{ copy.scenesTitle }}</span><strong>{{ copy.sceneCount(sceneEntries.length) }}</strong></div>
              <span class="presentation-scenes__summary-note">{{ currentScene ? (sceneNames[currentScene[0]] || currentScene[0]) : copy.sceneNavigation }}</span>
              <span class="presentation-disclosure__chevron" aria-hidden="true"></span>
            </summary>
            <div class="presentation-scenes__body">
              <nav>
                <button v-for="entry in sceneEntries" :key="entry.id" type="button" :class="{ 'is-active': entry.active }" @click="selectScene(entry.id)">
                  <span class="presentation-scene__index">{{ String(sceneEntries.indexOf(entry) + 1).padStart(2, '0') }}</span>
                  <span><strong>{{ entry.label }}</strong><small>{{ copy.slideCount(entry.slideCount) }}</small></span>
                  <i aria-hidden="true"></i>
                </button>
              </nav>
              <p class="presentation-scenes__hint">{{ copy.sceneHint }}</p>
            </div>
          </details>

          <section class="presentation-stage" :aria-label="copy.stageAria" aria-live="polite">
            <div class="presentation-stage__topline"><span>{{ activeSlide.deckTitle }}</span><span>{{ copy.slidePosition(activeIndex + 1, slides.length) }}</span></div>
            <div class="presentation-slide-frame" :data-image-failed="imageFailed ? 'true' : 'false'">
              <img v-if="!imageFailed" :src="activeSlide.imageUrl" :alt="copy.slideAlt(activeSlide.deckTitle, activeSlide.slideNumber)" draggable="false" @error="imageFailed = true" />
              <div v-else class="presentation-slide-fallback" role="alert"><span>{{ copy.fallbackMarker(activeSlide.slideNumber) }}</span><strong>{{ copy.imageUnavailableTitle }}</strong><p>{{ imageFailureMessage }}</p><button class="user-action user-action--primary" type="button" @click="imageFailed = false">{{ copy.reloadSlide }}</button></div>
            </div>
            <div class="presentation-stage__caption"><span>{{ String(activeIndex + 1).padStart(2, '0') }}</span><strong>{{ activeSlide.title || activeSlide.semanticSummary || copy.fallbackSlideTitle }}</strong><span v-if="currentScene">{{ sceneNames[currentScene[0]] || currentScene[0] }}</span></div>
            <footer class="presentation-controls">
              <div class="presentation-controls__buttons">
                <button type="button" :aria-label="copy.previousSlide" :title="copy.previousSlide" :disabled="activeIndex === 0" @click="previous"><span aria-hidden="true">←</span></button>
                <button class="presentation-controls__play" type="button" :aria-label="isPlaying ? copy.pausePlayback : copy.playCourseware" :title="isPlaying ? copy.pausePlayback : copy.playCourseware" :disabled="reducedMotion" @click="togglePlayback"><span v-if="isPlaying" class="presentation-pause" aria-hidden="true"></span><span v-else aria-hidden="true">▶</span></button>
                <button type="button" :aria-label="copy.nextSlide" :title="copy.nextSlide" :disabled="activeIndex >= slides.length - 1" @click="next"><span aria-hidden="true">→</span></button>
                <button class="presentation-controls__reset" type="button" @click="reset">{{ copy.reset }}</button>
              </div>
              <div class="presentation-progress" :aria-label="copy.progressAria"><span :style="{ width: `${progressPercent}%` }"></span></div>
              <span class="presentation-controls__count">{{ activeIndex + 1 }} / {{ slides.length }}</span>
            </footer>
          </section>

          <details class="presentation-notes" :aria-label="copy.notesAria">
            <summary>
              <span class="presentation-notes__summary-copy">
                 <span class="presentation-notes__label">{{ copy.slideDetails }}</span>
                <strong>{{ activeSlide.semanticSummary || activeSlide.title || copy.fallbackSlideTitle }}</strong>
              </span>
              <span class="presentation-disclosure__chevron" aria-hidden="true"></span>
            </summary>
            <div class="presentation-notes__body">
              <div class="presentation-notes__section"><h2>{{ activeSlide.semanticSummary || activeSlide.title || copy.fallbackSlideTitle }}</h2><p>{{ activeSlide.teachingFocus || activeSlide.rawText || copy.noSupplement }}</p></div>
              <div v-if="activeSlide.concepts.length" class="presentation-notes__section"><span class="presentation-notes__label">{{ copy.concepts }}</span><div class="presentation-tags"><span v-for="concept in activeSlide.concepts" :key="concept">{{ concept }}</span></div></div>
              <div class="presentation-notes__section presentation-notes__source"><span class="presentation-notes__label">{{ copy.source }}</span><strong>{{ activeSlide.deckTitle }}</strong><p>{{ copy.slideSource(activeSlide.slideNumber, activeSlide.chapter) }}</p><p v-if="activeSlide.speakerNotes">{{ copy.speakerNotes }}: {{ activeSlide.speakerNotes }}</p></div>
            </div>
          </details>
        </div>
      </template>
    </section>
    <template #rail><div class="user-rail-list"><strong>{{ copy.playbackSource }}</strong><p>{{ isLocalPreview ? copy.localSource : copy.publishedSource }}</p><strong>{{ copy.learningRecord }}</strong><p>{{ isLocalPreview ? copy.fixtureRecord : copy.authenticatedRecord }}</p><strong>{{ copy.currentSlide }}</strong><p>{{ activeSlide ? `${activeSlide.deckTitle} · ${activeSlide.slideNumber}` : copy.notLoaded }}</p></div></template>
  </UserFrame>
</template>

<style scoped>
.presentation-page { --presentation-ink: #252422; --presentation-muted: #716e68; --presentation-line: rgba(47, 45, 41, .16); --presentation-stage: #0b0b0a; --presentation-stage-soft: #1e1d1b; --presentation-highlight: #d2cfc7; min-width: 0; }
.presentation-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 1.5rem; margin-bottom: 1.35rem; }
.presentation-heading h1 { margin: .2rem 0 .4rem; color: var(--presentation-ink); font-family: var(--font-ui, var(--font-sans)); font-size: clamp(1.65rem, 3vw, 2.75rem); letter-spacing: 0; line-height: 1.04; }
.presentation-heading__intro { max-width: 62ch; margin: 0; color: var(--presentation-muted); font-size: .86rem; line-height: 1.65; }
.presentation-heading__actions { display: flex; align-items: center; gap: .55rem; flex-wrap: wrap; justify-content: flex-end; }
.presentation-status { display: inline-flex; align-items: center; gap: .45rem; min-height: 36px; padding: 0 .7rem; border: 1px solid var(--presentation-line); color: var(--presentation-muted); font-family: var(--font-mono); font-size: .63rem; letter-spacing: .03em; white-space: nowrap; }
.presentation-status i { width: .42rem; height: .42rem; border-radius: 50%; background: #77746e; }
.presentation-status[data-ready="true"] i { background: #77746e; box-shadow: 0 0 0 4px rgba(90, 88, 83, .12); }
.presentation-layout { display: grid; min-width: 0; grid-template-columns: minmax(0, 1fr); gap: .75rem; }
.presentation-scenes, .presentation-notes { min-width: 0; border: 1px solid var(--presentation-line); background: color-mix(in srgb, var(--surface) 87%, transparent); }
.presentation-scenes, .presentation-notes { display: block; }
.presentation-scenes { align-self: start; padding: .75rem; order: 2; }
.presentation-scenes > summary,
.presentation-notes > summary { display: flex; align-items: center; justify-content: space-between; gap: .75rem; cursor: pointer; list-style: none; }
.presentation-scenes > summary::-webkit-details-marker,
.presentation-notes > summary::-webkit-details-marker { display: none; }
.presentation-scenes__head { display: flex; min-width: 0; align-items: baseline; justify-content: space-between; padding: 0; border-bottom: 0; color: var(--presentation-muted); font-family: var(--font-mono); font-size: .61rem; letter-spacing: .12em; }
.presentation-scenes__head strong { color: var(--presentation-ink); font-size: .68rem; }
.presentation-scenes__summary-note { min-width: 0; overflow: hidden; color: var(--presentation-muted); font-family: var(--font-mono); font-size: .61rem; text-overflow: ellipsis; white-space: nowrap; }
.presentation-disclosure__chevron { width: 8px; height: 8px; flex: 0 0 auto; border-right: 1px solid currentColor; border-bottom: 1px solid currentColor; color: var(--presentation-muted); transform: rotate(45deg) translateY(-2px); transition: transform 180ms cubic-bezier(.22, 1, .36, 1); }
.presentation-scenes[open] > summary .presentation-disclosure__chevron,
.presentation-notes[open] > summary .presentation-disclosure__chevron { transform: rotate(225deg) translate(-2px, -2px); }
.presentation-scenes__body,
.presentation-notes__body { padding-top: .7rem; animation: presentation-disclosure-in 200ms cubic-bezier(.22, 1, .36, 1) both; }
.presentation-scenes nav { display: grid; gap: .25rem; padding-top: .55rem; }
.presentation-scenes button { display: grid; grid-template-columns: 1.25rem minmax(0, 1fr) .35rem; min-height: 52px; align-items: center; gap: .4rem; padding: .4rem .35rem; border: 1px solid transparent; background: transparent; color: var(--presentation-muted); cursor: pointer; text-align: left; transition: border-color 150ms ease, background-color 150ms ease, color 150ms ease, transform 150ms ease; }
.presentation-scenes button:hover { border-color: var(--presentation-line); background: rgba(255,255,255,.42); color: var(--presentation-ink); transform: translateX(2px); }
.presentation-scenes button.is-active { border-color: #73706a; background: #dedbd4; color: var(--presentation-ink); }
.presentation-scenes button i { width: .32rem; height: .32rem; border-radius: 50%; background: transparent; }
.presentation-scenes button.is-active i { background: #22211f; box-shadow: 0 0 0 3px rgba(0, 0, 0, .12); }
.presentation-scene__index { align-self: start; padding-top: .15rem; color: #999690; font-family: var(--font-mono); font-size: .59rem; }
.presentation-scenes strong, .presentation-scenes small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.presentation-scenes strong { font-size: .72rem; }
.presentation-scenes small { margin-top: .16rem; color: #999690; font-family: var(--font-mono); font-size: .58rem; }
.presentation-scenes__hint { margin: .9rem .15rem .1rem; color: #999690; font-size: .65rem; line-height: 1.55; }
.presentation-stage { min-width: 0; overflow: hidden; order: 1; background: var(--presentation-stage); color: #eeece7; box-shadow: 0 18px 40px rgba(0, 0, 0, .16); }
.presentation-stage__topline { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .75rem .9rem; border-bottom: 1px solid rgba(220, 218, 212, .16); color: rgba(235, 233, 226, .7); font-family: var(--font-mono); font-size: .61rem; letter-spacing: .04em; }
.presentation-stage__topline span:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.presentation-slide-frame { position: relative; display: grid; aspect-ratio: 16 / 9; min-height: 0; place-items: center; overflow: hidden; padding: clamp(.55rem, 1.6vw, 1rem); background: radial-gradient(circle at 70% 12%, rgba(220, 218, 212, .14), transparent 34%), linear-gradient(140deg, var(--presentation-stage-soft), var(--presentation-stage)); }
.presentation-slide-frame::before { position: absolute; inset: 0; background-image: linear-gradient(rgba(220, 218, 212, .045) 1px, transparent 1px), linear-gradient(90deg, rgba(220, 218, 212, .045) 1px, transparent 1px); background-size: 36px 36px; content: ""; opacity: .48; mask-image: linear-gradient(to bottom, rgba(0,0,0,.7), transparent 82%); }
.presentation-slide-frame img { position: relative; z-index: 1; display: block; width: 100%; height: 100%; max-height: none; object-fit: contain; background: #e4e1da; box-shadow: 0 18px 36px rgba(0,0,0,.28); }
.presentation-slide-frame__stamp { position: absolute; z-index: 2; right: 1.2rem; bottom: .85rem; color: rgba(235, 233, 226, .55); font-family: var(--font-mono); font-size: .56rem; letter-spacing: .08em; }
.presentation-slide-fallback { position: relative; z-index: 1; display: grid; max-width: 28rem; gap: .6rem; place-items: center; padding: 2rem; border: 1px solid rgba(220, 218, 212, .22); color: #eeece7; text-align: center; }
.presentation-slide-fallback span { color: var(--presentation-highlight); font-family: var(--font-mono); font-size: .64rem; letter-spacing: .13em; }
.presentation-slide-fallback strong { font-size: 1.1rem; }
.presentation-slide-fallback p { margin: 0; color: rgba(235, 233, 226, .68); font-size: .8rem; line-height: 1.55; }
.presentation-stage__caption { display: grid; grid-template-columns: 2.1rem minmax(0, 1fr) auto; align-items: center; gap: .7rem; padding: .75rem .9rem .85rem; border-top: 1px solid rgba(220, 218, 212, .16); }
.presentation-stage__caption > span:first-child { color: var(--presentation-highlight); font-family: var(--font-mono); font-size: .68rem; }
.presentation-stage__caption strong { overflow: hidden; color: #f0eee8; font-size: .78rem; text-overflow: ellipsis; white-space: nowrap; }
.presentation-stage__caption > span:last-child { color: rgba(235, 233, 226, .62); font-family: var(--font-mono); font-size: .6rem; }
.presentation-controls { display: grid; grid-template-columns: auto minmax(80px, 1fr) auto; align-items: center; gap: .75rem; padding: .7rem .9rem .85rem; border-top: 1px solid rgba(220, 218, 212, .16); background: rgba(14, 14, 13, .26); }
.presentation-controls__buttons { display: flex; align-items: center; gap: .32rem; }
.presentation-controls button { display: grid; width: 34px; height: 34px; place-items: center; padding: 0; border: 1px solid rgba(220, 218, 212, .2); background: rgba(220, 218, 212, .08); color: #eeece7; cursor: pointer; font: inherit; transition: background-color 150ms ease, transform 150ms ease, border-color 150ms ease; }
.presentation-controls button:hover:not(:disabled) { border-color: rgba(220, 218, 212, .52); background: rgba(220, 218, 212, .16); }
.presentation-controls button:active:not(:disabled) { transform: scale(.95); }
.presentation-controls button:disabled { cursor: not-allowed; opacity: .32; }
.presentation-controls__play { border-color: rgba(220, 218, 212, .6) !important; background: rgba(220, 218, 212, .16) !important; color: var(--presentation-highlight) !important; }
.presentation-controls__reset { width: auto !important; padding: 0 .55rem !important; color: rgba(235, 233, 226, .72) !important; font-family: var(--font-mono) !important; font-size: .58rem !important; }
.presentation-pause { display: inline-flex; width: 8px; height: 12px; border-right: 2px solid currentColor; border-left: 2px solid currentColor; }
.presentation-progress { height: 2px; overflow: hidden; background: rgba(220, 218, 212, .2); }
.presentation-progress span { display: block; height: 100%; background: var(--presentation-highlight); transition: width 260ms cubic-bezier(.16, 1, .3, 1); }
.presentation-controls__count { color: rgba(235, 233, 226, .6); font-family: var(--font-mono); font-size: .62rem; white-space: nowrap; }
.presentation-notes { align-self: start; padding: 1rem; order: 3; }
.presentation-notes__summary-copy { display: grid; min-width: 0; gap: .14rem; }
.presentation-notes__summary-copy strong { overflow: hidden; color: var(--presentation-ink); font-size: .82rem; text-overflow: ellipsis; white-space: nowrap; }
.presentation-notes__section { padding-bottom: 1rem; border-bottom: 1px solid var(--presentation-line); }
.presentation-notes__section + .presentation-notes__section { padding-top: 1rem; }
.presentation-notes__section:last-child { padding-bottom: 0; border-bottom: 0; }
.presentation-notes__label { display: block; margin-bottom: .5rem; color: #8f8b84; font-family: var(--font-mono); font-size: .59rem; letter-spacing: .11em; }
.presentation-notes h2 { margin: 0; color: var(--presentation-ink); font-size: .95rem; line-height: 1.3; }
.presentation-notes p { margin: .48rem 0 0; color: var(--presentation-muted); font-size: .72rem; line-height: 1.62; }
.presentation-notes__source strong { display: block; color: var(--presentation-ink); font-size: .72rem; line-height: 1.4; }
.presentation-tags { display: flex; flex-wrap: wrap; gap: .32rem; }
.presentation-tags span { padding: .26rem .4rem; border: 1px solid var(--presentation-line); color: #66625c; font-family: var(--font-mono); font-size: .58rem; }

@media (max-width: 1120px) { .presentation-scenes { padding: .55rem; } .presentation-notes { padding: .75rem; } }
@media (max-width: 840px) { .presentation-heading { align-items: flex-start; flex-direction: column; } .presentation-heading__actions { justify-content: flex-start; } .presentation-layout { grid-template-columns: 1fr; } .presentation-scenes { order: 2; } .presentation-scenes nav { grid-template-columns: repeat(3, minmax(0, 1fr)); } .presentation-scenes__hint { display: none; } .presentation-notes { order: 3; } .presentation-slide-frame { min-height: 0; } }
@media (max-width: 520px) { .presentation-heading h1 { font-size: 1.6rem; } .presentation-heading__intro { font-size: .78rem; } .presentation-stage__topline { align-items: flex-start; flex-direction: column; gap: .28rem; } .presentation-slide-frame { min-height: 0; padding: .5rem; } .presentation-stage__caption { grid-template-columns: 1.7rem minmax(0, 1fr); gap: .45rem; } .presentation-stage__caption > span:last-child { grid-column: 2; } .presentation-controls { grid-template-columns: 1fr auto; } .presentation-progress { grid-column: 1 / -1; grid-row: 1; } .presentation-controls__count { grid-column: 2; grid-row: 2; } .presentation-controls__buttons { grid-column: 1; grid-row: 2; } .presentation-scenes nav { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (prefers-reduced-motion: reduce) { .presentation-scenes button, .presentation-controls button, .presentation-progress span { transition: none; } .presentation-scenes__body, .presentation-notes__body { animation: none; } }
@media (prefers-reduced-transparency: reduce) { .presentation-scenes, .presentation-notes { background: var(--surface); } }

@keyframes presentation-disclosure-in { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }

/* Evolux-style disclosure rail: the verified slide stays the only large
   surface; scene navigation and teaching context open on demand. */
.presentation-layout { align-items: start; }
.presentation-scenes, .presentation-notes { padding: 0; border-radius: 0; }
.presentation-scenes > summary, .presentation-notes > summary { display: flex; min-height: 76px; align-items: center; justify-content: space-between; gap: .7rem; padding: .8rem .85rem; cursor: pointer; list-style: none; }
.presentation-scenes > summary::-webkit-details-marker, .presentation-notes > summary::-webkit-details-marker { display: none; }
.presentation-scenes__head { display: grid; gap: .2rem; padding: 0; border: 0; color: var(--presentation-ink); font-family: var(--font-sans); font-size: .82rem; letter-spacing: 0; }
.presentation-scenes__head span { font-weight: 700; }
.presentation-scenes__head strong { color: var(--presentation-muted); font-family: var(--font-mono); font-size: .58rem; font-weight: 500; letter-spacing: .08em; }
.presentation-scenes__summary-note { min-width: 0; overflow: hidden; color: var(--presentation-muted); font-family: var(--font-mono); font-size: .58rem; text-overflow: ellipsis; white-space: nowrap; }
.presentation-scenes__body, .presentation-notes__body { padding: .75rem; border-top: 1px solid var(--presentation-line); animation: presentation-disclosure-in 200ms cubic-bezier(.22, 1, .36, 1) both; }
.presentation-disclosure__chevron { width: 8px; height: 8px; flex: 0 0 auto; border-right: 1px solid currentColor; border-bottom: 1px solid currentColor; color: var(--presentation-muted); transform: rotate(45deg) translateY(-2px); transition: transform 180ms cubic-bezier(.22, 1, .36, 1); }
.presentation-scenes[open] > summary .presentation-disclosure__chevron, .presentation-notes[open] > summary .presentation-disclosure__chevron { transform: rotate(225deg) translate(-2px, -2px); }
.presentation-notes__summary-copy { display: grid; min-width: 0; gap: .22rem; }
.presentation-notes__summary-copy .presentation-notes__label { margin: 0; color: var(--presentation-muted); font-size: .58rem; letter-spacing: .08em; }
.presentation-notes__summary-copy strong { overflow: hidden; color: var(--presentation-ink); font-size: .78rem; text-overflow: ellipsis; white-space: nowrap; }
.presentation-notes__body .presentation-notes__section:first-child { padding-top: 0; }

.presentation-stage { background: var(--presentation-stage); color: var(--presentation-ink); box-shadow: 0 24px 56px rgba(0,0,0,.24); }
.presentation-stage__topline { border-color: rgba(220, 218, 212, .18); color: rgba(235, 233, 226, .72); }
.presentation-slide-frame { background: radial-gradient(circle at 70% 12%, rgba(220, 218, 212, .12), transparent 34%), linear-gradient(140deg, var(--presentation-stage-soft), var(--presentation-stage)); }
.presentation-slide-frame::before { background-image: linear-gradient(rgba(220, 218, 212, .06) 1px, transparent 1px), linear-gradient(90deg, rgba(220, 218, 212, .06) 1px, transparent 1px); }
.presentation-slide-frame__stamp { display: none; }
.presentation-stage__caption { border-color: rgba(220, 218, 212, .18); }
.presentation-stage__caption > span:first-child { color: var(--presentation-highlight); }
.presentation-stage__caption strong { color: var(--presentation-ink); }
.presentation-stage__caption > span:last-child { color: rgba(235, 233, 226, .64); }
.presentation-controls { border-color: rgba(220, 218, 212, .18); background: rgba(15, 15, 14, .92); }
.presentation-controls button { border-color: rgba(220, 218, 212, .28); background: rgba(220, 218, 212, .08); color: var(--presentation-ink); }
.presentation-controls button:hover:not(:disabled) { border-color: #aaa69e; background: rgba(220, 218, 212, .16); }
.presentation-controls__play { border-color: rgba(235, 233, 226, .74) !important; background: rgba(220, 218, 212, .2) !important; color: var(--presentation-ink) !important; }
.presentation-progress { background: rgba(220, 218, 212, .22); }
.presentation-progress span { background: #dedbd4; }
.presentation-controls__reset, .presentation-controls__count { color: rgba(235, 233, 226, .7) !important; }
.presentation-notes__label { color: #8f8b84; }
.presentation-tags span { color: #66625c; }

/* Keep the final neutral chrome readable after the shared dark theme is
   applied. The verified slide image remains untouched. */
:global([data-theme="dark"] .presentation-page) {
  --presentation-ink: #eeeeee;
  --presentation-muted: #b8b8b8;
  --presentation-line: rgba(220, 220, 220, .2);
  --presentation-stage: #090909;
  --presentation-stage-soft: #1b1b1b;
  --presentation-highlight: #dedede;
}
:global([data-theme="dark"] .presentation-heading h1),
:global([data-theme="dark"] .presentation-scenes__head),
:global([data-theme="dark"] .presentation-scenes__head strong),
:global([data-theme="dark"] .presentation-scenes__summary-note),
:global([data-theme="dark"] .presentation-scenes__hint),
:global([data-theme="dark"] .presentation-notes__summary-copy strong),
:global([data-theme="dark"] .presentation-notes h2),
:global([data-theme="dark"] .presentation-notes__source strong),
:global([data-theme="dark"] .presentation-notes p),
:global([data-theme="dark"] .presentation-notes__label),
:global([data-theme="dark"] .presentation-tags span),
:global([data-theme="dark"] .presentation-status),
:global([data-theme="dark"] .presentation-stage__topline),
:global([data-theme="dark"] .presentation-stage__caption),
:global([data-theme="dark"] .presentation-controls__count) {
  color: var(--presentation-ink);
}
:global([data-theme="dark"] .presentation-scenes small),
:global([data-theme="dark"] .presentation-scenes__hint),
:global([data-theme="dark"] .presentation-scene__index),
:global([data-theme="dark"] .presentation-status),
:global([data-theme="dark"] .presentation-stage__topline),
:global([data-theme="dark"] .presentation-stage__caption > span:last-child),
:global([data-theme="dark"] .presentation-controls__reset),
:global([data-theme="dark"] .presentation-controls__count) {
  color: var(--presentation-muted);
}
:global([data-theme="dark"] .presentation-scenes button:hover) { border-color: #666666; background: #303030; color: #eeeeee; }
:global([data-theme="dark"] .presentation-scenes button.is-active) { border-color: #777777; background: #3a3a3a; color: #eeeeee; }
:global([data-theme="dark"] .presentation-scenes button.is-active i) { background: #e0e0e0; box-shadow: 0 0 0 3px rgba(224,224,224,.12); }
:global([data-theme="dark"] .presentation-slide-frame) { background: radial-gradient(circle at 70% 12%, rgba(220,220,220,.12), transparent 34%), linear-gradient(140deg, var(--presentation-stage-soft), var(--presentation-stage)); }
:global([data-theme="dark"] .presentation-slide-frame::before) { background-image: linear-gradient(rgba(220,220,220,.06) 1px, transparent 1px), linear-gradient(90deg, rgba(220,220,220,.06) 1px, transparent 1px); }
:global([data-theme="dark"] .presentation-stage__topline),
:global([data-theme="dark"] .presentation-stage__caption),
:global([data-theme="dark"] .presentation-controls) { border-color: rgba(220,220,220,.18); }
:global([data-theme="dark"] .presentation-controls) { background: rgba(15,15,15,.84); }
:global([data-theme="dark"] .presentation-controls button) { border-color: rgba(220,220,220,.28); background: rgba(220,220,220,.08); color: #eeeeee; }
:global([data-theme="dark"] .presentation-controls button:hover:not(:disabled)) { border-color: #9b9b9b; background: rgba(220,220,220,.16); }
:global([data-theme="dark"] .presentation-controls__play) { border-color: rgba(235,235,235,.72) !important; background: rgba(220,220,220,.2) !important; color: #eeeeee !important; }
:global([data-theme="dark"] .presentation-progress) { background: rgba(220,220,220,.22); }
:global([data-theme="dark"] .presentation-progress span) { background: #dddddd; }
:global([data-theme="dark"] .presentation-notes__label) { color: #9b9b9b; }
:global([data-theme="dark"] .presentation-tags span) { border-color: #555555; color: #c0c0c0; }
:global([data-theme="dark"] .presentation-status i),
:global([data-theme="dark"] .presentation-status[data-ready="true"] i) { background: #d8d8d8; box-shadow: 0 0 0 4px rgba(216,216,216,.12); }

@media (max-width: 520px) {
  .presentation-heading h1 { font-size: 1.6rem; }
  .presentation-heading__intro { font-size: .78rem; }
  .presentation-stage__topline { align-items: flex-start; flex-direction: column; gap: .28rem; }
  .presentation-slide-frame { min-height: 0; padding: .5rem; }
  .presentation-stage__caption { grid-template-columns: 1.7rem minmax(0, 1fr); gap: .45rem; }
  .presentation-stage__caption > span:last-child { grid-column: 2; }
  .presentation-controls { grid-template-columns: 1fr auto; }
  .presentation-progress { grid-column: 1 / -1; grid-row: 1; }
  .presentation-controls__count { grid-column: 2; grid-row: 2; }
  .presentation-controls__buttons { grid-column: 1; grid-row: 2; }
  .presentation-scenes nav { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .presentation-status { min-height: 32px; font-size: .6rem; }
  .presentation-stage__topline,
  .presentation-stage__caption,
  .presentation-controls__count { color: #e9e9e4; }
  :global([data-theme="dark"] .presentation-heading h1),
  :global([data-theme="dark"] .presentation-scenes__head),
  :global([data-theme="dark"] .presentation-notes h2),
  :global([data-theme="dark"] .presentation-notes__source strong) { color: #f4f4f1; }
  :global([data-theme="dark"] .presentation-scenes__summary-note),
  :global([data-theme="dark"] .presentation-scenes__hint),
  :global([data-theme="dark"] .presentation-scenes small),
  :global([data-theme="dark"] .presentation-scene__index),
  :global([data-theme="dark"] .presentation-notes__label),
  :global([data-theme="dark"] .presentation-notes p),
  :global([data-theme="dark"] .presentation-controls__reset),
  :global([data-theme="dark"] .presentation-controls__count) { color: #c9c9c6; }
  :global([data-theme="dark"] .presentation-stage__topline),
  :global([data-theme="dark"] .presentation-stage__caption > span:last-child) { color: #d7d7d2; }
}

/* A light theme must change the courseware chrome as well as the surrounding
   page. The slide bitmap stays untouched so the verified teaching material
   keeps its original colors. */
:global([data-theme="light"] .presentation-page) {
  --presentation-stage: #f4f3ef;
  --presentation-stage-soft: #e9e7e1;
  --presentation-stage-ink: #252422;
  --presentation-stage-muted: #716e68;
  --presentation-stage-line: rgba(47, 45, 41, 0.18);
}
:global([data-theme="light"] .presentation-stage) {
  background: var(--presentation-stage);
  color: var(--presentation-stage-ink);
  box-shadow: 0 24px 56px rgba(47, 45, 41, 0.16);
}
:global([data-theme="light"] .presentation-stage__topline) {
  border-color: var(--presentation-stage-line);
  background: rgba(255, 255, 255, 0.44);
  color: var(--presentation-stage-muted);
}
:global([data-theme="light"] .presentation-slide-frame) {
  background: radial-gradient(circle at 70% 12%, rgba(255, 255, 255, 0.7), transparent 34%), linear-gradient(140deg, var(--presentation-stage-soft), var(--presentation-stage));
}
:global([data-theme="light"] .presentation-slide-frame::before) {
  background-image: linear-gradient(rgba(47, 45, 41, 0.055) 1px, transparent 1px), linear-gradient(90deg, rgba(47, 45, 41, 0.055) 1px, transparent 1px);
}
:global([data-theme="light"] .presentation-stage__caption) {
  border-color: var(--presentation-stage-line);
  background: rgba(255, 255, 255, 0.48);
}
:global([data-theme="light"] .presentation-stage__caption > span:first-child),
:global([data-theme="light"] .presentation-stage__caption > span:last-child) {
  color: var(--presentation-stage-muted);
}
:global([data-theme="light"] .presentation-stage__caption strong) { color: var(--presentation-stage-ink); }
:global([data-theme="light"] .presentation-controls) {
  border-color: var(--presentation-stage-line);
  background: rgba(247, 246, 242, 0.96);
}
:global([data-theme="light"] .presentation-controls button) {
  border-color: rgba(47, 45, 41, 0.24);
  background: rgba(47, 45, 41, 0.045);
  color: var(--presentation-stage-ink);
}
:global([data-theme="light"] .presentation-controls button:hover:not(:disabled)) {
  border-color: rgba(47, 45, 41, 0.5);
  background: rgba(47, 45, 41, 0.1);
}
:global([data-theme="light"] .presentation-controls__play) {
  border-color: #2d2c2a !important;
  background: #2d2c2a !important;
  color: #f4f3ef !important;
}
:global([data-theme="light"] .presentation-progress) { background: rgba(47, 45, 41, 0.18); }
:global([data-theme="light"] .presentation-progress span) { background: #2d2c2a; }
:global([data-theme="light"] .presentation-controls__reset),
:global([data-theme="light"] .presentation-controls__count) { color: var(--presentation-stage-muted) !important; }
</style>
