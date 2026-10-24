<!-- Vue recreation of OpenMAIC outlines-editor.tsx, Copyright 2026 THU-MAIC, MIT. -->
<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount,nextTick} from "vue";
import {useStudyroomPreparation} from "../../use-studyroom-preparation";
import {outlineApi} from "../../outline-runtime";
import {useOutlineGeneration} from "../../use-outline-generation";
import {useClassroomEvents} from '../../use-classroom-events';
import StudyroomGenerationFeedback from './StudyroomGenerationFeedback.vue';
import StudyroomPreparationFeedback from './StudyroomPreparationFeedback.vue';
import type {PreparationIdentity} from '../../use-studyroom-preparation';
import type {LearningDepth,OutlineDraft} from "../../../shared/types/outline";
const props=defineProps<{draftId?:string;initialQuestion?:string;sourceRoomId?:string}>();
const emit=defineEmits<{draft:[id:string];confirmed:[roomId:string]}>();
const editing=ref(false);const expanded=ref(false);const reviewButton=ref<HTMLButtonElement>();const collapseButton=ref<HTMLButtonElement>();
type ReviewTransition={finished:Promise<void>;skipTransition?:()=>void};
let activeTransition:ReviewTransition|undefined;let transitionVersion=0;
async function setExpanded(value:boolean){
 const current=++transitionVersion;activeTransition?.skipTransition?.();
 const change=async()=>{if(current!==transitionVersion)return;expanded.value=value;await nextTick();};
 const doc=document as Document&{startViewTransition?:(callback:()=>Promise<void>)=>ReviewTransition};
 if(!doc.startViewTransition||window.matchMedia('(prefers-reduced-motion: reduce)').matches){await change();return;}
 let transition:ReviewTransition|undefined;
 try{transition=doc.startViewTransition(change);activeTransition=transition;await transition.finished;}
 catch{if(current===transitionVersion&&expanded.value!==value)await change();}
 finally{if(activeTransition===transition)activeTransition=undefined;}
}
async function openReview(){await setExpanded(true);if(expanded.value)collapseButton.value?.focus();}
const localUnits=ref<Array<{id:string;title:string;objective:string}>>([]);const baseline=ref('[]');const editRevision=ref(0);const deleted=ref<{unit:{id:string;title:string;objective:string};index:number}|null>(null);const dragIndex=ref<number|null>(null);
const dirty=computed(()=>JSON.stringify(localUnits.value)!==baseline.value);
const valid=computed(()=>localUnits.value.length>0&&localUnits.value.length<=24&&localUnits.value.every(u=>u.title.trim()&&u.title.trim().length<=160&&u.objective.trim()&&u.objective.trim().length<=400));
function resizeField(event:Event){const el=event.target as HTMLTextAreaElement;el.style.height='auto';el.style.height=el.scrollHeight+'px';}
function resetEdits(){localUnits.value=(draft.value?.outline?.units??[]).map(({id,title,objective})=>({id,title,objective}));baseline.value=JSON.stringify(localUnits.value);editRevision.value=draft.value?.latestRevision??0;deleted.value=null;}
async function addUnit(index:number){if(localUnits.value.length>=24)return;localUnits.value.splice(index,0,{id:crypto.randomUUID(),title:'',objective:''});await nextTick();document.querySelectorAll<HTMLInputElement>('[aria-label="知识点标题"]')[index]?.focus();}
function moveUnit(from:number,to:number){if(reviewReadOnly.value||to<0||to>=localUnits.value.length)return;const [unit]=localUnits.value.splice(from,1);if(unit)localUnits.value.splice(to,0,unit);}
function startDrag(event:DragEvent,index:number){if(reviewReadOnly.value){event.preventDefault();return;}dragIndex.value=index;if(event.dataTransfer){event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',localUnits.value[index]!.id);}}
function dropUnit(event:DragEvent,index:number){if(reviewReadOnly.value)return;const id=event.dataTransfer?.getData('text/plain');const from=id?localUnits.value.findIndex(unit=>unit.id===id):dragIndex.value;if(from!==null&&from>=0)moveUnit(from,index);dragIndex.value=null;}
function removeUnit(index:number){const [unit]=localUnits.value.splice(index,1);if(unit)deleted.value={unit,index};}
function undoDelete(){if(!busy.value&&deleted.value){localUnits.value.splice(deleted.value.index,0,deleted.value.unit);deleted.value=null;}}
async function collapse(){await setExpanded(false);if(!expanded.value)reviewButton.value?.focus();}
async function saveManual(){
 if(!draft.value||!dirty.value||!valid.value||busy.value)return;
 const id=draft.value.id;busy.value=true;error.value='';
 try{
  const body={baseRevision:editRevision.value,units:localUnits.value.map(u=>({...u,title:u.title.trim(),objective:u.objective.trim()}))};
  const saved=await outlineApi.manualRevise(id,body,requestKey(JSON.stringify(body)));
  if(props.draftId!==id)return;
  const loaded=await load(id);
  if(loaded&&loaded.latestRevision===saved.revision){requestIdentity=undefined;resetEdits();}
 }catch(cause){
  if(props.draftId!==id)return;
  error.value=cause instanceof Error?cause.message:'保存失败，本地修改已保留';
  if(cause&&typeof cause==='object'&&'code' in cause&&cause.code==='OUTLINE_VERSION_CONFLICT')await load(id);
 }finally{busy.value=false;}
}
const feedbackInput=ref<HTMLTextAreaElement>();
watch(editing,async open=>{if(open){await nextTick();feedbackInput.value?.focus();}});
const question=ref(props.initialQuestion??"");const depth=ref<LearningDepth>("STANDARD");const feedback=ref("");
const draft=ref<OutlineDraft|null>(null);const busy=ref(false);const error=ref("");
const loadingDraft=ref(!!props.draftId);
async function reloadDraft(){if(!props.draftId)return;error.value="";loadingDraft.value=true;await load(props.draftId);}
const readyIdentity=ref<PreparationIdentity|null>(null);let entryTimer:ReturnType<typeof setTimeout>|undefined;
function enterReady(){const identity=readyIdentity.value;if(identity&&props.draftId===identity.draftId&&draft.value?.latestRevision===identity.revision&&draft.value?.confirmedRunId===identity.runId){clearTimeout(entryTimer);readyIdentity.value=null;emit('confirmed',identity.roomId);}}
const generation=useOutlineGeneration(draft,()=>{if(props.draftId)void load(props.draftId);});
const preparation=useStudyroomPreparation((id,signal)=>outlineApi.run(id,signal),identity=>{
 if(props.draftId===identity.draftId&&draft.value?.latestRevision===identity.revision&&draft.value?.confirmedRunId===identity.runId){readyIdentity.value=identity;entryTimer=setTimeout(enterReady,650);}
});
const preparing=preparation.waiting;const preparationError=preparation.error;
const preparationActive=computed(()=>!!draft.value?.confirmedRunId||preparing.value||!!readyIdentity.value||!!preparationError.value&&!!draft.value?.confirmedRunId);
function resumePreparation(){if(!draft.value?.confirmedRunId||!draft.value.confirmedRoomId||!props.draftId)return;preparation.start({draftId:props.draftId,revision:draft.value.latestRevision,runId:draft.value.confirmedRunId,roomId:draft.value.confirmedRoomId});}
const preparationEvents=useClassroomEvents();const preparationPhase=ref('QUEUED');
watch([()=>preparing.value,()=>draft.value?.confirmedRunId],([waiting,run])=>{
 preparationEvents.stop();preparationPhase.value='QUEUED';if(!waiting||!run)return;
 preparationEvents.start({streamId:`run:${run}`,path:`/studyroom-runs/${encodeURIComponent(run)}/events`,onSnapshot:snapshot=>{if(typeof snapshot.state.phase==='string')preparationPhase.value=snapshot.state.phase;preparation.refresh();},onEvent:event=>{if(typeof event.payload.phase==='string')preparationPhase.value=event.payload.phase;if(['content_published','failed'].includes(event.kind))preparation.refresh();}});
});
const options=[{value:"QUICK" as const,label:"快速总览",description:"核心概念、关键区别与一个例子"},{value:"STANDARD" as const,label:"标准学习",description:"概念、操作过程、实现与自检"},{value:"DEEP" as const,label:"深入掌握",description:"完整路线、边界、复杂度与实践"}];
const materialLabel=(kind:string)=>({PPT:'课件',ANIMATION:'动画',C_CODE:'C 代码'} as Record<string,string>)[kind]??kind;
const generating=computed(()=>!!draft.value?.pendingJobId||draft.value?.status==="GENERATING");
// Stream previews have no server IDs. Carry their slot keys into the validated
// revision once, then keep those keys attached to IDs during manual reordering.
const validatedRowKeys=ref<Record<string,string>>({});
const displayedUnits=computed(()=>generating.value
 ?Array.from({length:generation.units.value.length||3},(_,index)=>({id:`preview-${index}`,title:generation.units.value[index]?.title??'',objective:generation.units.value[index]?.objective??''}))
 :localUnits.value);
function rowKey(id:string,index:number){return generating.value?`stream:${draft.value?.pendingJobId}:${index}`:validatedRowKeys.value[id]??id;}
const reviewReadOnly=computed(()=>busy.value||generating.value||preparing.value||!!draft.value?.confirmedRunId);
const blocked=computed(()=>dirty.value||busy.value||preparing.value||generating.value||!!draft.value?.confirmedRunId||!draft.value?.outline||!!draft.value?.issues.some(i=>i.severity==="BLOCKER"));
let timer:ReturnType<typeof setTimeout>|undefined;let version=0;
let requestIdentity:{payload:string;key:string}|undefined;
function requestKey(payload:string){if(requestIdentity?.payload!==payload)requestIdentity={payload,key:crypto.randomUUID()};return requestIdentity.key;}
async function load(id:string){clearTimeout(timer);const current=++version;try{const loaded=await outlineApi.get(id);if(current!==version)return;const keepEdits=dirty.value;if(generating.value&&!loaded.pendingJobId&&loaded.status!=="GENERATING"&&loaded.outline){validatedRowKeys.value=Object.fromEntries(loaded.outline.units.map((unit,index)=>[unit.id,rowKey(unit.id,index)]));}draft.value=loaded;if(!keepEdits)resetEdits();if(!busy.value)depth.value=loaded.depth;if(loaded.confirmedRunId&&loaded.confirmedRoomId&&!preparing.value&&!readyIdentity.value)resumePreparation();if(loaded.pendingJobId)timer=setTimeout(()=>void load(id),document.visibilityState==="hidden"?10000:1800);return loaded;}catch(cause){if(current===version)error.value=cause instanceof Error?cause.message:"大纲加载失败，可刷新重试";}finally{if(current===version)loadingDraft.value=false;}}
watch(()=>props.draftId,id=>{transitionVersion++;activeTransition?.skipTransition?.();preparation.stop();preparationError.value='';clearTimeout(entryTimer);readyIdentity.value=null;version++;clearTimeout(timer);draft.value=null;validatedRowKeys.value={};localUnits.value=[];baseline.value='[]';expanded.value=false;loadingDraft.value=!!id;if(id)void load(id);},{immediate:true});
onBeforeUnmount(()=>{transitionVersion++;activeTransition?.skipTransition?.();preparation.stop();clearTimeout(entryTimer);version++;clearTimeout(timer);});
async function submit(){if(busy.value||preparing.value||generating.value||dirty.value||(props.draftId&&!feedback.value.trim()))return;busy.value=true;error.value="";try{
 if(props.draftId&&draft.value){const body={baseRevision:draft.value.latestRevision,feedback:feedback.value.trim(),depth:depth.value};await outlineApi.revise(props.draftId,body,requestKey(JSON.stringify(body)));feedback.value="";requestIdentity=undefined;await load(props.draftId);}
 else{const body={question:question.value.trim(),depth:depth.value,...(props.sourceRoomId?{sourceRoomId:props.sourceRoomId}:{})};const created=await outlineApi.create(body,requestKey(JSON.stringify(body)));requestIdentity=undefined;emit("draft",created.draftId);}
}catch(cause){error.value=cause instanceof Error?cause.message:"提交失败，请重试";}finally{busy.value=false;}}
async function confirm(){
 if(blocked.value||!draft.value||!draft.value.planHash)return;
 const snapshot=draft.value;busy.value=true;error.value="";
 try{
  const result=await outlineApi.confirm(snapshot.id,snapshot.latestRevision,requestKey(`confirm:${snapshot.id}:${snapshot.latestRevision}:${snapshot.planHash}`),snapshot.planHash!);
  if(draft.value!==snapshot||props.draftId!==snapshot.id)return;
  snapshot.confirmedRunId=result.runId;snapshot.confirmedRoomId=result.roomId;
  preparation.start({draftId:snapshot.id,revision:snapshot.latestRevision,runId:result.runId,roomId:result.roomId});
 }catch(cause){error.value=cause instanceof Error?cause.message:"确认失败，请刷新最新大纲后重试";}finally{busy.value=false;}
}
async function retry(){if(!props.draftId||busy.value)return;busy.value=true;error.value="";try{await outlineApi.retry(props.draftId,requestKey(`retry:${props.draftId}:${draft.value?.latestRevision}`));requestIdentity=undefined;await load(props.draftId);}catch(cause){error.value=cause instanceof Error?cause.message:"重试失败";}finally{busy.value=false;}}
async function restore(target:number){if(!draft.value||busy.value||generating.value||dirty.value)return;busy.value=true;error.value="";try{await outlineApi.restore(draft.value.id,draft.value.latestRevision,target,requestKey(`restore:${draft.value.id}:${draft.value.latestRevision}:${target}`));requestIdentity=undefined;await load(draft.value.id);}catch(cause){error.value=cause instanceof Error?cause.message:"恢复失败";}finally{busy.value=false;}}
</script>
<template>
 <section class="outline-flow" :class="{'is-waiting':generating||preparationActive,'is-entry':!draftId,'is-collapsed':draftId&&!expanded&&!preparationActive,'is-expanded':expanded&&!preparationActive,'is-preparing':preparationActive}" aria-label="学习大纲审阅">
  <div v-if="loadingDraft" class="outline-loading" role="status" aria-live="polite"><p>正在加载学习路线…</p><div class="outline-loading-rows" aria-hidden="true"><div v-for="index in 3" :key="index" class="outline-loading-row"><span class="unit-number">{{String(index).padStart(2,'0')}}</span><div class="loading-lines"><span/><span/></div></div></div></div>
  <div v-if="!loadingDraft&&draftId&&!draft&&error" class="outline-load-error" role="alert"><p>{{error}}</p><button type="button" @click="reloadDraft">重新加载大纲</button><a href="/studyroom">返回学习入口</a></div>
  <header v-show="!loadingDraft&&(!draftId||expanded)&&!preparationActive" class="outline-header">
   <div><p v-if="draftId" class="outline-kicker">{{preparing?'03 / 准备课堂':generating?'01 / 生成大纲':draft?.outline?'02 / 审阅大纲':'01 / 学习主题'}}</p><h2>{{generating?'正在编排学习路线':draft?.outline?.title||'先决定，这次想学到哪一步'}}</h2><p v-if="!draftId" class="entry-description">输入想学习的问题，先看看学习路线，再决定如何开始。</p></div>
   <div class="outline-header-actions"><span v-if="draft?.outline&&!generating" class="outline-count">{{localUnits.length}} 个知识点 · 第 {{draft.latestRevision}} 版</span><button v-if="draftId&&expanded" ref="collapseButton" type="button" class="review-toggle" @click="collapse">收起大纲 <span aria-hidden="true">↙</span></button></div>
  </header>
  <Transition name="review-card"><div v-if="!loadingDraft&&draftId&&draft&&!preparationActive&&!expanded" class="outline-compact"><StudyroomGenerationFeedback @review="openReview" :phase="generation.phase.value" :ready="!!draft?.outline&&!generating" :title="draft?.outline?.title" :units="generating?generation.units.value:localUnits" :connection-error="generation.connectionError.value"><button ref="reviewButton" type="button" class="review-trigger" :aria-expanded="expanded" aria-controls="outline-review-form" @click="openReview"><span aria-hidden="true">↗</span> {{generating?'点击审阅':'审阅大纲'}}</button></StudyroomGenerationFeedback><p v-if="dirty" class="compact-note">修改尚未保存，展开后可继续编辑。</p><p v-if="draft?.generation?.status==='FAILED'" role="alert" class="compact-note">{{draft.generation.failure_message}}</p><a v-if="draft&&!draft.outline&&!generating" href="/studyroom" class="compact-note">更换学习问题</a></div></Transition>
  <StudyroomPreparationFeedback v-if="!loadingDraft&&preparationActive" :phase="preparationPhase" :title="draft?.outline?.title" :error="preparationError" :ready="!!readyIdentity"><button v-if="readyIdentity" type="button" class="primary" @click="enterReady">进入课堂</button><template v-else-if="preparationError"><button type="button" @click="resumePreparation">重新检查准备状态</button><button v-if="draft?.confirmedRoomId" type="button" @click="emit('confirmed',draft.confirmedRoomId)">进入课堂处理</button></template></StudyroomPreparationFeedback>
  <Transition name="review-panel">
  <form id="outline-review-form" v-show="!loadingDraft&&(!draftId||expanded)&&!preparationActive" class="outline-form" @submit.prevent="submit">
   <div class="outline-body" tabindex="0" aria-label="大纲内容">
    <div v-if="!draftId" class="outline-create">
     <label class="field-label" for="outline-question">课程问题</label><textarea id="outline-question" v-model="question" maxlength="4000" placeholder="例如：栈的知识有哪些？" required/>
    </div>
    <details v-show="!preparing" class="outline-depth" :open="!draftId">
     <summary>学习详细程度 <span>{{options.find(option=>option.value===depth)?.label}}</span></summary>
     <fieldset :disabled="busy||generating||preparing||!!draft?.confirmedRunId"><legend class="sr-only">学习详细程度</legend><div class="depth-options"><label v-for="option in options" :key="option.value" :class="{selected:depth===option.value}"><input v-model="depth" name="depth" type="radio" :value="option.value"><strong>{{option.label}}</strong><span>{{option.description}}</span></label></div></fieldset>
    </details>

    <div v-if="(generating||draft?.outline)&&!preparing" class="outline-review" :class="{'is-generating':generating}" :aria-busy="generating">
     <p class="review-hint" role="status">{{generating?`正在生成，已编排 ${generation.units.value.length} 个知识点。完成检查后即可编辑。`:'按知识点查看目标与材料。展开可查看具体讲解安排。'}}</p>
     <p v-if="dirty" role="status">有未保存的修改，请先保存再确认或请 AI 调整。</p>
     <div v-if="!draft?.confirmedRunId" class="manual-actions"><button class="insert-divider" type="button" aria-label="添加知识点" @click="addUnit(0)" :disabled="reviewReadOnly||localUnits.length>=24"><span aria-hidden="true">＋</span><span class="sr-only">添加知识点</span></button><button v-if="deleted" type="button" :disabled="reviewReadOnly" @click="undoDelete">撤销删除</button></div>
     <ol class="outline-units">
      <template v-for="(unit,index) in displayedUnits" :key="rowKey(unit.id,index)"><li class="outline-unit" :class="{'is-dragging':dragIndex===index,'is-skeleton':generating&&!unit.title}" @dragenter.prevent @dragover.prevent @drop.prevent="dropUnit($event,index)">
       <button v-if="!draft?.confirmedRunId" class="unit-drag" type="button" aria-label="拖动知识点" aria-keyshortcuts="Control+ArrowUp Control+ArrowDown Meta+ArrowUp Meta+ArrowDown" @keydown.ctrl.up.prevent="moveUnit(index,index-1)" @keydown.ctrl.down.prevent="moveUnit(index,index+1)" @keydown.meta.up.prevent="moveUnit(index,index-1)" @keydown.meta.down.prevent="moveUnit(index,index+1)" :draggable="!reviewReadOnly" :disabled="reviewReadOnly" @dragstart="startDrag($event,index)" @dragend="dragIndex=null"><svg width="16" height="20" viewBox="0 0 16 20" aria-hidden="true"><circle v-for="n in 6" :key="n" :cx="n%2?5:11" :cy="4+Math.floor((n-1)/2)*6" r="1.4" fill="currentColor"/></svg></button><span class="unit-number" aria-hidden="true">{{String(index+1).padStart(2,'0')}}</span>
       <button v-if="!draft?.confirmedRunId" class="unit-delete" type="button" aria-label="删除知识点" :disabled="reviewReadOnly" @click="removeUnit(index)"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/></svg></button><div class="unit-content"><div class="unit-heading"><h3 v-if="draft?.confirmedRunId">{{unit.title}}</h3><textarea rows="1" @input="resizeField" class="unit-title-input" v-else v-model="unit.title" aria-label="知识点标题" :placeholder="generating?'':'输入知识点标题'" :disabled="reviewReadOnly" maxlength="160"/><div v-if="(draft?.outline?.units.find(u=>u.id===unit.id)?.materials??[]).length" class="unit-materials"><span v-for="(material,i) in (draft?.outline?.units.find(u=>u.id===unit.id)?.materials??[])" :key="i" :class="{'material-unavailable':material.availability==='UNAVAILABLE'}">{{materialLabel(material.kind)}}{{material.availability==='UNAVAILABLE'?' · 暂无匹配':''}}</span></div></div><p v-if="draft?.confirmedRunId" class="unit-objective">{{unit.objective}}</p><textarea rows="1" @input="resizeField" v-else v-model="unit.objective" aria-label="知识点目标" :placeholder="generating?'':'简述想学会什么'" :disabled="reviewReadOnly" maxlength="400"/>
        <div v-if="!draft?.confirmedRunId" class="row-keyboard-actions"><button type="button" aria-label="上移知识点" :disabled="reviewReadOnly||index===0" @click="moveUnit(index,index-1)">上移</button><button type="button" aria-label="下移知识点" :disabled="reviewReadOnly||index===localUnits.length-1" @click="moveUnit(index,index+1)">下移</button></div>
        <details v-if="!generating&&(draft?.fragmentPreview?.[unit.id]?.length||(draft?.outline?.units.find(u=>u.id===unit.id)?.materials??[]).length)" class="unit-details"><summary>查看讲解安排</summary><ol v-if="draft?.fragmentPreview?.[unit.id]?.length"><li v-for="fragment in draft?.fragmentPreview[unit.id]" :key="fragment.key">{{fragment.objective}}</li></ol><ul v-if="(draft?.outline?.units.find(u=>u.id===unit.id)?.materials??[]).length"><li v-for="(material,i) in (draft?.outline?.units.find(u=>u.id===unit.id)?.materials??[])" :key="i">{{materialLabel(material.kind)}} · {{material.purpose}}</li></ul></details>
       </div>
      </li><li v-if="!draft?.confirmedRunId" class="insertion-row"><button type="button" class="insert-divider" aria-label="添加知识点" :disabled="reviewReadOnly||localUnits.length>=24" @click="addUnit(index+1)"><span aria-hidden="true">＋</span><span class="sr-only">添加知识点</span></button></li></template>
     </ol>
     <details v-if="!generating&&draft?.outline&&(draft.outline.objectives.length||draft.outline.exclusions.length)" data-course-goals class="outline-secondary"><summary>课程总体目标与范围</summary><ul><li v-for="goal in draft.outline.objectives" :key="goal">{{goal}}</li></ul><p v-if="draft.outline.exclusions.length">本次不覆盖：{{draft.outline.exclusions.join('；')}}</p><p>{{draft.outline.coverageAssessment==='REVIEWED_FRAMEWORK'?'已对照课程框架':'基于当前授权资料规划'}}</p></details>
     <ul v-if="!generating&&draft?.issues.length" class="outline-issues" aria-label="大纲检查提示"><li v-for="(issue,index) in draft.issues" :key="index">{{issue.message}}</li></ul>
     <div v-if="editing&&!generating&&!draft?.confirmedRunId" class="outline-editor"><label class="field-label" for="outline-feedback">修改意见</label><textarea ref="feedbackInput" id="outline-feedback" v-model="feedback" maxlength="4000" :disabled="reviewReadOnly" placeholder="例如：先讲顺序栈，加入入栈出栈动画。"/><button :disabled="reviewReadOnly||dirty||!feedback.trim()">{{busy?'正在提交…':'按意见调整大纲'}}</button></div>
     <details v-if="!generating&&draft?.changes.length&&draft.latestRevision>1" class="outline-secondary"><summary>本版调整</summary><p v-for="(change,index) in draft.changes" :key="index">{{change.summary}}</p></details>
     <details v-if="!generating&&draft?.messages?.length" class="outline-secondary"><summary>大纲讨论记录</summary><p v-for="(message,index) in draft.messages" :key="index"><strong>{{message.role==='USER'?'你':'AI'}}：</strong>{{message.content}}</p></details>
     <details v-if="!generating&&draft?.revisions?.length" class="outline-secondary"><summary>历史版本</summary><p v-for="revision in draft.revisions" :key="revision.revision">第 {{revision.revision}} 版 <button v-if="revision.revision!==draft.latestRevision" type="button" :disabled="reviewReadOnly||dirty" @click="restore(revision.revision)">恢复为新版本</button></p></details>
    </div>
    <div v-if="draft?.generation?.status==='FAILED'" class="outline-failure" role="alert"><p>{{draft.generation.failure_message}}<span v-if="draft.outline">上一版大纲已保留。</span></p><button type="button" :disabled="busy||generating||preparing" @click="retry">重新生成（可能使用模型额度）</button></div>
    <p v-if="preparationError" role="alert">{{preparationError}}<button v-if="draft?.confirmedRoomId" type="button" @click="emit('confirmed',draft.confirmedRoomId)">查看课堂准备状态</button></p>
    <p v-if="error" role="alert" class="outline-error">{{error}}</p>
   </div>
   <footer class="outline-actions"><template v-if="dirty"><button type="button" :disabled="busy||!valid" @click="saveManual">保存修改</button><button type="button" :disabled="busy" @click="resetEdits">撤销修改</button></template>
    <a v-if="draftId&&!generating&&!preparing&&!draft?.outline" href="/studyroom">更换学习问题</a>
    <span v-if="generating||preparing" class="action-hint">{{preparing?'首段就绪后自动进入课堂':'完成检查后即可确认大纲'}}</span>
    <button v-if="!draftId" class="primary" :disabled="busy||!question.trim()">{{busy?'正在提交…':'生成大纲'}}</button>
    <template v-else>
     <button v-if="(generating||draft?.outline)&&!draft?.confirmedRunId&&!preparing" type="button" :disabled="reviewReadOnly" :aria-expanded="editing" @click="editing=!editing">{{editing?'收起修改':'修改大纲'}}</button>
     <button v-if="draft?.confirmedRoomId&&!preparing" class="primary" type="button" @click="emit('confirmed',draft.confirmedRoomId)">进入已确认课堂</button>
     <button v-else class="primary" type="button" data-action="confirm" :disabled="blocked" @click="confirm">{{preparing?'正在准备课堂…':generating?'正在生成大纲…':'确认大纲，开始学习'}}</button>
     <button v-if="draft?.sourceRoomId&&!draft.confirmedRunId" type="button" @click="emit('confirmed',draft.sourceRoomId)">返回当前课堂</button>
    </template>
   </footer>
  </form>
  </Transition>
 </section>
</template>
<style scoped src="./studyroom-outline.css"></style>
