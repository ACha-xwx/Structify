<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from "vue";

defineOptions({ inheritAttrs: false });

export type RuntimeSelectValue = string | number;

export interface RuntimeSelectOption {
  value: RuntimeSelectValue;
  label: string;
  disabled?: boolean;
}

const props = withDefaults(defineProps<{
  modelValue: RuntimeSelectValue;
  options: RuntimeSelectOption[];
  ariaLabel: string;
  disabled?: boolean;
  placeholder?: string;
  testId?: string;
  className?: string;
  field?: string;
}>(), {
  disabled: false,
  placeholder: "",
  testId: "",
  className: "",
  field: "",
});

const emit = defineEmits<{
  "update:modelValue": [value: RuntimeSelectValue];
  change: [value: RuntimeSelectValue];
}>();

const menuId = `runtime-select-menu-${useId()}`;
const trigger = ref<HTMLButtonElement | null>(null);
const selectRoot = ref<HTMLElement | null>(null);
const menu = ref<HTMLElement | null>(null);
const open = ref(false);
const activeIndex = ref(-1);
const menuStyle = ref<Record<string, string>>({});

const selectedIndex = computed(() => props.options.findIndex((option) => option.value === props.modelValue));
const selectedOption = computed(() => selectedIndex.value >= 0 ? props.options[selectedIndex.value] : null);
const displayLabel = computed(() => selectedOption.value?.label || props.placeholder || "");

function enabledIndex(from: number, direction: 1 | -1): number {
  if (!props.options.length) return -1;
  let index = from;
  for (let attempts = 0; attempts < props.options.length; attempts += 1) {
    index = (index + direction + props.options.length) % props.options.length;
    if (!props.options[index]?.disabled) return index;
  }
  return -1;
}

function firstEnabledIndex(): number {
  return props.options.findIndex((option) => !option.disabled);
}

function lastEnabledIndex(): number {
  for (let index = props.options.length - 1; index >= 0; index -= 1) {
    if (!props.options[index]?.disabled) return index;
  }
  return -1;
}

function positionMenu() {
  const rect = trigger.value?.getBoundingClientRect();
  if (!rect) return;
  const viewportWidth = window.innerWidth || document.documentElement.clientWidth;
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
  const availableBelow = Math.max(0, viewportHeight - rect.bottom - 8);
  const availableAbove = Math.max(0, rect.top - 8);
  const estimatedHeight = Math.min(320, Math.max(56, props.options.length * 48 + 8));
  const shouldOpenAbove = availableBelow < Math.min(estimatedHeight, 200) && availableAbove > availableBelow;
  const menuWidth = Math.min(Math.max(1, rect.width), Math.max(1, viewportWidth - 16));
  const left = Math.min(
    Math.max(8, rect.left),
    Math.max(8, viewportWidth - menuWidth - 8),
  );
  menuStyle.value = shouldOpenAbove
    ? {
        left: `${left}px`,
        top: `${Math.max(8, rect.top - Math.min(estimatedHeight, availableAbove))}px`,
        width: `${menuWidth}px`,
        maxHeight: `${Math.max(80, availableAbove)}px`,
      }
    : {
        left: `${left}px`,
        top: `${rect.bottom + 8}px`,
        width: `${menuWidth}px`,
        maxHeight: `${Math.max(80, availableBelow)}px`,
      };
}

async function openMenu(preferredIndex = selectedIndex.value) {
  if (props.disabled || !props.options.length) return;
  const initial = preferredIndex >= 0 && !props.options[preferredIndex]?.disabled
    ? preferredIndex
    : firstEnabledIndex();
  activeIndex.value = initial;
  // Calculate the fixed position before mounting the teleported menu so the
  // enter transition never flashes at the viewport origin.
  positionMenu();
  open.value = true;
  await nextTick();
  positionMenu();
  menu.value?.querySelector<HTMLElement>(`[data-option-index="${initial}"]`)?.focus();
}

function closeMenu(restoreFocus = false) {
  if (!open.value) return;
  open.value = false;
  if (restoreFocus) void nextTick(() => trigger.value?.focus());
}

function toggleMenu() {
  if (open.value) closeMenu();
  else void openMenu();
}

function selectIndex(index: number) {
  const option = props.options[index];
  if (!option || option.disabled) return;
  activeIndex.value = index;
  if (option.value !== props.modelValue) {
    emit("update:modelValue", option.value);
    emit("change", option.value);
  }
  closeMenu(true);
}

function handleNativeChange(event: Event) {
  const rawValue = (event.target as HTMLSelectElement).value;
  const index = props.options.findIndex((option) => String(option.value) === rawValue);
  if (index >= 0) selectIndex(index);
}

function moveActive(direction: 1 | -1) {
  const base = activeIndex.value >= 0 ? activeIndex.value : selectedIndex.value;
  const next = enabledIndex(base < 0 ? (direction === 1 ? -1 : 0) : base, direction);
  if (next < 0) return;
  activeIndex.value = next;
  void nextTick(() => menu.value?.querySelector<HTMLElement>(`[data-option-index="${next}"]`)?.focus());
}

function handleTriggerKeydown(event: KeyboardEvent) {
  if (props.disabled) return;
  if (event.key === "ArrowDown") {
    event.preventDefault();
    void openMenu(selectedIndex.value >= 0 ? selectedIndex.value : firstEnabledIndex());
  } else if (event.key === "ArrowUp") {
    event.preventDefault();
    void openMenu(selectedIndex.value >= 0 ? selectedIndex.value : lastEnabledIndex());
  } else if (event.key === "Home") {
    event.preventDefault();
    void openMenu(firstEnabledIndex());
  } else if (event.key === "End") {
    event.preventDefault();
    void openMenu(lastEnabledIndex());
  }
}

function handleOptionKeydown(event: KeyboardEvent) {
  if (event.key === "ArrowDown") {
    event.preventDefault();
    moveActive(1);
  } else if (event.key === "ArrowUp") {
    event.preventDefault();
    moveActive(-1);
  } else if (event.key === "Home") {
    event.preventDefault();
    const index = firstEnabledIndex();
    activeIndex.value = index;
    void nextTick(() => menu.value?.querySelector<HTMLElement>(`[data-option-index="${index}"]`)?.focus());
  } else if (event.key === "End") {
    event.preventDefault();
    const index = lastEnabledIndex();
    activeIndex.value = index;
    void nextTick(() => menu.value?.querySelector<HTMLElement>(`[data-option-index="${index}"]`)?.focus());
  } else if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    selectIndex(activeIndex.value);
  } else if (event.key === "Escape") {
    event.preventDefault();
    closeMenu(true);
  } else if (event.key === "Tab") {
    closeMenu();
  }
}

function handlePointerDown(event: PointerEvent) {
  const target = event.target as Node;
  if (selectRoot.value?.contains(target) || menu.value?.contains(target)) return;
  closeMenu();
}

watch(() => props.modelValue, () => {
  if (!open.value) activeIndex.value = selectedIndex.value;
});

onMounted(() => {
  window.addEventListener("resize", positionMenu);
  window.addEventListener("scroll", positionMenu, true);
  document.addEventListener("pointerdown", handlePointerDown);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", positionMenu);
  window.removeEventListener("scroll", positionMenu, true);
  document.removeEventListener("pointerdown", handlePointerDown);
});
</script>

<template>
  <span ref="selectRoot" class="runtime-select" :class="className" :data-open="String(open)">
    <select
      class="runtime-select__native"
      :data-testid="testId || undefined"
      :data-field="field || undefined"
      :value="String(modelValue)"
      :disabled="disabled"
      tabindex="-1"
      aria-hidden="true"
      @change="handleNativeChange"
    >
      <option v-for="option in options" :key="String(option.value)" :value="String(option.value)" :disabled="option.disabled">{{ option.label }}</option>
    </select>
    <button
      ref="trigger"
      class="runtime-select__trigger"
      type="button"
      :data-runtime-testid="testId || undefined"
      :aria-label="displayLabel ? `${ariaLabel}: ${displayLabel}` : ariaLabel"
      :aria-controls="open ? menuId : undefined"
      :aria-expanded="open"
      aria-haspopup="listbox"
      :disabled="disabled"
      @click="toggleMenu"
      @keydown="handleTriggerKeydown"
    >
      <span class="runtime-select__value-viewport" :data-placeholder="String(!selectedOption)">
        <span class="runtime-select__value-placeholder">{{ placeholder }}</span>
        <span
          class="runtime-select__value-track"
          :style="{ '--runtime-select-offset': `${Math.max(0, selectedIndex) * -1.35}rem` }"
          aria-hidden="true"
        >
          <span v-for="(option, index) in options" :key="`${String(option.value)}-${index}`" class="runtime-select__value-option">
            {{ option.label }}
          </span>
        </span>
      </span>
      <span class="runtime-select__chevron" aria-hidden="true"></span>
    </button>
  </span>

  <Teleport to="body">
    <Transition name="runtime-select-menu">
      <div
        v-if="open"
        :id="menuId"
        ref="menu"
        class="runtime-select__menu"
        :style="menuStyle"
        role="listbox"
        :aria-label="ariaLabel"
        @keydown="handleOptionKeydown"
      >
        <button
          v-for="(option, index) in options"
          :key="`${String(option.value)}-${index}`"
          class="runtime-select__option"
          type="button"
          role="option"
          :data-option-index="index"
          :aria-selected="option.value === modelValue"
          :disabled="option.disabled"
          :tabindex="index === activeIndex ? 0 : -1"
          @click="selectIndex(index)"
          @focus="activeIndex = index"
        >
          <span>{{ option.label }}</span>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.runtime-select {
  --runtime-select-height: 3.5rem;
  --runtime-select-min-width: 11rem;
  position: relative;
  display: block;
  min-width: var(--runtime-select-min-width);
}
.runtime-select__native { position: absolute !important; width: 1px !important; min-width: 1px !important; height: 1px !important; min-height: 1px !important; margin: -1px !important; padding: 0 !important; border: 0 !important; overflow: hidden !important; clip: rect(0 0 0 0); clip-path: inset(50%); white-space: nowrap; pointer-events: none; }
.runtime-select__trigger {
  display: flex;
  width: 100%;
  min-height: var(--runtime-select-height);
  align-items: center;
  justify-content: space-between;
  gap: .9rem;
  padding: .5rem .78rem .5rem 1rem;
  border: 1px solid color-mix(in srgb, var(--line-strong) 68%, var(--line));
  border-radius: 12px;
  background: var(--surface);
  color: var(--text);
  box-shadow: 0 1px 0 color-mix(in srgb, var(--text) 10%, transparent) inset;
  cursor: pointer;
  text-align: left;
  transition: border-color 200ms ease-out, background-color 200ms ease-out, box-shadow 200ms ease-out;
}
.runtime-select__trigger:hover:not(:disabled),
.runtime-select[data-open="true"] .runtime-select__trigger {
  border-color: var(--text-muted);
  background: var(--surface);
}
.runtime-select__trigger:focus-visible {
  border-color: var(--text);
  box-shadow: var(--focus-ring), 0 1px 0 color-mix(in srgb, var(--text) 10%, transparent) inset;
}
.runtime-select__trigger:disabled { cursor: not-allowed; opacity: .54; }
.runtime-select__value-viewport {
  position: relative;
  display: block;
  min-width: 0;
  min-height: 1.35rem;
  flex: 1 1 auto;
  overflow: hidden;
  font-size: .9rem;
  line-height: 1.35rem;
  white-space: nowrap;
}
.runtime-select__value-placeholder,
.runtime-select__value-track {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
}
.runtime-select__value-placeholder { overflow: hidden; }
.runtime-select__value-placeholder {
  color: var(--text-muted);
  opacity: 0;
  text-overflow: ellipsis;
  transition: opacity 200ms ease-out;
}
.runtime-select__value-viewport[data-placeholder="true"] .runtime-select__value-placeholder { opacity: 1; }
.runtime-select__value-track {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  height: 100%;
  transform: translateY(var(--runtime-select-offset, 0));
  transition: transform 300ms cubic-bezier(.16, 1, .3, 1), opacity 200ms ease-out;
}
.runtime-select__value-viewport[data-placeholder="true"] .runtime-select__value-track { opacity: 0; }
.runtime-select__value-option {
  display: flex;
  min-height: 1.35rem;
  flex: 0 0 1.35rem;
  align-items: center;
  overflow: hidden;
  text-overflow: ellipsis;
}
.runtime-select__chevron {
  width: .54rem;
  height: .54rem;
  flex: 0 0 auto;
  margin: -.18rem .15rem .18rem 0;
  border-right: 1.5px solid currentColor;
  border-bottom: 1.5px solid currentColor;
  opacity: .78;
  transform: rotate(45deg);
  transition: transform 200ms ease-out;
}
.runtime-select[data-open="true"] .runtime-select__chevron { transform: rotate(225deg) translate(-1px, -1px); }
.runtime-select__menu {
  position: fixed;
  z-index: 120;
  display: grid;
  overflow: auto;
  padding: .28rem;
  border: 1px solid color-mix(in srgb, var(--line-strong) 62%, var(--line));
  border-radius: 12px;
  background: var(--surface);
  box-shadow: 0 18px 38px color-mix(in srgb, var(--text) 18%, transparent), 0 1px 0 color-mix(in srgb, var(--text) 10%, transparent) inset;
}
.runtime-select__option {
  display: flex;
  min-height: 3rem;
  align-items: center;
  justify-content: flex-start;
  gap: .7rem;
  padding: .5rem 1.25rem;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font-size: .88rem;
  text-align: left;
  transition: background-color 160ms ease-out, color 160ms ease-out;
}
.runtime-select__option:hover:not(:disabled),
.runtime-select__option:focus-visible { background: var(--surface-subtle); outline: 0; }
.runtime-select__option[aria-selected="true"] { background: var(--surface-subtle); color: var(--text); font-weight: 650; }
.runtime-select__option:disabled { cursor: not-allowed; opacity: .48; }
.runtime-select-menu-enter-active,
.runtime-select-menu-leave-active { transition: opacity 200ms ease-out, transform 200ms ease-out; }
.runtime-select-menu-enter-from,
.runtime-select-menu-leave-to { opacity: 0; transform: translateY(-10px); }
@media (prefers-reduced-motion: reduce) {
  .runtime-select__trigger, .runtime-select__chevron, .runtime-select__option,
  .runtime-select__value-placeholder, .runtime-select__value-track,
  .runtime-select-menu-enter-active, .runtime-select-menu-leave-active { transition: none; }
}
@media (prefers-reduced-transparency: reduce) {
  .runtime-select__trigger, .runtime-select__menu { background: var(--surface); }
}
</style>
