// @vitest-environment node
import { describe, expect, it, vi } from "vitest";

vi.mock("@wasm-fmt/clang-format/vite", async () => ({
  ...await import("@wasm-fmt/clang-format/node"),
  default: async () => {},
}));

import { formatCCode } from "./format-code";

describe("ClangFormat C formatting", () => {
  it("formats block indentation without changing string contents", async () => {
    const output = await formatCCode('int main(){if(1){printf("a；b");}return 0;}');
    expect(output).toBe('int main() {\n  if (1) {\n    printf("a；b");\n  }\n  return 0;\n}');
    expect(await formatCCode(output)).toBe(output);
  });

  it("preserves the order of includes and formats array brackets", async () => {
    const output = await formatCCode('#include "z.h"\n#include "a.h"\nint a[2]={1,2};');
    expect(output).toBe('#include "z.h"\n#include "a.h"\nint a[2] = {1, 2};');
  });
});
