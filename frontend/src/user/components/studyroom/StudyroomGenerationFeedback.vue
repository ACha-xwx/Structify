<script setup lang="ts">
import {computed,ref} from 'vue';
const props=withDefaults(defineProps<{phase?:string;preparing?:boolean;units?:Array<{title:string;objective:string}>;connectionError?:string}>(),{phase:'QUEUED',units:()=>[]});
const expanded=ref(false);
const stages=[{id:'QUEUED',label:'等待开始'},{id:'RETRIEVING',label:'查阅课程资料'},{id:'PLANNING',label:'编排学习路线'},{id:'VALIDATING',label:'检查大纲'}];
const preparationLabels:Record<string,string>={PREPARING_RESOURCES:'正在整理首段课程材料',GENERATING_CONTENT:'正在编写首段讲解',PUBLISHING:'正在发布通过检查的首段讲解'};
const label=computed(()=>props.preparing?(preparationLabels[props.phase]??'正在准备首段讲解'):stages.find(stage=>stage.id===props.phase)?.label??'正在准备学习内容');
const index=computed(()=>Math.max(0,stages.findIndex(stage=>stage.id===props.phase)));
const preview=computed(()=>expanded.value?props.units:props.units.slice(-3));
const firstIndex=computed(()=>props.units.length-preview.value.length);
</script>
<template>
 <section class="generation-feedback" aria-label="课堂生成进度" :aria-busy="true">
  <ol v-if="!preparing" class="generation-phases" aria-label="生成阶段"><li v-for="(stage,i) in stages" :key="stage.id" :class="{current:i===index,done:i<index}" :aria-current="i===index?'step':undefined"><span class="phase-dot" aria-hidden="true"/><span class="sr-only">{{stage.label}}</span></li></ol>
  <div class="generation-stage">
   <div class="generation-visual" aria-hidden="true">
    <Transition name="stage-change" type="transition" mode="out-in">
     <div :key="preparing?'preparing':phase" class="visual-sheet" :class="{'is-checking':phase==='VALIDATING'||phase==='PUBLISHING'}">
      <div class="sheet-toolbar"><span/><span/><span/></div>
      <div class="sheet-title"/>
      <div v-for="i in 3" :key="i" class="sheet-row" :style="{'--row':i}"><span class="sheet-marker">{{phase==='VALIDATING'?'✓':String(i).padStart(2,'0')}}</span><div><i/><i/></div></div>
      <div class="sheet-scan"/>
     </div>
    </Transition>
   </div>
   <div class="generation-description"><p class="generation-eyebrow">{{preparing?'即将开始学习':'正在为你规划'}}</p><h3 role="status" aria-live="polite">{{label}}</h3><p>{{preparing?'首段通过检查后自动进入课堂。':'知识点逐项生成，检查完成后即可审阅。'}}</p></div>
  </div>
  <div v-if="units.length&&!preparing" class="generation-live">
   <div class="preview-heading"><span>已编排 {{units.length}} 个知识点</span><button v-if="units.length>3" type="button" :aria-expanded="expanded" @click="expanded=!expanded">{{expanded?'收起预览':'查看全部'}}</button><span v-else class="live-indicator" aria-hidden="true">●</span></div>
   <TransitionGroup tag="ol" name="outline-preview" class="generation-preview" :class="{'is-expanded':expanded}" aria-label="正在生成的知识点预览"><li v-for="(unit,i) in preview" :key="firstIndex+i"><span>{{String(firstIndex+i+1).padStart(2,'0')}}</span><h4>{{unit.title}}</h4></li></TransitionGroup>
  </div>
  <div v-else class="generation-wait" aria-hidden="true"><span/><span/><span/></div>
  <p v-if="connectionError" class="generation-reconnecting" role="status">连接暂时中断，正在恢复已有进度。</p>
 </section>
</template>
<style scoped>
.generation-feedback{box-sizing:border-box;min-height:0;flex:1;display:flex;flex-direction:column;align-items:center;justify-content:safe center;padding:18px;overflow:auto;overscroll-behavior:contain}
.generation-phases{display:flex;gap:9px;list-style:none;padding:0;margin:0 0 18px;flex:none}
.phase-dot{display:block;width:7px;height:7px;border-radius:99px;background:var(--line);transform-origin:center;transition:transform .25s ease,opacity .25s ease}
.current .phase-dot{background:var(--text);transform:scaleX(2.5);margin:0 7px}.done .phase-dot{background:var(--text);opacity:.35}
.generation-stage{display:flex;flex-direction:column;align-items:center;text-align:center;flex:none;gap:16px}
.generation-visual{width:220px;height:160px;display:grid;place-items:center;position:relative;contain:layout paint;flex:none}
.visual-sheet{width:170px;height:144px;box-sizing:border-box;border:1px solid var(--line);border-radius:14px;background:var(--surface);padding:12px 14px;position:relative;overflow:hidden;box-shadow:0 8px 20px color-mix(in srgb,var(--text) 7%,transparent);animation:sheet-float 3.6s ease-in-out infinite}
.sheet-toolbar{display:flex;gap:4px;border-bottom:1px solid var(--line);padding-bottom:9px;margin-bottom:12px}.sheet-toolbar>span{height:4px;width:4px;border-radius:50%;background:var(--line)}.sheet-title{height:5px;width:60%;background:var(--text-muted);border-radius:9px;opacity:.4;margin-bottom:12px}
.sheet-row{display:flex;gap:9px;align-items:center;margin:10px 0;animation:row-pulse 2.4s ease-in-out infinite;animation-delay:calc(var(--row)*.2s)}.sheet-marker{font-size:9px;font-variant-numeric:tabular-nums;color:var(--text-muted)}.sheet-row>div{flex:1;display:grid;gap:4px}.sheet-row i{height:4px;background:var(--line);border-radius:9px}.sheet-row i:last-child{width:70%;height:3px}.is-checking .sheet-marker{color:var(--text)}
.sheet-scan{position:absolute;left:0;right:0;top:0;height:50%;background:linear-gradient(transparent,color-mix(in srgb,var(--text) 4%,transparent),transparent);animation:sheet-scan 3s linear infinite}
.generation-description{max-width:380px}.generation-eyebrow{font-size:11px;letter-spacing:.1em;color:var(--text-muted);margin:0 0 6px}.generation-description h3{font-size:23px;line-height:1.4;margin:0 0 8px}.generation-description>p:last-child{font-size:12px;color:var(--text-muted);line-height:1.7;margin:0}
.generation-live{width:min(100%,460px);margin-top:22px;border:1px solid var(--line);border-radius:14px;overflow:hidden;flex:none}.preview-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 14px;font-size:11px;color:var(--text-muted);border-bottom:1px solid var(--line)}.preview-heading button{background:none;border:0;color:var(--text);font:inherit;cursor:pointer;padding:0}.live-indicator{animation:row-pulse 2.4s ease-in-out infinite;font-size:8px}
.generation-preview{list-style:none;margin:0;padding:5px 14px;max-height:120px;overflow:hidden;position:relative}.generation-preview.is-expanded{max-height:160px;overflow-y:auto;overscroll-behavior:contain}.generation-preview li{display:flex;gap:12px;align-items:center;height:34px;min-width:0}.generation-preview li>span{font-size:10px;color:var(--text-muted);font-variant-numeric:tabular-nums}.generation-preview h4{font-size:12px;font-weight:500;margin:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.generation-wait{display:flex;gap:5px;align-items:center;height:26px;margin-top:16px}.generation-wait span{height:4px;width:4px;border-radius:50%;background:var(--text-muted);animation:row-pulse 1.5s ease-in-out infinite}.generation-wait span:nth-child(2){animation-delay:.2s}.generation-wait span:nth-child(3){animation-delay:.4s}.generation-reconnecting{font-size:12px;color:var(--text-muted);margin:12px 0 0}
.stage-change-enter-active,.stage-change-leave-active{transition:opacity .2s ease,transform .2s cubic-bezier(.22,1,.36,1)}.stage-change-enter-from{opacity:0;transform:translateY(8px) scale(.96)}.stage-change-leave-to{opacity:0;transform:translateY(-8px) scale(.96)}
.outline-preview-enter-active{transition:opacity .24s ease,transform .24s cubic-bezier(.22,1,.36,1)}.outline-preview-enter-from{opacity:0;transform:translateY(8px)}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap}
@keyframes sheet-float{50%{transform:translateY(-5px)}}@keyframes row-pulse{50%{opacity:.4}}@keyframes sheet-scan{from{transform:translateY(-100%)}to{transform:translateY(300%)}}
@media(max-height:650px){.generation-feedback{padding:14px}.generation-stage{flex-direction:row;text-align:left;gap:18px}.generation-visual{width:130px;height:115px}.visual-sheet{transform:scale(.72);animation:none;flex:none}.generation-description h3{font-size:19px}.generation-live{margin-top:12px}.generation-phases{margin-bottom:10px}.generation-preview li{height:29px}}
@media(max-width:500px){.generation-feedback{padding:18px}.generation-description h3{font-size:20px}.generation-live{margin-top:16px}}
@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation:none!important;transition:none!important}.current .phase-dot{transform:none;width:22px}}
</style>
