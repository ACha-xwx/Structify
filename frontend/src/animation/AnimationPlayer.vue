<script setup lang="ts">
import { computed } from "vue";
import AnimationStage from "./AnimationStage.vue";
import { initialFrame } from "./frame";
import { PLAYBACK_SPEEDS, useAnimationPlayback } from "./useAnimationPlayback";
import { useI18n } from "../shared/i18n/locale";
import type { AnimationDefinition } from "../shared/types/animation";

/**
 * The animation transport: one trace, one set of controls, one renderer.
 *
 * Both the animation lab and the in-classroom demo mount this component, which is why a page chosen in
 * class and the same operation opened in the lab behave identically - the player owns the frames, not
 * the page around it.
 */
const props = withDefaults(defineProps<{
  definition: AnimationDefinition | null;
  /** The server's executed trace; its first step carries the rich initial frame. */
  trace?: Record<string, unknown> | null;
  /** Shown instead of the controls when there is no trace yet. */
  placeholder?: string;
  /** Hides the headline when the caller already renders the title (the classroom does). */
  compact?: boolean;
}>(), { placeholder: "", compact: false, trace: null });

const { t } = useI18n();
const steps = computed(() => props.definition?.steps ?? []);
const stepCount = computed(() => steps.value.length);
const playback = useAnimationPlayback(stepCount);

const currentStep = computed(() => (playback.index.value >= 0 ? steps.value[playback.index.value] ?? null : null));
/** Before the first step the learner sees the input, not a blank canvas. */
const initialState = computed(() => initialFrame(props.definition, props.trace));
/** The one big line under the title: the step's concrete outcome (note) beats its category (label),
 * and on the final step the note usually IS the result ("[13, 38, 49]"). */
const headline = computed(() => {
  const definition = props.definition;
  if (!definition) return "";
  // The header already carries the title in non-compact use, so the initial state reads "初始状态"
  // instead of repeating it; compact callers have no header and need the title here.
  if (playback.index.value < 0) return props.compact ? definition.title : t("player.initial");
  return currentStep.value?.note || currentStep.value?.label || definition.title;
});

function onKeydown(event: KeyboardEvent) {
  if (event.key === "ArrowRight") { event.preventDefault(); playback.next(); }
  else if (event.key === "ArrowLeft") { event.preventDefault(); playback.previous(); }
  else if (event.key === " ") { event.preventDefault(); playback.toggle(); }
  else if (event.key === "Home") { event.preventDefault(); playback.reset(); }
}
</script>

<template>
  <section class="player" :aria-label="definition ? t('player.labelOf', { title: definition.title }) : t('player.label')">
    <header v-if="!compact && definition" class="player__head">
      <h3 class="player__title">{{ definition.title }}</h3>
    </header>

    <template v-if="definition">
      <p class="player__headline" aria-live="polite">{{ headline }}</p>

      <div class="player__viewport" tabindex="0" role="group" :aria-label="t('player.canvas')" @keydown="onKeydown">
        <!-- The initial frame is only for the "before the first step" position: once a step is active its
             own frame must win, or playback would repaint the input on every tick. -->
        <AnimationStage
          :step="currentStep"
          :state="playback.index.value < 0 ? initialState : null"
          :empty-label="t('player.empty')"
        />
      </div>

      <div class="player__controls">
        <button class="player__button" type="button" :disabled="playback.atStart.value" @click="playback.previous">{{ t("player.previous") }}</button>
        <button class="player__button player__button--primary" type="button" @click="playback.toggle">
          {{ playback.playing.value ? t("player.pause") : t("player.play") }}
        </button>
        <button class="player__button" type="button" :disabled="playback.atEnd.value" @click="playback.next">{{ t("player.next") }}</button>
        <button class="player__button" type="button" @click="playback.reset">{{ t("player.reset") }}</button>
        <span class="player__position">{{ playback.positionLabel.value }}</span>
        <label class="player__speed">
          <span>{{ t("player.speed") }}</span>
          <select v-model.number="playback.speed.value" :aria-label="t('player.speedLabel')">
            <option v-for="option in PLAYBACK_SPEEDS" :key="option" :value="option">{{ option }}×</option>
          </select>
        </label>
      </div>

      <input
        class="player__scrubber"
        type="range"
        min="-1"
        :max="Math.max(0, stepCount - 1)"
        :value="playback.index.value"
        :aria-label="t('player.scrubber')"
        @input="playback.goTo(Number(($event.target as HTMLInputElement).value))"
      >
    </template>

    <p v-else class="player__placeholder">{{ placeholder || t("player.placeholder") }}</p>
  </section>
</template>

<style scoped>
.player {
  display: grid;
  gap: 14px;
  align-content: start;
  min-width: 0;
  color: var(--text);
}

.player__head { display: grid; gap: 4px; }
.player__title { margin: 0; font-size: 21px; font-weight: 680; letter-spacing: -.01em; }

.player__headline {
  margin: 0;
  min-height: 28px;
  font-size: 19px;
  font-weight: 600;
  text-wrap: pretty;
}

.player__viewport {
  padding: 16px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 22px;
  background: color-mix(in srgb, var(--surface) 82%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 8px 18px color-mix(in srgb, var(--text) 9%, transparent);
  outline: none;
  overflow-x: auto;
}

.player__viewport:focus-visible { box-shadow: var(--focus-ring), inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent); }

.player__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.player__button {
  min-height: 40px;
  padding: 8px 18px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 15px;
  transition: background-color .16s ease, border-color .16s ease, color .16s ease;
}

.player__button:hover:not(:disabled) { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.player__button:disabled { cursor: default; opacity: .38; }
.player__button--primary { border-color: transparent; background: var(--text); color: var(--surface); font-weight: 620; }
.player__button--primary:hover:not(:disabled) { background: var(--accent-strong); border-color: transparent; }

.player__position {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.player__speed { display: inline-flex; align-items: center; gap: 6px; color: var(--text-muted); font-size: 14px; }
.player__speed select {
  min-height: 36px;
  padding: 4px 12px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  font: inherit;
}

.player__scrubber { width: 100%; accent-color: var(--accent); }

.player__placeholder { margin: 0; color: var(--text-muted); font-size: 15px; }

@media (prefers-reduced-motion: reduce) {
  .player__button { transition: none; }
}
</style>
