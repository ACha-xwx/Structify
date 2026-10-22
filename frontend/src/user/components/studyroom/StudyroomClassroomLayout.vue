<script setup lang="ts">
defineProps<{questionOpen:boolean;navigationOpen:boolean}>();
</script>
<template>
 <div class="classroom-layout" :class="{'question-open':questionOpen,'navigation-open':navigationOpen}">
  <aside v-show="navigationOpen" class="classroom-navigation" aria-label="知识点导航"><slot name="navigation"/></aside>
  <section class="classroom-material" aria-label="当前学习材料"><slot/></section>
  <aside v-show="questionOpen" class="classroom-question" aria-label="课堂问答"><slot name="question"/></aside>
 </div>
</template>
<style scoped>
.classroom-layout{display:grid;grid-template-columns:minmax(0,1fr);gap:16px;flex:1;min-height:0;min-width:0;position:relative}
.classroom-layout.navigation-open{grid-template-columns:200px minmax(0,1fr)}
.classroom-layout.question-open{grid-template-columns:minmax(0,1fr) 330px}
.classroom-layout.navigation-open.question-open{grid-template-columns:190px minmax(0,1fr) 330px}
.classroom-navigation{min-height:0;overflow:auto;overscroll-behavior:contain;min-width:0;padding:8px 2px}
.classroom-material{display:flex;flex-direction:column;min-height:0;min-width:0;overflow:hidden}
.classroom-question{min-width:0;min-height:0;overflow:hidden}
.classroom-navigation :deep(.knowledge-route){display:flex;flex-direction:column;gap:8px}
.classroom-navigation :deep(button){width:100%;text-align:left}
.classroom-navigation :deep(ol){display:grid;grid-template-columns:1fr;gap:8px;padding:0}
.classroom-navigation :deep(li){min-width:0;list-style:none}
@media(max-width:1100px){.classroom-layout.navigation-open.question-open{grid-template-columns:160px minmax(0,1fr) 300px}}
@media(max-width:900px){
 .classroom-layout.navigation-open,.classroom-layout.navigation-open.question-open{grid-template-columns:minmax(0,1fr);grid-template-rows:auto minmax(0,1fr)}
 .classroom-layout.question-open{grid-template-columns:minmax(0,1fr)}
 .classroom-navigation{max-height:114px;padding:0}
 .classroom-navigation :deep(ol){display:flex;overflow-x:auto;margin:0;padding:0 0 8px}
 .classroom-navigation :deep(li){flex:0 0 172px}
 .classroom-navigation :deep(.knowledge-route){display:block}
 .classroom-question{position:absolute;inset:0 0 0 auto;width:min(360px,100%);z-index:30;box-shadow:-12px 0 40px color-mix(in srgb,var(--text) 12%,transparent);border-radius:20px;background:var(--surface)}
}
@media(max-width:600px){.classroom-layout{gap:8px}.classroom-question{position:fixed;inset:auto 0 0;width:100%;height:min(680px,86dvh);z-index:40;border-radius:22px 22px 0 0;box-shadow:0 -16px 60px color-mix(in srgb,var(--text) 12%,transparent)}}
@media(max-height:600px){.classroom-layout .classroom-question{position:fixed;inset:8px 8px 8px auto;height:calc(100dvh - 16px);width:min(360px,calc(100% - 16px));z-index:40;border-radius:18px;background:var(--surface);box-shadow:-12px 0 40px color-mix(in srgb,var(--text) 12%,transparent)}}
</style>
