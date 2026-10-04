export const C_FORMAT_STYLE = JSON.stringify({
  BasedOnStyle: "LLVM", IndentWidth: 2, ColumnLimit: 100,
  BreakBeforeBraces: "Attach", AllowShortFunctionsOnASingleLine: "None", SortIncludes: "Never",
});

let formatter: Promise<typeof import("@wasm-fmt/clang-format/vite")> | undefined;

export async function formatCCode(code: string): Promise<string> {
  formatter ??= import("@wasm-fmt/clang-format/vite").then(async (module) => {
    await module.default();
    return module;
  }).catch((error) => {
    formatter = undefined;
    throw error;
  });
  const module = await formatter;
  return module.format(code, "main.c", C_FORMAT_STYLE);
}
