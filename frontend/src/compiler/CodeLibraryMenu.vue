<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from "vue";
import { ArrowLeft, BookOpen, Check, ChevronDown, ChevronRight, FileCode, Layers } from "@lucide/vue";
import { useI18n } from "../shared/i18n/locale";
import type { LibraryMenuItem } from "./library-catalog";

defineOptions({ inheritAttrs: false });
const props = defineProps<{
  label: string;
  items: LibraryMenuItem[];
  selectedId: string;
  count: number;
}>();
const emit = defineEmits<{ select: [id: string] }>();
const { t } = useI18n();
const menuId = useId();
const triggerRef = ref<HTMLButtonElement | null>(null);
const popupRef = ref<HTMLDivElement | null>(null);
const panelRef = ref<HTMLDivElement | null>(null);
const searchRef = ref<HTMLInputElement | null>(null);
const open = ref(false);
const path = ref<LibraryMenuItem[]>([]);
const search = ref("");
const direction = ref<"forward" | "backward">("forward");
const activeIndex = ref(0);
const panelHeight = ref(0);
const popupStyle = ref<Record<string, string>>({});
const listHeight = ref(288);

const panelKey = computed(() => path.value.map((item) => item.id).join("/") || "root");
const panelLabel = computed(() => path.value.at(-1)?.label ?? props.label);
const panelItems = computed(() => path.value.at(-1)?.children ?? props.items);
const filteredItems = computed(() => {
  const query = search.value.trim().toLowerCase();
  return panelItems.value.filter((item) => !query || `${item.label} ${item.keywords ?? ""}`.toLowerCase().includes(query));
});
const hasSelection = computed(() => props.items.some(containsSelection));

function containsSelection(item: LibraryMenuItem): boolean {
  return item.id === props.selectedId || Boolean(item.children?.some(containsSelection));
}

function rows() {
  return Array.from(panelRef.value?.querySelectorAll<HTMLButtonElement>(".code-menu__item") ?? []);
}

function focusRow(index: number) {
  const buttons = rows();
  if (!buttons.length) return;
  activeIndex.value = (index + buttons.length) % buttons.length;
  buttons[activeIndex.value]?.focus();
}

// Teleport keeps the popup clear of the sidebar's scrolling boundary.
async function positionMenu() {
  if (!open.value || !triggerRef.value) return;
  const rect = triggerRef.value.getBoundingClientRect();
  const width = Math.min(288, Math.max(0, window.innerWidth - 24));
  const below = window.innerHeight - rect.bottom - 20;
  const above = rect.top - 20;
  const naturalHeight = panelRef.value?.offsetHeight ?? 0;
  const upward = below < naturalHeight && above > below;
  const available = upward ? above : below;
  listHeight.value = Math.max(48, Math.min(288, available - (path.value.length ? 49 : 0) - 2));
  await nextTick();
  if (!open.value) return;
  panelHeight.value = panelRef.value?.offsetHeight ?? 0;
  const height = panelHeight.value + 2;
  popupStyle.value = {
    width: `${width}px`,
    left: `${Math.max(12, Math.min(rect.left + (rect.width - width) / 2, window.innerWidth - width - 12))}px`,
    top: `${Math.max(12, upward ? rect.top - height - 8 : rect.bottom + 8)}px`,
    "--menu-list-height": `${listHeight.value}px`,
  };
}

async function showMenu() {
  path.value = [];
  search.value = "";
  direction.value = "forward";
  activeIndex.value = 0;
  panelHeight.value = 0;
  open.value = true;
  await nextTick();
  await positionMenu();
  if (open.value) focusRow(0);
}

function close(restoreFocus = false) {
  open.value = false;
  if (restoreFocus) triggerRef.value?.focus();
}

async function choose(item: LibraryMenuItem) {
  if (item.children) {
    direction.value = "forward";
    path.value = [...path.value, item];
    search.value = "";
    activeIndex.value = 0;
    await nextTick();
    await positionMenu();
    if (open.value) searchRef.value?.focus();
    return;
  }
  emit("select", item.id);
  close(true);
}

async function goBack() {
  if (!path.value.length) return;
  const previous = path.value.at(-1)!.id;
  direction.value = "backward";
  path.value = path.value.slice(0, -1);
  search.value = "";
  await nextTick();
  await positionMenu();
  if (open.value) focusRow(Math.max(0, filteredItems.value.findIndex((item) => item.id === previous)));
}

function onPanelKeydown(event: KeyboardEvent) {
  if (event.key === "ArrowLeft") {
    if (event.target instanceof HTMLInputElement) return;
    event.preventDefault();
    void goBack();
  } else if (event.key === "ArrowRight" && !(event.target instanceof HTMLInputElement)) {
    const item = filteredItems.value[activeIndex.value];
    if (item?.children) {
      event.preventDefault();
      void choose(item);
    }
  } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    const fromSearch = event.target instanceof HTMLInputElement;
    focusRow(fromSearch ? (event.key === "ArrowDown" ? 0 : filteredItems.value.length - 1) : activeIndex.value + (event.key === "ArrowDown" ? 1 : -1));
  } else if ((event.key === "Home" || event.key === "End") && !(event.target instanceof HTMLInputElement)) {
    event.preventDefault();
    focusRow(event.key === "Home" ? 0 : filteredItems.value.length - 1);
  }
}

function outside(event: Event) {
  if (!open.value || !(event.target instanceof Node)) return;
  if (!triggerRef.value?.contains(event.target) && !popupRef.value?.contains(event.target)) close();
}

function escape(event: KeyboardEvent) {
  if (!open.value || event.key !== "Escape") return;
  event.preventDefault();
  event.stopPropagation();
  close(true);
}

function reposition(event: Event) {
  if (event.target instanceof Node && popupRef.value?.contains(event.target)) return;
  void positionMenu();
}

watch(search, () => {
  activeIndex.value = 0;
  void positionMenu();
});
watch(() => props.items, () => close());

onMounted(() => {
  document.addEventListener("pointerdown", outside);
  document.addEventListener("focusin", outside);
  document.addEventListener("keydown", escape);
  window.addEventListener("resize", reposition);
  window.addEventListener("scroll", reposition, true);
});
onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", outside);
  document.removeEventListener("focusin", outside);
  document.removeEventListener("keydown", escape);
  window.removeEventListener("resize", reposition);
  window.removeEventListener("scroll", reposition, true);
});
</script>

<template>
  <button
    ref="triggerRef"
    v-bind="$attrs"
    class="code-menu__trigger"
    :class="{ 'code-menu__trigger--selected': hasSelection }"
    type="button"
    aria-haspopup="menu"
    :aria-expanded="open"
    :aria-controls="open ? menuId : undefined"
    :aria-label="label"
    @click="open ? close() : showMenu()"
    @keydown.down.prevent="showMenu"
    @keydown.up.prevent="showMenu"
  >
    <BookOpen :size="16" aria-hidden="true" />
    <span class="code-menu__trigger-label">{{ label }}</span>
    <span class="code-menu__count">{{ count }}</span>
    <ChevronDown :size="16" :class="{ 'code-menu__chevron--open': open }" aria-hidden="true" />
  </button>

  <Teleport to="body">
    <Transition name="code-menu-popup">
      <div
        v-if="open"
        :id="menuId"
        ref="popupRef"
        class="code-menu__popup"
        :style="popupStyle"
        @keydown="onPanelKeydown"
      >
        <div class="code-menu__viewport" :style="panelHeight ? { height: `${panelHeight}px` } : undefined">
          <Transition :name="`code-menu-${direction}`">
            <div :key="panelKey" ref="panelRef" class="code-menu__panel">
              <div v-if="path.length" class="code-menu__header">
                <button class="code-menu__back" type="button" :aria-label="t('compiler.menuBack')" :title="t('compiler.menuBack')" @click="goBack">
                  <ArrowLeft :size="16" aria-hidden="true" />
                </button>
                <input
                  ref="searchRef"
                  v-model="search"
                  class="code-menu__search"
                  type="search"
                  :aria-label="t('compiler.menuSearch')"
                  :placeholder="t('compiler.menuSearch')"
                  spellcheck="false"
                >
              </div>
              <div class="code-menu__items" role="menu" :aria-label="panelLabel">
                <button
                  v-for="(item, index) in filteredItems"
                  :key="item.id"
                  class="code-menu__item"
                  :class="{ 'code-menu__item--selected': !item.children && item.id === selectedId }"
                  type="button"
                  :role="item.children ? 'menuitem' : 'menuitemradio'"
                  :aria-checked="!item.children ? item.id === selectedId : undefined"
                  :aria-haspopup="item.children ? 'menu' : undefined"
                  :tabindex="activeIndex === index ? 0 : -1"
                  :title="item.origin"
                  :data-entry-id="item.children ? undefined : item.id"
                  @focus="activeIndex = index"
                  @click="choose(item)"
                >
                  <component :is="item.children ? Layers : FileCode" :size="16" class="code-menu__icon" aria-hidden="true" />
                  <span class="code-menu__label">{{ item.label }}</span>
                  <span v-if="item.children" class="code-menu__count">{{ item.children.length }}</span>
                  <ChevronRight v-if="item.children" :size="16" aria-hidden="true" />
                  <Check v-else-if="item.id === selectedId" :size="16" aria-hidden="true" />
                </button>
                <p v-if="!filteredItems.length" class="code-menu__empty" role="status">
                  {{ search ? t('compiler.noMatch') : t('compiler.emptyChapter') }}
                </p>
              </div>
            </div>
          </Transition>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.code-menu__trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 38px;
  padding: 8px 12px;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  background: var(--surface);
  color: var(--text);
  font-family: var(--font-ui);
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  text-align: left;
  cursor: pointer;
  transition: background 150ms, border-color 150ms;
}
.code-menu__trigger:hover,
.code-menu__trigger[aria-expanded="true"] { background: color-mix(in srgb, var(--text) 6%, var(--surface)); }
.code-menu__trigger--selected { border-color: color-mix(in srgb, var(--text) 50%, var(--surface)); }
.code-menu__trigger-label { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.code-menu__trigger svg { flex-shrink: 0; color: var(--text-muted); transition: transform 200ms; }
.code-menu__chevron--open { transform: rotate(180deg); }
.code-menu__count { flex-shrink: 0; color: var(--text-muted); font-family: var(--font-mono, monospace); font-size: 12px; font-weight: 400; }
.code-menu__popup {
  position: fixed;
  z-index: 100;
  width: min(288px, calc(100vw - 24px));
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 16px;
  background: var(--surface);
  color: var(--text);
  box-shadow: 0 10px 24px color-mix(in srgb, var(--text) 14%, transparent), 0 2px 6px color-mix(in srgb, var(--text) 7%, transparent);
  font-family: var(--font-ui);
  font-size: 14px;
  line-height: 20px;
  transform-origin: top center;
}
.code-menu__viewport { position: relative; overflow: hidden; transition: height 380ms cubic-bezier(.22, 1.1, .36, 1); }
.code-menu__panel { width: 100%; }
.code-menu__header { display: flex; align-items: center; gap: 4px; padding: 8px; border-bottom: 1px solid var(--line-strong); }
.code-menu__back { display: grid; place-items: center; flex-shrink: 0; width: 32px; height: 32px; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--text-muted); cursor: pointer; }
.code-menu__back:hover { background: color-mix(in srgb, var(--text) 6%, transparent); color: var(--text); }
.code-menu__search { flex: 1; min-width: 0; height: 32px; padding: 0 4px; border: 0; outline: none; background: transparent; color: var(--text); font: inherit; }
.code-menu__search::placeholder { color: var(--text-muted); }
.code-menu__items { max-height: min(288px, var(--menu-list-height, 288px)); overflow-y: auto; overscroll-behavior: contain; }
.code-menu__item { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 40px; padding: 10px 16px; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; transition: background 150ms, color 150ms; }
.code-menu__item + .code-menu__item { border-top: 1px solid var(--line-strong); }
.code-menu__item:hover,
.code-menu__item:focus-visible,
.code-menu__item--selected { background: color-mix(in srgb, var(--text) 6%, var(--surface)); }
.code-menu__item svg { flex-shrink: 0; color: var(--text-muted); }
.code-menu__label { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.code-menu__empty { margin: 0; padding: 24px 16px; color: var(--text-muted); text-align: center; }
.code-menu__trigger:focus-visible,
.code-menu__back:focus-visible,
.code-menu__item:focus-visible { outline: 2px solid var(--text-muted); outline-offset: -3px; }
.code-menu-popup-enter-active,
.code-menu-popup-leave-active { transition: opacity 380ms, transform 380ms cubic-bezier(.22, 1.1, .36, 1); }
.code-menu-popup-enter-from,
.code-menu-popup-leave-to { opacity: 0; transform: translateY(-6px) scale(.97); }
.code-menu-forward-enter-active,
.code-menu-forward-leave-active,
.code-menu-backward-enter-active,
.code-menu-backward-leave-active { transition: transform 380ms cubic-bezier(.22, 1.1, .36, 1), opacity 380ms, filter 380ms; }
.code-menu-forward-leave-active,
.code-menu-backward-leave-active { position: absolute; inset: 0; pointer-events: none; }
.code-menu-forward-enter-from,
.code-menu-backward-leave-to { transform: translateX(100%); opacity: 0; filter: blur(50px); }
.code-menu-forward-leave-to,
.code-menu-backward-enter-from { transform: translateX(-100%); opacity: 0; filter: blur(50px); }
@media (prefers-reduced-motion: reduce) {
  .code-menu__viewport,
  .code-menu__trigger svg,
  .code-menu-popup-enter-active,
  .code-menu-popup-leave-active,
  .code-menu-forward-enter-active,
  .code-menu-forward-leave-active,
  .code-menu-backward-enter-active,
  .code-menu-backward-leave-active { transition: none; }
  .code-menu-forward-enter-from,
  .code-menu-forward-leave-to,
  .code-menu-backward-enter-from,
  .code-menu-backward-leave-to { filter: none; }
}
</style>
