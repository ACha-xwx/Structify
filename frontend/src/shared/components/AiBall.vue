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
const spinDurationMs = 900;
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
  }, spinDurationMs);
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
    :data-spin-mode="spinning ? 'orbit' : undefined"
    :data-expressive="props.expressive"
    :data-expression="expression"
    :data-follow-pointer="props.followPointer"
    :aria-label="t('mascot.interact')"
    @click="spin"
  >
    <span :key="spinToken" class="ai-ball__stage" aria-hidden="true">
      <span class="ai-ball__orbit ai-ball__orbit--back" />
      <span class="ai-ball__motion">
        <span class="ai-ball__shape">
          <span class="ai-ball__eyes">
            <span class="ai-ball__eye" />
            <span class="ai-ball__eye" />
          </span>
        </span>
      </span>
      <span class="ai-ball__orbit ai-ball__orbit--front" />
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
  perspective: 220px;
  perspective-origin: 50% 44%;
  -webkit-tap-highlight-color: transparent;
}
.ai-ball--message { --ai-ball-size: 36px; }
.ai-ball:focus-visible { outline: 2px solid var(--text); outline-offset: 5px; }
.ai-ball__stage {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  transform-style: preserve-3d;
  isolation: isolate;
}
.ai-ball__motion {
  position: relative;
  z-index: 2;
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  transform: translate3d(0, 0, 0) rotate(-7deg);
  transform-origin: 50% 50%;
  transition: transform 520ms cubic-bezier(.2, .84, .24, 1);
  will-change: transform;
}
.ai-ball__shape {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  border-radius: 58% 42% 55% 45% / 52% 58% 42% 48%;
  background: #171717;
  transform: translateZ(0);
  transition: background-color 440ms cubic-bezier(.2, .84, .24, 1);
  animation: ai-ball-blob 9s ease-in-out infinite;
}
.ai-ball__shape::before,
.ai-ball__shape::after {
  content: "";
  position: absolute;
  top: 56%;
  width: 22%;
  height: 10%;
  border-radius: 50%;
  background: #ef8e9d;
  opacity: 0;
  filter: blur(.5px);
  transition: opacity 420ms ease;
}
.ai-ball__shape::before { left: 11%; }
.ai-ball__shape::after { right: 11%; }
.ai-ball__orbit {
  position: absolute;
  inset: -12%;
  z-index: 1;
  border: 1.5px solid rgba(255, 255, 255, .42);
  border-radius: 50%;
  box-shadow: 0 0 8px rgba(255, 255, 255, .15);
  opacity: 0;
  pointer-events: none;
  transform: rotateX(68deg) rotateZ(-18deg) translateZ(0);
  transform-style: preserve-3d;
  backface-visibility: hidden;
}
.ai-ball__orbit--back {
  clip-path: inset(-20% -20% 50% -20%);
  border-color: rgba(255, 255, 255, .28);
  filter: blur(.15px);
}
.ai-ball__orbit--front {
  z-index: 3;
  clip-path: inset(50% -20% -20% -20%);
  border-color: rgba(255, 255, 255, .78);
  box-shadow: 0 0 10px rgba(255, 255, 255, .26);
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
.ai-ball--expression-curious .ai-ball__motion { transform: translate3d(0, -1px, 0) rotate(8deg) scale(1.04); }
.ai-ball--expression-curious .ai-ball__eyes {
  transform: translate(calc(var(--ai-gaze-x, 1px) + 2px), calc(var(--ai-gaze-y, -1px) - 2px)) scale(1.08);
  transition-duration: 420ms;
}
.ai-ball--expression-look-around .ai-ball__eyes { animation: ai-ball-look-around 1.6s ease-in-out both; }
.ai-ball--expression-surprised .ai-ball__motion { transform: translate3d(0, -2px, 0) rotate(-2deg) scale(1.06); }
.ai-ball--expression-surprised .ai-ball__eyes {
  transform: translate(var(--ai-gaze-x, 1px), var(--ai-gaze-y, -1px)) scale(1.28);
  transition-duration: 180ms;
}
.ai-ball--expression-happy .ai-ball__motion { transform: translate3d(0, -1px, 0) rotate(-5deg) scale(1.02); }
.ai-ball--expression-happy .ai-ball__shape { animation: ai-ball-happy 1.2s cubic-bezier(.22, 1.5, .5, 1) both, ai-ball-blob 9s ease-in-out infinite; }
.ai-ball--expression-happy .ai-ball__eyes { transform: translate(var(--ai-gaze-x, 1px), calc(var(--ai-gaze-y, -1px) - 2px)) scaleY(.82); }
.ai-ball--expression-shy .ai-ball__motion { transform: translate3d(0, 1px, 0) rotate(6deg) scale(.98); }
.ai-ball--expression-shy .ai-ball__shape { background: #3a272b; }
.ai-ball--expression-shy .ai-ball__shape::before,
.ai-ball--expression-shy .ai-ball__shape::after { opacity: .78; }
.ai-ball--expression-shy .ai-ball__eyes { transform: translate(calc(var(--ai-gaze-x, 1px) + 4px), calc(var(--ai-gaze-y, -1px) + 2px)); }
.ai-ball--expression-sleepy .ai-ball__motion { transform: translate3d(0, 2px, 0) rotate(-5deg) scale(.97); }
.ai-ball--expression-sleepy .ai-ball__eyes { transform: translate(var(--ai-gaze-x, 1px), calc(var(--ai-gaze-y, -1px) + 2px)) scaleY(.55); }
.ai-ball--expression-wink .ai-ball__motion { transform: translate3d(0, 0, 0) rotate(5deg) scale(1.02); }
.ai-ball--expression-wink .ai-ball__eye:last-child { height: calc(var(--ai-ball-size) * .07); margin-top: calc(var(--ai-ball-size) * .09); }
.ai-ball--expression-thinking .ai-ball__motion { transform: translate3d(0, -1px, 0) rotate(-4deg); }
.ai-ball--expression-thinking .ai-ball__eyes { animation: ai-ball-thinking 1.45s ease-in-out both; }
.ai-ball--expression-peek .ai-ball__motion { transform: translate3d(2px, 0, 0) rotate(3deg); }
.ai-ball--expression-peek .ai-ball__eyes { animation: ai-ball-peek 1.1s ease-in-out both; }
.ai-ball[data-spinning="true"] .ai-ball__stage {
  animation: ai-ball-space-dip 900ms cubic-bezier(.2, .86, .26, 1) both;
}
.ai-ball[data-spinning="true"] .ai-ball__orbit {
  animation: ai-ball-click-orbit 900ms cubic-bezier(.2, .86, .26, 1) both;
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
@keyframes ai-ball-space-dip {
  0% { transform: translateZ(0) rotateX(0deg) rotateY(0deg); }
  24% { transform: translateZ(8px) rotateX(-8deg) rotateY(-12deg); }
  52% { transform: translateZ(4px) rotateX(10deg) rotateY(18deg); }
  78% { transform: translateZ(7px) rotateX(-5deg) rotateY(-8deg); }
  100% { transform: translateZ(0) rotateX(0deg) rotateY(0deg); }
}
@keyframes ai-ball-click-orbit {
  0% { opacity: 0; transform: rotateX(68deg) rotateY(-18deg) rotateZ(-18deg) scale(.86); }
  14% { opacity: .58; transform: rotateX(76deg) rotateY(24deg) rotateZ(12deg) scale(1.02); }
  38% { opacity: .9; transform: rotateX(24deg) rotateY(112deg) rotateZ(78deg) scale(1.08); }
  60% { opacity: .64; transform: rotateX(72deg) rotateY(214deg) rotateZ(170deg) scale(.96); }
  82% { opacity: .8; transform: rotateX(28deg) rotateY(302deg) rotateZ(246deg) scale(1.08); }
  100% { opacity: 0; transform: rotateX(68deg) rotateY(360deg) rotateZ(342deg) scale(.86); }
}
@media (prefers-reduced-motion: reduce) {
  .ai-ball__stage, .ai-ball__orbit, .ai-ball__shape, .ai-ball__eye, .ai-ball__eyes { animation: none; }
  .ai-ball__motion, .ai-ball__shape, .ai-ball__eyes, .ai-ball__shape::before, .ai-ball__shape::after { transition: none; }
}
</style>
