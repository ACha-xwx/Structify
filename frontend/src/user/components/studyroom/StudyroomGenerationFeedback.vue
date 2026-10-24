<script setup lang="ts">
import {computed} from 'vue';
const emit=defineEmits<{review:[]}>();
function reviewPaper(event:MouseEvent){if(event.target instanceof Element&&event.target.closest('button'))return;emit('review');}
const props=withDefaults(defineProps<{phase?:string;preparing?:boolean;ready?:boolean;title?:string;units?:Array<{title:string;objective:string}>;connectionError?:string}>(),{phase:'QUEUED',units:()=>[]});
const stages=[{id:'QUEUED',label:'等待开始'},{id:'RETRIEVING',label:'查阅课程资料'},{id:'PLANNING',label:'编排学习路线'},{id:'VALIDATING',label:'检查大纲'}];
const preparationLabels:Record<string,string>={PREPARING_RESOURCES:'正在整理首段课程材料',GENERATING_CONTENT:'正在编写首段讲解',PUBLISHING:'正在发布通过检查的首段讲解'};
const label=computed(()=>props.ready?'大纲已准备好':props.preparing?(preparationLabels[props.phase]??'正在准备首段讲解'):stages.find(stage=>stage.id===props.phase)?.label??'正在准备学习内容');
const index=computed(()=>props.ready?stages.length-1:Math.max(0,stages.findIndex(stage=>stage.id===props.phase)));
</script>
<template>
 <section class="generation-feedback" :class="{'is-ready':ready}" aria-label="课堂生成进度" :aria-busy="!ready">
  <ol v-if="!preparing" class="generation-phases" aria-label="生成阶段"><li v-for="(stage,i) in stages" :key="stage.id" :class="{current:i===index,done:i<index}" :aria-current="i===index?'step':undefined"><span class="phase-dot" aria-hidden="true"/><span class="sr-only">{{stage.label}}</span></li></ol>
  <div class="generation-stage">
   <div class="generation-visual" @click="reviewPaper">
    <Transition name="stage-change" type="transition" mode="out-in">
     <div aria-hidden="true" :key="preparing?'preparing':phase" class="visual-sheet" :class="{'is-checking':phase==='VALIDATING'||phase==='PUBLISHING'}">
      <div class="sheet-toolbar"><span/><b/></div>
      <div class="paper-timeline"><template v-if="units.length"><div v-for="(unit,i) in units.slice(0,4)" :key="i" class="paper-point"><strong>{{i+1}}. {{unit.title}}</strong><p>{{unit.objective||'…'}}</p></div><small v-if="units.length>4">+{{units.length-4}}</small></template><template v-else><div v-for="(width,i) in [58,78,48,68]" :key="i" class="paper-point paper-skeleton"><i :style="{width:width+'%'}"/><i/></div></template></div>

     </div>
    </Transition>
    <div class="sheet-review-action"><slot/></div>
   </div>
   <div class="generation-description"><span v-if="units.length&&!preparing" class="sr-only">已编排 {{units.length}} 个知识点</span><p class="generation-eyebrow">{{ready?'学习路线已就绪':preparing?'即将开始学习':'正在为你规划'}}</p><h3 role="status" aria-live="polite">{{label}}</h3><p>{{ready?(title||'打开审阅，可直接调整知识点与学习顺序。'):preparing?'首段通过检查后自动进入课堂。':'知识点逐项生成，随时展开审阅。'}}</p></div>
  </div>
  <p v-if="connectionError" class="generation-reconnecting" role="status">连接暂时中断，正在恢复已有进度。</p>
 </section>
</template>
<style scoped>
.sheet-review-action{position:absolute;bottom:12px;z-index:1}.is-ready .visual-sheet,.is-ready .sheet-scan,.is-ready .sheet-row{animation:none}.is-ready .generation-eyebrow{color:var(--text)}
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

/* StreamingOutlineVisualizer: tilted 176 × 208 paper with timeline and bottom affordance. */
.generation-visual{width:220px;height:228px;overflow:visible;contain:layout}.visual-sheet{view-transition-name:outline-review-surface;width:176px;height:208px;padding:12px;border-radius:16px;transform:rotate(-3deg);animation:none;transition:transform .28s cubic-bezier(.22,1,.36,1),box-shadow .28s}.generation-visual:hover .visual-sheet,.generation-visual:focus-within .visual-sheet{transform:translateY(-3px) scale(1.04) rotate(0);box-shadow:0 15px 35px #0002}.sheet-toolbar{justify-content:space-between;border:0;margin-bottom:13px;padding:0}.sheet-toolbar>span{width:48px;height:6px;border-radius:9px}.sheet-toolbar b{width:8px;height:8px;border-radius:50%;background:var(--text);animation:row-pulse 1.1s infinite}.paper-timeline{position:relative;margin-left:5px;padding-left:15px;border-left:1px solid var(--line);height:168px;overflow:hidden;text-align:left}.paper-point{position:relative;margin-bottom:13px}.paper-point:before{content:'';position:absolute;left:-19px;top:2px;width:7px;height:7px;border:1px solid var(--text-muted);border-radius:50%;background:var(--surface)}.paper-point strong{display:block;font-size:9px;line-height:1.3;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.paper-point p{font-size:7px;line-height:1.4;color:var(--text-muted);margin:4px 0 0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.paper-timeline small{font-size:8px;color:var(--text-muted)}.paper-skeleton i{display:block;height:7px;background:var(--line);border-radius:6px;animation:row-pulse 1.2s infinite}.paper-skeleton i+i{width:66%;height:4px;margin-top:5px}.visual-sheet:after{content:'';position:absolute;bottom:0;left:0;right:0;height:42px;background:linear-gradient(transparent,var(--surface))}.generation-stage{gap:20px}.sheet-review-action :deep(button){font-size:10px!important;padding:6px 12px!important;background:var(--text)!important;color:var(--surface)!important}.generation-feedback{padding:26px}.generation-description h3{font-size:24px}.generation-wait{display:none}
@media(max-height:650px){.generation-visual{width:160px;height:185px}.visual-sheet{transform:rotate(-3deg) scale(.82)}.generation-stage{gap:14px}.generation-visual:hover .visual-sheet{transform:scale(.85) rotate(0)}}
@media(prefers-reduced-motion:reduce){.visual-sheet,.generation-visual:hover .visual-sheet{transform:none;transition:none}.sheet-toolbar b,.paper-skeleton i{animation:none}}
</style>
