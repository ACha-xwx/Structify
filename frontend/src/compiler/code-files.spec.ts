import { describe, expect, it } from "vitest";
import { cFileName, cSourceFileName, decodeCSource } from "./code-files";

function bytes(value: string, encoding: "utf-8" | "utf-16le" | "utf-16be" = "utf-8") {
  if (encoding === "utf-8") return new TextEncoder().encode(value).buffer;
  const data = new Uint8Array(2 + value.length * 2);
  data[0] = encoding === "utf-16le" ? 0xff : 0xfe;
  data[1] = encoding === "utf-16le" ? 0xfe : 0xff;
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index);
    if (encoding === "utf-16le") {
      data[2 + index * 2] = code & 0xff;
      data[3 + index * 2] = code >> 8;
    } else {
      data[2 + index * 2] = code >> 8;
      data[3 + index * 2] = code & 0xff;
    }
  }
  return data.buffer;
}

describe("compiler source files", () => {
  it("decodes UTF-8 and BOM-marked UTF-16 source", () => {
    const source = "// 中文注释\nint main(void) { return 0; }\n";
    expect(decodeCSource(bytes(source))).toBe(source);
    expect(decodeCSource(bytes(source, "utf-16le"))).toBe(source);
    expect(decodeCSource(bytes(source, "utf-16be"))).toBe(source);
  });

  it("keeps a C or header extension and normalizes other names", () => {
    expect(cFileName("C:\\work\\demo.c")).toBe("demo.c");
    expect(cFileName("demo.h")).toBe("demo.h");
    expect(cFileName("demo.txt")).toBe("demo.txt.c");
    expect(cFileName("")).toBe("main.c");
  });

  it("exports catalog header-named programs as C sources", () => {
    expect(cSourceFileName("ch02/code/seqlist.h")).toBe("seqlist.c");
    expect(cSourceFileName("ch03/code/seqstack.c")).toBe("seqstack.c");
    expect(cSourceFileName("")).toBe("main.c");
  });

  it("rejects binary-looking content", () => {
    expect(() => decodeCSource(new Uint8Array([0x69, 0x6e, 0x00, 0x74]).buffer)).toThrow();
  });
});
