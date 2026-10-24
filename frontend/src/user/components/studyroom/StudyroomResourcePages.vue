<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,ref,shallowReactive,watch} from 'vue';
import type {StudyroomStep,StudyroomFollowUpContext} from '../../../shared/types/studyroom';
import type {MaterialPosition,TeachingAction} from '../../../shared/types/classroom-experience';
import type {LectureCue,LectureRegion} from '../../lecture-types';
import type {ClassroomMaterialAdapter} from '../../classroom-materials';
import type {DsvpSimulationResponse} from '../../../shared/types/animation';
import {classroomPages} from '../../classroom-pages';
import {userApi} from '../../runtime';
import StudyroomSlideScene from './StudyroomSlideScene.vue';
import StudyroomCodeScene from './StudyroomCodeScene.vue';
import AnimationPlayer from '../../../animation/AnimationPlayer.vue';
const props=defineProps<{step:StudyroomStep;revision:number;chapterId?:string;active:boolean;regions?:LectureRegion[];hasChecks?:boolean}>();
const emit=defineEmits<{context:[value:StudyroomFollowUpContext];navigate:[];page:[id:string,index:number,count:number];saveRegion:[value:LectureRegion];removeRegion:[id:string]}>();
const pages=computed(()=>{const base=classroomPages(props.step,props.revision);return props.hasChecks?[...base,{id:`${props.step.id}:${props.revision}:quiz`,title:'小测',kind:'QUIZ' as const,resourceId:undefined,scene:base[0]!.scene}]:base;});
const selected=ref('');const current=computed(()=>pages.value.find(page=>page.id===selected.value)??pages.value[0]!);
const index=computed(()=>pages.value.findIndex(page=>page.id===current.value.id));
const visited=ref(new Set<string>());const adapters=new Map<string,ClassroomMaterialAdapter>();
const animations=shallowReactive(new Map<string,DsvpSimulationResponse>());const errors=ref<Record<string,string>>({});
const textScroll=ref<HTMLElement>();const quizScroll=ref<HTMLElement>();
const currentScroll=()=>current.value.kind==='QUIZ'?quizScroll.value:textScroll.value;
let epoch=0;let navigationEpoch=0;let disposed=false;
function adapter(id:string,value:unknown){if(value)adapters.set(id,value as ClassroomMaterialAdapter);else adapters.delete(id);}
function context(value:Partial<StudyroomFollowUpContext>={}){if(!props.active)return;emit('context',{stepId:props.step.id,sceneId:current.value.scene.id,...(current.value.resourceId?{resourceId:current.value.resourceId}:{}),...value});}
function pause(){for(const adapter of adapters.values())adapter.pause();}
function selectPage(id:string,manual=true){if(!pages.value.some(page=>page.id===id))return false;navigationEpoch++;if(manual)emit('navigate');pause();selected.value=id;return true;}
function manualInteraction(){navigationEpoch++;emit('navigate');pause();}
function navigate(delta:number){const page=pages.value[index.value+delta];return page?selectPage(page.id):false;}
async function loadAnimation(){
 const page=current.value;if(page.kind!=='ANIMATION'||!props.active||animations.has(page.id))return;
 const version=epoch;errors.value[page.id]='';
 try{
  const resource=props.step.resources.find(resource=>resource.id===page.resourceId&&resource.type==='ANIMATION');
  if(!resource?.available)throw new Error('本页动画暂不可用。');
  const args=resource.parameters&&typeof resource.parameters==='object'&&!Array.isArray(resource.parameters)?resource.parameters as Record<string,unknown>:undefined;
  const planned=await userApi.planAnimation({capability:resource.id,arguments:args,sourceRef:props.step.id});
  if(disposed||version!==epoch)return;if(!planned.toolRequest)throw new Error(planned.error||'动画暂不可用。');
  const result=await userApi.simulateAnimation(planned.toolRequest.request);
  if(!disposed&&version===epoch)animations.set(page.id,result);
 }catch(cause){if(!disposed&&version===epoch)errors.value[page.id]=cause instanceof Error?cause.message:'动画暂不可用。';}
}
watch(()=>[current.value.id,props.active] as const,()=>{epoch++;pause();visited.value.add(current.value.id);context();emit('page',current.value.id,index.value,pages.value.length);void loadAnimation();},{immediate:true});
onBeforeUnmount(()=>{disposed=true;epoch++;pause();});
async function waitAndApply(id:string,action:TeachingAction,signal?:AbortSignal){
 const navigation=navigationEpoch;await nextTick();const version=epoch;
 for(let attempt=0;attempt<125;attempt++){
  if(disposed||!props.active||signal?.aborted||navigation!==navigationEpoch||version!==epoch||current.value.id!==id)return false;
  const material=adapters.get(id);if(material?.supports(action))return await material.apply(action);
  await new Promise(resolve=>setTimeout(resolve,40));
 }
 return false;
}
async function apply(action:TeachingAction){const target=pages.value.find(page=>page.scene.id===action.sceneId&&page.resourceId===action.resourceId);if(!target||!props.active||disposed)return false;selectPage(target.id,false);return await waitAndApply(target.id,action);}

defineExpose({pages,index,selectPage,navigate,pause:()=>{navigationEpoch++;pause();},
 capture:():MaterialPosition & {presentationPageId?:string}=>({...((current.value.kind==='TEXT'||current.value.kind==='QUIZ')?{kind:'TEXT' as const,scrollTop:currentScroll()?.scrollTop??0}:adapters.get(current.value.id)?.capture()??{kind:'TEXT' as const,scrollTop:0}),presentationPageId:current.value.id}),
 restore:async(position:MaterialPosition)=>{const pageId=(position as MaterialPosition & {presentationPageId?:string}).presentationPageId;if(pageId)selectPage(pageId,false);await nextTick();const scroll=currentScroll();if(position.kind==='TEXT'&&scroll){scroll.scrollTop=position.scrollTop;}else await adapters.get(current.value.id)?.restore(position);},
 supports:(action:TeachingAction)=>pages.value.some(page=>page.scene.id===action.sceneId&&page.resourceId===action.resourceId),apply,
 lecture:async(cue:LectureCue,signal:AbortSignal)=>{
  if(signal.aborted||!props.active||disposed)return false;
  if(cue.kind==='TEXT')return true;
  const target=cue.kind==='CHECK'?pages.value.find(page=>page.kind==='QUIZ'):pages.value.find(page=>page.scene.id===cue.sceneId&&page.resourceId===cue.resourceId);
  if(!target)return false;selectPage(target.id,false);await nextTick();if(signal.aborted||!props.active||disposed)return false;
  if(!cue.action)return true;
  const action:TeachingAction={id:cue.id,sequence:0,runId:'',fragmentId:'',contentRevision:0,sceneId:target.scene.id,resourceId:target.resourceId!,kind:cue.action,regionId:cue.regionId,startLine:cue.startLine,endLine:cue.endLine,stepIndex:cue.stepIndex};
  return await waitAndApply(target.id,action,signal);
 }
});
</script>
<template>
 <div class="resource-pages" aria-label="课堂展示页">
  <div v-if="pages[0]?.kind==='TEXT'&&visited.has(pages[0].id)" v-show="current.kind==='TEXT'" ref="textScroll" class="text-page-scroll" role="status"><h2>{{step.title}}</h2><p>{{step.orchestration?.objective}}</p><p>讲解内容请展开右侧“完整讲述”查看。</p></div>
  <div v-if="visited.has(`${step.id}:${revision}:quiz`)" v-show="current.kind==='QUIZ'" ref="quizScroll" class="resource-page quiz-page" :class="{'is-current':current.kind==='QUIZ'}"><slot name="checks"/></div>
  <template v-for="page in pages.filter(page=>page.kind!=='TEXT'&&page.kind!=='QUIZ')" :key="page.id">
   <div v-if="visited.has(page.id)" v-show="page.id===current.id" class="resource-page" :class="{'is-current':page.id===current.id,'animation-page':page.kind==='ANIMATION'}" @pointerdown="manualInteraction" @input="manualInteraction">
    <StudyroomSlideScene v-if="page.kind==='PPT'" :ref="value=>adapter(page.id,value)" :scene="page.scene" :resources="step.resources" :regions="regions" bounded single-page @context="page.id===current.id&&context({resourceId:$event})" @interact="manualInteraction" @save-region="emit('saveRegion',$event)" @remove-region="emit('removeRegion',$event)"/>
    <StudyroomCodeScene v-else-if="page.kind==='C_CODE'" :ref="value=>adapter(page.id,value)" :scene="page.scene" :resources="step.resources" :chapter-id="chapterId" @context="page.id===current.id&&context($event)"/>
    <template v-else-if="page.kind==='ANIMATION'"><AnimationPlayer v-if="animations.get(page.id)" :ref="value=>adapter(page.id,value)" :definition="animations.get(page.id)!.animationData" :trace="animations.get(page.id)!.trace" compact/><p v-else role="status">{{errors[page.id]||'正在准备动画…'}} <button v-if="errors[page.id]" type="button" @click="loadAnimation">重试</button></p></template>
   </div>
  </template>
 </div>
</template>
<style scoped>
.resource-pages,.resource-page,.text-page-scroll{flex:1;min-height:0;min-width:0;display:flex;flex-direction:column;overflow:auto;overscroll-behavior:contain}.resource-pages{height:100%}.resource-page{scrollbar-width:thin}.resource-page.is-current{animation:material-enter 180ms ease both}.resource-page :deep(.slide-scene){display:flex;flex-direction:column;flex:1;min-height:0}.resource-page :deep(.studyroom-slide-viewport){flex:1;min-height:0;height:auto;background:transparent;border:0}.resource-page :deep(.slide-canvas){min-height:0}.resource-page :deep(.studyroom__slide){object-fit:contain}.text-page-scroll{justify-content:center;align-items:center;text-align:center;padding:24px;gap:12px}.text-page-scroll h2,.text-page-scroll p{margin:0}.animation-page{padding:20px}.animation-page :deep(.player){width:min(100%,800px);flex-shrink:0;margin:auto}.animation-page>p{margin:auto;text-align:center}.quiz-page{padding:clamp(16px,3vw,36px)}
@keyframes material-enter{from{opacity:.3;transform:translateY(4px)}to{opacity:1;transform:none}}
.animation-page :deep(.cells),.animation-page :deep(.chain){justify-content:center}.animation-page :deep(.player__controls){justify-content:center}
@media(max-width:600px){.animation-page{padding:8px}.quiz-page{padding:18px 10px}}
@media(prefers-reduced-motion:reduce){.resource-page.is-current{animation:none}}
</style>
