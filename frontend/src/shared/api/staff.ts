import { ApiClientError } from "./client";

/**
 * Error text and date formatting shared by the staff consoles (admin and teacher).
 *
 * These used to live in the admin module, which made the teacher console import from a place it does not
 * belong to. The rule behind the message format: every failure names the action, the server code, and the
 * request id, so a screenshot of the error is enough to find it in the logs.
 */
export function staffErrorMessage(error: unknown, action: string): string {
  const status = typeof error === "object" && error !== null && "status" in error ? Number((error as { status?: unknown }).status) : undefined;
  const code = error instanceof ApiClientError ? error.code : "NETWORK_ERROR";
  const requestId = error instanceof ApiClientError ? error.requestId : "";
  const guidance = status === 403
    ? "当前账号没有执行此操作的权限。"
    : status === 404
      ? "目标资源不存在或已被撤回。"
      : status === 409
        ? "服务端拒绝了当前状态变更，请刷新后重新确认。"
        : status === 429
          ? "请求受到频率限制，请稍后重试。"
          : status === 503
            ? "管理服务暂时不可用，请确认服务配置后重试。"
            : status && status >= 500
              ? "服务暂时异常，可以稍后重试。"
              : "请检查网络连接和输入后重试。";
  return `${action}未完成（${code}）。${guidance}${requestId ? ` 请求 ID：${requestId}` : ""}`;
}

/** A timestamp as the staff consoles show it; an absent value reads as "未记录", never as a raw null. */
export function formatDateTime(value?: string | null): string {
  if (!value) return "未记录";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}
