<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { auth } from "../../app/providers/runtime";
import { useLocale } from "../../shared/i18n/locale";
import {
  courseItemNavigationContext,
  learningCourseGroups,
  localizeCourseCapability,
  type WorkbenchCourseItem,
} from "../fixtures/learning-workbench";
import {
  isLearningGlobalNavigationActive,
  localizedLearningGlobalNavigation,
  localizedLearningNavigation,
  localizedLearningTools,
} from "../workbench-navigation";
import { createLoginTarget } from "../login-target";
import { hasPreviewPresentation, previewChapter, previewPresentationLessonId } from "../fixtures/course-preview";
import "../views/LearningWorkbenchStrict.css";

const props = withDefaults(defineProps<{
  open?: boolean;
  synchronizing?: boolean;
}>(), {
  open: true,
  synchronizing: false,
});

const emit = defineEmits<{
  (event: "search"): void;
  (event: "refresh"): void;
}>();

const route = useRoute();
const { locale } = useLocale();
const isEnglish = computed(() => locale.value === "en-US");
const accountLabel = computed(() => auth.state.user?.username || auth.state.user?.email || (isEnglish.value ? "Sign in" : "登录"));
const loginTarget = computed(() => createLoginTarget(route.fullPath));
const copy = computed(() => isEnglish.value ? {
  navLabel: "Course outline",
  courseOutline: "Course outline",
  currentLesson: "Current lesson",
  refresh: "Refresh",
  syncing: "Syncing",
  workspace: "Data Structures Lab",
  workspaceMeta: "Structify learning workspace",
  workspaceMenu: "Workspace destinations",
  primaryNav: "Primary learning navigation",
  secondaryNav: "Learning modules",
  search: "Search course materials",
  catalogLink: "Browse full curriculum",
  catalogOverview: "Complete course map",
  accountLink: "Open learner account",
  account: "Open account",
  chapterCount: "CHAPTERS",
  topicCount: "TOPICS",
  unitCount: "UNITS",
  catalogScope: "CATALOG SCOPE",
} : {
  navLabel: "课程目录",
  courseOutline: "课程目录",
  currentLesson: "当前学习单元",
  refresh: "刷新",
  syncing: "同步中",
  workspace: "数据结构学习台",
  workspaceMeta: "Structify 学习工作区",
  workspaceMenu: "工作区入口",
  primaryNav: "学习主导航",
  secondaryNav: "学习模块",
  search: "检索课程资料",
  catalogLink: "浏览完整课程图谱",
  catalogOverview: "完整课程图谱",
  accountLink: "打开学习账户",
  account: "打开账户",
  chapterCount: "章",
  topicCount: "主题",
  unitCount: "单元",
  catalogScope: "课程范围",
});

function routeQueryValue(value: unknown): string | undefined {
  const values = Array.isArray(value) ? value : [value];
  return values.find((entry): entry is string => typeof entry === "string" && Boolean(entry.trim()));
}

const navigationContext = computed(() => {
  const chapterId = routeQueryValue(route.query.chapterId);
  const preview = chapterId ? previewChapter(chapterId) : null;
  const routeLessonId = routeQueryValue(route.query.lessonId);
  const coursewareLessonId = preview && hasPreviewPresentation(preview.id)
    ? previewPresentationLessonId(preview)
    : undefined;
  const lessonId = routeLessonId && routeLessonId !== coursewareLessonId ? routeLessonId : undefined;
  return {
    chapterId,
    lessonId,
    coursewareLessonId,
    from: routeQueryValue(route.query.from) || "workbench",
  };
});
const primaryNavigation = computed(() => localizedLearningGlobalNavigation(locale.value, navigationContext.value));
const moduleNavigation = computed(() => localizedLearningNavigation(locale.value, navigationContext.value));
const directTools = computed(() => localizedLearningTools(locale.value, navigationContext.value));
const courseGroups = computed(() => learningCourseGroups.map((group) => ({
  ...group,
  label: isEnglish.value ? group.labelEn : group.label,
  description: isEnglish.value ? group.descriptionEn : group.description,
  open: Boolean(group.open),
  items: group.items.map((item) => ({
    ...item,
    label: isEnglish.value ? (item.labelEn ?? item.label) : item.label,
    meta: isEnglish.value ? (item.metaEn ?? item.meta) : item.meta,
  })),
})));
const courseCatalogStats = computed(() => {
  const count = (items: readonly WorkbenchCourseItem[], kind: "chapter" | "lesson"): number => items.reduce((total, item) => total + (item.kind === kind ? 1 : 0) + count(item.children ?? [], kind), 0);
  return {
    topics: courseGroups.value.length,
    chapters: courseGroups.value.reduce((total, group) => total + count(group.items, "chapter"), 0),
    units: courseGroups.value.reduce((total, group) => total + count(group.items, "lesson"), 0),
  };
});

function itemTarget(item: WorkbenchCourseItem) {
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

function isCourseItemActive(item: WorkbenchCourseItem): boolean {
  const chapterId = routeQueryValue(route.query.chapterId);
  const lessonId = routeQueryValue(route.query.lessonId);
  if (route.path === `/user/chapters/${item.id}`) return true;
  // A lesson query identifies the leaf unit. Keep its parent chapter as
  // context only so the sidebar never shows two competing active pips.
  if (lessonId && lessonId !== navigationContext.value.coursewareLessonId) return lessonId === item.id;
  return chapterId === item.id || (route.path === "/user/home" && Boolean(item.current));
}

function itemHasCurrent(item: WorkbenchCourseItem): boolean {
  return isCourseItemActive(item) || Boolean(item.children?.some(itemHasCurrent));
}

function count(items: readonly WorkbenchCourseItem[], kind: "chapter" | "lesson"): number {
  return items.reduce((total, item) => total + (item.kind === kind ? 1 : 0) + count(item.children ?? [], kind), 0);
}

function courseCapability(item: WorkbenchCourseItem) {
  return localizeCourseCapability(item.capability, locale.value);
}

function chapterTarget() {
  const chapterId = routeQueryValue(route.query.chapterId);
  const lessonId = routeQueryValue(route.query.lessonId);
  return {
    path: "/user/chapters",
    query: {
      ...(chapterId ? { chapterId } : {}),
      ...(lessonId ? { lessonId } : {}),
      from: "workbench",
    },
  };
}
</script>

<template>
  <div class="learning-dashboard-sidebar-scope" :data-locale="locale">
    <svg class="workbench-sprite" aria-hidden="true" focusable="false">
      <symbol id="wb-route" viewBox="0 0 24 24"><path d="M5 18.5V8.8a3.8 3.8 0 0 1 3.8-3.8h9.7" /><path d="m15 2.5 3.5 2.5-3.5 2.5" /><circle cx="5" cy="18.5" r="2" /></symbol>
      <symbol id="wb-chat" viewBox="0 0 24 24"><path d="M5 5.5h14v10H9l-4 3v-13Z" /><path d="M8 9.5h8M8 12.5h5" /></symbol>
      <symbol id="wb-classroom" viewBox="0 0 24 24"><path d="M4 5.5h16v10H4z" /><path d="M8 19h8M12 15.5V19" /><path d="m8 9 2.5 2L16 8" /></symbol>
      <symbol id="wb-stage" viewBox="0 0 24 24"><path d="m4 17 5-5 3 3 7-8" /><path d="M16 7h3v3" /><path d="M4 20h16" /></symbol>
      <symbol id="wb-library" viewBox="0 0 24 24"><path d="M5 4.5h11a2 2 0 0 1 2 2V19H7a2 2 0 0 1-2-2V4.5Z" /><path d="M8 8h7M8 11h7M8 14h4" /><path d="M18 19h1a1 1 0 0 0 1-1V7" /></symbol>
      <symbol id="wb-code" viewBox="0 0 24 24"><path d="m8.5 7-4 5 4 5M15.5 7l4 5-4 5M13.5 4l-3 16" /></symbol>
      <symbol id="wb-search" viewBox="0 0 24 24"><circle cx="10.8" cy="10.8" r="5.8" /><path d="m15.3 15.3 4.2 4.2" /></symbol>
      <symbol id="wb-grid" viewBox="0 0 24 24"><path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z" /></symbol>
      <symbol id="wb-arrow" viewBox="0 0 24 24"><path d="M5 12h13M13 6l6 6-6 6" /></symbol>
      <symbol id="wb-reset" viewBox="0 0 24 24"><path d="M19 8V4m0 0h-4m4 0-3 3.2A8 8 0 1 0 20 13" /></symbol>
    </svg>
    <aside class="workbench-sidebar user-frame-sidebar" :class="{ 'is-collapsed': !props.open }" :aria-label="copy.navLabel" data-region="sidebar">
      <div class="workbench-sidebar__top">
        <details class="workbench-workspace-switcher">
          <summary><span class="workbench-workspace-mark" aria-hidden="true">DS</span><span class="workbench-workspace-copy"><strong>{{ copy.workspace }}</strong><small>{{ copy.workspaceMeta }}</small></span><i class="workbench-disclosure__chevron" aria-hidden="true"></i></summary>
          <div class="workbench-workspace-switcher__menu" :aria-label="copy.workspaceMenu">
            <RouterLink to="/user/home"><span>{{ copy.courseOutline }}</span><small>{{ copy.catalogOverview }}</small></RouterLink>
            <RouterLink to="/user/chapters"><span>{{ copy.catalogLink }}</span><small>{{ courseCatalogStats.topics }} {{ copy.topicCount }} · {{ courseCatalogStats.chapters }} {{ copy.chapterCount }}</small></RouterLink>
            <RouterLink :to="auth.state.user ? '/user/profile' : loginTarget"><span>{{ copy.accountLink }}</span><small>{{ accountLabel }}</small></RouterLink>
          </div>
        </details>
        <nav class="workbench-primary-nav" :aria-label="copy.primaryNav">
          <button class="workbench-nav-row" type="button" @click="emit('search')"><span class="workbench-nav-row__lead"><svg aria-hidden="true"><use href="#wb-search" /></svg><span>{{ copy.search }}</span></span><kbd>⌘K</kbd></button>
          <RouterLink v-for="item in primaryNavigation" :key="`primary-${item.id}`" class="workbench-nav-row" :class="{ 'is-active': isLearningGlobalNavigationActive(item.id, route.path) }" :to="item.to" :aria-current="isLearningGlobalNavigationActive(item.id, route.path) ? 'page' : undefined"><span class="workbench-nav-row__lead"><svg aria-hidden="true"><use :href="item.id === 'overview' ? '#wb-grid' : item.id === 'course' ? '#wb-route' : item.id === 'lab' ? '#wb-stage' : '#wb-library'" /></svg><span>{{ item.label }}</span></span><span v-if="isLearningGlobalNavigationActive(item.id, route.path)" class="workbench-nav-row__pip" aria-hidden="true"></span></RouterLink>
        </nav>
      </div>
      <div class="workbench-sidebar__divider" aria-hidden="true"></div>
      <nav class="workbench-course-outline user-frame-course-nav" :aria-label="copy.courseOutline">
        <div class="workbench-course-outline__identity"><div class="workbench-course-outline__identity-head"><span class="workbench-section-label">{{ copy.courseOutline }}</span><button class="workbench-refresh" type="button" :disabled="props.synchronizing" :aria-label="props.synchronizing ? copy.syncing : copy.refresh" :title="props.synchronizing ? copy.syncing : copy.refresh" @click="emit('refresh')"><svg aria-hidden="true"><use href="#wb-reset" /></svg></button></div><p class="workbench-course-outline__title user-frame-course-nav__title">{{ isEnglish ? "Data Structures & Algorithms" : "数据结构与算法" }}</p><div class="workbench-course-outline__scope" :aria-label="copy.catalogScope"><span><strong>{{ courseCatalogStats.topics }}</strong> {{ copy.topicCount }}</span><span><strong>{{ courseCatalogStats.chapters }}</strong> {{ copy.chapterCount }}</span><span><strong>{{ courseCatalogStats.units }}</strong> {{ copy.unitCount }}</span></div></div>
        <div class="workbench-course-groups">
          <details v-for="(group, groupIndex) in courseGroups" :key="group.id" class="workbench-course-group user-frame-course-group" :open="group.open">
            <summary class="workbench-course-group__summary"><span class="workbench-course-group__number">{{ String(groupIndex + 1).padStart(2, "0") }}</span><span class="workbench-course-group__copy"><strong>{{ group.label }}</strong><small>{{ group.description }}</small></span><span class="workbench-course-group__count">{{ count(group.items, "chapter") }} {{ copy.chapterCount }} · {{ count(group.items, "lesson") }} {{ copy.unitCount }}</span><span v-if="group.items.some(itemHasCurrent)" class="workbench-course-group__pip" :aria-label="copy.currentLesson"></span><i class="workbench-disclosure__chevron" aria-hidden="true"></i></summary>
            <div class="workbench-course-group__items"><template v-for="item in group.items" :key="item.id"><RouterLink class="workbench-course-item user-frame-course-item" :class="{ 'is-active': isCourseItemActive(item) }" :to="itemTarget(item)" :aria-current="isCourseItemActive(item) ? 'page' : undefined" :data-course-id="item.id"><span class="workbench-course-item__index user-frame-course-item__index">{{ String(group.items.indexOf(item) + 1).padStart(2, "0") }}</span><span class="workbench-course-item__copy user-frame-course-item__copy"><strong>{{ item.label }}</strong><small>{{ item.meta }}</small></span><span class="workbench-course-item__status" :data-capability="item.capability" :title="courseCapability(item).detail"></span><span v-if="isCourseItemActive(item)" class="workbench-course-item__pip user-frame-course-item__pip" :aria-label="copy.currentLesson"></span></RouterLink><RouterLink v-for="(lesson, lessonIndex) in (item.children ?? [])" :key="lesson.id" class="workbench-course-item workbench-course-item--lesson user-frame-course-item" :class="{ 'is-active': isCourseItemActive(lesson) }" :to="itemTarget(lesson)" :aria-current="isCourseItemActive(lesson) ? 'page' : undefined" :data-course-id="lesson.id"><span class="workbench-course-item__index user-frame-course-item__index">{{ String(lessonIndex + 1).padStart(2, "0") }}</span><span class="workbench-course-item__copy user-frame-course-item__copy"><strong>{{ lesson.label }}</strong><small>{{ lesson.meta }}</small></span><span class="workbench-course-item__status" :data-capability="lesson.capability" :title="courseCapability(lesson).detail"></span><span v-if="isCourseItemActive(lesson)" class="workbench-course-item__pip user-frame-course-item__pip" :aria-label="copy.currentLesson"></span></RouterLink></template></div>
          </details>
        </div>
      </nav>
      <details class="workbench-sidebar__modules user-frame-sidebar__other"><summary><span class="workbench-section-label">{{ copy.secondaryNav }}</span><i class="workbench-disclosure__chevron" aria-hidden="true"></i></summary><nav class="user-frame-secondary-nav workbench-sidebar__modules-content" :aria-label="copy.secondaryNav"><RouterLink v-for="item in moduleNavigation" :key="`module-${item.id}`" class="workbench-nav-row workbench-nav-row--module" :class="{ 'is-active': isLearningGlobalNavigationActive(item.id === 'path' ? 'course' : item.id === 'practice' ? 'lab' : item.id === 'library' ? 'library' : 'overview', route.path) }" :to="item.to"><span class="workbench-nav-row__lead workbench-nav-row__lead--module"><span class="workbench-nav-row__module-icon"><svg aria-hidden="true"><use :href="`#wb-${item.icon}`" /></svg></span><span class="user-frame-nav__copy"><strong>{{ item.label }}</strong><small>{{ item.caption }}</small></span></span></RouterLink></nav></details>
      <details class="user-frame-sidebar__direct-tools" :aria-label="isEnglish ? 'Direct learning tools' : '直接学习工具'"><summary><span>{{ isEnglish ? 'Learning tools' : '学习工具' }}</span><i class="workbench-disclosure__chevron" aria-hidden="true"></i></summary><RouterLink v-for="tool in directTools" :key="tool.id" :to="tool.to"><span class="user-frame-direct-tool__icon"><svg aria-hidden="true"><use :href="`#wb-${tool.icon}`" /></svg></span><span><strong>{{ tool.label }}</strong><small>{{ tool.meta }}</small></span><svg class="user-frame-direct-tool__arrow" aria-hidden="true"><use href="#wb-arrow" /></svg></RouterLink></details>
      <footer class="workbench-sidebar__footer"><RouterLink :to="chapterTarget()"><svg aria-hidden="true"><use href="#wb-arrow" /></svg><span>{{ copy.catalogLink }}</span></RouterLink><RouterLink :to="auth.state.user ? '/user/profile' : loginTarget"><svg aria-hidden="true"><use href="#wb-arrow" /></svg><span>{{ copy.accountLink }}</span></RouterLink></footer>
    </aside>
  </div>
</template>

<style>
/* Strict dashboard styles are shared with the canonical workbench. This
   scope wrapper neutralizes its full-viewport canvas behavior in UserFrame. */
.learning-dashboard-sidebar-scope { display: contents; width: auto; height: auto; min-height: 0; padding: 0; overflow: visible; background: transparent; }
.learning-dashboard-sidebar-scope > .workbench-sidebar { height: 100%; }
.learning-dashboard-sidebar-scope .workbench-course-item__status { display: none; }
</style>
