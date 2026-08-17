import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";

const adminCssPath = path.resolve(__dirname, "admin.css");

describe("管理端暗色主题表面契约", () => {
  it("直接覆盖所有统计项，而不是匹配不存在的内部 div", () => {
    const css = fs.readFileSync(adminCssPath, "utf8");

    expect(css).toContain('[data-theme="dark"] .signal-strip__item,');
    expect(css).toContain('[data-theme="dark"] .app-frame--admin .guardrail-grid > div,');
    expect(css).toContain('[data-theme="dark"] .app-frame--admin .admin-inspector__summary > div,');
    expect(css).toContain('[data-theme="dark"] .admin-signal-grid > div');
    expect(css).not.toContain(".signal-strip__item div");
  });
});
