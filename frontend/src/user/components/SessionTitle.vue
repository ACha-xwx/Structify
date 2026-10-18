<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";

const props = defineProps<{ title: string }>();
const viewport = ref<HTMLElement>();
const text = ref<HTMLElement>();
const scrolling = ref(false);
const style = ref<Record<string, string>>({});
let observer: ResizeObserver | undefined;

function reset() { scrolling.value = false; }
function start() {
  const distance = Math.max(0, (text.value?.scrollWidth ?? 0) - (viewport.value?.clientWidth ?? 0));
  if (!distance || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
  style.value = { '--title-distance': `${-distance}px`, '--title-duration': `${Math.max(2, distance / 35)}s` };
  scrolling.value = true;
}
watch(viewport, (element) => {
  observer?.disconnect();
  if (element && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(reset);
    observer.observe(element);
  }
});
watch(() => props.title, reset);
onBeforeUnmount(() => observer?.disconnect());
</script>

<template>
  <span ref="viewport" class="session-title" :title="title" @mouseenter="start" @mouseleave="reset">
    <span ref="text" class="session-title__text" :class="{ 'is-scrolling': scrolling }" :style="style">{{ title }}</span>
  </span>
</template>

<style scoped>
.session-title { display: block; width: 100%; min-width: 0; overflow: hidden; }
.session-title__text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-title__text.is-scrolling { width: max-content; overflow: visible; animation: title-pan var(--title-duration) linear .4s forwards; }
@keyframes title-pan { to { transform: translateX(var(--title-distance)); } }
@media (prefers-reduced-motion: reduce) { .session-title__text.is-scrolling { width: 100%; overflow: hidden; animation: none; } }
</style>
