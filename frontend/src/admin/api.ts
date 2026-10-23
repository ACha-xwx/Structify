import { api } from "../app/providers/runtime";
import type { Chapter } from "../shared/types/contracts";
import type {
  AdminAuditEventPage,
  AdminCapability,
  AdminClass,
  AdminClassMember,
  AddClassMemberRequest,
  AddClassMemberResult,
  CreateClassRequest,
  AdminCostReport,
  AdminJobPage,
  AdminObservabilityModule,
  AdminUser,
  AdminUserLearning,
  AdminUserPage,
  AdminUserRolesRequest,
  AdminUserStatusRequest,
  BackgroundTask,
  BackgroundTaskPage,
  ModelConfig,
  ModelConfigCapability,
  ModelConfigConnectionTest,
  MailConfig,
  MailConfigCapability,
  MailConnectionTest,
  TestMailResult,
  UpdateMailConfigRequest,
  SandboxConfig,
  SandboxConfigCapability,
  SandboxConnectionTest,
  UpdateSandboxConfigRequest,
  ReviewDetail,
  ReviewHistoryEvent,
  ReviewItem,
  ReviewItemPage,
  ReviewStatusRequest,
  AdminKnowledgeChunkDetail,
  AdminKnowledgeChunkPage,
  CreateKnowledgeChunkRequest,
  KnowledgeChunkQuery,
  KnowledgeOverview,
  KnowledgeSettingsView,
  KnowledgeTestSearchResult,
  PatchKnowledgeChunkRequest,
  SaveKnowledgeSettingsRequest,
  TestKnowledgeSearchRequest,
} from "../shared/types";
import { ApiClientError, formatDateTime, staffErrorMessage, type ApiResponse } from "../shared/api";

function jsonData<T>(response: ApiResponse<T>): T {
  if (response.kind !== "json") throw new Error("ADMIN_INVALID_RESPONSE");
  return response.data;
}

export interface AdminQuery {
  page?: number;
  size?: number;
  search?: string;
  status?: string;
  role?: string;
  type?: string;
  source?: string;
  groupBy?: string;
  userId?: number;
  taskType?: string;
  action?: string;
  targetType?: string;
  targetId?: string;
  actorUserId?: number;
  from?: string;
  to?: string;
}

function compactQuery(input: AdminQuery): Record<string, string | number> {
  return Object.fromEntries(Object.entries(input).filter(([, value]) => value !== undefined && value !== "")) as Record<string, string | number>;
}

export const adminApi = {
  async capabilities(): Promise<AdminCapability> {
    return jsonData(await api.get<AdminCapability>("/admin/capabilities"));
  },
  async getModelConfig(): Promise<ModelConfigCapability> {
    return jsonData(await api.get<ModelConfigCapability>("/admin/model-config"));
  },
  async updateModelConfig(payload: Record<string, unknown>): Promise<ModelConfig> {
    return jsonData(await api.put<ModelConfig>("/admin/model-config", payload));
  },
  async testModelConnection(): Promise<ModelConfigConnectionTest> {
    return jsonData(await api.post<ModelConfigConnectionTest>("/admin/model-config/test"));
  },
  async getMailConfig(): Promise<MailConfigCapability> {
    return jsonData(await api.get<MailConfigCapability>("/admin/mail-config"));
  },
  async updateMailConfig(payload: UpdateMailConfigRequest): Promise<MailConfig> {
    return jsonData(await api.put<MailConfig>("/admin/mail-config", payload));
  },
  async testMailConnection(payload: UpdateMailConfigRequest): Promise<MailConnectionTest> {
    return jsonData(await api.post<MailConnectionTest>("/admin/mail-config/test-connection", payload));
  },
  async sendTestMail(config: UpdateMailConfigRequest, recipient: string): Promise<TestMailResult> {
    return jsonData(await api.post<TestMailResult>("/admin/mail-config/test-email", { config, recipient }));
  },
  async getSandboxConfig(): Promise<SandboxConfigCapability> {
    return jsonData(await api.get<SandboxConfigCapability>("/admin/sandbox-config"));
  },
  async updateSandboxConfig(payload: UpdateSandboxConfigRequest): Promise<SandboxConfig> {
    return jsonData(await api.put<SandboxConfig>("/admin/sandbox-config", payload));
  },
  async testSandboxConnection(): Promise<SandboxConnectionTest> {
    return jsonData(await api.post<SandboxConnectionTest>("/admin/sandbox-config/test"));
  },
  async users(query: AdminQuery = {}): Promise<AdminUserPage> {
    return jsonData(await api.get<AdminUserPage>("/admin/users", { query: compactQuery(query) }));
  },
  async user(id: number): Promise<AdminUser> {
    return jsonData(await api.get<AdminUser>(`/admin/users/${encodeURIComponent(id)}`));
  },
  /** The learning footprint of one account: courses, classroom work, spend, jobs, and the mastery map. */
  async userLearning(id: number): Promise<AdminUserLearning> {
    return jsonData(await api.get<AdminUserLearning>(`/admin/users/${encodeURIComponent(id)}/learning`));
  },
  async updateUserStatus(id: number, payload: AdminUserStatusRequest): Promise<AdminUser> {
    return jsonData(await api.patch<AdminUser>(`/admin/users/${encodeURIComponent(id)}/status`, payload));
  },
  async updateUserRoles(id: number, payload: AdminUserRolesRequest): Promise<AdminUser> {
    return jsonData(await api.patch<AdminUser>(`/admin/users/${encodeURIComponent(id)}/roles`, payload));
  },
  async reviews(query: AdminQuery = {}): Promise<ReviewItemPage> {
    return jsonData(await api.get<ReviewItemPage>("/admin/reviews", { query: compactQuery(query) }));
  },
  async review(type: string, id: string): Promise<ReviewDetail> {
    return jsonData(await api.get<ReviewDetail>(`/admin/reviews/${encodeURIComponent(type)}/${encodeURIComponent(id)}`));
  },
  async updateReviewStatus(type: string, id: string, payload: ReviewStatusRequest): Promise<ReviewItem> {
    return jsonData(await api.patch<ReviewItem>(`/admin/reviews/${encodeURIComponent(type)}/${encodeURIComponent(id)}/status`, payload));
  },
  async reviewHistory(type: string, id: string): Promise<ReviewHistoryEvent[]> {
    return jsonData(await api.get<ReviewHistoryEvent[]>(`/admin/reviews/${encodeURIComponent(type)}/${encodeURIComponent(id)}/history`));
  },
  async tasks(query: AdminQuery = {}): Promise<BackgroundTaskPage> {
    return jsonData(await api.get<BackgroundTaskPage>("/admin/background-tasks", { query: compactQuery(query) }));
  },
  async task(id: number): Promise<BackgroundTask> {
    return jsonData(await api.get<BackgroundTask>(`/admin/background-tasks/${encodeURIComponent(id)}`));
  },
  async recoverTimeouts(): Promise<BackgroundTask> {
    return jsonData(await api.post<BackgroundTask>("/admin/background-tasks/recover-timeouts"));
  },
  async retryTask(id: number): Promise<BackgroundTask> {
    return jsonData(await api.post<BackgroundTask>(`/admin/background-tasks/${encodeURIComponent(id)}/retry`));
  },
  async cancelTask(id: number): Promise<BackgroundTask> {
    return jsonData(await api.post<BackgroundTask>(`/admin/background-tasks/${encodeURIComponent(id)}/cancel`));
  },
  async jobs(query: AdminQuery = {}): Promise<AdminJobPage> {
    return jsonData(await api.get<AdminJobPage>("/admin/observability/jobs", { query: compactQuery(query) }));
  },
  async cost(query: AdminQuery = {}): Promise<AdminCostReport> {
    return jsonData(await api.get<AdminCostReport>("/admin/observability/cost", { query: compactQuery(query) }));
  },
  async observabilityModules(): Promise<AdminObservabilityModule[]> {
    return jsonData(await api.get<AdminObservabilityModule[]>("/admin/observability/modules"));
  },
  async auditEvents(query: AdminQuery = {}): Promise<AdminAuditEventPage> {
    return jsonData(await api.get<AdminAuditEventPage>("/admin/audit-events", { query: compactQuery(query) }));
  },
  async classes(): Promise<AdminClass[]> {
    return jsonData(await api.get<AdminClass[]>("/admin/classes"));
  },
  async createClass(payload: CreateClassRequest): Promise<AdminClass> {
    return jsonData(await api.post<AdminClass>("/admin/classes", payload));
  },
  async classMembers(classId: string): Promise<AdminClassMember[]> {
    return jsonData(await api.get<AdminClassMember[]>(`/admin/classes/${encodeURIComponent(classId)}/members`));
  },
  async addClassMember(classId: string, payload: AddClassMemberRequest): Promise<AddClassMemberResult> {
    return jsonData(await api.post<AddClassMemberResult>(`/admin/classes/${encodeURIComponent(classId)}/members`, payload));
  },
  async removeClassMember(classId: string, userId: number): Promise<void> {
    await api.delete(`/admin/classes/${encodeURIComponent(classId)}/members/${encodeURIComponent(userId)}`);
  },
  async deleteClass(classId: string): Promise<void> {
    await api.delete(`/admin/classes/${encodeURIComponent(classId)}`);
  },
  /** The knowledge console: what the AI may quote, and how loosely it matches. */
  async knowledgeOverview(): Promise<KnowledgeOverview> {
    return jsonData(await api.get<KnowledgeOverview>("/admin/knowledge/overview"));
  },
  async knowledgeSettings(): Promise<KnowledgeSettingsView> {
    return jsonData(await api.get<KnowledgeSettingsView>("/admin/knowledge/settings"));
  },
  async saveKnowledgeSettings(payload: SaveKnowledgeSettingsRequest): Promise<KnowledgeSettingsView> {
    return jsonData(await api.put<KnowledgeSettingsView>("/admin/knowledge/settings", payload));
  },
  async resetKnowledgeSettings(): Promise<KnowledgeSettingsView> {
    return jsonData(await api.delete<KnowledgeSettingsView>("/admin/knowledge/settings"));
  },
  /** Try a query against the live snapshot with explicit tuning, without saving anything. */
  async testKnowledgeSearch(payload: TestKnowledgeSearchRequest): Promise<KnowledgeTestSearchResult> {
    return jsonData(await api.post<KnowledgeTestSearchResult>("/admin/knowledge/test-search", payload));
  },
  async knowledgeChunks(query: KnowledgeChunkQuery = {}): Promise<AdminKnowledgeChunkPage> {
    return jsonData(await api.get<AdminKnowledgeChunkPage>("/admin/knowledge/chunks", { query: compactQuery(query) }));
  },
  async knowledgeChunk(id: string): Promise<AdminKnowledgeChunkDetail> {
    return jsonData(await api.get<AdminKnowledgeChunkDetail>(`/admin/knowledge/chunks/${encodeURIComponent(id)}`));
  },
  async createKnowledgeChunk(payload: CreateKnowledgeChunkRequest): Promise<AdminKnowledgeChunkDetail> {
    return jsonData(await api.post<AdminKnowledgeChunkDetail>("/admin/knowledge/chunks", payload));
  },
  async updateKnowledgeChunk(id: string, payload: PatchKnowledgeChunkRequest): Promise<AdminKnowledgeChunkDetail> {
    return jsonData(await api.patch<AdminKnowledgeChunkDetail>(`/admin/knowledge/chunks/${encodeURIComponent(id)}`, payload));
  },
  async deleteKnowledgeChunk(id: string): Promise<void> {
    await api.delete(`/admin/knowledge/chunks/${encodeURIComponent(id)}`);
  },
  /** Chapter names for the knowledge console's dropdowns; the shared chapter catalog. */
  async knowledgeChapters(): Promise<Chapter[]> {
    return jsonData(await api.get<Chapter[]>("/chapters"));
  },
};

export function adminErrorMessage(error: unknown, action: string): string {
  return staffErrorMessage(error, action);
}

export function formatDate(value?: string | null): string {
  return formatDateTime(value);
}
