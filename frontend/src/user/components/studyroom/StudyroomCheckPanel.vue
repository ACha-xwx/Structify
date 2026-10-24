<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue';
import {classroomCheckApi} from '../../classroom-check-runtime';
import type {CheckState,CheckAttempt} from '../../../shared/types/classroom-experience';
const props=withDefaults(defineProps<{runId:string;unitId:string;canPrepare:boolean;active?:boolean}>(),{active:true});
const state=ref<CheckState|null>(null),error=ref(''),busy=ref('');
const selected=ref<Record<string,string>>({});const redo=ref(new Set<string>());
const questionIndex=ref(0);
const questionCount=computed(()=>state.value?.questions.length??0);
const question=computed(()=>state.value?.questions[questionIndex.value]);
let epoch=0;let readGeneration=0;let controller:AbortController|undefined;let timer:ReturnType<typeof setTimeout>|undefined;
const keys=new Map<string,string>();
function key(operation:string){if(!keys.has(operation))keys.set(operation,crypto.randomUUID());return keys.get(operation)!;}
function last(id:string):CheckAttempt|undefined {return redo.value.has(id)?undefined:state.value?.attempts.filter(attempt=>attempt.checkId===id).at(-1);}
function cancelRead() {
 readGeneration++;
 clearTimeout(timer);
 controller?.abort();
}
function poll(current=epoch) {
 if(state.value?.status==='PENDING'&&props.active!==false&&!busy.value)timer=setTimeout(()=>void load(current),2000);
}
async function load(current=epoch,afterPrepare=false) {
 if(current!==epoch||props.active===false||(busy.value&&!afterPrepare))return;
 cancelRead();
 const generation=readGeneration;
 const requestController=new AbortController();
 controller=requestController;
 try {
  const loaded=await classroomCheckApi.get(props.runId,props.unitId,requestController.signal);
  if(current!==epoch||generation!==readGeneration||requestController.signal.aborted)return;
  state.value=loaded;
  questionIndex.value=Math.min(questionIndex.value,Math.max(0,loaded.questions.length-1));
  for(const attempt of loaded.attempts.slice().reverse()){
   if(!redo.value.has(attempt.checkId)&&!selected.value[attempt.checkId])selected.value[attempt.checkId]=attempt.selectedOption;
  }
  error.value='';
  poll(current);
 } catch(cause){
  if(current===epoch&&generation===readGeneration&&!requestController.signal.aborted)error.value=cause instanceof Error?cause.message:'小测暂时无法读取';
 }
}
watch(()=>[props.runId,props.unitId] as const,()=>{epoch++;cancelRead();state.value=null;questionIndex.value=0;selected.value={};redo.value.clear();error.value='';busy.value='';keys.clear();void load();},{immediate:true});
watch(()=>props.active,active=>{if(active)void load();else if(active===false)cancelRead();});
async function submit(id:string) {
 const option=selected.value[id];if(!option||busy.value)return;const current=epoch;busy.value=id;error.value='';cancelRead();
 try {const attempt=await classroomCheckApi.attempt(props.runId,id,option,key(`attempt:${id}:${option}`));if(current!==epoch||!state.value)return;cancelRead();state.value.attempts.push(attempt);redo.value.delete(id);keys.delete(`attempt:${id}:${option}`);}
 catch(cause){if(current===epoch)error.value=cause instanceof Error?cause.message:'作答未保存，请重试';}
 finally{if(current===epoch){busy.value='';poll(current);}}
}
async function prepare() {
 if(busy.value)return;busy.value='prepare';const current=epoch;error.value='';cancelRead();
 try {await classroomCheckApi.prepare(props.runId,props.unitId,key('prepare'));if(current!==epoch)return;keys.delete('prepare');await load(current,true);}
 catch(cause){if(current===epoch)error.value=cause instanceof Error?cause.message:'暂时无法准备小测';}
 finally{if(current===epoch){busy.value='';poll(current);}}
}
function again(id:string){redo.value.add(id);delete selected.value[id];}
onBeforeUnmount(()=>{epoch++;cancelRead();});
</script>
<template>
 <section class="check-panel" aria-label="知识点小测"><header><h2>知识点小测</h2><span v-if="questionCount" class="check-count" aria-live="polite" aria-atomic="true">第 {{questionIndex+1}} / {{questionCount}} 题</span></header>
  <p v-if="!state&&!error" role="status">正在读取小测…</p>
  <p v-if="state?.status==='PENDING'" role="status">小测将在这个知识点的讲解准备完成后出现。</p>
  <p v-if="state?.status==='FAILED'" class="check-hint">当前小测尚未准备好，已就绪的讲解可以继续阅读。<button v-if="canPrepare" type="button" :disabled="!!busy" @click="prepare">{{busy==='prepare'?'正在提交…':'重新准备小测'}}</button></p>
  <p v-if="state?.status==='READY'&&!questionCount" role="status">暂无题目。</p>
  <form v-if="question" :key="question.id" @submit.prevent="submit(question.id)"><fieldset :disabled="!!busy||!!last(question.id)"><legend>{{questionIndex+1}}. {{question.prompt}}</legend><label v-for="option in question.options" :key="option.id"><input v-model="selected[question.id]" type="radio" :name="`check-${question.id}`" :value="option.id"><span>{{option.text}}</span></label></fieldset>
   <div v-if="last(question.id)" class="check-result" role="status"><strong>{{last(question.id)!.correct?'理解正确':'再想一想'}}</strong><p>{{last(question.id)!.analysis}}</p><p>参考选项：{{question.options.find(option=>option.id===last(question!.id)!.answerOption)?.text}}</p><button type="button" @click="again(question.id)">再做一次</button></div>
   <button v-else type="submit" :disabled="!!busy||!selected[question.id]">{{busy===question.id?'正在判分…':'提交答案'}}</button>
  </form>
  <nav v-if="questionCount" class="check-pagination" aria-label="小测题目切换"><button type="button" :disabled="questionIndex===0" @click="questionIndex--">上一题</button><button type="button" :disabled="questionIndex===questionCount-1" @click="questionIndex++">下一题</button></nav>
  <p v-if="error" role="alert">{{error}} <button type="button" @click="load()">重新读取</button></p>
 </section>
</template>
<style scoped>
.check-panel{box-sizing:border-box;width:min(100%,680px);margin:auto;padding:24px clamp(16px,4vw,32px);display:grid;gap:24px;color:var(--text);flex-shrink:0;overflow-wrap:anywhere}header{display:flex;align-items:center;justify-content:space-between;gap:16px}h2{font-size:23px;margin:8px 0;font-weight:550}.check-hint{font-size:12px;color:var(--text-muted);line-height:1.75;margin:0}.check-pagination{display:flex;align-items:center;justify-content:space-between;gap:12px}.check-count{font-size:12px;color:var(--text-muted);white-space:nowrap}form{display:grid;gap:14px;animation:check-enter 180ms ease-out}fieldset{border:0;padding:0;margin:0;display:grid;gap:10px;min-width:0}legend{font-size:16px;line-height:1.7;padding:0;margin-bottom:14px}label{display:flex;align-items:flex-start;gap:10px;padding:12px 14px;border:1px solid var(--line);border-radius:14px;font-size:14px;line-height:1.65;cursor:pointer}label:has(input:checked){border-color:var(--text);background:var(--surface)}input{margin-top:5px;accent-color:var(--text)}button{justify-self:start;font:inherit;font-size:12px;padding:10px 16px;border:1px solid var(--line);border-radius:99px;background:var(--surface);color:var(--text);cursor:pointer;transition:background 160ms ease,transform 160ms ease}button:hover:not(:disabled){background:var(--surface-alt,var(--surface))}button:active:not(:disabled){transform:translateY(1px)}button:disabled{opacity:.45;cursor:default}button:focus-visible,input:focus-visible{outline:2px solid var(--text);outline-offset:3px}.check-result{border-left:2px solid var(--text);padding-left:16px;font-size:14px;line-height:1.8}.check-result p{margin:8px 0}.check-hint button{margin-left:10px}
@keyframes check-enter{from{opacity:0;transform:translateY(3px)}to{opacity:1;transform:translateY(0)}}
@media(prefers-reduced-motion:reduce){form{animation:none}button{transition:none}}
</style>
