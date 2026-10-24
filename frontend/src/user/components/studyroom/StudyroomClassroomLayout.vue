<script setup lang="ts">
defineProps<{questionOpen:boolean;navigationOpen:boolean;notesOpen?:boolean;fullscreen?:boolean}>();
</script>
<template>
 <div class="classroom-layout" :class="{'question-open':questionOpen||notesOpen,'navigation-open':navigationOpen,'is-fullscreen':fullscreen}">
  <aside class="classroom-navigation" aria-label="知识点导航" :aria-hidden="!navigationOpen" :inert="!navigationOpen">
   <div class="classroom-brand"><slot name="brand"/></div>
   <div class="classroom-directory-scroll"><slot name="navigation"/></div>
   <div class="classroom-progress"><slot name="progress"/></div>
  </aside>
  <div class="classroom-workspace">
   <div class="classroom-workspace-header"><slot name="header"/></div>
   <div class="classroom-content">
    <section class="classroom-material" aria-label="当前学习材料"><slot/></section>
    <Transition name="classroom-drawer"><aside v-show="questionOpen" class="classroom-question" :aria-hidden="!questionOpen" :inert="!questionOpen" aria-label="课堂问答"><slot name="question"/></aside></Transition>
    <Transition name="classroom-drawer"><aside v-show="notesOpen&&!questionOpen" class="classroom-question classroom-notes" :aria-hidden="!notesOpen||questionOpen" :inert="!notesOpen||questionOpen" aria-label="完整讲述"><slot name="notes"/></aside></Transition>
   </div>
  </div>
 </div>
</template>
<style scoped>
.classroom-layout{--rail-width:0px;--notes-width:0px;display:grid;grid-template-columns:var(--rail-width) minmax(0,1fr);flex:1;min-height:0;min-width:0;height:100%;position:relative;transition:grid-template-columns 260ms ease}
.classroom-layout.navigation-open{--rail-width:236px}.classroom-layout.question-open{--notes-width:330px}
.classroom-navigation{display:flex;flex-direction:column;grid-column:1;grid-row:1;min-height:0;min-width:0;overflow:hidden;background:var(--surface);border-right:1px solid var(--line);opacity:0;visibility:hidden;transform:translateX(-12px);transition:opacity 180ms ease,transform 260ms ease}
.navigation-open .classroom-navigation{opacity:1;visibility:visible;transform:none;transition-delay:0s}
.classroom-brand{height:76px;min-height:76px;padding:0 14px;display:flex;align-items:center;justify-content:space-between;gap:8px;white-space:nowrap}
.classroom-directory-scroll{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;scrollbar-width:thin}
.classroom-progress{padding:14px 16px;border-top:1px solid var(--line);font-size:11px;line-height:1.5;color:var(--text-muted);white-space:nowrap;min-height:45px}
.classroom-workspace{grid-column:2;grid-row:1;display:flex;flex-direction:column;min-width:0;min-height:0}
.classroom-workspace-header{min-width:0;flex-shrink:0}
.classroom-content{position:relative;display:grid;grid-template-columns:minmax(0,1fr) var(--notes-width);grid-template-rows:minmax(0,1fr);gap:0;flex:1;min-height:0;min-width:0;padding:0 18px 14px;transition:grid-template-columns 260ms ease,gap 260ms ease}
.question-open .classroom-content{gap:14px}
.classroom-material{grid-column:1;grid-row:1;display:flex;flex-direction:column;min-height:0;min-width:0;overflow:hidden}
.classroom-question{grid-column:2;grid-row:1;min-width:0;min-height:0;overflow:hidden}
.classroom-drawer-enter-active,.classroom-drawer-leave-active{transition:opacity 180ms ease,transform 220ms ease}.classroom-drawer-enter-from,.classroom-drawer-leave-to{opacity:0;transform:translateX(8px)}
@media(max-width:1100px){.classroom-layout.navigation-open{--rail-width:210px}.classroom-layout.question-open{--notes-width:300px}.classroom-content{padding:0 12px 12px}.question-open .classroom-content{gap:10px}.classroom-brand{padding:0 10px}}
@media(max-width:900px){
 .classroom-layout.navigation-open{--rail-width:0px}.classroom-navigation{position:absolute;inset:0 auto 0 0;width:min(260px,85vw);z-index:35;box-shadow:8px 0 30px color-mix(in srgb,var(--text) 12%,transparent)}
 .classroom-layout.question-open{--notes-width:0px}.question-open .classroom-content{gap:0}.classroom-question{position:absolute;inset:0 12px 12px auto;width:min(360px,calc(100% - 24px));z-index:30;border-radius:16px;background:var(--surface);box-shadow:-12px 0 40px color-mix(in srgb,var(--text) 12%,transparent)}
}
@media(max-width:600px){.classroom-content{padding:0 8px 8px}.classroom-brand{height:66px;min-height:66px}.classroom-question{position:fixed;inset:auto 0 0;width:100%;height:min(680px,86dvh);border-radius:22px 22px 0 0;z-index:40;box-shadow:0 -16px 60px color-mix(in srgb,var(--text) 12%,transparent)}}
@media(max-height:600px){.classroom-brand{height:62px;min-height:62px}.classroom-content{padding-bottom:8px}.classroom-progress{min-height:35px;padding-block:9px}.classroom-question{position:fixed;inset:8px 8px 8px auto;height:calc(100dvh - 16px);width:min(360px,calc(100% - 16px));z-index:40;border-radius:18px;background:var(--surface);box-shadow:-12px 0 40px color-mix(in srgb,var(--text) 12%,transparent)}}
.is-fullscreen .classroom-workspace-header{display:none}.is-fullscreen .classroom-content{padding:8px}.is-fullscreen,.is-fullscreen .classroom-content,.is-fullscreen .classroom-navigation,.is-fullscreen .classroom-question{transition:none}
@media(prefers-reduced-motion:reduce){.classroom-layout,.classroom-content,.classroom-navigation,.classroom-question,.classroom-drawer-enter-active,.classroom-drawer-leave-active{transition:none;transform:none}}
</style>
