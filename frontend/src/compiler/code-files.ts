/** Preserve Chinese comments from common Windows C source encodings. */
export function decodeCSource(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let source: string;
  if (bytes[0] === 0xff && bytes[1] === 0xfe) {
    source = new TextDecoder("utf-16le", { fatal: true }).decode(bytes);
  } else if (bytes[0] === 0xfe && bytes[1] === 0xff) {
    source = new TextDecoder("utf-16be", { fatal: true }).decode(bytes);
  } else {
    try {
      source = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    } catch {
      source = new TextDecoder("gb18030", { fatal: true }).decode(bytes);
    }
  }
  if (source.includes("\0")) throw new Error("Source contains binary data");
  return source;
}

export function cFileName(path: string): string {
  const name = path.split(/[\\/]/).at(-1) || "main.c";
  return /\.(c|h)$/i.test(name) ? name : `${name}.c`;
}

/** Course catalog entries may use .h for complete runnable examples; export those as C sources. */
export function cSourceFileName(path: string): string {
  return cFileName(path).replace(/\.h$/i, ".c");
}
