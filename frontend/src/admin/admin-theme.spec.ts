import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";

describe("管理端主题样式边界", () => {
  const css = fs.readFileSync(path.resolve(__dirname, "admin.css"), "utf8");
  const mailView = fs.readFileSync(path.resolve(__dirname, "views/AdminMailConfigView.vue"), "utf8");

  it("在暗色主题下覆盖所有统计摘要子卡片", () => {
    expect(css).toContain('[data-theme="dark"] .admin-signal-grid > div');
    expect(css).toContain('[data-theme="dark"] .admin-inspector__summary > div');
  });

  it("为邮件工作区保留最后一层暗色主题权威规则", () => {
    expect(mailView).toContain("/* Final dark theme authority for the admin mail workspace. */");
    expect(mailView).toContain(':global([data-theme="dark"] .mail-operations .admin-field :is(input, select, textarea))');
    expect(mailView).toContain(':global([data-theme="dark"] .mail-operations .mail-preview__frame)');
  });
});
