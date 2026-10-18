<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { Copy, Scissors, ClipboardPaste, Undo2, Redo2, IndentIncrease, Play } from "@lucide/vue";
import downloadIcon from "../assets/compiler/download.svg?raw";
import { useI18n } from "../shared/i18n/locale";

const props = defineProps<{
  x: number; y: number; canUndo: boolean; canRedo: boolean; canPaste: boolean; formatting: boolean;
}>();
const emit = defineEmits<{ select: [action: string]; close: [restoreFocus: boolean] }>();
const { t } = useI18n();
const menu = ref<HTMLElement | null>(null);
const active = ref(-1);
const icons = { cut: Scissors, copy: Copy, paste: ClipboardPaste, undo: Undo2, redo: Redo2, format: IndentIncrease, run: Play };
const items = computed(() => [
  { id: "cut", label: t("compiler.cut"), shortcut: "Ctrl+X", disabled: false },
  { id: "copy", label: t("compiler.copy"), shortcut: "Ctrl+C", disabled: false },
  { id: "paste", label: t("compiler.paste"), shortcut: "Ctrl+V", disabled: !props.canPaste },
  { id: "undo", label: t("compiler.undo"), shortcut: "Ctrl+Z", disabled: !props.canUndo },
  { id: "redo", label: t("compiler.redo"), shortcut: "Ctrl+Y", disabled: !props.canRedo },
  { id: "format", label: t("compiler.format"), shortcut: "Shift+Alt+F", disabled: props.formatting },
  { id: "import", label: t("compiler.importCode"), shortcut: "Ctrl+O", disabled: false },
  { id: "export", label: t("compiler.exportCode"), shortcut: "Ctrl+S", disabled: false },
  { id: "run", label: t("compiler.run"), shortcut: "F6", disabled: false },
]);
const placement = computed(() => {
  const width = Math.min(246, window.innerWidth - 16);
  const height = Math.min(369, window.innerHeight - 16);
  const left = Math.max(8, Math.min(props.x + width + 8 <= window.innerWidth ? props.x : props.x - width, window.innerWidth - width - 8));
  const top = Math.max(8, Math.min(props.y + height + 8 <= window.innerHeight ? props.y : props.y - height, window.innerHeight - height - 8));
  return { left: `${left}px`, top: `${top}px`, width: `${width}px`, maxHeight: `${height}px`, transformOrigin: `${props.x - left}px ${props.y - top}px` };
});

function focusItem(index: number) {
  if (items.value[index]?.disabled) return;
  active.value = index;
  const item = menu.value?.querySelectorAll<HTMLButtonElement>("button")[index];
  item?.focus({ preventScroll: true });
  item?.scrollIntoView?.({ block: "nearest" });
}

function choose(index: number) {
  const item = items.value[index];
  if (item && !item.disabled) emit("select", item.id);
}

function keydown(event: KeyboardEvent) {
  const available = items.value.flatMap((item, index) => item.disabled ? [] : [index]);
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    const at = available.indexOf(active.value);
    const direction = event.key === "ArrowDown" ? 1 : -1;
    const next = at === -1 ? (direction === 1 ? 0 : available.length - 1) : (at + direction + available.length) % available.length;
    focusItem(available[next]!);
  } else if (event.key === "Home" || event.key === "End") {
    event.preventDefault();
    focusItem(available[event.key === "Home" ? 0 : available.length - 1]!);
  } else if (event.key === "Escape" || event.key === "Tab") {
    event.preventDefault();
    emit("close", true);
  } else if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    choose(active.value);
  }
}

function outside(event: Event) {
  if (!menu.value?.contains(event.target as Node)) emit("close", false);
}
function dismiss() { emit("close", false); }

onMounted(() => {
  menu.value?.focus({ preventScroll: true });
  document.addEventListener("pointerdown", outside, true);
  document.addEventListener("scroll", outside, true);
  window.addEventListener("resize", dismiss);
  window.addEventListener("blur", dismiss);
});
onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", outside, true);
  document.removeEventListener("scroll", outside, true);
  window.removeEventListener("resize", dismiss);
  window.removeEventListener("blur", dismiss);
});
</script>

<template>
  <div ref="menu" class="editor-menu" role="menu" tabindex="-1" :aria-label="t('compiler.editorMenu')"
    :style="placement" @keydown.stop="keydown" @contextmenu.prevent>
    <template v-for="(item, index) in items" :key="item.id">
      <div v-if="index === 3 || index === 5 || index === 6 || index === 8" class="editor-menu__separator" role="separator"></div>
      <button class="editor-menu__item" :class="{ 'editor-menu__item--active': active === index }"
        type="button" role="menuitem" tabindex="-1" :aria-disabled="item.disabled"
        @pointermove="focusItem(index)" @click="choose(index)">
        <span v-if="item.id === 'import' || item.id === 'export'" class="editor-menu__file-icon" :class="{ 'editor-menu__file-icon--import': item.id === 'import' }" aria-hidden="true" v-html="downloadIcon"></span>
        <component v-else :is="icons[item.id as keyof typeof icons]" :size="16" aria-hidden="true" />
        <span class="editor-menu__label">{{ item.label }}</span>
        <span class="editor-menu__shortcut" aria-hidden="true">{{ item.shortcut }}</span>
      </button>
    </template>
  </div>
</template>

<style scoped>
.editor-menu { position: fixed; z-index: 150; overflow: hidden; overscroll-behavior: contain; padding: 6px; border: 1px solid #e7e5e4; border-radius: 15px; background: #fff; color: #44403c; box-shadow: 0 1px 2px rgb(28 25 23 / 6%), 0 16px 36px -18px rgb(28 25 23 / 50%); outline: none; font-family: var(--font-ui); }
.editor-menu__item { display: flex; align-items: center; gap: 9px; width: 100%; height: 35px; padding: 0 11px; border: 0; border-radius: 8px; background: transparent; color: inherit; font: inherit; font-size: 14px; text-align: left; cursor: default; user-select: none; }
.editor-menu__item svg { flex: none; color: #78716c; }
.editor-menu__file-icon { display: inline-flex; flex: none; color: #78716c; }
.editor-menu__file-icon :deep(svg) { display: block; width: 16px; height: 16px; }
.editor-menu__file-icon--import { transform: rotate(180deg); }
.editor-menu__item--active { background: #f5f5f4; outline: none; }
.editor-menu__item[aria-disabled="true"] { color: #a8a29e; }
.editor-menu__item[aria-disabled="true"] svg { color: inherit; }
.editor-menu__label { flex: 1; min-width: 0; }
.editor-menu__shortcut { flex: none; color: #78716c; font-family: var(--font-mono, monospace); font-size: 11.5px; }
.editor-menu__separator { height: 10px; padding: 4px; }
.editor-menu__separator::after { content: ""; display: block; height: 1px; background: #e7e5e4; }
:global([data-theme="dark"]) .editor-menu { border-color: rgb(255 255 255 / 16%); background: #1d1d1a; color: #e7e5e4; box-shadow: 0 2px 12px rgb(0 0 0 / 60%); }
:global([data-theme="dark"]) .editor-menu__item--active { background: rgb(255 255 255 / 10%); }
:global([data-theme="dark"]) .editor-menu__item svg,
:global([data-theme="dark"]) .editor-menu__file-icon,
:global([data-theme="dark"]) .editor-menu__shortcut { color: #a8a29e; }
:global([data-theme="dark"]) .editor-menu__item[aria-disabled="true"] { color: #78716c; }
:global([data-theme="dark"]) .editor-menu__separator::after { background: rgb(255 255 255 / 10%); }
</style>
