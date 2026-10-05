import { cppLanguage } from "@codemirror/lang-cpp";
import { highlightTree } from "@lezer/highlight";
import { codeClasses } from "../shared/compiler/code-highlight";

const escape = (text: string) => text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
let otherHighlighter: ReturnType<typeof import("shiki/bundle/web").createHighlighter> | undefined;

export async function renderCode(code: string, language: string): Promise<string> {
  const name = language.toLowerCase();
  const safeName = /^[a-z0-9_+-]+$/i.test(name) ? name : "text";
  if (["c", "h", "cpp", "c++", "cc", "cxx"].includes(name)) {
    const spans: string[] = [];
    let position = 0;
    highlightTree(cppLanguage.parser.parse(code), codeClasses, (from, to, classes) => {
      spans.push(escape(code.slice(position, from)), `<span class="${classes}">${escape(code.slice(from, to))}</span>`);
      position = to;
    });
    spans.push(escape(code.slice(position)));
    return `<pre><code class="language-${safeName}">${spans.join("")}</code></pre>`;
  }
  if (["text", "plaintext", ""].includes(name) || safeName === "text") {
    return `<pre><code class="language-text">${escape(code)}</code></pre>`;
  }
  // Other grammars load only for those code blocks; C never loads Shiki/WASM.
  const { bundledLanguages, createHighlighter } = await import("shiki/bundle/web");
  const lang = Object.hasOwn(bundledLanguages, name) ? name as keyof typeof bundledLanguages : "text";
  if (!otherHighlighter) otherHighlighter = import("shiki/engine/javascript").then(({ createJavaScriptRegexEngine }) => createHighlighter({
    themes: ["github-light", "github-dark"], langs: [], engine: createJavaScriptRegexEngine(),
  }));
  const highlighter = await otherHighlighter;
  if (lang !== "text" && !highlighter.getLoadedLanguages().includes(lang)) await highlighter.loadLanguage(bundledLanguages[lang]);
  return highlighter.codeToHtml(code, {
    lang,
    themes: { light: "github-light", dark: "github-dark" }, defaultColor: false,
    transformers: [{ code(node) { this.addClassToHast(node, `language-${safeName}`); } }],
  });
}
