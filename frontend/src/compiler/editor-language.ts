import { cppLanguage } from "@codemirror/lang-cpp";
import { HighlightStyle } from "@codemirror/language";
import type { Diagnostic } from "@codemirror/lint";
import type { SyntaxNode } from "@lezer/common";
import { tags } from "@lezer/highlight";

export type SyntaxMessage = "compiler.syntaxError" | "compiler.missingSemicolon" | "compiler.chineseSemicolon";

export const codeColors = HighlightStyle.define([
  { tag: [tags.keyword, tags.controlKeyword], color: "var(--code-keyword)" },
  { tag: [tags.typeName, tags.className, tags.standard(tags.typeName)], color: "var(--code-type)" },
  { tag: [tags.function(tags.variableName), tags.function(tags.definition(tags.variableName))], color: "var(--code-function)" },
  { tag: [tags.string, tags.character, tags.special(tags.string)], color: "var(--code-string)" },
  { tag: [tags.number, tags.bool, tags.null], color: "var(--code-number)" },
  { tag: [tags.variableName, tags.propertyName], color: "var(--code-variable)" },
  { tag: [tags.operator, tags.meta], color: "var(--code-operator)" },
  { tag: [tags.bracket, tags.punctuation], color: "var(--code-punctuation)" },
  { tag: tags.comment, color: "var(--code-comment)", fontStyle: "normal" },
]);

const terminatedStatements = new Set([
  "Declaration", "ExpressionStatement", "ReturnStatement", "BreakStatement", "ContinueStatement", "GotoStatement",
]);

function lastCodeNode(node: SyntaxNode | null): SyntaxNode | null {
  while (node && /Comment$/.test(node.name)) node = node.prevSibling;
  if (!node) return null;
  return node.lastChild ? lastCodeNode(node.lastChild) ?? node : node;
}

function syntaxSource(code: string): string {
  const directives: { from: number; to: number }[] = [];
  cppLanguage.parser.parse(code).iterate({ enter(cursor) {
    if (cursor.name !== "PreprocDirective") return;
    const from = code.lastIndexOf("\n", Math.max(0, cursor.from - 1)) + 1;
    let to = cursor.from;
    do {
      const end = code.indexOf("\n", to);
      if (end === -1) { to = code.length; break; }
      const last = code[end - 1] === "\r" ? end - 2 : end - 1;
      to = end + 1;
      if (code[last] !== "\\") break;
    } while (to < code.length);
    directives.push({ from, to });
    return false;
  } });
  if (!directives.length) return code;
  const parts: string[] = [];
  let position = 0;
  // Preprocessing belongs to the real compiler. Blank directive logical lines only
  // for syntax checks, retaining offsets so macro recovery cannot taint nearby C code.
  for (const directive of directives) {
    const from = Math.max(position, directive.from);
    if (directive.to <= from) continue;
    parts.push(code.slice(position, from), code.slice(from, directive.to).replace(/[^\r\n]/g, " "));
    position = directive.to;
  }
  parts.push(code.slice(position));
  return parts.join("");
}

/** Parse syntax only. Type/name checking and execution stay with the real C compiler. */
export function diagnoseC(code: string, message: (key: SyntaxMessage) => string): Diagnostic[] {
  const diagnostics: Diagnostic[] = [];
  const seen = new Set<string>();
  const tree = cppLanguage.parser.parse(syntaxSource(code));
  tree.iterate({
    enter(cursor) {
      if (!cursor.type.isError || diagnostics.length >= 60) return;
      const node = cursor.node;
      const invalid = code.slice(node.from, node.to);
      // Only inspect invalid syntax nodes, so punctuation in strings/comments is never flagged.
      if (invalid.includes("；")) {
        for (let offset = 0; offset < invalid.length; offset++) {
          if (invalid[offset] !== "；") continue;
          const from = node.from + offset;
          diagnostics.push({
            from, to: from + 1, severity: "error", message: message("compiler.chineseSemicolon"),
            actions: [{ name: ";", apply: (view, start, end) => view.dispatch({ changes: { from: start, to: end, insert: ";" } }) }],
          });
        }
        return;
      }
      const previous = lastCodeNode(node.prevSibling);
      const previousEnd = previous?.to ?? node.from;
      const missingTerminator = node.from === node.to && terminatedStatements.has(node.parent?.name ?? "")
        && code[previousEnd - 1] !== ";";
      // Recovery can absorb the next line into an expression after a missing terminator.
      const nextLine = previous && code.slice(previousEnd, node.from).includes("\n")
        && /Expression$/.test(node.parent?.name ?? "");
      const missing = Boolean(missingTerminator || nextLine);
      const from = missing ? Math.max(0, previousEnd - 1) : node.from;
      const to = missing ? previousEnd : Math.min(code.length, Math.max(node.to, from + 1));
      const key = `${from}:${to}`;
      if (seen.has(key)) return;
      seen.add(key);
      diagnostics.push({ from, to, severity: "error", message: message(missing ? "compiler.missingSemicolon" : "compiler.syntaxError") });
    },
  });
  return diagnostics.sort((a, b) => a.from - b.from);
}
