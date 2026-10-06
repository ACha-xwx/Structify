<script setup lang="ts">
import { computed } from "vue";
import AnimationStage from "./AnimationStage.vue";
import LiquidMetalButton from "../admin/components/LiquidMetalButton.vue";
import RuntimeSelect from "../shared/components/RuntimeSelect.vue";
import leftArrowIcon from "../assets/classroom/left-arrow.svg";
import rightArrowIcon from "../assets/classroom/right-arrow.svg";
import playIcon from "../assets/animation/play.svg";
import pauseIcon from "../assets/animation/pause.svg";
import loopIcon from "../assets/animation/loop.svg";
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
  controlsVariant?: "default" | "silver";
}>(), { placeholder: "", compact: false, trace: null, controlsVariant: "default" });

const { t } = useI18n();
const steps = computed(() => props.definition?.steps ?? []);
const stepCount = computed(() => steps.value.length);
/** 这条动画的身份：标题 + 步数 + 首步标题。步数相同的两条动画也必须各自从起点开始播。 */
const playbackIdentity = computed(() =>
  `${props.definition?.title ?? ""}|${stepCount.value}|${steps.value[0]?.label ?? ""}`,
);
const playback = useAnimationPlayback(stepCount, { identity: playbackIdentity });

const currentStep = computed(() => (playback.index.value >= 0 ? steps.value[playback.index.value] ?? null : null));
/**
 * True when the engine produced no process frames at all, so the whole animation is one picture.
 *
 * 初始化、取平方取中、伪随机这类操作本来就只有一个状态，引擎给不出中间帧。此时进度条上只有一格、
 * 「下一步」按了也不动，学生只会以为演示坏了——把这件事说出来，比让他自己猜好。
 */
const singleFrame = computed(() => stepCount.value === 1);
/**
 * 这一帧的标题就是**一行代码**（引擎把赋值语句放在 step 的 title/note 里，phase 标成 `assign`）。
 * 代码用等宽字体显示：一眼分得出"这是一行程序"还是"这是一句解释"。
 */
const headlineIsCode = computed(() => currentStep.value?.phase === "assign");
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
      <p class="player__headline" :class="{ 'player__headline--code': headlineIsCode }" aria-live="polite">{{ headline }}</p>
      <!-- 引擎给不出过程帧的操作（初始化、哈希函数…）整条动画只有一帧。不写这一句，
           学生点「下一步」画面不动，只会以为坏了。 -->
      <p v-if="singleFrame" class="player__single">{{ t("player.singleFrame") }}</p>

      <div class="player__viewport" tabindex="0" role="group" :aria-label="t('player.canvas')" @keydown="onKeydown">
        <!-- The initial frame is only for the "before the first step" position: once a step is active its
             own frame must win, or playback would repaint the input on every tick. A step that carries no
             frame of its own falls back to the input rather than painting an empty canvas. -->
        <AnimationStage
          :step="currentStep"
          :state="playback.index.value < 0 || !currentStep?.dsvpState ? initialState : null"
          :empty-label="t('player.empty')"
        />
      </div>

      <div class="player__controls">
        <template v-if="controlsVariant === 'silver'">
          <LiquidMetalButton class="player__silver-control" view-mode="icon" :disabled="playback.atStart.value" :aria-label="t('player.previous')" :title="t('player.previous')" @click="playback.previous">
            <template #icon><img class="player__icon" :src="leftArrowIcon" alt="" aria-hidden="true"></template>
          </LiquidMetalButton>
          <LiquidMetalButton class="player__silver-control" view-mode="icon" :aria-label="playback.playing.value ? t('player.pause') : t('player.play')" :title="playback.playing.value ? t('player.pause') : t('player.play')" @click="playback.toggle">
            <template #icon><img class="player__icon" :src="playback.playing.value ? pauseIcon : playIcon" alt="" aria-hidden="true"></template>
          </LiquidMetalButton>
          <LiquidMetalButton class="player__silver-control" view-mode="icon" :disabled="playback.atEnd.value" :aria-label="t('player.next')" :title="t('player.next')" @click="playback.next">
            <template #icon><img class="player__icon" :src="rightArrowIcon" alt="" aria-hidden="true"></template>
          </LiquidMetalButton>
          <LiquidMetalButton class="player__silver-control" view-mode="icon" :aria-label="t('player.reset')" :title="t('player.reset')" @click="playback.reset">
            <template #icon><img class="player__icon" :src="loopIcon" alt="" aria-hidden="true"></template>
          </LiquidMetalButton>
        </template>
        <template v-else>
        <button class="player__button" type="button" :disabled="playback.atStart.value" @click="playback.previous">{{ t("player.previous") }}</button>
        <button class="player__button player__button--primary" type="button" @click="playback.toggle">
          {{ playback.playing.value ? t("player.pause") : t("player.play") }}
        </button>
        <button class="player__button" type="button" :disabled="playback.atEnd.value" @click="playback.next">{{ t("player.next") }}</button>
        <button class="player__button" type="button" @click="playback.reset">{{ t("player.reset") }}</button>
        </template>
        <span class="player__position">{{ playback.positionLabel.value }}</span>
        <label class="player__speed">
          <span>{{ t("player.speed") }}</span>
          <RuntimeSelect v-if="controlsVariant === 'silver'" v-model="playback.speed.value" class-name="player__speed-select" variant="reference" :options="PLAYBACK_SPEEDS.map(option => ({ value: option, label: `${option}×` }))" :ariaLabel="t('player.speedLabel')" />
          <select v-else v-model.number="playback.speed.value" :aria-label="t('player.speedLabel')">
            <option v-for="option in PLAYBACK_SPEEDS" :key="option" :value="option">{{ option }}×</option>
          </select>
        </label>
      </div>

      <!-- An animation is discrete, so its progress control is too: one tick per step, and a click jumps
           straight to that step (the classroom uses this to park on "the merge step" and talk). -->
      <div v-if="stepCount" class="player__rail" role="group" :aria-label="t('player.rail')">
        <button
          v-for="(step, index) in steps"
          :key="`tick-${index}-${step.op}`"
          class="player__tick"
          :class="{ 'player__tick--done': index <= playback.index.value, 'player__tick--current': index === playback.index.value }"
          type="button"
          :title="step.label"
          :aria-label="step.label"
          @click="playback.goTo(index)"
        />
      </div>
    </template>

    <p v-else class="player__placeholder">{{ placeholder || t("player.placeholder") }}</p>
  </section>
</template>

<style scoped>
/* Player chrome is page-level UI, so it speaks the site's one register: 19px and up. Only the canvas
   inside the viewport has its own (still never-tiny) scale. */
.player {
  display: grid;
  gap: 14px;
  align-content: start;
  min-width: 0;
  color: var(--text);
}

.player__head { display: grid; gap: 4px; }
.player__title { margin: 0; font-size: 22px; font-weight: 680; letter-spacing: -.01em; }

.player__headline {
  margin: 0;
  min-height: 28px;
  font-size: 19px;
  font-weight: 600;
  text-wrap: pretty;
}

/* 只有一帧的演示：说明白"这一步没有过程"，而不是留一个按了没反应的「下一步」。 */
.player__single {
  margin: 6px 0 0;
  font-size: 19px;
  color: var(--text-muted, #6f6d69);
  text-wrap: pretty;
}

/* 标题本身是一行代码时用等宽字体：学生一眼分得出"这是程序"还是"这是解释"。
   代码级动画的每一帧标题就是那行赋值（`p->next = B->next`），解释性文字越少越好。 */
.player__headline--code {
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  font-weight: 500;
  letter-spacing: 0;
  font-variant-ligatures: none;
}

/* The canvas the panels stand on: lighter than the cards inside it, so they read as raised. */
.player__viewport {
  padding: 16px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 22px;
  background: color-mix(in srgb, var(--surface) 46%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 8px 18px color-mix(in srgb, var(--text) 9%, transparent);
  outline: none;
  overflow-x: auto;
}

.player__viewport:focus-visible { box-shadow: var(--focus-ring), inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent); }

.player__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.player__button {
  min-height: 44px;
  padding: 10px 20px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 19px;
  transition: background-color .16s ease, border-color .16s ease, color .16s ease;
}

.player__button:hover:not(:disabled) { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.player__button:disabled { cursor: default; opacity: .38; }
.player__button--primary { border-color: transparent; background: var(--text); color: var(--surface); font-weight: 620; }
.player__button--primary:hover:not(:disabled) { background: var(--accent-strong); border-color: transparent; }

.player__silver-control { --liquid-width: 50px; --liquid-height: 50px; flex: 0 0 50px; }
.player__icon { display: block; width: 24px; height: 24px; }
:global([data-theme="dark"]) .player__icon { filter: invert(1); }
.player__speed :deep(.player__speed-select) { width: 98px; flex: none; }

.player__position {
  margin-left: auto;
  color: var(--text);
  font-size: 19px;
  font-weight: 620;
  font-variant-numeric: tabular-nums;
}

.player__speed { display: inline-flex; align-items: center; gap: 8px; color: var(--text-muted); font-size: 19px; }
.player__speed select {
  min-height: 40px;
  padding: 6px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  font: inherit;
  font-size: 19px;
}

.player__rail { display: flex; align-items: flex-end; gap: 3px; height: 16px; }
.player__tick {
  flex: 1 1 0;
  min-width: 3px;
  height: 6px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: color-mix(in srgb, var(--text) 14%, transparent);
  cursor: pointer;
  transition: background-color .16s ease, height .16s ease;
}
.player__tick:hover { height: 12px; background: color-mix(in srgb, var(--text) 36%, transparent); }
.player__tick--done { background: color-mix(in srgb, var(--text) 52%, transparent); }
.player__tick--current { height: 14px; background: var(--text); }
.player__tick:focus-visible { outline: none; box-shadow: var(--focus-ring); }

.player__placeholder { margin: 0; color: var(--text-muted); font-size: 19px; }

@media (prefers-reduced-motion: reduce) {
  .player__button, .player__tick { transition: none; }
}
</style>
