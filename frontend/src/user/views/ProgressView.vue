<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { auth } from "../../app/providers/runtime";
import type { LearningProgress } from "../../shared/types/contracts";
import { useLocale } from "../../shared/i18n/locale";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import { userApi } from "../runtime";
import { createLoginTarget } from "../login-target";

const progress = ref<LearningProgress | null>(null);
const route = useRoute();
const { isEnglish } = useLocale();
const loginTarget = computed(() => createLoginTarget(route.fullPath));
const loading = ref(true);
const savingChapterId = ref<string | null | undefined>(undefined);
const loadError = ref<UserErrorPresentation | null>(null);
const saveError = ref<UserErrorPresentation | null>(null);
const reviewSaved = ref(false);
const lastReviewChapterId = ref<string | null>(null);
let loadVersion = 0;
let progressOwnerId: number | null = null;

const isAuthenticated = computed(() => Boolean(auth.state.user));
const saving = computed(() => savingChapterId.value !== undefined);
const initialLoading = computed(() => loading.value && !progress.value);
const refreshing = computed(() => loading.value && Boolean(progress.value));
const copy = computed(() => isEnglish.value ? {
  eyebrow: "Learning record",
  title: "Review",
  introAuthenticated: "Review recorded learning activity returned by the service without replacing it with percentages or estimated statistics.",
  introGuest: "Personal review records load after sign-in. Course browsing and algorithm previews remain available.",
  refresh: "Refresh",
  signInTitle: "Sign in to view your review",
  signInDetail: "Your saved learning record appears here. No placeholder records are used.",
  signInContinue: "Sign in to continue",
  loadingTitle: "Loading learning records",
  loadingDetail: "Collecting saved learning activity.",
  reload: "Reload",
  signIn: "Sign in",
  refreshing: "Refreshing. The most recently loaded data remains visible.",
  recordedActivity: "Recorded activity",
  recordedActivityCount: (value: number) => `${value} saved learning ${value === 1 ? "activity" : "activities"}`,
  saving: "Saving...",
  completeAll: "Complete this overall review",
  reviewRecorded: "Review completion recorded",
  retryReview: "Submit review again",
  emptyTitle: "No chapter review data yet",
  emptyDetail: "The service returns records after you view resources, Q&A, classroom sessions, algorithms, or code.",
  chapter: "Chapter",
  chat: "Q&A",
  classroom: "Classroom",
  algorithm: "Algorithm",
  code: "Code",
  other: "Other",
  latestActivity: "Latest activity",
  openCourse: "Open course",
  completeReview: "Complete review",
  refreshFailed: "Refresh failed",
  recordedScope: "Record scope",
  recordedScopeDetail: "Only saved learning activity is shown.",
  currentView: "Current view",
  currentViewDetail: "The page stays empty when there is not enough data; it does not fill gaps with estimates.",
  savePermissionTitle: "Sign in to save your review",
  savePermissionDetail: "Review completion is written to your personal learning record. Sign in before saving it.",
  permissionTitle: "Sign in to use personal review",
  permissionDetail: "Courses and algorithm previews remain available. Sign in before viewing or saving personal learning records.",
  resourceUnavailableTitle: "This learning record is not available",
  resourceUnavailableDetail: "It may no longer be available for this account.",
  stateChangedTitle: "This learning state has changed",
  stateChangedDetail: "Refresh and try again so the latest learning state is kept.",
  requestLimitedTitle: "Please wait before trying again",
  requestLimitedDetail: "The service is temporarily limiting requests.",
  serviceUnavailableTitle: "Learning services are temporarily unavailable",
  serviceUnavailableDetail: "Your current page remains available. Try again once the service recovers.",
  timeoutTitle: "The request timed out",
  timeoutDetail: "The service did not respond in time.",
  networkTitle: "Network connection is unavailable",
  networkDetail: "Check the connection and try again. Your current learning context will remain here.",
  validationTitle: "This review cannot be processed",
  validationDetail: "Check the request and try again.",
  unknownTitle: "This action was not completed",
  unknownDetail: "The service returned an unexpected result. Please try again.",
} : {
  eyebrow: "学习记录",
  title: "复盘",
  introAuthenticated: "显示服务端聚合的真实学习活动，不用百分比或推测统计替代。",
  introGuest: "个人复盘记录只在登录后读取；课程浏览和算法预览不受影响。",
  refresh: "刷新",
  signInTitle: "登录后查看个人复盘",
  signInDetail: "这里显示你的服务端学习记录，不会用虚构数据填充。",
  signInContinue: "登录后继续",
  loadingTitle: "正在读取学习记录",
  loadingDetail: "正在汇总已保存的学习活动。",
  reload: "重新加载",
  signIn: "前往登录",
  refreshing: "正在刷新，继续显示上次成功读取的数据。",
  recordedActivity: "已记录活动",
  recordedActivityCount: (value: number) => `${value} 项服务端学习活动`,
  saving: "正在保存…",
  completeAll: "完成本次总复盘",
  reviewRecorded: "复盘完成已记录",
  retryReview: "重新提交复盘",
  emptyTitle: "暂无章节复盘数据",
  emptyDetail: "查看资源、问答、课堂、动画或代码后，服务端才会返回相应记录。",
  chapter: "第",
  chat: "问答",
  classroom: "课堂",
  algorithm: "动画",
  code: "代码",
  other: "其他",
  latestActivity: "最近活动",
  openCourse: "进入章节",
  completeReview: "完成复盘",
  refreshFailed: "刷新失败",
  recordedScope: "记录范围",
  recordedScopeDetail: "仅显示已保存的学习活动。",
  currentView: "当前视图",
  currentViewDetail: "没有足够数据时保持空状态，不用推测值填充。",
  savePermissionTitle: "登录后保存复盘",
  savePermissionDetail: "复盘会写入个人学习记录；登录后才能保存。",
  permissionTitle: "登录后查看个人复盘",
  permissionDetail: "课程与算法预览保持可用；查看或保存个人学习记录时请在此处登录。",
  resourceUnavailableTitle: "学习记录不可访问",
  resourceUnavailableDetail: "该学习记录可能不再对当前账户可用。",
  stateChangedTitle: "学习状态已变化",
  stateChangedDetail: "请刷新后继续操作，以保留最新学习状态。",
  requestLimitedTitle: "请求过于频繁",
  requestLimitedDetail: "服务暂时限制了请求，请稍后重试。",
  serviceUnavailableTitle: "学习服务暂不可用",
  serviceUnavailableDetail: "当前页面位置已保留，服务恢复后可再次尝试。",
  timeoutTitle: "请求超时",
  timeoutDetail: "服务未在规定时间内响应。",
  networkTitle: "网络连接不可用",
  networkDetail: "请检查网络后重试，当前学习位置不会丢失。",
  validationTitle: "复盘无法处理",
  validationDetail: "请检查请求后重试。",
  unknownTitle: "操作未完成",
  unknownDetail: "服务返回了未预期的结果，请稍后重试。",
});
const displayedLoadError = computed(() => loadError.value ? localizedUserError(loadError.value) : null);
const displayedSaveError = computed(() => saveError.value ? localizedUserError(saveError.value) : null);

function currentUserId(): number | null {
  return auth.state.user?.id ?? null;
}

function clearGuestState(): void {
  progress.value = null;
  progressOwnerId = null;
  loadError.value = null;
  saveError.value = null;
  reviewSaved.value = false;
  lastReviewChapterId.value = null;
  savingChapterId.value = undefined;
}

function formatActivityTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString(isEnglish.value ? "en-US" : "zh-CN", { dateStyle: "medium", timeStyle: "short" });
}

async function load(): Promise<void> {
  const version = ++loadVersion;
  const requestedUserId = currentUserId();
  if (requestedUserId === null) {
    clearGuestState();
    loading.value = false;
    return;
  }
  if (progressOwnerId !== null && progressOwnerId !== requestedUserId) progress.value = null;
  loading.value = true;
  loadError.value = null;
  try {
    const nextProgress = await userApi.getLearningProgress();
    if (version === loadVersion && requestedUserId === currentUserId()) {
      progress.value = nextProgress;
      progressOwnerId = requestedUserId;
    }
  } catch (cause) {
    if (version === loadVersion && requestedUserId === currentUserId()) {
      loadError.value = presentUserError(cause);
      if (loadError.value.kind === "permission") progress.value = null;
    }
  } finally {
    if (version === loadVersion && requestedUserId === currentUserId()) loading.value = false;
  }
}

async function completeReview(chapterId: string | null = null): Promise<void> {
  if (saving.value) return;
  const requestedUserId = currentUserId();
  if (requestedUserId === null) {
    saveError.value = { kind: "permission", title: copy.value.savePermissionTitle, message: copy.value.savePermissionDetail, retryable: false };
    return;
  }
  const normalizedChapterId = chapterId || null;
  savingChapterId.value = normalizedChapterId;
  lastReviewChapterId.value = normalizedChapterId;
  saveError.value = null;
  reviewSaved.value = false;
  try {
    await userApi.recordLearningEvent({ eventType: "REVIEW_COMPLETED", chapterId: normalizedChapterId });
    if (requestedUserId !== currentUserId()) return;
    reviewSaved.value = true;
    await load();
  } catch (cause) {
    if (requestedUserId === currentUserId()) saveError.value = presentUserError(cause);
  } finally {
    savingChapterId.value = undefined;
  }
}

function retrySave(): Promise<void> {
  return completeReview(lastReviewChapterId.value);
}

function localizedUserError(error: UserErrorPresentation): UserErrorPresentation {
  if (!isEnglish.value) return error;
  const localized = {
    permission: { title: copy.value.permissionTitle, message: copy.value.permissionDetail },
    "not-found": { title: copy.value.resourceUnavailableTitle, message: copy.value.resourceUnavailableDetail },
    conflict: { title: copy.value.stateChangedTitle, message: copy.value.stateChangedDetail },
    limited: { title: copy.value.requestLimitedTitle, message: copy.value.requestLimitedDetail },
    timeout: { title: copy.value.timeoutTitle, message: copy.value.timeoutDetail },
    service: { title: copy.value.serviceUnavailableTitle, message: copy.value.serviceUnavailableDetail },
    network: { title: copy.value.networkTitle, message: copy.value.networkDetail },
    validation: { title: copy.value.validationTitle, message: copy.value.validationDetail },
    unknown: { title: copy.value.unknownTitle, message: copy.value.unknownDetail },
  }[error.kind];
  return localized ? { ...error, ...localized } : error;
}

onMounted(load);
watch(() => auth.state.user?.id, () => { void load(); });
</script>

<template>
  <UserFrame shell="course">
    <section class="user-page" aria-labelledby="progress-title">
      <header class="user-page__heading">
        <div>
          <p class="user-page__eyebrow">{{ copy.eyebrow }}</p>
          <h1 id="progress-title">{{ copy.title }}</h1>
          <p class="user-page__intro">{{ isAuthenticated ? copy.introAuthenticated : copy.introGuest }}</p>
        </div>
        <button class="user-action" data-testid="progress-refresh" type="button" :disabled="loading || saving" @click="load">{{ copy.refresh }}</button>
      </header>

      <UserState
        v-if="!isAuthenticated"
        data-testid="progress-load-state"
        mode="permission"
        :title="copy.signInTitle"
        :message="copy.signInDetail"
      >
        <RouterLink class="user-action user-action--primary" :to="loginTarget">{{ copy.signInContinue }}</RouterLink>
      </UserState>
      <UserState
        v-else-if="initialLoading"
        data-testid="progress-load-state"
        mode="loading"
        :title="copy.loadingTitle"
        :message="copy.loadingDetail"
      />
      <UserState
        v-else-if="displayedLoadError && !progress"
        data-testid="progress-load-state"
        :mode="displayedLoadError.kind === 'permission' ? 'permission' : 'error'"
        :title="displayedLoadError.title"
        :message="displayedLoadError.message"
        :retry-label="displayedLoadError.retryable ? copy.reload : undefined"
        @retry="load"
      >
          <RouterLink v-if="displayedLoadError.kind === 'permission'" class="user-action user-action--primary" :to="loginTarget">{{ copy.signIn }}</RouterLink>
      </UserState>

      <template v-else-if="progress">
        <p v-if="refreshing" class="user-list__meta" data-testid="progress-refreshing" role="status">{{ copy.refreshing }}</p>
        <section class="user-panel">
          <h2>{{ copy.recordedActivity }}</h2>
          <p>{{ copy.recordedActivityCount(progress.totalActivities) }}</p>
          <div class="user-page__actions">
            <button class="user-action user-action--primary" data-testid="progress-complete-all" type="button" :disabled="saving || loading" @click="completeReview()">{{ saving && savingChapterId === null ? copy.saving : copy.completeAll }}</button>
            <span v-if="reviewSaved" class="user-list__meta" role="status">{{ copy.reviewRecorded }}</span>
          </div>
        </section>

        <UserState
          v-if="displayedSaveError"
          data-testid="progress-save-state"
          :mode="displayedSaveError.kind === 'permission' ? 'permission' : 'error'"
          :title="displayedSaveError.title"
          :message="displayedSaveError.message"
          :retry-label="displayedSaveError.retryable ? copy.retryReview : undefined"
          @retry="retrySave"
        >
          <RouterLink v-if="displayedSaveError.kind === 'permission'" class="user-action user-action--primary" :to="loginTarget">{{ copy.signIn }}</RouterLink>
        </UserState>

        <UserState
          v-if="!progress.chapters.length"
          data-testid="progress-empty-state"
          mode="empty"
          :title="copy.emptyTitle"
          :message="copy.emptyDetail"
        />
        <div v-else class="user-list" data-testid="progress-list">
          <article v-for="chapter in progress.chapters" :key="chapter.chapterId" class="user-list__row">
            <div>
              <h3>{{ isEnglish ? `${copy.chapter} ${chapter.chapterNumber} ${chapter.title}` : `${copy.chapter} ${chapter.chapterNumber} 章 ${chapter.title}` }}</h3>
              <p>{{ copy.chat }} {{ chapter.chatCount }} · {{ copy.classroom }} {{ chapter.classroomCount }} · {{ copy.algorithm }} {{ chapter.animationCount }} · {{ copy.code }} {{ chapter.codeRunCount }} · {{ copy.other }} {{ chapter.eventCount }}</p>
              <p v-if="chapter.lastActivityAt" class="user-list__meta user-list__meta--left">{{ copy.latestActivity }}: {{ formatActivityTime(chapter.lastActivityAt) }}</p>
            </div>
            <div class="user-page__actions">
              <RouterLink class="user-action" :to="`/user/chapters/${chapter.chapterId}`">{{ copy.openCourse }}</RouterLink>
              <button class="user-action" :data-testid="`progress-complete-${chapter.chapterId}`" type="button" :disabled="saving || loading" @click="completeReview(chapter.chapterId)">{{ saving && savingChapterId === chapter.chapterId ? copy.saving : copy.completeReview }}</button>
            </div>
          </article>
        </div>

        <p v-if="displayedLoadError" class="inline-notice inline-notice--warning" role="alert">
          {{ copy.refreshFailed }}: {{ displayedLoadError.message }}
          <button v-if="displayedLoadError.retryable" class="user-action" type="button" :disabled="loading || saving" @click="load">{{ copy.reload }}</button>
        </p>
      </template>
    </section>

    <template #rail>
      <div class="user-rail-list">
        <strong>{{ copy.recordedScope }}</strong>
        <p>{{ copy.recordedScopeDetail }}</p>
        <strong>{{ copy.currentView }}</strong>
        <p>{{ copy.currentViewDetail }}</p>
      </div>
    </template>
  </UserFrame>
</template>
