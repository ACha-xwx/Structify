<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import AdminPageFrame from "../components/AdminPageFrame.vue";
import AdminTableControls from "../components/AdminTableControls.vue";
import { adminApi, adminErrorMessage, formatDate } from "../api";
import type { BackgroundTask, BackgroundTaskStatus } from "../../shared/types";
import LoadingState from "../../shared/components/LoadingState.vue";
import ErrorState from "../../shared/components/ErrorState.vue";
import EmptyState from "../../shared/components/EmptyState.vue";
import RetryButton from "../../shared/components/RetryButton.vue";
import StatusBadge from "../../shared/components/StatusBadge.vue";
import InlineNotice from "../../shared/components/InlineNotice.vue";
import DirectionalArrowIcon from "../../shared/components/DirectionalArrowIcon.vue";
import RuntimeSelect, { type RuntimeSelectOption } from "../../shared/components/RuntimeSelect.vue";

const page = ref(0); const size = 20; const total = ref(0); const items = ref<BackgroundTask[]>([]); const loading = ref(true); const error = ref(""); const notice = ref(""); const actionBusy = ref<number | null>(null); const selected = ref<BackgroundTask | null>(null); const detailBusy = ref(false); const detailError = ref("");
const filters = reactive<{ status: string; taskType: string }>({ status: "", taskType: "" });
const statuses: BackgroundTaskStatus[] = ["PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELED"];
const taskStatusOptions = computed<RuntimeSelectOption[]>(() => [{ value: "", label: "全部状态" }, ...statuses.map((status) => ({ value: status, label: status }))]);
const taskTypeOptions: RuntimeSelectOption[] = [{ value: "", label: "全部类型" }, { value: "STALE_TASK_RECOVERY", label: "过期任务恢复" }];
const tableFilter = ref("");
const sortKey = ref("createdAt");
const sortDirection = ref<"asc" | "desc">("desc");
const visibleColumns = ref(["task", "status", "lifecycle", "result", "actions"]);
const selectedIds = ref<Set<number>>(new Set());
const tableColumns = [
  { key: "task", label: "任务" },
  { key: "status", label: "状态" },
  { key: "lifecycle", label: "生命周期" },
  { key: "result", label: "结果" },
  { key: "actions", label: "操作" },
];
let listRequest = 0;
let detailRequest = 0;

const displayItems = computed(() => {
  const query = tableFilter.value.trim().toLowerCase();
  const filtered = query
    ? items.value.filter((task) => `${task.id} ${task.taskType} ${task.status} ${task.requestId} ${task.failureCode || ""}`.toLowerCase().includes(query))
    : [...items.value];
  const direction = sortDirection.value === "asc" ? 1 : -1;
  return filtered.sort((left, right) => {
    const leftValue = sortKey.value === "status" ? left.status : sortKey.value === "task" ? `${left.taskType} ${left.id}` : sortKey.value === "result" ? (left.resultCount ?? -1) : left.createdAt;
    const rightValue = sortKey.value === "status" ? right.status : sortKey.value === "task" ? `${right.taskType} ${right.id}` : sortKey.value === "result" ? (right.resultCount ?? -1) : right.createdAt;
    return String(leftValue).localeCompare(String(rightValue), "zh-CN", { numeric: true }) * direction;
  });
});
const allSelected = computed(() => displayItems.value.length > 0 && displayItems.value.every((task) => selectedIds.value.has(task.id)));
function isColumnVisible(key: string) { return visibleColumns.value.includes(key); }
function setSort(key: string) {
  if (sortKey.value === key) sortDirection.value = sortDirection.value === "asc" ? "desc" : "asc";
  else { sortKey.value = key; sortDirection.value = "asc"; }
}
function toggleRow(id: number) {
  const next = new Set(selectedIds.value);
  if (next.has(id)) next.delete(id); else next.add(id);
  selectedIds.value = next;
}
function toggleAllRows() {
  const next = new Set(selectedIds.value);
  if (allSelected.value) displayItems.value.forEach((task) => next.delete(task.id));
  else displayItems.value.forEach((task) => next.add(task.id));
  selectedIds.value = next;
}
function clearRows() { selectedIds.value = new Set(); }

function noticeTone(value: string): "success" | "danger" { return value.includes("未完成") || value.includes("失败") ? "danger" : "success"; }

function closeDetail() {
  detailRequest += 1;
  selected.value = null;
  detailBusy.value = false;
  detailError.value = "";
}

async function load({ clearCurrentSelection = false } = {}): Promise<boolean> {
  const request = ++listRequest;
  if (clearCurrentSelection) closeDetail();
  loading.value = true;
  error.value = "";
  try {
    const result = await adminApi.tasks({ page: page.value, size, status: filters.status, taskType: filters.taskType });
    if (request !== listRequest) return false;
    items.value = result.items;
    total.value = result.total;
    const available = new Set(result.items.map((task) => task.id));
    selectedIds.value = new Set([...selectedIds.value].filter((id) => available.has(id)));
    return true;
  } catch (failure) {
    if (request === listRequest) error.value = adminErrorMessage(failure, "读取后台任务");
    return false;
  } finally {
    if (request === listRequest) loading.value = false;
  }
}

function refresh() { notice.value = ""; void load({ clearCurrentSelection: true }); }
function applyFilters() { page.value = 0; notice.value = ""; void load({ clearCurrentSelection: true }); }
function tone(status: BackgroundTaskStatus): "success" | "danger" | "warning" | "neutral" { return status === "SUCCEEDED" ? "success" : status === "FAILED" || status === "CANCELED" ? "danger" : status === "RUNNING" ? "warning" : "neutral"; }

async function openTask(task: BackgroundTask) {
  if (detailBusy.value || actionBusy.value !== null) return;
  const request = ++detailRequest;
  selected.value = task;
  detailBusy.value = true;
  detailError.value = "";
  try {
    const detail = await adminApi.task(task.id);
    if (request !== detailRequest) return;
    selected.value = detail;
  } catch (failure) {
    if (request === detailRequest) detailError.value = adminErrorMessage(failure, "读取任务详情");
  } finally {
    if (request === detailRequest) detailBusy.value = false;
  }
}

async function runAction(task: BackgroundTask, operation: "retry" | "cancel") {
  if (actionBusy.value !== null) return;
  if (!window.confirm(`确认${operation === "retry" ? "重试" : "取消"}任务 #${task.id}？`)) return;
  actionBusy.value = task.id;
  notice.value = "";
  try {
    const updated = operation === "retry" ? await adminApi.retryTask(task.id) : await adminApi.cancelTask(task.id);
    items.value = items.value.map((item) => item.id === updated.id ? updated : item);
    if (selected.value?.id === updated.id) selected.value = updated;
    const refreshed = await load();
    notice.value = refreshed
      ? `任务 #${task.id} 已提交${operation === "retry" ? "重试" : "取消"}。`
      : `任务 #${task.id} 已提交${operation === "retry" ? "重试" : "取消"}，但任务列表刷新失败，请手动刷新确认进度。`;
    if (!refreshed) error.value = "";
  } catch (failure) {
    notice.value = adminErrorMessage(failure, operation === "retry" ? "重试任务" : "取消任务");
  } finally {
    actionBusy.value = null;
  }
}

async function recoverTimeouts() {
  if (actionBusy.value !== null) return;
  actionBusy.value = -1;
  notice.value = "";
  try {
    const task = await adminApi.recoverTimeouts();
    const refreshed = await load();
    notice.value = refreshed
      ? `超时恢复任务 #${task.id} 已提交。`
      : `超时恢复任务 #${task.id} 已提交，但任务列表刷新失败，请手动刷新确认进度。`;
    if (!refreshed) error.value = "";
  } catch (failure) {
    notice.value = adminErrorMessage(failure, "提交超时恢复任务");
  } finally {
    actionBusy.value = null;
  }
}
onMounted(load);
</script>

<template>
  <AdminPageFrame
    title="后台任务"
    description="后台任务页只允许提交服务端定义的 STALE_TASK_RECOVERY，并展示真实生命周期和安全 requestId。"
  >
    <template #actions>
      <button class="button button--small button--primary" type="button" :disabled="actionBusy !== null" @click="recoverTimeouts">恢复超时任务</button>
      <button class="button button--small" type="button" :disabled="loading || actionBusy !== null" @click="refresh">刷新</button>
    </template>

    <form class="admin-command-row admin-toolbar" @submit.prevent="applyFilters">
      <div class="admin-command-row__label"><span class="admin-kicker">任务筛选</span><strong>后台任务</strong></div>
      <label class="admin-field"><span>状态</span><RuntimeSelect v-model="filters.status" :options="taskStatusOptions" ariaLabel="状态" /></label>
      <label class="admin-field"><span>任务类型</span><RuntimeSelect v-model="filters.taskType" :options="taskTypeOptions" ariaLabel="任务类型" /></label>
      <button class="button button--primary admin-command-row__submit" type="submit" :disabled="loading || actionBusy !== null">筛选<span aria-hidden="true">↗</span></button>
    </form>

    <InlineNotice v-if="notice" :message="notice" :tone="noticeTone(notice)" />
    <LoadingState v-if="loading" label="正在读取后台任务…" />
    <ErrorState v-else-if="error" title="后台任务读取失败" :message="error"><RetryButton @retry="load" /></ErrorState>
    <EmptyState v-else-if="!items.length" title="暂无后台任务" message="当前筛选条件没有返回任务记录。" />

    <template v-else>
      <div class="signal-strip admin-motion-enter" aria-label="后台任务摘要">
        <div class="signal-strip__item"><span class="signal-strip__label">任务总数</span><strong>{{ total }}</strong></div>
        <div class="signal-strip__item"><span class="signal-strip__label">当前页</span><strong>{{ items.length }} 条记录</strong></div>
        <div class="signal-strip__item"><span class="signal-strip__label">当前选择</span><strong>{{ selected ? selected.status : "未选择" }}</strong></div>
        <div class="signal-strip__item"><span class="signal-strip__label">恢复操作</span><strong>服务端任务</strong></div>
      </div>

      <div :class="{ 'data-rail': selected }">
        <section class="admin-data-surface admin-table-wrap admin-motion-enter" aria-label="后台任务">
          <header class="admin-data-surface__head"><div><p class="admin-kicker">执行记录</p><h2>任务列表</h2></div><span class="admin-data-surface__hint">requestId 全部来自服务端</span></header>
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
          <table class="admin-table admin-data-table admin-task-table">
            <thead><tr><th v-if="isColumnVisible('task')"><button class="admin-table__sort" type="button" data-sort-key="task" @click="setSort('task')">任务 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('status')"><button class="admin-table__sort" type="button" data-sort-key="status" @click="setSort('status')">状态 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('lifecycle')">生命周期</th><th v-if="isColumnVisible('result')"><button class="admin-table__sort" type="button" data-sort-key="result" @click="setSort('result')">结果 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('actions')">操作</th><th class="admin-table__selection-heading" aria-label="选择" /></tr></thead>
            <tbody>
              <tr v-if="!displayItems.length" class="admin-table__empty-row"><td :colspan="visibleColumns.length + 1">当前页没有符合表格筛选的任务。</td></tr>
              <tr v-for="task in displayItems" :key="task.id" class="admin-data-row" :data-selected="selected?.id === task.id">
                <td v-if="isColumnVisible('task')"><div class="admin-record"><span class="admin-record__index admin-code">#{{ task.id }}</span><div><strong>{{ task.taskType }}</strong><div class="admin-muted admin-code">requestId {{ task.requestId }}</div></div></div></td>
                <td v-if="isColumnVisible('status')"><StatusBadge :label="task.status" :tone="tone(task.status)" /><div v-if="task.failureCode" class="admin-danger admin-data-note">{{ task.failureCode }}</div></td>
                <td v-if="isColumnVisible('lifecycle')"><ul class="admin-timeline admin-timeline--compact"><li><span class="admin-timeline__node" aria-hidden="true"></span><span>创建 <time>{{ formatDate(task.createdAt) }}</time></span></li><li><span class="admin-timeline__node" aria-hidden="true"></span><span>开始 <time>{{ formatDate(task.startedAt) }}</time></span></li><li><span class="admin-timeline__node" aria-hidden="true"></span><span>截止 <time>{{ formatDate(task.deadlineAt) }}</time></span></li><li><span class="admin-timeline__node" aria-hidden="true"></span><span>心跳 <time>{{ formatDate(task.heartbeatAt) }}</time></span></li><li><span class="admin-timeline__node" aria-hidden="true"></span><span>结束 <time>{{ formatDate(task.finishedAt) }}</time></span></li></ul></td>
                <td v-if="isColumnVisible('result')"><strong>{{ task.resultCount ?? "未完成" }}</strong><div class="admin-muted">尝试 {{ task.retryCount }}/{{ task.maxAttempts }}</div></td>
                <td v-if="isColumnVisible('actions')"><details class="admin-row-actions"><summary aria-label="行操作">⋯</summary><div class="admin-table__actions admin-action-cluster"><button class="button button--small" type="button" :disabled="detailBusy || actionBusy !== null" @click="openTask(task)">详情</button><button v-if="task.status === 'FAILED' && task.taskType === 'STALE_TASK_RECOVERY'" class="button button--small" type="button" :disabled="actionBusy !== null" @click="runAction(task, 'retry')">重试</button><button v-if="task.status === 'PENDING' && task.taskType === 'STALE_TASK_RECOVERY'" class="button button--small" type="button" :disabled="actionBusy !== null" @click="runAction(task, 'cancel')">取消</button></div></details></td>
                <td class="admin-table__selection-cell"><input type="checkbox" :checked="selectedIds.has(task.id)" :aria-label="`选择任务 ${task.id}`" @change="toggleRow(task.id)" /></td>
              </tr>
            </tbody>
          </table>
        </section>

        <section v-if="selected" class="admin-inspector admin-detail admin-motion-enter" aria-label="任务详情">
          <div class="admin-detail__header admin-inspector__header"><div><p class="admin-kicker">当前任务</p><h2>任务 #{{ selected.id }}</h2><p class="admin-code">{{ selected.taskType }}</p></div><button class="button button--small" type="button" @click="closeDetail">关闭</button></div>
          <LoadingState v-if="detailBusy" label="正在读取任务详情…" />
          <ErrorState v-else-if="detailError" title="任务详情读取失败" :message="detailError"><RetryButton @retry="openTask(selected)" /></ErrorState>
          <template v-else>
            <section class="admin-inspector__summary"><div><span class="admin-kicker">状态</span><StatusBadge :label="selected.status" :tone="tone(selected.status)" /></div><div><span class="admin-kicker">失败原因</span><strong>{{ selected.failureReason || "未记录" }}</strong></div></section>
            <dl><dt>状态</dt><dd>{{ selected.status }}</dd><dt>失败原因</dt><dd>{{ selected.failureReason || "未记录" }}</dd><dt>请求 ID</dt><dd class="admin-code">{{ selected.requestId }}</dd><dt>请求人</dt><dd>{{ selected.requestedByUserId ?? "未记录" }}</dd></dl>
          </template>
        </section>
      </div>

      <div class="admin-pagination admin-pagination--rail"><span class="admin-code">第 {{ page + 1 }} 页 · {{ total }} 条记录</span><div class="admin-pagination__actions"><button class="button button--small" type="button" :disabled="page === 0 || loading || actionBusy !== null" @click="page--; notice = ''; load({ clearCurrentSelection: true })"><DirectionalArrowIcon direction="left" />上一页</button><button class="button button--small" type="button" :disabled="(page + 1) * size >= total || loading || actionBusy !== null" @click="page++; notice = ''; load({ clearCurrentSelection: true })">下一页<DirectionalArrowIcon direction="right" /></button></div></div>
    </template>
  </AdminPageFrame>
</template>
