import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";

const stylesheetPath = path.resolve(__dirname, "admin.css");
const shellPath = path.resolve(__dirname, "../app/app-shell/AppShell.vue");

describe("管理端详情布局样式契约", () => {
  it("keeps selected-row details below the table on desktop", () => {
    const source = fs.readFileSync(stylesheetPath, "utf8");

    expect(source).toMatch(/\.app-frame--admin \.data-rail\s*\{\s*display:\s*block;/);
    expect(source).not.toMatch(/\.app-frame--admin \.data-rail\s*\{\s*display:\s*grid;\s*grid-template-columns:/);
    expect(source).toMatch(/\.app-frame--admin \.data-rail > \.admin-detail\s*\{[^}]*width:\s*100%/s);
  });

  it("keeps the desktop rail fixed and the requested logout treatment explicit", () => {
    const source = fs.readFileSync(stylesheetPath, "utf8");
    const shell = fs.readFileSync(shellPath, "utf8");

    expect(shell).toMatch(/\.admin-sidebar\s*\{[\s\S]*?position:\s*fixed;/);
    expect(shell).toContain("固定管理端导航");
    expect(source).toContain("#e90000");
    expect(source).toContain("#a80000");
  });
});
