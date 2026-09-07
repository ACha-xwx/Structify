<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { auth } from "../../app/providers/runtime";
import { useLocale } from "../../shared/i18n/locale";
import type { Resource } from "../../shared/types/contracts";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import { userApi } from "../runtime";
import { getResourcePreview, type ResourcePreview } from "../fixtures/resource-preview";

const route = useRoute();
const { isEnglish } = useLocale();
const resourceId = computed(() => String(route.params.resourceId || ""));
const resource = ref<Resource | null>(null);
const contentUrl = ref<string | null>(null);
const contentType = ref<string | null>(null);
const disposition = ref<string | null>(null);
const preview = ref<ResourcePreview | null>(null);
const loading = ref(true);
const saving = ref(false);
const error = ref<UserErrorPresentation | null>(null);
type ResourceEventKey = "view-recorded" | "view-record-not-saved" | "fixture-download-started" | "guest-download-started" | "download-recorded" | "download-record-not-saved";
const eventMessageKey = ref<ResourceEventKey | null>(null);
const localPreviewUnavailable = ref(false);
const isFixture = computed(() => preview.value?.mode === "fixture");
let loadVersion = 0;

const copy = computed(() => isEnglish.value ? {
  eyebrow: "Course materials",
  fallbackTitle: "Resource details",
  fixtureIntro: "This example material is available for guest browsing and does not represent published access or a personal learning record.",
  authenticatedIntro: "This resource is available according to its publication status and the current account's access.",
  fixtureBadge: "Example preview",
  returnChapter: "Back to chapter",
  loadingTitle: "Loading course material",
  loadingDetail: "Checking access and reading the resource.",
  reload: "Reload",
  resourceType: "Resource type",
  source: "Source",
  version: "Version",
  publication: "Publication status",
  accessScope: "Access scope",
  contentType: "Content type",
  notProvided: "Not provided",
  previewBoundary: "Preview boundary",
  download: "Download material",
  downloadPreview: "Download example material",
  saving: "Saving...",
  iframeTitle: (title: string) => `${title} preview`,
  presentationUnavailableTitle: "Courseware is ready, but playback is unavailable in the browser",
  presentationUnavailableDetail: "Download the courseware to open it locally. This page does not imitate a playback state.",
  previewUnavailableTitle: "This file cannot be previewed in the browser",
  previewUnavailableDetail: "The content has been read safely. Use download to open it locally.",
  previewSource: "Preview source",
  materialStatus: "Material status",
  fixtureRail: "Guest previews do not save a personal learning record.",
  authenticatedRail: "Course material visible to the current account has been loaded.",
  authenticatedDownloadRail: "Downloading can continue to save this learning activity.",
  privacy: "Privacy",
  privacyDetail: "File locations and credentials are not shown on this page.",
  fixtureBoundaryDetail: "Guest browsing only. It does not represent published access, account permissions, or a personal learning record.",
  exampleHandout: "Example handout image",
  pdfDocument: "PDF document",
  courseware: "Courseware",
  examplePreview: "Example preview",
  exampleContent: "Example content",
  published: "Published",
  publicMaterial: "Public material",
  courseMaterial: "Course material",
  classroomMaterial: "Classroom material",
  localPreviewUnavailable: { title: "Example material is unavailable", message: "This item is not included in the local guest preview. Return to the course chapter and choose another material." },
  eventMessages: {
    "view-recorded": "This material visit has been saved.",
    "view-record-not-saved": "The material opened, but the visit was not saved.",
    "fixture-download-started": "The example download has started. It will not save a learning record.",
    "guest-download-started": "The download has started. Sign in to save learning activity.",
    "download-recorded": "The download has started and the learning record was saved.",
    "download-record-not-saved": "The download has started, but the download record was not saved.",
  },
  errors: {
    permission: { title: "Sign in to view this material", message: "Sign in to access this course material." },
    "not-found": { title: "Resource is unavailable", message: "This resource may be unpublished, removed, or outside this account's access." },
    conflict: { title: "This page has changed", message: "Refresh and try again to use the latest learning state." },
    limited: { title: "Please wait before trying again", message: "The learning service is temporarily limiting requests." },
    timeout: { title: "The request timed out", message: "The learning service did not respond in time." },
    service: { title: "Learning services are temporarily unavailable", message: "This page remains available. Try again when the service recovers." },
    network: { title: "Network connection is unavailable", message: "Check the connection and try again. Your current page remains available." },
    validation: { title: "This request cannot be processed", message: "Check the resource request and try again." },
    unknown: { title: "This action was not completed", message: "The learning service returned an unexpected result. Try again." },
  },
} : {
  eyebrow: "课程资料",
  fallbackTitle: "资源详情",
  fixtureIntro: "仅用于游客浏览，不代表已发布课程资料、账号权限或个人学习记录。",
  authenticatedIntro: "读取受发布状态和账号范围控制的资源。",
  fixtureBadge: "示例预览",
  returnChapter: "返回章节",
  loadingTitle: "正在加载课程资料",
  loadingDetail: "正在确认资源权限并读取内容。",
  reload: "重新加载",
  resourceType: "资源类型",
  source: "来源",
  version: "版本",
  publication: "发布状态",
  accessScope: "访问范围",
  contentType: "内容类型",
  notProvided: "未提供",
  previewBoundary: "预览边界",
  download: "下载资料",
  downloadPreview: "下载预览资料",
  saving: "正在记录…",
  iframeTitle: (title: string) => `${title} 预览`,
  presentationUnavailableTitle: "课件已读取，暂不支持网页内播放",
  presentationUnavailableDetail: "可以下载课件后在本机打开，页面不会伪造播放状态。",
  previewUnavailableTitle: "此文件不支持浏览器内预览",
  previewUnavailableDetail: "内容已安全读取，可以使用下载按钮在本机打开。",
  previewSource: "预览来源",
  materialStatus: "资料状态",
  fixtureRail: "游客预览不会写入个人学习记录。",
  authenticatedRail: "已读取当前账户可见的课程资料。",
  authenticatedDownloadRail: "下载后可继续保存资料学习活动。",
  privacy: "隐私说明",
  privacyDetail: "文件位置和凭据不会显示在页面上。",
  fixtureBoundaryDetail: "仅用于游客浏览，不代表已发布课程资料、账号权限或个人学习记录。",
  exampleHandout: "示例讲义图",
  pdfDocument: "PDF 资料",
  courseware: "课程课件",
  examplePreview: "示例预览",
  exampleContent: "示例内容",
  published: "已发布",
  publicMaterial: "公开资料",
  courseMaterial: "课程资料",
  classroomMaterial: "课堂资料",
  localPreviewUnavailable: { title: "本地预览资料不存在", message: "游客可浏览的本地资料预览中没有这一项。请从课程章节重新选择。" },
  eventMessages: {
    "view-recorded": "已记录本次资料访问",
    "view-record-not-saved": "资料已打开，但访问记录未保存",
    "fixture-download-started": "本地预览资料已开始下载，不会保存学习记录",
    "guest-download-started": "下载已开始；登录后可保存资料学习记录",
    "download-recorded": "下载已开始，学习记录已保存",
    "download-record-not-saved": "下载已开始，但下载记录未保存",
  },
  errors: {
    permission: { title: "登录后解锁此功能", message: "登录后可访问此课程资料。" },
    "not-found": { title: "资源不可访问", message: "该资源可能未发布、已移除，或当前账号没有访问范围。" },
    conflict: { title: "当前状态已变化", message: "请刷新后继续操作，避免覆盖最新学习状态。" },
    limited: { title: "请求过于频繁", message: "学习服务暂时限制了请求，请稍后重试。" },
    timeout: { title: "请求超时", message: "学习服务未在规定时间内响应。" },
    service: { title: "学习服务暂不可用", message: "当前页面位置已保留，服务恢复后可再次尝试。" },
    network: { title: "网络连接不可用", message: "请检查网络后重试，当前学习位置不会丢失。" },
    validation: { title: "提交内容无法处理", message: "请检查资源请求后重试。" },
    unknown: { title: "操作未完成", message: "服务返回了未预期的结果，请重试。" },
  },
});

const eventMessage = computed(() => {
  const key = eventMessageKey.value;
  return key ? copy.value.eventMessages[key] : "";
});
const displayedError = computed(() => {
  if (!error.value) return null;
  if (localPreviewUnavailable.value) return { ...error.value, ...copy.value.localPreviewUnavailable };
  return localizedUserError(error.value);
});

const isPreviewable = computed(() => Boolean(contentUrl.value && (
  contentType.value?.includes("pdf") || contentType.value?.startsWith("image/")
)));
const isPresentationFile = computed(() => /powerpoint|presentation|ms-powerpoint|\.pptx?$/i.test(`${contentType.value || ""} ${resource.value?.type || ""} ${downloadName.value}`));
const downloadName = computed(() => {
  const match = disposition.value?.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i);
  const raw = match?.[1] || match?.[2];
  if (!raw) return resource.value?.title || copy.value.fallbackTitle;
  try { return decodeURIComponent(raw); } catch { return raw; }
});

function localizedUserError(value: UserErrorPresentation): UserErrorPresentation {
  return { ...value, ...copy.value.errors[value.kind] };
}

function resourceTypeLabel(value: string): string {
  if (isFixture.value) return copy.value.exampleHandout;
  if (value === "PDF") return copy.value.pdfDocument;
  if (/^(PPT|PPTX|POWERPOINT|PRESENTATION)$/i.test(value)) return copy.value.courseware;
  return value;
}

function reviewStatusLabel(value: string): string {
  if (isFixture.value) return copy.value.exampleContent;
  return value === "PUBLISHED" ? copy.value.published : value;
}

function accessScopeLabel(value: string): string {
  if (value === "PUBLIC") return copy.value.publicMaterial;
  if (value === "COURSE") return copy.value.courseMaterial;
  if (value === "CLASSROOM") return copy.value.classroomMaterial;
  return value;
}

function versionLabel(value: string): string {
  return isFixture.value && value === "LOCAL FIXTURE" ? copy.value.examplePreview : value;
}

function releaseContentUrl() {
  if (contentUrl.value) URL.revokeObjectURL(contentUrl.value);
  contentUrl.value = null;
}

function currentUserId(): number | null {
  return auth.state.user?.id ?? null;
}

function isCurrentLoad(version: number, requestedId: string, requestedUserId: number | null): boolean {
  return version === loadVersion && requestedId === resourceId.value && requestedUserId === currentUserId();
}

async function load() {
  const version = ++loadVersion;
  const requestedId = resourceId.value;
  const requestedUserId = currentUserId();
  loading.value = true;
  error.value = null;
  eventMessageKey.value = null;
  localPreviewUnavailable.value = false;
  resource.value = null;
  preview.value = null;
  contentType.value = null;
  disposition.value = null;
  releaseContentUrl();
  if (requestedUserId === null) {
    const localPreview = getResourcePreview(requestedId);
    if (!localPreview) {
      localPreviewUnavailable.value = true;
      error.value = { kind: "not-found", title: "", message: "", retryable: false };
      loading.value = false;
      return;
    }
    preview.value = localPreview;
    resource.value = localPreview.resource;
    contentType.value = localPreview.content.contentType;
    disposition.value = localPreview.content.disposition;
    contentUrl.value = URL.createObjectURL(new Blob([localPreview.content.bytes], { type: localPreview.content.contentType }));
    if (isCurrentLoad(version, requestedId, requestedUserId)) loading.value = false;
    return;
  }
  try {
    const [metadata, payload] = await Promise.all([
      userApi.getResource(resourceId.value),
      userApi.getResourceContent(resourceId.value),
    ]);
    if (!isCurrentLoad(version, requestedId, requestedUserId)) return;
    resource.value = metadata;
    contentType.value = payload.contentType;
    disposition.value = payload.disposition;
    contentUrl.value = URL.createObjectURL(new Blob([payload.bytes], { type: payload.contentType || "application/octet-stream" }));
    if (isCurrentLoad(version, requestedId, requestedUserId)) {
      userApi.recordLearningEvent({ eventType: "RESOURCE_VIEW", chapterId: metadata.chapterId, referenceId: metadata.id })
        .then(() => { if (isCurrentLoad(version, requestedId, requestedUserId)) eventMessageKey.value = "view-recorded"; })
        .catch(() => { if (isCurrentLoad(version, requestedId, requestedUserId)) eventMessageKey.value = "view-record-not-saved"; });
    }
  } catch (cause) {
    if (!isCurrentLoad(version, requestedId, requestedUserId)) return;
    resource.value = null;
    error.value = presentUserError(cause);
  } finally {
    if (isCurrentLoad(version, requestedId, requestedUserId)) loading.value = false;
  }
}

async function download() {
  if (!contentUrl.value || !resource.value) return;
  const requestedUserId = currentUserId();
  saving.value = true;
  const link = document.createElement("a");
  link.href = contentUrl.value;
  link.download = downloadName.value;
  link.rel = "noopener";
  link.style.display = "none";
  document.body.append(link);
  link.click();
  link.remove();
  if (requestedUserId === null || requestedUserId !== currentUserId()) {
    eventMessageKey.value = isFixture.value ? "fixture-download-started" : "guest-download-started";
    saving.value = false;
    return;
  }
  try {
    await userApi.recordLearningEvent({ eventType: "RESOURCE_DOWNLOAD", chapterId: resource.value.chapterId, referenceId: resource.value.id });
    if (requestedUserId === currentUserId()) eventMessageKey.value = "download-recorded";
  } catch {
    if (requestedUserId === currentUserId()) eventMessageKey.value = "download-record-not-saved";
  } finally {
    saving.value = false;
  }
}

watch(resourceId, () => { void load(); }, { immediate: true });
watch(() => auth.state.user?.id, () => { void load(); });
onBeforeUnmount(releaseContentUrl);
</script>

<template>
  <UserFrame shell="course">
    <section class="user-page" aria-labelledby="resource-title">
      <header class="user-page__heading">
        <div><p class="user-page__eyebrow">{{ copy.eyebrow }}</p><h1 id="resource-title">{{ resource?.title || copy.fallbackTitle }}</h1><p class="user-page__intro">{{ resource?.description || (isFixture ? copy.fixtureIntro : copy.authenticatedIntro) }}</p></div>
        <span v-if="isFixture" class="user-preview-chip">{{ copy.fixtureBadge }}</span>
        <RouterLink class="user-action" :to="resource ? `/user/chapters/${resource.chapterId}` : '/user/chapters'">{{ copy.returnChapter }}</RouterLink>
      </header>

      <UserState v-if="loading" mode="loading" :title="copy.loadingTitle" :message="copy.loadingDetail" />
      <UserState v-else-if="displayedError" :mode="displayedError.kind === 'permission' ? 'permission' : 'error'" :title="displayedError.title" :message="displayedError.message" :retry-label="displayedError.retryable ? copy.reload : undefined" @retry="load" />
      <template v-else-if="resource">
        <dl class="user-kv user-panel">
          <dt>{{ copy.resourceType }}</dt><dd>{{ resourceTypeLabel(resource.type) }}</dd>
          <dt>{{ copy.source }}</dt><dd>{{ resource.sourceName }}</dd>
          <dt>{{ copy.version }}</dt><dd>{{ versionLabel(resource.versionLabel) }}</dd>
          <dt>{{ copy.publication }}</dt><dd>{{ reviewStatusLabel(resource.reviewStatus) }}</dd>
          <dt>{{ copy.accessScope }}</dt><dd>{{ accessScopeLabel(resource.licenseScope) }}</dd>
          <dt>{{ copy.contentType }}</dt><dd>{{ contentType || copy.notProvided }}</dd>
          <template v-if="isFixture"><dt>{{ copy.previewBoundary }}</dt><dd>{{ copy.fixtureBoundaryDetail }}</dd></template>
        </dl>
        <div class="user-page__actions">
          <button class="user-action user-action--primary" type="button" :disabled="!contentUrl || saving" @click="download">{{ saving ? copy.saving : (isFixture ? copy.downloadPreview : copy.download) }}</button>
          <span v-if="eventMessage" class="user-list__meta" role="status">{{ eventMessage }}</span>
        </div>
        <iframe v-if="isPreviewable && contentType?.includes('pdf')" class="user-resource-frame" :src="contentUrl || undefined" :title="copy.iframeTitle(resource.title)"></iframe>
        <img v-else-if="isPreviewable" class="user-resource-image" :src="contentUrl || undefined" :alt="resource.title" />
        <UserState v-else mode="empty" :title="isPresentationFile ? copy.presentationUnavailableTitle : copy.previewUnavailableTitle" :message="isPresentationFile ? copy.presentationUnavailableDetail : copy.previewUnavailableDetail" />
      </template>
    </section>
    <template #rail><div class="user-rail-list"><strong>{{ isFixture ? copy.previewSource : copy.materialStatus }}</strong><template v-if="isFixture"><p>{{ copy.examplePreview }}</p><p>{{ copy.fixtureRail }}</p></template><template v-else><p>{{ copy.authenticatedRail }}</p><p>{{ copy.authenticatedDownloadRail }}</p></template><strong>{{ copy.privacy }}</strong><p>{{ copy.privacyDetail }}</p></div></template>
  </UserFrame>
</template>
