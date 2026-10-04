<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "../i18n/locale";

const props = withDefaults(defineProps<{
  followPointer?: boolean;
  expressive?: boolean;
  size?: "title" | "message";
}>(), { followPointer: true, expressive: false, size: "title" });

const { t } = useI18n();
const ball = ref<HTMLButtonElement>();
const spinning = ref(false);
const spinToken = ref(0);
type AiExpression = "idle" | "curious" | "look-around" | "surprised" | "happy" | "shy" | "sleepy" | "wink" | "thinking" | "peek";
const expression = ref<AiExpression>("idle");
let lastExpression: AiExpression = "idle";
const expressionChoices: readonly AiExpression[] = [
  "curious",
  "look-around",
  "surprised",
  "happy",
  "shy",
  "sleepy",
  "wink",
  "thinking",
  "peek",
];
const expressionCycleMs = 6500;
let frame: number | undefined;
let spinTimer: ReturnType<typeof setTimeout> | undefined;
let expressionTimer: ReturnType<typeof setTimeout> | undefined;
let expressionResetTimer: ReturnType<typeof setTimeout> | undefined;
let listening = false;
let pointer: { x: number; y: number } | undefined;

// Write only this mascot's CSS variables; pointer movement never rerenders the chat tree.
function paintGaze() {
  frame = undefined;
  if (!ball.value || !pointer || (props.expressive && expression.value !== "idle")) return;
  const rect = ball.value.getBoundingClientRect();
  const dx = pointer.x - (rect.left + rect.width / 2);
  const dy = pointer.y - (rect.top + rect.height / 2);
  const distance = Math.hypot(dx, dy);
  const reach = rect.width * 0.1 / Math.max(80, distance);
  ball.value.style.setProperty("--ai-gaze-x", `${(dx * reach).toFixed(2)}px`);
  ball.value.style.setProperty("--ai-gaze-y", `${(dy * reach).toFixed(2)}px`);
}

function follow(event: PointerEvent) {
  if (event.pointerType === "touch") return;
  pointer = { x: event.clientX, y: event.clientY };
  if (frame === undefined) frame = requestAnimationFrame(paintGaze);
}

function rest() {
  if (frame !== undefined) cancelAnimationFrame(frame);
  frame = undefined;
  pointer = undefined;
  ball.value?.style.removeProperty("--ai-gaze-x");
  ball.value?.style.removeProperty("--ai-gaze-y");
}

function leave(event: PointerEvent) {
  if (event.relatedTarget === null) rest();
}

function stopFollowing() {
  if (listening) {
    window.removeEventListener("pointermove", follow);
    window.removeEventListener("pointerout", leave);
    window.removeEventListener("blur", rest);
  }
  listening = false;
  rest();
}

function syncFollowing() {
  stopFollowing();
  if (!props.followPointer || (props.expressive && expression.value !== "idle")) return;
  window.addEventListener("pointermove", follow, { passive: true });
  window.addEventListener("pointerout", leave, { passive: true });
  window.addEventListener("blur", rest);
  listening = true;
}

function spin() {
  if (spinTimer !== undefined) clearTimeout(spinTimer);
  spinToken.value += 1;
  spinning.value = true;
  spinTimer = setTimeout(() => {
    spinning.value = false;
    spinTimer = undefined;
  }, 760);
}

function prefersReducedMotion() {
  return typeof window !== "undefined"
    && typeof window.matchMedia === "function"
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function stopExpressionTour() {
  if (expressionTimer !== undefined) clearTimeout(expressionTimer);
  if (expressionResetTimer !== undefined) clearTimeout(expressionResetTimer);
  expressionTimer = undefined;
  expressionResetTimer = undefined;
  expression.value = "idle";
  lastExpression = "idle";
}

function expressionDuration(expressionName: AiExpression) {
  switch (expressionName) {
    case "look-around": return 1600;
    case "happy": return 1200;
    case "thinking": return 1450;
    case "peek": return 1100;
    case "curious":
    case "surprised":
    case "shy":
    case "sleepy":
    case "wink":
      return 1000;
    default: return 0;
  }
}

function scheduleExpression() {
  if (!props.expressive || prefersReducedMotion()) return;
  expressionTimer = setTimeout(() => {
    const available = expressionChoices.filter((item) => item !== lastExpression);
    const index = Math.min(available.length - 1, Math.floor(Math.random() * available.length));
    expression.value = available[index] ?? "curious";
    lastExpression = expression.value;
    stopFollowing();
    expressionTimer = undefined;
    expressionResetTimer = setTimeout(() => {
      expression.value = "idle";
      expressionResetTimer = undefined;
      syncFollowing();
    }, expressionDuration(expression.value));
    scheduleExpression();
  }, expressionCycleMs);
}

function syncExpressionTour() {
  stopExpressionTour();
  syncFollowing();
  scheduleExpression();
}

onMounted(() => {
  syncFollowing();
  syncExpressionTour();
});
watch(() => props.followPointer, syncFollowing);
watch(() => props.expressive, syncExpressionTour);
onBeforeUnmount(() => {
  stopFollowing();
  stopExpressionTour();
  if (spinTimer !== undefined) clearTimeout(spinTimer);
});
</script>

<template>
  <button
    ref="ball"
    type="button"
    class="ai-ball"
    :class="[`ai-ball--${props.size}`, `ai-ball--expression-${expression}`]"
    :data-awake="spinning"
    :data-spinning="spinning"
    :data-expressive="props.expressive"
    :data-expression="expression"
    :data-follow-pointer="props.followPointer"
    :aria-label="t('mascot.interact')"
    @click="spin"
  >
    <span :key="spinToken" class="ai-ball__shape" aria-hidden="true">
      <span class="ai-ball__eyes">
        <span class="ai-ball__eye" />
        <span class="ai-ball__eye" />
      </span>
    </span>
  </button>
</template>

<style scoped>
.ai-ball {
  --ai-ball-size: 40px;
  display: inline-flex;
  width: var(--ai-ball-size);
  height: var(--ai-ball-size);
  flex: 0 0 var(--ai-ball-size);
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #fff;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.ai-ball--message { --ai-ball-size: 36px; }
.ai-ball:focus-visible { outline: 2px solid var(--text); outline-offset: 5px; }
.ai-ball__shape {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  border-radius: 58% 42% 55% 45% / 52% 58% 42% 48%;
  background: #171717;
  transform: rotate(-7deg);
  transition: transform 440ms cubic-bezier(.22, 1.5, .5, 1);
  animation: ai-ball-blob 9s ease-in-out infinite;
}
.ai-ball__eyes {
  display: flex;
  gap: calc(var(--ai-ball-size) * .2);
  transform: translate(var(--ai-gaze-x, 1px), var(--ai-gaze-y, -1px));
  transition: transform 80ms linear;
}
.ai-ball__eye {
  display: block;
  width: calc(var(--ai-ball-size) * .1);
  height: calc(var(--ai-ball-size) * .25);
  border-radius: 5px;
  background: currentColor;
  animation: ai-ball-blink 6.5s infinite;
}
.ai-ball--expression-curious .ai-ball__shape { transform: rotate(8deg) scale(1.04); }
.ai-ball--expression-curious .ai-ball__eyes {
  transform: translate(calc(var(--ai-gaze-x, 1px) + 2px), calc(var(--ai-gaze-y, -1px) - 2px)) scale(1.08);
  transition-duration: 420ms;
}
.ai-ball--expression-look-around .ai-ball__eyes { animation: ai-ball-look-around 1.6s ease-in-out both; }
.ai-ball--expression-surprised .ai-ball__shape { transform: rotate(-2deg) scale(1.06); }
.ai-ball--expression-surprised .ai-ball__eyes {
  transform: translate(var(--ai-gaze-x, 1px), var(--ai-gaze-y, -1px)) scale(1.28);
  transition-duration: 180ms;
}
.ai-ball--expression-happy .ai-ball__shape { animation: ai-ball-happy 1.2s cubic-bezier(.22, 1.5, .5, 1) both, ai-ball-blob 9s ease-in-out infinite; }
.ai-ball--expression-happy .ai-ball__eyes { transform: translate(var(--ai-gaze-x, 1px), calc(var(--ai-gaze-y, -1px) - 2px)) scaleY(.82); }
.ai-ball--expression-shy .ai-ball__shape { background: #3a272b; transform: rotate(6deg) scale(.98); }
.ai-ball--expression-shy .ai-ball__shape::before,
.ai-ball--expression-shy .ai-ball__shape::after {
  content: "";
  position: absolute;
  top: 56%;
  width: 22%;
  height: 10%;
  border-radius: 50%;
  background: #ef8e9d;
  opacity: .78;
  filter: blur(.5px);
}
.ai-ball--expression-shy .ai-ball__shape::before { left: 11%; }
.ai-ball--expression-shy .ai-ball__shape::after { right: 11%; }
.ai-ball--expression-shy .ai-ball__eyes { transform: translate(calc(var(--ai-gaze-x, 1px) + 4px), calc(var(--ai-gaze-y, -1px) + 2px)); }
.ai-ball--expression-sleepy .ai-ball__shape { transform: rotate(-5deg) translateY(2px) scale(.97); }
.ai-ball--expression-sleepy .ai-ball__eyes { transform: translate(var(--ai-gaze-x, 1px), calc(var(--ai-gaze-y, -1px) + 2px)) scaleY(.55); }
.ai-ball--expression-wink .ai-ball__shape { transform: rotate(5deg) scale(1.02); }
.ai-ball--expression-wink .ai-ball__eye:last-child { height: calc(var(--ai-ball-size) * .07); margin-top: calc(var(--ai-ball-size) * .09); }
.ai-ball--expression-thinking .ai-ball__shape { transform: rotate(-4deg); }
.ai-ball--expression-thinking .ai-ball__eyes { animation: ai-ball-thinking 1.45s ease-in-out both; }
.ai-ball--expression-peek .ai-ball__shape { transform: rotate(3deg) translateX(2px); }
.ai-ball--expression-peek .ai-ball__eyes { animation: ai-ball-peek 1.1s ease-in-out both; }
.ai-ball[data-spinning="true"] .ai-ball__shape {
  animation: ai-ball-click-spin 760ms cubic-bezier(.22, 1.2, .36, 1) both;
}
@keyframes ai-ball-blob {
  0%, 100% { border-radius: 58% 42% 55% 45% / 52% 58% 42% 48%; }
  33% { border-radius: 45% 55% 48% 52% / 58% 44% 56% 42%; }
  66% { border-radius: 52% 48% 42% 58% / 45% 52% 48% 55%; }
}
@keyframes ai-ball-blink {
  0%, 42%, 46%, 100% { transform: scaleY(1); }
  44% { transform: scaleY(.12); }
}
@keyframes ai-ball-look-around {
  0%, 100% { transform: translate(-2px, -1px) rotate(0deg); }
  34% { transform: translate(-5px, 1px) rotate(-4deg); }
  68% { transform: translate(5px, -1px) rotate(4deg); }
}
@keyframes ai-ball-happy {
  0%, 100% { transform: translateY(0) rotate(-7deg) scale(1); }
  35% { transform: translateY(-3px) rotate(-4deg) scale(1.05); }
  65% { transform: translateY(1px) rotate(-9deg) scale(.98); }
}
@keyframes ai-ball-thinking {
  0%, 100% { transform: translate(1px, -2px); }
  35% { transform: translate(-3px, -4px); }
  70% { transform: translate(4px, -3px); }
}
@keyframes ai-ball-peek {
  0%, 100% { transform: translate(1px, -1px); }
  28% { transform: translate(-5px, 1px); }
  52% { transform: translate(5px, 1px); }
  76% { transform: translate(3px, -2px); }
}
@keyframes ai-ball-click-spin {
  0% { transform: rotate(-7deg) scale(1); }
  38% { transform: rotate(170deg) scale(1.08); }
  72% { transform: rotate(320deg) scale(.98); }
  100% { transform: rotate(360deg) scale(1); }
}
@media (prefers-reduced-motion: reduce) {
  .ai-ball__shape, .ai-ball__eye, .ai-ball__eyes { animation: none; }
  .ai-ball__shape, .ai-ball__eyes { transition: none; }
}
</style>
