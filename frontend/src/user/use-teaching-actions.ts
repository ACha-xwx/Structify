import {ref} from 'vue';
import type {MaterialIdentity,TeachingAction} from '../shared/types/classroom-experience';
import type {ClassroomMaterialAdapter} from './classroom-materials';
export function useTeachingActions(current:()=>MaterialIdentity|null,adapter:()=>ClassroomMaterialAdapter|undefined,appliedAction:()=>void=()=>{}) {
 const notice=ref('');const applying=ref(false);const applied=new Set<string>();
 async function apply(actions:TeachingAction[]) {
  notice.value='';
  for(const action of [...actions].sort((a,b)=>a.sequence-b.sequence)) {
   if(applied.has(action.id))continue;
   const identity=current(),material=adapter();
   if(!identity||action.runId!==identity.runId||action.fragmentId!==identity.fragmentId||action.contentRevision!==identity.contentRevision||action.sceneId!==identity.sceneId||!material?.supports(action)){notice.value='部分材料定位暂不可用，可继续阅读文字回答。';continue;}
   applied.add(action.id);
   applying.value=true;
   try {if(await material.apply(action))appliedAction();else notice.value='材料定位暂不可用，可继续阅读文字回答。';}
   catch{notice.value='材料定位暂不可用，可继续阅读文字回答。';}
   finally{applying.value=false;}
  }
 }
 return {apply,notice,applying};
}
