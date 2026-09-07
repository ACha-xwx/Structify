<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { auth } from "../../app/providers/runtime";
import { useLocale } from "../../shared/i18n/locale";
import type { ClassroomAction, ClassroomScript, ClassroomSession } from "../../shared/types/contracts";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import { userApi } from "../runtime";
import { createClassroomScriptsPreview, loadClassroomScripts, type ClassroomFixtureReason } from "../adapters/classroom";
import {
  actInClassroomPreview,
  classroomPreviewEnglishText,
  classroomPreviewNavigationContext,
  classroomPreviewScript,
  classroomPreviewScriptForSessionId,
  classroomPreviewSessionId,
  classroomPreviewTitleEn,
  createClassroomPreviewSession,
  isClassroomPreviewScript,
  isClassroomPreviewSession,
} from "../fixtures/classroom-preview";

const route = useRoute();
const router = useRouter();
const { isEnglish } = useLocale();
function queryString(value: unknown): string {
  if (Array.isArray(value)) return value.find((item) => typeof item === "string" && item.trim())?.trim() ?? "";
  return typeof value === "string" ? value.trim() : "";
}
const chapterId = computed(() => queryString(route.query.chapterId));
const lessonId = computed(() => queryString(route.query.lessonId));
const recoverySessionId = computed(() => queryString(route.query.sessionId));
const scripts = ref<ClassroomScript[]>([]);
const session = ref<ClassroomSession | null>(null);
const answer = ref("");
const loading = ref(true);
const acting = ref(false);
const error = ref<UserErrorPresentation | null>(null);
type ClassroomContentMode = "live" | "fixture";
const contentMode = ref<ClassroomContentMode>(auth.state.user ? "live" : "fixture");
const fixtureReason = ref<ClassroomFixtureReason | null>(auth.state.user ? null : "guest-preview");
type LocalErrorKey = "restore-session" | "classroom-scope" | "answer-required";
const localErrorKey = ref<LocalErrorKey | null>(null);
const isFixture = computed(() => contentMode.value === "fixture");
const copy = computed(() => isEnglish.value ? {
  eyebrow: "CLASSROOM",
  title: "Classroom",
  fixtureIntro: "The example classroom is ready to explore. Sign in only to restore and save a personal classroom session.",
  liveIntro: "The classroom advances through explanations, questions, and your responses.",
  fixtureBadge: "Example preview",
  fixtureFallbackEmpty: "The classroom service has not published a script for this chapter yet. You can explore a local example without creating a personal record.",
  fixtureFallbackUnavailable: "The classroom service is temporarily unavailable. You can continue with a local example; it does not save a personal record.",
  returnChapter: "Back to chapter",
  fixtureNotice: "The example classroom does not write to your learning record or call a model service.",
  liveNotice: "Classroom content is associated with the current account. Open courseware from the learning workbench.",
  loadingTitle: "Loading classroom",
  loadingDetail: "Reading the published classroom script for this chapter.",
  reload: "Reload",
  scriptsTitle: "Available classroom scripts",
  currentChapterScope: "Limited to this chapter",
  allCoursesScope: "All visible chapters",
  emptyTitle: "No classroom scripts available",
  emptyDetail: "No published, accessible classroom script is available for this chapter.",
  currentChapterClassroom: "Current chapter classroom",
  courseClassroom: "Course classroom",
  start: "Start classroom",
  classroomInProgress: "Classroom in progress",
  paused: "Paused",
  inProgress: "In progress",
  classroomContent: "Classroom content",
  myAnswer: "My answer",
  answerPlaceholder: "Answer the current classroom question",
  viewAlgorithmStage: "Open algorithm stage",
  returnCourseSelection: "Back to course selection",
  summary: "Classroom summary",
  previewSource: "Preview source",
  classroomStatus: "Classroom status",
  fixtureRail: "The example classroom does not save a personal session.",
  liveRailAccount: "This classroom is associated with the current account.",
  liveRailProgress: "Classroom actions are saved with learning progress.",
  learningScope: "Learning scope",
  currentChapter: "Current chapter",
  allCourses: "All courses",
  answerSeparator: ": ",
  previewTitle: "Sequential-list insertion · local classroom",
  stateLabels: { OPENING: "Opening", EXPLAIN: "Explanation", QUESTION: "Question", WAITING: "Waiting for an answer", DISCUSS: "Discussion", BLACKBOARD: "Board demonstration", SUMMARY: "Summary" },
  stageLabels: { topic: "Current topic", prompt: "Classroom question", hint: "Thinking prompt", question: "Classroom question", discussion: "Discussion", explanation: "Explanation", blackboard: "Demonstration", summary: "Lesson summary", step: "Current step", result: "Result", response: "My response" },
  actionLabels: { ANSWER: "Submit answer", PAUSE: "Pause classroom", RESUME: "Resume classroom", CONTINUE: "Next step", FINISH: "Finish and summarize" },
  localErrors: {
    "restore-session": { title: "Sign in to restore a classroom session", message: "Guests can start the example classroom. Only signed-in accounts can restore a personal classroom session." },
    "classroom-scope": { title: "Classroom scope unavailable", message: "This classroom script has no usable chapter scope. Return to the course map and open it again." },
    "answer-required": { title: "Provide a classroom answer", message: "Your classroom answer cannot be empty." },
  },
} : {
  eyebrow: "课堂互动",
  title: "课堂学习",
  fixtureIntro: "示例课堂可以直接操作；登录后才会恢复并保存个人课堂会话。",
  liveIntro: "课堂会随着讲解、提问和你的回答逐步推进。",
  fixtureBadge: "示例预览",
  fixtureFallbackEmpty: "课堂服务暂未为本章节发布脚本。你可以先使用本地示例，不会创建个人学习记录。",
  fixtureFallbackUnavailable: "课堂服务暂时不可用。你可以先继续使用本地示例，不会保存个人学习记录。",
  returnChapter: "返回章节",
  fixtureNotice: "示例课堂不会写入个人学习记录，也不会调用模型服务。",
  liveNotice: "课堂内容会与当前账户同步；课件播放请从学习台的课程课件入口打开。",
  loadingTitle: "正在加载课堂",
  loadingDetail: "正在读取当前章节的已发布课堂脚本。",
  reload: "重新加载",
  scriptsTitle: "可用课堂脚本",
  currentChapterScope: "已限定当前章节",
  allCoursesScope: "全部可见章节",
  emptyTitle: "暂无可用课堂脚本",
  emptyDetail: "当前章节没有返回已发布且可访问的课堂脚本。",
  currentChapterClassroom: "当前章节课堂",
  courseClassroom: "课程课堂",
  start: "开始课堂",
  classroomInProgress: "课堂进行中",
  paused: "已暂停",
  inProgress: "进行中",
  classroomContent: "课堂内容",
  myAnswer: "我的回答",
  answerPlaceholder: "根据当前课堂问题作答",
  viewAlgorithmStage: "查看算法舞台",
  returnCourseSelection: "返回课程选择",
  summary: "课堂总结",
  previewSource: "预览来源",
  classroomStatus: "课堂状态",
  fixtureRail: "示例课堂不会保存个人会话。",
  liveRailAccount: "当前课堂已与账户关联。",
  liveRailProgress: "课堂动作会按学习进度保存。",
  learningScope: "学习范围",
  currentChapter: "当前章节",
  allCourses: "全部课程",
  answerSeparator: "：",
  previewTitle: "顺序表插入 · 本地课堂",
  stateLabels: { OPENING: "开场", EXPLAIN: "讲解", QUESTION: "提问", WAITING: "等待回答", DISCUSS: "讨论", BLACKBOARD: "黑板演示", SUMMARY: "总结" },
  stageLabels: { topic: "当前主题", prompt: "课堂问题", hint: "思考提示", question: "课堂问题", discussion: "讨论内容", explanation: "讲解", blackboard: "演示内容", summary: "本节总结", step: "当前步骤", result: "结果", response: "我的回答" },
  actionLabels: { ANSWER: "提交回答", PAUSE: "暂停课堂", RESUME: "继续课堂", CONTINUE: "下一步", FINISH: "结束并总结" },
  localErrors: {
    "restore-session": { title: "登录后恢复课堂会话", message: "游客可以开始示例课堂；只有已登录账号才能恢复个人课堂会话。" },
    "classroom-scope": { title: "课堂范围不可用", message: "该课堂脚本没有可用的章节范围，请返回课程目录后重新打开。" },
    "answer-required": { title: "请先填写课堂回答", message: "课堂回答不能为空。" },
  },
});
const scriptChapterId = computed(() => {
  const scriptId = session.value?.scriptId;
  if (!scriptId) return "";
  const previewContext = classroomPreviewNavigationContext(scriptId);
  if (previewContext?.chapterId) return previewContext.chapterId;
  return scripts.value.find((script) => script.id === scriptId)?.chapterId.trim() || "";
});
const scriptLessonId = computed(() => {
  const scriptId = session.value?.scriptId;
  if (!scriptId) return "";
  return classroomPreviewNavigationContext(scriptId)?.lessonId ?? "";
});
const resolvedChapterId = computed(() => {
  if (scriptChapterId.value) return scriptChapterId.value;
  if (chapterId.value.trim()) return chapterId.value.trim();
  return session.value ? classroomPreviewScriptForSessionId(session.value.id)?.chapterId ?? "" : "";
});
const resolvedLessonId = computed(() => {
  if (scriptLessonId.value) return scriptLessonId.value;
  if (lessonId.value) return lessonId.value;
  return "";
});
const chapterReturnTarget = computed(() => resolvedChapterId.value
  ? {
      path: "/user/chapters",
      query: {
        chapterId: resolvedChapterId.value,
        ...(resolvedLessonId.value ? { lessonId: resolvedLessonId.value } : {}),
        from: "classroom",
      },
    }
  : "/user/chapters");
const stageTarget = computed(() => {
  if (!session.value || !resolvedChapterId.value) return null;
  return {
    path: "/user/animation",
    query: {
      chapterId: resolvedChapterId.value,
      ...(resolvedLessonId.value ? { lessonId: resolvedLessonId.value } : {}),
      from: "classroom",
      sessionId: session.value.id,
    },
  };
});

const stateLabels = computed<Record<string, string>>(() => copy.value.stateLabels);
const stageLabels = computed<Record<string, string>>(() => copy.value.stageLabels);
const stageEntries = computed(() => Object.entries(session.value?.stage || {}).filter(([, value]) => value !== null && value !== undefined));
const allowedActions = computed<ClassroomAction[]>(() => {
  if (!session.value) return [];
  if (session.value.paused) return ["RESUME"];
  const actions: ClassroomAction[] = ["PAUSE"];
  if (session.value.state === "WAITING") actions.unshift("ANSWER");
  if (session.value.state !== "SUMMARY") actions.push("CONTINUE", "FINISH");
  return actions;
});
const actionLabels = computed<Record<ClassroomAction, string>>(() => copy.value.actionLabels);
const englishApiErrors: Record<UserErrorPresentation["kind"], Pick<UserErrorPresentation, "title" | "message">> = {
  permission: { title: "Sign in to unlock this feature", message: "Guests can browse public learning content. Sign in here to use model services or save personal learning records." },
  "not-found": { title: "Resource unavailable", message: "This resource may not be published, may have been removed, or may be outside the current account's access scope." },
  conflict: { title: "The current state has changed", message: "Refresh before continuing so the latest learning state is not overwritten." },
  limited: { title: "Too many requests", message: "The service is temporarily limiting requests. Try again shortly." },
  timeout: { title: "Request timed out", message: "An upstream service did not respond in time." },
  service: { title: "Learning service unavailable", message: "This page position has been kept. Try again when the service recovers." },
  network: { title: "Network connection unavailable", message: "Check the connection and try again. Your current learning position will not be lost." },
  validation: { title: "Unable to process this submission", message: "Review the submitted content and try again." },
  unknown: { title: "Action not completed", message: "The service returned an unexpected result." },
};
function localizeApiError(current: UserErrorPresentation): Pick<UserErrorPresentation, "title" | "message"> {
  if (current.kind === "permission" && current.title === "当前账号没有权限") {
    return { title: "Account access denied", message: "The service denied access to this learning resource or action." };
  }
  return englishApiErrors[current.kind];
}
const displayedError = computed<UserErrorPresentation | null>(() => {
  if (!error.value) return null;
  if (localErrorKey.value) return { ...error.value, ...copy.value.localErrors[localErrorKey.value] };
  if (!isEnglish.value) return error.value;
  return { ...error.value, ...localizeApiError(error.value) };
});

const contentNotice = computed(() => {
  if (!isFixture.value) return copy.value.liveNotice;
  if (fixtureReason.value === "api-empty") return copy.value.fixtureFallbackEmpty;
  if (fixtureReason.value === "api-unavailable") return copy.value.fixtureFallbackUnavailable;
  return copy.value.fixtureNotice;
});

const previewEnglishText: Record<string, string> = {
  "顺序表的插入": "Insertion into a sequential list",
  "把 23 插入 12, 18, 27, 31, 44 时，为什么从尾部开始移动？": "When inserting 23 into 12, 18, 27, 31, 44, why do we shift from the end?",
  "先观察空位，再解释覆盖风险。": "Locate the empty slot, then explain the overwrite risk.",
  "比较 27": "Compare 27",
  "回答移动方向": "Answer the shift direction",
  "复盘插入结果": "Review the insertion result",
  "O(n) 移动边界": "O(n) shift boundary",
  "先从尾部右移，再把新值写入空位；这样不会覆盖尚未读取的元素。": "Shift right from the end, then write the new value into the empty slot. This avoids overwriting elements that have not yet been read.",
  "需要从尾部开始，避免覆盖尚未读取的值。": "Start from the end to avoid overwriting values that have not yet been read.",
  "回答抓住了移动边界。": "Your answer identifies the shifting boundary.",
  "再想想覆盖风险和移动方向。": "Consider the overwrite risk and the direction of shifting.",
  "（空回答）": "(No answer)",
};

function setLocalError(key: LocalErrorKey): void {
  localErrorKey.value = key;
  error.value = { kind: key === "restore-session" ? "permission" : "validation", title: "", message: "", retryable: false };
}

function displayScriptTitle(script: ClassroomScript): string {
  if (!isEnglish.value) return script.title;
  return classroomPreviewTitleEn(script.id) || (script.id === classroomPreviewScript.id ? copy.value.previewTitle : script.title);
}

function narrowScriptsForLesson(nextScripts: ClassroomScript[]): ClassroomScript[] {
  if (!lessonId.value) return nextScripts;
  const matching = nextScripts.filter((script) => {
    const previewContext = classroomPreviewNavigationContext(script.id);
    return script.chapterId.trim() === lessonId.value
      || script.id === lessonId.value
      || previewContext?.lessonId === lessonId.value;
  });
  return matching.length ? matching : nextScripts;
}

function displayStageValue(value: unknown): string {
  const text = typeof value === "string" ? value : JSON.stringify(value);
  if (!isEnglish.value || !session.value || !isClassroomPreviewSession(session.value.id)) return text;
  return classroomPreviewEnglishText(session.value.scriptId, text) || previewEnglishText[text] || text;
}

async function load() {
  const requestedChapterId = chapterId.value;
  const requestedLessonId = lessonId.value;
  const requestedSessionId = recoverySessionId.value;
  if (requestedSessionId && session.value?.id === requestedSessionId) return;
  loading.value = true;
  error.value = null;
  localErrorKey.value = null;
  session.value = null;
  scripts.value = [];
  contentMode.value = auth.state.user ? "live" : "fixture";
  fixtureReason.value = auth.state.user ? null : "guest-preview";
  if (!auth.state.user) {
    const previewScript = requestedSessionId ? classroomPreviewScriptForSessionId(requestedSessionId) : null;
    if (requestedSessionId && !previewScript) setLocalError("restore-session");
    else {
      const snapshot = await loadClassroomScripts(userApi, {
        chapterId: requestedChapterId || requestedLessonId || previewScript?.chapterId,
        signedIn: false,
      });
      scripts.value = narrowScriptsForLesson(snapshot.scripts);
      contentMode.value = snapshot.mode;
      fixtureReason.value = snapshot.fixtureReason;
      if (previewScript) session.value = createClassroomPreviewSession(previewScript.id);
    }
    loading.value = false;
    return;
  }
  // Local preview sessions can be reopened after a mode switch or a page
  // refresh. They remain explicitly non-persistent even for signed-in users.
  const requestedPreviewScript = requestedSessionId ? classroomPreviewScriptForSessionId(requestedSessionId) : null;
  if (requestedPreviewScript) {
    scripts.value = narrowScriptsForLesson(createClassroomScriptsPreview(requestedChapterId || requestedPreviewScript.chapterId, "api-empty").scripts);
    contentMode.value = "fixture";
    fixtureReason.value = "api-empty";
    session.value = createClassroomPreviewSession(requestedPreviewScript.id);
    loading.value = false;
    return;
  }
  try {
    const snapshot = await loadClassroomScripts(userApi, { chapterId: requestedChapterId, signedIn: true });
    if (requestedChapterId !== chapterId.value || requestedSessionId !== recoverySessionId.value) return;
    scripts.value = narrowScriptsForLesson(snapshot.scripts);
    contentMode.value = snapshot.mode;
    fixtureReason.value = snapshot.fixtureReason;
    if (requestedSessionId) {
      const nextSession = await userApi.getClassroomSession(requestedSessionId);
      if (requestedChapterId === chapterId.value && requestedSessionId === recoverySessionId.value) {
        session.value = nextSession;
        contentMode.value = "live";
        fixtureReason.value = null;
      }
    }
  } catch (cause) {
    if (requestedChapterId === chapterId.value && requestedSessionId === recoverySessionId.value) {
      localErrorKey.value = null;
      error.value = presentUserError(cause);
    }
  } finally {
    if (requestedChapterId === chapterId.value && requestedSessionId === recoverySessionId.value) loading.value = false;
  }
}

async function start(scriptId: string) {
  const script = scripts.value.find((item) => item.id === scriptId);
  const previewContext = classroomPreviewNavigationContext(scriptId);
  const scriptChapterId = previewContext?.chapterId || script?.chapterId.trim() || "";
  const scriptLessonId = previewContext?.lessonId || "";
  if (!scriptChapterId) {
    setLocalError("classroom-scope");
    return;
  }
  acting.value = true;
  error.value = null;
  localErrorKey.value = null;
  if (isClassroomPreviewScript(scriptId)) {
    session.value = createClassroomPreviewSession(scriptId);
    contentMode.value = "fixture";
    fixtureReason.value = auth.state.user ? "api-empty" : "guest-preview";
    acting.value = false;
    await router.replace({ query: { ...route.query, chapterId: scriptChapterId, ...(scriptLessonId ? { lessonId: scriptLessonId } : { lessonId: undefined }), sessionId: session.value.id } });
    return;
  }
  try {
    const nextSession = await userApi.startClassroom(scriptId);
    session.value = nextSession;
    await router.replace({ query: { ...route.query, chapterId: scriptChapterId, ...(scriptLessonId ? { lessonId: scriptLessonId } : { lessonId: undefined }), sessionId: nextSession.id } });
  } catch (cause) { localErrorKey.value = null; error.value = presentUserError(cause); } finally { acting.value = false; }
}

async function act(action: ClassroomAction) {
  if (!session.value) return;
  if (action === "ANSWER" && !answer.value.trim()) { setLocalError("answer-required"); return; }
  acting.value = true;
  error.value = null;
  localErrorKey.value = null;
  if (isClassroomPreviewSession(session.value.id)) {
    session.value = actInClassroomPreview(session.value, action, answer.value.trim());
    if (action === "ANSWER") answer.value = "";
    acting.value = false;
    return;
  }
  try {
    session.value = await userApi.actInClassroom(session.value.id, { action, ...(action === "ANSWER" ? { content: answer.value.trim() } : {}) });
    if (action === "ANSWER") answer.value = "";
  } catch (cause) { localErrorKey.value = null; error.value = presentUserError(cause); } finally { acting.value = false; }
}

watch([chapterId, lessonId, recoverySessionId], () => { void load(); }, { immediate: true });
watch(() => auth.state.user?.id, () => { void load(); });
</script>

<template>
  <UserFrame shell="course">
    <section class="user-page" aria-labelledby="classroom-title">
      <header class="user-page__heading"><div><p class="user-page__eyebrow">{{ copy.eyebrow }}</p><h1 id="classroom-title">{{ copy.title }}</h1><p class="user-page__intro">{{ isFixture ? copy.fixtureIntro : copy.liveIntro }}</p></div><div class="user-page__actions"><span v-if="isFixture" class="user-preview-chip">{{ copy.fixtureBadge }}</span><RouterLink class="user-action" :to="chapterReturnTarget">{{ copy.returnChapter }}</RouterLink></div></header>
      <p class="inline-notice inline-notice--warning">{{ contentNotice }}</p>
      <UserState v-if="loading" mode="loading" :title="copy.loadingTitle" :message="copy.loadingDetail" />
      <UserState v-else-if="displayedError && !session && (!scripts.length || recoverySessionId)" :mode="displayedError.kind === 'permission' ? 'permission' : 'error'" :title="displayedError.title" :message="displayedError.message" :retry-label="displayedError.retryable ? copy.reload : undefined" @retry="load" />

      <template v-else>
        <section v-if="!session" class="user-page__section"><header><h2>{{ copy.scriptsTitle }}</h2><p>{{ chapterId ? copy.currentChapterScope : copy.allCoursesScope }}</p></header>
          <UserState v-if="!scripts.length" mode="empty" :title="copy.emptyTitle" :message="copy.emptyDetail" />
          <div v-else class="user-list"><div v-for="script in scripts" :key="script.id" class="user-list__row"><div><h3>{{ displayScriptTitle(script) }}</h3><p>{{ chapterId ? copy.currentChapterClassroom : copy.courseClassroom }}</p></div><button class="user-action user-action--primary" :data-testid="`classroom-start-${script.id}`" type="button" :disabled="acting" @click="start(script.id)">{{ copy.start }}</button></div></div>
        </section>

        <template v-else>
          <section class="user-panel classroom-stage" aria-live="polite"><div class="user-page__actions"><span class="user-status">{{ stateLabels[session.state] || copy.classroomInProgress }}</span><span class="user-list__meta">{{ session.paused ? copy.paused : copy.inProgress }}</span></div><dl class="user-kv"><template v-for="([key, value]) in stageEntries" :key="key"><dt>{{ stageLabels[key] || copy.classroomContent }}</dt><dd>{{ displayStageValue(value) }}</dd></template></dl></section>
          <section v-if="session.state === 'WAITING' && !session.paused" class="user-form user-panel"><label>{{ copy.myAnswer }}<textarea v-model="answer" data-testid="classroom-answer" maxlength="4000" :placeholder="copy.answerPlaceholder"></textarea></label></section>
          <p v-if="session.answerEvaluation" class="inline-notice" :data-tone="session.answerEvaluation.status === 'CORRECT' ? 'success' : 'warning'">{{ displayStageValue(session.answerEvaluation.feedback) }}<span v-if="session.answerEvaluation.misconception">{{ copy.answerSeparator }}{{ displayStageValue(session.answerEvaluation.misconception) }}</span></p>
          <p v-if="displayedError" class="inline-notice" data-tone="danger" role="alert">{{ displayedError.message }}</p>
          <div class="user-page__actions"><RouterLink v-if="stageTarget" class="user-action" data-testid="classroom-animation" :to="stageTarget">{{ copy.viewAlgorithmStage }}</RouterLink><RouterLink v-else class="user-action" data-testid="classroom-animation-unavailable" to="/user/chapters">{{ copy.returnCourseSelection }}</RouterLink><button v-for="action in allowedActions" :key="action" class="user-action" :class="{ 'user-action--primary': action === 'ANSWER' || action === 'CONTINUE' }" :data-testid="`classroom-action-${action}`" type="button" :disabled="acting" @click="act(action)">{{ actionLabels[action] }}</button></div>
          <section v-if="session.summary" class="user-panel"><h2>{{ copy.summary }}</h2><p>{{ displayStageValue(session.summary) }}</p></section>
        </template>
      </template>
    </section>
    <template #rail><div class="user-rail-list"><strong>{{ isFixture ? copy.previewSource : copy.classroomStatus }}</strong><p v-if="isFixture">{{ copy.fixtureRail }}</p><template v-else><p>{{ copy.liveRailAccount }}</p><p>{{ copy.liveRailProgress }}</p></template><strong>{{ copy.learningScope }}</strong><p>{{ resolvedChapterId ? copy.currentChapter : copy.allCourses }}</p></div></template>
  </UserFrame>
</template>
