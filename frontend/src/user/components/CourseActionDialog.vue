<script setup lang="ts">
import {nextTick,onMounted,onBeforeUnmount,ref} from 'vue';
import type {PersonalCourse} from '../course-library';
import {useLearningCenterCopy} from '../learning-center-copy';
const props=defineProps<{course:PersonalCourse;mode:'rename'|'delete';busy:boolean;error:string}>();
const emit=defineEmits<{close:[];confirm:[title:string]}>();
const {copy}=useLearningCenterCopy();const dialog=ref<HTMLDialogElement>();const input=ref<HTMLInputElement>();const title=ref(props.course.title);
let previous:HTMLElement|null=null;
onMounted(async()=>{const focused=document.activeElement as HTMLElement;previous=focused.closest('details')?.querySelector('summary')??focused;await nextTick();dialog.value?.showModal?.();if(props.mode==='rename')input.value?.focus();else dialog.value?.querySelector<HTMLButtonElement>('[data-action="cancel-course"]')?.focus();});
onBeforeUnmount(()=>{dialog.value?.close?.();if(previous?.isConnected)previous.focus();else document.querySelector<HTMLAnchorElement>('.center-header a')?.focus();});
function cancel(event:Event){event.preventDefault();if(!props.busy)emit('close');}
</script>
<template>
 <dialog ref="dialog" class="course-dialog" aria-labelledby="course-dialog-title" @cancel="cancel" @click="event=>{if(event.target===dialog&&!busy)$emit('close')}">
  <form @submit.prevent="emit('confirm',title.trim())">
   <p class="dialog-kicker">{{copy.courses}}</p><h2 id="course-dialog-title">{{mode==='rename'?copy.rename:copy.deleteTitle}}</h2>
   <template v-if="mode==='rename'"><label for="course-title">{{copy.name}}</label><input id="course-title" ref="input" v-model="title" name="course-title" maxlength="160" required :disabled="busy"/></template>
   <template v-else><p class="dialog-course">{{course.title}}</p><p class="dialog-hint">{{copy.deleteHint}}</p></template>
   <p v-if="error" class="dialog-error" role="alert">{{error}}</p>
   <footer><button type="button" data-action="cancel-course" :disabled="busy" @click="emit('close')">{{copy.cancel}}</button><button class="primary" type="submit" data-action="confirm-course" :disabled="busy||(mode==='rename'&&!title.trim())">{{busy?copy.working:mode==='rename'?copy.save:copy.remove}}</button></footer>
  </form>
 </dialog>
</template>
<style scoped>
.course-dialog{box-sizing:border-box;width:min(460px,calc(100% - 32px));border:1px solid var(--line);border-radius:16px;background:var(--surface);color:var(--text);padding:30px}.course-dialog::backdrop{background:rgb(15 18 24 / .42)}form{display:grid;gap:14px}.dialog-kicker{font-size:11px;color:var(--text-muted);margin:0}h2{font-size:22px;font-weight:600;margin:0 0 10px}label{font-size:13px}input{box-sizing:border-box;width:100%;padding:12px;font:inherit;border:1px solid var(--line);border-radius:7px;background:var(--bg);color:var(--text)}.dialog-course{margin:0;font-weight:600;overflow-wrap:anywhere}.dialog-hint,.dialog-error{margin:0;font-size:13px;line-height:1.8;color:var(--text-muted)}.dialog-error{color:var(--text)}footer{display:flex;justify-content:flex-end;gap:10px;margin-top:16px}button{border:1px solid var(--line);background:var(--surface);color:var(--text);font:inherit;font-size:13px;padding:11px 16px;border-radius:8px;cursor:pointer}.primary{background:var(--text);color:var(--surface)}button:disabled{opacity:.5;cursor:default}:focus-visible{outline:2px solid var(--text);outline-offset:3px}
</style>
<style scoped>
.course-dialog{width:min(520px,calc(100% - 32px));border:1px double color-mix(in srgb,var(--text) 16%,transparent);border-radius:26px;background:color-mix(in srgb,var(--surface) 82%,transparent);padding:32px;box-shadow:inset 0 1px 0 color-mix(in srgb,var(--surface) 92%,transparent),0 16px 40px color-mix(in srgb,var(--text) 14%,transparent);-webkit-backdrop-filter:blur(14px);backdrop-filter:blur(14px);font-family:var(--font-ui)}
h2{font-size:28px;font-weight:400}.dialog-kicker{font-size:16px}.dialog-course{font-size:21px;font-weight:500}.dialog-hint,.dialog-error,label{font-size:18px;line-height:1.65}input{border-radius:999px;padding:13px 18px;font-size:19px;background:color-mix(in srgb,var(--surface) 40%,transparent)}button{min-height:46px;border-radius:999px;font-size:18px;padding:9px 20px;background:color-mix(in srgb,var(--surface) 62%,transparent)}.primary{background:color-mix(in srgb,var(--surface) 30%,transparent);color:var(--text);box-shadow:inset 0 1px 0 color-mix(in srgb,var(--surface) 92%,transparent),inset 0 0 2px color-mix(in srgb,var(--text) 44%,transparent)}
@media(prefers-reduced-transparency:reduce){.course-dialog,input,button{background:var(--surface);-webkit-backdrop-filter:none;backdrop-filter:none}}
@media(max-width:520px){.course-dialog{padding:24px}footer{gap:8px}button{padding:9px 15px;font-size:17px}}
</style>
