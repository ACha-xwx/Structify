<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount,nextTick} from "vue";
import {useStudyroomPreparation} from "../../use-studyroom-preparation";
import {outlineApi} from "../../outline-runtime";
import {useOutlineGeneration} from "../../use-outline-generation";
import {useClassroomEvents} from '../../use-classroom-events';
import StudyroomGenerationFeedback from './StudyroomGenerationFeedback.vue';
import type {LearningDepth,OutlineDraft} from "../../../shared/types/outline";
const props=defineProps<{draftId?:string;initialQuestion?:string;sourceRoomId?:string}>();
const emit=defineEmits<{draft:[id:string];confirmed:[roomId:string]}>();
const editing=ref(false);
const feedbackInput=ref<HTMLTextAreaElement>();
watch(editing,async open=>{if(open){await nextTick();feedbackInput.value?.focus();}});
const question=ref(props.initialQuestion??"");const depth=ref<LearningDepth>("STANDARD");const feedback=ref("");
const draft=ref<OutlineDraft|null>(null);const busy=ref(false);const error=ref("");
const generation=useOutlineGeneration(draft,()=>{if(props.draftId)void load(props.draftId);});
const preparation=useStudyroomPreparation((id,signal)=>outlineApi.run(id,signal),identity=>{
 if(props.draftId===identity.draftId&&draft.value?.latestRevision===identity.revision&&draft.value?.confirmedRunId===identity.runId)emit("confirmed",identity.roomId);
});
const preparing=preparation.waiting;const preparationError=preparation.error;
const preparationEvents=useClassroomEvents();const preparationPhase=ref('QUEUED');
watch([()=>preparing.value,()=>draft.value?.confirmedRunId],([waiting,run])=>{
 preparationEvents.stop();preparationPhase.value='QUEUED';if(!waiting||!run)return;
 preparationEvents.start({streamId:`run:${run}`,path:`/studyroom-runs/${encodeURIComponent(run)}/events`,onSnapshot:snapshot=>{if(typeof snapshot.state.phase==='string')preparationPhase.value=snapshot.state.phase;preparation.refresh();},onEvent:event=>{if(typeof event.payload.phase==='string')preparationPhase.value=event.payload.phase;if(['content_published','failed'].includes(event.kind))preparation.refresh();}});
});
const options=[{value:"QUICK" as const,label:"快速总览",description:"核心概念、关键区别与一个例子"},{value:"STANDARD" as const,label:"标准学习",description:"概念、操作过程、实现与自检"},{value:"DEEP" as const,label:"深入掌握",description:"完整路线、边界、复杂度与实践"}];
const materialLabel=(kind:string)=>({PPT:'课件',ANIMATION:'动画',C_CODE:'C 代码'} as Record<string,string>)[kind]??kind;
const generating=computed(()=>!!draft.value?.pendingJobId||draft.value?.status==="GENERATING");
const blocked=computed(()=>busy.value||preparing.value||generating.value||!!draft.value?.confirmedRunId||!draft.value?.outline||!!draft.value?.issues.some(i=>i.severity==="BLOCKER"));
let timer:ReturnType<typeof setTimeout>|undefined;let version=0;
let requestIdentity:{payload:string;key:string}|undefined;
function requestKey(payload:string){if(requestIdentity?.payload!==payload)requestIdentity={payload,key:crypto.randomUUID()};return requestIdentity.key;}
async function load(id:string){clearTimeout(timer);const current=++version;try{const loaded=await outlineApi.get(id);if(current!==version)return;draft.value=loaded;if(!busy.value)depth.value=loaded.depth;if(loaded.pendingJobId)timer=setTimeout(()=>void load(id),document.visibilityState==="hidden"?10000:1800);}catch(cause){if(current===version)error.value=cause instanceof Error?cause.message:"大纲加载失败，可刷新重试";}}
watch(()=>props.draftId,id=>{preparation.stop();version++;clearTimeout(timer);draft.value=null;if(id)void load(id);},{immediate:true});
onBeforeUnmount(()=>{preparation.stop();version++;clearTimeout(timer);});
async function submit(){if(busy.value||preparing.value||generating.value)return;busy.value=true;error.value="";try{
 if(props.draftId&&draft.value){const body={baseRevision:draft.value.latestRevision,feedback:feedback.value.trim()||`调整为${options.find(o=>o.value===depth.value)?.label}`,depth:depth.value};await outlineApi.revise(props.draftId,body,requestKey(JSON.stringify(body)));feedback.value="";requestIdentity=undefined;await load(props.draftId);}
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
async function restore(target:number){if(!draft.value||busy.value||generating.value)return;busy.value=true;error.value="";try{await outlineApi.restore(draft.value.id,draft.value.latestRevision,target,requestKey(`restore:${draft.value.id}:${draft.value.latestRevision}:${target}`));requestIdentity=undefined;await load(draft.value.id);}catch(cause){error.value=cause instanceof Error?cause.message:"恢复失败";}finally{busy.value=false;}}
</script>
<template>
 <section class="outline-flow" :class="{'is-waiting':generating||preparing,'is-entry':!draftId}" aria-label="学习大纲审阅">
  <header class="outline-header">
   <div><p v-if="draftId" class="outline-kicker">{{preparing?'03 / 准备课堂':generating?'01 / 生成大纲':draft?.outline?'02 / 审阅大纲':'01 / 学习主题'}}</p><h2>{{generating?'正在编排学习路线':draft?.outline?.title||'先决定，这次想学到哪一步'}}</h2><p v-if="!draftId" class="entry-description">输入想学习的问题，先看看学习路线，再决定如何开始。</p></div>
   <span v-if="draft?.outline&&!generating" class="outline-count">{{draft.outline.units.length}} 个知识点 · 第 {{draft.latestRevision}} 版</span>
  </header>
  <form class="outline-form" @submit.prevent="submit">
   <div class="outline-body" tabindex="0" aria-label="大纲内容">
    <div v-if="!draftId" class="outline-create">
     <label class="field-label" for="outline-question">课程问题</label><textarea id="outline-question" v-model="question" maxlength="4000" placeholder="例如：栈的知识有哪些？" required/>
    </div>
    <details v-show="!generating&&!preparing" class="outline-depth" :open="!draftId">
     <summary>学习详细程度 <span>{{options.find(option=>option.value===depth)?.label}}</span></summary>
     <fieldset :disabled="busy||generating||preparing||!!draft?.confirmedRunId"><legend class="sr-only">学习详细程度</legend><div class="depth-options"><label v-for="option in options" :key="option.value" :class="{selected:depth===option.value}"><input v-model="depth" name="depth" type="radio" :value="option.value"><strong>{{option.label}}</strong><span>{{option.description}}</span></label></div></fieldset>
    </details>
    <StudyroomGenerationFeedback v-if="preparing" preparing :phase="preparationPhase"/>
    <StudyroomGenerationFeedback v-else-if="generating" :phase="generation.phase.value" :units="generation.units.value" :connection-error="generation.connectionError.value"/>
    <div v-if="draft?.outline&&!preparing&&!generating" class="outline-review">
     <p class="review-hint">按知识点查看目标与材料。展开可查看具体讲解安排。</p>
     <ol class="outline-units">
      <li v-for="(unit,index) in draft.outline.units" :key="unit.id" class="outline-unit">
       <span class="unit-number" aria-hidden="true">{{String(index+1).padStart(2,'0')}}</span>
       <div class="unit-content"><div class="unit-heading"><h3>{{unit.title}}</h3><div v-if="unit.materials.length" class="unit-materials"><span v-for="(material,i) in unit.materials" :key="i" :class="{'material-unavailable':material.availability==='UNAVAILABLE'}">{{materialLabel(material.kind)}}{{material.availability==='UNAVAILABLE'?' · 暂无匹配':''}}</span></div></div><p class="unit-objective">{{unit.objective}}</p>
        <details v-if="draft.fragmentPreview?.[unit.id]?.length||unit.materials.length" class="unit-details"><summary>查看讲解安排</summary><ol v-if="draft.fragmentPreview?.[unit.id]?.length"><li v-for="fragment in draft.fragmentPreview[unit.id]" :key="fragment.key">{{fragment.objective}}</li></ol><ul v-if="unit.materials.length"><li v-for="(material,i) in unit.materials" :key="i">{{materialLabel(material.kind)}} · {{material.purpose}}</li></ul></details>
       </div>
      </li>
     </ol>
     <details v-if="draft.outline.objectives.length||draft.outline.exclusions.length" data-course-goals class="outline-secondary"><summary>课程总体目标与范围</summary><ul><li v-for="goal in draft.outline.objectives" :key="goal">{{goal}}</li></ul><p v-if="draft.outline.exclusions.length">本次不覆盖：{{draft.outline.exclusions.join('；')}}</p><p>{{draft.outline.coverageAssessment==='REVIEWED_FRAMEWORK'?'已对照课程框架':'基于当前授权资料规划'}}</p></details>
     <ul v-if="draft.issues.length" class="outline-issues" aria-label="大纲检查提示"><li v-for="(issue,index) in draft.issues" :key="index">{{issue.message}}</li></ul>
     <div v-if="editing&&!draft.confirmedRunId" class="outline-editor"><label class="field-label" for="outline-feedback">修改意见</label><textarea ref="feedbackInput" id="outline-feedback" v-model="feedback" maxlength="4000" :disabled="busy||generating||preparing" placeholder="例如：先讲顺序栈，加入入栈出栈动画。"/><button :disabled="busy||generating||preparing">{{busy?'正在提交…':'按意见调整大纲'}}</button></div>
     <details v-if="draft.changes.length&&draft.latestRevision>1" class="outline-secondary"><summary>本版调整</summary><p v-for="(change,index) in draft.changes" :key="index">{{change.summary}}</p></details>
     <details v-if="draft.messages?.length" class="outline-secondary"><summary>大纲讨论记录</summary><p v-for="(message,index) in draft.messages" :key="index"><strong>{{message.role==='USER'?'你':'AI'}}：</strong>{{message.content}}</p></details>
     <details v-if="draft.revisions?.length" class="outline-secondary"><summary>历史版本</summary><p v-for="revision in draft.revisions" :key="revision.revision">第 {{revision.revision}} 版 <button v-if="revision.revision!==draft.latestRevision" type="button" :disabled="busy||generating||preparing" @click="restore(revision.revision)">恢复为新版本</button></p></details>
    </div>
    <div v-if="draft?.generation?.status==='FAILED'" class="outline-failure" role="alert"><p>{{draft.generation.failure_message}}<span v-if="draft.outline">上一版大纲已保留。</span></p><button type="button" :disabled="busy||generating||preparing" @click="retry">重新生成（可能使用模型额度）</button></div>
    <p v-if="preparationError" role="alert">{{preparationError}}<button v-if="draft?.confirmedRoomId" type="button" @click="emit('confirmed',draft.confirmedRoomId)">查看课堂准备状态</button></p>
    <p v-if="error" role="alert" class="outline-error">{{error}}</p>
   </div>
   <footer class="outline-actions">
    <a v-if="draftId&&!generating&&!preparing&&!draft?.outline" href="/studyroom">更换学习问题</a>
    <span v-if="generating||preparing" class="action-hint">{{preparing?'首段就绪后自动进入课堂':'完成检查后即可确认大纲'}}</span>
    <button v-if="!draftId" class="primary" :disabled="busy||!question.trim()">{{busy?'正在提交…':'生成大纲'}}</button>
    <template v-else>
     <button v-if="draft?.outline&&!draft.confirmedRunId&&!generating&&!preparing" type="button" :aria-expanded="editing" @click="editing=!editing">{{editing?'收起修改':'修改大纲'}}</button>
     <button v-if="draft?.confirmedRoomId&&!preparing" class="primary" type="button" @click="emit('confirmed',draft.confirmedRoomId)">进入已确认课堂</button>
     <button v-else class="primary" type="button" data-action="confirm" :disabled="blocked" @click="confirm">{{preparing?'正在准备课堂…':generating?'正在生成大纲…':'确认大纲，开始学习'}}</button>
     <button v-if="draft?.sourceRoomId&&!draft.confirmedRunId" type="button" @click="emit('confirmed',draft.sourceRoomId)">返回当前课堂</button>
    </template>
   </footer>
  </form>
 </section>
</template>
<style scoped src="./studyroom-outline.css"></style>
