<script setup lang="ts">
import {computed} from "vue";
import {useRoute,useRouter} from "vue-router";
import StudyroomOutlineFlow from "../components/studyroom/StudyroomOutlineFlow.vue";
const route=useRoute();const router=useRouter();
const draftId=computed(()=>typeof route.params.draftId==="string"?route.params.draftId:undefined);
const initialQuestion=computed(()=>typeof route.query.question==="string"?route.query.question:undefined);
const sourceRoomId=computed(()=>typeof route.query.sourceRoomId==="string"?route.query.sourceRoomId:undefined);
</script>
<template><main class="outline-page"><header><div><p>STRUCTIFY · STUDYROOM</p><h1>课程问答</h1></div><RouterLink to="/">返回学习入口</RouterLink></header><StudyroomOutlineFlow :draft-id="draftId" :initial-question="initialQuestion" :source-room-id="sourceRoomId" @draft="id=>router.replace(`/studyroom/outlines/${encodeURIComponent(id)}`)" @confirmed="id=>router.replace(`/studyroom/${encodeURIComponent(id)}`)"/></main></template>
<style scoped>.outline-page{max-width:1480px;margin:auto;display:grid;gap:20px;color:var(--text)}header{display:flex;justify-content:space-between;align-items:center}header p,header a{font-size:12px;color:var(--text-muted)}h1{font-size:28px;margin:6px 0}a{text-decoration:none}</style>
