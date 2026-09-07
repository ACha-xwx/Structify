<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useSlots, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { auth } from "../../app/providers/runtime";
import ThemeToggle from "../../shared/design/ThemeToggle.vue";
import AiRuntimeFrame from "../../shared/components/AiRuntimeFrame.vue";
import LearningDashboardSidebar from "./LearningDashboardSidebar.vue";
import runtimePoster from "../../assets/ai-runtime-poster.webp";
import { useLocale } from "../../shared/i18n/locale";
import { isLearningGlobalNavigationActive, isLearningNavigationActive, localizedLearningGlobalNavigation, localizedLearningNavigation, localizedLearningTools } from "../workbench-navigation";
import { courseItemNavigationContext, learningWorkbenchFixture, learningCourseGroups, type WorkbenchCourseItem } from "../fixtures/learning-workbench";
import { hasPreviewPresentation, previewChapter, previewPresentationLessonId } from "../fixtures/course-preview";
import { createLoginTarget } from "../login-target";
import "../user.css";

type UserFrameSurface = "course" | "runtime";

const props = withDefaults(defineProps<{
  shell?: UserFrameSurface;
  compact?: boolean;
}>(), {
  shell: "runtime",
  compact: false,
});

const route = useRoute();
const router = useRouter();
const slots = useSlots();
const { locale } = useLocale();
const isEnglish = computed(() => locale.value === "en-US");
const accountLabel = computed(() => auth.state.user?.username || auth.state.user?.email || (isEnglish.value ? "Sign in" : "登录"));
const hasRail = computed(() => Boolean(slots.rail));
const showRuntimeReview = computed(() => hasRail.value && props.shell === "runtime" && route.path !== "/user/home" && route.path !== "/user/progress");
const loginTarget = computed(() => createLoginTarget(route.fullPath));
const copy = computed(() => isEnglish.value ? {
  brand: "Back to Structify product home",
  product: "Structify",
  account: auth.state.user ? accountLabel.value : "Sign in",
  navLabel: "Course outline",
  skip: "Skip to main content",
  current: "Current module",
  courseOutline: "Course outline",
  currentLesson: "Current lesson",
  otherEntrances: "Other learning entrances",
  directTools: "Direct learning tools",
  rail: "Current learning context",
  workspace: "Data Structures",
  workspaceMeta: "Learning workspace",
  workspaceMenu: "Workspace destinations",
  primaryNav: "Primary learning navigation",
  modules: "Learning modules",
  review: "Review",
  reviewMeta: "Activity log",
  tools: "Learning tools",
  openMenu: "Open learning navigation",
  closeMenu: "Close learning navigation",
  collapseSidebar: "Collapse course outline",
  expandSidebar: "Expand course outline",
  context: "Learning workspace",
} : {
  brand: "返回 Structify 产品首页",
  product: "Structify",
  account: auth.state.user ? accountLabel.value : "登录",
  navLabel: "课程目录",
  skip: "跳到主内容",
  current: "当前模块",
  courseOutline: "课程目录",
  currentLesson: "当前学习单元",
  otherEntrances: "其它学习入口",
  directTools: "直接学习工具",
  rail: "当前学习上下文",
  workspace: "数据结构课程",
  workspaceMeta: "学习工作台",
  workspaceMenu: "工作台入口",
  primaryNav: "学习主导航",
  modules: "学习模块",
  review: "学习复盘",
  reviewMeta: "活动记录",
  tools: "学习工具",
  openMenu: "打开学习导航",
  closeMenu: "关闭学习导航",
  collapseSidebar: "收起课程目录",
  expandSidebar: "展开课程目录",
  context: "学习工作台",
});
const navigationContext = computed(() => {
  const chapterId = routeQueryValue(route.query.chapterId);
  const preview = chapterId ? previewChapter(chapterId) : null;
  const routeLessonId = routeQueryValue(route.query.lessonId);
  const previewCoursewareLessonId = preview && hasPreviewPresentation(preview.id)
    ? previewPresentationLessonId(preview)
    : undefined;
  // Presentation plan ids (for example 02-02B) belong only to the deck
  // adapter. Keep them out of the learning-unit context used by code,
  // classroom, algorithm and Q&A links.
  const lessonId = routeLessonId && routeLessonId !== previewCoursewareLessonId ? routeLessonId : undefined;

  return { chapterId, lessonId, coursewareLessonId: previewCoursewareLessonId, from: routeQueryValue(route.query.from) };
});
const navigation = computed(() => localizedLearningNavigation(locale.value, navigationContext.value));
const globalNavigation = computed(() => localizedLearningGlobalNavigation(locale.value, navigationContext.value));
const directTools = computed(() => localizedLearningTools(locale.value, navigationContext.value));
const courseCatalogGroups = computed(() => learningCourseGroups.map((group) => ({
  ...group,
  open: Boolean(group.open),
  label: isEnglish.value ? group.labelEn : group.label,
  description: isEnglish.value ? group.descriptionEn : group.description,
  items: group.items.map((item) => ({
    ...item,
    current: Boolean(item.current),
    label: isEnglish.value ? (item.labelEn ?? item.label) : item.label,
    meta: isEnglish.value ? (item.metaEn ?? item.meta) : item.meta,
  })),
})));
const courseCatalog = computed(() => courseCatalogGroups.value.flatMap((group) => group.items.map((item, index) => ({
  ...item,
  index: String(index + 1).padStart(2, "0"),
}))));

const currentContextLabel = computed(() => {
  const labels = isEnglish.value ? {
    home: "Workbench",
    chapters: "Course outline",
    animation: "Algorithm stage",
    knowledge: "Knowledge library",
    presentation: "Courseware",
    coach: "AI coach",
    classroom: "Classroom",
    code: "C compiler",
    progress: "Review",
    profile: "Account",
  } : {
    home: "学习台",
    chapters: "课程目录",
    animation: "算法舞台",
    knowledge: "知识检索",
    presentation: "课程课件",
    coach: "AI 伴学",
    classroom: "课堂",
    code: "C 编译器",
    progress: "学习复盘",
    profile: "账户",
  };
  if (route.path === "/user/home") return labels.home;
  if (route.path === "/user/chapters" || route.path.startsWith("/user/chapters/")) return labels.chapters;
  if (route.path === "/user/animation") return labels.animation;
  if (route.path === "/user/knowledge" || route.path.startsWith("/user/resources/")) return labels.knowledge;
  if (route.path === "/user/presentation") return labels.presentation;
  if (route.path === "/user/coach") return labels.coach;
  if (route.path === "/user/classroom") return labels.classroom;
  if (route.path === "/user/code") return labels.code;
  if (route.path === "/user/progress") return labels.progress;
  if (route.path === "/user/profile") return labels.profile;
  return String(route.meta.module || copy.value.context);
});

const sidebarOpen = ref(true);
const sidebarSynchronizing = ref(false);
const searchOpen = ref(false);
const searchQuery = ref("");
const searchInput = ref<HTMLInputElement | null>(null);
const searchTrigger = ref<HTMLButtonElement | null>(null);
let sidebarSyncTimer: number | undefined;
const mobileMenuOpen = ref(false);
const mobileMenuToggle = ref<HTMLButtonElement | null>(null);
const mobileMenuClose = ref<HTMLButtonElement | null>(null);
const mobileMenuDialog = ref<HTMLElement | null>(null);
let lockedBodyOverflow = "";
let lockedDocumentOverflow = "";
let lastActiveElement: HTMLElement | null = null;

function lockMobileMenu() {
  lockedBodyOverflow = document.body.style.overflow;
  lockedDocumentOverflow = document.documentElement.style.overflow;
  document.body.style.overflow = "hidden";
  document.documentElement.style.overflow = "hidden";
}

function unlockMobileMenu() {
  document.body.style.overflow = lockedBodyOverflow;
  document.documentElement.style.overflow = lockedDocumentOverflow;
}

function getMobileMenuFocusableElements() {
  return Array.from(mobileMenuDialog.value?.querySelectorAll<HTMLElement>([
    "a[href]",
    "button:not([disabled])",
    "input:not([disabled]):not([type='hidden'])",
    "select:not([disabled])",
    "textarea:not([disabled])",
    "[tabindex]:not([tabindex='-1'])",
  ].join(", ")) ?? []).filter((element) => !element.hidden && element.tabIndex >= 0);
}

function focusFirstMobileMenuControl() {
  const focusable = getMobileMenuFocusableElements();
  (focusable[0] ?? mobileMenuDialog.value)?.focus();
}

function setMobileMenuOpen(next: boolean, restoreFocus = true) {
  if (next === mobileMenuOpen.value) return;
  if (next) {
    lastActiveElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    lockMobileMenu();
  } else {
    unlockMobileMenu();
  }
  mobileMenuOpen.value = next;
  if (next) {
    void nextTick(() => {
      mobileMenuClose.value?.focus();
      focusFirstMobileMenuControl();
    });
  } else if (restoreFocus) {
    void nextTick(() => {
      if (lastActiveElement?.isConnected) {
        lastActiveElement.focus();
      } else {
        mobileMenuToggle.value?.focus();
      }
    });
  }
}

function toggleMobileMenu() {
  setMobileMenuOpen(!mobileMenuOpen.value);
}

function toggleSidebar() {
  if (window.innerWidth <= 760) {
    toggleMobileMenu();
    return;
  }
  sidebarOpen.value = !sidebarOpen.value;
}

function openSearch() {
  searchOpen.value = true;
  void nextTick(() => searchInput.value?.focus());
}

function closeSearch(restoreFocus = true) {
  searchOpen.value = false;
  if (restoreFocus) {
    void nextTick(() => searchTrigger.value?.focus());
  }
}

async function submitSearch() {
  const query = searchQuery.value.trim();
  if (!query) {
    await nextTick(() => searchInput.value?.focus());
    return;
  }

  const context = navigationContext.value;
  closeSearch(false);
  await router.push({
    path: "/user/knowledge",
    query: {
      q: query,
      ...(context.chapterId ? { chapterId: context.chapterId } : {}),
      ...(context.lessonId ? { lessonId: context.lessonId } : {}),
      from: "workbench",
    },
  });
}

function refreshSidebar() {
  if (sidebarSynchronizing.value) return;
  sidebarSynchronizing.value = true;
  if (sidebarSyncTimer !== undefined) window.clearTimeout(sidebarSyncTimer);
  sidebarSyncTimer = window.setTimeout(() => {
    sidebarSynchronizing.value = false;
    sidebarSyncTimer = undefined;
  }, 520);
}

function constrainMobileMenuFocus(event: KeyboardEvent) {
  const focusable = getMobileMenuFocusableElements();
  if (focusable.length === 0) {
    event.preventDefault();
    mobileMenuDialog.value?.focus();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = document.activeElement;
  if (event.shiftKey && (!active || !mobileMenuDialog.value?.contains(active) || active === first)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (!active || !mobileMenuDialog.value?.contains(active) || active === last)) {
    event.preventDefault();
    first.focus();
  }
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (searchOpen.value && event.key === "Escape") {
    event.preventDefault();
    closeSearch();
    return;
  }
  if (!mobileMenuOpen.value) return;
  if (event.key === "Escape") {
    event.preventDefault();
    setMobileMenuOpen(false);
  } else if (event.key === "Tab") {
    constrainMobileMenuFocus(event);
  }
}

function closeMobileMenuAtDesktop() {
  if (window.innerWidth > 760 && mobileMenuOpen.value) setMobileMenuOpen(false, false);
}

function groupHasCurrent(group: { items: WorkbenchCourseItem[] }) {
  return group.items.some(itemHasCurrent);
}
function routeQueryValue(value: unknown): string | undefined {
  const candidates = Array.isArray(value) ? value : [value];
  return candidates.find((candidate): candidate is string => typeof candidate === "string" && Boolean(candidate.trim()));
}

function isNavigationActive(id: (typeof navigation.value)[number]["id"]): boolean {
  return isLearningNavigationActive(id, route.path);
}

function isGlobalNavigationActive(id: (typeof globalNavigation.value)[number]["id"]): boolean {
  return isLearningGlobalNavigationActive(id, route.path);
}

function isCourseItemActive(item: { id: string; current?: boolean }): boolean {
  if (route.path === `/user/chapters/${item.id}` || route.query.chapterId === item.id) return true;
  return route.path === "/user/home" && Boolean(item.current);
}

function itemHasCurrent(item: WorkbenchCourseItem): boolean {
  return Boolean(isCourseItemActive(item) || item.children?.some(itemHasCurrent));
}

function courseItemTarget(item: WorkbenchCourseItem) {
  const context = courseItemNavigationContext(item);
  return {
    path: "/user/chapters",
    query: {
      chapterId: context.chapterId,
      ...(context.lessonId ? { lessonId: context.lessonId } : {}),
      from: "workbench",
    },
  };
}

watch(() => route.fullPath, () => setMobileMenuOpen(false, false));
onMounted(() => {
  window.addEventListener("keydown", handleGlobalKeydown);
  window.addEventListener("resize", closeMobileMenuAtDesktop);
});
onBeforeUnmount(() => {
  if (mobileMenuOpen.value) unlockMobileMenu();
  if (sidebarSyncTimer !== undefined) window.clearTimeout(sidebarSyncTimer);
  window.removeEventListener("keydown", handleGlobalKeydown);
  window.removeEventListener("resize", closeMobileMenuAtDesktop);
});
</script>

<template>
  <AiRuntimeFrame mode="workbench" :class="{ 'ai-runtime-frame--course-shell': props.shell === 'course' }" :show-background="props.shell !== 'course'" :video-poster="runtimePoster" :brand-label="copy.brand" :sign-in-href="auth.state.user ? '/user/profile' : `/login?redirect=${encodeURIComponent(route.fullPath)}`" :sign-in-label="copy.account">
    <template #nav>
      <slot name="runtime-nav">
        <RouterLink v-for="item in globalNavigation" :key="item.id" :to="item.to" :class="{ 'is-active': isGlobalNavigationActive(item.id) }" :aria-current="isGlobalNavigationActive(item.id) ? 'page' : undefined">{{ item.label }}</RouterLink>
      </slot>
    </template>
    <template #actions>
      <slot name="runtime-actions">
        <div v-if="props.shell !== 'course'" class="user-frame-runtime-actions">
          <ThemeToggle />
          <RouterLink class="ai-runtime-frame__sign-in user-frame-runtime-account" :to="auth.state.user ? '/user/profile' : loginTarget" :aria-label="copy.account">{{ copy.account }}</RouterLink>
        </div>
      </slot>
    </template>
    <template #mobile-nav>
      <slot name="runtime-mobile-nav">
        <RouterLink v-for="item in globalNavigation" :key="item.id" :to="item.to" :class="{ 'is-active': isGlobalNavigationActive(item.id) }" :aria-current="isGlobalNavigationActive(item.id) ? 'page' : undefined">{{ item.label }}</RouterLink>
        <template v-if="props.shell === 'course'">
        <p class="user-frame-mobile-section-label">{{ copy.courseOutline }}</p>
        <div class="user-frame-mobile-course-groups">
          <details v-for="group in courseCatalogGroups" :key="`mobile-group-${group.id}`" class="user-frame-mobile-course-group" :open="Boolean(group.open)">
            <summary><span>{{ group.label }}</span><small>{{ group.items.length }}</small></summary>
            <template v-for="item in group.items" :key="`mobile-course-${group.id}-${item.id}`">
              <RouterLink class="user-frame-mobile-course-link" :data-course-id="item.id" :to="courseItemTarget(item)" :class="{ 'is-active': isCourseItemActive(item) }" :aria-current="isCourseItemActive(item) ? 'page' : undefined"><span>{{ item.label }}</span><small>{{ item.meta }}</small></RouterLink>
              <RouterLink v-for="lesson in (item.children ?? [])" :key="`mobile-course-${group.id}-${lesson.id}`" class="user-frame-mobile-course-link user-frame-mobile-course-link--lesson" :data-course-id="lesson.id" :to="courseItemTarget(lesson)" :class="{ 'is-active': isCourseItemActive(lesson) }" :aria-current="isCourseItemActive(lesson) ? 'page' : undefined"><span>{{ lesson.label }}</span><small>{{ lesson.meta }}</small></RouterLink>
            </template>
          </details>
        </div>
        </template>
        <details class="user-frame-mobile-other">
          <summary>{{ copy.otherEntrances }}</summary>
          <RouterLink v-for="item in navigation" :key="`mobile-${item.id}`" :to="item.to" :class="{ 'is-active': isNavigationActive(item.id) }" :aria-current="isNavigationActive(item.id) ? 'page' : undefined">{{ item.label }}</RouterLink>
          <RouterLink to="/user/progress">{{ isEnglish ? "Review" : "学习复盘" }}</RouterLink>
        </details>
      </slot>
    </template>
    <template #mobile-actions>
      <slot name="runtime-mobile-actions">
        <ThemeToggle :register-control="false" />
        <RouterLink class="ai-runtime-frame__mobile-sign-in" :to="auth.state.user ? '/user/profile' : loginTarget">{{ auth.state.user ? (isEnglish ? 'Account' : '账户') : copy.account }}</RouterLink>
      </slot>
    </template>

    <template v-if="props.shell === 'course'">
      <div class="learning-workbench-v2 user-frame-course-surface" data-visual-contract="dashboard-sidebar" :data-locale="locale">
        <svg class="workbench-sprite" aria-hidden="true" focusable="false">
          <symbol id="wb-panel-close" viewBox="0 0 24 24"><rect x="4" y="5" width="16" height="14" rx="1" /><path d="M9 5v14M14 9l-2 3 2 3" /></symbol>
          <symbol id="wb-panel-open" viewBox="0 0 24 24"><rect x="4" y="5" width="16" height="14" rx="1" /><path d="M9 5v14M12 9l2 3-2 3" /></symbol>
          <symbol id="wb-search" viewBox="0 0 24 24"><circle cx="10.8" cy="10.8" r="5.8" /><path d="m15.3 15.3 4.2 4.2" /></symbol>
          <symbol id="wb-grid" viewBox="0 0 24 24"><path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z" /></symbol>
          <symbol id="wb-arrow" viewBox="0 0 24 24"><path d="M5 12h13M13 6l6 6-6 6" /></symbol>
        </svg>

        <a class="workbench-skip-link user-skip-link" href="#user-learning-content">{{ copy.skip }}</a>
        <div class="workbench-layout user-frame-layout" data-shell="course" :data-has-rail="String(hasRail)" :class="{ 'is-two-column': !hasRail, 'is-sidebar-collapsed': !sidebarOpen }" data-region="workbench">
          <LearningDashboardSidebar :open="sidebarOpen" :synchronizing="sidebarSynchronizing" @search="openSearch" @refresh="refreshSidebar" />

          <main id="user-learning-content" class="workbench-main user-frame-content" data-region="content" :aria-label="isEnglish ? 'Learning content' : '学习内容'">
            <header class="workbench-context-bar user-frame-course-toolbar" data-region="toolbar">
              <div class="workbench-context-bar__leading user-frame-course-toolbar__leading">
                <button class="workbench-sidebar-toggle" type="button" :aria-label="sidebarOpen ? copy.collapseSidebar : copy.expandSidebar" :title="sidebarOpen ? copy.collapseSidebar : copy.expandSidebar" @click="toggleSidebar">
                  <svg aria-hidden="true"><use :href="sidebarOpen ? '#wb-panel-close' : '#wb-panel-open'" /></svg>
                </button>
                <span class="workbench-context-bar__crumb user-frame-course-toolbar__crumb">{{ copy.workspace }}</span>
                <span class="workbench-context-bar__separator user-frame-course-toolbar__separator" aria-hidden="true">/</span>
                <strong>{{ currentContextLabel }}</strong>
              </div>
              <div class="workbench-context-bar__utility user-frame-course-toolbar__actions">
                <ThemeToggle />
                <button ref="searchTrigger" class="workbench-tool-button workbench-command-trigger workbench-search-pill" type="button" :aria-label="isEnglish ? 'Search course materials' : '检索课程资料'" :title="isEnglish ? 'Search course materials' : '检索课程资料'" @click="openSearch">
                  <svg aria-hidden="true"><use href="#wb-search" /></svg>
                  <span>{{ isEnglish ? 'Search' : '检索' }}</span>
                  <kbd>⌘K</kbd>
                </button>
                <RouterLink v-if="auth.state.user" class="workbench-account user-frame-course-toolbar__account" to="/user/profile" :aria-label="copy.account">
                  <span class="workbench-account__avatar user-frame-course-toolbar__avatar">{{ accountLabel.slice(0, 1).toUpperCase() }}</span>
                  <span class="workbench-account__name">{{ accountLabel }}</span>
                </RouterLink>
                <RouterLink v-else class="workbench-sign-in" :to="loginTarget" :aria-label="copy.account">{{ copy.account }}</RouterLink>
              </div>
            </header>
            <div class="workbench-learning-content user-frame-course-content-body" data-region="learning-content">
              <slot />
            </div>
          </main>

          <aside v-if="hasRail" class="workbench-rail user-frame-rail" data-region="rail" :aria-label="copy.rail">
            <slot name="rail" />
          </aside>
        </div>

        <div v-if="searchOpen" class="workbench-command-layer" @mousedown.self="closeSearch(false)">
          <section class="workbench-command-palette" role="dialog" aria-modal="true" :aria-label="isEnglish ? 'Search course materials' : '检索课程资料'">
            <form class="workbench-command-search" role="search" @submit.prevent="submitSearch">
              <svg aria-hidden="true"><use href="#wb-search" /></svg>
              <input ref="searchInput" v-model="searchQuery" :aria-label="isEnglish ? 'Search course materials' : '检索课程资料'" :placeholder="isEnglish ? 'Search course materials' : '检索课程资料'" />
              <kbd>ESC</kbd>
              <button class="workbench-command-search__submit" type="submit" :aria-label="isEnglish ? 'Submit search' : '提交检索'" :title="isEnglish ? 'Submit search' : '提交检索'"><svg aria-hidden="true"><use href="#wb-arrow" /></svg></button>
              <button type="button" :aria-label="isEnglish ? 'Close search' : '关闭检索'" @click="closeSearch()">×</button>
            </form>
            <div class="workbench-command-empty" aria-live="polite">
              <svg aria-hidden="true"><use href="#wb-grid" /></svg>
              <p>{{ searchQuery.trim() ? (isEnglish ? 'Press Enter to search course materials' : '按 Enter 检索课程资料') : (isEnglish ? 'Type a topic, algorithm, or action' : '输入知识点、算法或操作') }}</p>
            </div>
          </section>
        </div>
      </div>
    </template>
    <template v-else>
      <a class="user-skip-link" href="#user-learning-content">{{ copy.skip }}</a>
      <div id="user-learning-content" class="user-frame-runtime-content" :class="{ 'user-frame-runtime-content--compact': props.compact }" data-region="content" data-shell="runtime" :data-has-rail="String(hasRail)">
        <div class="user-frame-runtime-main" :aria-label="isEnglish ? 'Learning content' : '学习内容'">
          <slot />
        </div>
        <aside v-if="hasRail || showRuntimeReview" class="user-frame-runtime-rail" data-region="rail" :aria-label="copy.rail">
          <slot name="rail" />
          <RouterLink v-if="showRuntimeReview" class="user-frame-runtime-review" to="/user/progress">
            <span>{{ isEnglish ? "Review" : "学习复盘" }}</span>
            <small>{{ isEnglish ? "Activity log" : "活动记录" }}</small>
          </RouterLink>
        </aside>
      </div>
    </template>
  </AiRuntimeFrame>
</template>
