export async function highlightChatCode(code: string, language: string): Promise<string> {
  const { bundledLanguages, codeToHtml } = await import("shiki/bundle/web");
  const name = language.toLowerCase();
  const lang = Object.hasOwn(bundledLanguages, name) ? name : "text";
  const className = /^[a-z0-9_+-]+$/i.test(language) ? language : "text";
  return codeToHtml(code, {
    lang,
    themes: { light: "github-light", dark: "github-dark" },
    defaultColor: false,
    transformers: [{
      code(node) { this.addClassToHast(node, `language-${className}`); },
    }],
  });
}
