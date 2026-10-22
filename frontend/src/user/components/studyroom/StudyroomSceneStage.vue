<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch, shallowReactive } from 'vue';
import { scenesFor } from '../../studyroom-scenes';
import type { StudyroomFollowUpContext, StudyroomStep } from '../../../shared/types/studyroom';
import type { DsvpSimulationResponse } from '../../../shared/types/animation';
import { userApi } from '../../runtime';
import AnimationPlayer from '../../../animation/AnimationPlayer.vue';
import StudyroomSlideScene from './StudyroomSlideScene.vue';
import StudyroomCodeScene from './StudyroomCodeScene.vue';
import type {ClassroomMaterialAdapter} from '../../classroom-materials';
import type {MaterialPosition,TeachingAction} from '../../../shared/types/classroom-experience';
import type {LectureCue,LectureRegion} from '../../lecture-types';
const props = withDefaults(defineProps<{ step: StudyroomStep; chapterId?: string; active?: boolean; preferMaterials?:boolean;explanationOpen?:boolean;hasChecks?:boolean;regions?:LectureRegion[] }>(), { active: true });
const emit = defineEmits<{ context: [value: StudyroomFollowUpContext];explanation:[];material:[];navigate:[];checkActive:[active:boolean];saveRegion:[region:LectureRegion];removeRegion:[id:string] }>();
const checkSelected=ref(false);
watch(checkSelected,active=>emit('checkActive',active),{immediate:true});
function select(sceneId:string){emit('navigate');checkSelected.value=false;selected.value=sceneId;emit('material');}
function explain(){emit('navigate');checkSelected.value=false;emit('explanation');}
function checks(){emit('navigate');for(const adapter of adapters.values())adapter.pause();checkSelected.value=true;emit('material');}
const orchestration = computed(() => scenesFor(props.step));
const scenes = computed(() => [orchestration.value.primary, ...orchestration.value.supplementary]);
const selected = ref('');
const activeScene = computed(() => scenes.value.find(s => s.id === selected.value) ?? (props.preferMaterials?scenes.value.find(s=>s.kind!=='TEXT'):undefined) ?? orchestration.value.primary);
const visited = ref(new Set<string>());
const animation = ref<DsvpSimulationResponse | null>(null);
const animationCache=shallowReactive(new Map<string,DsvpSimulationResponse>());
const adapters=new Map<string,ClassroomMaterialAdapter>();
function adapter(id:string,value:unknown){if(value)adapters.set(id,value as ClassroomMaterialAdapter);else adapters.delete(id);}
const error = ref(''); let version = 0;
const contextByScene = new Map<string, StudyroomFollowUpContext>();
function context(sceneId: string, value: Partial<StudyroomFollowUpContext> = {}) {
  const next = { stepId: props.step.id, sceneId, ...value }; contextByScene.set(sceneId, next);
  if (props.active && activeScene.value.id === sceneId) emit('context', next);
}
watch(() => [activeScene.value.id, props.active,checkSelected.value,props.explanationOpen] as const, () => {
  for(const [id,material] of adapters)if(!props.active||checkSelected.value||props.explanationOpen||id!==activeScene.value.id)material.pause();
  visited.value.add(activeScene.value.id);
  context(activeScene.value.id, contextByScene.get(activeScene.value.id) ?? { resourceId: activeScene.value.items[0]?.resourceId, ...(activeScene.value.kind === 'C_CODE' ? { codeMode: activeScene.value.codeMode ?? 'READ' } : {}) });
  void loadAnimation();
}, { immediate: true });
async function loadAnimation() {
  const current = ++version; error.value = '';
  if (!props.active || checkSelected.value||props.explanationOpen||activeScene.value.kind !== 'ANIMATION') return;
  const scene = activeScene.value;
  const cached=animationCache.get(scene.id);if(cached){animation.value=cached;return;}
  animation.value=null;
  const resource = props.step.resources.find(r => r.type.toUpperCase() === 'ANIMATION' && r.id === scene.items[0]?.resourceId);
  try {
    if (!resource?.available) throw new Error('本步动画暂不可用。');
    const args = resource.parameters && typeof resource.parameters === 'object' && !Array.isArray(resource.parameters) ? resource.parameters as Record<string, unknown> : undefined;
    const plan = await userApi.planAnimation({ capability: resource.id, arguments: args, sourceRef: props.step.id });
    if (current !== version) return;
    if (!plan.toolRequest) throw new Error(plan.error || '动画暂不可用。');
    const result = await userApi.simulateAnimation(plan.toolRequest.request);
    animationCache.set(scene.id,result);
    if (current === version) animation.value = result;
  } catch (cause) { if (current === version) error.value = cause instanceof Error ? cause.message : '动画加载失败。'; }
}
onBeforeUnmount(() => { version++; });
defineExpose({
 lecture:async(cue:LectureCue,signal:AbortSignal)=>{
  if(signal.aborted)return false;
  if(cue.kind==='CHECK'){if(!props.hasChecks)return false;checkSelected.value=true;for(const material of adapters.values())material.pause();emit('material');return true;}
  checkSelected.value=false;
  if(cue.kind==='TEXT'){emit('explanation');return true;}
  const scene=scenes.value.find(scene=>scene.id===cue.sceneId&&scene.kind===cue.kind&&scene.items.some(item=>item.resourceId===cue.resourceId));if(!scene)return false;
  selected.value=scene.id;emit('material');await nextTick();if(signal.aborted)return false;
  if(!cue.action)return true;
  const action:TeachingAction={id:cue.id,sequence:0,runId:'',fragmentId:'',contentRevision:0,sceneId:scene.id,resourceId:cue.resourceId!,kind:cue.action,regionId:cue.regionId,startLine:cue.startLine,endLine:cue.endLine,stepIndex:cue.stepIndex};
  for(let attempts=0;attempts<125&&!signal.aborted;attempts++){
   const material=adapters.get(scene.id);if(material?.supports(action)){if(signal.aborted)return false;return await material.apply(action);}
   await new Promise(resolve=>setTimeout(resolve,40));
  }return false;
 },
 capture:():MaterialPosition=>adapters.get(activeScene.value.id)?.capture()??{kind:'TEXT',scrollTop:window.scrollY},
 pause:()=>adapters.get(activeScene.value.id)?.pause(),
 restore:(position:MaterialPosition)=>adapters.get(activeScene.value.id)?.restore(position),
 supports:(action:TeachingAction)=>action.sceneId===activeScene.value.id&&(adapters.get(activeScene.value.id)?.supports(action)??false),
 apply:(action:TeachingAction)=>action.sceneId===activeScene.value.id?(adapters.get(activeScene.value.id)?.apply(action)??false):false,
});
</script>
<template>
  <div class="scene-stage" :class="{'material-workspace':preferMaterials}">
    <nav v-if="preferMaterials||scenes.length>1" class="scene-choices" aria-label="本步教学材料"><button v-for="(scene, index) in scenes.filter(scene=>!preferMaterials||scene.kind!=='TEXT')" :key="scene.id" type="button" :data-action="index===0?'view-material':undefined" :aria-pressed="scene.id === activeScene.id&&!explanationOpen&&!checkSelected" @click="select(scene.id)">{{ index === 0 ? '本步重点 · ' : '' }}{{ scene.purpose }}</button><button v-if="preferMaterials" type="button" data-action="read-explanation" :aria-pressed="!!explanationOpen&&!checkSelected" @click="explain">完整讲解</button><button v-if="hasChecks" type="button" data-action="show-check" :aria-pressed="checkSelected" @click="checks">自检</button></nav>
    <div v-show="explanationOpen&&!checkSelected" class="scene-content explanation-content"><slot name="explanation"/></div>
    <div v-show="checkSelected" class="scene-content check-content"><slot name="checks"/></div>
    <template v-for="scene in scenes" :key="scene.id">
      <div v-if="visited.has(scene.id) && scene.kind !== 'TEXT'" v-show="scene.id === activeScene.id&&!explanationOpen&&!checkSelected" class="scene-content" @pointerdown="emit('navigate')" @input="emit('navigate')">
        <StudyroomSlideScene v-if="scene.kind === 'PPT'" :ref="value=>adapter(scene.id,value)" :scene="scene" :resources="step.resources" :bounded="preferMaterials" :regions="regions" @save-region="emit('saveRegion',$event)" @remove-region="emit('removeRegion',$event)" @interact="emit('navigate')" @context="context(scene.id, { resourceId: $event })" />
        <StudyroomCodeScene v-else-if="scene.kind === 'C_CODE'" :ref="value=>adapter(scene.id,value)" :scene="scene" :resources="step.resources" :chapter-id="chapterId" @context="context(scene.id, $event)" />
        <template v-else-if="scene.kind === 'ANIMATION'"><AnimationPlayer v-if="animationCache.get(scene.id)" :ref="value=>adapter(scene.id,value)" :definition="animationCache.get(scene.id)!.animationData" :trace="animationCache.get(scene.id)!.trace" compact /><p v-else role="status">{{ error || '正在准备动画…' }} <button v-if="error" type="button" @click="loadAnimation">重试动画</button></p><p v-if="scene.items[0]?.explanation">{{ scene.items[0].explanation }}</p></template>
      </div>
    </template>
  </div>
</template>
<style scoped>
.scene-stage { min-width:0; }.scene-choices { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; }.scene-choices button, .scene-content button { min-height:36px; padding:7px 12px; border:1px solid var(--line); border-radius:6px; color:var(--text-muted); background:var(--bg); cursor:pointer; }.scene-choices button[aria-pressed=true] { color:var(--text); border-color:var(--text); }.scene-choices button:focus-visible { outline:2px solid var(--text); outline-offset:2px; }.scene-content { min-width:0; }
</style>
<style scoped>
.material-workspace{display:flex;flex-direction:column;min-height:0;overflow:hidden}
.explanation-content{display:flex;flex-direction:column}.check-content{padding:0 8px}.check-content :deep(.check-panel){border:0;padding-top:12px}
.material-workspace .scene-choices{flex-shrink:0;flex-wrap:nowrap;overflow:auto;margin-bottom:10px;padding-bottom:3px}.material-workspace .scene-choices button{white-space:nowrap;font-size:12px}
.material-workspace .scene-content{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain}
.material-workspace :deep(.slide-scene){display:flex;flex-direction:column;height:100%;min-height:0}
.material-workspace :deep(.slide-toolbar){flex-shrink:0;padding-bottom:8px}
.material-workspace :deep(.studyroom-slide-viewport){flex:1;min-height:100px;height:auto}
.material-workspace :deep(.slide-caption){flex-shrink:0;margin-top:8px}
.material-workspace :deep(.slide-narration){max-height:64px;overflow:auto;flex-shrink:0;font-size:13px;line-height:1.65;margin:8px 0}
.material-workspace :deep(.slide-directory){flex-shrink:0;max-height:100px;overflow:auto;margin-top:8px;padding-top:8px}
</style>
