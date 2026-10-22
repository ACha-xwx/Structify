import {onScopeDispose,ref,shallowRef} from 'vue';
import {ClassroomEventCursor,classroomEvents} from './classroom-events';
import type {ClassroomEvent,ClassroomStreamSnapshot} from '../shared/types/classroom-experience';

type Subscription={streamId:string;path:string;onSnapshot:(snapshot:ClassroomStreamSnapshot)=>void;onEvent:(event:ClassroomEvent)=>void};
export function useClassroomEvents() {
 const connected=ref(false);const error=ref('');const snapshot=shallowRef<ClassroomStreamSnapshot|null>(null);
 let controller:AbortController|undefined;let epoch=0;
 function stop(){epoch++;controller?.abort();controller=undefined;connected.value=false;}
 function start(options:Subscription) {
  stop();const current=epoch;controller=new AbortController();const signal=controller.signal;
  const cursor=new ClassroomEventCursor(options.streamId);snapshot.value=null;error.value='';
  void (async()=>{
   let retry=0;
   while(!signal.aborted&&current===epoch) {
    try {
     // Initial/reset requests obtain the authoritative snapshot through the SSE reset contract.
     const since=snapshot.value&&!cursor.needsReset?cursor.sequence:Number.MAX_SAFE_INTEGER;
     for await(const incoming of classroomEvents(options.path,since,signal)) {
      if(current!==epoch||signal.aborted)return;
      connected.value=true;error.value='';retry=0;
      if(incoming.kind==='reset') {
       const next=incoming.data as ClassroomStreamSnapshot;
       if(cursor.reset(next)){snapshot.value=next;options.onSnapshot(next);if(next.terminal)return;}
      } else {
       const event=incoming.data as ClassroomEvent;
       if(cursor.accept(event)){options.onEvent(event);if(['failed','cancelled','outline_validated','answer_completed'].includes(event.kind))return;}
       else if(cursor.needsReset)break;
      }
     }
    } catch(cause) {
     if(signal.aborted)return;
     error.value=cause instanceof Error?cause.message:'连接中断，正在恢复进度';
     if(cause&&typeof cause==='object'&&'status' in cause&&[401,403,404].includes(Number(cause.status)))return;
    }
    connected.value=false;
    await new Promise<void>(resolve=>{
     const finish=()=>{clearTimeout(timer);signal.removeEventListener('abort',finish);resolve();};
     const timer=setTimeout(finish,[1000,2000,5000,10000][Math.min(retry++,3)]);
     signal.addEventListener('abort',finish,{once:true});
    });
   }
  })();
 }
 onScopeDispose(stop);
 return {start,stop,connected,error,snapshot};
}
