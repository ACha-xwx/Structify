<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId } from "vue";

const cursor = ref<HTMLDivElement | null>(null);
const glyph = ref<SVGSVGElement | null>(null);
const filterId = `smooth-cursor-shadow-${useId()}`;
const cursorClass = "structify-custom-cursor";
const arrowTargetClass = "structify-native-arrow-hidden";
let arrowTarget: Element | null = null;
let finePointer: MediaQueryList | undefined;
let previousPosition: { x: number; y: number } | null = null;
let previousAngle = 0;
let rotation = 0;
let idleTimer: number | undefined;

function resetScale() {
  if (glyph.value) glyph.value.style.transform = `rotate(${rotation}deg) scale(0.5)`;
  idleTimer = undefined;
}

function restoreNativeCursor() {
  arrowTarget?.classList.remove(arrowTargetClass);
  arrowTarget = null;
}

function usesArrowCursor(target: Element, event: MouseEvent) {
  const style = window.getComputedStyle(target);
  const nativeCursor = style.cursor || "auto";
  if (nativeCursor === "default") return true;
  if (nativeCursor !== "auto") return false;

  // Browser-selected auto cursors include text editing and link pointers.
  if (target.closest("input, textarea, select, a[href], iframe, object, embed")) return false;
  const editable = target.closest("[contenteditable]");
  if (editable && editable.getAttribute("contenteditable") !== "false") return false;
  if (target.closest("button") || style.userSelect === "none") return true;

  // Auto also becomes an I-beam over selectable text, but not over its padding.
  const range = document.createRange();
  for (const node of target.childNodes) {
    if (node.nodeType !== Node.TEXT_NODE || !node.textContent?.trim()) continue;
    range.selectNodeContents(node);
    for (const rect of Array.from(range.getClientRects?.() ?? [])) {
      if (event.clientX >= rect.left && event.clientX <= rect.right
        && event.clientY >= rect.top && event.clientY <= rect.bottom) return false;
    }
  }
  return true;
}

function hideCursor() {
  restoreNativeCursor();
  if (cursor.value) cursor.value.hidden = true;
  document.documentElement.classList.remove(cursorClass);
  previousPosition = null;
  if (idleTimer !== undefined) window.clearTimeout(idleTimer);
  resetScale();
}

function moveCursor(event: MouseEvent) {
  // Remove our override before reading the target's actual cursor, including inheritance.
  restoreNativeCursor();
  if (finePointer && !finePointer.matches) return;
  const element = cursor.value;
  if (!element) return;
  const target = event.target instanceof Element ? event.target : document.documentElement;
  if (!usesArrowCursor(target, event)) {
    hideCursor();
    return;
  }

  // Update position in the event itself: no spring, interpolation, or position transition.
  element.style.transform = `translate3d(${event.clientX}px, ${event.clientY}px, 0) translate(-50%, -50%)`;
  element.hidden = false;
  arrowTarget = target;
  target.classList.add(arrowTargetClass);
  document.documentElement.classList.add(cursorClass);

  if (previousPosition && glyph.value) {
    const dx = event.clientX - previousPosition.x;
    const dy = event.clientY - previousPosition.y;
    if (dx !== 0 || dy !== 0) {
      const angle = Math.atan2(dy, dx) * 180 / Math.PI + 90;
      const delta = ((angle - previousAngle + 540) % 360) - 180;
      rotation += delta;
      previousAngle = angle;
      glyph.value.style.transform = `rotate(${rotation}deg) scale(0.475)`;
      if (idleTimer !== undefined) window.clearTimeout(idleTimer);
      idleTimer = window.setTimeout(resetScale, 150);
    }
  }
  previousPosition = { x: event.clientX, y: event.clientY };
}

function handlePointerDown(event: PointerEvent) {
  if (event.pointerType !== "mouse") hideCursor();
}

onMounted(() => {
  finePointer = window.matchMedia?.("(any-pointer: fine)");
  finePointer?.addEventListener("change", hideCursor);
  window.addEventListener("mousemove", moveCursor, { passive: true });
  window.addEventListener("mouseover", moveCursor, { passive: true });
  window.addEventListener("pointerdown", handlePointerDown, { passive: true });
  window.addEventListener("blur", hideCursor);
  document.documentElement.addEventListener("mouseleave", hideCursor);
});

onBeforeUnmount(() => {
  window.removeEventListener("mousemove", moveCursor);
  window.removeEventListener("mouseover", moveCursor);
  window.removeEventListener("pointerdown", handlePointerDown);
  window.removeEventListener("blur", hideCursor);
  document.documentElement.removeEventListener("mouseleave", hideCursor);
  finePointer?.removeEventListener("change", hideCursor);
  hideCursor();
});
</script>

<template>
  <Teleport to="body">
    <div ref="cursor" class="smooth-cursor" hidden aria-hidden="true">
      <svg ref="glyph" class="smooth-cursor__glyph" xmlns="http://www.w3.org/2000/svg" width="50" height="54" viewBox="0 0 50 54" fill="none">
        <g :filter="`url(#${filterId})`">
          <path d="M42.6817 41.1495L27.5103 6.79925C26.7269 5.02557 24.2082 5.02558 23.3927 6.79925L7.59814 41.1495C6.75833 42.9759 8.52712 44.8902 10.4125 44.1954L24.3757 39.0496C24.8829 38.8627 25.4385 38.8627 25.9422 39.0496L39.8121 44.1954C41.6849 44.8902 43.4884 42.9759 42.6817 41.1495Z" fill="black" />
          <path d="M43.7146 40.6933L28.5431 6.34306C27.3556 3.65428 23.5772 3.69516 22.3668 6.32755L6.57226 40.6778C5.3134 43.4156 7.97238 46.298 10.803 45.2549L24.7662 40.109C25.0221 40.0147 25.2999 40.0156 25.5494 40.1082L39.4193 45.254C42.2261 46.2953 44.9254 43.4347 43.7146 40.6933Z" stroke="white" stroke-width="2.25825" />
        </g>
        <defs>
          <filter :id="filterId" x="0.602397" y="0.952444" width="49.0584" height="52.428" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB">
            <feFlood flood-opacity="0" result="BackgroundImageFix" />
            <feColorMatrix in="SourceAlpha" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0" result="hardAlpha" />
            <feOffset dy="2.25825" />
            <feGaussianBlur stdDeviation="2.25825" />
            <feComposite in2="hardAlpha" operator="out" />
            <feColorMatrix type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.08 0" />
            <feBlend mode="normal" in2="BackgroundImageFix" result="dropShadow" />
            <feBlend mode="normal" in="SourceGraphic" in2="dropShadow" result="shape" />
          </filter>
        </defs>
      </svg>
    </div>
  </Teleport>
</template>

<style>
.structify-native-arrow-hidden {
  cursor: none !important;
}
</style>

<style scoped>
.smooth-cursor {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 2147483647;
  width: 50px;
  height: 54px;
  pointer-events: none;
  will-change: transform;
}

.smooth-cursor__glyph {
  display: block;
  transform: scale(0.5);
  transform-origin: center;
  transition: transform 140ms ease-out;
}

@media (prefers-reduced-motion: reduce) {
  .smooth-cursor__glyph { transition: none; }
}
</style>
