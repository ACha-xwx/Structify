<script setup lang="ts">
import { computed, ref, useId } from "vue";
import { useLocale } from "../i18n/locale";
import { useTheme } from "./theme";

const props = withDefaults(defineProps<{
  /** Keep hidden/contextual copies out of generic theme-toggle queries. */
  registerControl?: boolean;
}>(), {
  registerControl: true,
});

const { isDark, toggleTheme } = useTheme();
const { locale, toggleLocale } = useLocale();

const label = computed(() => {
  if (locale.value === "en-US") return isDark.value ? "Switch to light theme" : "Switch to dark theme";
  return isDark.value ? "切换到浅色主题" : "切换到深色主题";
});
const labelId = `theme-toggle-label-${useId()}`;
const grainFilterId = `theme-toggle-grain-${useId()}`;
const localeControlLabel = computed(() => locale.value === "zh-CN" ? "切换语言，当前中文" : "Switch language, currently English");
const pulseId = ref(0);

function updateTheme() {
  pulseId.value += 1;
  toggleTheme();
}

function updateLocale() {
  toggleLocale();
}
</script>

<template>
  <div class="theme-toggle" :title="label">
    <span :id="labelId" class="theme-toggle__sr-only">{{ label }}</span>
    <svg class="theme-toggle__filters" aria-hidden="true">
      <filter :id="grainFilterId">
        <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="3" />
        <feColorMatrix type="saturate" values="0" />
        <feComponentTransfer><feFuncA type="linear" slope="0.12" /></feComponentTransfer>
        <feBlend in="SourceGraphic" mode="overlay" />
      </filter>
    </svg>
    <button
      class="theme-toggle__control"
      :data-theme-toggle="props.registerControl ? '' : undefined"
      :class="{ 'is-dark': isDark }"
      type="button"
      role="switch"
      :aria-checked="isDark"
      :aria-labelledby="labelId"
      @click.stop="updateTheme"
    >
      <span class="theme-toggle__groove" aria-hidden="true"></span>
      <span class="theme-toggle__gloss" aria-hidden="true"></span>
      <span class="theme-toggle__texture" :style="{ filter: `url(#${grainFilterId})` }" aria-hidden="true"></span>
      <svg class="theme-toggle__ambient-icon theme-toggle__ambient-icon--sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="3.5" />
        <path d="M12 2.5v2M12 19.5v2M21.5 12h-2M4.5 12h-2M18.72 5.28l-1.42 1.42M6.7 17.3l-1.42 1.42M18.72 18.72l-1.42-1.42M6.7 6.7 5.28 5.28" />
      </svg>
      <svg class="theme-toggle__ambient-icon theme-toggle__ambient-icon--moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M20.2 15.2A8.6 8.6 0 0 1 8.8 3.8 8.65 8.65 0 1 0 20.2 15.2Z" />
      </svg>
      <span class="theme-toggle__thumb" aria-hidden="true">
        <span v-if="pulseId" :key="pulseId" class="theme-toggle__particles">
          <i v-for="index in 3" :key="index" class="theme-toggle__particle" :style="{ '--particle-delay': `${(index - 1) * 45}ms` }"></i>
        </span>
        <span class="theme-toggle__thumb-gloss"></span>
        <svg v-if="isDark" class="theme-toggle__thumb-icon theme-toggle__thumb-icon--moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
          <path d="M20.2 15.2A8.6 8.6 0 0 1 8.8 3.8 8.65 8.65 0 1 0 20.2 15.2Z" />
        </svg>
        <svg v-else class="theme-toggle__thumb-icon theme-toggle__thumb-icon--sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
          <circle cx="12" cy="12" r="3.5" />
          <path d="M12 2.5v2M12 19.5v2M21.5 12h-2M4.5 12h-2M18.72 5.28l-1.42 1.42M6.7 17.3l-1.42 1.42M18.72 18.72l-1.42-1.42M6.7 6.7 5.28 5.28" />
        </svg>
      </span>
    </button>
    <button
      class="theme-toggle__locale-toggle"
      type="button"
      role="switch"
      :aria-checked="locale === 'en-US'"
      :aria-label="localeControlLabel"
      :title="localeControlLabel"
      @click.stop="updateLocale"
    >
      <span class="theme-toggle__locale-track" aria-hidden="true"></span>
      <span class="theme-toggle__locale-thumb" aria-hidden="true">{{ locale === 'en-US' ? 'EN' : '中' }}</span>
    </button>
  </div>
</template>

<style scoped>
.theme-toggle {
  position: relative;
  display: inline-flex;
  width: 136px;
  height: 40px;
  flex: 0 0 136px;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
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

.theme-toggle__filters { position: absolute; width: 0; height: 0; overflow: hidden; }

.theme-toggle__control {
  position: relative;
  display: flex;
  width: 64px;
  height: 40px;
  flex: 0 0 64px;
  align-items: center;
  padding: 4px;
  overflow: hidden;
  border: 1px solid rgba(190, 190, 186, 0.88);
  border-radius: 999px;
  background: radial-gradient(ellipse at top left, #ffffff 0%, #f1f1ef 42%, #cbcbc7 100%);
  box-shadow: inset 2px 2px 5px rgba(105, 105, 99, 0.26), inset -2px -2px 5px rgba(255, 255, 255, 0.96), inset 4px 4px 8px rgba(105, 105, 99, 0.13), inset -4px -4px 8px rgba(255, 255, 255, 0.76), 0 1px 1px rgba(255, 255, 255, 0.96), 0 3px 8px rgba(0, 0, 0, 0.1), 0 8px 16px rgba(0, 0, 0, 0.06);
  color: #5d6063;
  cursor: pointer;
  isolation: isolate;
  touch-action: manipulation;
  user-select: none;
  transition: background 180ms ease, border-color 180ms ease, box-shadow 180ms ease, transform 120ms ease;
}

.theme-toggle__control.is-dark {
  border-color: rgba(83, 83, 83, 0.92);
  background: radial-gradient(ellipse at top left, #3b3b3b 0%, #222222 42%, #101010 100%);
  box-shadow: inset 2px 2px 5px rgba(0, 0, 0, 0.88), inset -2px -2px 5px rgba(110, 110, 110, 0.28), inset 4px 4px 8px rgba(0, 0, 0, 0.58), inset -4px -4px 8px rgba(115, 115, 115, 0.16), 0 1px 1px rgba(255, 255, 255, 0.05), 0 3px 8px rgba(0, 0, 0, 0.36), 0 8px 16px rgba(0, 0, 0, 0.3);
  color: #d4d8dc;
}

.theme-toggle__control:active { transform: scale(0.985); }
.theme-toggle__control:focus-visible { outline: 2px solid var(--text); outline-offset: 3px; }

.theme-toggle__groove,
.theme-toggle__gloss,
.theme-toggle__texture {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
}

.theme-toggle__groove {
  inset: 2px;
  box-shadow: inset 0 1px 4px rgba(83, 83, 78, 0.34), inset 0 -1px 2px rgba(255, 255, 255, 0.84);
}
.theme-toggle__control.is-dark .theme-toggle__groove { box-shadow: inset 0 1px 4px rgba(0, 0, 0, 0.84), inset 0 -1px 2px rgba(127, 127, 127, 0.24); }

.theme-toggle__gloss { background: radial-gradient(ellipse at top, rgba(255, 255, 255, 0.72) 0%, transparent 54%), linear-gradient(to bottom, rgba(255, 255, 255, 0.34), transparent 34%, transparent 70%, rgba(70, 70, 67, 0.12)); mix-blend-mode: overlay; }
.theme-toggle__control.is-dark .theme-toggle__gloss { background: radial-gradient(ellipse at top, rgba(215, 215, 215, 0.12) 0%, transparent 54%), linear-gradient(to bottom, rgba(215, 215, 215, 0.12), transparent 34%, transparent 70%, rgba(0, 0, 0, 0.28)); }
.theme-toggle__texture { z-index: 1; opacity: 0.24; }

.theme-toggle__ambient-icon {
  position: absolute;
  z-index: 2;
  top: 50%;
  width: 15px;
  height: 15px;
  transform: translateY(-50%);
  transition: color 180ms ease, opacity 180ms ease;
}
.theme-toggle__ambient-icon--sun { left: 7px; color: #666666; }
.theme-toggle__ambient-icon--moon { right: 7px; color: #62666a; }
.theme-toggle__control.is-dark .theme-toggle__ambient-icon--sun { color: #cfcfcf; opacity: 0.7; }
.theme-toggle__control.is-dark .theme-toggle__ambient-icon--moon { color: #e1e1e1; opacity: 0.88; }

.theme-toggle__thumb {
  position: relative;
  z-index: 3;
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 50%;
  background: linear-gradient(145deg, #ffffff 0%, #fefefe 52%, #f3f3f1 100%);
  box-shadow: inset 1px 1px 2px rgba(196, 196, 190, 0.3), inset -1px -1px 2px rgba(255, 255, 255, 1), inset 0 1px 1px rgba(255, 255, 255, 1), 0 1px 2px rgba(255, 255, 255, 0.9), 0 3px 7px rgba(0, 0, 0, 0.16);
  color: #606060;
  transform: translateX(0);
  transition: transform 360ms cubic-bezier(0.22, 1.28, 0.36, 1), background 180ms ease, border-color 180ms ease, box-shadow 180ms ease, color 180ms ease;
}

.theme-toggle__control.is-dark .theme-toggle__thumb {
  border-color: rgba(163, 163, 163, 0.34);
  background: linear-gradient(145deg, #707070 0%, #4b4b4b 52%, #303030 100%);
  box-shadow: inset 1px 1px 2px rgba(168, 168, 168, 0.28), inset -1px -1px 2px rgba(0, 0, 0, 0.78), inset 0 1px 1px rgba(255, 255, 255, 0.13), 0 3px 8px rgba(0, 0, 0, 0.48);
  color: #e1e1e1;
  transform: translateX(26px);
}

.theme-toggle__thumb-gloss { position: absolute; inset: 0; border-radius: inherit; background: linear-gradient(to bottom, rgba(255, 255, 255, 0.4), transparent 43%, rgba(0, 0, 0, 0.1)); mix-blend-mode: overlay; pointer-events: none; }
.theme-toggle__thumb-icon { position: relative; z-index: 2; width: 14px; height: 14px; }

.theme-toggle__particles { position: absolute; inset: 0; z-index: 1; pointer-events: none; }
.theme-toggle__particle {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 5px;
  height: 5px;
  margin: -2.5px;
  border-radius: 50%;
  background: radial-gradient(circle, currentColor 0%, transparent 72%);
  opacity: 0;
  transform: scale(0.1);
  animation: theme-toggle-particle 440ms var(--particle-delay) ease-out both;
}

.theme-toggle__locale-toggle {
  position: relative;
  display: inline-flex;
  width: 64px;
  height: 40px;
  flex: 0 0 64px;
  align-items: center;
  padding: 4px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, currentColor 30%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface, #f7f7f5) 76%, #898989 24%);
  color: var(--text, #242424);
  cursor: pointer;
  transition: background 180ms ease, border-color 180ms ease, transform 120ms ease;
}
.theme-toggle__locale-toggle:active { transform: scale(.985); }
.theme-toggle__locale-toggle:focus-visible { outline: 2px solid var(--text); outline-offset: 3px; }
.theme-toggle__locale-track { position: absolute; inset: 3px; border-radius: inherit; background: linear-gradient(to bottom, rgba(255,255,255,.42), rgba(0,0,0,.08)); pointer-events: none; }
.theme-toggle__locale-thumb {
  position: relative;
  z-index: 1;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid color-mix(in srgb, currentColor 24%, transparent);
  border-radius: 50%;
  background: var(--surface, #ffffff);
  box-shadow: 0 2px 6px rgba(0,0,0,.16);
  font-family: var(--font-mono, ui-monospace, monospace);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
  transform: translateX(0);
  transition: transform 260ms cubic-bezier(.22,1.28,.36,1), background 180ms ease;
}
.theme-toggle__locale-toggle[aria-checked="true"] { background: color-mix(in srgb, var(--text, #242424) 22%, var(--surface, #f7f7f5)); }
.theme-toggle__locale-toggle[aria-checked="true"] .theme-toggle__locale-thumb { transform: translateX(26px); }

@keyframes theme-toggle-particle {
  0% { opacity: 0; transform: scale(0.1); }
  28% { opacity: 0.72; }
  100% { opacity: 0; transform: scale(6); }
}

@media (prefers-reduced-motion: reduce) {
  .theme-toggle__control,
  .theme-toggle__thumb,
  .theme-toggle__ambient-icon { transition: none; }
  .theme-toggle__particle { animation: none; }
}

@media (max-width: 720px) {
  /* Keep both independent switches reachable on narrow screens. */
  .theme-toggle {
    width: 136px;
    flex-basis: 136px;
  }

  .theme-toggle__locale-toggle { position: relative; top: auto; right: auto; }
}
</style>
