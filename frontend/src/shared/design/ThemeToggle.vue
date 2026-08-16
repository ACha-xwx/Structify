<script setup lang="ts">
import { computed, useId } from "vue";
import { useTheme } from "./theme";

const { isDark, setTheme } = useTheme();

const label = computed(() => isDark.value ? "切换到浅色主题" : "切换到深色主题");
const labelId = `theme-toggle-label-${useId()}`;

function updateTheme(event: Event) {
  setTheme((event.target as HTMLInputElement).checked ? "dark" : "light");
}
</script>

<template>
  <div class="theme-toggle" :title="label">
    <span :id="labelId" class="theme-toggle__sr-only">{{ label }}</span>
    <input
      class="theme-toggle__input"
      type="checkbox"
      role="switch"
      :checked="isDark"
      :aria-labelledby="labelId"
      @change="updateTheme"
    />
    <span class="theme-toggle__track" aria-hidden="true"><span class="theme-toggle__thumb"></span></span>
  </div>
</template>

<style scoped>
.theme-toggle {
  position: relative;
  display: inline-flex;
  width: 44px;
  height: 28px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.theme-toggle__sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

.theme-toggle__input {
  position: absolute;
  inset: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  margin: 0;
  cursor: pointer;
  opacity: 0;
}

.theme-toggle__track {
  display: flex;
  width: 42px;
  height: 24px;
  align-items: center;
  padding: 2px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--theme-toggle-track);
  box-shadow: inset 0 1px 1px rgba(0, 0, 0, 0.12), inset 0 -1px 0 rgba(255, 255, 255, 0.16);
  transition: background-color 150ms ease, border-color 150ms ease;
}

.theme-toggle__thumb {
  display: block;
  width: 18px;
  height: 18px;
  border: 1px solid var(--theme-toggle-thumb-line);
  border-radius: 50%;
  background: var(--theme-toggle-thumb);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.72);
  transform: translateX(0);
  transition: transform 180ms cubic-bezier(0.22, 1, 0.36, 1), background-color 150ms ease, border-color 150ms ease;
}

.theme-toggle__input:checked + .theme-toggle__track .theme-toggle__thumb { transform: translateX(16px); }
.theme-toggle__input:focus-visible + .theme-toggle__track { box-shadow: var(--focus-ring), inset 0 1px 1px rgba(0, 0, 0, 0.12); }

@media (prefers-reduced-motion: reduce) {
  .theme-toggle__track,
  .theme-toggle__thumb { transition: none; }
}
</style>
