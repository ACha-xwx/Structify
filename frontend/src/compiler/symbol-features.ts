import { EditorState, StateField, type Extension, type Text } from "@codemirror/state";
import { Decoration, EditorView, hoverTooltip, type DecorationSet } from "@codemirror/view";
import { cppLanguage } from "@codemirror/lang-cpp";
import { highlightTree } from "@lezer/highlight";
import type { useI18n } from "../shared/i18n/locale";
import { codeColors } from "./editor-language";
import { CodeSymbols, type SymbolKind } from "./editor-symbols";

type Translate = ReturnType<typeof useI18n>["t"];
const indexes = new WeakMap<Text, CodeSymbols>();

function symbolsFor(state: EditorState): CodeSymbols {
  let symbols = indexes.get(state.doc);
  if (!symbols) {
    symbols = new CodeSymbols(state.doc.toString());
    indexes.set(state.doc, symbols);
  }
  return symbols;
}

function markIdentifiers(state: EditorState): DecorationSet {
  const selection = state.selection.main;
  const symbols = symbolsFor(state);
  const identifier = symbols.identifierAt(selection.head);
  if (!identifier || (!selection.empty && (selection.from !== identifier.from || selection.to !== identifier.to))) return Decoration.none;
  return Decoration.set(symbols.occurrences(identifier).map((match) => Decoration.mark({
    class: "cm-symbol-match", attributes: { "data-symbol": identifier.name },
  }).range(match.from, match.to)), true);
}

const identifierHighlights = StateField.define<DecorationSet>({
  create: markIdentifiers,
  update: (decorations, transaction) => transaction.docChanged || transaction.selection
    ? markIdentifiers(transaction.state) : decorations,
  provide: (field) => EditorView.decorations.from(field),
});

function highlightedDeclaration(signature: string): HTMLElement {
  const code = document.createElement("code");
  code.className = "c-symbol__declaration";
  let position = 0;
  highlightTree(cppLanguage.parser.parse(signature), codeColors, (from, to, classes) => {
    if (position < from) code.append(document.createTextNode(signature.slice(position, from)));
    const span = document.createElement("span");
    span.className = classes;
    span.textContent = signature.slice(from, to);
    code.append(span);
    position = to;
  });
  if (position < signature.length) code.append(document.createTextNode(signature.slice(position)));
  return code;
}

const kindLabels: Record<SymbolKind, Parameters<Translate>[0]> = {
  variable: "compiler.symbolVariable", parameter: "compiler.symbolParameter", function: "compiler.symbolFunction",
  type: "compiler.symbolType", struct: "compiler.symbolStruct", enum: "compiler.symbolEnum",
  field: "compiler.symbolField", constant: "compiler.symbolConstant", macro: "compiler.symbolMacro",
};

const symbolTheme = EditorView.theme({
  ".cm-symbol-match": { borderRadius: "2px", boxShadow: "inset 0 0 0 1px color-mix(in srgb, var(--text) 42%, transparent)", backgroundColor: "color-mix(in srgb, var(--text) 5%, transparent)" },
  ".cm-tooltip.c-symbol, .cm-tooltip .c-symbol": { maxWidth: "min(704px, calc(100vw - 32px))", boxSizing: "border-box", padding: "13.2px 15.4px", borderRadius: "6px" },
  ".c-symbol__heading": { display: "flex", alignItems: "center", gap: "15.4px", flexWrap: "wrap", marginBottom: "8.8px", fontSize: "13.2px", lineHeight: "19.8px" },
  ".c-symbol__kind": { color: "var(--text)", fontWeight: "600" },
  ".c-symbol__location": { color: "var(--text-muted)", fontFamily: "var(--font-ui)" },
  ".c-symbol__declaration": { display: "block", maxHeight: "308px", overflow: "auto", fontFamily: "var(--font-mono, Consolas, monospace)", fontSize: "15.4px", lineHeight: "1.6", whiteSpace: "pre-wrap", overflowWrap: "anywhere", tabSize: "2" },
});

export function codeSymbolFeatures(t: Translate): Extension {
  return [identifierHighlights, symbolTheme, hoverTooltip((view, position, side) => {
    const symbols = symbolsFor(view.state);
    const identifier = symbols.identifierAt(position, side);
    if (!identifier) return null;
    const definition = symbols.definition(identifier);
    if (!definition) return null;
    return {
      pos: identifier.from, end: identifier.to, above: true,
      create: () => {
        const dom = document.createElement("div");
        dom.className = "c-symbol";
        const heading = document.createElement("div");
        heading.className = "c-symbol__heading";
        const kind = document.createElement("span");
        kind.className = "c-symbol__kind";
        kind.textContent = t(kindLabels[definition.kind]);
        const location = document.createElement("span");
        location.className = "c-symbol__location";
        location.textContent = definition.header ? `<${definition.header}>`
          : `${t("compiler.symbolDefinition")} ${t("compiler.line")} ${view.state.doc.lineAt(definition.from).number}`;
        heading.append(kind, location);
        dom.append(heading, highlightedDeclaration(definition.signature));
        return { dom };
      },
    };
  }, { hoverTime: 300, hideOnChange: true, hideOn: (transaction) => transaction.reconfigured })];
}
