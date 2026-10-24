<script setup lang="ts">
import {ref} from 'vue';
import {ChevronLeft,ChevronRight} from '@lucide/vue';
import type {LearningFragment} from '../../../shared/types/outline';
import type {StudyroomStep,StudyroomFollowUpContext} from '../../../shared/types/studyroom';
import type {ClassroomMaterialAdapter} from '../../classroom-materials';
import type {LectureCue,LecturePlan,LectureRegion,LectureStageAdapter} from '../../lecture-types';
import StudyroomResourcePages from './StudyroomResourcePages.vue';
import StudyroomLectureControls from './StudyroomLectureControls.vue';
import StudyroomCheckPanel from './StudyroomCheckPanel.vue';
import {lectureApi} from '../../lecture-runtime';
const props=defineProps<{fragment:LearningFragment;step?:StudyroomStep;chapterId?:string;active:boolean;busy:boolean;runId?:string;unitId?:string;canPrepareChecks?:boolean;lectureBlocked?:boolean;autoStart?:boolean;globalIndex?:number;globalCount?:number;hasPrevious?:boolean;hasNext?:boolean}>();
const emit=defineEmits<{retry:[fragment:LearningFragment];regenerate:[fragment:LearningFragment];context:[value:StudyroomFollowUpContext];navigate:[];lectureEnded:[];boundary:[direction:number];page:[id:string,index:number,count:number]}>();
interface PagesAdapter extends ClassroomMaterialAdapter,LectureStageAdapter{navigate:(direction:number)=>boolean;selectPage:(id:string,manual?:boolean)=>boolean}
const stage=ref<PagesAdapter>();const lecture=ref<{pause:()=>void;reload:()=>Promise<void>}>();
const plan=ref<LecturePlan>();const error=ref('');const page=ref({id:'',index:0,count:1});
function pageChanged(id:string,index:number,count:number){page.value={id,index,count};emit('page',id,index,count);}
function navigate(direction:number){lecture.value?.pause();if(!stage.value?.navigate(direction))emit('boundary',direction);}
async function saveRegion(region:LectureRegion){if(!props.runId)return;lecture.value?.pause();error.value='';try{await lectureApi.save(props.runId,props.fragment.id,props.fragment.contentRevision,region);await lecture.value?.reload();}catch(cause){error.value=cause instanceof Error?cause.message:'区域暂未保存，请重试';}}
async function removeRegion(id:string){if(!props.runId)return;lecture.value?.pause();error.value='';try{await lectureApi.remove(props.runId,props.fragment.id,props.fragment.contentRevision,id);await lecture.value?.reload();}catch(cause){error.value=cause instanceof Error?cause.message:'区域暂未移除';}}
async function applyCue(cue:LectureCue,signal:AbortSignal){return await stage.value?.lecture(cue,signal)??false;}
function pause(){lecture.value?.pause();stage.value?.pause();}
defineExpose({pageState:()=>page.value,capture:()=>stage.value?.capture()??{kind:'TEXT' as const,scrollTop:0},pause,restore:async(position:Parameters<ClassroomMaterialAdapter['restore']>[0])=>stage.value?.restore(position),supports:(action:Parameters<ClassroomMaterialAdapter['supports']>[0])=>stage.value?.supports(action)??false,apply:async(action:Parameters<ClassroomMaterialAdapter['apply']>[0])=>stage.value?.apply(action)??false,selectPage:(id:string,manual=true)=>{if(manual)pause();return stage.value?.selectPage(id,manual);},navigate});
</script>
<template>
 <section class="fragment-reader" :aria-label="step?'知识点讲解':'准备当前讲解'">
  <template v-if="step">
   <div class="reader-stage"><StudyroomResourcePages ref="stage" :step="step" :revision="fragment.contentRevision" :chapter-id="chapterId" :active="active" :regions="plan?.regions" :has-checks="!!runId&&!!unitId&&canPrepareChecks" @page="pageChanged" @navigate="pause();$emit('navigate')" @context="$emit('context',$event)" @save-region="saveRegion" @remove-region="removeRegion"><template #checks><StudyroomCheckPanel v-if="runId&&unitId" :run-id="runId" :unit-id="unitId" :can-prepare="!!canPrepareChecks" :active="active&&page.id.endsWith(':quiz')"/></template></StudyroomResourcePages></div>
   <p v-if="error" class="fragment-status" role="alert">{{error}}</p>
   <StudyroomLectureControls v-if="runId" ref="lecture" :run-id="runId" :fragment-id="fragment.id" :revision="fragment.contentRevision" :active="active&&!lectureBlocked" :auto-start="autoStart" :apply-cue="applyCue" @plan="plan=$event" @finished="$emit('lectureEnded')">
    <template #before><slot name="directory"/><span class="page-position" aria-live="polite">{{(globalIndex??page.index)+1}} / {{globalCount??page.count}} 页</span></template>
    <template #previous><button type="button" aria-label="上一页" title="上一页" :disabled="!hasPrevious&&page.index===0" @click="navigate(-1)"><ChevronLeft :size="18"/></button></template>
    <template #after-play><button type="button" aria-label="下一页" title="下一页"  @click="navigate(1)"><ChevronRight :size="18"/></button></template>
    <template #tools><slot name="tools"/></template>
   </StudyroomLectureControls>
  </template>
  <div v-if="fragment.status==='FAILED'" class="fragment-status" role="alert"><p>{{fragment.error?.message||'这段讲解暂未准备完成，已就绪的内容仍可阅读。'}}</p><button v-if="fragment.error?.retryable!==false" :disabled="busy" type="button" @click="$emit('retry',fragment)">重新准备这段内容</button><button :disabled="busy" type="button" @click="$emit('regenerate',fragment)">重新生成该片段，可能使用模型额度</button></div>
  <p v-else-if="!step" class="fragment-status" role="status">{{fragment.status==='PENDING'?'即将准备当前讲解…':'正在准备讲解与所需材料…'}}内容就绪后会在这里显示。</p>
  <p v-else-if="fragment.status!=='READY'" class="fragment-status" role="status">正在准备新的内容修订，当前内容可以继续阅读。</p>
 </section>
</template>
<style scoped>
.fragment-reader{container:fragment-reader / inline-size;display:flex;flex-direction:column;flex:1;min-height:0;min-width:0;background:var(--surface);border:1px solid var(--line);border-radius:16px;overflow:hidden}.reader-stage{display:flex;flex:1;min-height:0;min-width:0;padding:12px;overflow:hidden}.fragment-status{padding:12px 18px;font-size:13px;line-height:1.7;max-height:45%;overflow:auto}.page-position{font:11px var(--font-mono);color:var(--text-muted);white-space:nowrap}.fragment-reader :deep(.lecture-controls){margin:0;padding:0;border:0;border-top:1px solid var(--line)}.fragment-status button{font:inherit;padding:8px;border:1px solid var(--line);border-radius:8px;background:var(--surface);color:var(--text);cursor:pointer}
@media(max-width:600px){.reader-stage{padding:8px}.fragment-reader :deep(.lecture-controls){padding:0}.page-position{font-size:10px}}@media(max-height:600px){.reader-stage{padding:6px}.fragment-reader :deep(.lecture-controls){padding:0}}
</style>
