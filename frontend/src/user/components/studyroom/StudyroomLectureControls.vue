<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue';
import {Play,Pause,Volume2,VolumeX} from '@lucide/vue';
import AiBall from '../../../shared/components/AiBall.vue';
import {lectureApi} from '../../lecture-runtime';
import {LecturePlayer,browserLectureVoice} from '../../lecture-player';
import type {LectureCue,LecturePlan} from '../../lecture-types';
const props=defineProps<{runId:string;fragmentId:string;revision:number;active:boolean;autoStart?:boolean;applyCue:(cue:LectureCue,signal:AbortSignal)=>Promise<boolean>}>();
const emit=defineEmits<{plan:[value:LecturePlan];finished:[]}>();
const plan=ref<LecturePlan>();const error=ref('');const loading=ref(false);const tick=ref(0);
let player:LecturePlayer|undefined;let controller:AbortController|undefined;let epoch=0;
let finishedSent=false;let automaticStarted=false;
const view=computed(()=>{tick.value;return {index:player?.index??0,state:player?.state??'idle',muted:player?.muted??false,rate:player?.rate??1,notice:player?.notice??''};});
const cue=computed(()=>plan.value?.cues[view.value.index]);
function pause(){player?.pause();}
async function reload(){
 const version=++epoch;pause();controller?.abort();controller=new AbortController();loading.value=true;error.value='';
 try{
  const loaded=await lectureApi.get(props.runId,props.fragmentId,props.revision,controller.signal);if(version!==epoch)return;
  player?.dispose();plan.value=loaded;emit('plan',loaded);
  const key=`structify.lecture.${props.runId}.${props.fragmentId}.${props.revision}.${loaded.fingerprint}`;
  player=new LecturePlayer(loaded.cues,props.applyCue,browserLectureVoice(),()=>{tick.value++;try{localStorage.setItem(key,String(player?.index??0));localStorage.setItem('structify.lecture.preferences',JSON.stringify({muted:player?.muted,rate:player?.rate}));}catch{}if(player?.state==='playing')finishedSent=false;if(player?.state==='ended'&&!finishedSent){finishedSent=true;emit('finished');}});
  try{const preference=JSON.parse(localStorage.getItem('structify.lecture.preferences')??'{}');player.muted=preference.muted===true;if([.75,1,1.25,1.5,2].includes(preference.rate))player.rate=preference.rate;}catch{}
  try{const index=Number(localStorage.getItem(key));if(Number.isInteger(index)&&index>=0&&index<loaded.cues.length)player.index=index;}catch{}
  tick.value++;
 }catch(cause){if(version===epoch&&!controller.signal.aborted)error.value=cause instanceof Error?cause.message:'讲述脚本暂不可用';}
 finally{if(version===epoch)loading.value=false;}
}
watch(()=>[props.runId,props.fragmentId,props.revision],()=>void reload(),{immediate:true});
watch(()=>props.active,active=>{if(!active)pause();});
watch(()=>[props.active,props.autoStart,plan.value?.fingerprint],()=>{if(props.active&&props.autoStart&&player&&!automaticStarted){automaticStarted=true;player.index=0;void player.play();}});
function hidden(){if(document.hidden)pause();}document.addEventListener('visibilitychange',hidden);
onBeforeUnmount(()=>{epoch++;controller?.abort();player?.dispose();document.removeEventListener('visibilitychange',hidden);});
defineExpose({pause,reload});
</script>
<template>
 <section class="lecture-controls" aria-label="顺序讲授">

  <div class="lecture-buttons"><div class="toolbar-start"><slot name="before"/></div><div class="toolbar-center"><button type="button" :aria-label="view.muted?'开启语音讲授':'静音讲授'" :title="view.muted?'开启语音讲授':'静音讲授'" :aria-pressed="view.muted" @click="player?.configure(!view.muted,view.rate)"><VolumeX v-if="view.muted" :size="18"/><Volume2 v-else :size="18"/></button><button type="button" aria-label="讲授速度" title="切换讲授速度" @click="player?.configure(view.muted,[.75,1,1.25,1.5,2][([.75,1,1.25,1.5,2].indexOf(view.rate)+1)%5]!)">{{view.rate}}×</button><slot name="previous"/><button type="button" :aria-label="view.state==='playing'?'暂停讲授':'开始讲授'" :title="view.state==='playing'?'暂停讲授':'开始讲授'" class="lecture-play" :disabled="!plan?.cues.length||loading||!active" @click="view.state==='playing'?pause():player?.play()"><Pause v-if="view.state==='playing'" :size="18"/><Play v-else :size="18"/></button><slot name="after-play"/></div><div class="toolbar-end"><slot name="tools"/></div></div>
  <div class="lecture-narration">
   <AiBall size="message" :follow-pointer="false"/>
   <div class="narration-scroll">
    <p class="lecture-caption" aria-label="当前讲述字幕"><span>{{view.state==='waiting-check'?'等待作答':view.state==='ended'?'本段讲述结束':view.state==='playing'?'正在讲述':'当前讲述'}}</span>{{cue?.text||(loading?'正在准备讲述…':error?'讲述暂不可用，可以继续查看材料。':'点击播放，开始当前讲述。')}}</p>
    <p v-if="error" class="lecture-notice" role="status">{{error}} <button type="button" @click="reload">重试讲述</button></p><p v-else-if="view.notice" class="lecture-notice" role="status">{{view.notice}}</p>
   </div>
  </div>
 </section>
</template>
<style scoped>
.lecture-controls{flex-shrink:0;min-width:0;display:flex;flex-direction:column;margin:0;padding:0;color:var(--text)}
.lecture-buttons{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);gap:8px;align-items:center;padding:10px 16px;min-height:60px}
.toolbar-start,.toolbar-center,.toolbar-end{display:flex;align-items:center;gap:6px;min-width:0}.toolbar-end{justify-content:flex-end}.toolbar-center{justify-content:center}
.lecture-buttons :deep(button){font:inherit;line-height:1;display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;width:36px;height:36px;min-width:36px;min-height:36px;padding:0;border:1px solid var(--line);border-radius:9px;background:var(--surface);color:var(--text);cursor:pointer;transition:background 150ms ease,transform 150ms ease}
.lecture-buttons :deep(svg){width:18px;height:18px;stroke-width:1.7;flex-shrink:0}.lecture-buttons button[aria-label='讲授速度']{font-size:12px;font-variant-numeric:tabular-nums}.lecture-buttons .lecture-play{background:var(--text);color:var(--surface);border-color:var(--text)}.lecture-buttons :deep(button:hover:not(:disabled)){background:var(--surface-subtle)}.lecture-buttons .lecture-play:hover:not(:disabled){background:color-mix(in srgb,var(--text) 85%,var(--surface))}.lecture-buttons :deep(button:active:not(:disabled)){transform:scale(.94)}.lecture-buttons :deep(button:disabled){opacity:.4;cursor:default}.lecture-buttons :deep(button:focus-visible){outline:2px solid var(--text);outline-offset:2px}
.lecture-narration{height:116px;min-height:116px;display:flex;align-items:flex-start;gap:14px;border-top:1px solid var(--line);background:color-mix(in srgb,var(--surface) 75%,var(--surface-subtle));padding:18px 22px;box-sizing:border-box}.lecture-narration :deep(.ai-ball){--ai-ball-size:32px;flex-shrink:0;margin-top:2px}.narration-scroll{flex:1;min-width:0;min-height:0;max-height:100%;overflow:auto;overscroll-behavior:contain;scrollbar-width:thin}.lecture-caption{font-size:15px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere;margin:0}.lecture-caption span{display:block;font-size:11px;color:var(--text-muted);line-height:1.5;margin-bottom:4px}.lecture-notice{font-size:12px;color:var(--text-muted);line-height:1.7;margin:5px 0 0}.lecture-notice button{font:inherit;color:var(--text);background:var(--surface);border:1px solid var(--line);border-radius:6px;padding:4px 7px;cursor:pointer}.lecture-notice button:focus-visible{outline:2px solid var(--text);outline-offset:2px}
@media(max-width:1100px){.lecture-buttons{padding-inline:11px;gap:5px}.toolbar-start,.toolbar-center,.toolbar-end{gap:4px}.lecture-buttons :deep(button){width:32px;min-width:32px;height:34px;min-height:34px}}
@media(max-width:650px){.lecture-buttons{grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:8px;padding:9px 10px}.toolbar-start{grid-column:1;grid-row:1}.toolbar-end{grid-column:2;grid-row:1}.toolbar-center{grid-column:1/-1;grid-row:2}.lecture-buttons :deep(button){width:34px;min-width:34px;height:34px;min-height:34px}.lecture-narration{height:104px;min-height:104px;padding:14px;gap:10px}.lecture-caption{font-size:13px}}
@media(max-height:600px){.lecture-narration{height:78px;min-height:78px;padding:10px 14px}.lecture-caption{font-size:12px;line-height:1.7}.lecture-caption span{font-size:10px;margin-bottom:2px}.lecture-buttons{padding-block:6px;min-height:46px}}
@container fragment-reader (max-width:520px){.lecture-buttons{grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:8px;padding:9px 10px}.toolbar-start{grid-column:1;grid-row:1}.toolbar-end{grid-column:2;grid-row:1}.toolbar-center{grid-column:1/-1;grid-row:2}}
@media(prefers-reduced-motion:reduce){.lecture-buttons :deep(button){transition:none}.lecture-buttons :deep(button:active:not(:disabled)){transform:none}}
</style>
