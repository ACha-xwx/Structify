import { onScopeDispose, ref } from 'vue';

export interface PreparationIdentity { draftId:string; revision:number; runId:string; roomId:string }
interface PreparationRun {
  stateVersion:number; firstReadableFragmentId:string|null;
  knowledgePoints:Array<{fragments:Array<{status:string;error?:{message:string}|null}>}>;
}

export function useStudyroomPreparation(
  read:(run:string,signal:AbortSignal)=>Promise<PreparationRun>,
  enter:(identity:PreparationIdentity)=>void,
) {
  const waiting=ref(false);const error=ref('');
  let epoch=0;let timer:ReturnType<typeof setTimeout>|undefined;let controller:AbortController|undefined;
  let refresh:()=>void=()=>{};
  function stop(){epoch++;clearTimeout(timer);controller?.abort();waiting.value=false;refresh=()=>{};}
  function start(identity:PreparationIdentity){
    stop();const current=epoch;waiting.value=true;error.value='';let version=-1;
    controller=new AbortController();const signal=controller.signal;
    let reading=false;
    async function poll(){
      if(reading)return;reading=true;clearTimeout(timer);
      try{
        const result=await read(identity.runId,signal);
        if(current!==epoch||signal.aborted)return;
        if(result.stateVersion>=version){
          version=result.stateVersion;
          if(result.firstReadableFragmentId){stop();enter(identity);return;}
          const first=result.knowledgePoints[0]?.fragments[0];
          if(first?.status==='FAILED'){error.value=first.error?.message??'首段未准备完成，可以进入课堂重新准备。';stop();return;}
        }
        timer=setTimeout(()=>void poll(),document.visibilityState==='hidden'?10000:2000);
      }catch(cause){
        if(current!==epoch||signal.aborted)return;
        error.value=cause instanceof Error?cause.message:'准备状态暂时无法读取，请重试。';stop();
      }finally{reading=false;}
    }
    refresh=()=>{if(current===epoch&&!signal.aborted)void poll();};
    void poll();
  }
  onScopeDispose(stop);
  return {waiting,error,start,stop,refresh:()=>refresh()};
}
