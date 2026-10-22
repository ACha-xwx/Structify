<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue';
import {ArrowRight,ArrowUpRight,Plus,Search,Bookmark,Network,BookOpen,ArrowDownWideNarrow} from '@lucide/vue';
import BrandStage from '../../shared/components/BrandStage.vue';
import AiTitle from '../../shared/components/AiTitle.vue';
import CourseCard from '../components/CourseCard.vue';
import CourseActionDialog from '../components/CourseActionDialog.vue';
import {courseLibrary,courseDestination,resumeCourse,sortCourses,type PersonalCourse} from '../course-library';
import {useLearningCenterCopy} from '../learning-center-copy';
const {copy}=useLearningCenterCopy();
const courses=ref<PersonalCourse[]>([]);const loading=ref(true);const error=ref('');const search=ref('');
const action=ref<{mode:'rename'|'delete';course:PersonalCourse}|null>(null);const busy=ref(false);const actionError=ref('');
const ordered=computed(()=>sortCourses(courses.value));const resume=computed(()=>resumeCourse(courses.value));
const filtered=computed(()=>ordered.value.filter(c=>c.title.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase())));
const resumeAction=computed(()=>!resume.value?copy.value.newCourse:resume.value.status==='DRAFT'?copy.value.draftAction:resume.value.status==='COMPLETED'?copy.value.reviewAction:resume.value.readableFragments?copy.value.learnAction:resume.value.status==='NEEDS_ATTENTION'?copy.value.attentionAction:copy.value.prepareAction);
let alive=true;let listVersion=0;let timer:ReturnType<typeof setTimeout>|undefined;
async function load(initial=true){
 const version=++listVersion;clearTimeout(timer);if(initial)loading.value=true;error.value='';
 try{const items=await courseLibrary.list();if(alive&&version===listVersion)courses.value=items;}
 catch{if(alive&&version===listVersion&&initial)error.value=copy.value.loadFailed;}
 finally{if(alive){loading.value=false;timer=setTimeout(()=>{if(document.visibilityState==='visible'&&!action.value)void load(false);else schedule();},15000);}}
}
function schedule(){clearTimeout(timer);if(alive)timer=setTimeout(()=>void load(false),15000);}
function open(mode:'rename'|'delete',course:PersonalCourse){action.value={mode,course};actionError.value='';}
async function confirm(title:string){
 if(!action.value||busy.value)return;const selected=action.value;
 if(selected.mode==='rename'&&(!title||title.length>160)){actionError.value=copy.value.invalidName;return;}
 busy.value=true;listVersion++;actionError.value='';
 try{
  if(selected.mode==='rename'){await courseLibrary.rename(selected.course.id,title);courses.value=courses.value.map(c=>c.id===selected.course.id?{...c,title}:c);}
  else{await courseLibrary.remove(selected.course.id);courses.value=courses.value.filter(c=>c.id!==selected.course.id);}
  action.value=null;
 }catch{actionError.value=selected.mode==='rename'?copy.value.renameFailed:copy.value.deleteFailed;}
 finally{busy.value=false;}
}
onMounted(()=>void load());onBeforeUnmount(()=>{alive=false;clearTimeout(timer);});
</script>
<template>
 <BrandStage wide>
  <main class="learning-center" aria-labelledby="learning-center-title">
   <header class="center-header"><div><AiTitle><h1 id="learning-center-title">{{copy.title}}</h1></AiTitle><p class="center-intro">{{copy.intro}}</p></div><RouterLink class="center-button center-button--primary" to="/studyroom"><Plus :size="19"/>{{copy.newCourse}}</RouterLink></header>
   <p v-if="loading" class="center-status" role="status">{{copy.loading}}</p>
   <div v-else-if="error" class="center-status" role="alert">{{error}} <button class="center-button" type="button" data-action="reload" @click="load()">{{copy.retry}}</button></div>
   <template v-else>
    <section v-if="resume" class="resume-panel" data-testid="resume" aria-labelledby="resume-title">
     <div class="resume-content"><p class="center-kicker">{{resume.lastLearningAt?copy.resume:copy.recent}}</p><h2 id="resume-title">{{resume.title}}</h2><p v-if="resume.lastPointTitle" class="resume-point">{{copy.point}}：{{resume.lastPointTitle}}</p><p v-else class="resume-point">{{resume.status==='DRAFT'?copy.draftHint:resume.status==='NEEDS_ATTENTION'?copy.attentionHint:copy.prepareHint}}</p><div v-if="resume.totalPoints" class="resume-progress"><span>{{resume.completedPoints}} / {{resume.totalPoints}} {{copy.progress}}</span><progress :value="resume.completedPoints" :max="resume.totalPoints" :aria-label="copy.progress"/></div></div>
     <RouterLink class="center-button center-button--primary" :to="courseDestination(resume)">{{resumeAction}}<ArrowRight :size="17"/></RouterLink>
    </section>
    <section class="course-library" aria-labelledby="course-library-title">
     <div class="library-heading"><h2 id="course-library-title">{{copy.courses}} <span>{{courses.length}}</span></h2><nav :aria-label="copy.eyebrow"><RouterLink to="/user/bookmarks"><Bookmark :size="15"/>{{copy.bookmarks}}<ArrowUpRight :size="13"/></RouterLink><RouterLink to="/learning-map"><Network :size="15"/>{{copy.map}}<ArrowUpRight :size="13"/></RouterLink></nav></div>
     <div v-if="courses.length" class="library-tools"><label class="course-search"><Search :size="17"/><input v-model="search" type="search" :placeholder="copy.search" :aria-label="copy.search" maxlength="160"/></label><span class="course-sort"><ArrowDownWideNarrow :size="15"/>{{copy.sort}}</span></div>
     <div v-if="filtered.length" class="course-grid"><CourseCard v-for="course in filtered" :key="course.id" :course="course" @rename="open('rename',$event)" @remove="open('delete',$event)"/></div>
     <div v-else-if="courses.length" class="library-empty"><Search :size="26" :stroke-width="1.25"/><h3>{{copy.noResults}}</h3><button type="button" class="center-button" @click="search=''">{{copy.clear}}</button></div>
     <div v-else class="library-empty"><BookOpen :size="36" :stroke-width="1.25"/><h3>{{copy.empty}}</h3><p>{{copy.emptyHint}}</p><RouterLink class="center-button center-button--primary" to="/studyroom">{{copy.newCourse}}<ArrowRight :size="16"/></RouterLink></div>
    </section>
   </template>
   <footer class="center-footer"><RouterLink to="/begin">{{copy.back}}<ArrowUpRight :size="16"/></RouterLink></footer>
   <CourseActionDialog v-if="action" :key="`${action.mode}:${action.course.id}`" :course="action.course" :mode="action.mode" :busy="busy" :error="actionError" @close="action=null" @confirm="confirm"/>
  </main>
 </BrandStage>
</template>
<style scoped src="../learning-center.css"></style>
