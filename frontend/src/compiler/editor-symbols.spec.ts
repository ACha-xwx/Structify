import { describe, expect, it } from "vitest";
import { CodeSymbols } from "./editor-symbols";

function definition(code: string, marker: string) {
  const symbols = new CodeSymbols(code);
  const identifier = symbols.identifierAt(code.lastIndexOf(marker) + 1)!;
  return symbols.definition(identifier);
}

describe("C symbol definitions", () => {
  it("finds every ArcNode type reference without matching strings or comments", () => {
    const code = 'typedef struct ArcNode { int adjacent; struct ArcNode *next; } ArcNode;\nArcNode *head;\n// ArcNode\nchar *label = "ArcNode";';
    const symbols = new CodeSymbols(code);
    const identifier = symbols.identifierAt(code.indexOf("ArcNode *head") + 1)!;
    expect(symbols.occurrences(identifier)).toHaveLength(4);
    expect(symbols.definition(identifier)).toMatchObject({ kind: "type", signature: code.split("\n")[0] });
    expect(symbols.identifierAt(code.indexOf("// ArcNode") + 4)).toBeUndefined();
    expect(symbols.identifierAt(code.indexOf('"ArcNode"') + 2)).toBeUndefined();
  });

  it("shows variable declarations with initializers and separates comma declarators", () => {
    const code = "int total = 3, *pointer;\nint main(void) { return total + (pointer != 0); }";
    expect(definition(code, "total +")).toMatchObject({ kind: "variable", signature: "int total = 3;" });
    expect(definition(code, "pointer !=")).toMatchObject({ kind: "variable", signature: "int *pointer;" });
  });

  it("preserves function return and parameter types", () => {
    const code = "int *lookup(const int *values, int length, int (*compare)(int, int)) { return 0; }\nint main(void) { lookup(0, 0, 0); }";
    expect(definition(code, "lookup(0")).toMatchObject({
      kind: "function", signature: "int *lookup(const int *values, int length, int (*compare)(int, int));",
    });
    expect(definition("int sum(int left, int right);\nint main(void) { return sum(1, 2); }", "sum(1")).toMatchObject({ kind: "function", signature: "int sum(int left, int right);" });
  });

  it("recognizes function pointers as variables and aliases as types", () => {
    expect(definition("int (*compare)(int, int);\nint main(void) { return compare(1, 2); }", "compare(1")).toMatchObject({ kind: "variable", signature: "int (*compare)(int, int);" });
    expect(definition("typedef int (*Compare)(const void *, const void *);\nCompare callback;", "Compare callback")).toMatchObject({ kind: "type", signature: "typedef int (*Compare)(const void *, const void *);" });
  });

  it("resolves the nearest preceding local definition and function parameter", () => {
    const code = "int value;\nvoid visit(double value) {\n  value++;\n  { char value; value++; }\n  value++;\n}\nint main(void) { return value; }";
    const symbols = new CodeSymbols(code);
    const at = (position: number) => symbols.definition(symbols.identifierAt(position + 1)!);
    expect(at(code.indexOf("value++;"))).toMatchObject({ kind: "parameter", signature: "double value" });
    expect(at(code.indexOf("value++; }"))?.signature).toBe("char value;");
    expect(at(code.lastIndexOf("value++;"))).toMatchObject({ kind: "parameter", signature: "double value" });
    expect(definition(code, "value; }")).toMatchObject({ kind: "variable", signature: "int value;" });
  });

  it("keeps for-loop declarations within the loop", () => {
    const code = "double index;\nvoid visit(void) { for (int index = 0; index < 5; index++) {} index++; }";
    expect(definition(code, "index <")).toMatchObject({ signature: "int index = 0;" });
    expect(definition(code, "index++;")).toMatchObject({ signature: "double index;" });
  });

  it("does not expose parameters inside callback signatures as outer parameters", () => {
    const code = "int count;\nvoid visit(int (*callback)(double count)) { count++; }";
    expect(definition(code, "count++")).toMatchObject({ kind: "variable", signature: "int count;" });
  });

  it("resolves fields by their receiver type, including aliases and pointer chains", () => {
    const code = "typedef struct ArcNode { int value; struct ArcNode *next; } ArcNode;\ntypedef ArcNode *List;\nstruct Other { double value; };\nvoid visit(List head, struct Other other) { head->next->value++; other.value++; }";
    expect(definition(code, "value++; other")).toMatchObject({ kind: "field", signature: "int value;", owners: ["ArcNode", "ArcNode"] });
    expect(definition(code, "value++; }")).toMatchObject({ kind: "field", signature: "double value;", owners: ["Other"] });
  });

  it("recognizes anonymous struct aliases and enum constants", () => {
    expect(definition("typedef struct { int value; } Item;\nvoid visit(Item item) { item.value++; }", "value++")).toMatchObject({ kind: "field", signature: "int value;" });
    expect(definition("enum Color { RED = 1, BLUE };\nint color = RED;", "RED;")).toMatchObject({ kind: "constant", signature: "RED = 1" });
  });

  it("preserves pointer and array typedefs from a shared declaration", () => {
    const code = "typedef int *Pointer, Array[10];\nPointer pointer;\nArray values;";
    expect(definition(code, "Pointer pointer")).toMatchObject({ kind: "type", signature: "typedef int *Pointer;" });
    expect(definition(code, "Array values")).toMatchObject({ kind: "type", signature: "typedef int Array[10];" });
  });

  it("shows preprocessor macros despite parser recovery", () => {
    const code = "#define TRUE 1\n#define LIMIT 10\nint enabled = TRUE;\nint values[LIMIT];";
    expect(definition(code, "TRUE;")).toMatchObject({ kind: "macro", signature: "#define TRUE 1" });
    expect(definition(code, "LIMIT];")).toMatchObject({ kind: "macro", signature: "#define LIMIT 10" });
  });

  it("provides included standard library signatures and prefers user declarations", () => {
    expect(definition('#include <stdio.h>\nint main(void) { printf("hello"); }', "printf(")).toMatchObject({
      kind: "function", header: "stdio.h", signature: "int printf(const char *restrict format, ...);",
    });
    expect(definition('#include <stdlib.h>\nvoid *buffer = malloc(10);', "malloc(")).toMatchObject({ signature: "void *malloc(size_t size);" });
    expect(definition('#include <string.h>\nint main(void) { return strlen("a"); }', "strlen(")).toMatchObject({ signature: "size_t strlen(const char *text);" });
    const custom = definition('#include <stdio.h>\nlong printf(int value) { return value; }\nint main(void) { return printf(1); }', "printf(1");
    expect(custom).toMatchObject({ signature: "long printf(int value);" });
    expect(custom).not.toHaveProperty("header");
    expect(definition('int main(void) { printf("hello"); }', "printf(")).toBeUndefined();
    expect(definition('// #include <stdio.h>\nint main(void) { printf("hello"); }', "printf(")).toBeUndefined();
    expect(definition('int main(void) { mystery(1); }', "mystery(")).toBeUndefined();
  });

  it("respects pointer side at identifier boundaries", () => {
    const symbols = new CodeSymbols("int count;");
    expect(symbols.identifierAt(4, -1)).toBeUndefined();
    expect(symbols.identifierAt(4, 1)?.name).toBe("count");
    expect(symbols.identifierAt(9, -1)?.name).toBe("count");
    expect(symbols.identifierAt(9, 1)).toBeUndefined();
  });
});
