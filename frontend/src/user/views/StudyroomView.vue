<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {List,Maximize,Minimize,NotebookText,MessageCircle,PanelLeftClose,ArrowLeft,X} from '@lucide/vue';
import { useRoute } from 'vue-router';
import type { StudyroomView as Classroom, StudyroomFollowUpContext } from '../../shared/types/studyroom';
import type { LearningFragment, StudyroomRun } from '../../shared/types/outline';
import { userApi } from '../runtime';
import { outlineApi } from '../outline-runtime';
import { fragmentApi } from '../studyroom-fragment-runtime';
import {classroomPages} from '../classroom-pages';
import StudyroomPageDirectory from '../components/studyroom/StudyroomPageDirectory.vue';
import StudyroomFragmentReader from '../components/studyroom/StudyroomFragmentReader.vue';
import StudyroomClassroomLayout from '../components/studyroom/StudyroomClassroomLayout.vue';
import StudyroomQuestionPanel from '../components/studyroom/StudyroomQuestionPanel.vue';
import {useTeachingActions} from '../use-teaching-actions';
import BrandName from '../../shared/components/BrandName.vue';
import ThemeToggle from '../../shared/design/ThemeToggle.vue';
import brandIcon from '../../favicon.svg';
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
const navigationOpen=ref(window.innerWidth>900);const directoryTrigger=ref<HTMLButtonElement>();const directoryClose=ref<HTMLButtonElement>();const notesOpen=ref(false);let resumeNotes=false;const notesTrigger=ref<HTMLButtonElement>();
const classroomRoot=ref<HTMLElement>();const fullscreen=ref(false);
let fullscreenLayout:{navigation:boolean;notes:boolean;question:boolean}|null=null;
const reachedEnd=ref(false);const currentPageId=ref('');const selectedPages=new Map<string,string>();
const coursePages=computed(()=>run.value?.knowledgePoints.flatMap(point=>point.fragments.flatMap((fragment,index)=>{const step=room.value?.steps.find(item=>item.id===fragment.stepId);if(!step)return [];return [...classroomPages(step,fragment.contentRevision).map(page=>page.id),...(index===point.fragments.length-1&&point.fragments.every(item=>item.stepId)?[`${step.id}:${fragment.contentRevision}:quiz`]:[])];}))??[]);
const coursePageIndex=computed(()=>Math.max(0,coursePages.value.indexOf(currentPageId.value)));
const seenPages=new Set<string>();const completing=new Set<string>();
const settledReaders=new Set<string>();
interface PageReader extends ClassroomMaterialAdapter{selectPage:(id:string,manual?:boolean)=>boolean;navigate:(delta:number)=>void;pageState:()=>{id:string;index:number;count:number}}
let pageNavigation=0;
async function selectPage(unit:string,fragment?:string,page?:string){
 const navigation=++pageNavigation;
 if(unitId.value!==unit)await selectPoint(unit);
 if(navigation!==pageNavigation||unitId.value!==unit)return;
 if(fragment)choose(fragment);
 await nextTick();if(navigation!==pageNavigation)return;if(page)(readers.get(fragmentId.value) as PageReader|undefined)?.selectPage(page);
}
function pageChanged(fragment:string,id:string){
 if(fragment===fragmentId.value)currentPageId.value=id;
 if(fragment===fragmentId.value&&settledReaders.has(fragment)){seenPages.add(id);}
 if(!settledReaders.has(fragment)&&selectedPages.has(fragment))return;
 selectedPages.set(fragment,id);
 try{localStorage.setItem(`structify.pages.${roomId.value}`,JSON.stringify(Object.fromEntries(selectedPages)));}catch{}
}
async function saveReadCompletion(){
 const state=run.value,knowledge=point.value,content=room.value;
 if(!state||!knowledge||!content||knowledge.completed||knowledge.hasPendingContent||completing.has(knowledge.id))return;
 const expected=knowledge.fragments.flatMap((fragment,index)=>{
  const step=content.steps.find(step=>step.id===fragment.stepId);if(!step)return ['not-published'];
  return [...classroomPages(step,fragment.contentRevision).map(page=>page.id),...(index===knowledge.fragments.length-1?[`${step.id}:${fragment.contentRevision}:quiz`]:[])];
 });
 if(!expected.length||!expected.every(id=>seenPages.has(id)))return;
 const last=knowledge.fragments.at(-1);if(!last?.stepId)return;
 const revisions=Object.fromEntries(knowledge.fragments.map(fragment=>[fragment.id,fragment.contentRevision]));
 const signature=JSON.stringify(revisions);
 completing.add(knowledge.id);
 try{await fragmentApi.progress(state.id,knowledge.id,last.id,true);if(run.value?.id===state.id){const current=run.value.knowledgePoints.find(point=>point.id===knowledge.id);if(current&&JSON.stringify(Object.fromEntries(current.fragments.map(fragment=>[fragment.id,fragment.contentRevision])))===signature)current.completed=true;}}
 catch{error.value='页面已浏览，进度暂未保存，请稍后重试。';}
 finally{completing.delete(knowledge.id);}
}
async function boundary(direction:number){
 if(direction>0){await continueLearning();return;}
 reachedEnd.value=false;
 if(index.value>0){const fragment=point.value!.fragments[index.value-1]!;choose(fragment.id);await selectLastPage(fragment);return;}
 const previous=run.value?.knowledgePoints[(run.value?.knowledgePoints.findIndex(p=>p.id===unitId.value)??0)-1];
 if(previous){await selectPoint(previous.id);const last=previous.fragments.at(-1);if(last?.stepId){choose(last.id);await selectLastPage(last);}}
}
async function selectLastPage(fragment:LearningFragment){
 const step=room.value?.steps.find(step=>step.id===fragment.stepId);if(!step)return;
 const knowledge=run.value?.knowledgePoints.find(point=>point.fragments.some(item=>item.id===fragment.id));
 const quiz=knowledge?.fragments.at(-1)?.id===fragment.id&&knowledge.fragments.every(item=>item.stepId);
 const page=quiz?`${step.id}:${fragment.contentRevision}:quiz`:classroomPages(step,fragment.contentRevision).at(-1)?.id;
 await nextTick();if(fragmentId.value===fragment.id&&page)(readers.get(fragment.id) as PageReader|undefined)?.selectPage(page);
}
function syncFullscreen(){
 const active=document.fullscreenElement===classroomRoot.value;
 if(active&&!fullscreen.value){
  fullscreenLayout={navigation:navigationOpen.value,notes:notesOpen.value,question:questionOpen.value};
  navigationOpen.value=false;notesOpen.value=false;questionOpen.value=false;
 }else if(!active&&fullscreen.value&&fullscreenLayout){
  navigationOpen.value=fullscreenLayout.navigation;notesOpen.value=fullscreenLayout.notes;questionOpen.value=fullscreenLayout.question;fullscreenLayout=null;
 }
 fullscreen.value=active;
}
async function toggleFullscreen(){try{if(document.fullscreenElement)await document.exitFullscreen();else{if(!classroomRoot.value?.requestFullscreen)throw new Error('Fullscreen unavailable');await classroomRoot.value.requestFullscreen();}}catch{error.value='当前浏览器暂不支持全屏，请使用浏览器全屏模式。';}}
onMounted(()=>document.addEventListener('fullscreenchange',syncFullscreen));
async function toggleDirectory(){navigationOpen.value=!navigationOpen.value;await nextTick();(navigationOpen.value?directoryClose.value:directoryTrigger.value)?.focus({preventScroll:true});}
async function closeDirectory(){navigationOpen.value=false;await nextTick();directoryTrigger.value?.focus({preventScroll:true});}
function toggleNotes(){notesOpen.value=!notesOpen.value;if(notesOpen.value){questionOpen.value=false;readers.get(fragmentId.value)?.pause();}}
function closeNotes(){notesOpen.value=false;notesTrigger.value?.focus({preventScroll:true});}
function classroomKey(event:KeyboardEvent){if(event.key!=='Escape')return;if(notesOpen.value){event.preventDefault();closeNotes();}else if(navigationOpen.value){event.preventDefault();void closeDirectory();}}
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
function registerReader(id:string,value:unknown){
 if(value){
  if(readers.get(id)===value)return;readers.set(id,value as PageReader);settledReaders.delete(id);const saved=selectedPages.get(id);
  void nextTick(async()=>{if(readers.get(id)!==value)return;if(saved)(value as PageReader).selectPage(saved,false);await nextTick();if(readers.get(id)!==value)return;settledReaders.add(id);const current=(value as PageReader).pageState();if(current.id)pageChanged(id,current.id);});
 }else{readers.delete(id);settledReaders.delete(id);}
}
function setQuestionTrigger(value:unknown){if(value instanceof HTMLButtonElement)questionTrigger.value=value;}
function setNotesTrigger(value:unknown){if(value instanceof HTMLButtonElement)notesTrigger.value=value;}
function setDirectoryTrigger(value:unknown){if(value instanceof HTMLButtonElement)directoryTrigger.value=value;}
function openQuestion(){autoplayFragmentId.value='';resumeNotes=notesOpen.value;notesOpen.value=false;session.ask();questionOpen.value=true;}
function materialNavigation(){reachedEnd.value=false;autoplayFragmentId.value='';session.navigated();}
function lectureEnded(){if(!next.value)return;autoplayFragmentId.value=next.value.id;if(next.value.stepId)choose(next.value.id,true,true);else waitingFor.value=next.value.id;}
async function resumeLearning(){questionOpen.value=false;notesOpen.value=resumeNotes;resumeNotes=false;await session.resume();questionTrigger.value?.focus({preventScroll:true});}
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
    await saveReadCompletion();
    const position=run.value.knowledgePoints.findIndex(p=>p.id===unitId.value);
    const following=run.value.knowledgePoints[position+1];
    if(following)await selectPoint(following.id);else {reachedEnd.value=true;await load(roomId.value);}
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
watch(roomId,id=>{epoch++;clearTimeout(timer);questionRuntime.stop();session.reset();question.value='';questionOpen.value=false;notesOpen.value=false;selectedPages.clear();seenPages.clear();completing.clear();pageNavigation++;try{const saved=JSON.parse(localStorage.getItem('structify.pages.'+id)??'{}');if(saved&&typeof saved==='object')for(const [fragment,page] of Object.entries(saved))if(typeof page==='string')selectedPages.set(fragment,page);}catch{}currentPageId.value='';room.value=null;run.value=null;unitId.value='';fragmentId.value='';visited.value=new Set();waitingFor.value='';focusSequence=0;if(id)void load(id);},{immediate:true});
onBeforeUnmount(()=>{epoch++;clearTimeout(timer);document.removeEventListener('fullscreenchange',syncFullscreen);});
</script>
<template>
 <BrandStage wide fixed :show-brand="false" :show-theme="false" class="classroom-shell">
  <main ref="classroomRoot" class="studyroom" @keydown="classroomKey">
   <StudyroomClassroomLayout :question-open="questionOpen" :navigation-open="navigationOpen" :notes-open="notesOpen" :fullscreen="fullscreen">
    <template #brand>
     <RouterLink class="course-brand" to="/" aria-label="数筑 Structify 首页"><img :src="brandIcon" width="28" height="28" alt=""/><BrandName/></RouterLink>
     <button ref="directoryClose" type="button" class="rail-toggle" aria-label="收起课程目录" title="收起课程目录" @click="closeDirectory"><PanelLeftClose :size="18"/></button>
    </template>
    <template #navigation><StudyroomPageDirectory v-if="room&&run" :run="run" :steps="room.steps" :active-id="unitId" :page-id="currentPageId" @select="selectPage" @close="closeDirectory"/><div v-else class="directory-loading" aria-hidden="true"><span/><span/><span/></div></template>
    <template #progress><span v-if="run">已浏览 {{run.knowledgePoints.filter(p=>p.completed).length}} / {{run.knowledgePoints.length}} 个知识点</span><span v-else>正在读取课程…</span></template>
    <template #header>
     <header class="studyroom-header">
      <RouterLink class="return-entry" to="/begin" aria-label="返回学习入口" title="返回学习入口"><ArrowLeft :size="20"/></RouterLink>
      <div class="course-title"><span class="course-name" :title="room?.title">{{room?.title||'正在打开课堂'}}</span><h1>{{point?.title||'正在读取学习内容'}}</h1></div>
      <div class="course-tools"><ThemeToggle/></div>
     </header>
    </template>
    <template v-if="room&&run">
     <div class="lesson-panel"><p v-if="reachedEnd" class="preparation-note" role="status">已到达课程末页，可以回看资源或继续提问。</p>
      <StudyroomFragmentReader v-for="fragment in visibleReaders" v-show="fragment.id===fragmentId" :key="`${room.id}:${fragment.id}:${fragment.contentRevision}`" :ref="value=>registerReader(fragment.id,value)" :fragment="fragment" :step="room.steps.find(s=>s.id===fragment.stepId)" :chapter-id="room.chapterId??undefined" :active="fragment.id===fragmentId" :run-id="run.id" :lecture-blocked="questionOpen" :unit-id="run.knowledgePoints.find(point=>point.fragments.some(item=>item.id===fragment.id))?.id" :can-prepare-checks="run.knowledgePoints.some(point=>point.fragments.at(-1)?.id===fragment.id&&point.fragments.every(item=>!!item.stepId))" :global-index="coursePageIndex" :global-count="coursePages.length" :has-previous="index>0||run.knowledgePoints[0]?.id!==unitId" :has-next="!!next||run.knowledgePoints.at(-1)?.id!==unitId" @boundary="boundary" @page="id=>pageChanged(fragment.id,id)" @navigate="materialNavigation" :auto-start="autoplayFragmentId===fragment.id" @lecture-ended="lectureEnded" :busy="busy" @retry="retry($event)" @regenerate="retry($event,true)" @context="setContext">
       <template #directory><button v-if="fragment.id===fragmentId" :ref="setDirectoryTrigger" type="button" class="directory-toggle" aria-label="课程目录" title="课程目录" :aria-expanded="navigationOpen" @click="toggleDirectory"><List :size="18"/></button></template>
       <template #tools><template v-if="fragment.id===fragmentId"><button type="button" :aria-label="fullscreen?'退出全屏':'全屏'" :title="fullscreen?'退出全屏':'全屏'" @click="toggleFullscreen"><component :is="fullscreen?Minimize:Maximize" :size="18"/></button><button :ref="setNotesTrigger" type="button" aria-label="完整讲述" title="完整讲述" :aria-expanded="notesOpen" @click="toggleNotes"><NotebookText :size="18"/></button><button :ref="setQuestionTrigger" type="button" aria-label="对这里提问 ↗" title="对这里提问" data-action="open-question" :aria-expanded="questionOpen" :disabled="!activeStep" @click="openQuestion"><MessageCircle :size="18"/></button></template></template>
      </StudyroomFragmentReader>
      <div v-if="waiting&&!waiting.stepId" class="waiting-panel"><p class="preparation-note">下一段尚未就绪，当前内容可以继续阅读。</p><StudyroomFragmentReader :fragment="waiting" :active="false" :busy="busy" @retry="retry($event)" @regenerate="retry($event,true)"/></div>
     </div>
     <p v-if="teaching.notice.value" class="preparation-note" role="status">{{teaching.notice.value}}</p>
    </template>
    <div v-else class="classroom-loading" role="status"><div class="loading-canvas" aria-hidden="true"/><p>正在读取课堂…</p><div class="loading-narration" aria-hidden="true"/></div>
    <p v-if="error" class="error" role="alert">{{error}} <button type="button" @click="load(roomId)">重新读取</button></p>
    <template #notes><div class="course-notes"><header><strong>完整讲述</strong><button type="button" @click="openQuestion">课堂提问</button><button type="button" class="close-notes" aria-label="收起完整讲述" @click="closeNotes"><X :size="18"/></button></header><div class="notes-scroll" tabindex="0" aria-label="完整讲解正文"><section v-for="knowledge in run?.knowledgePoints??[]" :key="knowledge.id" :aria-current="knowledge.id===unitId?'step':undefined"><h2>{{knowledge.title}}</h2><template v-for="fragment in knowledge.fragments" :key="fragment.id"><article v-if="room?.steps.find(step=>step.id===fragment.stepId)"><h3>{{room.steps.find(step=>step.id===fragment.stepId)?.title}}</h3><p>{{room.steps.find(step=>step.id===fragment.stepId)?.explanation}}</p></article><p v-else class="notes-pending">这一段尚未就绪。</p></template></section></div></div></template>
    <template #question><StudyroomQuestionPanel v-if="room" :open="questionOpen" v-model:draft="question" :busy="questionRuntime.busy.value" :source="questionSource" :question="questionRuntime.question.value" :messages="room.messages" :error="questionRuntime.error.value" :connection-error="questionRuntime.connectionError.value" :can-ask="!!activeStep" @ask="ask" @cancel="questionRuntime.cancel(roomId)" @close="resumeLearning" @resume="resumeLearning"/></template>
   </StudyroomClassroomLayout>
  </main>
 </BrandStage>
</template>
<style scoped>
.classroom-shell{padding:0}.studyroom{height:100%;min-width:0;min-height:0;display:flex;color:var(--text)}
.studyroom:fullscreen{background:var(--bg);width:100%;height:100%;overflow:hidden}
.course-brand{display:inline-flex;align-items:center;gap:8px;color:var(--text);text-decoration:none;font-size:16px;font-weight:650;min-width:0;white-space:nowrap}.course-brand img{flex-shrink:0}.course-brand :deep(.brand-name){gap:0;letter-spacing:-.025em}
.rail-toggle,.return-entry{width:32px;height:34px;flex-shrink:0;padding:0;display:inline-flex;align-items:center;justify-content:center;border:0;border-radius:8px;background:var(--surface-subtle);color:var(--text-muted);cursor:pointer}.return-entry{background:transparent;text-decoration:none}.rail-toggle:hover,.return-entry:hover{background:var(--line);color:var(--text)}
.studyroom-header{height:76px;display:flex;align-items:center;gap:14px;padding:0 24px;min-width:0}.course-title{min-width:0;display:flex;flex-direction:column;gap:4px}.course-name{font-size:11px;color:var(--text-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:560px}h1{font-size:20px;line-height:1.3;font-weight:600;letter-spacing:-.02em;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin:0}.course-tools{margin-left:auto;flex-shrink:0;padding-left:8px}.course-tools :deep(.theme-toggle){position:static}
.lesson-panel{display:flex;flex-direction:column;flex:1;min-width:0;min-height:0;gap:8px}.waiting-panel{max-height:35%;overflow:auto;flex-shrink:0}.preparation-note{font-size:12px;color:var(--text-muted);line-height:1.7;margin:0}.error{position:absolute;z-index:36;bottom:20px;left:24px;max-width:calc(100% - 48px);padding:12px 16px;margin:0;background:var(--surface);border:1px solid var(--line);border-radius:12px;font-size:13px;line-height:1.7}.error button{font:inherit;padding:5px 8px;border:1px solid var(--line);border-radius:6px;background:var(--surface);color:var(--text);cursor:pointer}
.course-notes{display:flex;flex-direction:column;height:100%;background:var(--surface);border:1px solid var(--line);border-radius:16px;overflow:hidden}.course-notes header{display:flex;align-items:center;gap:14px;padding:14px 18px;border-bottom:1px solid var(--line);font-size:12px;min-height:54px;flex-shrink:0}.course-notes header strong{margin-right:auto}.course-notes header button{font:inherit;font-size:11px;background:transparent;border:0;padding:4px 0;color:var(--text-muted);cursor:pointer}.course-notes .close-notes{display:flex;align-items:center;justify-content:center;width:24px;min-width:24px;height:24px;padding:0}.notes-scroll{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;padding:20px;scrollbar-width:thin}.notes-scroll h2{font-size:17px;font-weight:550;line-height:1.5;margin:0 0 8px}.notes-scroll h3{font-size:13px;line-height:1.6;margin:18px 0 8px}.notes-scroll p{font-size:14px;line-height:1.95;white-space:pre-wrap;overflow-wrap:anywhere}.notes-scroll section+section{border-top:1px solid var(--line);margin-top:24px;padding-top:24px}.notes-scroll .notes-pending{font-size:12px;color:var(--text-muted)}.notes-scroll section[aria-current]{border-left:2px solid var(--text-muted);padding-left:12px}
.classroom-loading{display:flex;flex-direction:column;flex:1;min-height:0;border:1px solid var(--line);border-radius:16px;overflow:hidden;background:var(--surface)}.loading-canvas{flex:1;min-height:0;background:color-mix(in srgb,var(--surface) 70%,var(--surface-subtle))}.classroom-loading p{font-size:13px;color:var(--text-muted);padding:14px 24px;margin:0;border-top:1px solid var(--line)}.loading-narration{height:110px;flex-shrink:0;border-top:1px solid var(--line)}.directory-loading{padding:20px;display:grid;gap:24px}.directory-loading span{height:110px;border-radius:8px;background:var(--surface-subtle)}
button,a{transition:background 150ms ease,color 150ms ease,transform 150ms ease}button:active:not(:disabled){transform:scale(.97)}button:focus-visible,a:focus-visible,.notes-scroll:focus-visible{outline:2px solid var(--text);outline-offset:2px}
@media(max-width:1100px){.studyroom-header{padding:0 16px;gap:10px}.course-brand{font-size:14px;gap:6px}.course-brand img{width:25px;height:25px}h1{font-size:18px}.course-name{max-width:340px}}
@media(max-width:900px){.studyroom-header{gap:8px}.course-tools :deep(.theme-toggle){gap:6px}}
@media(max-width:600px){.studyroom-header{height:66px;padding:0 10px;gap:6px}.rail-toggle,.return-entry{width:27px;height:30px}.course-tools{padding-left:0}.course-tools :deep(.theme-toggle){transform:scale(.78);transform-origin:right center;margin-left:-38px}.course-name{font-size:10px;max-width:180px}h1{font-size:15px}.course-title{gap:3px}.notes-scroll{padding:16px}.notes-scroll p{font-size:13px}.loading-narration{height:100px}}
@media(max-height:600px){.studyroom-header{height:62px}.course-name{font-size:10px}h1{font-size:16px}.loading-narration{height:78px}}
@media(prefers-reduced-motion:reduce){button,a{transition:none}button:active:not(:disabled){transform:none}}
</style>
