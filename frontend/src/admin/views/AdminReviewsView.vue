<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import AdminPageFrame from "../components/AdminPageFrame.vue";
import AdminTableControls from "../components/AdminTableControls.vue";
import { adminApi, adminErrorMessage, formatDate } from "../api";
import type { ReviewDetail, ReviewHistoryEvent, ReviewItem, ReviewStatus } from "../../shared/types";
import LoadingState from "../../shared/components/LoadingState.vue";
import ErrorState from "../../shared/components/ErrorState.vue";
import EmptyState from "../../shared/components/EmptyState.vue";
import RetryButton from "../../shared/components/RetryButton.vue";
import StatusBadge from "../../shared/components/StatusBadge.vue";
import InlineNotice from "../../shared/components/InlineNotice.vue";
import DirectionalArrowIcon from "../../shared/components/DirectionalArrowIcon.vue";
import RuntimeSelect, { type RuntimeSelectOption } from "../../shared/components/RuntimeSelect.vue";

const page = ref(0); const size = 20; const total = ref(0); const items = ref<ReviewItem[]>([]); const loading = ref(true); const error = ref(""); const notice = ref("");
const filters = reactive({ search: "", status: "", type: "" }); const selected = ref<ReviewDetail | null>(null); const history = ref<ReviewHistoryEvent[]>([]); const detailLoading = ref(false); const actionBusy = ref(false); const nextStatus = ref<ReviewStatus>("DRAFT"); const note = ref("");
const statuses: ReviewStatus[] = ["LEGACY_UNVERIFIED", "DRAFT", "PUBLISHED", "VERIFIED", "EXCLUDED"];
const reviewTypeOptions: RuntimeSelectOption[] = [
  { value: "", label: "全部类型" },
  { value: "RESOURCE", label: "课程资料" },
  { value: "KNOWLEDGE_CHUNK", label: "知识片段" },
  { value: "PRESENTATION_MANIFEST", label: "课件目录" },
  { value: "PRESENTATION_PAGE", label: "课件页面" },
  { value: "DSVP_REQUEST_SNAPSHOT", label: "算法请求快照" },
];
const reviewStatusOptions = computed<RuntimeSelectOption[]>(() => [{ value: "", label: "全部状态" }, ...statuses.map((status) => ({ value: status, label: status }))]);
const reviewNextStatusOptions = computed<RuntimeSelectOption[]>(() => statuses.map((status) => ({ value: status, label: status })));
const tableFilter = ref("");
const sortKey = ref("updatedAt");
const sortDirection = ref<"asc" | "desc">("desc");
const visibleColumns = ref(["item", "status", "source", "updatedAt", "actions"]);
const selectedIds = ref<Set<string>>(new Set());
const tableColumns = [
  { key: "item", label: "项目" },
  { key: "status", label: "状态" },
  { key: "source", label: "来源链" },
  { key: "updatedAt", label: "更新时间" },
  { key: "actions", label: "操作" },
];
let listRequest = 0;
let detailRequest = 0;

const displayItems = computed(() => {
  const query = tableFilter.value.trim().toLowerCase();
  const filtered = query
    ? items.value.filter((item) => `${item.title} ${item.type} ${item.id} ${item.status} ${item.chapterId || ""}`.toLowerCase().includes(query))
    : [...items.value];
  const direction = sortDirection.value === "asc" ? 1 : -1;
  return filtered.sort((left, right) => {
    const leftValue = sortKey.value === "status" ? left.status : sortKey.value === "source" ? (left.sourceComplete ? 1 : 0) : sortKey.value === "item" ? `${left.title} ${left.id}` : left.updatedAt;
    const rightValue = sortKey.value === "status" ? right.status : sortKey.value === "source" ? (right.sourceComplete ? 1 : 0) : sortKey.value === "item" ? `${right.title} ${right.id}` : right.updatedAt;
    return String(leftValue).localeCompare(String(rightValue), "zh-CN", { numeric: true }) * direction;
  });
});
const allSelected = computed(() => displayItems.value.length > 0 && displayItems.value.every((item) => selectedIds.value.has(reviewKey(item))));
function isColumnVisible(key: string) { return visibleColumns.value.includes(key); }
function setSort(key: string) {
  if (sortKey.value === key) sortDirection.value = sortDirection.value === "asc" ? "desc" : "asc";
  else { sortKey.value = key; sortDirection.value = "asc"; }
}
function toggleRow(item: ReviewItem) {
  const key = reviewKey(item);
  const next = new Set(selectedIds.value);
  if (next.has(key)) next.delete(key); else next.add(key);
  selectedIds.value = next;
}
function toggleAllRows() {
  const next = new Set(selectedIds.value);
  if (allSelected.value) displayItems.value.forEach((item) => next.delete(reviewKey(item)));
  else displayItems.value.forEach((item) => next.add(reviewKey(item)));
  selectedIds.value = next;
}
function clearRows() { selectedIds.value = new Set(); }

function noticeTone(value: string): "success" | "danger" { return value.includes("未完成") || value.includes("失败") ? "danger" : "success"; }

function reviewKey(item: Pick<ReviewItem, "type" | "id">) { return `${item.type}:${item.id}`; }

function closeDetail() {
  detailRequest += 1;
  selected.value = null;
  history.value = [];
  detailLoading.value = false;
  note.value = "";
}

async function load({ clearCurrentSelection = false } = {}): Promise<boolean> {
  const request = ++listRequest;
  if (clearCurrentSelection) closeDetail();
  loading.value = true;
  error.value = "";
  try {
    const result = await adminApi.reviews({ page: page.value, size, search: filters.search, status: filters.status, type: filters.type });
    if (request !== listRequest) return false;
    items.value = result.items;
    total.value = result.total;
    const available = new Set(result.items.map((item) => reviewKey(item)));
    selectedIds.value = new Set([...selectedIds.value].filter((key) => available.has(key)));
    return true;
  } catch (failure) {
    if (request === listRequest) error.value = adminErrorMessage(failure, "读取审核队列");
    return false;
  } finally {
    if (request === listRequest) loading.value = false;
  }
}

function refresh() { notice.value = ""; void load({ clearCurrentSelection: true }); }
function applyFilters() { page.value = 0; notice.value = ""; void load({ clearCurrentSelection: true }); }

async function openDetail(item: ReviewItem) {
  if (detailLoading.value || actionBusy.value) return;
  const request = ++detailRequest;
  detailLoading.value = true;
  selected.value = { item, sourceChain: [] };
  history.value = [];
  notice.value = "";
  try {
    const detail = await adminApi.review(item.type, item.id);
    if (request !== detailRequest) return;
    selected.value = detail;
    nextStatus.value = detail.item.status;
    note.value = "";
    try {
      const events = await adminApi.reviewHistory(item.type, item.id);
      if (request === detailRequest) history.value = events;
    } catch (failure) {
      if (request === detailRequest) notice.value = adminErrorMessage(failure, "读取审核历史");
    }
  } catch (failure) {
    if (request === detailRequest) {
      selected.value = null;
      notice.value = adminErrorMessage(failure, "读取审核详情");
    }
  } finally {
    if (request === detailRequest) detailLoading.value = false;
  }
}

async function updateStatus() {
  if (!selected.value || actionBusy.value || detailLoading.value) return;
  const item = selected.value.item;
  if (item.status === nextStatus.value) {
    notice.value = "目标状态与当前状态相同，未提交变更。";
    return;
  }
  if (!window.confirm(`确认将 ${item.title} 变更为 ${nextStatus.value}？来源链完整性和发布规则仍由服务端最终校验。`)) return;
  const key = reviewKey(item);
  actionBusy.value = true;
  notice.value = "";
  try {
    const updated = await adminApi.updateReviewStatus(item.type, item.id, { status: nextStatus.value, note: note.value || null });
    items.value = items.value.map((entry) => entry.id === updated.id && entry.type === updated.type ? updated : entry);
    if (selected.value && reviewKey(selected.value.item) === key) selected.value.item = updated;
    const refreshed = await load();
    try {
      const events = await adminApi.reviewHistory(updated.type, updated.id);
      if (selected.value && reviewKey(selected.value.item) === key) history.value = events;
      notice.value = refreshed ? "审核状态已更新。" : "审核状态已更新，但审核队列刷新失败，请手动刷新确认。";
    } catch (failure) {
      notice.value = refreshed
        ? `审核状态已更新，但${adminErrorMessage(failure, "读取审核历史")}`
        : `审核状态已更新，但审核队列刷新失败；${adminErrorMessage(failure, "读取审核历史")}`;
    }
    if (!refreshed) error.value = "";
  } catch (failure) {
    notice.value = adminErrorMessage(failure, "更新审核状态");
  } finally {
    actionBusy.value = false;
  }
}
function tone(status: ReviewStatus): "success" | "danger" | "warning" | "neutral" { return status === "VERIFIED" || status === "PUBLISHED" ? "success" : status === "EXCLUDED" ? "danger" : status === "DRAFT" ? "warning" : "neutral"; }
onMounted(load);
</script>

<template>
  <AdminPageFrame
    title="证据与资源审核"
    description="审核队列只展示后端返回的真实项目、来源链和历史。VERIFIED 的完整性规则由服务端决定。"
  >
    <template #actions>
      <span class="admin-header-readout admin-code">共 {{ total }} 项</span>
      <button class="button button--small admin-command" type="button" :disabled="loading || actionBusy" @click="refresh"><span class="admin-command__dot" aria-hidden="true"></span>刷新</button>
    </template>

    <form class="admin-command-row admin-toolbar" @submit.prevent="applyFilters">
      <div class="admin-command-row__label"><span class="admin-kicker">审核筛选</span><strong>审核队列</strong></div>
      <label class="admin-field admin-field--wide"><span>搜索</span><input v-model="filters.search" maxlength="160" placeholder="标题或标识" /></label>
      <label class="admin-field"><span>类型</span><RuntimeSelect v-model="filters.type" :options="reviewTypeOptions" ariaLabel="类型" /></label>
      <label class="admin-field"><span>状态</span><RuntimeSelect v-model="filters.status" :options="reviewStatusOptions" ariaLabel="状态" /></label>
      <button class="button button--primary admin-command-row__submit" type="submit" :disabled="loading || actionBusy">筛选<span aria-hidden="true">↗</span></button>
    </form>

    <InlineNotice v-if="notice" :message="notice" :tone="noticeTone(notice)" />
    <LoadingState v-if="loading" label="正在读取审核队列…" />
    <ErrorState v-else-if="error" title="审核队列读取失败" :message="error"><RetryButton @retry="load" /></ErrorState>
    <EmptyState v-else-if="!items.length" title="审核队列为空" message="当前筛选条件没有待处理项目。" />

    <template v-else>
      <div class="signal-strip admin-motion-enter" aria-label="审核队列摘要">
        <div class="signal-strip__item"><span class="signal-strip__label">项目总数</span><strong>{{ total }}</strong></div>
        <div class="signal-strip__item"><span class="signal-strip__label">当前页</span><strong>{{ items.length }} 个项目</strong></div>
        <div class="signal-strip__item"><span class="signal-strip__label">当前选择</span><strong>{{ selected ? selected.item.status : "未选择" }}</strong></div>
        <div class="signal-strip__item"><span class="signal-strip__label">来源链</span><strong>服务端核验</strong></div>
      </div>

      <div :class="{ 'data-rail': selected }">
        <section class="admin-data-surface admin-table-wrap admin-motion-enter" aria-label="审核队列">
          <header class="admin-data-surface__head"><div><p class="admin-kicker">审核清单</p><h2>审核队列</h2></div><span class="admin-data-surface__hint">来源链完整性由服务端最终校验</span></header>
          <AdminTableControls
            v-model="tableFilter"
            v-model:sort-key="sortKey"
            v-model:sort-direction="sortDirection"
            v-model:visible-columns="visibleColumns"
            :columns="tableColumns"
            :selected-count="selectedIds.size"
            :total-count="displayItems.length"
            :all-selected="allSelected"
            @toggle-all="toggleAllRows"
            @clear-selection="clearRows"
          />
          <table class="admin-table admin-data-table">
            <thead><tr><th v-if="isColumnVisible('item')"><button class="admin-table__sort" type="button" data-sort-key="item" @click="setSort('item')">项目 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('status')"><button class="admin-table__sort" type="button" data-sort-key="status" @click="setSort('status')">状态 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('source')"><button class="admin-table__sort" type="button" data-sort-key="source" @click="setSort('source')">来源链 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('updatedAt')"><button class="admin-table__sort" type="button" data-sort-key="updatedAt" @click="setSort('updatedAt')">更新时间 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('actions')">操作</th><th class="admin-table__selection-heading" aria-label="选择" /></tr></thead>
            <tbody>
              <tr v-if="!displayItems.length" class="admin-table__empty-row"><td :colspan="visibleColumns.length + 1">当前页没有符合表格筛选的审核项目。</td></tr>
              <tr v-for="item in displayItems" :key="`${item.type}-${item.id}`" class="admin-data-row" :data-selected="selected && selected.item.id === item.id && selected.item.type === item.type">
                <td v-if="isColumnVisible('item')"><div class="admin-record"><span class="admin-record__index admin-code">{{ item.id }}</span><div><strong>{{ item.title }}</strong><div class="admin-muted admin-code">{{ item.type }}</div></div></div></td>
                <td v-if="isColumnVisible('status')"><StatusBadge :label="item.status" :tone="tone(item.status)" /></td>
                <td v-if="isColumnVisible('source')"><StatusBadge :label="item.sourceComplete ? '完整' : '不完整'" :tone="item.sourceComplete ? 'success' : 'warning'" /></td>
                <td v-if="isColumnVisible('updatedAt')" class="admin-code admin-nowrap">{{ formatDate(item.updatedAt) }}</td>
                <td v-if="isColumnVisible('actions')"><details class="admin-row-actions"><summary aria-label="行操作">⋯</summary><div class="admin-table__actions"><button class="button button--small admin-action-button" type="button" data-action="open-review" :disabled="detailLoading || actionBusy" @click="openDetail(item)">查看详情<DirectionalArrowIcon direction="right" /></button></div></details></td>
                <td class="admin-table__selection-cell"><input type="checkbox" :checked="selectedIds.has(reviewKey(item))" :aria-label="`选择审核项目 ${item.title}`" @change="toggleRow(item)" /></td>
              </tr>
            </tbody>
          </table>
        </section>

        <section v-if="selected" class="admin-inspector admin-detail admin-motion-enter" aria-label="审核详情">
          <div class="admin-detail__header admin-inspector__header"><div><p class="admin-kicker">当前项目</p><h2>{{ selected.item.title }}</h2><p class="admin-code">{{ selected.item.type }} · {{ selected.item.id }}</p></div><button class="button button--small" type="button" @click="closeDetail">关闭</button></div>
          <div v-if="detailLoading" data-state="review-detail-loading"><LoadingState label="正在读取来源链…" /></div>
          <template v-else>
            <section class="admin-inspector__summary"><div><span class="admin-kicker">当前状态</span><StatusBadge :label="selected.item.status" :tone="tone(selected.item.status)" /></div><div><span class="admin-kicker">章节</span><strong>{{ selected.item.chapterId || "未关联" }}</strong></div><div><span class="admin-kicker">来源链</span><strong>{{ selected.item.sourceComplete ? "完整" : "不完整" }}</strong></div></section>
            <section class="admin-inspector__section"><div class="admin-section-head admin-section-head--compact"><h3>来源链</h3><span class="admin-code">{{ selected.sourceChain.length }} 个节点</span></div><ul class="admin-list admin-timeline"><li v-for="source in selected.sourceChain" :key="`${source.type}-${source.id}`"><span class="admin-timeline__node" aria-hidden="true"></span><div><strong>{{ source.title }}</strong><div class="admin-muted admin-code">{{ source.type }} · {{ source.id }} · {{ source.status }}</div></div></li><li v-if="!selected.sourceChain.length" class="admin-muted">服务未返回来源链。</li></ul></section>
            <section class="admin-inspector__section"><div class="admin-section-head admin-section-head--compact"><h3>状态变更</h3><span class="admin-code">受保护写入</span></div><div class="admin-form__grid"><label class="admin-field"><span>目标状态</span><RuntimeSelect v-model="nextStatus" :options="reviewNextStatusOptions" ariaLabel="目标状态" field="next-status" :disabled="actionBusy" /></label><label class="admin-field"><span>审核备注</span><input v-model="note" maxlength="500" placeholder="可选，说明审核依据" :disabled="actionBusy" /></label></div><button class="button button--primary admin-inspector__submit" type="button" data-action="update-review" :disabled="actionBusy" @click="updateStatus">{{ actionBusy ? "提交中…" : "提交状态变更" }}<DirectionalArrowIcon direction="right" /></button></section>
            <section class="admin-inspector__section"><div class="admin-section-head admin-section-head--compact"><h3>审核历史</h3><span class="admin-code">历史记录</span></div><ul class="admin-list admin-timeline"><li v-for="event in history" :key="event.id"><span class="admin-timeline__node" aria-hidden="true"></span><div><strong><span>{{ event.previousStatus }}</span><DirectionalArrowIcon direction="right" /><span>{{ event.nextStatus }}</span></strong><div class="admin-muted">{{ formatDate(event.createdAt) }} · {{ event.note || "无备注" }}</div><div class="admin-muted admin-code">requestId {{ event.requestId }}</div></div></li><li v-if="!history.length" class="admin-muted">暂无审核历史。</li></ul></section>
          </template>
        </section>
      </div>

      <div class="admin-pagination admin-pagination--rail"><span class="admin-code">第 {{ page + 1 }} 页 · 共 {{ total }} 项</span><div class="admin-pagination__actions"><button class="button button--small" type="button" :disabled="page === 0 || loading || actionBusy" @click="page--; notice = ''; load({ clearCurrentSelection: true })"><DirectionalArrowIcon direction="left" />上一页</button><button class="button button--small" type="button" :disabled="(page + 1) * size >= total || loading || actionBusy" @click="page++; notice = ''; load({ clearCurrentSelection: true })">下一页<DirectionalArrowIcon direction="right" /></button></div></div>
    </template>
  </AdminPageFrame>
</template>
