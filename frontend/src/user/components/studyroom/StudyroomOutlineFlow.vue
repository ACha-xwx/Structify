<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount} from "vue";
import {useStudyroomPreparation} from "../../use-studyroom-preparation";
import {outlineApi} from "../../outline-runtime";
import {useOutlineGeneration} from "../../use-outline-generation";
import {useClassroomEvents} from '../../use-classroom-events';
import StudyroomGenerationFeedback from './StudyroomGenerationFeedback.vue';
import type {LearningDepth,OutlineDraft} from "../../../shared/types/outline";
const props=defineProps<{draftId?:string;initialQuestion?:string;sourceRoomId?:string}>();
const emit=defineEmits<{draft:[id:string];confirmed:[roomId:string]}>();
const question=ref(props.initialQuestion??"");const depth=ref<LearningDepth>("STANDARD");const feedback=ref("");
const draft=ref<OutlineDraft|null>(null);const busy=ref(false);const error=ref("");
const generation=useOutlineGeneration(draft,()=>{if(props.draftId)void load(props.draftId);});
const preparation=useStudyroomPreparation((id,signal)=>outlineApi.run(id,signal),identity=>{
 if(props.draftId===identity.draftId&&draft.value?.latestRevision===identity.revision&&draft.value?.confirmedRunId===identity.runId)emit("confirmed",identity.roomId);
});
const preparing=preparation.waiting;const preparationError=preparation.error;
const preparationEvents=useClassroomEvents();const preparationPhase=ref('QUEUED');
watch(()=>[preparing.value,draft.value?.confirmedRunId] as const,([waiting,run])=>{
 preparationEvents.stop();preparationPhase.value='QUEUED';if(!waiting||!run)return;
 preparationEvents.start({streamId:`run:${run}`,path:`/studyroom-runs/${encodeURIComponent(run)}/events`,onSnapshot:snapshot=>{if(typeof snapshot.state.phase==='string')preparationPhase.value=snapshot.state.phase;preparation.refresh();},onEvent:event=>{if(typeof event.payload.phase==='string')preparationPhase.value=event.payload.phase;if(['content_published','failed'].includes(event.kind))preparation.refresh();}});
});
const options=[{value:"QUICK" as const,label:"快速总览",description:"核心概念、关键区别与一个例子"},{value:"STANDARD" as const,label:"标准学习",description:"概念、操作过程、实现与自检"},{value:"DEEP" as const,label:"深入掌握",description:"完整路线、边界、复杂度与实践"}];
const generating=computed(()=>!!draft.value?.pendingJobId||draft.value?.status==="GENERATING");
const blocked=computed(()=>busy.value||preparing.value||generating.value||!!draft.value?.confirmedRunId||!draft.value?.outline||!!draft.value?.issues.some(i=>i.severity==="BLOCKER"));
let timer:ReturnType<typeof setTimeout>|undefined;let version=0;
let requestIdentity:{payload:string;key:string}|undefined;
function requestKey(payload:string){if(requestIdentity?.payload!==payload)requestIdentity={payload,key:crypto.randomUUID()};return requestIdentity.key;}
async function load(id:string){const current=++version;try{const loaded=await outlineApi.get(id);if(current!==version)return;draft.value=loaded;if(!busy.value)depth.value=loaded.depth;if(loaded.pendingJobId)timer=setTimeout(()=>void load(id),document.visibilityState==="hidden"?10000:1800);}catch(cause){if(current===version)error.value=cause instanceof Error?cause.message:"大纲加载失败，可刷新重试";}}
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
 <section class="outline-flow" aria-label="学习大纲审阅">
  <header><p class="outline-kicker">01 选择深度 · 02 审阅大纲 · 03 开始学习</p><h2>{{ draft?.outline?.title||"先决定，这次想学到哪一步" }}</h2><p>先生成学习路线。你可以提出修改意见，确认后再准备讲解与材料。</p></header>
  <StudyroomGenerationFeedback v-if="preparing" preparing :phase="preparationPhase"/>
  <p v-if="preparationError" role="alert">{{preparationError}}<button v-if="draft?.confirmedRoomId" type="button" @click="emit('confirmed',draft.confirmedRoomId)">查看课堂准备状态</button></p>
  <form @submit.prevent="submit">
   <fieldset :disabled="busy||generating||preparing"><legend>学习详细程度</legend><div class="depth-options"><label v-for="option in options" :key="option.value" :class="{selected:depth===option.value}"><input v-model="depth" name="depth" type="radio" :value="option.value"><strong>{{option.label}}</strong><span>{{option.description}}</span></label></div></fieldset>
   <template v-if="!draftId"><label class="field-label" for="outline-question">课程问题</label><textarea id="outline-question" v-model="question" maxlength="4000" placeholder="例如：栈的知识有哪些？" required/><button class="primary" :disabled="busy||!question.trim()">{{busy?"正在提交…":"生成大纲"}}</button></template>
   <template v-else>
    <StudyroomGenerationFeedback v-if="generating" :phase="generation.phase.value" :units="generation.units.value" :connection-error="generation.connectionError.value"/>
    <template v-if="draft?.outline">
     <div class="outline-meta"><span>第 {{draft.latestRevision}} 版</span><span>{{draft.outline.units.length}} 个知识点</span><span>{{draft.outline.coverageAssessment==='REVIEWED_FRAMEWORK'?'已对照课程框架':'基于当前授权资料规划'}}</span></div>
     <p v-for="goal in draft.outline.objectives" :key="goal">学习目标：{{goal}}</p>
     <ol class="outline-units"><li v-for="unit in draft.outline.units" :key="unit.id"><h3>{{unit.title}}</h3><p>{{unit.objective}}</p><details v-if="draft.fragmentPreview?.[unit.id]?.length"><summary>查看讲解安排</summary><ol><li v-for="fragment in draft.fragmentPreview[unit.id]" :key="fragment.key">{{fragment.objective}}</li></ol></details><ul v-if="unit.materials.length"><li v-for="(material,materialIndex) in unit.materials" :key="`${material.kind}:${materialIndex}`">{{({PPT:'课件',ANIMATION:'动画',C_CODE:'C 代码'} as Record<string,string>)[material.kind]}} · {{material.purpose}}<span v-if="material.availability==='UNAVAILABLE'">（暂无匹配资源）</span></li></ul></li></ol>
     <details v-if="draft.changes.length&&draft.latestRevision>1"><summary>本版调整</summary><p v-for="(change,index) in draft.changes" :key="index">{{change.summary}}</p></details>
     <p v-if="draft.outline.exclusions.length">本次不覆盖：{{draft.outline.exclusions.join('；')}}</p>
     <ul v-if="draft.issues.length" class="outline-issues"><li v-for="(issue,index) in draft.issues" :key="index">{{issue.message}}</li></ul>
     <details v-if="draft.messages?.length"><summary>大纲讨论记录</summary><p v-for="(message,index) in draft.messages" :key="index"><strong>{{message.role==='USER'?'你':'AI'}}：</strong>{{message.content}}</p></details>
     <label class="field-label" for="outline-feedback">修改意见</label><textarea id="outline-feedback" v-model="feedback" maxlength="4000" :disabled="busy||generating||preparing" placeholder="例如：先讲顺序栈，加入入栈出栈动画，再给一个 C 代码实验。"/>
     <div class="outline-actions"><button :disabled="busy||generating||preparing">{{busy?'正在提交…':'按意见调整大纲'}}</button><button v-if="draft.confirmedRoomId" class="primary" type="button" @click="emit('confirmed',draft.confirmedRoomId)">进入已确认课堂</button><button v-else class="primary" type="button" data-action="confirm" :disabled="blocked" @click="confirm">确认大纲，开始学习</button></div>
    </template>
    <details v-if="draft?.revisions?.length"><summary>历史版本</summary><p v-for="revision in draft.revisions" :key="revision.revision">第 {{revision.revision}} 版 <button v-if="revision.revision!==draft.latestRevision" type="button" :disabled="busy||generating||preparing" @click="restore(revision.revision)">恢复为新版本</button></p></details>
    <button v-if="draft?.generation?.status==='FAILED'" type="button" :disabled="busy||generating||preparing" @click="retry">重新生成（可能使用模型额度）</button>
    <p v-if="draft?.generation?.status==='FAILED'" role="alert">{{draft.generation.failure_message}}<span v-if="draft.outline">上一版大纲已保留。</span></p>
   </template>
   <button v-if="draft?.sourceRoomId&&!draft.confirmedRoomId" type="button" @click="emit('confirmed',draft.sourceRoomId)">返回当前课堂</button>
   <p v-if="error" role="alert" class="outline-error">{{error}}</p>
  </form>
 </section>
</template>
<style scoped>
.outline-flow{border:1px solid var(--line);border-radius:var(--radius-lg);background:var(--surface);padding:24px;max-width:1040px;width:100%;margin:auto;box-sizing:border-box}.outline-flow h2{font-size:22px;margin:8px 0}.outline-flow p{line-height:1.65}.outline-kicker,.outline-meta{color:var(--text-muted);font-size:12px}.outline-flow fieldset{border:0;padding:0;margin:24px 0}.outline-flow legend,.field-label{font-size:13px;font-weight:600;margin-bottom:10px}.depth-options{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.depth-options label{display:grid;grid-template-columns:auto 1fr;gap:9px;padding:16px;border:1px solid var(--line);border-radius:var(--radius-md);cursor:pointer}.depth-options label.selected{border-color:var(--text);background:var(--surface-subtle)}.depth-options span{grid-column:2;font-size:12px;color:var(--text-muted);line-height:1.6}.field-label{display:block;margin-top:20px}.outline-flow textarea{box-sizing:border-box;width:100%;min-height:92px;resize:vertical;border:1px solid var(--line);border-radius:var(--radius-md);padding:12px;background:var(--bg);color:var(--text);font:inherit;line-height:1.6}.outline-flow button{padding:11px 18px;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface);color:var(--text);font:inherit;cursor:pointer;margin-top:12px}.outline-flow button.primary{background:var(--text);color:var(--surface)}.outline-flow button:disabled{opacity:.5;cursor:default}.outline-flow :focus-visible{outline:2px solid var(--text);outline-offset:3px}.outline-meta,.outline-actions{display:flex;gap:16px;flex-wrap:wrap}.outline-meta{padding:18px 0 4px}.outline-units{padding-left:24px}.outline-units>li{padding:14px 0;border-bottom:1px solid var(--line)}.outline-units h3{margin:0;font-size:16px}.outline-units p{margin:8px 0}.outline-units ul{font-size:13px;color:var(--text-muted);padding-left:18px;line-height:1.9}.outline-flow details{margin:18px 0;font-size:13px}.outline-flow summary{cursor:pointer}.outline-status,.outline-issues{padding:16px;background:var(--surface-subtle);border-radius:var(--radius-md)}.outline-error{color:var(--text);border-left:3px solid currentColor;padding-left:12px}@media(max-width:640px){.outline-flow{padding:16px}.depth-options{grid-template-columns:1fr}.outline-actions button{width:100%}}
</style>
