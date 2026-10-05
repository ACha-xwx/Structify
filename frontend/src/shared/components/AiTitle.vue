<script setup lang="ts">
import AiBall from "./AiBall.vue";

withDefaults(defineProps<{
  followPointer?: boolean;
  expressive?: boolean;
}>(), { followPointer: true, expressive: false });
</script>

<template>
  <div class="ai-title">
    <slot />
    <AiBall :follow-pointer="followPointer" :expressive="expressive" />
  </div>
</template>

<style scoped>
.ai-title {
  display: flex;
  width: fit-content;
  max-width: 100%;
  min-width: 0;
  align-items: center;
  gap: clamp(10px, 1.6vw, 18px);
}
.ai-title :deep(h1) { min-width: 0; }
@media (max-width: 520px) {
  .ai-title :deep(.ai-ball) { --ai-ball-size: 32px; }
}
</style>
