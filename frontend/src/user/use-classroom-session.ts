import {ref,shallowRef} from 'vue';
import type {ClassroomMode,LearningSnapshot,MaterialIdentity} from '../shared/types/classroom-experience';
import type {ClassroomMaterialAdapter} from './classroom-materials';
import {sameMaterial} from './classroom-materials';
export function useClassroomSession(current:()=>MaterialIdentity|null,adapter:()=>ClassroomMaterialAdapter|undefined) {
 const mode=ref<ClassroomMode>('READING');const snapshot=shallowRef<LearningSnapshot|null>(null);
 let navigationVersion=0;
 let teachingIdentity:MaterialIdentity|null=null;
 function navigated(){navigationVersion++;}
 function ask() {
  if(mode.value!=='READING')return;
  const identity=current(),material=adapter();
  teachingIdentity=null;
  if(identity&&material){snapshot.value={identity:{...identity},navigationVersion,position:material.capture()};material.pause();}
  mode.value='ASKING';
 }
 function answering(){mode.value='ANSWERING';}
 function answered(){if(mode.value!=='READING')mode.value='AWAITING_RESUME';}
 async function resume() {
  const saved=snapshot.value,identity=current();mode.value='READING';snapshot.value=null;
  if(!saved||!identity||saved.navigationVersion!==navigationVersion||!sameMaterial(teachingIdentity??saved.identity,identity))return false;
  await adapter()?.restore(saved.position);return true;
 }
 function allowTeachingNavigation(){const identity=current();if(snapshot.value&&identity&&snapshot.value.navigationVersion===navigationVersion)teachingIdentity={...identity};}
 function reset(){navigationVersion++;mode.value='READING';snapshot.value=null;teachingIdentity=null;}
 return {mode,snapshot,ask,answering,answered,resume,navigated,reset,allowTeachingNavigation};
}
