import {computed} from 'vue';
import {useLocale} from '../shared/i18n/locale';
export function useLearningCenterCopy(){
 const {isEnglish,locale}=useLocale();
 const copy=computed(()=>isEnglish.value?{
  eyebrow:'YOUR LEARNING SPACE',title:'Pick up where you left off',intro:'Your questions, your courses, your next step.',newCourse:'Start new learning',courses:'My courses',bookmarks:'Saved steps',map:'Learning map',back:'Back to home',
  search:'Search course titles',sort:'Recent activity',resume:'Continue learning',recent:'Continue your latest task',empty:'No courses yet',emptyHint:'Start with a question. Your outline and progress will be kept here.',noResults:'No matching courses',clear:'Clear search',loading:'Loading your courses…',loadFailed:'Your courses could not be loaded.',retry:'Try again',
  DRAFT:'Outline draft',PREPARING:'Preparing',LEARNING:'Learning',COMPLETED:'Completed',NEEDS_ATTENTION:'Needs attention',
  draftAction:'Continue outline',prepareAction:'View preparation',learnAction:'Continue learning',reviewAction:'Review course',attentionAction:'View course status',
  draftHint:'Review your learning scope before preparing content.',prepareHint:'Your outline is confirmed. Learning content is being prepared.',attentionHint:'Some content needs attention. Open the course to review or retry.',point:'Last visited',progress:'knowledge points completed',updated:'Updated',learned:'Last learned',
  menu:'Course actions',rename:'Rename',remove:'Delete course',cancel:'Cancel',save:'Save name',working:'Working…',name:'Course name',renameFailed:'The course could not be renamed. Try again.',deleteFailed:'The course could not be deleted. Try again.',invalidName:'Use 1–160 characters.',deleteTitle:'Delete this course?',deleteHint:'The course and its saved steps will be hidden. Preparation will stop. This cannot be undone here.',
 }:{
  eyebrow:'你的学习空间',title:'回来，继续你的学习',intro:'从上次停下的地方开始，也可以探索一个新主题。',newCourse:'开始新学习',courses:'我的课程',bookmarks:'我的收藏',map:'学习地图',back:'返回首页',
  search:'搜索课程名称',sort:'最近活动',resume:'继续学习',recent:'继续最近任务',empty:'还没有课程',emptyHint:'从一个问题开始。大纲草稿和学习进度都会保存在这里。',noResults:'没有找到匹配的课程',clear:'清除搜索',loading:'正在读取你的课程…',loadFailed:'课程暂时无法加载。',retry:'重新加载',
  DRAFT:'大纲草稿',PREPARING:'准备中',LEARNING:'学习中',COMPLETED:'已完成',NEEDS_ATTENTION:'需要处理',
  draftAction:'继续编辑大纲',prepareAction:'查看准备进度',learnAction:'继续学习',reviewAction:'回顾课程',attentionAction:'查看课程状态',
  draftHint:'先审阅学习范围，确认后再准备内容。',prepareHint:'大纲已确认，正在准备学习内容。',attentionHint:'部分内容准备失败，可进入课堂查看原因并重试。',point:'上次停留',progress:'个知识点已完成',updated:'更新于',learned:'最近学习于',
  menu:'课程操作',rename:'重命名',remove:'删除课程',cancel:'取消',save:'保存名称',working:'正在处理…',name:'课程名称',renameFailed:'重命名失败，请重试。',deleteFailed:'删除课程失败，请重试。',invalidName:'名称须为 1–160 个字符。',deleteTitle:'删除这门课程？',deleteHint:'课程及关联收藏将从个人空间移除，正在准备的任务会停止。此处无法撤销。',
 });
 return {copy,locale};
}
