<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import AdminPageFrame from "../components/AdminPageFrame.vue";
import { adminApi, adminErrorMessage, formatDate } from "../api";
import type { AdminCostReport, AdminJobPage, AdminObservabilityModule } from "../../shared/types";

/**
 * Work the product is doing and what it costs.
 *
 * The console used to show only the maintenance ledger, so the studyroom's generation jobs - the ones that
 * actually run for minutes and spend tokens - were invisible. This page reads both ledgers through
 * /admin/observability and reports the settled spend; it never changes a job.
 */
const jobs = ref<AdminJobPage | null>(null);
const cost = ref<AdminCostReport | null>(null);
const modules = ref<AdminObservabilityModule[]>([]);
const loading = ref(true);
const jobsError = ref("");
const costError = ref("");

const source = ref<"" | "BACKGROUND" | "STUDYROOM">("");
const status = ref("");
const owner = ref("");
const page = ref(0);
const pageSize = 20;

const groupBy = ref<"DAY" | "USER">("DAY");
const from = ref(dayOffset(29));
const to = ref(dayOffset(0));

function dayOffset(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

const total = computed(() => jobs.value?.total ?? 0);
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const studyroomCounts = computed(() => counts("studyroom"));
const learningCounts = computed(() => counts("learning"));
const quotaCounts = computed(() => counts("quota"));

function counts(module: string): Record<string, number> {
  return modules.value.find((entry) => entry.module === module)?.counts ?? {};
}

function isFailure(value: string): boolean {
  return /FAIL|ERROR|CANCEL|DEAD/.test(value.toUpperCase());
}

async function loadJobs(): Promise<void> {
  try {
    jobsError.value = "";
    jobs.value = await adminApi.jobs({
      source: source.value === "" ? undefined : source.value,
      status: status.value.trim() === "" ? undefined : status.value.trim(),
      userId: owner.value.trim() === "" ? undefined : Number(owner.value.trim()),
      page: page.value,
      size: pageSize,
    });
  } catch (failure) {
    jobsError.value = adminErrorMessage(failure, "读取作业列表");
  }
}

async function loadCost(): Promise<void> {
  try {
    costError.value = "";
    cost.value = await adminApi.cost({ from: from.value, to: to.value, groupBy: groupBy.value });
  } catch (failure) {
    costError.value = adminErrorMessage(failure, "读取花费汇总");
  }
}

async function loadModules(): Promise<void> {
  try {
    modules.value = await adminApi.observabilityModules();
  } catch {
    // The counts are a convenience; a failure here must not block the two tables.
    modules.value = [];
  }
}

async function refresh(): Promise<void> {
  loading.value = true;
  await Promise.all([loadJobs(), loadCost(), loadModules()]);
  loading.value = false;
}

function applyFilters(): void {
  page.value = 0;
  void loadJobs();
}

function turn(next: number): void {
  page.value = Math.min(Math.max(0, next), pageCount.value - 1);
  void loadJobs();
}

onMounted(refresh);
</script>

<template>
  <AdminPageFrame
    title="工作量与花费"
    description="后台任务与课程生成作业的合并视图，以及按天/按用户结算的 token 花费。只读，不会改变任何作业状态。"
  >
    <template #actions>
      <button class="admin-primary" type="button" :disabled="loading" @click="refresh">刷新</button>
    </template>

    <section class="admin-panel obs">
      <div class="obs__stats">
        <div class="obs__stat">
          <span class="obs__label">生成作业</span>
          <strong>{{ studyroomCounts.jobs ?? 0 }}</strong>
          <span class="admin-muted">排队/运行 {{ studyroomCounts.queued ?? 0 }} · 失败 {{ studyroomCounts.failed ?? 0 }}</span>
        </div>
        <div class="obs__stat">
          <span class="obs__label">碎片</span>
          <strong>{{ studyroomCounts.fragments ?? 0 }}</strong>
          <span class="admin-muted">房间 {{ studyroomCounts.rooms ?? 0 }} · run {{ studyroomCounts.runs ?? 0 }}</span>
        </div>
        <div class="obs__stat">
          <span class="obs__label">学习证据</span>
          <strong>{{ learningCounts.classroomEvents ?? 0 }}</strong>
          <span class="admin-muted">代码 {{ learningCounts.codeRuns ?? 0 }} · 动画 {{ learningCounts.animationRecords ?? 0 }}</span>
        </div>
        <div class="obs__stat">
          <span class="obs__label">今日 token</span>
          <strong>{{ quotaCounts.tokensToday ?? 0 }}</strong>
          <span class="admin-muted">{{ quotaCounts.reservationsToday ?? 0 }} 次结算</span>
        </div>
      </div>
    </section>

    <section class="admin-panel obs">
      <header class="admin-section-head">
        <h2>作业</h2>
        <p class="admin-muted">共 {{ total }} 条 · 第 {{ page + 1 }} / {{ pageCount }} 页</p>
      </header>
      <div class="admin-toolbar">
        <label class="admin-field">
          <span>来源</span>
          <select v-model="source" @change="applyFilters">
            <option value="">全部</option>
            <option value="BACKGROUND">后台任务</option>
            <option value="STUDYROOM">课程生成</option>
          </select>
        </label>
        <label class="admin-field">
          <span>状态</span>
          <input v-model="status" placeholder="如 RUNNING / QUEUED" @keyup.enter="applyFilters" />
        </label>
        <label class="admin-field">
          <span>用户 ID</span>
          <input v-model="owner" inputmode="numeric" placeholder="全部" @keyup.enter="applyFilters" />
        </label>
        <button type="button" class="admin-primary" @click="applyFilters">查询</button>
      </div>

      <p v-if="jobsError" class="admin-danger">{{ jobsError }}</p>
      <div class="admin-data-surface">
        <table class="obs__table">
          <thead>
            <tr>
              <th>来源</th>
              <th>类型</th>
              <th>用户</th>
              <th>状态</th>
              <th>创建</th>
              <th>更新</th>
              <th>token</th>
              <th>失败原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in jobs?.items ?? []" :key="`${row.source}-${row.id}`">
              <td class="admin-nowrap">{{ row.source === "STUDYROOM" ? "生成" : "后台" }}</td>
              <td class="admin-code">{{ row.type }}</td>
              <td>{{ row.ownerUserId ?? "—" }}</td>
              <td :class="isFailure(row.status) ? 'admin-danger' : ''">{{ row.status }}</td>
              <td class="admin-nowrap">{{ formatDate(row.createdAt) }}</td>
              <td class="admin-nowrap">{{ formatDate(row.updatedAt) }}</td>
              <td>{{ row.tokens || "—" }}</td>
              <td class="obs__error">{{ row.error ?? "—" }}</td>
            </tr>
            <tr v-if="(jobs?.items ?? []).length === 0">
              <td colspan="8" class="admin-muted">没有符合条件的作业</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="obs__pager">
        <button type="button" :disabled="page === 0" @click="turn(page - 1)">上一页</button>
        <button type="button" :disabled="page + 1 >= pageCount" @click="turn(page + 1)">下一页</button>
      </div>
    </section>

    <section class="admin-panel obs">
      <header class="admin-section-head">
        <h2>花费</h2>
        <p class="admin-muted">只统计已结算的调用（{{ cost?.calls ?? 0 }} 次 · {{ cost?.tokens ?? 0 }} token）</p>
      </header>
      <div class="admin-toolbar">
        <label class="admin-field">
          <span>从</span>
          <input v-model="from" type="date" @change="loadCost" />
        </label>
        <label class="admin-field">
          <span>到</span>
          <input v-model="to" type="date" @change="loadCost" />
        </label>
        <label class="admin-field">
          <span>分组</span>
          <select v-model="groupBy" @change="loadCost">
            <option value="DAY">按天</option>
            <option value="USER">按用户</option>
          </select>
        </label>
      </div>

      <p v-if="costError" class="admin-danger">{{ costError }}</p>
      <div class="admin-data-surface">
        <table class="obs__table">
          <thead>
            <tr>
              <th>{{ groupBy === "USER" ? "用户 ID" : "日期" }}</th>
              <th>调用</th>
              <th>token</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in cost?.rows ?? []" :key="`${row.bucket}-${row.userId}`">
              <td>{{ row.userId ?? row.bucket ?? "—" }}</td>
              <td>{{ row.calls }}</td>
              <td>{{ row.tokens }}</td>
            </tr>
            <tr v-if="(cost?.rows ?? []).length === 0">
              <td colspan="3" class="admin-muted">该区间没有已结算的调用</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </AdminPageFrame>
</template>

<style scoped>
.obs + .obs {
  margin-top: 18px;
}

.obs__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
}

.obs__stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid rgba(12, 44, 52, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
}

.obs__stat strong {
  font-size: 26px;
  line-height: 1.15;
}

.obs__label {
  font-size: 13px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--admin-muted);
}

.obs__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.obs__table th,
.obs__table td {
  padding: 9px 10px;
  text-align: left;
  border-bottom: 1px solid rgba(12, 44, 52, 0.1);
  vertical-align: top;
}

.obs__table th {
  font-weight: 600;
  color: var(--admin-muted);
  white-space: nowrap;
}

.obs__error {
  max-width: 320px;
  overflow-wrap: anywhere;
  color: var(--admin-muted);
}

.obs__pager {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
