import type { IsoDateTime, Page } from "./api";
import type { Role } from "./auth";

export type AdminModuleStatus = "AVAILABLE" | "UNAVAILABLE" | "NOT_CONFIGURED";
export type AdminServiceStatusValue = "AVAILABLE" | "UNAVAILABLE";

export interface AdminModuleCapability {
  available: boolean;
  status: AdminModuleStatus;
  reason?: string | null;
}

export interface AdminServiceStatus {
  name: "spring";
  version: string;
  status: AdminServiceStatusValue;
}

export interface AdminCapability {
  userId: number;
  roles: Role[];
  modules: Record<string, AdminModuleCapability>;
  service: AdminServiceStatus;
}

export type AdminUserStatus = "ACTIVE" | "DISABLED";

export interface AdminUser {
  id: number;
  email: string;
  username?: string | null;
  status: AdminUserStatus;
  disabledReason?: string | null;
  disabledAt?: IsoDateTime | null;
  roles: Role[];
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export type AdminUserPage = Page<AdminUser>;

export interface AdminUserStatusRequest {
  status: AdminUserStatus;
  reason?: string | null;
}

export interface AdminUserRolesRequest {
  roles: Role[];
}

export interface AdminAuditEvent {
  id: number;
  actorUserId: number;
  action: string;
  targetType: string;
  targetId: string;
  result: string;
  requestId: string;
  beforeSummary: string;
  afterSummary: string;
  createdAt: IsoDateTime;
}

export type AdminAuditEventPage = Page<AdminAuditEvent>;

export type BackgroundTaskStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELED";

export interface BackgroundTask {
  id: number;
  taskType: string;
  status: BackgroundTaskStatus;
  createdAt: IsoDateTime;
  startedAt?: IsoDateTime | null;
  deadlineAt?: IsoDateTime | null;
  heartbeatAt?: IsoDateTime | null;
  finishedAt?: IsoDateTime | null;
  failureCode?: string | null;
  failureReason?: string | null;
  resultCount?: number | null;
  retryCount: number;
  maxAttempts: number;
  cancelRequestedAt?: IsoDateTime | null;
  requestedByUserId?: number | null;
  requestId: string;
}

export type BackgroundTaskPage = Page<BackgroundTask>;

export type ReviewType =
  | "RESOURCE"
  | "KNOWLEDGE_CHUNK"
  | "PRESENTATION_MANIFEST"
  | "PRESENTATION_PAGE"
  | "DSVP_REQUEST_SNAPSHOT";

export type ReviewStatus = "LEGACY_UNVERIFIED" | "DRAFT" | "PUBLISHED" | "VERIFIED" | "EXCLUDED";

export interface ReviewItem {
  type: ReviewType;
  id: string;
  title: string;
  status: ReviewStatus;
  chapterId?: string | null;
  versionLabel?: string | null;
  sourceComplete: boolean;
  updatedAt: IsoDateTime;
}

export type ReviewItemPage = Page<ReviewItem>;

export interface ReviewSource {
  type: string;
  id: string;
  title: string;
  status: string;
}

export interface ReviewDetail {
  item: ReviewItem;
  sourceChain: ReviewSource[];
}

export interface ReviewStatusRequest {
  status: ReviewStatus;
  note?: string | null;
}

export interface ReviewHistoryEvent {
  id: number;
  previousStatus: ReviewStatus;
  nextStatus: ReviewStatus;
  note: string;
  reviewerUserId?: number | null;
  requestId: string;
  createdAt: IsoDateTime;
}

export type ModelConfigCapabilityReason =
  | "MASTER_KEY_UNAVAILABLE"
  | "NOT_CONFIGURED"
  | "PERSISTED_CONFIGURATION_DISABLED"
  | "PERSISTED_QUOTA_NOT_CONFIGURED"
  | "MODEL_CONFIG_UNAVAILABLE";

export interface ModelConfig {
  provider: string;
  baseUrl: string;
  model: string;
  apiKeyConfigured: boolean;
  temperature: number;
  maxOutputTokens: number;
  requestTimeoutMs: number;
  retryCount: number;
  dailyTokenQuota: number;
  enabled: boolean;
  lastConnectionTestStatus?: string | null;
  lastConnectionTestedAt?: IsoDateTime | null;
  updatedAt: IsoDateTime;
}

export interface ModelConfigCapability {
  available: boolean;
  reason?: ModelConfigCapabilityReason | null;
  configuration?: ModelConfig | null;
}

export interface UpdateModelConfigRequest {
  provider: string;
  baseUrl: string;
  model: string;
  apiKey?: string;
  temperature?: number;
  maxOutputTokens?: number;
  requestTimeoutMs?: number;
  retryCount?: number;
  dailyTokenQuota?: number;
  enabled?: boolean;
}

export interface ModelConfigConnectionTest {
  connected: boolean;
  code: string;
}

export type MailSecurityMode = "NONE" | "STARTTLS" | "SSL";

export type MailConfigCapabilityReason =
  | "MASTER_KEY_UNAVAILABLE"
  | "MAIL_CONFIG_UNAVAILABLE";

export interface MailConfig {
  siteName: string;
  enabled: boolean;
  smtpHost: string;
  smtpPort: number;
  securityMode: MailSecurityMode;
  smtpUsername: string;
  smtpPasswordConfigured: boolean;
  fromEmail: string;
  fromName: string;
  connectionTimeoutSeconds: number;
  verificationTtlMinutes: number;
  resendIntervalSeconds: number;
  sessionTtlDays: number;
  verificationSubject: string;
  verificationTemplateHtml: string;
  lastConnectionTestStatus?: string | null;
  lastConnectionTestedAt?: IsoDateTime | null;
  updatedAt?: IsoDateTime | null;
}

export interface MailConfigCapability {
  available: boolean;
  reason?: MailConfigCapabilityReason | string | null;
  configuration?: MailConfig | null;
}

export interface UpdateMailConfigRequest {
  siteName: string;
  enabled: boolean;
  smtpHost: string;
  smtpPort: number;
  securityMode: MailSecurityMode;
  smtpUsername: string;
  smtpPassword?: string;
  clearSmtpPassword: boolean;
  fromEmail: string;
  fromName: string;
  connectionTimeoutSeconds: number;
  verificationTtlMinutes: number;
  resendIntervalSeconds: number;
  sessionTtlDays: number;
  verificationSubject: string;
  verificationTemplateHtml: string;
}

export interface MailConnectionTest {
  connected: boolean;
  code: string;
}

export interface TestMailResult {
  sent: boolean;
  code: string;
}

/**
 * The Spring compiler reads the selected provider from the persisted admin
 * configuration. The URL is always server-side; no provider credentials cross
 * the browser boundary.
 */
export type SandboxProvider = "PISTON" | "JUDGE0";
/** @deprecated Use SandboxProvider; retained for callers during the migration. */
export type SandboxProtocol = SandboxProvider;

export type SandboxConfigCapabilityReason =
  | "SANDBOX_CONFIG_UNAVAILABLE"
  | "NOT_CONFIGURED"
  | "PERSISTED_CONFIGURATION_DISABLED"
  | "MASTER_KEY_UNAVAILABLE"
  | "SANDBOX_CONFIG_INVALID"
  | string;

export interface SandboxConfig {
  provider: SandboxProvider;
  baseUrl: string;
  enabled: boolean;
  updatedAt?: IsoDateTime | null;
  lastConnectionTestStatus?: string | null;
  lastConnectionTestedAt?: IsoDateTime | null;
}

export interface SandboxRuntimeStatus {
  codeExecutionConfigured: boolean;
  source?: "environment" | "persisted" | "unknown";
  provider?: SandboxProvider | "UNKNOWN";
  checkedAt?: IsoDateTime | null;
}

export interface SandboxConfigCapability {
  available: boolean;
  reason?: SandboxConfigCapabilityReason | null;
  configuration?: SandboxConfig | null;
  runtime?: SandboxRuntimeStatus | null;
}

export interface UpdateSandboxConfigRequest {
  provider: SandboxProvider;
  baseUrl: string;
  enabled: boolean;
}

export interface SandboxConnectionTest {
  connected: boolean;
  code: string;
}
