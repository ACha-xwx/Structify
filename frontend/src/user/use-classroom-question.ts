import {computed,onScopeDispose,ref,shallowRef} from 'vue';
import {classroomQuestionApi,type ClassroomQuestionRequest} from './classroom-question-runtime';
import {useClassroomEvents} from './use-classroom-events';
import type {ClassroomQuestion,QuestionStatus} from '../shared/types/classroom-experience';
const terminal=(status:QuestionStatus)=>['COMPLETED','FAILED','CANCELLED'].includes(status);
export function useClassroomQuestion(completed:()=>void) {
 const question=shallowRef<ClassroomQuestion|null>(null);const error=ref('');const submitting=ref(false);
 const subscription=useClassroomEvents();let epoch=0;let request:{payload:string;key:string}|undefined;
 const busy=computed(()=>submitting.value||!!question.value&&!terminal(question.value.status));
 function stop(){epoch++;subscription.stop();question.value=null;submitting.value=false;error.value='';request=undefined;}
 async function attach(room:string,id:string) {
  subscription.stop();const current=++epoch;
  try {
   const loaded=await classroomQuestionApi.get(room,id);if(current!==epoch)return;question.value=loaded;
   if(terminal(loaded.status)){completed();return;}
   async function refresh() {
    const latest=await classroomQuestionApi.get(room,id);if(current!==epoch)return;
    question.value=latest;if(terminal(latest.status))completed();
   }
   subscription.start({streamId:loaded.streamId,path:classroomQuestionApi.events(room,id),
    onSnapshot:snapshot=>{
     if(current!==epoch||!question.value)return;
     question.value={...question.value,answer:typeof snapshot.state.answer==='string'?snapshot.state.answer:question.value.answer,status:(snapshot.state.status??question.value.status) as QuestionStatus};
     if(snapshot.terminal)void refresh().catch(cause=>{error.value=String(cause);});
    },
    onEvent:event=>{
     if(current!==epoch||!question.value)return;
     if(event.kind==='answer_delta'&&typeof event.payload.delta==='string')question.value={...question.value,status:'RUNNING',answer:question.value.answer+event.payload.delta};
     if(['answer_completed','failed','cancelled'].includes(event.kind))void refresh().catch(cause=>{error.value=String(cause);});
    }});
  }catch(cause){if(current===epoch)error.value=cause instanceof Error?cause.message:'问题状态暂时无法读取';}
 }
 async function ask(room:string,body:ClassroomQuestionRequest) {
  if(busy.value)return false;const current=epoch;submitting.value=true;error.value='';
  const payload=JSON.stringify({room,...body});if(request?.payload!==payload)request={payload,key:crypto.randomUUID()};
  try {const receipt=await classroomQuestionApi.create(room,body,request.key);if(current!==epoch)return false;request=undefined;question.value={...receipt,question:body.question,answer:'',context:body.context?{...body.context}:{},runId:body.runId,fragmentId:body.fragmentId,contentRevision:body.contentRevision};await attach(room,receipt.questionId);return true;}
  catch(cause){if(current===epoch)error.value=cause instanceof Error?cause.message:'提问未提交，请重试';return false;}
  finally{if(current===epoch||question.value)submitting.value=false;}
 }
 async function cancel(room:string) {
  const id=question.value?.questionId;if(!id)return;
  try {await classroomQuestionApi.cancel(room,id);await attach(room,id);}
  catch(cause){error.value=cause instanceof Error?cause.message:'停止失败，请重试';}
 }
 onScopeDispose(stop);
 return {question,error,busy,ask,attach,cancel,stop,connectionError:subscription.error};
}
