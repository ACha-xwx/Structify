<script setup lang="ts">
import { ref, useAttrs } from "vue";

defineOptions({ inheritAttrs: false });

const props = defineProps<{ modelValue?: string }>();
const emit = defineEmits<{ (event: "update:modelValue", value: string): void }>();
const attrs = useAttrs();
const textarea = ref<HTMLTextAreaElement | null>(null);

defineExpose({ textarea });

function updateValue(event: Event) {
  emit("update:modelValue", (event.target as HTMLTextAreaElement).value);
}
</script>

<template>
  <textarea
    ref="textarea"
    v-bind="attrs"
    :value="props.modelValue"
    class="textarea"
    @input="updateValue"
  />
</template>

<style scoped>
.textarea {
  display: block;
  width: 100%;
  min-height: 80px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md, 8px);
  outline: 0;
  background: var(--surface);
  color: var(--text);
  font-size: 14px;
  line-height: 1.5;
  resize: vertical;
  transition: border-color 140ms ease, box-shadow 140ms ease, background-color 140ms ease;
}

.textarea::placeholder { color: var(--text-muted); }
.textarea:focus-visible { border-color: var(--line-strong); box-shadow: var(--focus-ring); }
.textarea:disabled { cursor: not-allowed; opacity: 0.5; }

@media (prefers-reduced-motion: reduce) {
  .textarea { transition: none; }
}
</style>
