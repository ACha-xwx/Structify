import {ref,watch,type Ref} from 'vue';
import {useClassroomEvents} from './use-classroom-events';
import type {OutlineDraft} from '../shared/types/outline';

export function useOutlineGeneration(draft:Ref<OutlineDraft|null>,refresh:()=>void) {
 const phase=ref('QUEUED');const units=ref<Array<{title:string;objective:string}>>([]);
 const subscription=useClassroomEvents();
 function state(value:Record<string,unknown>) {
  phase.value=typeof value.phase==='string'?value.phase:'QUEUED';
  units.value=Array.isArray(value.units)?value.units.filter((unit):unit is {title:string;objective:string}=>!!unit&&typeof unit==='object'&&typeof unit.title==='string'&&typeof unit.objective==='string'):[];
 }
 watch(()=>[draft.value?.id,draft.value?.pendingJobId] as const,([id,job])=>{
  subscription.stop();phase.value='QUEUED';units.value=[];
  if(!id||!job)return;
  subscription.start({streamId:`outline:${id}`,path:`/study-outlines/${encodeURIComponent(id)}/events`,
   onSnapshot:snapshot=>{state(snapshot.state);if(snapshot.terminal)refresh();},
   onEvent:event=>{
    if(event.kind==='phase'&&typeof event.payload.phase==='string')phase.value=event.payload.phase;
    if(event.kind==='outline_item'&&typeof event.payload.title==='string'&&typeof event.payload.objective==='string')units.value.push({title:event.payload.title,objective:event.payload.objective});
    if(['failed','cancelled','outline_validated'].includes(event.kind))refresh();
   }});
 });
 return {phase,units,connectionError:subscription.error};
}
