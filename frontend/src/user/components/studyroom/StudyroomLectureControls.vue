<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue';
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
  <div class="lecture-buttons"><button type="button" aria-label="上一句" :disabled="!active||!plan||view.index===0" @click="player?.seek(view.index-1)">←</button><button type="button" class="lecture-play" :disabled="!plan?.cues.length||loading||!active" @click="view.state==='playing'?pause():player?.play()">{{view.state==='playing'?'暂停讲授':(view.state==='ended'||view.state==='waiting-check')?'重新讲授':'开始讲授'}}</button><button type="button" aria-label="下一句" :disabled="!active||!plan||view.index>=plan.cues.length-1" @click="player?.seek(view.index+1)">→</button><span class="lecture-position">{{loading?'准备讲述脚本…':plan?`${view.index+1} / ${plan.cues.length}`:'讲述未就绪'}}</span><button type="button" :aria-pressed="view.muted" @click="player?.configure(!view.muted,view.rate)">{{view.muted?'静音字幕':'语音讲授'}}</button><select aria-label="讲授速度" :value="view.rate" @change="player?.configure(view.muted,Number(($event.target as HTMLSelectElement).value))"><option :value="0.75">0.75×</option><option :value="1">1×</option><option :value="1.25">1.25×</option><option :value="1.5">1.5×</option><option :value="2">2×</option></select></div>
  <p v-if="cue" class="lecture-caption" aria-label="当前讲述字幕"><span>{{view.state==='waiting-check'?'等待作答':view.state==='ended'?'本段讲述结束':view.state==='playing'?'正在讲述':'待讲述'}}</span>{{cue.text}}</p>
  <p v-if="error" class="lecture-notice" role="status">{{error}} <button type="button" @click="reload">重试讲述</button></p><p v-else-if="view.notice" class="lecture-notice" role="status">{{view.notice}}</p>
 </section>
</template>
<style scoped>
.lecture-controls{flex-shrink:0;min-width:0;padding:10px 0 12px;border-bottom:1px solid var(--line);margin-bottom:12px}.lecture-buttons{display:flex;align-items:center;gap:6px;flex-wrap:wrap}.lecture-buttons button,.lecture-buttons select,.lecture-notice button{font:inherit;font-size:12px;border:1px solid var(--line);border-radius:99px;background:var(--surface);color:var(--text);padding:7px 11px;cursor:pointer}.lecture-buttons .lecture-play{background:var(--text);color:var(--surface)}.lecture-position{font:11px var(--font-mono);color:var(--text-muted);margin-right:auto}.lecture-caption{font-size:14px;line-height:1.75;max-height:64px;overflow:auto;margin:10px 0 0;overscroll-behavior:contain;white-space:pre-wrap}.lecture-caption span{color:var(--text-muted);font-size:11px;margin-right:10px}.lecture-notice{font-size:11px;color:var(--text-muted);line-height:1.7;margin:6px 0 0;max-height:40px;overflow:auto}button:disabled{opacity:.4;cursor:default}button:focus-visible,select:focus-visible{outline:2px solid var(--text);outline-offset:2px}@media(max-width:600px){.lecture-controls{padding:5px 0 8px;margin-bottom:8px}.lecture-buttons{gap:4px}.lecture-buttons button,.lecture-buttons select{padding:6px 9px}.lecture-caption{font-size:13px;max-height:46px}.lecture-position{margin-right:0}}@media(max-height:600px){.lecture-caption{max-height:28px;margin-top:5px}.lecture-controls{margin-bottom:6px;padding:3px 0 6px}}
</style>
<style scoped>
.lecture-controls{padding-top:0;padding-bottom:8px;margin-bottom:8px}.lecture-caption{margin-top:6px}
</style>
<style scoped>
@media(max-height:600px){.lecture-controls{margin:0 0 5px;padding:0;border:0}.lecture-caption,.lecture-notice{display:none}.lecture-buttons{gap:4px;flex-wrap:nowrap;overflow:auto}.lecture-buttons button,.lecture-buttons select{font-size:11px;padding:4px 8px;white-space:nowrap}.lecture-position{white-space:nowrap}}
</style>
