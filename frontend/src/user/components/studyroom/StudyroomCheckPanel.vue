<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue';
import {classroomCheckApi} from '../../classroom-check-runtime';
import type {CheckState,CheckAttempt} from '../../../shared/types/classroom-experience';
const props=defineProps<{runId:string;unitId:string;canPrepare:boolean;active?:boolean}>();
const state=ref<CheckState|null>(null),error=ref(''),busy=ref('');
const selected=ref<Record<string,string>>({});const redo=ref(new Set<string>());
let epoch=0;let controller:AbortController|undefined;let timer:ReturnType<typeof setTimeout>|undefined;
const keys=new Map<string,string>();
function key(operation:string){if(!keys.has(operation))keys.set(operation,crypto.randomUUID());return keys.get(operation)!;}
function last(id:string):CheckAttempt|undefined {return redo.value.has(id)?undefined:state.value?.attempts.filter(attempt=>attempt.checkId===id).at(-1);}
async function load(current=epoch) {
 clearTimeout(timer);
 try {const loaded=await classroomCheckApi.get(props.runId,props.unitId,controller?.signal);if(current!==epoch)return;state.value=loaded;error.value='';if(loaded.status==='PENDING')timer=setTimeout(()=>void load(current),2000);}
 catch(cause){if(current===epoch&&!controller?.signal.aborted)error.value=cause instanceof Error?cause.message:'自检暂时无法读取';}
}
watch(()=>[props.runId,props.unitId] as const,()=>{epoch++;clearTimeout(timer);controller?.abort();controller=new AbortController();state.value=null;selected.value={};redo.value.clear();error.value='';busy.value='';keys.clear();void load();},{immediate:true});
watch(()=>props.active,active=>{if(active)void load();});
async function submit(id:string) {
 const option=selected.value[id];if(!option||busy.value)return;const current=epoch;busy.value=id;error.value='';
 try {const attempt=await classroomCheckApi.attempt(props.runId,id,option,key(`attempt:${id}:${option}`));if(current!==epoch||!state.value)return;state.value.attempts.push(attempt);redo.value.delete(id);keys.delete(`attempt:${id}:${option}`);}
 catch(cause){if(current===epoch)error.value=cause instanceof Error?cause.message:'作答未保存，请重试';}
 finally{if(current===epoch)busy.value='';}
}
async function prepare() {
 if(busy.value)return;busy.value='prepare';const current=epoch;error.value='';
 try {await classroomCheckApi.prepare(props.runId,props.unitId,key('prepare'));if(current!==epoch)return;keys.delete('prepare');clearTimeout(timer);await load(current);}
 catch(cause){if(current===epoch)error.value=cause instanceof Error?cause.message:'暂时无法准备自检';}
 finally{if(current===epoch)busy.value='';}
}
function again(id:string){redo.value.add(id);delete selected.value[id];}
onBeforeUnmount(()=>{epoch++;clearTimeout(timer);controller?.abort();});
</script>
<template>
 <section class="check-panel" aria-label="知识点自检"><header><div><span class="check-kicker">CHECK YOUR UNDERSTANDING</span><h2>停一下，检验理解</h2><p>自检只记录本次作答，不自动改变学习完成状态。</p></div></header>
  <p v-if="state?.status==='PENDING'" role="status">自检将在这个知识点的讲解准备完成后出现。</p>
  <p v-if="state?.status==='FAILED'" class="check-hint">当前自检尚未准备好，已就绪的讲解可以继续阅读。<button v-if="canPrepare" type="button" :disabled="!!busy" @click="prepare">{{busy==='prepare'?'正在提交…':'重新准备自检'}}</button></p>
  <form v-for="(question,index) in state?.questions??[]" :key="question.id" @submit.prevent="submit(question.id)"><fieldset :disabled="!!busy||!!last(question.id)"><legend>{{index+1}}. {{question.prompt}}</legend><label v-for="option in question.options" :key="option.id"><input v-model="selected[question.id]" type="radio" :name="`check-${question.id}`" :value="option.id"><span>{{option.text}}</span></label></fieldset>
   <div v-if="last(question.id)" class="check-result" role="status"><strong>{{last(question.id)!.correct?'理解正确':'再想一想'}}</strong><p>{{last(question.id)!.analysis}}</p><p>参考选项：{{question.options.find(option=>option.id===last(question.id)!.answerOption)?.text}}</p><button type="button" @click="again(question.id)">再做一次</button></div>
   <button v-else type="submit" :disabled="!!busy||!selected[question.id]">{{busy===question.id?'正在判分…':'提交答案'}}</button>
  </form><p v-if="error" role="alert">{{error}} <button type="button" @click="load()">重新读取</button></p>
 </section>
</template>
<style scoped>
.check-panel{padding:26px 0;border-top:1px solid var(--line);display:grid;gap:24px}.check-kicker{font:10px var(--font-mono);letter-spacing:.12em;color:var(--text-muted)}h2{font-size:23px;margin:8px 0;font-weight:550}header p,.check-hint{font-size:12px;color:var(--text-muted);line-height:1.75;margin:0}form{display:grid;gap:14px}fieldset{border:0;padding:0;margin:0;display:grid;gap:10px;min-width:0}legend{font-size:16px;line-height:1.7;padding:0;margin-bottom:14px}label{display:flex;align-items:flex-start;gap:10px;padding:12px 14px;border:1px solid var(--line);border-radius:14px;font-size:14px;line-height:1.65;cursor:pointer}label:has(input:checked){border-color:var(--text);background:var(--surface)}input{margin-top:5px;accent-color:var(--text)}button{justify-self:start;font:inherit;font-size:12px;padding:10px 16px;border:1px solid var(--line);border-radius:99px;background:var(--surface);color:var(--text);cursor:pointer}button:disabled{opacity:.45;cursor:default}button:focus-visible,input:focus-visible{outline:2px solid var(--text);outline-offset:3px}.check-result{border-left:2px solid var(--text);padding-left:16px;font-size:14px;line-height:1.8}.check-result p{margin:8px 0}.check-hint button{margin-left:10px}
</style>
