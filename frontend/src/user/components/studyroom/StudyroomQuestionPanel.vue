<script setup lang="ts">
import {ref,watch,nextTick} from 'vue';
import type {ClassroomQuestion} from '../../../shared/types/classroom-experience';
import type {StudyroomMessage} from '../../../shared/types/studyroom';
const props=defineProps<{open:boolean;draft:string;busy:boolean;source:string;question:ClassroomQuestion|null;messages:StudyroomMessage[];error?:string;connectionError?:string;canAsk:boolean}>();
const emit=defineEmits<{close:[];ask:[];cancel:[];resume:[];'update:draft':[value:string]}>();
const input=ref<HTMLTextAreaElement>();
const transcript=ref<HTMLElement>();const followLatest=ref(true);
function trackScroll(){const element=transcript.value;if(element)followLatest.value=element.scrollHeight-element.scrollTop-element.clientHeight<48;}
async function scrollLatest(){await nextTick();if(transcript.value)transcript.value.scrollTop=transcript.value.scrollHeight;}
watch(()=>props.open,async open=>{if(open){followLatest.value=true;await scrollLatest();input.value?.focus({preventScroll:true});}});
watch(()=>[props.messages.at(-1)?.id,props.question?.answer,props.question?.status],()=>{if(followLatest.value)void scrollLatest();});
</script>
<template>
 <section class="question-panel" @keydown.esc.stop="emit('close')">
  <header><div><span class="question-eyebrow">AI 导师</span><h2>围绕这里问一问</h2></div><button type="button" aria-label="收起课堂问答" @click="emit('close')">×</button></header>
  <p class="question-source"><span aria-hidden="true">↗</span> {{source||'选择一段已就绪的讲解后提问'}}</p>
  <div ref="transcript" class="question-transcript" tabindex="0" aria-label="问答记录" @scroll="trackScroll"><div class="question-history" aria-label="课堂对话"><article v-for="message in messages" :key="message.id" :class="message.role"><span>{{message.role==='assistant'?'AI 导师':'你'}}</span><p>{{message.content}}</p></article></div>
  <article v-if="question&&question.status!=='COMPLETED'" class="live-answer"><span>{{question.status==='QUEUED'?'问题已保存，等待当前任务完成':question.status==='RUNNING'?'AI 导师正在回答':question.status==='CANCELLED'?'已停止回答':'回答未完成'}}</span><p class="asked-question">{{question.question}}</p><p>{{question.answer||'正在结合当前材料准备回答…'}}</p><p v-if="question.error" role="alert">{{question.error.message}}</p></article>
  <p v-if="connectionError" class="question-hint" role="status">连接中断，正在恢复回答。</p><p v-if="error" role="alert" class="question-error">{{error}}</p><p v-if="!messages.length&&!question" class="question-empty">从当前材料出发，问一个你还没理解的问题。</p></div>
  <form @submit.prevent="emit('ask')"><label for="classroom-question">课程问题</label><textarea id="classroom-question" ref="input" :value="draft" maxlength="4000" rows="3" placeholder="这一处为什么这样做？" @input="emit('update:draft',($event.target as HTMLTextAreaElement).value)" @keydown.ctrl.enter.prevent="emit('ask')"/><div class="question-controls"><span>结合当前材料回答</span><button v-if="busy" type="button" @click="emit('cancel')">停止回答</button><button v-else type="submit" :disabled="!draft.trim()||!canAsk">发送追问 ↗</button></div></form>
  <button class="resume-learning" type="button" @click="emit('resume')">返回刚才的位置，继续学习 →</button>
 </section>
</template>
<style scoped>
.question-panel{border:1px solid var(--line);border-radius:22px;background:var(--surface);padding:22px;display:grid;gap:18px}.question-panel header{display:flex;align-items:center;justify-content:space-between;gap:12px}.question-eyebrow{font-size:11px;letter-spacing:.08em;color:var(--text-muted)}h2{font-size:21px;margin:5px 0 0;font-weight:550}.question-panel header button{border:0;background:transparent;font-size:26px;padding:4px 10px}.question-source{margin:0;border-bottom:1px solid var(--line);padding-bottom:16px;font-size:12px;line-height:1.65;color:var(--text-muted);overflow-wrap:anywhere}.question-history{display:grid;gap:20px;max-height:38dvh;overflow:auto;overscroll-behavior:contain}.question-history:empty{display:none}article>span{font-size:11px;color:var(--text-muted)}article p{white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px;line-height:1.85;margin:6px 0 0}.question-history .user{margin-left:18px;padding-left:12px;border-left:1px solid var(--line)}.asked-question{color:var(--text-muted)}form{display:grid;gap:9px}label{font-size:12px;color:var(--text-muted)}textarea{box-sizing:border-box;width:100%;resize:vertical;min-height:94px;max-height:220px;padding:13px 14px;border:1px solid var(--line);border-radius:16px;color:var(--text);background:var(--bg);font:inherit;font-size:14px;line-height:1.7}.question-controls{display:flex;justify-content:space-between;gap:8px;align-items:center}.question-controls span,.question-hint,.question-error{font-size:11px;color:var(--text-muted)}button{font:inherit;font-size:12px;padding:10px 15px;border:1px solid var(--line);border-radius:99px;background:var(--surface);color:var(--text);cursor:pointer}.question-controls button{background:var(--text);color:var(--surface);white-space:nowrap}.resume-learning{width:100%;padding:12px}button:disabled{opacity:.45;cursor:default}button:focus-visible,textarea:focus-visible{outline:2px solid var(--text);outline-offset:3px}@media(max-width:767px){.question-panel{border:0;padding:20px 8px 4px}.question-history{max-height:28dvh}}
</style>
<style scoped>
.question-panel{box-sizing:border-box;height:100%;min-height:0;display:flex;flex-direction:column;gap:12px;padding:18px;overflow:hidden}
.question-panel>header,.question-source,form,.resume-learning{flex-shrink:0}.question-source{padding-bottom:10px;max-height:60px;overflow:auto}.question-panel h2{font-size:18px}
.question-transcript{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;scrollbar-gutter:stable}.question-history{max-height:none;overflow:visible}.live-answer{margin-top:14px}.question-empty{font-size:13px;line-height:1.8;color:var(--text-muted);padding:16px 0}
textarea{resize:none;min-height:80px;max-height:110px}.resume-learning{padding:9px;font-size:11px}.question-transcript:focus-visible{outline:2px solid var(--text);outline-offset:-2px}
@media(max-width:767px){.question-panel{padding:18px 16px max(14px,env(safe-area-inset-bottom))}.question-history{max-height:none}}
@media(max-height:600px){.question-panel{gap:7px;padding:12px}.question-source{max-height:35px;padding-bottom:6px}.question-eyebrow,.resume-learning{display:none}textarea{min-height:60px;height:60px}}
</style>
