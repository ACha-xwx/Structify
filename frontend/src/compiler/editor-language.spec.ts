import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { diagnoseC } from "./editor-language";

const diagnose = (code: string) => diagnoseC(code, (key) => key);

describe("C syntax diagnostics", () => {
  it("accepts declarations, nested brackets, loops and a valid program", () => {
    expect(diagnose('#include <stdio.h>\nint main(void) {\n  int a[2] = {1, 2};\n  for (int i = 0; i < 2; i++) { printf("%d", a[i]); }\n  return 0;\n}\n')).toEqual([]);
  });

  it("points to the preceding statement when a call is missing its semicolon", () => {
    const code = 'int main(void) {\n  printf("hi")\n  have = 1;\n  return 0;\n}';
    const diagnostics = diagnose(code);
    const missing = diagnostics.find((item) => item.message === "compiler.missingSemicolon");
    expect(missing).toBeDefined();
    expect(code.slice(missing!.from, missing!.to)).toBe(")");
    expect(missing!.to).toBe(code.indexOf('printf("hi")') + 'printf("hi")'.length);
  });

  it("marks a declaration's missing semicolon", () => {
    const code = "int main(void) {\n  int a = 1\n  return 0;\n}";
    expect(diagnose(code)).toContainEqual(expect.objectContaining({ message: "compiler.missingSemicolon", severity: "error" }));
  });

  it("marks each Chinese semicolon and offers an English replacement", () => {
    const code = "int main(void) { int a = 1； return 0； }";
    const diagnostics = diagnose(code).filter((item) => item.message === "compiler.chineseSemicolon");
    expect(diagnostics).toHaveLength(2);
    for (const item of diagnostics) {
      expect(code.slice(item.from, item.to)).toBe("；");
      expect(item.actions?.[0]?.name).toBe(";");
    }
  });

  it("leaves Chinese punctuation in strings and comments alone", () => {
    expect(diagnose('int main(void) {\n  // 中文；注释\n  /* 中文；注释 */\n  printf("中文；字符串");\n  return 0;\n}')).toEqual([]);
  });

  it("does not flag boolean-like macros in preprocessor definitions", () => {
    const code = '#include <stdbool.h>\n#define TRUE 1\n#define FALSE 0\nint main(void) {\n  return TRUE && !FALSE ? 0 : 1;\n}\n';
    expect(diagnose(code)).toEqual([]);
  });

  it("accepts the actual head-insertion linked-list sample with blank lines after FALSE", () => {
    const catalog = JSON.parse(readFileSync(resolve("../backend/spring/src/main/resources/classroom-code/lessons.json"), "utf8"));
    function find(value: unknown): { code: string } | undefined {
      if (!value || typeof value !== "object") return undefined;
      if ("id" in value && value.id === "02-03-s1" && "code" in value && typeof value.code === "string") return { code: value.code };
      for (const entry of Object.values(value)) { const found = find(entry); if (found) return found; }
    }
    const sample = find(catalog);
    expect(sample).toBeDefined();
    expect(diagnose(sample!.code)).toEqual([]);
  });

  it.each(["\n", "\n\n", "\n  \n\n", "\r\n\r\n", "\n/* next declaration */\n\n"])(
    "does not turn a macro value into a missing-semicolon error across whitespace %j", (gap) => {
      expect(diagnose(`#define TRUE 1\n#define FALSE 0${gap}typedef char ElemType;\nint main(void) { return FALSE; }`)).toEqual([]);
    },
  );

  it("ignores multiline macro bodies while preserving nearby real error positions", () => {
    const code = '#define PRINT(value) \\\n  printf("%d", value)\n\nint main(void) {\n  int count = 1；\n  return 0;\n}';
    const diagnostic = diagnose(code).find((item) => item.message === "compiler.chineseSemicolon");
    expect(diagnostic).toBeDefined();
    expect(diagnostic!.from).toBe(code.indexOf("；"));
    expect(diagnose('#define FALSE 0\n\nint main(void) {\n  int count = 1\n  return 0;\n}')).toContainEqual(
      expect.objectContaining({ message: "compiler.missingSemicolon" }),
    );
  });

  it("reports malformed brackets without going beyond the document", () => {
    const code = "int main(void) {\n  int a[2 = {1, 2};\n  return 0;";
    const diagnostics = diagnose(code);
    expect(diagnostics.length).toBeGreaterThan(0);
    expect(diagnostics.every((item) => item.from >= 0 && item.to <= code.length && item.from <= item.to)).toBe(true);
  });
});
