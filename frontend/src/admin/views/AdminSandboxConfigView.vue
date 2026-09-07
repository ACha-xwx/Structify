<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import AdminPageFrame from "../components/AdminPageFrame.vue";
import { adminApi, adminErrorMessage, formatDate } from "../api";
import type { SandboxConfig, SandboxConfigCapability, SandboxProvider, SandboxRuntimeStatus } from "../../shared/types";
import LoadingState from "../../shared/components/LoadingState.vue";
import ErrorState from "../../shared/components/ErrorState.vue";
import RetryButton from "../../shared/components/RetryButton.vue";
import InlineNotice from "../../shared/components/InlineNotice.vue";
import StatusBadge from "../../shared/components/StatusBadge.vue";
import RuntimeSelect, { type RuntimeSelectOption } from "../../shared/components/RuntimeSelect.vue";

type NoticeTone = "neutral" | "success" | "warning" | "danger";
const providerOptions: RuntimeSelectOption[] = [
  { value: "PISTON", label: "Piston-compatible" },
  { value: "JUDGE0", label: "Judge0" },
];

const capability = ref<SandboxConfigCapability | null>(null);
const runtime = ref<SandboxRuntimeStatus | null>(null);
const loading = ref(true);
const saving = ref(false);
const testing = ref(false);
const error = ref("");
const notice = ref("");
const noticeTone = ref<NoticeTone>("neutral");
const copied = ref(false);
const draftSaved = ref(false);
const loadedConfig = ref<SandboxConfig | null>(null);
const loadedFormSignature = ref("");
const formInitialized = ref(false);
let loadRequest = 0;

const form = reactive({
  provider: "PISTON" as SandboxProvider,
  baseUrl: "",
  enabled: false,
});

const apiSupported = computed(() => !["SANDBOX_CONFIG_UNAVAILABLE", "MASTER_KEY_UNAVAILABLE"].includes(String(capability.value?.reason || "")));
const isDirty = computed(() => formInitialized.value && formSignature() !== loadedFormSignature.value);
const runtimeConfigured = computed(() => runtime.value?.codeExecutionConfigured === true);
const runtimeLabel = computed(() => runtime.value ? (runtimeConfigured.value ? "运行时已配置" : "未配置") : "未读取");
const configStatusLabel = computed(() => {
  if (!capability.value) return "未读取";
  if (capability.value.reason === "SANDBOX_CONFIG_UNAVAILABLE") return "接口待接入";
  if (loadedConfig.value && !loadedConfig.value.enabled) return "已停用";
  if (loadedConfig.value?.enabled && loadedConfig.value.baseUrl) return "已保存";
  return "待配置";
});
const configStatusTone = computed<NoticeTone>(() => {
  if (capability.value?.reason === "SANDBOX_CONFIG_UNAVAILABLE") return "warning";
  if (loadedConfig.value && !loadedConfig.value.enabled) return "neutral";
  return loadedConfig.value?.enabled && loadedConfig.value.baseUrl ? "success" : "warning";
});
const safeBaseUrl = computed(() => {
  const value = form.baseUrl.trim();
  if (!value) return "";
  try {
    const parsed = new URL(value);
    if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password || parsed.search || parsed.hash) return "";
    return value.replace(/\/+$/, "");
  } catch {
    return "";
  }
});
const envSnippet = computed(() => {
  const url = safeBaseUrl.value;
  const variable = form.provider === "JUDGE0" ? "JUDGE0_BASE_URL" : "PISTON_BASE_URL";
  const label = form.provider === "JUDGE0" ? "Judge0" : "Piston-compatible";
  return url
    ? `${variable}=${url}\n# 修改后重启 spring-api 使配置生效`
    : `${variable}=\n# 填入经过审核的 ${label} 服务地址后再部署`;
});

const providerLabel = computed(() => form.provider === "JUDGE0" ? "Judge0" : "Piston-compatible");

function formSignature() {
  return JSON.stringify({ provider: form.provider, baseUrl: form.baseUrl, enabled: form.enabled });
}

function fill(config?: SandboxConfig | null) {
  loadedConfig.value = config || null;
  form.provider = config?.provider || "PISTON";
  form.baseUrl = config?.baseUrl || "";
  form.enabled = config?.enabled ?? false;
  loadedFormSignature.value = formSignature();
  formInitialized.value = true;
  draftSaved.value = false;
}

function setNotice(message: string, tone: NoticeTone = "neutral") {
  notice.value = message;
  noticeTone.value = tone;
}

function reasonText(reason?: string | null) {
  const labels: Record<string, string> = {
    SANDBOX_CONFIG_UNAVAILABLE: "当前后端尚未提供沙箱配置管理接口。页面仍可校验地址并生成部署片段，但不会假装已经保存或启用。",
    NOT_CONFIGURED: "服务端尚未登记执行器地址。填写审核过的 Piston-compatible 或 Judge0 地址后保存。",
    PERSISTED_CONFIGURATION_DISABLED: "已保存的执行器配置当前处于停用状态。",
    MASTER_KEY_UNAVAILABLE: "部署环境缺少配置加密根密钥，服务端暂不能保存配置。",
    SANDBOX_CONFIG_INVALID: "服务端拒绝了当前沙箱配置，请检查地址和协议。",
  };
  return reason ? labels[reason] || `服务返回状态：${reason}` : "";
}

function statusForError(failure: unknown): number | undefined {
  return typeof failure === "object" && failure !== null && "status" in failure
    ? Number((failure as { status?: unknown }).status)
    : undefined;
}

async function load(): Promise<boolean> {
  const request = ++loadRequest;
  loading.value = true;
  error.value = "";
  notice.value = "";
  copied.value = false;
  if (request !== loadRequest) return false;
  try {
    const nextCapability = await adminApi.getSandboxConfig();
    if (request !== loadRequest) return false;
    const fallbackRuntime: SandboxRuntimeStatus | null = nextCapability.runtime || (nextCapability.configuration
      ? {
          codeExecutionConfigured: nextCapability.configuration.enabled === true,
          source: "persisted",
          provider: nextCapability.configuration.provider,
          checkedAt: null,
        }
      : null);
    runtime.value = fallbackRuntime;
    capability.value = { ...nextCapability, runtime: fallbackRuntime };
    fill(nextCapability.configuration);
    return true;
  } catch (failure) {
    if (request !== loadRequest) return false;
    if (statusForError(failure) === 404) {
      runtime.value = null;
      capability.value = { available: false, reason: "SANDBOX_CONFIG_UNAVAILABLE", configuration: null, runtime: null };
      fill(null);
      return true;
    }
    capability.value = null;
    error.value = adminErrorMessage(failure, "读取沙箱配置");
    return false;
  } finally {
    if (request === loadRequest) loading.value = false;
  }
}

function reload() {
  if (loading.value || saving.value || testing.value) return;
  if (isDirty.value && !window.confirm("沙箱配置还有未保存的更改，确定重新读取并丢弃这些更改吗？")) return;
  void load();
}

function validateBaseUrl(): string | null {
  const value = form.baseUrl.trim();
  if (!value) return "服务地址不能为空。";
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    return "服务地址必须是完整的 http 或 https 地址。";
  }
  if (!["http:", "https:"].includes(parsed.protocol)) return "服务地址只能使用 http 或 https。";
  if (parsed.username || parsed.password || parsed.search || parsed.hash) return "服务地址不能包含账号、密码、查询参数或片段。";
  form.baseUrl = value.replace(/\/+$/, "");
  return null;
}

async function save() {
  if (saving.value || testing.value) return;
  error.value = "";
  notice.value = "";
  const validation = validateBaseUrl();
  if (validation) {
    setNotice(`保存未完成（VALIDATION_ERROR）。${validation}`, "danger");
    return;
  }
  if (!apiSupported.value) {
    setNotice("当前后端没有沙箱配置保存接口。已保留当前页面草稿，请复制部署片段并由运维注入外部环境。", "warning");
    return;
  }
  saving.value = true;
  try {
    const updated = await adminApi.updateSandboxConfig({ provider: form.provider, baseUrl: form.baseUrl, enabled: form.enabled });
    fill(updated);
    capability.value = { available: updated.enabled, reason: updated.enabled ? null : "PERSISTED_CONFIGURATION_DISABLED", configuration: updated, runtime: runtime.value };
    setNotice("沙箱配置已由服务端保存。执行器仍需通过连接测试并在服务端重启后生效。", "success");
  } catch (failure) {
    setNotice(adminErrorMessage(failure, "保存沙箱配置"), "danger");
  } finally {
    saving.value = false;
  }
}

async function testConnection() {
  if (testing.value || saving.value) return;
  error.value = "";
  notice.value = "";
  if (!apiSupported.value) {
    setNotice("当前后端没有沙箱连接测试接口。连接测试必须由服务端执行，浏览器不会直接请求执行器。", "warning");
    return;
  }
  if (!loadedConfig.value) {
    setNotice("尚未保存沙箱配置，无法执行连接测试。", "warning");
    return;
  }
  if (isDirty.value) {
    setNotice("当前表单有未保存的更改。连接测试只验证已保存的服务端配置，请先保存。", "warning");
    return;
  }
  testing.value = true;
  try {
    const result = await adminApi.testSandboxConnection();
    setNotice(result.connected ? `连接测试返回 ${result.code}。这只表示本次探测结果。` : `连接测试返回 ${result.code}，请检查执行器和服务端网络。`, result.connected ? "success" : "warning");
    if (capability.value?.configuration) capability.value.configuration.lastConnectionTestStatus = result.code;
  } catch (failure) {
    setNotice(adminErrorMessage(failure, "测试沙箱连接"), "danger");
  } finally {
    testing.value = false;
  }
}

function keepDraftOnly() {
  const validation = validateBaseUrl();
  if (validation) {
    setNotice(`草稿未记录（VALIDATION_ERROR）。${validation}`, "danger");
    return;
  }
  draftSaved.value = true;
  setNotice("草稿仅保留在当前页面内，未写入浏览器存储，也未宣称生产服务已配置。", "neutral");
}

async function copySnippet() {
  if (!safeBaseUrl.value) {
    setNotice("请先填写不含凭据、查询参数或片段的有效服务地址，再复制部署片段。", "warning");
    return;
  }
  try {
    await navigator.clipboard.writeText(envSnippet.value);
    copied.value = true;
    setNotice("部署配置片段已复制。", "success");
  } catch {
    setNotice("浏览器未允许访问剪贴板，请手动复制下方配置片段。", "warning");
  }
}

function warnBeforeUnload(event: BeforeUnloadEvent | Event) {
  if (!isDirty.value) return;
  event.preventDefault();
  (event as BeforeUnloadEvent).returnValue = "";
}

onBeforeRouteLeave(() => !isDirty.value || window.confirm("沙箱配置还有未保存的更改，确定离开此页面吗？"));
onMounted(() => {
  window.addEventListener("beforeunload", warnBeforeUnload);
  void load();
});
onBeforeUnmount(() => window.removeEventListener("beforeunload", warnBeforeUnload));
</script>

<template>
  <AdminPageFrame title="沙箱配置" description="登记服务端代码执行器的地址和启用状态。浏览器不会直接向执行器发送代码，也不会保存执行器密钥。">
    <template #actions>
      <StatusBadge :label="configStatusLabel" :tone="configStatusTone" />
      <button class="button button--small" type="button" :disabled="loading || saving || testing" @click="reload">重新读取</button>
    </template>

    <LoadingState v-if="loading" label="正在读取沙箱状态…" />
    <ErrorState v-else-if="error && !capability" title="沙箱配置不可读取" :message="error"><RetryButton @retry="load" /></ErrorState>
    <template v-else>
      <section class="admin-hero-rail admin-panel admin-motion-enter sandbox-hero" aria-labelledby="sandbox-status-title">
        <span class="admin-hero-rail__index" aria-hidden="true"></span>
        <div class="admin-hero-rail__body">
          <div class="admin-hero-rail__heading">
            <div>
              <p class="admin-kicker">代码运行</p>
              <h2 id="sandbox-status-title">执行器状态</h2>
              <p>运行请求仍由 Spring 网关负责限流、超时和输出裁剪。</p>
            </div>
            <StatusBadge :label="runtimeLabel" :tone="runtimeConfigured ? 'success' : 'warning'" />
          </div>
          <div class="signal-strip" aria-label="沙箱状态摘要">
            <div class="signal-strip__item"><span class="signal-strip__label">运行时</span><strong>{{ runtimeLabel }}</strong></div>
            <div class="signal-strip__item"><span class="signal-strip__label">管理接口</span><strong>{{ apiSupported ? "可用" : "待接入" }}</strong></div>
            <div class="signal-strip__item"><span class="signal-strip__label">协议</span><strong>{{ capability?.configuration?.provider === 'JUDGE0' ? 'Judge0' : 'Piston-compatible' }}</strong></div>
            <div class="signal-strip__item"><span class="signal-strip__label">最近检查</span><strong>{{ formatDate(runtime?.checkedAt) }}</strong></div>
          </div>
        </div>
        <span class="admin-hero-rail__pulse" :aria-label="runtimeConfigured ? '代码运行服务已配置' : '代码运行服务待处理'"></span>
      </section>

      <InlineNotice v-if="capability?.reason" :message="reasonText(capability.reason)" :tone="capability.reason === 'SANDBOX_CONFIG_UNAVAILABLE' ? 'warning' : capability.reason === 'MASTER_KEY_UNAVAILABLE' ? 'danger' : 'neutral'" />
      <InlineNotice v-if="!runtime" message="暂时无法读取运行时健康状态。页面不会根据未知状态推断执行器可用。" tone="warning" />
      <InlineNotice v-if="isDirty" message="当前表单有未保存的更改。连接测试只验证已保存的服务端配置。" tone="warning" />
      <InlineNotice v-if="notice" :message="notice" :tone="noticeTone" />

      <section class="admin-panel admin-panel--focus panel-enter" aria-labelledby="sandbox-form-title">
        <div class="admin-panel__header">
          <div><p class="admin-kicker">配置表单</p><h2 id="sandbox-form-title">服务端执行器</h2><p>只接受经过审核的地址。保存动作是否可用，以当前后端接口状态为准。</p></div>
          <StatusBadge :label="form.enabled ? '启用' : '停用'" :tone="form.enabled ? 'success' : 'neutral'" />
        </div>
        <form class="admin-form sandbox-form" aria-label="沙箱配置表单" @submit.prevent="save">
          <div class="admin-form__grid">
            <label class="admin-field"><span>执行协议</span><RuntimeSelect v-model="form.provider" :options="providerOptions" ariaLabel="执行协议" /><small>地址必须与所选协议的 API 根路径匹配。</small></label>
            <label class="admin-field admin-field--full"><span>执行器地址（Base URL）</span><input v-model="form.baseUrl" type="url" inputmode="url" autocomplete="off" maxlength="2048" placeholder="https://sandbox.example" required /><small>不要填入 `/execute`、账号密码、查询参数或密钥。</small></label>
            <label class="admin-check sandbox-enabled"><input v-model="form.enabled" type="checkbox" /><span>允许服务端使用此配置</span></label>
          </div>
          <div class="admin-form__actions">
            <button class="button button--primary" type="submit" :disabled="saving || testing || !apiSupported">{{ saving ? "保存中…" : apiSupported ? "保存到服务端" : "服务端接口未就绪" }}</button>
            <button class="button" type="button" :disabled="saving || testing" @click="keepDraftOnly">{{ draftSaved ? "草稿已保留" : "仅保留当前草稿" }}</button>
            <button class="button" type="button" :disabled="saving || testing || !loadedConfig || isDirty" @click="testConnection">{{ testing ? "测试中…" : "测试已保存连接" }}</button>
          </div>
        </form>
      </section>

      <section class="admin-panel admin-panel--quiet panel-enter sandbox-handoff" style="--panel-delay: 90ms" aria-labelledby="sandbox-handoff-title">
        <div class="admin-panel__header"><div><p class="admin-kicker">部署交接</p><h2 id="sandbox-handoff-title">环境配置片段</h2><p>管理员配置会写入服务端数据库；若按环境文件启动，也可使用这段片段并按部署规范重启服务。</p></div><button class="button button--small" type="button" @click="copySnippet">{{ copied ? "已复制" : "复制片段" }}</button></div>
        <pre class="sandbox-snippet" aria-label="沙箱环境配置片段"><code>{{ envSnippet }}</code></pre>
        <div class="guardrail-grid sandbox-guardrails">
          <div><span>浏览器边界</span><strong>不直连执行器</strong><small>代码只发给同源 Spring 网关。</small></div>
          <div><span>协议边界</span><strong>{{ providerLabel }}</strong><small>地址必须与所选协议的 API 根路径匹配。</small></div>
          <div><span>生效边界</span><strong>服务端配置</strong><small>保存后由 Spring 网关读取；环境片段仍需重启后端。</small></div>
        </div>
      </section>
    </template>
  </AdminPageFrame>
</template>

<style scoped>
.sandbox-hero { --sandbox-delay: 0ms; }
.sandbox-hero .admin-hero-rail__heading > .badge {
  flex: 0 0 auto;
  white-space: nowrap;
}
.sandbox-form { max-width: 860px; }
.sandbox-enabled { grid-column: 1 / -1; }
.sandbox-handoff { overflow: hidden; }
.sandbox-snippet {
  margin: 18px 0 0;
  padding: 14px 16px;
  overflow-x: auto;
  border: 1px solid var(--admin-line);
  border-radius: 7px;
  background: color-mix(in srgb, var(--admin-panel-solid) 72%, var(--admin-canvas));
  color: var(--admin-ink);
  font-family: var(--font-mono, Consolas, monospace);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre;
}
.sandbox-snippet code { font: inherit; }
.sandbox-guardrails { margin-top: 16px; }

@media (max-width: 760px) {
  .sandbox-enabled { grid-column: auto; }
  .sandbox-snippet { font-size: 12px; }
}

@media (prefers-reduced-transparency: reduce) {
  .sandbox-snippet { background: var(--admin-panel-solid); }
}
</style>
