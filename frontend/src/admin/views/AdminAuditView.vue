<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import AdminPageFrame from "../components/AdminPageFrame.vue";
import AdminTableControls from "../components/AdminTableControls.vue";
import { adminApi, adminErrorMessage, formatDate } from "../api";
import type { AdminAuditEvent } from "../../shared/types";
import LoadingState from "../../shared/components/LoadingState.vue";
import ErrorState from "../../shared/components/ErrorState.vue";
import EmptyState from "../../shared/components/EmptyState.vue";
import RetryButton from "../../shared/components/RetryButton.vue";
import InlineNotice from "../../shared/components/InlineNotice.vue";
import DirectionalArrowIcon from "../../shared/components/DirectionalArrowIcon.vue";

const page = ref(0); const size = 50; const total = ref(0); const items = ref<AdminAuditEvent[]>([]); const loading = ref(true); const error = ref(""); const selected = ref<AdminAuditEvent | null>(null);
const filters = reactive({ actorUserId: "", action: "", targetType: "", targetId: "", from: "", to: "" });
const filterError = ref("");
const tableFilter = ref("");
const sortKey = ref("createdAt");
const sortDirection = ref<"asc" | "desc">("desc");
const visibleColumns = ref(["createdAt", "action", "result", "summary", "requestId"]);
const selectedIds = ref<Set<number>>(new Set());
const tableColumns = [
  { key: "createdAt", label: "时间" },
  { key: "action", label: "操作与目标" },
  { key: "result", label: "结果" },
  { key: "summary", label: "安全摘要" },
  { key: "requestId", label: "请求 ID" },
];
let listRequest = 0;

const displayItems = computed(() => {
  const query = tableFilter.value.trim().toLowerCase();
  const filtered = query
    ? items.value.filter((event) => `${event.action} ${event.targetType} ${event.targetId} ${event.result} ${event.requestId}`.toLowerCase().includes(query))
    : [...items.value];
  const direction = sortDirection.value === "asc" ? 1 : -1;
  return filtered.sort((left, right) => {
    const leftValue = sortKey.value === "createdAt" ? left.createdAt : sortKey.value === "result" ? left.result : sortKey.value === "requestId" ? left.requestId : `${left.action} ${left.targetType} ${left.targetId}`;
    const rightValue = sortKey.value === "createdAt" ? right.createdAt : sortKey.value === "result" ? right.result : sortKey.value === "requestId" ? right.requestId : `${right.action} ${right.targetType} ${right.targetId}`;
    return String(leftValue).localeCompare(String(rightValue), "zh-CN") * direction;
  });
});
const allSelected = computed(() => displayItems.value.length > 0 && displayItems.value.every((event) => selectedIds.value.has(event.id)));
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
  if (allSelected.value) displayItems.value.forEach((event) => next.delete(event.id));
  else displayItems.value.forEach((event) => next.add(event.id));
  selectedIds.value = next;
}
function clearRows() { selectedIds.value = new Set(); }

function toIsoDateTime(value: string, label: string, endOfMinute = false): string | undefined {
  if (!value) return undefined;
  const parsed = new Date(endOfMinute && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value) ? `${value}:59.999` : value);
  if (Number.isNaN(parsed.getTime())) throw new Error(`${label}格式无效`);
  return parsed.toISOString();
}

function buildQuery() {
  try {
    filterError.value = "";
    const actorUserId = String(filters.actorUserId).trim();
    if (actorUserId && (!/^\d+$/.test(actorUserId) || Number(actorUserId) < 1 || !Number.isSafeInteger(Number(actorUserId)))) {
      throw new Error("操作者 ID 必须是正整数");
    }
    const from = toIsoDateTime(filters.from, "开始时间");
    const to = toIsoDateTime(filters.to, "结束时间", true);
    if (from && to && new Date(from).getTime() > new Date(to).getTime()) throw new Error("开始时间不能晚于结束时间");
    return {
      page: page.value,
      size,
      actorUserId: actorUserId ? Number(actorUserId) : undefined,
      action: filters.action.trim(),
      targetType: filters.targetType.trim(),
      targetId: filters.targetId.trim(),
      from,
      to,
    };
  } catch (failure) {
    filterError.value = failure instanceof Error ? failure.message : "筛选条件无效";
    return null;
  }
}

async function load({ clearCurrentSelection = false } = {}): Promise<boolean> {
  const query = buildQuery();
  if (!query) return false;
  const request = ++listRequest;
  if (clearCurrentSelection) selected.value = null;
  loading.value = true;
  error.value = "";
  try {
    const result = await adminApi.auditEvents(query);
    if (request !== listRequest) return false;
    items.value = result.items;
    total.value = result.total;
    const available = new Set(result.items.map((event) => event.id));
    selectedIds.value = new Set([...selectedIds.value].filter((id) => available.has(id)));
    return true;
  } catch (failure) {
    if (request === listRequest) error.value = adminErrorMessage(failure, "读取审计日志");
    return false;
  } finally {
    if (request === listRequest) loading.value = false;
  }
}

function refresh() { void load({ clearCurrentSelection: true }); }
function applyFilters() { page.value = 0; void load({ clearCurrentSelection: true }); }
onMounted(load);
</script>

<template>
  <AdminPageFrame
    title="审计日志"
    description="审计记录为只读安全摘要，展示操作者、结果、前后状态和 requestId，不展示完整请求正文或凭据。"
  >
    <template #actions>
      <span class="admin-header-readout admin-code">只读 · {{ total }} 条</span>
      <button class="button button--small admin-command" type="button" :disabled="loading" @click="refresh"><span class="admin-command__dot" aria-hidden="true"></span>刷新</button>
    </template>

    <form class="admin-command-row admin-toolbar admin-command-row--audit" @submit.prevent="applyFilters">
      <div class="admin-command-row__label"><span class="admin-kicker">事件筛选</span><strong>安全检索</strong></div>
      <label class="admin-field"><span>操作者 ID</span><input v-model="filters.actorUserId" type="number" min="1" step="1" /></label>
      <label class="admin-field"><span>操作</span><input v-model="filters.action" maxlength="64" placeholder="如 USER_ROLES_CHANGED" /></label>
      <label class="admin-field"><span>目标类型</span><input v-model="filters.targetType" maxlength="64" placeholder="如 USER" /></label>
      <label class="admin-field"><span>目标 ID</span><input v-model="filters.targetId" maxlength="160" /></label>
      <label class="admin-field"><span>开始时间</span><input v-model="filters.from" type="datetime-local" /></label>
      <label class="admin-field"><span>结束时间</span><input v-model="filters.to" type="datetime-local" /></label>
      <button class="button button--primary admin-command-row__submit" type="submit">筛选<span aria-hidden="true">↗</span></button>
    </form>

    <InlineNotice v-if="filterError" :message="filterError" tone="warning" />
    <LoadingState v-if="loading" label="正在读取审计日志…" />
    <ErrorState v-else-if="error" title="审计日志读取失败" :message="error"><RetryButton @retry="load" /></ErrorState>
    <EmptyState v-else-if="!items.length" title="暂无审计记录" message="当前筛选条件没有返回审计事件。" />

    <template v-else>
      <div class="signal-strip admin-motion-enter" aria-label="审计日志摘要">
        <div class="signal-strip__item"><span class="signal-strip__label">事件总数</span><strong>{{ total }}</strong></div>
        <div class="signal-strip__item"><span class="signal-strip__label">当前页</span><strong>{{ items.length }} 条事件</strong></div>
        <div class="signal-strip__item"><span class="signal-strip__label">筛选条件</span><strong>{{ filterError ? "需要修正" : "有效" }}</strong></div>
        <div class="signal-strip__item"><span class="signal-strip__label">记录内容</span><strong>仅安全摘要</strong></div>
      </div>

      <div :class="{ 'data-rail': selected }">
        <section class="admin-data-surface admin-table-wrap admin-motion-enter" aria-label="审计日志">
          <header class="admin-data-surface__head"><div><p class="admin-kicker">不可变记录</p><h2>审计事件</h2></div><span class="admin-data-surface__hint">仅显示摘要，不展开请求正文</span></header>
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
          <table class="admin-table admin-data-table admin-audit-table">
            <thead><tr><th v-if="isColumnVisible('createdAt')"><button class="admin-table__sort" type="button" data-sort-key="createdAt" @click="setSort('createdAt')">时间 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('action')"><button class="admin-table__sort" type="button" data-sort-key="action" @click="setSort('action')">操作与目标 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('result')"><button class="admin-table__sort" type="button" data-sort-key="result" @click="setSort('result')">结果 <span aria-hidden="true">↕</span></button></th><th v-if="isColumnVisible('summary')">安全摘要</th><th v-if="isColumnVisible('requestId')"><button class="admin-table__sort" type="button" data-sort-key="requestId" @click="setSort('requestId')">请求 ID <span aria-hidden="true">↕</span></button></th><th class="admin-table__selection-heading" aria-label="选择" /></tr></thead>
            <tbody>
              <tr v-if="!displayItems.length" class="admin-table__empty-row"><td :colspan="visibleColumns.length + 1">当前页没有符合表格筛选的审计事件。</td></tr>
              <tr v-for="event in displayItems" :key="event.id" class="admin-data-row" :data-selected="selected?.id === event.id">
                <td v-if="isColumnVisible('createdAt')" class="admin-code admin-nowrap">{{ formatDate(event.createdAt) }}</td>
                <td v-if="isColumnVisible('action')"><div class="admin-record"><span class="admin-record__index admin-code">#{{ event.id }}</span><div><strong>{{ event.action }}</strong><div class="admin-muted">{{ event.targetType }} · {{ event.targetId }} · 操作者 #{{ event.actorUserId }}</div></div></div></td>
                <td v-if="isColumnVisible('result')"><span class="admin-result-mark" aria-hidden="true">●</span>{{ event.result }}</td>
                <td v-if="isColumnVisible('summary')"><details class="admin-row-actions"><summary aria-label="行操作">⋯</summary><div class="admin-table__actions"><button class="button button--small admin-action-button" type="button" @click="selected = event">查看摘要<span aria-hidden="true">↗</span></button></div></details></td>
                <td v-if="isColumnVisible('requestId')" class="admin-code admin-nowrap">{{ event.requestId }}</td>
                <td class="admin-table__selection-cell"><input type="checkbox" :checked="selectedIds.has(event.id)" :aria-label="`选择审计事件 ${event.id}`" @change="toggleRow(event.id)" /></td>
              </tr>
            </tbody>
          </table>
        </section>

        <section v-if="selected" class="admin-inspector admin-detail admin-motion-enter" aria-label="审计事件摘要">
          <div class="admin-detail__header admin-inspector__header"><div><p class="admin-kicker">当前事件</p><h2>审计事件 #{{ selected.id }}</h2><p class="admin-code">{{ formatDate(selected.createdAt) }} · {{ selected.action }}</p></div><button class="button button--small" type="button" @click="selected = null">关闭</button></div>
          <section class="admin-inspector__summary"><div><span class="admin-kicker">结果</span><strong>{{ selected.result }}</strong></div><div><span class="admin-kicker">操作者</span><strong>#{{ selected.actorUserId }}</strong></div><div><span class="admin-kicker">目标</span><strong>{{ selected.targetType }} · {{ selected.targetId }}</strong></div></section>
          <dl><dt>变更前摘要</dt><dd>{{ selected.beforeSummary }}</dd><dt>变更后摘要</dt><dd>{{ selected.afterSummary }}</dd><dt>结果</dt><dd>{{ selected.result }}</dd><dt>Request ID</dt><dd class="admin-code">{{ selected.requestId }}</dd></dl>
        </section>
      </div>

      <div class="admin-pagination admin-pagination--rail"><span class="admin-code">第 {{ page + 1 }} 页 · {{ total }} 条事件</span><div class="admin-pagination__actions"><button class="button button--small" type="button" :disabled="page === 0 || loading" @click="page--; load({ clearCurrentSelection: true })"><DirectionalArrowIcon direction="left" />上一页</button><button class="button button--small" type="button" :disabled="(page + 1) * size >= total || loading" @click="page++; load({ clearCurrentSelection: true })">下一页<DirectionalArrowIcon direction="right" /></button></div></div>
    </template>
  </AdminPageFrame>
</template>
