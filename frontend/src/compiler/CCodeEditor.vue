<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref, render, useId, watch, type VNodeChild } from "vue";
import { Compartment, EditorState, Prec, StateEffect, StateField, Transaction } from "@codemirror/state";
import { crosshairCursor, drawSelection, dropCursor, EditorView, highlightActiveLine, highlightActiveLineGutter, highlightSpecialChars, keymap, lineNumbers, rectangularSelection, runScopeHandlers, showPanel, type Command, type Panel } from "@codemirror/view";
import { cpp } from "@codemirror/lang-cpp";
import { bracketMatching, defaultHighlightStyle, foldGutter, foldKeymap, indentOnInput, indentUnit, syntaxHighlighting } from "@codemirror/language";
import { defaultKeymap, history, historyKeymap, indentWithTab, isolateHistory, redo, redoDepth, undo, undoDepth } from "@codemirror/commands";
import { autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap } from "@codemirror/autocomplete";
import { forEachDiagnostic, forceLinting, linter, lintGutter, lintKeymap, type Diagnostic } from "@codemirror/lint";
import { closeSearchPanel, findNext, findPrevious, getSearchQuery, highlightSelectionMatches, openSearchPanel, replaceAll, replaceNext, search, searchKeymap, SearchQuery, selectMatches, setSearchQuery } from "@codemirror/search";
import { Check, ChevronDown, CircleAlert, IndentIncrease, Search, X } from "@lucide/vue";
import leftArrow from "../assets/classroom/left-arrow.svg";
import rightArrow from "../assets/classroom/right-arrow.svg";
import foldRight from "../assets/compiler/fold-right.svg?raw";
import foldDown from "../assets/compiler/fold-down.svg?raw";
import { useI18n } from "../shared/i18n/locale";
import EditorContextMenu from "./EditorContextMenu.vue";
import { codeColors, diagnoseC } from "./editor-language";
import { formatCCode } from "./format-code";
import { codeSymbolFeatures } from "./symbol-features";

const props = defineProps<{ modelValue: string; documentKey: number }>();
const emit = defineEmits<{ "update:modelValue": [value: string]; run: []; import: []; export: [] }>();
const { t, locale } = useI18n();
const host = ref<HTMLElement | null>(null);
const menuAt = ref<{ x: number; y: number } | null>(null);
const formatting = ref(false);
const operationError = ref("");
const line = ref(1);
const column = ref(1);
const problems = ref(0);
const canUndo = ref(false);
const canRedo = ref(false);
const canPaste = computed(() => Boolean(navigator.clipboard?.readText));
const phrases = new Compartment();
const diagnosticsContentId = `${useId()}-diagnostics`;
const setDiagnosticsPanel = StateEffect.define<boolean>();
const diagnosticsPanel = StateField.define<boolean>({
  create: () => false,
  update: (open, transaction) => {
    for (const effect of transaction.effects) if (effect.is(setDiagnosticsPanel)) open = effect.value;
    return open;
  },
  provide: (field) => showPanel.from(field, (open) => open ? createDiagnosticsPanel : null),
});
let view: EditorView | undefined;
let revision = 0;
let hold: ReturnType<typeof setTimeout> | undefined;
let holdFrom: { x: number; y: number } | undefined;

// Keep basicSetup's features explicit so its default fold gutter can be customized.
const editorSetup = [
  lineNumbers(), highlightActiveLineGutter(), highlightSpecialChars(), history(),
  drawSelection(), dropCursor(), EditorState.allowMultipleSelections.of(true), indentOnInput(),
  syntaxHighlighting(defaultHighlightStyle, { fallback: true }), bracketMatching(), closeBrackets(),
  autocompletion(), rectangularSelection(), crosshairCursor(), highlightActiveLine(), highlightSelectionMatches(),
  keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap,
    ...foldKeymap, ...completionKeymap, ...lintKeymap]),
];

function translations() {
  return [EditorState.phrases.of(locale.value === "zh-CN" ? {
    "Search": "查找", "Find": "查找", "Replace": "替换", "next": "下一个", "previous": "上一个", "all": "全部",
    "match case": "区分大小写", "regexp": "正则", "by word": "全词", "replace": "替换",
    "replace all": "全部替换", "close": "关闭", "No diagnostics": "没有语法错误",
    "Diagnostics": "语法问题", "Go to line": "跳转到行", "go": "跳转",
  } : {}), foldGutter({ markerDOM: (open) => {
    const marker = document.createElement("button");
    marker.type = "button";
    marker.className = "c-fold-toggle";
    marker.title = t(open ? "compiler.foldLine" : "compiler.unfoldLine");
    marker.setAttribute("aria-label", marker.title);
    marker.setAttribute("aria-expanded", String(open));
    marker.innerHTML = open ? foldDown : foldRight;
    marker.firstElementChild?.setAttribute("aria-hidden", "true");
    // Rotate the new fold marker instead of restarting the SVG's stroke drawing.
    marker.querySelector("animate")?.remove();
    return marker;
  } })];
}

function syncStatus() {
  if (!view) return;
  const cursor = view.state.selection.main.head;
  const currentLine = view.state.doc.lineAt(cursor);
  line.value = currentLine.number;
  column.value = cursor - currentLine.from + 1;
  canUndo.value = undoDepth(view.state) > 0;
  canRedo.value = redoDepth(view.state) > 0;
}

function glassButton(name: string, label: string, action: () => void, content: VNodeChild = label, round = false) {
  return h("button", {
    name, type: "button", class: ["c-search__glass", { "c-search__glass--icon": round }],
    "aria-label": label, title: label, onClick: action,
  }, [
    h("span", { class: "c-search__shade", "aria-hidden": "true" }),
    h("span", { class: "c-search__light", "aria-hidden": "true" }),
    h("span", { class: "c-search__rim", "aria-hidden": "true" }),
    h("span", { class: "c-search__label" }, [content]),
  ]);
}

function openDiagnostics(editor: EditorView): boolean {
  forceLinting(editor);
  editor.dispatch({ effects: setDiagnosticsPanel.of(true) });
  return true;
}

function createDiagnosticsPanel(editor: EditorView): Panel {
  const dom = document.createElement("div");
  dom.className = "c-diagnostics";
  dom.setAttribute("role", "region");
  let expanded = true;
  const close = () => {
    editor.dispatch({ effects: setDiagnosticsPanel.of(false) });
    editor.focus();
  };

  function draw() {
    const entries: { diagnostic: Diagnostic; from: number; to: number }[] = [];
    forEachDiagnostic(editor.state, (diagnostic, from, to) => entries.push({ diagnostic, from, to }));
    const title = t("compiler.diagnostics");
    dom.setAttribute("aria-label", title);
    render(h("div", { class: "c-diagnostics__body", onKeydown: (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); close(); return; }
      const items = Array.from(dom.querySelectorAll<HTMLButtonElement>(".c-diagnostics__entry"));
      if (!expanded || !items.length || !["ArrowUp", "ArrowDown", "Home", "End"].includes(event.key)) return;
      event.preventDefault();
      const index = items.indexOf(event.target as HTMLButtonElement);
      const next = event.key === "Home" ? 0 : event.key === "End" ? items.length - 1
        : (index + (event.key === "ArrowDown" ? 1 : -1) + items.length) % items.length;
      items[next]?.focus();
    } }, [
      h("button", {
        type: "button", class: "c-diagnostics__trigger", "aria-expanded": expanded,
        "aria-controls": diagnosticsContentId,
        onClick: () => { expanded = !expanded; draw(); },
      }, [
        h(CircleAlert, { size: 20, "aria-hidden": "true" }),
        h("span", { class: "c-diagnostics__title" }, `${title} (${entries.length})`),
        h(ChevronDown, { size: 16, class: "c-diagnostics__chevron", "aria-hidden": "true" }),
      ]),
      glassButton("close", editor.state.phrase("close"), close, h(X, { size: 16, "aria-hidden": "true" }), true),
      h("div", {
        id: diagnosticsContentId, class: ["c-diagnostics__content", { "c-diagnostics__content--open": expanded }],
        "aria-hidden": !expanded, inert: !expanded,
      }, [h("div", { class: "c-diagnostics__inner" }, [
        entries.length ? h("ul", { class: "c-diagnostics__list" }, entries.map(({ diagnostic, from, to }) => {
          const location = editor.state.doc.lineAt(from);
          const position = `${t("compiler.line")} ${location.number}, ${t("compiler.column")} ${from - location.from + 1}`;
          const selected = editor.state.selection.main;
          return h("li", { key: `${from}:${to}:${diagnostic.message}`, class: "c-diagnostics__item" }, [
            h("button", {
              type: "button", class: "c-diagnostics__entry", "aria-pressed": selected.from === from && selected.to === to,
              onClick: () => {
                editor.dispatch({ selection: { anchor: from, head: to }, effects: EditorView.scrollIntoView(from, { y: "center" }) });
                editor.focus();
              },
            }, [
              h("span", { class: "c-diagnostics__message" }, diagnostic.message),
              h("span", { class: "c-diagnostics__position" }, position),
            ]),
            ...(diagnostic.actions?.map((action, index) => glassButton(`fix-${from}-${index}`, action.name,
              () => { action.apply(editor, from, to); editor.focus(); })) ?? []),
          ]);
        })) : h("p", { class: "c-diagnostics__empty", role: "status" }, editor.state.phrase("No diagnostics")),
      ])]),
    ]), dom);
  }

  function measureHeight() {
    editor.requestMeasure({ key: dom, read: () => editor.dom.clientHeight - 20, write: (height) => {
      if (height > 0) dom.style.maxHeight = `${height}px`;
    } });
  }

  draw();
  return {
    dom,
    mount: () => { dom.querySelector<HTMLButtonElement>(".c-diagnostics__trigger")?.focus(); measureHeight(); },
    update: (update) => { draw(); if (update.geometryChanged) measureHeight(); },
    destroy: () => render(null, dom),
  };
}

function createSearchPanel(editor: EditorView): Panel {
  const dom = document.createElement("div");
  dom.className = "cm-search";
  let query = getSearchQuery(editor.state);
  let panelLocale = locale.value;
  const field = (name: string) => dom.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;

  function commit() {
    const next = new SearchQuery({
      search: field("search").value, replace: field("replace").value,
      caseSensitive: field("case").checked, regexp: field("re").checked,
      wholeWord: field("word").checked, literal: query.literal,
    });
    if (!next.eq(query)) editor.dispatch({ effects: setSearchQuery.of(next) });
  }

  function draw() {
    const phrase = (text: string) => editor.state.phrase(text);
    const button = (name: string, label: string, command: Command, content: VNodeChild = phrase(label), round = false) =>
      glassButton(name, phrase(label), () => command(editor), content, round);
    const textField = (name: string, label: string, value: string) => h("input", {
      name, value, class: "cm-textfield", placeholder: phrase(label), "aria-label": phrase(label),
      "main-field": name === "search" ? "true" : undefined, spellcheck: false, onInput: commit,
    });
    const checkbox = (name: string, label: string, checked: boolean) => h("label", { class: "c-search__option" }, [
      h("span", { class: "c-search__check" }, [
        h("input", { type: "checkbox", name, checked, onChange: commit }),
        h(Check, { size: 16, "aria-hidden": "true" }),
      ]), phrase(label),
    ]);
    render(h("div", {
      class: "c-search__body",
      onKeydown: (event: KeyboardEvent) => {
        if (runScopeHandlers(editor, event, "search-panel")) event.preventDefault();
        else if (event.key === "Enter" && !event.isComposing) {
          if (event.target === field("search")) {
            event.preventDefault();
            (event.shiftKey ? findPrevious : findNext)(editor);
          } else if (event.target === field("replace")) {
            event.preventDefault();
            replaceNext(editor);
          }
        }
      },
    }, [
      h("div", { class: "c-search__row" }, [
        textField("search", "Find", query.search),
        h("div", { class: "c-search__actions" }, [
          button("prev", "previous", findPrevious, h("img", { src: leftArrow, alt: "", "aria-hidden": "true" }), true),
          button("next", "next", findNext, h("img", { src: rightArrow, alt: "", "aria-hidden": "true" }), true),
          button("select", "all", selectMatches),
          button("close", "close", closeSearchPanel, h(X, { size: 16, "aria-hidden": "true" }), true),
        ]),
      ]),
      h("div", { class: "c-search__options" }, [
        checkbox("case", "match case", query.caseSensitive),
        checkbox("re", "regexp", query.regexp),
        checkbox("word", "by word", query.wholeWord),
      ]),
      h("div", { class: "c-search__row", hidden: editor.state.readOnly }, [
        textField("replace", "Replace", query.replace),
        button("replace", "replace", replaceNext), button("replaceAll", "replace all", replaceAll),
      ]),
    ]), dom);
  }

  draw();
  return {
    dom, top: true,
    mount: () => field("search").select(),
    update: (update) => {
      if (panelLocale !== locale.value || update.transactions.some((transaction) => transaction.effects.some((effect) => effect.is(setSearchQuery)))) {
        panelLocale = locale.value;
        query = getSearchQuery(update.state);
        draw();
      }
    },
    destroy: () => render(null, dom),
  };
}

const editorTheme = EditorView.theme({
  "&": { height: "100%", backgroundColor: "var(--code-bg)", color: "var(--text)", fontSize: "16px" },
  ".cm-scroller": { overflow: "auto", fontFamily: "var(--font-mono, Consolas, monospace)", lineHeight: "1.6" },
  ".cm-content": { padding: "14px 0", caretColor: "var(--text)" },
  ".cm-line": { padding: "0 20px 0 12px" },
  ".cm-gutters": { backgroundColor: "var(--code-bg)", color: "var(--text-muted)", borderRight: "1px solid var(--line-strong)" },
  ".cm-lineNumbers .cm-gutterElement": { minWidth: "40px", padding: "0 10px 0 8px" },
  ".cm-foldGutter .cm-gutterElement": { padding: "0 2px" },
  ".c-fold-toggle": { display: "inline-flex", alignItems: "center", justifyContent: "center", width: "20px", height: "20px", padding: "0", border: "none", borderRadius: "2px", background: "transparent", color: "inherit", verticalAlign: "middle", cursor: "pointer" },
  ".c-fold-toggle svg": { display: "block", width: "18px", height: "18px", pointerEvents: "none" },
  ".c-fold-toggle:hover": { color: "var(--text)" },
  ".c-fold-toggle:focus-visible": { outline: "2px solid var(--text-muted)", outlineOffset: "1px" },
  ".cm-activeLine, .cm-activeLineGutter": { backgroundColor: "color-mix(in srgb, var(--text) 5%, transparent)" },
  ".cm-cursor, .cm-dropCursor": { borderLeftColor: "var(--text)" },
  "&.cm-focused": { outline: "none" },
  "&.cm-focused .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection": { backgroundColor: "color-mix(in srgb, #4285f4 25%, transparent)" },
  ".cm-matchingBracket": { backgroundColor: "color-mix(in srgb, var(--code-punctuation) 20%, transparent)", outline: "1px solid var(--code-punctuation)", borderRadius: "2px" },
  ".cm-nonmatchingBracket": { backgroundColor: "color-mix(in srgb, #ef4444 20%, transparent)", color: "#ef4444" },
  ".cm-tooltip, .cm-panels": { backgroundColor: "var(--surface)", color: "var(--text)", border: "1px solid var(--line-strong)", fontFamily: "var(--font-ui)" },
  ".cm-panels-top": { position: "absolute", inset: "10px 10px auto", zIndex: "5", border: "none", background: "transparent", pointerEvents: "none" },
  ".cm-panels-bottom": { position: "absolute", inset: "auto 10px 10px", zIndex: "5", border: "none", background: "transparent", pointerEvents: "none" },
  ".cm-panel.c-diagnostics": { position: "relative", display: "flex", width: "max-content", maxWidth: "100%", boxSizing: "border-box", border: "1px solid var(--line-strong)", borderRadius: "8px", background: "var(--surface)", boxShadow: "0 6px 20px color-mix(in srgb, var(--text) 14%, transparent)", pointerEvents: "auto", overflow: "hidden" },
  ".c-diagnostics__body": { display: "flex", flexDirection: "column", width: "100%", minWidth: "0", minHeight: "0" },
  ".c-diagnostics__trigger": { display: "flex", flex: "none", alignItems: "center", gap: "12px", width: "100%", minHeight: "60px", boxSizing: "border-box", margin: "0", padding: "16px 64px 16px 16px", border: "none", borderBottom: "1px solid var(--line-strong)", background: "transparent", color: "var(--text)", fontFamily: "var(--font-ui)", fontSize: "18px", fontWeight: "500", lineHeight: "28px", textAlign: "left", cursor: "pointer" },
  ".c-diagnostics__trigger > svg": { flex: "none", color: "var(--text-muted)" },
  ".c-diagnostics__title": { flex: "1", overflowWrap: "anywhere" },
  ".c-diagnostics__chevron": { transition: "transform 200ms ease" },
  ".c-diagnostics__trigger[aria-expanded=true] .c-diagnostics__chevron": { transform: "rotate(180deg)" },
  ".c-diagnostics [name=close]": { position: "absolute", top: "12px", right: "12px", margin: "0" },
  ".c-diagnostics__content": { display: "grid", gridTemplateRows: "0fr", visibility: "hidden", minHeight: "0", overflow: "auto", transition: "grid-template-rows 200ms ease, visibility 200ms ease" },
  ".c-diagnostics__content--open": { gridTemplateRows: "1fr", visibility: "visible" },
  ".c-diagnostics__inner": { minHeight: "0", overflow: "hidden" },
  ".c-diagnostics__list": { display: "flex", flexDirection: "column", gap: "16px", margin: "0", padding: "8px 16px 16px", listStyle: "none" },
  ".c-diagnostics__item": { display: "flex", alignItems: "center", gap: "12px" },
  ".c-diagnostics__entry": { display: "flex", flex: "1", flexDirection: "column", alignItems: "flex-start", gap: "4px", minWidth: "0", margin: "0", padding: "8px 12px", border: "none", borderRadius: "4px", background: "transparent", color: "var(--text)", fontFamily: "var(--font-ui)", textAlign: "left", cursor: "pointer", transition: "background 150ms ease" },
  ".c-diagnostics__entry:hover, .c-diagnostics__entry[aria-pressed=true]": { background: "color-mix(in srgb, var(--text) 6%, transparent)" },
  ".c-diagnostics__message": { fontSize: "16px", fontWeight: "600", lineHeight: "24px", whiteSpace: "pre-wrap", overflowWrap: "anywhere" },
  ".c-diagnostics__position": { color: "var(--text-muted)", fontSize: "13px", lineHeight: "20px" },
  ".c-diagnostics__empty": { margin: "0", padding: "16px", color: "var(--text-muted)", fontSize: "14px" },
  ".c-diagnostics__trigger:focus-visible, .c-diagnostics__entry:focus-visible": { outline: "none", boxShadow: "var(--focus-ring)" },
  ".cm-panel.cm-search": { width: "min(480px, 100%)", boxSizing: "border-box", marginLeft: "auto", padding: "12px", border: "1px solid var(--line-strong)", borderRadius: "8px", background: "color-mix(in srgb, var(--surface) 96%, transparent)", boxShadow: "0 6px 20px color-mix(in srgb, var(--text) 14%, transparent)", fontSize: "13px", pointerEvents: "auto", backdropFilter: "blur(10px)" },
  ".c-search__body": { display: "grid", gap: "12px" },
  ".c-search__row": { display: "flex", flexWrap: "wrap", alignItems: "center", gap: "8px" },
  ".c-search__actions": { display: "inline-flex", flex: "none", alignItems: "center", gap: "8px" },
  ".c-search__row[hidden]": { display: "none" },
  ".cm-search .cm-textfield": { flex: "1 1 100px", minWidth: "80px", width: "0", height: "34px", margin: "0", boxSizing: "border-box", fontFamily: "var(--font-ui)", fontSize: "13px" },
  ".cm-search .cm-textfield:focus-visible": { outline: "2px solid var(--text-muted)", outlineOffset: "2px" },
  ".cm-panel.cm-search .c-search__glass": { margin: "0" },
  ".c-search__glass": { display: "inline-flex", position: "relative", flex: "none", alignItems: "center", justifyContent: "center", overflow: "hidden", height: "34px", padding: "0 12px", border: "1px double color-mix(in srgb, var(--text) 8%, transparent)", borderRadius: "999px", background: "color-mix(in srgb, var(--surface) 8%, transparent)", color: "var(--text)", fontFamily: "var(--font-ui)", fontSize: "13px", whiteSpace: "nowrap", cursor: "pointer", backdropFilter: "blur(5px)", filter: "brightness(1.05)", boxShadow: "inset 2px -2px 1px -1px rgba(255,255,255,.9), inset -2px 2px 1px -1px rgba(255,255,255,.9), inset 6px -6px 1px -6px rgba(255,255,255,.55), inset -6px 6px 1px -6px rgba(255,255,255,.55), inset 0 0 2px color-mix(in srgb, var(--text) 80%, transparent), 0 4px 8px color-mix(in srgb, var(--text) 20%, transparent)", transition: "transform 250ms linear, background-color 250ms linear, box-shadow 250ms linear, filter 250ms linear" },
  ".c-search__glass:hover": { transform: "scale(1.02)", background: "transparent", filter: "brightness(1.1)", boxShadow: "inset 2px -2px 1px -1px rgba(255,255,255,.95), inset -2px 2px 1px -1px rgba(255,255,255,.95), inset 6px -6px 1px -6px rgba(255,255,255,.65), inset -6px 6px 1px -6px rgba(255,255,255,.65), inset 0 0 2px color-mix(in srgb, var(--text) 65%, transparent), 0 6px 12px color-mix(in srgb, var(--text) 22%, transparent)" },
  ".c-search__glass:active": { transform: "scale(1)" },
  ".c-search__glass:focus-visible": { outline: "2px solid var(--text)", outlineOffset: "2px" },
  ".c-search__glass--icon": { width: "34px", padding: "0", borderRadius: "50%" },
  ".cm-panel.cm-search [name=close]": { position: "relative", top: "auto", right: "auto", border: "1px double color-mix(in srgb, var(--text) 8%, transparent)", background: "color-mix(in srgb, var(--surface) 8%, transparent)", fontSize: "13px", padding: "0", lineHeight: "1" },
  ".c-search__shade, .c-search__light, .c-search__rim": { position: "absolute", borderRadius: "inherit", pointerEvents: "none", transition: "opacity 250ms linear, transform 250ms linear, filter 250ms linear, border-color 250ms linear" },
  ".c-search__shade": { top: "35%", left: "8px", right: "8px", height: "calc(100% - 16px)", border: "1px solid color-mix(in srgb, var(--text) 90%, transparent)", filter: "blur(4px)" },
  ".c-search__light": { inset: "0", zIndex: "1", opacity: ".8", filter: "blur(7px)", background: "linear-gradient(45deg, rgba(255,255,255,.8) 0%, transparent 15%, transparent 85%, rgba(255,255,255,.8) 100%)" },
  ".c-search__rim": { inset: "4.5px", zIndex: "2", border: "1px solid rgba(255,255,255,.2)", filter: "blur(1px)" },
  ".c-search__label": { position: "relative", zIndex: "3", display: "inline-flex", alignItems: "center", justifyContent: "center", filter: "drop-shadow(0 25px 3px rgba(102,102,102,.15))", transition: "color 250ms linear, filter 250ms linear, transform 250ms linear" },
  ".c-search__label img": { display: "block", width: "18px", height: "18px" },
  ".c-search__glass:hover .c-search__shade": { opacity: ".8", filter: "blur(12px)" },
  ".c-search__glass:hover .c-search__light": { opacity: "1", transform: "scale(1.05)", filter: "blur(9px)" },
  ".c-search__glass:hover .c-search__rim": { borderColor: "rgba(255,255,255,.45)" },
  ".c-search__glass:hover .c-search__label": { transform: "translateY(-.5px)", filter: "drop-shadow(0 28px 4px rgba(102,102,102,.2))" },
  ".c-search__options": { display: "flex", flexWrap: "wrap", gap: "8px 14px" },
  ".cm-panel.cm-search .c-search__option": { display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "13px", margin: "0", whiteSpace: "nowrap", cursor: "pointer" },
  ".c-search__check": { position: "relative", display: "inline-flex", flex: "none", width: "16px", height: "16px" },
  ".cm-panel.cm-search .c-search__check input": { appearance: "none", width: "16px", height: "16px", boxSizing: "border-box", padding: "0", margin: "0", border: "1px solid var(--text)", borderRadius: "2px", background: "transparent", cursor: "pointer" },
  ".cm-panel.cm-search .c-search__check input:checked": { background: "var(--text)" },
  ".c-search__check input:focus-visible": { outline: "2px solid var(--text-muted)", outlineOffset: "2px" },
  ".c-search__check svg": { position: "absolute", inset: "0", color: "var(--surface)", pointerEvents: "none", opacity: "0" },
  ".c-search__check input:checked + svg": { opacity: "1" },
  ".c-search__check input:disabled": { opacity: ".5", cursor: "not-allowed" },
  ".cm-textfield": { padding: "4px 8px", border: "1px solid var(--line-strong)", borderRadius: "4px", background: "var(--code-bg)", color: "var(--text)" },
  ".cm-button": { border: "1px solid var(--line-strong)", borderRadius: "4px", background: "var(--surface)", color: "var(--text)", fontSize: "12px" },
  ".cm-searchMatch": { backgroundColor: "color-mix(in srgb, #ffc700 30%, transparent)" },
  ".cm-searchMatch.cm-searchMatch-selected": { backgroundColor: "color-mix(in srgb, #ff9e00 50%, transparent)" },
  ".cm-diagnostic-error": { borderLeftColor: "#ef4444" },
  ".cm-foldPlaceholder": { backgroundColor: "var(--surface)", color: "var(--text-muted)", borderColor: "var(--line-strong)" },
  "@media (prefers-reduced-motion: reduce)": {
    ".c-search__glass, .c-search__shade, .c-search__light, .c-search__rim, .c-search__label": { transition: "none" },
    ".c-search__glass:hover, .c-search__glass:hover .c-search__light, .c-search__glass:hover .c-search__label": { transform: "none" },
    ".c-diagnostics__content, .c-diagnostics__chevron, .c-diagnostics__entry": { transition: "none" },
  },
  "@media (prefers-reduced-transparency: reduce)": {
    ".cm-panel.cm-search, .c-search__glass": { background: "var(--surface)", backdropFilter: "none" },
  },
});

function createState(code: string) {
  return EditorState.create({ doc: code, extensions: [
    editorSetup, cpp(), indentUnit.of("  "), syntaxHighlighting(codeColors), editorTheme,
    codeSymbolFeatures(t),
    search({ top: true, createPanel: createSearchPanel }), phrases.of(translations()), lintGutter(), diagnosticsPanel,
    linter((editor) => {
      const diagnostics = diagnoseC(editor.state.doc.toString(), t);
      problems.value = diagnostics.length;
      return diagnostics;
    }, { delay: 300 }),
    EditorView.contentAttributes.of({ "aria-label": t("compiler.code"), "aria-haspopup": "menu", spellcheck: "false" }),
    Prec.highest(keymap.of([
      { key: "Mod-Enter", run: () => { emit("run"); return true; } },
      { key: "F6", run: () => { emit("run"); return true; } },
      { key: "Mod-o", run: () => { emit("import"); return true; } },
      { key: "Mod-s", run: () => { emit("export"); return true; } },
      { key: "Mod-Shift-m", run: openDiagnostics },
      { key: "Mod-y", run: redo },
      { key: "Shift-Alt-f", run: () => { void formatDocument(); return true; } },
      indentWithTab,
    ])),
    EditorView.updateListener.of((update) => {
      if (update.docChanged) {
        revision++;
        operationError.value = "";
        emit("update:modelValue", update.state.doc.toString());
      }
      syncStatus();
    }),
  ] });
}

function closeMenu(restoreFocus = true) {
  menuAt.value = null;
  if (restoreFocus) view?.focus();
}

function openMenu(x: number, y: number) {
  if (!view) return;
  syncStatus();
  const position = view.posAtCoords({ x, y });
  const range = view.state.selection.main;
  if (position !== null && (range.empty || position < range.from || position > range.to)) {
    view.dispatch({ selection: { anchor: position } });
  }
  menuAt.value = { x, y };
}

function contextMenu(event: MouseEvent) {
  if (!(event.target as Element).closest(".cm-content, .cm-gutters")) return;
  event.preventDefault();
  clearHold();
  openMenu(event.clientX, event.clientY);
}

function contextKey(event: KeyboardEvent) {
  if (event.key !== "ContextMenu" && !(event.shiftKey && event.key === "F10")) return;
  if (!(event.target as Element).closest(".cm-content")) return;
  event.preventDefault();
  const coords = view?.coordsAtPos(view.state.selection.main.head);
  const bounds = host.value!.getBoundingClientRect();
  menuAt.value = { x: coords?.left ?? bounds.left + 20, y: coords?.bottom ?? bounds.top + 20 };
  syncStatus();
}

function clearHold() { clearTimeout(hold); hold = undefined; holdFrom = undefined; }
function pointerDown(event: PointerEvent) {
  if (event.pointerType !== "touch") return;
  clearHold();
  holdFrom = { x: event.clientX, y: event.clientY };
  hold = setTimeout(() => { openMenu(event.clientX, event.clientY); clearHold(); }, 460);
}
function pointerMove(event: PointerEvent) {
  if (holdFrom && Math.hypot(event.clientX - holdFrom.x, event.clientY - holdFrom.y) > 8) clearHold();
}

async function formatDocument() {
  if (!view || formatting.value) return;
  formatting.value = true;
  operationError.value = "";
  const editor = view;
  const before = editor.state;
  const startedAt = revision;
  try {
    const formatted = await formatCCode(before.doc.toString());
    if (view !== editor || revision !== startedAt) {
      if (view) operationError.value = t("compiler.editChanged");
      return;
    }
    if (formatted !== before.doc.toString()) {
      editor.dispatch({
        changes: { from: 0, to: before.doc.length, insert: formatted },
        selection: { anchor: Math.min(before.selection.main.head, formatted.length) },
        annotations: [Transaction.userEvent.of("input.format"), isolateHistory.of("full")],
      });
    }
    editor.focus();
  } catch {
    if (view) operationError.value = t("compiler.formatFailed");
  } finally { formatting.value = false; }
}

async function menuAction(action: string) {
  closeMenu();
  if (!view) return;
  if (action === "run") { emit("run"); return; }
  if (action === "import") { emit("import"); return; }
  if (action === "export") { emit("export"); return; }
  if (action === "undo") { undo(view); return; }
  if (action === "redo") { redo(view); return; }
  if (action === "format") { await formatDocument(); return; }
  const editor = view;
  const before = editor.state;
  const startedAt = revision;
  const selection = before.selection.main;
  const currentLine = before.doc.lineAt(selection.head);
  const from = selection.empty ? currentLine.from : selection.from;
  const to = selection.empty ? Math.min(currentLine.to + 1, before.doc.length) : selection.to;
  operationError.value = "";
  try {
    if (action === "copy" || action === "cut") {
      await navigator.clipboard.writeText(before.sliceDoc(from, to));
      if (action === "copy") return;
    }
    const pasted = action === "paste" ? await navigator.clipboard.readText() : "";
    if (view !== editor || revision !== startedAt || !editor.state.selection.eq(before.selection)) {
      if (view) operationError.value = t("compiler.editChanged");
      return;
    }
    editor.dispatch(action === "paste" ? {
      ...editor.state.replaceSelection(pasted), annotations: Transaction.userEvent.of("input.paste"),
    } : {
      changes: { from, to, insert: "" }, selection: { anchor: from }, annotations: Transaction.userEvent.of("delete.cut"),
    });
    editor.focus();
  } catch { operationError.value = t("compiler.clipboardFailed"); }
}

function find() {
  if (view) openSearchPanel(view);
}

onMounted(() => {
  view = new EditorView({ state: createState(props.modelValue), parent: host.value! });
  syncStatus();
});
watch(() => [props.documentKey, props.modelValue] as const, ([key, code], previous) => {
  if (!view) return;
  if (key !== previous?.[0]) {
    revision++;
    closeMenu(false);
    operationError.value = "";
    problems.value = 0;
    view.setState(createState(code));
    syncStatus();
  } else if (code !== view.state.doc.toString()) {
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: code }, annotations: isolateHistory.of("full") });
  }
});
watch(locale, () => {
  if (!view) return;
  view.dispatch({ effects: phrases.reconfigure(translations()) });
  view.contentDOM.setAttribute("aria-label", t("compiler.code"));
  forceLinting(view);
});
onBeforeUnmount(() => { clearHold(); revision++; view?.destroy(); view = undefined; });

defineExpose({ getView: () => view });
</script>

<template>
  <div class="c-editor code-palette">
    <div ref="host" class="c-editor__host" @contextmenu="contextMenu" @keydown.capture="contextKey"
      @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="clearHold" @pointercancel="clearHold" @pointerleave="clearHold"></div>
    <p v-if="operationError" class="c-editor__error" role="alert">{{ operationError }}</p>
    <div class="c-editor__status">
      <button class="c-editor__tool" type="button" :title="t('compiler.diagnostics')" :aria-label="t('compiler.diagnostics')" @click="view && openDiagnostics(view)">
        <CircleAlert :size="14" aria-hidden="true" />
        <span :class="{ 'c-editor__problem-count': problems }">{{ problems }}</span>
      </button>
      <span v-if="formatting" class="c-editor__formatting" role="status">{{ t('compiler.formatting') }}</span>
      <span class="c-editor__position">{{ t('compiler.line') }} {{ line }}, {{ t('compiler.column') }} {{ column }}</span>
      <span class="c-editor__encoding">C · UTF-8</span>
      <button class="c-editor__tool" type="button" :title="t('compiler.find')" :aria-label="t('compiler.find')" @click="find"><Search :size="15" aria-hidden="true" /></button>
      <button class="c-editor__tool" type="button" :disabled="formatting" :title="t('compiler.format')" :aria-label="t('compiler.format')" @click="formatDocument"><IndentIncrease :size="15" aria-hidden="true" /></button>
    </div>
    <Teleport to="body">
      <Transition name="editor-menu">
        <EditorContextMenu v-if="menuAt" :x="menuAt.x" :y="menuAt.y" :can-undo="canUndo" :can-redo="canRedo"
          :can-paste="canPaste" :formatting="formatting" @select="menuAction" @close="closeMenu" />
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.c-editor {
  --font-mono: "JetBrains Mono", "Cascadia Code", "SFMono-Regular", Consolas, "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Noto Sans SC", monospace;
  --code-bg: color-mix(in srgb, var(--surface) 94%, var(--bg));
  display: flex; flex-direction: column; min-width: 0; min-height: 0; height: 100%; overflow: hidden;
}
:global([data-theme="dark"]) .c-editor :deep(.c-search__label img) { filter: invert(1); }
.c-editor__host { flex: 1; min-width: 0; min-height: 0; overflow: hidden; }
.c-editor :deep(.c-fold-toggle svg) { transform-origin: center; }
.c-editor :deep(.c-fold-toggle[aria-expanded="true"] svg) { animation: c-fold-expand 200ms ease-out; }
.c-editor :deep(.c-fold-toggle[aria-expanded="false"] svg) { animation: c-fold-collapse 200ms ease-out; }
@keyframes c-fold-expand { from { transform: rotate(-90deg); } to { transform: rotate(0deg); } }
@keyframes c-fold-collapse { from { transform: rotate(90deg); } to { transform: rotate(0deg); } }
.c-editor__status { display: flex; flex: none; align-items: center; gap: 12px; min-height: 32px; padding: 0 12px; border-top: 1px solid var(--line-strong); background: var(--surface); color: var(--text-muted); font-family: var(--font-mono, monospace); font-size: 12px; }
.c-editor__tool { display: inline-flex; flex: none; align-items: center; justify-content: center; gap: 5px; height: 28px; min-width: 28px; padding: 0 4px; border: 0; border-radius: 4px; background: transparent; color: inherit; font: inherit; cursor: pointer; }
.c-editor__tool:hover { background: color-mix(in srgb, var(--text) 6%, transparent); color: var(--text); }
.c-editor__tool:focus-visible { outline: 2px solid var(--text-muted); }
.c-editor__tool:disabled { opacity: .45; cursor: default; }
.c-editor__position { margin-left: auto; white-space: nowrap; }
.c-editor__problem-count { color: #df3549; }
.c-editor__formatting { font-family: var(--font-ui); }
.c-editor__error { flex: none; margin: 0; padding: 8px 12px; border-top: 1px solid var(--line-strong); color: #df3549; background: var(--surface); font-size: 13px; }
.editor-menu-enter-active { transition: opacity 200ms, transform 200ms cubic-bezier(.23, 1, .32, 1); }
.editor-menu-leave-active { transition: opacity 140ms, transform 140ms cubic-bezier(.4, 0, 1, 1); }
.editor-menu-enter-from { opacity: 0; transform: scale(.96); }
.editor-menu-leave-to { opacity: 0; transform: scale(.98); pointer-events: none; }
@media (max-width: 520px) { .c-editor__status { gap: 6px; padding-inline: 6px; } .c-editor__encoding { display: none; } }
@media (prefers-reduced-motion: reduce) {
  .editor-menu-enter-active, .editor-menu-leave-active { transition: none; }
  .c-editor :deep(.c-fold-toggle svg) { animation: none; }
}
</style>
