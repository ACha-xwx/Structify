<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { scenesFor } from '../../studyroom-scenes';
import type { StudyroomFollowUpContext, StudyroomStep } from '../../../shared/types/studyroom';
import type { DsvpSimulationResponse } from '../../../shared/types/animation';
import { userApi } from '../../runtime';
import AnimationPlayer from '../../../animation/AnimationPlayer.vue';
import StudyroomSlideScene from './StudyroomSlideScene.vue';
import StudyroomCodeScene from './StudyroomCodeScene.vue';
const props = withDefaults(defineProps<{ step: StudyroomStep; chapterId?: string; active?: boolean }>(), { active: true });
const emit = defineEmits<{ context: [value: StudyroomFollowUpContext] }>();
const orchestration = computed(() => scenesFor(props.step));
const scenes = computed(() => [orchestration.value.primary, ...orchestration.value.supplementary]);
const selected = ref('');
const activeScene = computed(() => scenes.value.find(s => s.id === selected.value) ?? orchestration.value.primary);
const visited = ref(new Set<string>());
const animation = ref<DsvpSimulationResponse | null>(null);
const error = ref(''); let version = 0;
const contextByScene = new Map<string, StudyroomFollowUpContext>();
function context(sceneId: string, value: Partial<StudyroomFollowUpContext> = {}) {
  const next = { stepId: props.step.id, sceneId, ...value }; contextByScene.set(sceneId, next);
  if (props.active && activeScene.value.id === sceneId) emit('context', next);
}
watch(() => [activeScene.value.id, props.active] as const, () => {
  visited.value.add(activeScene.value.id);
  context(activeScene.value.id, contextByScene.get(activeScene.value.id) ?? { resourceId: activeScene.value.items[0]?.resourceId, ...(activeScene.value.kind === 'C_CODE' ? { codeMode: activeScene.value.codeMode ?? 'READ' } : {}) });
  void loadAnimation();
}, { immediate: true });
async function loadAnimation() {
  const current = ++version; animation.value = null; error.value = '';
  if (!props.active || activeScene.value.kind !== 'ANIMATION') return;
  const scene = activeScene.value;
  const resource = props.step.resources.find(r => r.type.toUpperCase() === 'ANIMATION' && r.id === scene.items[0]?.resourceId);
  try {
    if (!resource?.available) throw new Error('本步动画暂不可用。');
    const args = resource.parameters && typeof resource.parameters === 'object' && !Array.isArray(resource.parameters) ? resource.parameters as Record<string, unknown> : undefined;
    const plan = await userApi.planAnimation({ capability: resource.id, arguments: args, sourceRef: props.step.id });
    if (current !== version) return;
    if (!plan.toolRequest) throw new Error(plan.error || '动画暂不可用。');
    const result = await userApi.simulateAnimation(plan.toolRequest.request);
    if (current === version) animation.value = result;
  } catch (cause) { if (current === version) error.value = cause instanceof Error ? cause.message : '动画加载失败。'; }
}
onBeforeUnmount(() => { version++; });
</script>
<template>
  <div class="scene-stage">
    <nav v-if="scenes.length > 1" class="scene-choices" aria-label="本步教学材料"><button v-for="(scene, index) in scenes" :key="scene.id" type="button" :aria-pressed="scene.id === activeScene.id" @click="selected = scene.id">{{ index === 0 ? '本步重点 · ' : '' }}{{ scene.purpose }}</button></nav>
    <template v-for="scene in scenes" :key="scene.id">
      <div v-if="visited.has(scene.id) && scene.kind !== 'TEXT'" v-show="scene.id === activeScene.id" class="scene-content">
        <StudyroomSlideScene v-if="scene.kind === 'PPT'" :scene="scene" :resources="step.resources" @context="context(scene.id, { resourceId: $event })" />
        <StudyroomCodeScene v-else-if="scene.kind === 'C_CODE'" :scene="scene" :resources="step.resources" :chapter-id="chapterId" @context="context(scene.id, $event)" />
        <template v-else-if="scene.kind === 'ANIMATION' && active && scene.id === activeScene.id"><AnimationPlayer v-if="animation" :definition="animation.animationData" :trace="animation.trace" compact /><p v-else role="status">{{ error || '正在准备动画…' }} <button v-if="error" type="button" @click="loadAnimation">重试动画</button></p><p v-if="scene.items[0]?.explanation">{{ scene.items[0].explanation }}</p></template>
      </div>
    </template>
  </div>
</template>
<style scoped>
.scene-stage { min-width:0; }.scene-choices { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; }.scene-choices button, .scene-content button { min-height:36px; padding:7px 12px; border:1px solid var(--line); border-radius:6px; color:var(--text-muted); background:var(--bg); cursor:pointer; }.scene-choices button[aria-pressed=true] { color:var(--text); border-color:var(--text); }.scene-choices button:focus-visible { outline:2px solid var(--text); outline-offset:2px; }.scene-content { min-width:0; }
</style>
