<script setup lang="ts">
import {computed} from "vue";
import {useRoute,useRouter} from "vue-router";
import StudyroomOutlineFlow from "../components/studyroom/StudyroomOutlineFlow.vue";
import BrandStage from '../../shared/components/BrandStage.vue';
import AiTitle from '../../shared/components/AiTitle.vue';
const route=useRoute();const router=useRouter();
const draftId=computed(()=>typeof route.params.draftId==="string"?route.params.draftId:undefined);
const initialQuestion=computed(()=>typeof route.query.question==="string"?route.query.question:undefined);
const sourceRoomId=computed(()=>typeof route.query.sourceRoomId==="string"?route.query.sourceRoomId:undefined);
</script>
<template><BrandStage :wide="!!draftId" :fixed="!!draftId"><main class="outline-page" :class="{'outline-page--entry':!draftId}"><header><div><p>STRUCTIFY · STUDYROOM</p><AiTitle v-if="!draftId"><h1>课程问答</h1></AiTitle><h1 v-else>课程问答</h1></div><RouterLink to="/begin">返回学习入口</RouterLink></header><StudyroomOutlineFlow :draft-id="draftId" :initial-question="initialQuestion" :source-room-id="sourceRoomId" @draft="id=>router.replace(`/studyroom/outlines/${encodeURIComponent(id)}`)" @confirmed="id=>router.replace(`/studyroom/${encodeURIComponent(id)}`)"/></main></BrandStage></template>
<style scoped>.outline-page{max-width:1040px;width:100%;min-width:0;min-height:0;margin:0 auto;display:grid;grid-template-rows:auto minmax(0,1fr);gap:14px;color:var(--text)}header{display:flex;justify-content:space-between;align-items:center;gap:12px}header p,header a{font-size:12px;color:var(--text-muted)}header p{margin:0 0 4px}h1{font-size:22px;margin:0}a{text-decoration:none}.outline-page--entry{max-width:920px;grid-template-rows:auto auto;gap:22px}.outline-page--entry h1{font-size:28px}@media(max-height:600px){.outline-page{gap:8px}header p{display:none}h1{font-size:18px}}</style>
