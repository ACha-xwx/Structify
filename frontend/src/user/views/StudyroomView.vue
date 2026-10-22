<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import type { StudyroomView as Classroom, StudyroomFollowUpContext } from '../../shared/types/studyroom';
import type { LearningFragment, StudyroomRun } from '../../shared/types/outline';
import { userApi } from '../runtime';
import { outlineApi } from '../outline-runtime';
import { fragmentApi } from '../studyroom-fragment-runtime';
import StudyroomKnowledgeRoute from '../components/studyroom/StudyroomKnowledgeRoute.vue';
import StudyroomFragmentReader from '../components/studyroom/StudyroomFragmentReader.vue';
import StudyroomClassroomLayout from '../components/studyroom/StudyroomClassroomLayout.vue';
import StudyroomQuestionPanel from '../components/studyroom/StudyroomQuestionPanel.vue';
import {useTeachingActions} from '../use-teaching-actions';
import AiTitle from '../../shared/components/AiTitle.vue';
import BrandStage from '../../shared/components/BrandStage.vue';
import {useClassroomQuestion} from '../use-classroom-question';
import {useClassroomSession} from '../use-classroom-session';
import type {ClassroomMaterialAdapter} from '../classroom-materials';
import type {MaterialIdentity} from '../../shared/types/classroom-experience';
const route=useRoute();
const room=ref<Classroom|null>(null);const run=ref<StudyroomRun|null>(null);
const unitId=ref('');const fragmentId=ref('');const waitingFor=ref('');
const visited=ref(new Set<string>());const question=ref('');const error=ref('');const busy=ref(false);
const context=ref<StudyroomFollowUpContext>();
const questionOpen=ref(false);const questionTrigger=ref<HTMLButtonElement>();const readers=new Map<string,ClassroomMaterialAdapter>();
const navigationOpen=ref(window.innerWidth>900);
const autoplayFragmentId=ref('');
const questionRuntime=useClassroomQuestion(()=>{session.answered();if(roomId.value)void load(roomId.value);});
const session=useClassroomSession(()=>materialIdentity.value,()=>readers.get(fragmentId.value));
const teaching=useTeachingActions(()=>materialIdentity.value,()=>readers.get(fragmentId.value),()=>session.allowTeachingNavigation());
watch(()=>questionRuntime.question.value,answer=>{if(answer?.status==='COMPLETED'&&answer.actions?.length)void teaching.apply(answer.actions);});
let epoch=0;let timer:ReturnType<typeof setTimeout>|undefined;let focusSequence=0;
const roomId=computed(()=>typeof route.params.id==='string'?route.params.id:'');
const point=computed(()=>run.value?.knowledgePoints.find(p=>p.id===unitId.value));
const active=computed(()=>point.value?.fragments.find(f=>f.id===fragmentId.value));
const activeStep=computed(()=>room.value?.steps.find(s=>s.id===active.value?.stepId));
const waiting=computed(()=>point.value?.fragments.find(f=>f.id===waitingFor.value));
const allFragments=computed(()=>run.value?.knowledgePoints.flatMap(p=>p.fragments)??[]);
const visibleReaders=computed(()=>allFragments.value.filter(f=>visited.value.has(f.id)));
const index=computed(()=>point.value?.fragments.findIndex(f=>f.id===fragmentId.value)??-1);
const next=computed(()=>point.value?.fragments[index.value+1]);
const materialIdentity=computed<MaterialIdentity|null>(()=>room.value&&run.value&&active.value?{roomId:room.value.id,runId:run.value.id,unitId:unitId.value,fragmentId:active.value.id,contentRevision:active.value.contentRevision,sceneId:context.value?.sceneId??activeStep.value?.orchestration?.primary.id??'text',resourceId:context.value?.resourceId??null}:null);
const questionSource=computed(()=>{
 const pending=questionRuntime.question.value;
 if(pending&&['QUEUED','RUNNING'].includes(pending.status)){
  const source=run.value?.knowledgePoints.find(point=>point.fragments.some(fragment=>fragment.id===pending.fragmentId));
  return `针对「${source?.title??'此前选择的讲解'}」第 ${pending.contentRevision} 版回答`;
 }
 return [...new Set([point.value?.title,activeStep.value?.title,context.value?.resourceId?activeStep.value?.resources.find(resource=>resource.id===context.value?.resourceId)?.title:null].filter(Boolean))].join(' · ');
});
function registerReader(id:string,value:unknown){if(value)readers.set(id,value as ClassroomMaterialAdapter);else readers.delete(id);}
function openQuestion(){autoplayFragmentId.value='';session.ask();questionOpen.value=true;}
function materialNavigation(){autoplayFragmentId.value='';session.navigated();}
function lectureEnded(){if(!next.value)return;autoplayFragmentId.value=next.value.id;if(next.value.stepId)choose(next.value.id,true,true);else waitingFor.value=next.value.id;}
async function resumeLearning(){questionOpen.value=false;await session.resume();questionTrigger.value?.focus({preventScroll:true});}
function choose(id:string,persist=true,automatic=false){
  const selected=allFragments.value.find(f=>f.id===id);if(!selected)return;
  if(!automatic)autoplayFragmentId.value='';
  if(fragmentId.value!==id)session.navigated();
  fragmentId.value=id;visited.value.add(id);waitingFor.value='';context.value=undefined;
  if(persist&&selected.stepId&&run.value&&point.value)
    void fragmentApi.progress(run.value.id,point.value.id,id).catch(()=>{error.value='阅读位置暂未保存，可稍后重试。';});
}
async function load(id:string){
  const current=++epoch;clearTimeout(timer);
  try{
    const [content,state]=await Promise.all([userApi.getStudyroom(id),outlineApi.current(id)]);
    if(current!==epoch)return;
    if(run.value?.id===state.id&&state.stateVersion<run.value.stateVersion)return;
    if(run.value&&run.value.id!==state.id){questionRuntime.stop();session.reset();questionOpen.value=false;visited.value=new Set();}
    room.value=content;run.value=state;focusSequence=Math.max(focusSequence,state.progress?.focusSequence??0);
    if(content.activeQuestionId&&content.activeQuestionId!==questionRuntime.question.value?.questionId){questionOpen.value=true;session.answering();void questionRuntime.attach(id,content.activeQuestionId);}
    if(!state.knowledgePoints.some(p=>p.id===unitId.value))unitId.value=state.progress?.unitId??state.knowledgePoints[0]?.id??'';
    if(!point.value?.fragments.some(f=>f.id===fragmentId.value)){
      const restored=point.value?.fragments.find(f=>f.id===state.progress?.fragmentId);
      choose(restored?.id??point.value?.fragments[0]?.id??'');
    }
    if(waitingFor.value&&waiting.value?.stepId)choose(waitingFor.value,true,autoplayFragmentId.value===waitingFor.value);
    if(state.knowledgePoints.some(p=>p.fragments.some(f=>f.status==='QUEUED'||f.status==='GENERATING')))
      timer=setTimeout(()=>void load(id),document.visibilityState==='hidden'?10000:2000);
  }catch(cause){if(current===epoch)error.value=cause instanceof Error?cause.message:'课堂暂时无法加载，请重试。';}
}
async function selectPoint(id:string){
  if(!run.value)return;unitId.value=id;choose(point.value?.fragments[0]?.id??'',false);
  try{await fragmentApi.focus(run.value.id,id,++focusSequence);if(roomId.value)await load(roomId.value);}
  catch(cause){error.value=cause instanceof Error?cause.message:'当前知识点暂时无法准备。';}
}
async function continueLearning(){
  if(!run.value||!point.value||!active.value)return;
  if(next.value){if(next.value.stepId)choose(next.value.id);else waitingFor.value=next.value.id;return;}
  if(!active.value.stepId)return;
  try{
    await fragmentApi.progress(run.value.id,point.value.id,active.value.id,true);
    const position=run.value.knowledgePoints.findIndex(p=>p.id===unitId.value);
    const following=run.value.knowledgePoints[position+1];
    if(following)await selectPoint(following.id);else await load(roomId.value);
  }catch(cause){error.value=cause instanceof Error?cause.message:'学习进度暂未保存。';}
}
async function retry(fragment:LearningFragment,regenerate=false){
  if(!run.value||busy.value)return;busy.value=true;error.value='';
  try{
    if(regenerate)await fragmentApi.regenerate(run.value.id,fragment.id,fragment.contentRevision);
    else await fragmentApi.retry(run.value.id,fragment.id);
    await load(roomId.value);
  }catch(cause){error.value=cause instanceof Error?cause.message:'重新准备失败，请稍后重试。';}
  finally{busy.value=false;}
}
async function ask(){
  if(!room.value||!run.value||!active.value||!question.value.trim()||questionRuntime.busy.value||!activeStep.value)return;
  session.ask();session.answering();questionOpen.value=true;
  if(await questionRuntime.ask(room.value.id,{question:question.value.trim(),runId:run.value.id,fragmentId:active.value.id,contentRevision:active.value.contentRevision,...(context.value?{context:context.value}:{})}))question.value='';
}
function setContext(value:StudyroomFollowUpContext){if(value.stepId===activeStep.value?.id){if(context.value&&(context.value.sceneId!==value.sceneId||context.value.resourceId!==value.resourceId)&&!teaching.applying.value)session.navigated();context.value=value;}}
watch(roomId,id=>{epoch++;clearTimeout(timer);questionRuntime.stop();session.reset();questionOpen.value=false;room.value=null;run.value=null;unitId.value='';fragmentId.value='';visited.value=new Set();waitingFor.value='';focusSequence=0;if(id)void load(id);},{immediate:true});
onBeforeUnmount(()=>{epoch++;clearTimeout(timer);});
</script>
<template>
 <BrandStage wide fixed class="classroom-shell">
  <main class="studyroom">
    <header class="studyroom-header"><div class="course-title"><AiTitle><h1>{{room?.title||'正在打开课堂'}}</h1></AiTitle><span v-if="run" class="course-progress">{{run.knowledgePoints.filter(p=>p.completed).length}} / {{run.knowledgePoints.length}} 个知识点已完成</span></div><div class="course-tools"><details v-if="room&&run" class="course-info"><summary>课程信息</summary><div class="course-info-content"><p class="summary">{{room.summary}}</p><strong>已确认的大纲与范围</strong><ol><li v-for="unit in run.outline.units" :key="unit.id"><strong>{{unit.title}}</strong>：{{unit.objective}}</li></ol><p v-if="run.outline.exclusions?.length">本次不覆盖：{{run.outline.exclusions.join('；')}}</p><p v-if="run.codeExecutionAvailable===false">当前可阅读、编辑 C 代码；代码运行服务尚未配置。</p><RouterLink :to="{path:'/studyroom',query:{question:room.initialQuestion,sourceRoomId:room.id}}">调整学习路线</RouterLink></div></details><RouterLink to="/">返回学习入口</RouterLink></div></header>
    <template v-if="room&&run">
      <div class="classroom-toolbar"><button type="button" class="directory-toggle" :aria-expanded="navigationOpen" @click="navigationOpen=!navigationOpen">☰ <span>课程目录</span></button><span class="current-point">{{point?.title}}</span><button ref="questionTrigger" type="button" data-action="open-question" :aria-expanded="questionOpen" :disabled="!activeStep" @click="openQuestion">{{questionRuntime.busy.value?'查看回答':'对这里提问'}} ↗</button></div>
      <StudyroomClassroomLayout :question-open="questionOpen" :navigation-open="navigationOpen">
      <template #navigation><StudyroomKnowledgeRoute :points="run.knowledgePoints" :active-id="unitId" @select="selectPoint"/></template>
      <div class="lesson-panel"><StudyroomFragmentReader v-for="fragment in visibleReaders" v-show="fragment.id===fragmentId" :key="`${room.id}:${fragment.id}:${fragment.contentRevision}`" :ref="value=>registerReader(fragment.id,value)" :fragment="fragment" :step="room.steps.find(s=>s.id===fragment.stepId)" :chapter-id="room.chapterId??undefined" :active="fragment.id===fragmentId" :run-id="run.id" :lecture-blocked="questionOpen" :unit-id="run.knowledgePoints.find(point=>point.fragments.some(item=>item.id===fragment.id))?.id" :can-prepare-checks="point?.fragments.every(item=>!!item.stepId)" @navigate="materialNavigation" :auto-start="autoplayFragmentId===fragment.id" @lecture-ended="lectureEnded" :busy="busy" @retry="retry($event)" @regenerate="retry($event,true)" @context="setContext"/>
      <div v-if="waiting&&!waiting.stepId" class="waiting-panel"><p class="preparation-note">下一段尚未就绪，当前内容可以继续阅读。</p><StudyroomFragmentReader :fragment="waiting" :active="false" :busy="busy" @retry="retry($event)" @regenerate="retry($event,true)"/></div></div>
      <template #question><StudyroomQuestionPanel :open="questionOpen" v-model:draft="question" :busy="questionRuntime.busy.value" :source="questionSource" :question="questionRuntime.question.value" :messages="room.messages" :error="questionRuntime.error.value" :connection-error="questionRuntime.connectionError.value" :can-ask="!!activeStep" @ask="ask" @cancel="questionRuntime.cancel(roomId)" @close="resumeLearning" @resume="resumeLearning"/></template>
      </StudyroomClassroomLayout>
      <footer v-if="point&&active" class="learning-actions"><button type="button" :disabled="index<=0" @click="choose(point.fragments[index-1]!.id)">回看前段</button><span v-if="point.hasPendingContent" role="status">正在准备后续内容</span><span v-else class="fragment-progress">本知识点 · {{index+1}} / {{point.fragments.length}} 段</span><button class="primary" type="button" data-action="continue" :disabled="!active.stepId||busy" @click="continueLearning">{{next?'继续学习':point.completed?'再次完成本知识点':'完成本知识点'}}</button></footer>
      <p v-if="teaching.notice.value" class="preparation-note" role="status">{{teaching.notice.value}}</p>
    </template>
    <p v-else role="status">正在读取课堂…</p>
    <p v-if="error" class="error" role="alert">{{error}} <button type="button" @click="load(roomId)">重新读取</button></p>
  </main>
 </BrandStage>
</template>
<style scoped>
.classroom-shell{padding:72px clamp(14px,2.4vw,34px) 14px}
.studyroom{width:min(1600px,100%);height:100%;min-height:0;margin:0 auto;display:flex;flex-direction:column;gap:12px;color:var(--text)}.studyroom>*{min-width:0}
.studyroom-header{display:flex;justify-content:space-between;align-items:center;gap:20px;flex-shrink:0}
.course-title{display:flex;align-items:center;min-width:0;gap:20px}.course-title :deep(.workbench-title){font-size:24px}.course-title :deep(.ai-title){min-width:0}h1{font-size:24px;font-weight:550;letter-spacing:-.025em;margin:0;line-height:1.3}.course-progress{font-size:12px;color:var(--text-muted);white-space:nowrap}
.course-tools{display:flex;align-items:center;gap:20px;flex-shrink:0}a{color:var(--text-muted);font-size:12px;white-space:nowrap;text-underline-offset:4px}.course-info{position:relative;font-size:12px;color:var(--text-muted)}summary{cursor:pointer}.course-info-content{position:absolute;z-index:35;right:0;top:28px;width:min(440px,calc(100vw - 32px));max-height:60dvh;overflow:auto;overscroll-behavior:contain;padding:22px;border:1px solid var(--line);border-radius:18px;background:var(--surface);box-shadow:0 12px 40px color-mix(in srgb,var(--text) 12%,transparent);font-size:13px;line-height:1.8}.summary{margin:0 0 18px}.course-info-content ol{padding-left:20px}.course-info-content li{margin-bottom:10px}
.classroom-toolbar{display:flex;align-items:center;gap:14px;padding:7px 10px;border:1px solid var(--line);border-radius:99px;background:var(--surface);flex-shrink:0}.classroom-toolbar button{border-radius:99px;white-space:nowrap;padding:9px 14px}.classroom-toolbar>button:last-child{margin-left:auto}.current-point{font-size:12px;color:var(--text-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.classroom-tabs{display:flex;gap:2px;flex-shrink:0}.classroom-tabs button{border:0;background:transparent;color:var(--text-muted)}.classroom-tabs button[aria-pressed=true]{background:var(--text);color:var(--surface)}.directory-toggle{border-color:transparent}
.lesson-panel{display:flex;flex-direction:column;flex:1;min-height:0;gap:8px}.waiting-panel{max-height:40%;overflow:auto;flex-shrink:0}.preparation-note{font-size:12px;color:var(--text-muted);line-height:1.7;margin:0}.check-scroll{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;padding:0 24px 24px;border:1px solid var(--line);border-radius:var(--radius-lg);background:var(--surface)}.check-scroll :deep(.check-panel){border:0}
.learning-actions{display:flex;align-items:center;gap:16px;flex-shrink:0}.learning-actions span{font-size:12px;color:var(--text-muted)}button{font:inherit;font-size:13px;padding:10px 16px;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface);color:var(--text);cursor:pointer}button.primary{margin-left:auto;background:var(--text);color:var(--surface);border-color:var(--text)}button:disabled{opacity:.45;cursor:default}button:focus-visible,summary:focus-visible,.check-scroll:focus-visible{outline:2px solid var(--text);outline-offset:-2px}.error{font-size:13px;margin:0;max-height:80px;overflow:auto;line-height:1.7;flex-shrink:0}
@media(max-width:1100px){.course-title{display:block}.course-progress{display:block;margin-top:4px}}
@media(max-width:600px){.classroom-shell{padding:70px 12px 12px}.studyroom{gap:10px}.studyroom-header{align-items:flex-start;gap:10px}.course-title{flex:1}.course-info-content{position:fixed;top:130px;right:12px}.course-title h1{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.course-title :deep(.workbench-title),h1{font-size:20px;line-height:1.3}.course-tools{flex-direction:column;align-items:flex-end;gap:8px}.course-progress{font-size:11px}.classroom-toolbar{gap:2px;padding:5px}.classroom-toolbar button{font-size:12px;padding:8px 10px}.directory-toggle span,.current-point{display:none}.learning-actions{gap:8px}.learning-actions button{font-size:12px;padding:10px 12px}.fragment-progress{display:none}.check-scroll{padding:0 14px 14px}}
</style>
<style scoped>
@media(max-height:600px){.classroom-shell{padding-top:60px;padding-bottom:8px}.studyroom{gap:8px}.course-title{display:flex;gap:12px;align-items:center}.course-title h1{font-size:18px}.course-progress{margin:0}.studyroom-header{gap:12px}.classroom-toolbar{padding:4px 8px}.classroom-toolbar button{padding:6px 10px}.learning-actions button{padding:7px 12px}.course-title :deep(.ai-ball){--ai-ball-size:30px}}
</style>
