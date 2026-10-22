<script setup lang="ts">
import type { KnowledgePointState } from '../../../shared/types/outline';
defineProps<{points:KnowledgePointState[];activeId:string}>();
defineEmits<{select:[id:string]}>();
function status(point:KnowledgePointState){
  if(point.completed)return '已完成学习';
  if(point.failedFragmentCount)return point.readableFragmentCount?'部分内容可读':'需要重新准备';
  return point.status==='READY'?'内容已就绪':point.readableFragmentCount?'可开始阅读':'正在准备';
}
</script>
<template>
  <nav class="knowledge-route" aria-label="知识点路线">
    <button v-for="(point,index) in points" :key="point.id" type="button" :data-unit-id="point.id"
      :aria-current="activeId===point.id?'step':undefined" @click="$emit('select',point.id)">
      <span class="route-number">{{String(index+1).padStart(2,'0')}}</span>
      <span><strong>{{point.title}}</strong><small>{{status(point)}}</small></span>
    </button>
  </nav>
</template>
<style scoped>
.knowledge-route{display:flex;gap:8px;flex-wrap:wrap;border-bottom:1px solid var(--line);padding-bottom:16px}
button{display:flex;align-items:center;gap:12px;padding:12px 16px;border:1px solid transparent;border-radius:var(--radius-md);background:transparent;color:var(--text);text-align:left;cursor:pointer;font:inherit}
button:hover{background:var(--surface-subtle)}button[aria-current]{border-color:var(--text);background:var(--surface)}
.route-number{font:12px var(--font-mono);color:var(--text-muted)}strong{font-size:14px;font-weight:550}small{display:block;margin-top:5px;font-size:11px;color:var(--text-muted)}
button:focus-visible{outline:2px solid var(--text);outline-offset:3px}
@media(max-width:600px){.knowledge-route{display:grid;grid-template-columns:1fr 1fr}button{padding:10px;min-width:0}strong{overflow-wrap:anywhere}}
</style>
