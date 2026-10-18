<script setup lang="ts">
import { computed } from "vue";
import { answerParts } from "../chat-markdown";
import ChatCodeBlock from "./ChatCodeBlock.vue";

const props = defineProps<{ text: string; streaming?: boolean }>();
const parts = computed(() => answerParts(props.text));
</script>

<template>
  <div class="chat-answer">
    <template v-for="(part, index) in parts" :key="index">
      <ChatCodeBlock v-if="part.kind === 'code'" :code="part.code" :language="part.language" :streaming="streaming" />
      <div v-else v-html="part.html" />
    </template>
  </div>
</template>
