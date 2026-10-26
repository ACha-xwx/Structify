import type { IsoDateTime, Page } from "./api";
import type { Role } from "./auth";
import type { CourseLibraryEntry, LearningMap } from "./contracts";

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

export interface ConnectionTest {
  connected: boolean;
  code: string;
}

export type ModelConfigConnectionTest = ConnectionTest;

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

export type MailConnectionTest = ConnectionTest;

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

export type SandboxConnectionTest = ConnectionTest;

export type AdminJobSource = "BACKGROUND" | "STUDYROOM";

/** A row of the merged job list: the maintenance ledger and the studyroom's own job table. */
export interface AdminJobItem {
  source: AdminJobSource;
  id: string;
  type: string;
  ownerUserId: number | null;
  status: string;
  createdAt: string | null;
  updatedAt: string | null;
  error: string | null;
  tokens: number;
}

export interface AdminJobPage {
  items: AdminJobItem[];
  page: number;
  size: number;
  total: number;
}

export interface AdminCostRow {
  bucket: string | null;
  userId: number | null;
  calls: number;
  tokens: number;
}

export interface AdminCostReport {
  from: string;
  to: string;
  groupBy: "DAY" | "USER";
  calls: number;
  tokens: number;
  rows: AdminCostRow[];
}

export interface AdminObservabilityModule {
  module: string;
  available: boolean;
  status: string;
  reason: string | null;
  counts: Record<string, number>;
}

/** A class as the administrator maintains it. */
export interface AdminClass {
  id: string;
  name: string;
  term: string | null;
  students: number;
  teachers: string[];
}

export type ClassRole = "STUDENT" | "TEACHER";

/** One person enrolled in a class. */
export interface AdminClassMember {
  userId: number;
  username: string | null;
  email: string;
  roleInClass: ClassRole;
  joinedAt: string | null;
}

export interface CreateClassRequest {
  name: string;
  term?: string;
}

export interface AddClassMemberRequest {
  identifier: string;
  roleInClass: ClassRole;
}

/**
 * The enrolment that just happened. `teacherRoleGranted` is true when this call also switched on the platform
 * TEACHER role for the account - the console reports it so nobody wonders whether one more step is needed.
 */
export interface AddClassMemberResult {
  member: AdminClassMember;
  teacherRoleGranted: boolean;
}

/** One course in a learner's library; the shape lives in contracts.ts next to the learning map. */
export type AdminUserCourse = CourseLibraryEntry;

/** One recent studyroom generation job of the user, with what it cost. */
export interface AdminUserRecentJob {
  id: string;
  type: string | null;
  status: string;
  createdAt: string | null;
  tokens: number;
}

/** The learning footprint of one account, gathered for the administrator in a single read. */
export interface AdminUserLearning {
  userId: number;
  email: string;
  username: string | null;
  status: string;
  courses: AdminUserCourse[];
  bookmarks: number;
  classroomSessions: number;
  classroomEvents: number;
  lastActivityAt: string | null;
  tokensLast30Days: number;
  callsLast30Days: number;
  recentJobs: AdminUserRecentJob[];
  learningMap: LearningMap;
}

// ----- knowledge base console ---------------------------------------------------

/** The retrieval tuning in effect. A saved admin row wins over the configuration defaults. */
export interface KnowledgeMatchingSettings {
  minimumScore: number;
  searchLimit: number;
  titleWeightLong: number;
  titleWeightShort: number;
}

export interface KnowledgeSettingsView extends KnowledgeMatchingSettings {
  /** True when an administrator has saved a row; false means the configuration defaults are in effect. */
  customized: boolean;
  defaults: KnowledgeMatchingSettings;
}

export interface KnowledgeOverview {
  totalChunks: number;
  byReviewStatus: Record<string, number>;
  bySource: Record<string, number>;
  snapshotChunks: number;
  settingsCustomized: boolean;
}

export type KnowledgeReviewStatus = "DRAFT" | "PENDING" | "VERIFIED" | "REJECTED";
export type KnowledgeLicenseScope = "PUBLIC" | "CLASSROOM_ONLY" | "TEAM_ONLY";

export interface AdminKnowledgeChunkRow {
  id: string;
  chapterId: string | null;
  title: string;
  sourcePath: string;
  pageLabel: string | null;
  reviewStatus: KnowledgeReviewStatus | string;
  licenseScope: KnowledgeLicenseScope | string;
  /** Whether the live retrieval gate (review + source chain + chapter) would let this chunk through. */
  retrievable: boolean;
  /** True when the import pipeline owns this source and the next import would overwrite edits. */
  importOwned: boolean;
  contentLength: number;
  createdAt: string | null;
}

export type AdminKnowledgeChunkPage = Page<AdminKnowledgeChunkRow>;

export interface AdminKnowledgeChunkDetail {
  id: string;
  chapterId: string | null;
  title: string;
  content: string;
  sourcePath: string;
  pageLabel: string | null;
  reviewStatus: KnowledgeReviewStatus | string;
  licenseScope: KnowledgeLicenseScope | string;
  retrievable: boolean;
  importOwned: boolean;
  createdAt: string | null;
}

export interface KnowledgeChunkQuery {
  page?: number;
  size?: number;
  chapterId?: string;
  status?: string;
  sourcePrefix?: string;
  q?: string;
}

export interface SaveKnowledgeSettingsRequest {
  minimumScore: number;
  searchLimit: number;
  titleWeightLong: number;
  titleWeightShort: number;
}

export interface TestKnowledgeSearchRequest {
  query: string;
  chapterId?: string;
  audience?: "GUEST" | "STUDENT" | "TEAM";
  minimumScore?: number;
  searchLimit?: number;
  titleWeightLong?: number;
  titleWeightShort?: number;
}

export interface KnowledgeTestSearchHit {
  chunkId: string;
  title: string;
  sourcePath: string;
  pageLabel: string | null;
  score: number;
}

export interface KnowledgeTestSearchResult {
  effectiveSettings: KnowledgeMatchingSettings;
  hits: KnowledgeTestSearchHit[];
}

export interface CreateKnowledgeChunkRequest {
  chapterId?: string;
  title: string;
  content: string;
  pageLabel?: string;
  reviewStatus?: KnowledgeReviewStatus;
  licenseScope?: KnowledgeLicenseScope;
}

export type PatchKnowledgeChunkRequest = Partial<CreateKnowledgeChunkRequest>;
