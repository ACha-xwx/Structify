import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";
import { compileStyle, parse } from "@vue/compiler-sfc";

const viewPath = path.resolve(__dirname, "AdminMailConfigView.vue");

describe("邮件配置页暗色 scoped 样式契约", () => {
  it("保留完整的主题和 mail-operations 选择器", () => {
    const source = fs.readFileSync(viewPath, "utf8");
    const parsed = parse(source, { filename: viewPath });
    const style = parsed.descriptor.styles.find((candidate) => candidate.scoped);

    expect(parsed.errors).toHaveLength(0);
    expect(style).toBeDefined();

    const compiled = compileStyle({
      filename: viewPath,
      id: "data-v-mail-config-contract",
      scoped: true,
      source: style?.content ?? "",
    });

    expect(compiled.errors).toHaveLength(0);
    expect(compiled.code).toContain('[data-theme="dark"] .mail-operations');
    expect(compiled.code).toContain('[data-theme="dark"] .mail-operations .mail-card');
    expect(compiled.code).toContain('[data-theme="dark"] .mail-operations .admin-field');
    expect(compiled.code).not.toMatch(/\[data-theme="dark"\]\s*\{[^}]*--mail-ink/s);
  });

  it("gives the subject and template editors intentional working space", () => {
    const source = fs.readFileSync(viewPath, "utf8");
    expect(source).toContain(".mail-template-field--body");
    expect(source).toContain(".mail-template-field .mail-template-textarea");
    expect(source).toMatch(/\.mail-template-field \.mail-template-textarea\s*\{[\s\S]*?min-height:\s*420px/);
    expect(source).toMatch(/\.mail-preview__frame\s*\{[\s\S]*?min-height:\s*420px/);
  });
});
