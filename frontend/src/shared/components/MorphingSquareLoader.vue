<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(defineProps<{
  message?: string;
  messagePlacement?: "top" | "bottom" | "left" | "right";
}>(), {
  message: "",
  messagePlacement: "bottom",
});

const hasMessage = computed(() => Boolean(props.message.trim()));
const accessibleLabel = computed(() => props.message.trim() || "正在加载");
</script>

<template>
  <div
    class="morphing-square-loader"
    :data-message-placement="props.messagePlacement"
    role="status"
    aria-live="polite"
    :aria-label="accessibleLabel"
  >
    <span class="morphing-square-loader__shape" aria-hidden="true"></span>
    <span v-if="hasMessage" class="morphing-square-loader__message">{{ props.message }}</span>
  </div>
</template>

<style scoped>
.morphing-square-loader {
  --morphing-loader-text: var(--text, #202020);
  --morphing-loader-muted: var(--text-muted, #6c6c6c);
  --morphing-loader-accent: var(--accent, #3c5d64);
  --morphing-loader-surface: var(--surface, #ffffff);
  --morphing-loader-line: var(--line, rgba(32, 32, 32, 0.18));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  max-inline-size: 100%;
  gap: 0.7rem;
  color: var(--morphing-loader-text);
}

.morphing-square-loader[data-message-placement="top"],
.morphing-square-loader[data-message-placement="bottom"] {
  flex-direction: column;
}

.morphing-square-loader[data-message-placement="top"] {
  flex-direction: column-reverse;
}

.morphing-square-loader[data-message-placement="left"] {
  flex-direction: row-reverse;
}

.morphing-square-loader__shape {
  inline-size: 40px;
  block-size: 40px;
  flex: 0 0 40px;
  border: 1px solid var(--morphing-loader-line);
  border-radius: 6%;
  background: var(--morphing-loader-accent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--morphing-loader-surface) 62%, transparent);
  animation: morphing-square-loader-morph 2s ease-in-out infinite;
}

.morphing-square-loader__message {
  min-inline-size: 0;
  color: var(--morphing-loader-muted);
  font: inherit;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

@keyframes morphing-square-loader-morph {
  0% {
    border-radius: 6%;
    transform: rotate(0deg);
  }

  50% {
    border-radius: 50%;
    transform: rotate(180deg);
  }

  100% {
    border-radius: 6%;
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .morphing-square-loader__shape {
    animation: none;
    border-radius: 6%;
    transform: none;
  }
}
</style>
