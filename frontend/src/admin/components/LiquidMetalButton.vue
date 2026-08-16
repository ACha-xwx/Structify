<script setup lang="ts">
import { Comment, computed, nextTick, onBeforeUnmount, onMounted, ref, Text, useAttrs, useSlots, watch, type VNode } from "vue";
import { liquidMetalFragmentShader, ShaderMount } from "@paper-design/shaders";

defineOptions({ inheritAttrs: false });

const props = withDefaults(defineProps<{
  variant?: "primary" | "quiet";
  type?: "button" | "submit" | "reset";
  disabled?: boolean;
  loading?: boolean;
  viewMode?: "text" | "icon";
}>(), {
  variant: "primary",
  type: "button",
  disabled: false,
  loading: false,
});

const emit = defineEmits<{
  click: [event: MouseEvent];
}>();

const attrs = useAttrs();
const slots = useSlots();
const button = ref<HTMLButtonElement | null>(null);
const shaderHost = ref<HTMLElement | null>(null);
const hovered = ref(false);
const pressed = ref(false);
const ripples = ref<Array<{ id: number; x: string; y: string }>>([]);

let shader: ShaderMount | null = null;
let shaderSpeedTimer: number | undefined;
let rippleId = 0;
let reducedMotion = false;
let motionQuery: MediaQueryList | null = null;
const rippleTimers = new Set<number>();

function isAriaHidden(node: VNode) {
  return node.props?.["aria-hidden"] === true || node.props?.["aria-hidden"] === "true";
}

function isVisibleSlotNode(node: VNode): boolean {
  if (node.type === Comment) return false;
  if (isAriaHidden(node)) return false;
  if (node.type === Text) return String(node.children ?? "").trim().length > 0;
  if (typeof node.children === "string") return node.children.trim().length > 0;
  return true;
}

const iconOnly = computed(() => {
  if (props.viewMode === "icon") return true;
  if (props.viewMode === "text") return false;

  const defaultNodes = slots.default?.() ?? [];
  const hasVisibleDefault = defaultNodes.some(isVisibleSlotNode);
  const hasDecorativeDefault = defaultNodes.some(isAriaHidden);
  return !hasVisibleDefault && (hasDecorativeDefault || Boolean(slots.icon));
});

function slotText(node: unknown): string {
  if (Array.isArray(node)) return node.map(slotText).join("");
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (!node || typeof node !== "object") return "";

  const vnode = node as VNode;
  if (vnode.type === Comment || isAriaHidden(vnode)) return "";
  if (vnode.type === Text || typeof vnode.children === "string") return String(vnode.children ?? "");
  if (Array.isArray(vnode.children)) return vnode.children.map(slotText).join("");
  return "";
}

const defaultSlotLabel = computed(() => (slots.default?.() ?? []).map(slotText).join(" ").replace(/\s+/g, " ").trim());

const ariaLabel = computed<string | undefined>(() => {
  const label = attrs["aria-label"];
  if (typeof label === "string" && label.trim()) return label;
  if (props.loading) return "正在提交";
  return attrs["aria-labelledby"] ? undefined : defaultSlotLabel.value || undefined;
});

const buttonAttrs = computed(() => {
  const { class: _class, style: _style, ...nativeAttrs } = attrs;
  return nativeAttrs;
});

function supportsWebGlShader() {
  if (typeof window === "undefined" || /jsdom/i.test(navigator.userAgent)) return false;
  const canvas = document.createElement("canvas");
  return Boolean(canvas.getContext("webgl2"));
}

function setShaderSpeed(speed: number) {
  shader?.setSpeed(reducedMotion || props.loading ? 0 : speed);
}

function clearShaderSpeedTimer() {
  if (shaderSpeedTimer !== undefined) {
    window.clearTimeout(shaderSpeedTimer);
    shaderSpeedTimer = undefined;
  }
}

function clearRipples() {
  rippleTimers.forEach((timer) => window.clearTimeout(timer));
  rippleTimers.clear();
  ripples.value = [];
}

function disposeShader() {
  shader?.dispose();
  shader = null;
}

function mountShader() {
  if (props.loading || !shaderHost.value || !supportsWebGlShader()) return;
  disposeShader();
  try {
    shader = new ShaderMount(
      shaderHost.value,
      liquidMetalFragmentShader,
      {
        u_repetition: 4,
        u_softness: 0.5,
        u_shiftRed: 0.3,
        u_shiftBlue: 0.3,
        u_distortion: 0,
        u_contour: 0,
        u_angle: 45,
        u_scale: 8,
        u_shape: 1,
        u_offsetX: 0.1,
        u_offsetY: -0.1,
      },
      undefined,
      reducedMotion ? 0 : 0.6,
    );
    if (reducedMotion) shader.setFrame(0);
  } catch {
    shaderHost.value?.replaceChildren();
    shader = null;
  }
}

function handlePointerEnter() {
  if (props.disabled || props.loading) return;
  hovered.value = true;
  setShaderSpeed(1);
}

function handlePointerLeave() {
  hovered.value = false;
  pressed.value = false;
  setShaderSpeed(0.6);
}

function handlePointerDown(event: PointerEvent) {
  if (props.disabled || props.loading || (event.pointerType === "mouse" && event.button !== 0)) return;
  pressed.value = true;
}

function handlePointerUp() {
  pressed.value = false;
}

function handleKeydown(event: KeyboardEvent) {
  if (props.disabled || props.loading || event.repeat || (event.key !== "Enter" && event.key !== " ")) return;
  pressed.value = true;
}

function handleKeyup(event: KeyboardEvent) {
  if (event.key === "Enter" || event.key === " ") handlePointerUp();
}

function ripplePosition(event: MouseEvent) {
  const element = button.value;
  if (!element) return { x: "50%", y: "50%" };
  const bounds = element.getBoundingClientRect();
  if (
    bounds.width <= 0
    || bounds.height <= 0
    || event.clientX < bounds.left
    || event.clientX > bounds.right
    || event.clientY < bounds.top
    || event.clientY > bounds.bottom
  ) {
    return { x: "50%", y: "50%" };
  }
  return {
    x: `${Math.round(event.clientX - bounds.left)}px`,
    y: `${Math.round(event.clientY - bounds.top)}px`,
  };
}

function startRipple(event: MouseEvent) {
  if (reducedMotion) return;
  const position = ripplePosition(event);
  const id = rippleId++;
  ripples.value = [...ripples.value, { id, ...position }];
  const timer = window.setTimeout(() => {
    rippleTimers.delete(timer);
    ripples.value = ripples.value.filter((ripple) => ripple.id !== id);
  }, 420);
  rippleTimers.add(timer);
}

function handleClick(event: MouseEvent) {
  if (props.disabled || props.loading) {
    event.preventDefault();
    return;
  }

  clearShaderSpeedTimer();
  setShaderSpeed(2.4);
  shaderSpeedTimer = window.setTimeout(() => {
    shaderSpeedTimer = undefined;
    setShaderSpeed(hovered.value ? 1 : 0.6);
  }, 300);
  startRipple(event);
  emit("click", event);
}

function handleMotionPreferenceChange(event: MediaQueryListEvent) {
  reducedMotion = event.matches;
  if (reducedMotion) {
    shader?.setSpeed(0);
    shader?.setFrame(0);
    clearRipples();
    return;
  }
  setShaderSpeed(hovered.value ? 1 : 0.6);
}

watch(
  () => props.loading,
  async (loading) => {
    pressed.value = false;
    clearShaderSpeedTimer();
    clearRipples();
    if (loading) {
      disposeShader();
      return;
    }
    await nextTick();
    mountShader();
    setShaderSpeed(hovered.value ? 1 : 0.6);
  },
);

onMounted(() => {
  if (typeof window !== "undefined" && typeof window.matchMedia === "function") {
    motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    reducedMotion = motionQuery.matches;
    motionQuery.addEventListener("change", handleMotionPreferenceChange);
  }
  mountShader();
});

onBeforeUnmount(() => {
  clearShaderSpeedTimer();
  clearRipples();
  motionQuery?.removeEventListener("change", handleMotionPreferenceChange);
  motionQuery = null;
  disposeShader();
});
</script>

<template>
  <div
    class="liquid-metal-button"
    :class="[
      `liquid-metal-button--${props.variant}`,
      attrs.class,
      {
        'liquid-metal-button--icon-only': iconOnly,
        'is-hovered': hovered,
        'is-pressed': pressed,
        'is-disabled': props.disabled || props.loading,
        'is-loading': props.loading,
      },
    ]"
    :style="attrs.style"
  >
    <span class="liquid-metal-button__stage">
      <span v-if="!props.loading" class="liquid-metal-button__scene" aria-hidden="true">
        <span class="liquid-metal-button__content-layer">
          <span v-if="$slots.icon" class="liquid-metal-button__icon"><slot name="icon" /></span>
          <slot />
        </span>
        <span class="liquid-metal-button__surface-layer" aria-hidden="true"><span class="liquid-metal-button__surface"></span></span>
        <span class="liquid-metal-button__shader-layer" aria-hidden="true">
          <span class="liquid-metal-button__refractive-rim" aria-hidden="true"></span>
          <span ref="shaderHost" class="liquid-metal-button__shader"></span>
        </span>
      </span>
      <button
        ref="button"
        v-bind="buttonAttrs"
        class="liquid-metal-button__native"
        :type="props.type"
        :disabled="props.disabled || props.loading"
        :aria-busy="props.loading ? 'true' : undefined"
        :aria-label="ariaLabel"
        @blur="handlePointerLeave"
        @click="handleClick"
        @keydown="handleKeydown"
        @keyup="handleKeyup"
        @pointerdown="handlePointerDown"
        @pointerenter="handlePointerEnter"
        @pointerleave="handlePointerLeave"
        @pointerup="handlePointerUp"
        @pointercancel="handlePointerLeave"
      >
        <span
          v-if="!props.loading && defaultSlotLabel"
          class="liquid-metal-button__native-label"
          aria-hidden="true"
        >{{ defaultSlotLabel }}</span>
        <span
          v-for="ripple in ripples"
          :key="ripple.id"
          class="liquid-metal-button__ripple"
          :style="{ '--ripple-x': ripple.x, '--ripple-y': ripple.y }"
          aria-hidden="true"
        ></span>
        <span v-if="props.loading" class="liquid-metal-button__spinner" aria-hidden="true"></span>
      </button>
    </span>
  </div>
</template>

<style scoped>
.liquid-metal-button {
  --liquid-width: 142px;
  --liquid-height: 46px;
  --liquid-rim-inset: 3px;
  --liquid-ease-out: cubic-bezier(0.16, 0.82, 0.27, 1);
  position: relative;
  display: inline-block;
  width: var(--liquid-width);
  min-width: var(--liquid-width);
  max-width: 100%;
  height: var(--liquid-height);
  min-height: var(--liquid-height);
  perspective: 1000px;
  perspective-origin: 50% 50%;
  overflow: visible;
}

.liquid-metal-button--icon-only {
  --liquid-width: 46px;
}

.liquid-metal-button__scene,
.liquid-metal-button__content-layer,
.liquid-metal-button__surface-layer,
.liquid-metal-button__shader-layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  pointer-events: none;
}

.liquid-metal-button__stage {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
  transition: width 220ms ease, height 220ms ease;
  transform: none;
}

.liquid-metal-button__native {
  position: absolute;
  z-index: 40;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  padding: 0;
  overflow: hidden;
  border: 0;
  border-radius: 100px;
  outline: none;
  background: transparent;
  color: #26373d;
  cursor: pointer;
  font: inherit;
  transform: translateZ(25px);
  transform-style: preserve-3d;
  transition: transform 140ms ease-out, width 220ms ease, height 220ms ease;
}

.liquid-metal-button__native-label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

.liquid-metal-button__content-layer {
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #26373d;
  font-size: 14px;
  font-weight: 400;
  line-height: 1;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.88);
  white-space: nowrap;
  transform: translateZ(20px);
  transition: transform 160ms var(--liquid-ease-out), width 220ms ease, height 220ms ease, gap 180ms ease;
}

.liquid-metal-button--icon-only .liquid-metal-button__content-layer { font-size: 16px; }

.liquid-metal-button__icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  line-height: 0;
}

.liquid-metal-button__content-layer :deep(svg) {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  filter: drop-shadow(0 1px 0 rgba(255, 255, 255, 0.88));
}

.liquid-metal-button__surface-layer {
  z-index: 20;
  transform: translateZ(10px) translateY(0) scale(1);
  transition: transform 160ms var(--liquid-ease-out), width 220ms ease, height 220ms ease;
}

.liquid-metal-button__surface {
  position: absolute;
  inset: var(--liquid-rim-inset);
  border-radius: 100px;
  background:
    linear-gradient(106deg, transparent 0 27%, rgba(255, 255, 255, 0.78) 43%, transparent 59%),
    linear-gradient(180deg, #ffffff 0%, #f6f8f9 31%, #e8ecee 68%, #d5dce0 100%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.98),
    inset 0 -1px 0 rgba(67, 83, 92, 0.17),
    inset 1px 0 0 rgba(255, 255, 255, 0.72);
  transition: background 180ms ease, box-shadow 140ms ease, width 220ms ease, height 220ms ease;
}

.liquid-metal-button--quiet .liquid-metal-button__surface {
  background:
    linear-gradient(106deg, transparent 0 27%, rgba(255, 255, 255, 0.88) 43%, transparent 59%),
    linear-gradient(180deg, #ffffff 0%, #fafbfb 31%, #edf0f1 68%, #dde3e6 100%);
}

.liquid-metal-button__shader-layer {
  z-index: 10;
  transform: translateZ(0) translateY(0) scale(1);
  transition: transform 160ms var(--liquid-ease-out), width 220ms ease, height 220ms ease;
}

.liquid-metal-button__shader-layer::before {
  position: absolute;
  inset: 0;
  border-radius: 100px;
  background: rgb(0 0 0 / 0);
  box-shadow:
    0 0 0 1px rgba(0, 0, 0, 0.3),
    0 36px 14px rgba(0, 0, 0, 0.02),
    0 20px 12px rgba(0, 0, 0, 0.08),
    0 9px 9px rgba(0, 0, 0, 0.12),
    0 2px 5px rgba(0, 0, 0, 0.15);
  content: "";
  transition: box-shadow 140ms ease, width 220ms ease, height 220ms ease;
}

.liquid-metal-button__shader {
  position: absolute;
  z-index: 0;
  inset: 0;
  display: block;
  width: 100%;
  max-width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: 100px;
  transform: scaleX(1.045) scaleY(1.14);
  transform-origin: 50% 50%;
  transition: transform 180ms ease, width 400ms ease, height 400ms ease;
  will-change: transform;
}

.liquid-metal-button__refractive-rim {
  position: absolute;
  z-index: 1;
  inset: -1px;
  padding: 1.25px;
  border-radius: 100px;
  background: conic-gradient(
    from 212deg at 50% 50%,
    rgba(97, 225, 255, 0.88),
    rgba(220, 244, 255, 0.54) 13%,
    rgba(143, 156, 255, 0.64) 27%,
    rgba(255, 255, 255, 0.22) 42%,
    rgba(255, 214, 119, 0.82) 62%,
    rgba(255, 145, 218, 0.76) 77%,
    rgba(103, 233, 255, 0.84) 91%,
    rgba(97, 225, 255, 0.88)
  );
  opacity: 0.76;
  pointer-events: none;
  transform: scaleX(1.025) scaleY(1.075);
  transform-origin: 50% 50%;
  transition: transform 180ms ease, opacity 150ms ease;
  will-change: transform;
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  mask-composite: exclude;
}

.liquid-metal-button__shader :deep(canvas) {
  position: absolute !important;
  top: 0 !important;
  left: 0 !important;
  display: block !important;
  width: 100% !important;
  height: 100% !important;
  border-radius: 100px !important;
}

.liquid-metal-button.is-hovered .liquid-metal-button__shader-layer::before {
  box-shadow:
    0 0 0 1px rgba(0, 0, 0, 0.4),
    0 12px 6px rgba(0, 0, 0, 0.05),
    0 8px 5px rgba(0, 0, 0, 0.1),
    0 4px 4px rgba(0, 0, 0, 0.15),
    0 1px 2px rgba(0, 0, 0, 0.2);
}

.liquid-metal-button.is-hovered .liquid-metal-button__shader {
  transform: scaleX(1.075) scaleY(1.2);
}

.liquid-metal-button.is-hovered .liquid-metal-button__refractive-rim {
  opacity: 0.92;
  transform: scaleX(1.045) scaleY(1.14);
}

.liquid-metal-button.is-pressed .liquid-metal-button__surface-layer,
.liquid-metal-button.is-pressed .liquid-metal-button__shader-layer {
  transform: translateZ(10px) translateY(1px) scale(0.98);
}

.liquid-metal-button.is-pressed .liquid-metal-button__shader-layer {
  transform: translateZ(0) translateY(1px) scale(0.98);
}

.liquid-metal-button.is-pressed .liquid-metal-button__surface {
  box-shadow:
    inset 0 2px 4px rgba(68, 84, 94, 0.22),
    inset 0 1px 2px rgba(68, 84, 94, 0.16),
    inset 0 -1px 0 rgba(255, 255, 255, 0.72);
}

.liquid-metal-button.is-pressed .liquid-metal-button__shader-layer::before {
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.5), 0 1px 2px rgba(0, 0, 0, 0.3);
}

.liquid-metal-button.is-pressed .liquid-metal-button__shader {
  transform: scaleX(1.09) scaleY(1.25);
}

.liquid-metal-button.is-pressed .liquid-metal-button__refractive-rim {
  opacity: 1;
  transform: scaleX(1.06) scaleY(1.18);
}

.liquid-metal-button__ripple {
  position: absolute;
  z-index: 40;
  top: var(--ripple-y);
  left: var(--ripple-x);
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(43, 61, 71, 0.22) 0%, rgba(43, 61, 71, 0) 70%);
  pointer-events: none;
  transform: translate(-50%, -50%) scale(0);
  animation: liquid-metal-ripple 420ms ease-out both;
}

.liquid-metal-button__spinner {
  position: absolute;
  z-index: 40;
  inset: 0;
  width: 18px;
  height: 18px;
  margin: auto;
  border: 2px solid rgba(112, 112, 112, 0.24);
  border-right-color: #6b6b6b;
  border-bottom-color: #b5b5b5;
  border-radius: 50%;
  animation: liquid-metal-spin 720ms linear infinite;
}

.liquid-metal-button__native:focus-visible { outline: 2px solid rgba(74, 74, 74, 0.6); outline-offset: 3px; }
.liquid-metal-button__native:disabled { cursor: not-allowed; opacity: 0.52; }
.liquid-metal-button.is-disabled:not(.is-loading) { opacity: 0.52; }
.liquid-metal-button.is-loading .liquid-metal-button__native:disabled { opacity: 1; }

@keyframes liquid-metal-ripple {
  from { opacity: 0.6; transform: translate(-50%, -50%) scale(0); }
  to { opacity: 0; transform: translate(-50%, -50%) scale(4); }
}

@keyframes liquid-metal-spin {
  to { transform: rotate(1turn); }
}

@media (prefers-reduced-motion: reduce) {
  .liquid-metal-button,
  .liquid-metal-button__stage,
  .liquid-metal-button__native,
  .liquid-metal-button__content-layer,
  .liquid-metal-button__surface-layer,
  .liquid-metal-button__surface,
  .liquid-metal-button__shader-layer,
  .liquid-metal-button__shader-layer::before,
  .liquid-metal-button__refractive-rim,
  .liquid-metal-button__shader { transition: none; }

  .liquid-metal-button__ripple { display: none; }
  .liquid-metal-button__spinner { animation: none; }
}
</style>
