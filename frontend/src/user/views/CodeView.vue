<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import { auth } from "../../app/providers/runtime";
import type { AiReadiness, CodeAnalysisResponse, CodeLanguage, CodeRunRequest, CodeRunResponse } from "../../shared/types/contracts";
import { useLocale } from "../../shared/i18n/locale";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { presentUserError, type UserErrorPresentation } from "../errors";
import { userApi } from "../runtime";
import { createLoginTarget } from "../login-target";
import RuntimeSelect, { type RuntimeSelectOption } from "../../shared/components/RuntimeSelect.vue";

const route = useRoute();
const { isEnglish } = useLocale();
const chapterId = computed(() => String(route.query.chapterId || ""));
const language = ref<CodeLanguage>("c");
const languageOptions: RuntimeSelectOption[] = [
  { value: "c", label: "C" },
  { value: "python", label: "Python" },
];
const code = ref("#include <stdio.h>\n\nint main(void) {\n  printf(\"hello, data structure!\\n\");\n  return 0;\n}\n");
const stdin = ref("");
const result = ref<CodeRunResponse | null>(null);
const lastRun = ref<CodeRunRequest | null>(null);
const analysis = ref<CodeAnalysisResponse | null>(null);
type CodePanel = "input" | "output" | "diagnostics" | "analysis";
const activePanel = ref<CodePanel>("output");
const running = ref(false);
const analyzing = ref(false);
const runError = ref<UserErrorPresentation | null>(null);
const analysisError = ref<UserErrorPresentation | null>(null);
const isAuthenticated = computed(() => Boolean(auth.state.user));
const loginTarget = computed(() => createLoginTarget(route.fullPath));

function defaultCode(nextLanguage: CodeLanguage): string {
  return nextLanguage === "python"
    ? "print('hello, data structure!')\n"
    : "#include <stdio.h>\n\nint main(void) {\n  printf(\"hello, data structure!\\n\");\n  return 0;\n}\n";
}

const copy = computed(() => isEnglish.value ? {
  eyebrow: "C / Python",
  title: "Code lab",
  introAuthenticated: "Edit source, feed stdin, run in an isolated sandbox, and inspect output, diagnostics, and AI review in place.",
  introGuest: "Guests can edit source and run code directly. Sign in only when you want AI review.",
  returnToCourse: "Back to course",
  workspace: "Workspace",
  sourceFile: "Source file",
  language: "Language",
  learningScope: "Learning scope",
  currentCourse: "Current course",
  allCourses: "All courses",
  codeEditor: "Code editor",
  console: "Console",
  fileName: "File",
  unsaved: "Local changes",
  ready: "Ready",
  reset: "Reset editor",
  runShortcut: "Run · Ctrl / Cmd + Enter",
  panels: "Console panels",
  inputPanel: "Input",
  outputPanel: "Output",
  diagnosticsPanel: "Diagnostics",
  analysisPanel: "AI review",
  stdinPanel: "Standard input",
  noDiagnostics: "No diagnostics for this run.",
  resultNeedsAttention: "The sandbox returned a non-success status. Review the output above.",
  editorHint: "Ctrl / Cmd + Enter runs the current file.",
  analysisHint: "Ask the model to review the latest run.",
  standardInput: "Standard input",
  optional: "Optional",
  running: "Running...",
  runCode: "Run code",
  analyzing: "Analyzing...",
  analyzeResult: "Analyze result",
  signInToAnalyze: "Sign in to analyze",
  runResult: "Run result",
  notRun: "Not run yet",
  runningCode: "Running code",
  runningDetail: "The code is being submitted to the isolated sandbox.",
  emptyCodeTitle: "No code to run",
  emptyCodeDetail: "Enter C or Python code before running it.",
  retryRun: "Run again",
  signIn: "Sign in",
  duration: "Duration",
  standardOutput: "Standard output",
  standardError: "Error output",
  noOutput: "(no output)",
  noErrorOutput: "(no error output)",
  resultEmptyTitle: "No code has run yet",
  resultEmptyDetail: "Select a language and run the code to view the sandbox output here.",
  analysis: "Code analysis",
  checkingAnalysis: "Checking code analysis",
  checkingAnalysisDetail: "Checking the account, model, and quota state.",
  signInAnalysisTitle: "Sign in to unlock code analysis",
  signInAnalysisDetail: "Code analysis uses a model service. Sign in before submitting it.",
  analysisBlockedTitle: "Code analysis cannot start yet",
  retryAnalysis: "Analyze again",
  analysisResult: "Analysis result",
  analysisEmptyTitle: "No code analysis requested",
  analysisEmptyDetail: "After signing in, analysis follows the current model access and quota.",
  execution: "Execution",
  executionAuthenticated: "Code runs in an isolated sandbox.",
  executionGuest: "Guests can run code directly; sign in to unlock AI analysis.",
  analysisCapability: "Analysis",
  analysisAuthenticated: "Analysis follows the current model access and quota.",
  analysisGuest: "Sign in to unlock code analysis.",
  modelUnavailable: "The code-analysis model is currently unavailable. Please try again later.",
  quotaUnconfigured: "The code-analysis quota is not configured yet, so analysis cannot be submitted.",
  quotaExhausted: "Today's code-analysis quota has been used. Please try again later.",
  quotaLimited: "The code-analysis service is handling another request. Please try again shortly.",
  readinessNotMet: "The current code-analysis conditions are not met. Please try again later.",
  missingSnapshot: "The code snapshot for this run is unavailable.",
  statusLabels: { success: "Run completed", compile_error: "Compilation failed", runtime_error: "Run failed" },
  permissionTitle: "Sign in to use the code lab",
  permissionDetail: "Guests can run code directly. Sign in to unlock model analysis.",
  resourceUnavailableTitle: "This code run is not available",
  resourceUnavailableDetail: "The requested code run may no longer be available.",
  stateChangedTitle: "This code state has changed",
  stateChangedDetail: "Refresh and try again to keep the latest result.",
  requestLimitedTitle: "Please wait before trying again",
  requestLimitedDetail: "The service is temporarily limiting requests.",
  serviceUnavailableTitle: "Code services are temporarily unavailable",
  serviceUnavailableDetail: "Your current code remains in the editor. Try again once the service recovers.",
  compilerNotConfiguredTitle: "Code execution is temporarily unavailable",
  compilerNotConfiguredDetail: "The execution service is not connected yet. Your code remains in the editor; try again after the service is ready.",
  runAccessDetail: "The code execution service rejected this guest request. Try again later.",
  timeoutTitle: "The request timed out",
  timeoutDetail: "The sandbox did not respond in time.",
  networkTitle: "Network connection is unavailable",
  networkDetail: "Check the connection and try again. Your code remains in the editor.",
  validationTitle: "This code cannot be processed",
  validationDetail: "Check the code and try again.",
  unknownTitle: "This action was not completed",
  unknownDetail: "The service returned an unexpected result. Please try again.",
} : {
  eyebrow: "C / Python",
  title: "编译器工作台",
  introAuthenticated: "在隔离沙箱里编辑源代码、填写标准输入、运行程序，并就地查看输出、诊断和智能分析。",
  introGuest: "游客可以直接编辑并运行代码，只有需要智能分析时才登录。",
  returnToCourse: "返回章节",
  workspace: "工作区",
  sourceFile: "源文件",
  language: "语言",
  learningScope: "学习范围",
  currentCourse: "当前章节",
  allCourses: "全部课程",
  codeEditor: "代码编辑器",
  console: "控制台",
  fileName: "文件",
  unsaved: "本地改动",
  ready: "就绪",
  reset: "重置编辑器",
  runShortcut: "运行 · Ctrl / Cmd + Enter",
  panels: "控制台面板",
  inputPanel: "输入",
  outputPanel: "输出",
  diagnosticsPanel: "诊断",
  analysisPanel: "智能分析",
  stdinPanel: "标准输入",
  noDiagnostics: "本次运行没有诊断信息。",
  resultNeedsAttention: "沙箱返回了非成功状态，请查看上方输出。",
  editorHint: "Ctrl / Cmd + Enter 运行当前文件。",
  analysisHint: "请求模型检查最近一次运行。",
  standardInput: "标准输入",
  optional: "可选",
  running: "运行中…",
  runCode: "运行代码",
  analyzing: "分析中…",
  analyzeResult: "分析结果",
  signInToAnalyze: "登录后分析",
  runResult: "运行结果",
  notRun: "尚未运行",
  runningCode: "正在运行代码",
  runningDetail: "代码正在提交到隔离沙箱。",
  emptyCodeTitle: "代码为空",
  emptyCodeDetail: "请输入要运行的 C 或 Python 代码。",
  retryRun: "重新运行",
  signIn: "前往登录",
  duration: "耗时",
  standardOutput: "标准输出",
  standardError: "错误输出",
  noOutput: "（无输出）",
  noErrorOutput: "（无错误输出）",
  resultEmptyTitle: "尚未运行代码",
  resultEmptyDetail: "选择语言并提交代码后，这里显示沙箱真实输出。",
  analysis: "代码分析",
  checkingAnalysis: "正在检查并分析代码",
  checkingAnalysisDetail: "正在确认账号、模型和配额状态。",
  signInAnalysisTitle: "登录后解锁代码分析",
  signInAnalysisDetail: "代码分析会调用模型服务；登录后才能提交分析。",
  analysisBlockedTitle: "当前不能开始代码分析",
  retryAnalysis: "重新分析",
  analysisResult: "分析结果",
  analysisEmptyTitle: "尚未请求代码分析",
  analysisEmptyDetail: "登录后会按当前模型权限与额度执行分析。",
  execution: "执行方式",
  executionAuthenticated: "代码会在安全沙箱中执行。",
  executionGuest: "游客可直接运行代码，登录后解锁智能分析。",
  analysisCapability: "分析能力",
  analysisAuthenticated: "分析会按当前模型权限与额度执行。",
  analysisGuest: "登录后解锁代码分析。",
  modelUnavailable: "代码分析模型当前不可用，请稍后重试。",
  quotaUnconfigured: "代码分析配额尚未配置，暂时不能提交分析。",
  quotaExhausted: "今日代码分析配额已用尽，请稍后再试。",
  quotaLimited: "代码分析服务正在处理其他请求，请稍后重试。",
  readinessNotMet: "当前不满足代码分析条件，请稍后重试。",
  missingSnapshot: "缺少与本次运行对应的代码快照",
  statusLabels: { success: "运行成功", compile_error: "编译失败", runtime_error: "运行失败" },
  permissionTitle: "登录后使用代码实验",
  permissionDetail: "游客可直接运行代码，登录后解锁智能分析。",
  resourceUnavailableTitle: "本次代码运行不可访问",
  resourceUnavailableDetail: "请求的代码运行记录可能已不可用。",
  stateChangedTitle: "代码状态已变化",
  stateChangedDetail: "请刷新后继续操作，以保留最新结果。",
  requestLimitedTitle: "请求过于频繁",
  requestLimitedDetail: "服务暂时限制了请求，请稍后重试。",
  serviceUnavailableTitle: "代码服务暂不可用",
  serviceUnavailableDetail: "当前代码仍保留在编辑器中，服务恢复后可再次尝试。",
  compilerNotConfiguredTitle: "代码执行暂不可用",
  compilerNotConfiguredDetail: "代码执行服务尚未连接，当前代码已保留在编辑器中。服务恢复后可以重新运行。",
  runAccessDetail: "代码执行服务拒绝了本次游客请求，请稍后重试。",
  timeoutTitle: "请求超时",
  timeoutDetail: "沙箱未在规定时间内响应。",
  networkTitle: "网络连接不可用",
  networkDetail: "请检查网络后重试，当前代码会保留在编辑器中。",
  validationTitle: "代码无法处理",
  validationDetail: "请检查代码后重试。",
  unknownTitle: "操作未完成",
  unknownDetail: "服务返回了未预期的结果，请稍后重试。",
});

const busy = computed(() => running.value || analyzing.value);
const analysisDisabled = computed(() => busy.value || (isAuthenticated.value && !result.value));
const statusLabel = computed(() => running.value ? copy.value.running : result.value ? copy.value.statusLabels[result.value.status] : copy.value.notRun);
const displayedRunError = computed(() => runError.value ? localizedUserError(runError.value) : null);
const displayedAnalysisError = computed(() => analysisError.value ? localizedUserError(analysisError.value) : null);
const fileName = computed(() => language.value === "c" ? "main.c" : "main.py");
const codeLines = computed(() => code.value.split("\n"));
const codeLineCount = computed(() => Math.max(codeLines.value.length, 1));
const isDirty = computed(() => code.value !== defaultCode(language.value) || Boolean(stdin.value));
const editorStateLabel = computed(() => running.value ? copy.value.running : isDirty.value ? copy.value.unsaved : copy.value.ready);
const diagnosticsText = computed(() => {
  if (displayedRunError.value) return displayedRunError.value.message;
  if (!result.value) return copy.value.noDiagnostics;
  if (result.value.stderr) return result.value.stderr;
  if (result.value.status === "success") return copy.value.noDiagnostics;
  return copy.value.resultNeedsAttention;
});
const panels = computed(() => [
  { id: "input" as const, label: copy.value.inputPanel },
  { id: "output" as const, label: copy.value.outputPanel },
  { id: "diagnostics" as const, label: copy.value.diagnosticsPanel },
  { id: "analysis" as const, label: copy.value.analysisPanel },
]);

function clearRunState(): void {
  result.value = null;
  lastRun.value = null;
  analysis.value = null;
  runError.value = null;
  analysisError.value = null;
}

function changeLanguage(): void {
  if (busy.value) return;
  clearRunState();
  code.value = defaultCode(language.value);
  stdin.value = "";
  activePanel.value = "output";
}

function resetEditor(): void {
  if (busy.value) return;
  code.value = defaultCode(language.value);
  stdin.value = "";
  clearRunState();
  activePanel.value = "output";
}

function selectPanel(panel: CodePanel): void {
  if (panel === "analysis" && !result.value && !analysisError.value && isAuthenticated.value) return;
  activePanel.value = panel;
}

function movePanel(panel: CodePanel, direction: 1 | -1): void {
  const currentIndex = panels.value.findIndex((item) => item.id === panel);
  const nextIndex = (currentIndex + direction + panels.value.length) % panels.value.length;
  const nextPanel = panels.value[nextIndex].id;
  if (nextPanel === "analysis" && !result.value && !analysisError.value && isAuthenticated.value) return;
  activePanel.value = nextPanel;
}

function handlePanelKeydown(event: KeyboardEvent, panel: CodePanel): void {
  if (event.key === "ArrowRight" || event.key === "ArrowDown") {
    event.preventDefault();
    movePanel(panel, 1);
  } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
    event.preventDefault();
    movePanel(panel, -1);
  }
}

function invalidateRun(): void {
  if (!running.value) clearRunState();
}

function runRequest(): CodeRunRequest {
  return {
    language: language.value,
    code: code.value,
    ...(stdin.value ? { stdin: stdin.value } : {}),
    ...(chapterId.value ? { chapterId: chapterId.value } : {}),
  };
}

function analysisBlockedMessage(readiness: AiReadiness): string {
  if (!readiness.modelAvailable) return copy.value.modelUnavailable;
  if (readiness.quotaStatus === "NOT_CONFIGURED") return copy.value.quotaUnconfigured;
  if (readiness.quotaStatus === "EXHAUSTED") return copy.value.quotaExhausted;
  if (readiness.quotaStatus === "CONCURRENCY_LIMITED") return copy.value.quotaLimited;
  return copy.value.readinessNotMet;
}

function analysisRequest(currentResult: CodeRunResponse): Parameters<typeof userApi.analyzeCode>[0] {
  if (currentResult.runId) return { runId: currentResult.runId };
  const request = lastRun.value;
  if (!request) throw new Error(copy.value.missingSnapshot);
  return {
    runId: null,
    language: request.language,
    code: request.code,
    ...(request.stdin ? { stdin: request.stdin } : {}),
    stdout: currentResult.stdout,
    stderr: currentResult.stderr,
    status: currentResult.status,
    ...(request.chapterId ? { chapterId: request.chapterId } : {}),
  };
}

async function run(): Promise<void> {
  if (busy.value) return;
  activePanel.value = "output";
  clearRunState();
  const request = runRequest();
  if (!request.code.trim()) {
    runError.value = { kind: "validation", title: copy.value.emptyCodeTitle, message: copy.value.emptyCodeDetail, retryable: false };
    return;
  }
  running.value = true;
  try {
    result.value = await userApi.runCode(request);
    lastRun.value = request;
  } catch (cause) {
    runError.value = presentCodeRunError(cause);
  } finally {
    running.value = false;
  }
}

async function analyze(): Promise<void> {
  if (busy.value) return;
  activePanel.value = "analysis";
  if (!isAuthenticated.value) {
    analysisError.value = { kind: "permission", title: copy.value.signInAnalysisTitle, message: copy.value.signInAnalysisDetail, retryable: false };
    return;
  }
  const currentResult = result.value;
  if (!currentResult) return;
  analysis.value = null;
  analysisError.value = null;
  analyzing.value = true;
  try {
    const readiness = await userApi.getReadiness({ operation: "CODE_ANALYSIS", chapterId: chapterId.value || undefined });
    if (!readiness.allowFormalGeneration) {
      analysisError.value = { kind: "service", title: copy.value.analysisBlockedTitle, message: analysisBlockedMessage(readiness), retryable: true };
      return;
    }
    analysis.value = await userApi.analyzeCode(analysisRequest(currentResult));
  } catch (cause) {
    analysisError.value = presentUserError(cause);
  } finally {
    analyzing.value = false;
  }
}

function localizedUserError(error: UserErrorPresentation): UserErrorPresentation {
  if (error.code === "COMPILER_NOT_CONFIGURED") {
    return {
      ...error,
      title: copy.value.compilerNotConfiguredTitle,
      message: copy.value.compilerNotConfiguredDetail,
    };
  }
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

function presentCodeRunError(cause: unknown): UserErrorPresentation {
  const code = (cause as { code?: unknown } | undefined)?.code;
  if (code === "COMPILER_NOT_CONFIGURED") {
    return {
      kind: "service",
      title: copy.value.compilerNotConfiguredTitle,
      message: copy.value.compilerNotConfiguredDetail,
      retryable: false,
      code: "COMPILER_NOT_CONFIGURED",
    };
  }
  const status = (cause as { status?: unknown } | undefined)?.status;
  if (status === 401 || status === 403 || status === 500) {
    return {
      kind: "service",
      title: copy.value.serviceUnavailableTitle,
      message: status === 401 || status === 403 ? copy.value.runAccessDetail : copy.value.serviceUnavailableDetail,
      retryable: true,
    };
  }
  return presentUserError(cause);
}
</script>

<template>
  <UserFrame shell="course">
    <section class="user-page code-workbench" aria-labelledby="code-title">
      <header class="code-workbench__heading">
        <div>
          <p class="user-page__eyebrow">{{ copy.eyebrow }}</p>
          <h1 id="code-title">{{ copy.title }}</h1>
          <p class="user-page__intro">{{ isAuthenticated ? copy.introAuthenticated : copy.introGuest }}</p>
        </div>
        <RouterLink class="user-action" :to="chapterId ? `/user/chapters/${chapterId}` : '/user/chapters'">{{ copy.returnToCourse }}</RouterLink>
      </header>

      <form class="code-workbench__surface" @submit.prevent="run">
        <div class="code-workbench__main">
          <div class="code-workbench__workspace-bar" :aria-label="copy.workspace">
            <div class="code-workbench__file">
              <span class="code-workbench__file-icon" aria-hidden="true">{ }</span>
              <div><strong>{{ fileName }}</strong><small>{{ copy.learningScope }} · {{ chapterId ? copy.currentCourse : copy.allCourses }}</small></div>
            </div>
            <div class="code-workbench__workspace-controls">
              <label class="code-workbench__language"><span class="sr-only">{{ copy.language }}</span><RuntimeSelect v-model="language" :options="languageOptions" :ariaLabel="copy.language" :disabled="busy" test-id="code-language" @change="changeLanguage" /></label>
              <span class="code-workbench__state" :data-dirty="String(isDirty)"><i aria-hidden="true"></i>{{ editorStateLabel }}</span>
              <button class="code-icon-action" type="button" :disabled="busy" :aria-label="copy.reset" :title="copy.reset" @click="resetEditor">↺</button>
            </div>
          </div>

          <div class="code-workbench__editor-shell">
            <div class="code-workbench__editor-toolbar"><span>{{ copy.codeEditor }}</span><span>{{ copy.editorHint }}</span><kbd>⌘ ↵</kbd></div>
            <div class="code-workbench__editor">
              <div class="code-workbench__gutter" aria-hidden="true"><span v-for="line in codeLineCount" :key="line" :class="{ 'is-active': line === 1 }">{{ String(line).padStart(2, "0") }}</span></div>
              <textarea v-model="code" class="user-code-editor" wrap="off" spellcheck="false" autocapitalize="off" autocomplete="off" autocorrect="off" :aria-label="copy.codeEditor" :disabled="busy" @keydown.ctrl.enter.prevent="run" @keydown.meta.enter.prevent="run" @input="invalidateRun"></textarea>
            </div>
          </div>

          <div class="code-workbench__console-bar">
            <div>
              <p class="code-workbench__console-eyebrow">{{ copy.console }}</p>
              <div class="code-workbench__tabs" role="tablist" :aria-label="copy.panels">
                <button v-for="panel in panels" :key="panel.id" type="button" role="tab" :aria-selected="activePanel === panel.id" :tabindex="activePanel === panel.id ? 0 : -1" :disabled="panel.id === 'analysis' && !result && !analysisError && isAuthenticated" @keydown="handlePanelKeydown($event, panel.id)" @click="selectPanel(panel.id)">{{ panel.label }}<span v-if="panel.id === 'output' && result" class="code-workbench__tab-dot" aria-hidden="true"></span></button>
              </div>
            </div>
            <div class="code-workbench__run-actions">
              <span class="code-workbench__shortcut">{{ copy.runShortcut }}</span>
              <button class="user-action user-action--primary" type="submit" :disabled="busy">{{ running ? copy.running : copy.runCode }}</button>
              <button class="user-action" data-testid="code-analyze" type="button" :disabled="analysisDisabled" @click="analyze">{{ analyzing ? copy.analyzing : (isAuthenticated ? copy.analyzeResult : copy.signInToAnalyze) }}</button>
            </div>
          </div>

          <section class="code-workbench__panel" aria-live="polite">
            <div v-if="activePanel === 'input'" class="code-panel-content">
              <header><div><p class="code-panel-content__eyebrow">STDIN</p><h2>{{ copy.stdinPanel }}</h2></div><span>{{ copy.optional }}</span></header>
              <textarea v-model="stdin" class="user-code-stdin" wrap="off" :placeholder="copy.optional" :disabled="busy" @input="invalidateRun"></textarea>
            </div>
            <div v-else-if="activePanel === 'diagnostics'" class="code-panel-content">
              <header><div><p class="code-panel-content__eyebrow">CHECKS</p><h2>{{ copy.diagnosticsPanel }}</h2></div><span>{{ result ? statusLabel : copy.notRun }}</span></header>
              <pre class="code-panel-output" data-testid="code-diagnostics">{{ diagnosticsText }}</pre>
            </div>
            <div v-else-if="activePanel === 'analysis'" class="code-panel-content">
              <header><div><p class="code-panel-content__eyebrow">MODEL REVIEW</p><h2 id="analysis-title">{{ copy.analysis }}</h2></div><span>{{ copy.analysisHint }}</span></header>
              <UserState v-if="analyzing" data-testid="code-analysis-state" mode="loading" :title="copy.checkingAnalysis" :message="copy.checkingAnalysisDetail" />
              <UserState v-else-if="displayedAnalysisError" data-testid="code-analysis-state" :mode="displayedAnalysisError.kind === 'permission' ? 'permission' : 'error'" :title="displayedAnalysisError.title" :message="displayedAnalysisError.message" :retry-label="displayedAnalysisError.retryable ? copy.retryAnalysis : undefined" @retry="analyze"><RouterLink v-if="displayedAnalysisError.kind === 'permission'" class="user-action user-action--primary" :to="loginTarget">{{ copy.signIn }}</RouterLink></UserState>
              <section v-else-if="analysis" class="code-analysis-result" data-testid="code-analysis"><h3>{{ copy.analysisResult }}</h3><p class="user-prewrap">{{ analysis.analysis }}</p></section>
              <UserState v-else data-testid="code-analysis-state" mode="empty" :title="copy.analysisEmptyTitle" :message="copy.analysisEmptyDetail" />
            </div>
            <div v-else class="code-panel-content">
              <header><div><p class="code-panel-content__eyebrow">TERMINAL</p><h2>{{ copy.runResult }}</h2></div><span :data-status="result?.status || 'idle'">{{ statusLabel }}</span></header>
              <UserState v-if="running" data-testid="code-result-state" mode="loading" :title="copy.runningCode" :message="copy.runningDetail" />
              <UserState v-else-if="displayedRunError" data-testid="code-result-state" :mode="displayedRunError.kind === 'permission' ? 'permission' : 'error'" :title="displayedRunError.title" :message="displayedRunError.message" :retry-label="displayedRunError.retryable ? copy.retryRun : undefined" @retry="run" />
              <div v-else-if="result" data-testid="code-run-result" class="code-output-grid"><div><span>{{ copy.standardOutput }}</span><pre>{{ result.stdout || copy.noOutput }}</pre></div><div><span>{{ copy.standardError }}</span><pre>{{ result.stderr || copy.noErrorOutput }}</pre></div><p>{{ copy.duration }} {{ result.durationMs }} ms · {{ result.language === 'c' ? 'C' : 'Python' }}</p></div>
              <UserState v-else data-testid="code-result-state" mode="empty" :title="copy.resultEmptyTitle" :message="copy.resultEmptyDetail" />
            </div>
          </section>
        </div>
      </form>
    </section>

    <template #rail>
      <div class="user-rail-list">
        <strong>{{ copy.execution }}</strong>
        <p>{{ isAuthenticated ? copy.executionAuthenticated : copy.executionGuest }}</p>
        <strong>{{ copy.analysisCapability }}</strong>
        <p>{{ isAuthenticated ? copy.analysisAuthenticated : copy.analysisGuest }}</p>
      </div>
    </template>
  </UserFrame>
</template>

<style scoped>
.code-workbench {
  display: grid;
  gap: 1.15rem;
}

.code-workbench__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: .35rem;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 80%, transparent);
}

.code-workbench__heading .user-page__intro {
  max-width: 68ch;
}

.code-workbench__surface {
  display: block;
  min-width: 0;
  padding: 0;
  border: 1px solid color-mix(in srgb, var(--line) 90%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--surface) 92%, transparent);
  box-shadow: var(--shadow-sm);
}

.code-workbench__main {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
}

.code-workbench__workspace-bar {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: .7rem 1rem;
  padding: .62rem .85rem;
  border-bottom: 1px solid var(--line);
  background: var(--surface-subtle);
}

.code-workbench__workspace-controls {
  display: flex;
  min-width: 0;
  flex: 0 1 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: .55rem;
}

.code-workbench__console-bar {
  display: flex;
  min-width: 0;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  padding: .7rem .85rem;
  border-top: 1px solid var(--line);
  background: var(--surface-subtle);
}

.code-workbench__console-bar > div:first-child {
  display: grid;
  min-width: 0;
  gap: .35rem;
}

.code-workbench__console-eyebrow {
  margin: 0;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: .62rem;
  letter-spacing: .08em;
  text-transform: uppercase;
}

.code-workbench__bar,
.code-workbench__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: .75rem;
}

.code-workbench__bar {
  padding-bottom: .4rem;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 72%, transparent);
}

.code-workbench__file {
  display: inline-flex;
  align-items: center;
  gap: .75rem;
  min-width: 0;
}

.code-workbench__file-icon {
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  flex: 0 0 2.25rem;
  place-items: center;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: var(--surface-subtle);
  color: var(--text);
  font-family: var(--font-mono);
  font-size: .7rem;
}

.code-workbench__file > div {
  display: flex;
  min-width: 0;
  align-items: baseline;
  flex-wrap: wrap;
  column-gap: .45rem;
}

.code-workbench__file strong,
.code-workbench__file small {
  display: block;
  min-width: 0;
}

.code-workbench__file strong {
  font-size: .92rem;
  font-weight: 650;
}

.code-workbench__file small,
.code-workbench__shortcut {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: .72rem;
}

@media (min-width: 761px) {
  .code-workbench__file > div {
    flex-wrap: nowrap;
  }
}

.code-workbench__bar-actions,
.code-workbench__run-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: .55rem;
}

.code-workbench__workspace-controls .code-workbench__language .runtime-select {
  min-width: 5.3rem;
}

.code-workbench__language .runtime-select {
  min-width: 6.4rem;
}

.code-workbench__state {
  display: inline-flex;
  align-items: center;
  gap: .42rem;
  min-height: 2.55rem;
  padding: 0 .75rem;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface-subtle);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: .72rem;
  white-space: nowrap;
}

.code-workbench__state i {
  width: .45rem;
  height: .45rem;
  border-radius: 50%;
  background: var(--text-muted);
}

.code-workbench__state[data-dirty="true"] {
  border-color: color-mix(in srgb, var(--accent) 40%, var(--line));
  color: var(--text);
}

.code-workbench__state[data-dirty="true"] i {
  background: var(--accent);
}

.code-icon-action {
  display: inline-grid;
  width: 2.55rem;
  height: 2.55rem;
  place-items: center;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
  cursor: pointer;
}

.code-workbench__editor-shell {
  display: grid;
  gap: .7rem;
}

.code-workbench__editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: .75rem;
  color: var(--text-muted);
  font-size: .78rem;
}

.code-workbench__editor-toolbar kbd {
  padding: .18rem .4rem;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface-subtle);
  color: var(--text);
  font-family: var(--font-mono);
  font-size: .68rem;
}

.code-workbench__editor {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  min-height: 24rem;
  overflow: clip;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.code-workbench__gutter {
  display: grid;
  align-content: start;
  min-width: 3.2rem;
  padding: .95rem .55rem .95rem .7rem;
  border-right: 1px solid var(--line);
  background: var(--surface-subtle);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: .74rem;
  line-height: 1.6;
  text-align: right;
}

.code-workbench__gutter span.is-active {
  color: var(--text);
  font-weight: 700;
}

.code-workbench .user-code-editor {
  min-height: 24rem !important;
  width: 100%;
  padding: .95rem 1rem !important;
  border: 0;
  border-radius: 0;
  background: var(--surface);
  color: var(--text);
  font-family: var(--font-mono);
  font-size: .84rem;
  line-height: 1.62;
  resize: vertical;
}

.code-workbench .user-code-editor:focus {
  box-shadow: none;
}

.code-workbench__tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: .45rem;
}

.code-workbench__tabs button {
  min-height: 2.55rem;
  padding: 0 .9rem;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
}

.code-workbench__tabs button[aria-selected="true"] {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--text);
  font-weight: 700;
}

.code-workbench__tab-dot {
  display: inline-block;
  width: .45rem;
  height: .45rem;
  margin-left: .45rem;
  border-radius: 50%;
  background: var(--accent);
}

.code-workbench__panel {
  padding-top: .25rem;
}

.code-panel-content {
  display: grid;
  gap: .85rem;
  padding: 1rem;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.code-panel-content > header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: .8rem;
}

.code-panel-content h2,
.code-panel-content h3,
.code-panel-content p {
  margin: 0;
}

.code-panel-content h2 {
  font-size: .98rem;
}

.code-panel-content__eyebrow {
  margin: 0 0 .18rem;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: .68rem;
  letter-spacing: .08em;
}

.code-panel-content > header > span,
.code-panel-content > header > span[data-status] {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: .72rem;
  text-align: right;
}

.code-panel-output,
.code-analysis-result {
  margin: 0;
  padding: .9rem 1rem;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-subtle);
}

.code-panel-output {
  min-height: 8rem;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: var(--font-mono);
}

.code-output-grid {
  display: grid;
  gap: .85rem;
}

.code-output-grid > div {
  display: grid;
  gap: .4rem;
}

.code-output-grid > div > span {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: .72rem;
}

.code-output-grid pre {
  min-height: 5rem;
  margin: 0;
  padding: .9rem 1rem;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-subtle);
  font-family: var(--font-mono);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.code-output-grid p {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: .72rem;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

@media (max-width: 920px) {
  .code-workbench__heading,
  .code-workbench__bar,
  .code-workbench__footer,
  .code-workbench__console-bar {
    align-items: flex-start;
  }

  .code-workbench__console-bar {
    flex-wrap: wrap;
  }
}

@media (max-width: 760px) {
  .code-workbench__surface {
    padding: 0;
  }

  .code-workbench__main {
    grid-template-rows: auto auto auto minmax(0, 1fr);
  }

  .code-workbench__workspace-bar {
    align-items: flex-start;
  }

  .code-workbench__workspace-controls {
    width: 100%;
    justify-content: flex-start;
  }

  .code-workbench__editor {
    grid-template-columns: 1fr;
  }

  .code-workbench__gutter {
    display: none;
  }

  .code-workbench .user-code-editor {
    min-height: 18rem !important;
  }

  .code-workbench__run-actions {
    width: 100%;
  }

  .code-workbench__tabs,
  .code-workbench__run-actions {
    width: 100%;
  }

  .code-workbench__run-actions .user-action,
  .code-workbench__run-actions .code-workbench__shortcut {
    flex: 1 1 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .code-workbench__tabs button,
  .code-icon-action {
    transition: none;
  }
}
</style>
