<script setup lang="ts">
withDefaults(defineProps<{
  direction: "left" | "right";
  size?: number;
}>(), {
  size: 18,
});
</script>

<template>
  <svg
    class="directional-arrow-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    focusable="false"
  >
    <path
      v-if="direction === 'left'"
      d="M5 12H19M5 12L11 6M5 12L11 18"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      v-else
      d="M5 12H19M19 12L13 6M19 12L13 18"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>
