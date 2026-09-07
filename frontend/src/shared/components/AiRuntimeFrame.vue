<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import runtimeLogo from "../../assets/logo.webp";
import { useLocale } from "../i18n/locale";

export type AiRuntimeFrameMode = "landing" | "workbench";

export type AiRuntimeMenuItem = {
  id?: string;
  label: string;
  href?: string;
  active?: boolean;
  disabled?: boolean;
};

const RUNTIME_VIDEO_URL = "https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260809_012548_ef22562c-c0ae-4816-ad9d-f8922af4e6a7.mp4";

let frameCount = 0;

const props = withDefaults(defineProps<{
  mode?: AiRuntimeFrameMode;
  menuItems?: readonly AiRuntimeMenuItem[];
  menuOpen?: boolean;
  brandHref?: string;
  brandLabel?: string;
  signInHref?: string;
  signInLabel?: string;
  videoSrc?: string;
  videoPoster?: string;
  videoLabel?: string;
  showBackground?: boolean;
}>(), {
  mode: "landing",
  menuItems: () => [],
  menuOpen: undefined,
  brandHref: "/",
  brandLabel: "Structify",
  signInHref: "/login",
  signInLabel: "登录",
  videoSrc: RUNTIME_VIDEO_URL,
  videoPoster: "",
  showBackground: true,
});

const emit = defineEmits<{
  "update:menuOpen": [value: boolean];
  menuOpen: [];
  menuClose: [];
}>();

const menuId = `ai-runtime-menu-${++frameCount}`;
const { locale } = useLocale();
const menuToggle = ref<HTMLButtonElement | null>(null);
const menuDialog = ref<HTMLElement | null>(null);
const backgroundVideo = ref<HTMLVideoElement | null>(null);
const localMenuOpen = ref(false);
const reduceMotion = ref(false);
const reduceTransparency = ref(false);
let prefersReducedMotion: MediaQueryList | null = null;
let prefersReducedTransparency: MediaQueryList | null = null;
let lockedBodyOverflow = "";
let lockedDocumentOverflow = "";
let lastActiveElement: HTMLElement | null = null;
let restoreFocusOnClose = true;

const isControlled = computed(() => props.menuOpen !== undefined);
const chromeCopy = computed(() => locale.value === "en-US" ? {
  desktopNavigation: "Primary navigation",
  mobileNavigation: "Mobile primary navigation",
  dialogNavigation: "Primary navigation",
  openMenu: "Open navigation menu",
  closeMenu: "Close navigation menu",
  backgroundVideo: "AI Runtime background video",
} : {
  desktopNavigation: "主导航",
  mobileNavigation: "移动端主导航",
  dialogNavigation: "主导航",
  openMenu: "打开导航菜单",
  closeMenu: "关闭导航菜单",
  backgroundVideo: "AI Runtime 背景视频",
});
const backgroundVideoLabel = computed(() => props.videoLabel || chromeCopy.value.backgroundVideo);
const isMenuOpen = computed({
  get: () => props.menuOpen ?? localMenuOpen.value,
  set: (value: boolean) => {
    if (!isControlled.value) localMenuOpen.value = value;
    emit("update:menuOpen", value);
  },
});

const frameClasses = computed(() => [
  `ai-runtime-frame--${props.mode}`,
  {
    "is-menu-open": isMenuOpen.value,
    "is-reduced-motion": reduceMotion.value,
    "is-reduced-transparency": reduceTransparency.value,
  },
]);

function setMenuOpen(next: boolean, restoreFocus = true) {
  if (next === isMenuOpen.value) return;
  restoreFocusOnClose = restoreFocus;
  if (next) lastActiveElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  isMenuOpen.value = next;
}

function toggleMenu() {
  setMenuOpen(!isMenuOpen.value);
}

function closeMenu(restoreFocus = true) {
  setMenuOpen(false, restoreFocus);
}

function lockPageScroll() {
  lockedBodyOverflow = document.body.style.overflow;
  lockedDocumentOverflow = document.documentElement.style.overflow;
  document.body.classList.add("ai-runtime-menu-open");
  document.body.style.overflow = "hidden";
  document.documentElement.style.overflow = "hidden";
}

function unlockPageScroll() {
  document.body.classList.remove("ai-runtime-menu-open");
  document.body.style.overflow = lockedBodyOverflow;
  document.documentElement.style.overflow = lockedDocumentOverflow;
}

function getFocusableElements() {
  return Array.from(menuDialog.value?.querySelectorAll<HTMLElement>([
    "a[href]",
    "button:not([disabled])",
    "input:not([disabled]):not([type='hidden'])",
    "select:not([disabled])",
    "textarea:not([disabled])",
    "[tabindex]:not([tabindex='-1'])",
  ].join(", ")) ?? []).filter((element) => !element.hidden && element.tabIndex >= 0);
}

function focusFirstMenuControl() {
  const focusable = getFocusableElements();
  (focusable[0] ?? menuDialog.value)?.focus();
}

function constrainMenuFocus(event: KeyboardEvent) {
  const focusable = getFocusableElements();
  if (focusable.length === 0) {
    event.preventDefault();
    menuDialog.value?.focus();
    return;
  }

  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = document.activeElement;
  const activeInMenu = Boolean(active && menuDialog.value?.contains(active));

  if (event.shiftKey && (!activeInMenu || active === first)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (!activeInMenu || active === last)) {
    event.preventDefault();
    first.focus();
  }
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (!isMenuOpen.value) return;
  if (event.key === "Escape") {
    event.preventDefault();
    closeMenu();
  } else if (event.key === "Tab") {
    constrainMenuFocus(event);
  }
}

function handleMobileMenuClick(event: MouseEvent) {
  const target = event.target;
  if (!(target instanceof Element)) return;
  const navigationTarget = target.closest<HTMLElement>("a[href], [data-ai-runtime-close-menu]");
  if (!navigationTarget || navigationTarget.getAttribute("aria-disabled") === "true") return;
  closeMenu(false);
}

function preventDisabledNavigation(event: MouseEvent, item: AiRuntimeMenuItem) {
  if (item.disabled) event.preventDefault();
}

function handleViewportResize() {
  if (window.innerWidth > 720) closeMenu(false);
}

function syncVideoPlayback() {
  const video = backgroundVideo.value;
  if (!video) return;
  if (reduceMotion.value) {
    if (video.readyState > 0) video.pause();
    return;
  }
  // Wait until media metadata exists. Besides avoiding a needless rejected
  // autoplay call, ask the browser to start loading when a CDN response has
  // not initialized the media element yet. The poster remains visible while
  // the request is pending or if decoding fails.
  if (video.readyState === 0) {
    const isJsdom = typeof navigator !== "undefined" && /jsdom/i.test(navigator.userAgent);
    if (!isJsdom) {
      try { video.load(); } catch { /* a media loader may be unavailable */ }
    }
    return;
  }
  try {
    const playback = video.play();
    if (playback && typeof playback.catch === "function") void playback.catch(() => undefined);
  } catch {
    // Browsers may reject autoplay, and jsdom does not implement media playback.
  }
}

function updateMotionPreference(event: MediaQueryListEvent | MediaQueryList) {
  reduceMotion.value = event.matches;
  syncVideoPlayback();
}

function updateTransparencyPreference(event: MediaQueryListEvent | MediaQueryList) {
  reduceTransparency.value = event.matches;
}

watch(isMenuOpen, async (open, wasOpen) => {
  if (open) {
    lockPageScroll();
    emit("menuOpen");
    await nextTick();
    focusFirstMenuControl();
    return;
  }

  if (!wasOpen) return;
  unlockPageScroll();
  emit("menuClose");
  if (restoreFocusOnClose) {
    await nextTick();
    if (lastActiveElement?.isConnected) {
      lastActiveElement.focus();
      if (document.activeElement === lastActiveElement) return;
    }
    menuToggle.value?.focus();
  }
}, { immediate: true });

onMounted(() => {
  const matchMedia = typeof window.matchMedia === "function"
    ? window.matchMedia.bind(window)
    : (query: string) => ({ matches: false, media: query, addEventListener: () => undefined, removeEventListener: () => undefined } as unknown as MediaQueryList);
  prefersReducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
  prefersReducedTransparency = matchMedia("(prefers-reduced-transparency: reduce)");
  updateMotionPreference(prefersReducedMotion);
  updateTransparencyPreference(prefersReducedTransparency);
  prefersReducedMotion.addEventListener("change", updateMotionPreference);
  prefersReducedTransparency.addEventListener("change", updateTransparencyPreference);
  window.addEventListener("keydown", handleGlobalKeydown);
  window.addEventListener("resize", handleViewportResize);
});

onBeforeUnmount(() => {
  if (isMenuOpen.value) unlockPageScroll();
  prefersReducedMotion?.removeEventListener("change", updateMotionPreference);
  prefersReducedTransparency?.removeEventListener("change", updateTransparencyPreference);
  window.removeEventListener("keydown", handleGlobalKeydown);
  window.removeEventListener("resize", handleViewportResize);
});
</script>

<template>
  <section class="ai-runtime-frame" :class="frameClasses" :data-mode="mode">
    <div v-if="showBackground" class="ai-runtime-frame__background">
      <video
        ref="backgroundVideo"
        class="ai-runtime-frame__video"
        :aria-label="backgroundVideoLabel"
        :autoplay="!reduceMotion"
        muted
        loop
        playsinline
        preload="auto"
        :poster="videoPoster || undefined"
        @loadedmetadata="syncVideoPlayback"
        @loadeddata="syncVideoPlayback"
        @canplay="syncVideoPlayback"
      >
        <source :src="videoSrc" type="video/mp4" />
      </video>
    </div>
    <div class="ai-runtime-frame__scrim" aria-hidden="true"></div>

    <div class="ai-runtime-frame__page">
      <header class="ai-runtime-frame__header">
        <slot name="brand">
          <a class="ai-runtime-frame__mark" :href="brandHref" :aria-label="brandLabel">
            <img class="ai-runtime-frame__mark-image" :src="runtimeLogo" alt="" width="52" height="52" />
          </a>
        </slot>

        <nav class="ai-runtime-frame__nav" :aria-label="chromeCopy.desktopNavigation">
          <slot name="nav" :items="menuItems">
            <a
              v-for="item in menuItems"
              :key="item.id ?? item.label"
              :class="{ 'is-active': item.active }"
              :href="item.href ?? '#'"
              :aria-current="item.active ? 'page' : undefined"
              :aria-disabled="item.disabled || undefined"
              @click="preventDisabledNavigation($event, item)"
            >{{ item.label }}</a>
          </slot>
        </nav>

        <div class="ai-runtime-frame__actions">
          <slot name="actions">
            <a class="ai-runtime-frame__sign-in" :href="signInHref">{{ signInLabel }}</a>
          </slot>
        </div>

        <button
          ref="menuToggle"
          class="ai-runtime-frame__menu-toggle"
          type="button"
          :aria-expanded="isMenuOpen"
          :aria-controls="menuId"
          :aria-label="isMenuOpen ? chromeCopy.closeMenu : chromeCopy.openMenu"
          @click="toggleMenu"
        >
          <span></span><span></span><span></span>
        </button>
      </header>

      <div class="ai-runtime-frame__content">
        <slot name="hero"><slot /></slot>
      </div>

      <footer v-if="$slots.footer" class="ai-runtime-frame__footer"><slot name="footer" /></footer>

    <div
      :id="menuId"
      class="ai-runtime-frame__mobile-layer"
      :hidden="!isMenuOpen"
      :inert="isMenuOpen ? undefined : true"
      @click.self="closeMenu()"
    >
      <section ref="menuDialog" class="ai-runtime-frame__mobile-menu" role="dialog" aria-modal="true" :aria-label="chromeCopy.dialogNavigation" tabindex="-1">
        <nav class="ai-runtime-frame__mobile-nav" :aria-label="chromeCopy.mobileNavigation" @click="handleMobileMenuClick">
          <slot name="mobile-nav" :items="menuItems">
            <slot name="nav" :items="menuItems">
              <a
                v-for="item in menuItems"
                :key="item.id ?? item.label"
                :class="{ 'is-active': item.active }"
                :href="item.href ?? '#'"
                :aria-current="item.active ? 'page' : undefined"
                :aria-disabled="item.disabled || undefined"
                @click="preventDisabledNavigation($event, item)"
              >{{ item.label }}</a>
            </slot>
          </slot>
        </nav>
        <div class="ai-runtime-frame__mobile-actions" @click="handleMobileMenuClick">
          <slot name="mobile-actions">
            <slot name="actions">
              <a class="ai-runtime-frame__mobile-sign-in" :href="signInHref">{{ signInLabel }}</a>
            </slot>
          </slot>
        </div>
      </section>
    </div>
    </div>
  </section>
</template>

<style src="./ai-runtime-frame.css"></style>
