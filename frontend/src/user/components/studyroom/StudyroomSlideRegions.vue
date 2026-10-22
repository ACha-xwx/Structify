<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue';
import {containedImageBox,regionFromDrag} from '../../slide-regions';
import type {LectureRegion} from '../../lecture-types';
const props=defineProps<{sceneId:string;resourceId:string;naturalWidth:number;naturalHeight:number;regions:LectureRegion[];focusId?:string}>();
const emit=defineEmits<{save:[value:LectureRegion];remove:[id:string];interact:[]}>();
const root=ref<HTMLElement>();const width=ref(0),height=ref(0);const editing=ref(false);const narration=ref('');const draft=ref<ReturnType<typeof regionFromDrag>>(null);const start=ref<{x:number;y:number}>();const id=ref('');const localFocus=ref('');
const box=computed(()=>containedImageBox(width.value,height.value,props.naturalWidth,props.naturalHeight));
const focused=computed(()=>draft.value??props.regions.find(region=>region.id===(localFocus.value||props.focusId)));
const style=computed(()=>{const area=focused.value,b=box.value;return area&&b?{left:`${b.left+area.x*b.width}px`,top:`${b.top+area.y*b.height}px`,width:`${area.width*b.width}px`,height:`${area.height*b.height}px`}:{};});
let observer:ResizeObserver|undefined;
function measure(){width.value=root.value?.clientWidth??0;height.value=root.value?.clientHeight??0;}
onMounted(()=>{measure();if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(measure);if(root.value)observer.observe(root.value);}});onBeforeUnmount(()=>observer?.disconnect());
watch(()=>props.focusId,()=>{localFocus.value='';});
watch(()=>props.regions,regions=>{if(regions.some(region=>region.id===id.value)){editing.value=false;draft.value=null;narration.value='';}});
watch(()=>props.resourceId,()=>{editing.value=false;draft.value=null;localFocus.value='';});
function point(event:PointerEvent){const rect=root.value!.getBoundingClientRect();return {x:event.clientX-rect.left,y:event.clientY-rect.top};}
function down(event:PointerEvent){if(!editing.value||!box.value||event.button!==0)return;const p=point(event),b=box.value;if(p.x<b.left||p.y<b.top||p.x>b.left+b.width||p.y>b.top+b.height)return;start.value=p;root.value?.setPointerCapture(event.pointerId);event.preventDefault();}
function move(event:PointerEvent){if(start.value&&box.value)draft.value=regionFromDrag(box.value,start.value,point(event));}
function up(event:PointerEvent){move(event);start.value=undefined;root.value?.releasePointerCapture?.(event.pointerId);}
function begin(){emit('interact');editing.value=true;draft.value=null;id.value=crypto.randomUUID();narration.value='';}
function save(){if(draft.value&&narration.value.trim())emit('save',{id:id.value,sceneId:props.sceneId,resourceId:props.resourceId,...draft.value,narration:narration.value.trim()});}
</script>
<template>
 <div ref="root" class="slide-regions" :class="{'is-marking':editing}" @pointerdown.stop="down" @pointermove.stop="move" @pointerup.stop="up" @pointercancel="start=undefined">
  <div v-if="focused&&box" class="lecture-spotlight" :style="style" aria-label="当前讲述区域"/>
  <div class="region-tools" @pointerdown.stop><button v-if="!editing" type="button" :disabled="!box" @click="begin">标注讲述区域</button><details v-if="regions.length&&!editing"><summary>区域 {{regions.length}}</summary><div v-for="region in regions" :key="region.id"><button type="button" @click="emit('interact');localFocus=region.id">{{region.narration.slice(0,20)}}</button><button type="button" aria-label="移除讲述区域" @click="emit('interact');emit('remove',region.id)">移除</button></div></details></div>
  <p v-if="editing&&!draft" class="region-instruction">在课件图片上拖出一个讲述区域</p>
  <form v-if="editing&&draft" class="region-form" @pointerdown.stop @submit.prevent="save"><label :for="`region-${id}`">这一区域讲什么？</label><textarea :id="`region-${id}`" v-model="narration" maxlength="800" rows="2" placeholder="输入讲述内容，顺序讲授会在这里聚焦并朗读。"/><div><button type="submit" :disabled="!narration.trim()">保存讲述区域</button><button type="button" @click="editing=false;draft=null">取消</button></div></form>
  <button v-else-if="editing" class="region-cancel" type="button" @pointerdown.stop @click="editing=false;draft=null">取消标注</button>
 </div>
</template>
<style scoped>
.slide-regions{position:absolute;inset:0;overflow:hidden;pointer-events:none}.slide-regions.is-marking{pointer-events:auto;cursor:crosshair;touch-action:none}.lecture-spotlight{position:absolute;border:2px solid var(--text);box-shadow:0 0 0 9999px color-mix(in srgb,var(--text) 30%,transparent);border-radius:4px;pointer-events:none;z-index:1}.region-tools{position:absolute;top:6px;left:6px;display:flex;align-items:flex-start;gap:6px;pointer-events:auto;z-index:2}.region-tools>button,.region-tools details{opacity:.8}.region-tools:hover>button,.region-tools:hover details{opacity:1}.region-tools details{background:var(--surface);border:1px solid var(--line);padding:6px;border-radius:8px;max-width:240px;max-height:160px;overflow:auto;font-size:11px}.region-tools details>div{display:flex;gap:4px;margin-top:6px}.region-instruction,.region-cancel{position:absolute;bottom:8px;left:8px;z-index:2;background:var(--surface);padding:8px;border-radius:8px;font-size:12px}.region-instruction{bottom:38px}.region-cancel{pointer-events:auto}.region-form{position:absolute;z-index:3;bottom:8px;left:8px;right:8px;display:grid;gap:6px;background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:10px;cursor:default;pointer-events:auto;font-size:12px}.region-form textarea{box-sizing:border-box;width:100%;font:inherit;resize:none;color:var(--text);background:var(--bg);border:1px solid var(--line);border-radius:6px;padding:6px}.region-form>div{display:flex;gap:6px}button{font:inherit;font-size:11px;padding:6px 8px;border:1px solid var(--line);border-radius:7px;background:var(--surface);color:var(--text);cursor:pointer}button:disabled{opacity:.4}button:focus-visible,textarea:focus-visible{outline:2px solid var(--text);outline-offset:2px}
</style>
