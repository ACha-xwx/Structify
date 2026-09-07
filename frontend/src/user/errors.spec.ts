import { describe, expect, it } from "vitest";
import { presentUserError } from "./errors";

describe("学习端错误呈现", () => {
  it("将未登录的耗费型操作提示为局部解锁，而非页面访问被拒绝", () => {
    const error = presentUserError({ status: 401, code: "AUTH_REQUIRED", message: "请先登录" });

    expect(error).toMatchObject({ kind: "permission", title: "登录后解锁此功能", retryable: false });
    expect(error.message).toContain("游客可以继续浏览");
    expect(error.message).toContain("在此处登录");
  });
});
