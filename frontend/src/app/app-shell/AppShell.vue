<script setup lang="ts">
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import ThemeToggle from "../../shared/design/ThemeToggle.vue";
import { auth } from "../providers/runtime";
import LiquidMetalButton from "../../admin/components/LiquidMetalButton.vue";
import DirectionalArrowIcon from "../../shared/components/DirectionalArrowIcon.vue";
import ExitArrowIcon from "../../shared/components/ExitArrowIcon.vue";

type NavigationIcon = "overview" | "users" | "reviews" | "tasks" | "audit" | "settings" | "mail";
type NavigationItem = { to: string; label: string };
type AdminNavigationItem = NavigationItem & { icon: NavigationIcon };

const route = useRoute();
const router = useRouter();
const isAdmin = computed(() => route.meta.layout === "admin");
const desktopSidebarExpanded = ref(false);
const desktopSidebarPinned = ref(false);
const desktopSidebarVisible = computed(() => desktopSidebarExpanded.value || desktopSidebarPinned.value);
const mobileNavOpen = ref(false);
const mobileMenuToggle = ref<HTMLButtonElement | null>(null);
const mobileNavClose = ref<HTMLButtonElement | null>(null);
const mobileNavLayer = ref<HTMLElement | null>(null);
let mobileNavScrollLock: { bodyOverflow: string; rootOverflow: string } | null = null;

// Transport failures keep the last verified user in the store; that is still a usable session.
const hasRetainedSession = computed(() => Boolean(auth.state.user));
const adminNavItems: AdminNavigationItem[] = [
  { to: "/admin", label: "总览", icon: "overview" },
  { to: "/admin/users", label: "用户", icon: "users" },
  { to: "/admin/reviews", label: "审核", icon: "reviews" },
  { to: "/admin/tasks", label: "后台任务", icon: "tasks" },
  { to: "/admin/audit", label: "审计", icon: "audit" },
  { to: "/admin/settings", label: "模型设置", icon: "settings" },
  { to: "/admin/mail", label: "邮件设置", icon: "mail" },
];
const learningNavItems: NavigationItem[] = [
  { to: "/user/chapters", label: "章节" },
  { to: "/user/coach", label: "教练" },
  { to: "/user/classroom", label: "课堂" },
  { to: "/user/animation", label: "舞台" },
];

const navigationIconPaths: Record<NavigationIcon, string[]> = {
  overview: ["M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z"],
  users: ["M16 20v-1.6a3.4 3.4 0 0 0-3.4-3.4H7.4A3.4 3.4 0 0 0 4 18.4V20", "M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7", "M16 4.5a3.5 3.5 0 0 1 0 6.8", "M20 20v-1.6a3.4 3.4 0 0 0-2.4-3.25"],
  reviews: ["M12 3.5a8.5 8.5 0 1 0 8.5 8.5A8.5 8.5 0 0 0 12 3.5Z", "m8.5 12 2.25 2.25 4.75-4.75"],
  tasks: ["M8 4h8a2 2 0 0 1 2 2v14H6V6a2 2 0 0 1 2-2Z", "M9 3h6v3H9z", "M9 11h6", "M9 15h4"],
  audit: ["M8 6h11", "M8 12h11", "M8 18h11", "M4.5 6h.01", "M4.5 12h.01", "M4.5 18h.01"],
  settings: ["M4 6h16", "M4 12h16", "M4 18h16", "M8 4v4", "M16 10v4", "M10 16v4"],
  mail: ["M4 6h16v12H4z", "m4 7 8 6 8-6"],
};

const AdminNavIcon = (props: { name: NavigationIcon }) => h(
  "svg",
  {
    class: "admin-nav__icon",
    viewBox: "0 0 24 24",
    width: "18",
    height: "18",
    fill: "none",
    stroke: "currentColor",
    "stroke-width": "1.8",
    "stroke-linecap": "round",
    "stroke-linejoin": "round",
    "aria-hidden": "true",
    focusable: "false",
  },
  navigationIconPaths[props.name].map((d) => h("path", { d })),
);

watch(() => route.fullPath, () => closeMobileNavWithoutRestoringFocus());

function expandDesktopSidebar() { desktopSidebarExpanded.value = true; }
function collapseDesktopSidebar() {
  if (!desktopSidebarPinned.value) desktopSidebarExpanded.value = false;
}

function toggleDesktopSidebarPinned() {
  desktopSidebarPinned.value = !desktopSidebarPinned.value;
  desktopSidebarExpanded.value = desktopSidebarPinned.value;
}

function handleDesktopSidebarFocusOut(event: FocusEvent) {
  const sidebar = event.currentTarget;
  const nextFocus = event.relatedTarget;
  if (sidebar instanceof HTMLElement && nextFocus instanceof Node && sidebar.contains(nextFocus)) return;
  collapseDesktopSidebar();
}

function toggleMobileNav() {
  if (mobileNavOpen.value) {
    closeMobileNav();
    return;
  }
  lockMobileNavBackground();
  mobileNavOpen.value = true;
  void nextTick(() => mobileNavClose.value?.focus());
}

function closeMobileNav() { closeMobileNavWithFocus(true); }
function closeMobileNavWithoutRestoringFocus() { closeMobileNavWithFocus(false); }

function closeMobileNavWithFocus(restoreFocus: boolean) {
  if (!mobileNavOpen.value) return;
  mobileNavOpen.value = false;
  unlockMobileNavBackground();
  if (restoreFocus) void nextTick(() => mobileMenuToggle.value?.focus());
}

function lockMobileNavBackground() {
  if (mobileNavScrollLock) return;
  mobileNavScrollLock = {
    bodyOverflow: document.body.style.overflow,
    rootOverflow: document.documentElement.style.overflow,
  };
  document.body.style.overflow = "hidden";
  document.documentElement.style.overflow = "hidden";
}

function unlockMobileNavBackground() {
  if (!mobileNavScrollLock) return;
  document.body.style.overflow = mobileNavScrollLock.bodyOverflow;
  document.documentElement.style.overflow = mobileNavScrollLock.rootOverflow;
  mobileNavScrollLock = null;
}

function getMobileNavFocusableElements() {
  return Array.from(mobileNavLayer.value?.querySelectorAll<HTMLElement>([
    "a[href]",
    "button:not([disabled])",
    "input:not([disabled]):not([type='hidden'])",
    "select:not([disabled])",
    "textarea:not([disabled])",
    "[tabindex]:not([tabindex='-1'])",
  ].join(", ")) ?? []).filter((element) => element.tabIndex >= 0 && element.getAttribute("aria-hidden") !== "true");
}

function constrainMobileNavFocus(event: KeyboardEvent) {
  const focusableElements = getMobileNavFocusableElements();
  const mobileNav = mobileNavLayer.value;
  if (!mobileNav || focusableElements.length === 0) return;
  const firstElement = focusableElements[0];
  const lastElement = focusableElements[focusableElements.length - 1];
  const activeElement = document.activeElement;
  const focusIsInsideMenu = mobileNav.contains(activeElement);
  if (event.shiftKey && (!focusIsInsideMenu || activeElement === firstElement)) {
    event.preventDefault();
    lastElement.focus();
  } else if (!event.shiftKey && (!focusIsInsideMenu || activeElement === lastElement)) {
    event.preventDefault();
    firstElement.focus();
  }
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (!mobileNavOpen.value) return;
  if (event.key === "Escape") {
    event.preventDefault();
    closeMobileNav();
  } else if (event.key === "Tab") {
    constrainMobileNavFocus(event);
  }
}

function closeMobileNavAtDesktop() {
  if (window.innerWidth > 920) closeMobileNavWithoutRestoringFocus();
}

async function signOut() {
  closeMobileNavWithoutRestoringFocus();
  await auth.logout();
  await router.replace("/login");
}

onMounted(() => {
  window.addEventListener("resize", closeMobileNavAtDesktop);
  window.addEventListener("keydown", handleGlobalKeydown);
});
onBeforeUnmount(() => {
  unlockMobileNavBackground();
  window.removeEventListener("resize", closeMobileNavAtDesktop);
  window.removeEventListener("keydown", handleGlobalKeydown);
});
</script>

<template>
  <div class="app-frame" :class="{ 'app-frame--admin': isAdmin }">
    <template v-if="isAdmin">
      <header class="admin-mobile-bar">
        <RouterLink class="admin-mobile-bar__brand" to="/admin" aria-label="返回管理总览">
          <span class="admin-brand-mark" aria-hidden="true"></span><span>管理后台</span>
        </RouterLink>
        <div class="admin-mobile-bar__actions">
          <ThemeToggle />
          <button ref="mobileMenuToggle" class="admin-menu-toggle" type="button" :aria-expanded="mobileNavOpen" aria-controls="admin-mobile-navigation" :aria-label="mobileNavOpen ? '关闭管理端导航' : '打开管理端导航'" @click="toggleMobileNav"><span></span><span></span><span></span></button>
        </div>
      </header>

      <Transition name="admin-menu">
        <div v-if="mobileNavOpen" id="admin-mobile-navigation" ref="mobileNavLayer" class="admin-mobile-nav-layer" role="dialog" aria-modal="true" aria-label="管理端导航" tabindex="-1">
          <div class="admin-mobile-nav-layer__topline">
            <RouterLink class="admin-mobile-nav-layer__brand" to="/admin" aria-label="返回管理总览" @click="closeMobileNavWithoutRestoringFocus"><span class="admin-brand-mark" aria-hidden="true"></span><span>管理后台</span></RouterLink>
            <button ref="mobileNavClose" class="admin-mobile-nav-layer__close" type="button" aria-label="关闭管理端导航" @click="closeMobileNav"><span aria-hidden="true">x</span></button>
          </div>
          <nav class="admin-mobile-nav" aria-label="管理端移动导航">
            <RouterLink v-for="item in adminNavItems" :key="item.to" :to="item.to" :aria-label="item.label"><span class="admin-nav__mark"><AdminNavIcon :name="item.icon" /></span><span>{{ item.label }}</span></RouterLink>
          </nav>
          <div class="admin-mobile-nav__footer">
            <div class="admin-mobile-nav__identity"><span class="admin-mobile-nav__email">{{ auth.state.user?.email || '访客' }}</span><ThemeToggle /></div>
            <LiquidMetalButton v-if="hasRetainedSession" class="admin-mobile-nav__signout admin-signout-metal" view-mode="text" aria-label="退出" @click="signOut"><template #icon><span class="admin-signout-glyph" aria-hidden="true"><ExitArrowIcon /></span></template>退出</LiquidMetalButton>
            <RouterLink v-else class="admin-mobile-nav__signout" to="/login">登录</RouterLink>
          </div>
        </div>
      </Transition>

      <div class="admin-workspace" :class="{ 'is-sidebar-expanded': desktopSidebarVisible, 'is-sidebar-pinned': desktopSidebarPinned }">
        <aside class="admin-sidebar admin-sidebar--fixed" data-layout="admin-sidebar" :aria-label="desktopSidebarPinned ? '管理端导航，已固定展开' : desktopSidebarVisible ? '管理端导航，已展开' : '管理端导航，已收拢'" @mouseenter="expandDesktopSidebar" @mouseleave="collapseDesktopSidebar" @focusin="expandDesktopSidebar" @focusout="handleDesktopSidebarFocusOut">
          <div class="admin-sidebar__brand-row">
            <RouterLink class="admin-sidebar__brand" to="/admin" aria-label="返回管理总览" title="管理后台"><span class="admin-brand-mark" aria-hidden="true"></span><span class="admin-sidebar__label">管理后台</span></RouterLink>
            <button class="admin-sidebar__pin" type="button" :aria-pressed="desktopSidebarPinned" :aria-label="desktopSidebarPinned ? '取消固定管理端导航' : '固定管理端导航'" :title="desktopSidebarPinned ? '取消固定侧边栏' : '固定侧边栏'" @click.stop="toggleDesktopSidebarPinned">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M11.9999 17V21M6.9999 12.6667V6C6.9999 4.89543 7.89533 4 8.9999 4H14.9999C16.1045 4 16.9999 4.89543 16.9999 6V12.6667L18.9135 15.4308C19.3727 16.094 18.898 17 18.0913 17H5.90847C5.1018 17 4.62711 16.094 5.08627 15.4308L6.9999 12.6667Z" /></svg>
            </button>
          </div>
          <nav class="admin-sidebar__nav" aria-label="管理端导航">
            <RouterLink v-for="item in adminNavItems" :key="item.to" :to="item.to" :aria-label="item.label" :title="desktopSidebarExpanded ? undefined : item.label"><span class="admin-nav__mark"><AdminNavIcon :name="item.icon" /></span><span class="admin-sidebar__label">{{ item.label }}</span></RouterLink>
          </nav>
          <div class="admin-sidebar__footer">
            <div class="admin-sidebar__identity"><span class="admin-sidebar__email">{{ auth.state.user?.email || '访客' }}</span><ThemeToggle /></div>
            <div v-if="hasRetainedSession" class="admin-sidebar__signout" title="退出">
              <LiquidMetalButton v-if="desktopSidebarVisible" class="admin-signout-metal" view-mode="text" aria-label="退出" @click="signOut"><template #icon><span class="admin-signout-glyph" aria-hidden="true"><ExitArrowIcon /></span></template>退出</LiquidMetalButton>
              <LiquidMetalButton v-else class="admin-signout-metal" view-mode="icon" aria-label="退出" @click="signOut"><ExitArrowIcon class="admin-signout-glyph" /></LiquidMetalButton>
            </div>
            <RouterLink v-else class="admin-sidebar__signout" to="/login" title="登录"><DirectionalArrowIcon direction="right" class="admin-sidebar__login-icon" /><span class="admin-sidebar__label">登录</span></RouterLink>
          </div>
        </aside>
        <main class="app-main admin-workspace__main" id="main-content"><slot /></main>
      </div>
    </template>

    <template v-else>
      <header class="app-header">
        <RouterLink class="app-brand" to="/" aria-label="返回首页"><span class="app-brand__mark" aria-hidden="true">ds</span><span class="app-brand__name">数据结构工作台</span></RouterLink>
        <nav class="app-nav" aria-label="学习端导航"><RouterLink v-for="item in learningNavItems" :key="item.to" :to="item.to">{{ item.label }}</RouterLink></nav>
        <span class="app-header__spacer"></span>
        <ThemeToggle />
        <div class="app-user"><span class="app-user__email">{{ auth.state.user?.email || '访客' }}</span><button v-if="hasRetainedSession" class="button button--small" type="button" @click="signOut">退出</button><RouterLink v-else class="button button--small" to="/login">登录</RouterLink></div>
      </header>
      <main class="app-main" id="main-content"><slot /></main>
      <footer class="app-footer">Spring v1 共享基础 · 学习端</footer>
    </template>
  </div>
</template>

<style scoped>
.app-frame--admin {
  --admin-ink: var(--text);
  --admin-canvas: var(--bg);
  --admin-muted: var(--text-muted);
  --admin-line: var(--line);
  --admin-line-strong: var(--line-strong);
  --admin-panel: color-mix(in srgb, var(--surface) 82%, transparent);
  --admin-panel-solid: var(--surface);
  --admin-shadow: var(--shadow-sm);
  --admin-shadow-raised: var(--shadow-md);
  min-height: 100dvh;
  background: var(--admin-canvas);
  color: var(--admin-ink);
}

.admin-workspace {
  display: grid;
  width: 100%;
  min-height: 100dvh;
  grid-template-columns: 72px minmax(0, 1fr);
  background: var(--admin-canvas);
  transition: grid-template-columns 240ms cubic-bezier(0.22, 1, 0.36, 1);
}
.admin-workspace.is-sidebar-expanded { grid-template-columns: 300px minmax(0, 1fr); }

.admin-sidebar {
  position: fixed;
  z-index: 80;
  top: 0;
  left: 0;
  bottom: 0;
  display: flex;
  width: 72px;
  height: 100dvh;
  min-height: 100dvh;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid var(--admin-line);
  background: color-mix(in srgb, var(--surface) 72%, transparent);
  box-shadow: inset -1px 0 color-mix(in srgb, var(--surface) 58%, transparent);
  -webkit-backdrop-filter: blur(18px) saturate(0.9);
  backdrop-filter: blur(18px) saturate(0.9);
  transition: width 240ms cubic-bezier(0.22, 1, 0.36, 1), background-color 150ms ease;
}
.admin-workspace.is-sidebar-expanded .admin-sidebar { width: 300px; }

.admin-sidebar__brand-row {
  position: relative;
  display: flex;
  min-height: 72px;
  align-items: center;
  border-bottom: 1px solid var(--admin-line);
}

.admin-sidebar__brand,
.admin-mobile-bar__brand,
.admin-mobile-nav-layer__brand {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  color: var(--admin-ink);
  font-size: 15px;
  font-weight: 700;
  line-height: 1;
  text-decoration: none;
  white-space: nowrap;
}
.admin-sidebar__brand { min-height: 72px; padding: 0 48px 0 18px; }
.admin-workspace:not(.is-sidebar-expanded) .admin-sidebar__brand { width: 100%; justify-content: center; padding-right: 0; padding-left: 0; }
.admin-sidebar__pin {
  position: absolute;
  top: 24px;
  right: 10px;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  padding: 0;
  border: 1px solid color-mix(in srgb, var(--admin-line-strong) 86%, transparent);
  border-radius: 50%;
  background: color-mix(in srgb, var(--surface) 74%, transparent);
  color: var(--admin-muted);
  cursor: pointer;
  opacity: 0.82;
  box-shadow: inset 1px 1px 0 color-mix(in srgb, var(--surface) 72%, transparent), inset -1px -1px 0 color-mix(in srgb, var(--text) 10%, transparent), 0 5px 12px color-mix(in srgb, var(--text) 12%, transparent);
  -webkit-backdrop-filter: blur(12px) saturate(1.05);
  backdrop-filter: blur(12px) saturate(1.05);
  transition: color 150ms ease, background-color 150ms ease, border-color 150ms ease, opacity 150ms ease, box-shadow 180ms ease, transform 180ms ease;
}
.admin-sidebar:hover .admin-sidebar__pin,
.admin-sidebar:focus-within .admin-sidebar__pin,
.admin-workspace.is-sidebar-pinned .admin-sidebar__pin { opacity: 1; }
.admin-sidebar__pin:hover,
.admin-sidebar__pin:focus-visible { outline: 0; border-color: var(--admin-ink); background: color-mix(in srgb, var(--surface) 88%, transparent); color: var(--admin-ink); box-shadow: var(--focus-ring), inset 1px 1px 0 color-mix(in srgb, var(--surface) 76%, transparent), 0 7px 16px color-mix(in srgb, var(--text) 16%, transparent); }
.admin-sidebar__pin:active { transform: scale(0.94); }
.admin-sidebar__pin svg { width: 19px; height: 19px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; transform: rotate(90deg); transform-box: fill-box; transform-origin: center; transition: transform 220ms cubic-bezier(0.22, 1, 0.36, 1); }
.admin-sidebar__pin[aria-pressed="true"] svg { transform: rotate(0deg); }

.admin-brand-mark {
  position: relative;
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  overflow: hidden;
  border: 1px solid var(--admin-ink);
  border-radius: 8px;
  background: var(--admin-ink);
  box-shadow: inset 1px 1px color-mix(in srgb, var(--surface) 22%, transparent), inset -1px -1px rgba(0, 0, 0, 0.24);
}
.admin-brand-mark::before,
.admin-brand-mark::after { position: absolute; display: block; content: ""; }
.admin-brand-mark::before { top: 7px; left: 7px; width: 8px; height: 8px; border: 1px solid var(--surface); border-radius: 2px; }
.admin-brand-mark::after { right: 7px; bottom: 7px; width: 8px; height: 8px; border-radius: 50%; background: var(--surface); }

.admin-sidebar__nav { display: grid; gap: 4px; padding: 18px 10px; }
.admin-sidebar__nav a,
.admin-mobile-nav a,
.admin-sidebar__signout,
.admin-mobile-nav__signout {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 44px;
  align-items: center;
  gap: 12px;
  border: 1px solid transparent;
  color: var(--admin-muted);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.35;
  text-decoration: none;
  transition: color 150ms ease, background-color 150ms ease, border-color 150ms ease, transform 150ms ease;
}
.admin-sidebar__nav a,
.admin-sidebar__signout { padding: 0 12px; border-radius: 7px; }
.admin-sidebar__nav a::before {
  position: absolute;
  top: 50%;
  left: -11px;
  width: 2px;
  height: 20px;
  border-radius: 0 2px 2px 0;
  background: var(--admin-ink);
  content: "";
  opacity: 0;
  transform: translateY(-50%) scaleY(0.6);
  transition: opacity 150ms ease, transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}
.admin-nav__mark {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--admin-ink);
  line-height: 1;
  transition: transform 180ms cubic-bezier(0.22, 1, 0.36, 1), background-color 150ms ease, color 150ms ease;
}
.admin-nav__icon { display: block; width: 18px; height: 18px; flex: 0 0 18px; }
.admin-sidebar__label {
  display: block;
  max-width: 0;
  overflow: hidden;
  opacity: 0;
  transform: translateX(-6px);
  transition: max-width 240ms cubic-bezier(0.22, 1, 0.36, 1), opacity 130ms ease, transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
  white-space: nowrap;
}
.is-sidebar-expanded .admin-sidebar__label { max-width: 174px; opacity: 1; transform: translateX(0); transition-delay: 45ms; }
.admin-sidebar__nav a:hover,
.admin-sidebar__signout:hover { border-color: color-mix(in srgb, var(--admin-line-strong) 78%, transparent); background: color-mix(in srgb, var(--surface) 76%, transparent); color: var(--admin-ink); }
.admin-sidebar__nav a:hover .admin-nav__mark,
.admin-sidebar__nav a:focus-visible .admin-nav__mark { transform: translateX(1px); }
.admin-sidebar__nav a.router-link-exact-active { border-color: color-mix(in srgb, var(--admin-line-strong) 76%, transparent); background: color-mix(in srgb, var(--text) 8%, var(--surface)); color: var(--admin-ink); }
.admin-sidebar__nav a.router-link-exact-active::before { opacity: 1; transform: translateY(-50%) scaleY(1); }
.admin-sidebar__nav a.router-link-exact-active .admin-nav__mark { background: var(--admin-ink); color: var(--surface); }
.admin-sidebar__nav a:focus-visible,
.admin-sidebar__signout:focus-visible,
.admin-mobile-nav a:focus-visible,
.admin-mobile-nav__signout:focus-visible { outline: 0; box-shadow: var(--focus-ring); }

.admin-sidebar__footer { display: grid; gap: 10px; margin-top: auto; padding: 14px 10px 18px; border-top: 1px solid var(--admin-line); }
.admin-sidebar__identity { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 10px; padding: 0 4px; }
.admin-workspace:not(.is-sidebar-expanded) .admin-sidebar__footer { padding-right: 4px; padding-left: 4px; }
.admin-workspace:not(.is-sidebar-expanded) .admin-sidebar__identity { justify-content: center; gap: 0; padding-right: 0; padding-left: 0; }
.admin-sidebar__email {
  max-width: 0;
  overflow: hidden;
  color: var(--admin-muted);
  font-size: 12px;
  opacity: 0;
  text-overflow: ellipsis;
  transform: translateX(-6px);
  transition: max-width 240ms cubic-bezier(0.22, 1, 0.36, 1), opacity 130ms ease, transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
  white-space: nowrap;
}
.is-sidebar-expanded .admin-sidebar__email { max-width: 180px; opacity: 1; transform: translateX(0); transition-delay: 45ms; }
.admin-sidebar__signout { width: 100%; min-height: 46px; justify-content: flex-start; background: transparent; cursor: pointer; font: inherit; }
.admin-sidebar__signout .liquid-metal-button { max-width: 100%; }
.admin-workspace:not(.is-sidebar-expanded) .admin-sidebar__signout { display: flex; justify-content: center; }

.admin-workspace__main {
  grid-column: 2;
  width: 100%;
  min-width: 0;
  max-width: none;
  margin: 0;
  padding: clamp(24px, 4vw, 54px);
  background: var(--admin-canvas);
  animation: admin-workspace-enter 220ms cubic-bezier(0.22, 1, 0.36, 1) both;
}
.admin-mobile-bar,
.admin-mobile-nav-layer { display: none; }
@keyframes admin-workspace-enter { from { opacity: 0; transform: translateY(8px) scale(0.995); } to { opacity: 1; transform: translateY(0) scale(1); } }

@media (max-width: 920px) {
  .admin-workspace { display: block; min-height: calc(100dvh - 60px); }
  .admin-sidebar { display: none; }
  .admin-workspace__main { grid-column: auto; min-height: calc(100dvh - 60px); padding: 22px 14px 40px; }
  .admin-mobile-bar {
    position: sticky;
    z-index: 70;
    top: 0;
    display: flex;
    min-height: 60px;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding: 0 14px;
    border-bottom: 1px solid var(--admin-line);
    background: color-mix(in srgb, var(--surface) 86%, transparent);
    box-shadow: inset 0 1px color-mix(in srgb, var(--surface) 74%, transparent);
    -webkit-backdrop-filter: blur(18px) saturate(0.9);
    backdrop-filter: blur(18px) saturate(0.9);
  }
  .admin-mobile-bar__brand { gap: 9px; font-size: 14px; }
  .admin-mobile-bar__brand .admin-brand-mark { width: 28px; height: 28px; flex-basis: 28px; }
  .admin-mobile-bar__brand .admin-brand-mark::before { top: 6px; left: 6px; width: 6px; height: 6px; }
  .admin-mobile-bar__brand .admin-brand-mark::after { right: 6px; bottom: 6px; width: 6px; height: 6px; }
  .admin-mobile-bar__actions { display: inline-flex; align-items: center; gap: 8px; }
  .admin-menu-toggle,
  .admin-mobile-nav-layer__close {
    display: inline-grid;
    width: 44px;
    height: 44px;
    place-content: center;
    padding: 0;
    border: 1px solid var(--admin-line-strong);
    border-radius: 50%;
    background: var(--surface);
    color: var(--admin-ink);
    cursor: pointer;
    font: inherit;
    transition: transform 140ms ease, background-color 150ms ease, border-color 150ms ease;
  }
  .admin-menu-toggle { gap: 4px; }
  .admin-menu-toggle:active,
  .admin-mobile-nav-layer__close:active { transform: scale(0.95); }
  .admin-menu-toggle span { display: block; width: 17px; height: 1.5px; background: currentColor; transition: transform 180ms cubic-bezier(0.22, 1, 0.36, 1), opacity 120ms ease; }
  .admin-menu-toggle[aria-expanded="true"] span:nth-child(1) { transform: translateY(5.5px) rotate(45deg); }
  .admin-menu-toggle[aria-expanded="true"] span:nth-child(2) { opacity: 0; }
  .admin-menu-toggle[aria-expanded="true"] span:nth-child(3) { transform: translateY(-5.5px) rotate(-45deg); }
  .admin-mobile-nav-layer { position: fixed; z-index: 100; inset: 0; display: flex; min-height: 100dvh; flex-direction: column; padding: 18px 18px 24px; overflow: auto; background: var(--admin-canvas); color: var(--admin-ink); }
  .admin-mobile-nav-layer__topline { display: flex; min-height: 44px; align-items: center; justify-content: space-between; gap: 14px; }
  .admin-mobile-nav-layer__brand { font-size: 16px; }
  .admin-mobile-nav-layer__close { font-size: 21px; line-height: 1; }
  .admin-mobile-nav { display: grid; gap: 8px; margin-top: 28px; }
  .admin-mobile-nav a { min-height: 54px; padding: 0 12px; border-color: var(--admin-line); border-radius: 8px; background: color-mix(in srgb, var(--surface) 62%, transparent); color: var(--admin-ink); }
  .admin-mobile-nav a.router-link-exact-active { border-color: var(--admin-ink); background: color-mix(in srgb, var(--text) 8%, var(--surface)); }
  .admin-mobile-nav a.router-link-exact-active .admin-nav__mark { background: var(--admin-ink); color: var(--surface); }
  .admin-mobile-nav__footer { display: grid; gap: 14px; margin-top: auto; padding-top: 24px; border-top: 1px solid var(--admin-line); }
  .admin-mobile-nav__identity { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 16px; }
  .admin-mobile-nav__email { overflow: hidden; color: var(--admin-muted); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
  .admin-mobile-nav__signout { justify-content: center; border-color: var(--admin-line-strong); border-radius: 8px; background: var(--surface); color: var(--admin-ink); cursor: pointer; font: inherit; }
  .admin-menu-enter-active,
  .admin-menu-leave-active { transition: opacity 200ms ease-out, transform 220ms cubic-bezier(0.22, 1, 0.36, 1); }
  .admin-menu-enter-from,
  .admin-menu-leave-to { opacity: 0; transform: translateX(-16px); }
}

@media (max-width: 520px) {
  .admin-mobile-bar { padding-right: 12px; padding-left: 12px; }
  .admin-mobile-bar__brand > span:last-child { display: none; }
  .admin-workspace__main { padding-right: 10px; padding-left: 10px; }
}
@media (prefers-reduced-motion: reduce) {
  .admin-workspace,
  .admin-sidebar__label,
  .admin-sidebar__email,
  .admin-sidebar__nav a,
  .admin-sidebar__nav a::before,
  .admin-nav__mark,
  .admin-sidebar__signout,
  .admin-menu-toggle,
  .admin-menu-toggle span,
  .admin-mobile-nav-layer__close,
  .admin-menu-enter-active,
  .admin-menu-leave-active,
  .admin-workspace__main { animation: none !important; transition: none !important; }
}
@media (prefers-reduced-transparency: reduce) {
  .admin-sidebar,
  .admin-mobile-bar { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
@media (prefers-contrast: more) {
  .admin-sidebar,
  .admin-mobile-bar,
  .admin-mobile-nav a,
  .admin-menu-toggle,
  .admin-mobile-nav-layer__close { border-color: var(--admin-ink); }
}
</style>
