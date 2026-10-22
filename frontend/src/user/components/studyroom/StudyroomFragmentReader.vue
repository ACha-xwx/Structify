<script setup lang="ts">
import {computed,nextTick,ref,watch} from 'vue';
import type {LearningFragment} from '../../../shared/types/outline';
import type {StudyroomStep,StudyroomFollowUpContext} from '../../../shared/types/studyroom';
import type {ClassroomMaterialAdapter} from '../../classroom-materials';
import type {MaterialPosition,TeachingAction} from '../../../shared/types/classroom-experience';
import StudyroomSceneStage from './StudyroomSceneStage.vue';
import StudyroomLectureControls from './StudyroomLectureControls.vue';
import StudyroomCheckPanel from './StudyroomCheckPanel.vue';
import {lectureApi} from '../../lecture-runtime';
import type {LectureCue,LecturePlan,LectureRegion,LectureStageAdapter} from '../../lecture-types';
const props=defineProps<{fragment:LearningFragment;step?:StudyroomStep;chapterId?:string;active:boolean;busy:boolean;runId?:string;unitId?:string;canPrepareChecks?:boolean;lectureBlocked?:boolean;autoStart?:boolean}>();
defineEmits<{retry:[fragment:LearningFragment];regenerate:[fragment:LearningFragment];context:[value:StudyroomFollowUpContext];navigate:[];lectureEnded:[]}>();
const stage=ref<ClassroomMaterialAdapter&LectureStageAdapter>();
const lecture=ref<{pause:()=>void;reload:()=>Promise<void>}>();const plan=ref<LecturePlan>();const regionError=ref('');
const checkActive=ref(false);
async function applyCue(cue:LectureCue,signal:AbortSignal){return await stage.value?.lecture(cue,signal)??false;}
async function saveRegion(region:LectureRegion){if(!props.runId)return;lecture.value?.pause();regionError.value='';try{await lectureApi.save(props.runId,props.fragment.id,props.fragment.contentRevision,region);await lecture.value?.reload();}catch(cause){regionError.value=cause instanceof Error?cause.message:'区域暂未保存，请重试';}}
async function removeRegion(id:string){if(!props.runId)return;lecture.value?.pause();regionError.value='';try{await lectureApi.remove(props.runId,props.fragment.id,props.fragment.contentRevision,id);await lecture.value?.reload();}catch(cause){regionError.value=cause instanceof Error?cause.message:'区域暂未移除';}}
const explanation=ref<HTMLElement>();
const hasMaterial=computed(()=>!!props.step?.orchestration&&[props.step.orchestration.primary,...props.step.orchestration.supplementary].some(scene=>scene.kind!=='TEXT'));
const panel=ref<'material'|'explanation'>('explanation');
watch(hasMaterial,available=>{panel.value=available?'material':'explanation';},{immediate:true});
function readExplanation(){stage.value?.pause();panel.value='explanation';}
defineExpose({
 capture:():MaterialPosition=>panel.value==='explanation'?{kind:'TEXT',scrollTop:explanation.value?.scrollTop??0}:stage.value?.capture()??{kind:'TEXT',scrollTop:0},
 pause:()=>{lecture.value?.pause();stage.value?.pause();},
 restore:async(position:MaterialPosition)=>{if(position.kind==='TEXT'){panel.value='explanation';await nextTick();if(explanation.value)explanation.value.scrollTop=position.scrollTop;}else{panel.value='material';await nextTick();await stage.value?.restore(position);}},
 supports:(action:TeachingAction)=>stage.value?.supports(action)??false,
 apply:async(action:TeachingAction)=>{panel.value='material';await nextTick();return stage.value?.apply(action)??false;},
});
</script>
<template>
 <section class="fragment-reader" :class="{'has-lecture':!!runId}" :aria-label="step?'知识点讲解':'准备当前讲解'">
  <template v-if="step">
   <header class="reader-heading"><h2>{{step.title}}</h2></header>
   <StudyroomLectureControls v-if="runId" ref="lecture" :run-id="runId" :fragment-id="fragment.id" :revision="fragment.contentRevision" :active="active&&!lectureBlocked" :auto-start="autoStart" :apply-cue="applyCue" @plan="plan=$event" @finished="$emit('lectureEnded')"/>
   <p v-if="regionError" class="fragment-status" role="alert">{{regionError}}</p>
   <div class="reader-stage"><StudyroomSceneStage ref="stage" :step="step" :chapter-id="chapterId" :active="active" :explanation-open="panel==='explanation'" :has-checks="!!runId&&!!unitId" :regions="plan?.regions" prefer-materials @check-active="checkActive=$event" @explanation="readExplanation" @material="panel='material'" @navigate="lecture?.pause();$emit('navigate')" @save-region="saveRegion" @remove-region="removeRegion" @context="$emit('context',$event)">
    <template #explanation><div ref="explanation" class="explanation-scroll" tabindex="0" aria-label="完整讲解正文"><p class="explanation">{{step.explanation}}</p></div></template>
    <template #checks><StudyroomCheckPanel v-if="runId&&unitId" :run-id="runId" :unit-id="unitId" :can-prepare="!!canPrepareChecks" :active="active&&checkActive"/></template>
   </StudyroomSceneStage></div>
  </template>
  <div v-if="fragment.status==='FAILED'" class="fragment-status" role="alert"><p>{{fragment.error?.message||'这段讲解暂未准备完成，已就绪的内容仍可阅读。'}}</p><button v-if="fragment.error?.retryable!==false" :disabled="busy" type="button" @click="$emit('retry',fragment)">重新准备这段内容</button><button :disabled="busy" type="button" @click="$emit('regenerate',fragment)">重新生成该片段，可能使用模型额度</button></div>
  <p v-else-if="!step" class="fragment-status" role="status">{{fragment.status==='PENDING'?'即将准备当前讲解…':'正在准备讲解与所需材料…'}}内容就绪后会在这里显示。</p>
  <p v-else-if="fragment.status!=='READY'" class="fragment-status" role="status">正在准备新的内容修订，当前内容可以继续阅读。</p>
 </section>
</template>
<style scoped>
.fragment-reader{display:flex;flex-direction:column;flex:1;min-height:0;min-width:0;padding:20px;background:var(--surface);border:1px solid var(--line);border-radius:var(--radius-lg);overflow:hidden}
.reader-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-shrink:0;margin-bottom:16px}
h2{font-size:19px;font-weight:550;margin:0;line-height:1.4;min-width:0}
.reader-switch{display:flex;flex-shrink:0;gap:4px;padding:3px;border:1px solid var(--line);border-radius:99px}.reader-switch button{border:0;border-radius:99px;padding:7px 12px;margin:0;font-size:12px;color:var(--text-muted)}.reader-switch button[aria-pressed=true]{background:var(--text);color:var(--surface)}
.explanation-scroll,.reader-stage{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;scrollbar-gutter:stable}
.explanation{max-width:72ch;font-size:16px;line-height:1.95;white-space:pre-wrap;margin:0 auto;padding:8px 8px 28px}
.reader-stage{display:flex;flex-direction:column}.reader-stage :deep(.scene-stage){flex:1;min-height:0}
.fragment-status{flex-shrink:0;max-height:40%;overflow:auto;line-height:1.75;font-size:13px;color:var(--text-muted)}
button{font:inherit;color:var(--text);background:var(--surface);border:1px solid var(--line);border-radius:var(--radius-md);padding:10px 14px;cursor:pointer;margin-right:10px}button:disabled{opacity:.5;cursor:default}button:focus-visible,.explanation-scroll:focus-visible{outline:2px solid var(--text);outline-offset:-2px}
@media(max-width:600px){.fragment-reader{padding:14px}.reader-heading{align-items:flex-start;flex-direction:column;gap:10px;margin-bottom:10px}h2{font-size:18px}.explanation{font-size:15px;padding:0 2px 20px}.reader-switch button{padding:6px 12px}}
</style>
<style scoped>
.has-lecture{padding:16px;position:relative}.has-lecture .reader-heading{position:absolute;width:1px;height:1px;padding:0;margin:0;overflow:hidden;clip-path:inset(50%)}
@media(max-width:600px){.has-lecture{padding:12px}}@media(max-height:600px){.has-lecture{padding:10px}}
</style>
<style scoped>
@media(max-height:600px){.fragment-reader{padding:10px}.reader-heading{margin-bottom:5px}h2{font-size:16px}}
</style>
