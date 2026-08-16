<script setup lang="ts">
import MorphingSquareLoader from "./MorphingSquareLoader.vue";

withDefaults(defineProps<{ label?: string }>(), { label: "正在加载" });
</script>
<template>
  <div class="state state--loading" role="status" aria-live="polite">
    <MorphingSquareLoader aria-hidden="true" />
    <span>{{ label }}</span>
  </div>
</template>
