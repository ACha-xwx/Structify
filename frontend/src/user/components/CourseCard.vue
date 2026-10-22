<script setup lang="ts">
import {ArrowUpRight,BookOpen,FilePenLine,LoaderCircle,Ellipsis,Check,TriangleAlert} from '@lucide/vue';
import {computed} from 'vue';
import {courseDestination,type PersonalCourse} from '../course-library';
import {useLearningCenterCopy} from '../learning-center-copy';
const props=defineProps<{course:PersonalCourse}>();
defineEmits<{rename:[course:PersonalCourse];remove:[course:PersonalCourse]}>();
const {copy,locale}=useLearningCenterCopy();
const icon=computed(()=>({DRAFT:FilePenLine,PREPARING:LoaderCircle,LEARNING:BookOpen,COMPLETED:Check,NEEDS_ATTENTION:TriangleAlert})[props.course.status]);
const action=computed(()=>props.course.status==='DRAFT'?copy.value.draftAction:props.course.status==='COMPLETED'?copy.value.reviewAction:props.course.readableFragments?copy.value.learnAction:props.course.status==='NEEDS_ATTENTION'?copy.value.attentionAction:copy.value.prepareAction);
const date=computed(()=>new Intl.DateTimeFormat(locale.value,{month:'short',day:'numeric'}).format(new Date(props.course.activityAt)));
</script>
<template>
 <article class="course-card" data-testid="course-card">
  <div class="course-card__body">
   <div class="course-card__meta"><span class="course-card__state"><component :is="icon" :size="13"/>{{copy[course.status]}}</span>
    <details class="course-menu"><summary :aria-label="`${copy.menu}：${course.title}`"><Ellipsis :size="20"/></summary><div class="course-menu__items"><button type="button" data-action="rename" @click="$emit('rename',course);($event.currentTarget as HTMLElement).closest('details')?.removeAttribute('open')">{{copy.rename}}</button><button type="button" data-action="delete" @click="$emit('remove',course);($event.currentTarget as HTMLElement).closest('details')?.removeAttribute('open')">{{copy.remove}}</button></div></details>
   </div>
   <h3>{{course.title}}</h3>
   <p v-if="course.status==='DRAFT'">{{copy.draftHint}}</p><p v-else-if="course.status==='NEEDS_ATTENTION'">{{copy.attentionHint}}</p><p v-else-if="course.totalPoints">{{course.completedPoints}} / {{course.totalPoints}} {{copy.progress}}</p><p v-else>{{copy.prepareHint}}</p>
   <footer><time :datetime="course.activityAt">{{copy.updated}} {{date}}</time><RouterLink :to="courseDestination(course)">{{action}}<ArrowUpRight :size="15"/></RouterLink></footer>
  </div>
 </article>
</template>
<style scoped src="./course-card.css"></style>
