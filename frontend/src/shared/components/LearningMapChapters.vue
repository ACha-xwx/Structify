<script setup lang="ts">
import { computed, ref } from "vue";
import type { LearningMapNode } from "../types/contracts";

/**
 * The chapter-level rendering of a learning map, shared by the admin inspector and the teacher console.
 *
 * The rules this component owns so neither page can drift from them: a null mastery reads as a dash, never as
 * zero; a student with no records gets one honest sentence instead of ten empty chapters; and with
 * `expandable` the lessons inside a chapter stay folded until somebody asks, because a chapter can hold dozens.
 */
const props = withDefaults(defineProps<{ nodes: LearningMapNode[]; expandable?: boolean }>(), {
  expandable: false,
});

const started = computed(() => props.nodes.some((node) => node.status !== "NOT_STARTED"));

/** Chapters in their natural order, each with the lessons that belong to it. */
const chapters = computed<Array<{ node: LearningMapNode; lessons: LearningMapNode[] }>>(() =>
  props.nodes
    .filter((node) => node.level === 1)
    .sort((left, right) => left.id.localeCompare(right.id))
    .map((node) => ({
      node,
      lessons: props.nodes
        .filter((lesson) => lesson.level === 2 && lesson.parentId === node.id)
        .sort((left, right) => left.id.localeCompare(right.id)),
    }))
);

const expanded = ref<string[]>([]);
function toggle(chapterId: string): void {
  expanded.value = expanded.value.includes(chapterId)
    ? expanded.value.filter((id) => id !== chapterId)
    : [...expanded.value, chapterId];
}

function percent(value: number | null | undefined): string {
  return value === null || value === undefined ? "—" : `${Math.round(value * 100)}%`;
}

function statusLabel(status: string): string {
  if (status === "ESTIMATED") return "已估算";
  if (status === "LOW_EVIDENCE") return "证据不足";
  return "未开始";
}
</script>

<template>
  <p v-if="!started" class="lmc__empty">还没有任何学习记录——不是 0 分，是还没开始。</p>
  <ul v-else class="lmc">
    <li v-for="chapter in chapters" :key="chapter.node.id" class="lmc__chapter">
      <div class="lmc__head">
        <span class="lmc__title" :title="chapter.node.title">{{ chapter.node.title }}</span>
        <span class="lmc__meta">{{ percent(chapter.node.mastery) }} · 可信 {{ percent(chapter.node.confidence) }} · {{ statusLabel(chapter.node.status) }}</span>
      </div>
      <div class="lmc__track">
        <div class="lmc__fill" :style="{ width: `${Math.round((chapter.node.mastery ?? 0) * 100)}%` }" />
      </div>
      <template v-if="expandable && chapter.lessons.length">
        <button
          type="button"
          class="lmc__toggle"
          :aria-expanded="expanded.includes(chapter.node.id)"
          @click="toggle(chapter.node.id)"
        >{{ expanded.includes(chapter.node.id) ? "收起课时" : `${chapter.lessons.length} 个课时` }}</button>
        <ul v-if="expanded.includes(chapter.node.id)" class="lmc__lessons">
          <li v-for="lesson in chapter.lessons" :key="lesson.id">
            <span class="lmc__lesson-title">{{ lesson.title }}</span>
            <span class="lmc__meta lmc__nowrap">{{ percent(lesson.mastery) }} · {{ statusLabel(lesson.status) }}</span>
          </li>
        </ul>
      </template>
    </li>
  </ul>
</template>

<style scoped>
.lmc__empty {
  margin: 6px 0;
  font-size: 14px;
  color: var(--admin-muted, rgba(12, 44, 52, 0.55));
}

.lmc {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.lmc__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 5px;
  font-size: 14px;
}

.lmc__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lmc__meta {
  color: var(--admin-muted, rgba(12, 44, 52, 0.55));
  white-space: nowrap;
}

.lmc__nowrap {
  white-space: nowrap;
}

.lmc__track {
  height: 8px;
  border-radius: 999px;
  background: rgba(12, 44, 52, 0.1);
  overflow: hidden;
}

.lmc__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(8, 118, 111, 0.55), rgba(8, 118, 111, 0.9));
}

.lmc__toggle {
  margin-top: 7px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--admin-muted, rgba(12, 44, 52, 0.55));
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  text-decoration: underline;
}

.lmc__toggle:hover {
  color: var(--text, inherit);
}

.lmc__lessons {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.lmc__lessons li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0 4px 12px;
  border-left: 2px solid rgba(8, 118, 111, 0.28);
  font-size: 13px;
}

.lmc__lesson-title {
  min-width: 0;
}
</style>
