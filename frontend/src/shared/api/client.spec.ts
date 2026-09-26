import { describe, expect, it, vi } from "vitest";
import { ApiClientError, createApiClient, parseSseStream } from "./client";

describe("API 客户端公共边界", () => {
  it("携带 credentials 和 request id，并保留 Spring 错误字段", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "AUTH_REQUIRED",
          message: "请先登录",
          requestId: "req-42",
          details: ["session expired"],
        }),
        {
          status: 401,
          headers: { "content-type": "application/json", "x-request-id": "req-42" },
        },
      ),
    );
    const client = createApiClient({ fetcher });

    await expect(client.request("/users/me")).rejects.toMatchObject({
      status: 401,
      code: "AUTH_REQUIRED",
      requestId: "req-42",
      details: ["session expired"],
    });
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/users/me",
      expect.objectContaining({
        credentials: "include",
        headers: expect.objectContaining({ "X-Request-Id": expect.any(String) }),
      }),
    );
  });

  it("区分 204 空响应和二进制响应", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(new Uint8Array([1, 2, 3]), { status: 200 }));
    const client = createApiClient({ fetcher });

    await expect(client.request("/auth/logout", { method: "POST" })).resolves.toMatchObject({
      kind: "empty",
      status: 204,
    });
    await expect(client.request("/resources/file", {}, { responseType: "binary" })).resolves.toMatchObject({
      kind: "binary",
      status: 200,
    });
  });

  it("cancels the reader when the stream abort signal fires", async () => {
    let canceled = false;
    const stream = new ReadableStream<Uint8Array>({
      cancel() {
        canceled = true;
      },
    });
    const controller = new AbortController();
    const events = parseSseStream(stream, { signal: controller.signal });

    const next = events.next();
    controller.abort();

    await expect(next).resolves.toEqual({ done: true, value: undefined });
    expect(canceled).toBe(true);
  });

  it("把一直不响应的请求报成 NETWORK_TIMEOUT，而不是永远等下去", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(
      (_url, init) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => reject(new Error("aborted")));
        }),
    );
    const client = createApiClient({ fetcher });

    await expect(client.request("/code/library", { timeoutMs: 10 })).rejects.toMatchObject({
      code: "NETWORK_TIMEOUT",
      status: 0,
    });
  });

  it("响应头到齐后停表：慢的响应体不会被超时掐断", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(
      () =>
        new Promise<Response>((resolve) => {
          setTimeout(
            () => resolve(new Response('{"ok":true}', { status: 200, headers: { "content-type": "application/json" } })),
            30,
          );
        }),
    );
    const client = createApiClient({ fetcher });

    await expect(client.request("/code/samples", { timeoutMs: 10 })).resolves.toMatchObject({
      kind: "json",
      status: 200,
    });
  });

  it("调用方自己的 abort 仍然按取消处理，不冒充超时", async () => {
    const controller = new AbortController();
    const fetcher = vi.fn<typeof fetch>().mockImplementation(
      (_url, init) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
        }),
    );
    const client = createApiClient({ fetcher });
    const pending = client.request("/code/library", { signal: controller.signal, timeoutMs: 5000 });

    controller.abort();
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
  });
});
