import { Marked, type TokensList } from "marked";
import DOMPurify from "dompurify";

const markdown = new Marked({ gfm: true, breaks: true });

// Some Chinese model replies omit the ATX heading space. Let the Markdown lexer
// recognize those headings, so fenced/inline code remains untouched (e.g. #include).
markdown.use({ extensions: [{
  name: "compactChineseHeading",
  level: "block",
  start(source) {
    // Marked omits the first character here; only newlines identify real line starts.
    const match = /\n {0,3}#{1,6}(?=\p{Script=Han})/u.exec(source);
    return match ? match.index + 1 : undefined;
  },
  tokenizer(source) {
    const match = /^ {0,3}(#{1,6})(?=\p{Script=Han})([^\r\n]*)(?:\r?\n|$)/u.exec(source);
    if (!match) return undefined;
    const text = match[2].replace(/\s+#+\s*$/, "").trim();
    return { type: "compactChineseHeading", raw: match[0], depth: match[1].length,
      text, tokens: this.lexer.inlineTokens(text) };
  },
  renderer(token) {
    return `<h${token.depth}>${this.parser.parseInline(token.tokens ?? [])}</h${token.depth}>\n`;
  },
}] });

function sanitize(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ["p", "br", "strong", "em", "s", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li",
      "pre", "code", "blockquote", "a", "table", "thead", "tbody", "tr", "th", "td", "hr"],
    ALLOWED_ATTR: ["href", "title", "class", "start"],
  });
}

/** Model text remains untrusted, including raw HTML and Markdown link destinations. */
export function renderAnswer(text: string): string {
  return sanitize(markdown.parse(text, { async: false }));
}

export type AnswerPart = { kind: "html"; html: string } | { kind: "code"; code: string; language: string };

/** Keep fenced code as data so Vue can own its copy action and highlighting lifecycle. */
export function answerParts(text: string): AnswerPart[] {
  const tokens = markdown.lexer(text);
  const parts: AnswerPart[] = [];
  let pending: TokensList = Object.assign([], { links: tokens.links });
  const flush = () => {
    if (pending.length) parts.push({ kind: "html", html: sanitize(markdown.parser(pending)) });
    pending = Object.assign([], { links: tokens.links });
  };
  for (const token of tokens) {
    if (token.type === "code") {
      flush();
      parts.push({ kind: "code", code: `${token.text}\n`, language: token.lang?.trim().split(/\s+/)[0] || "text" });
    } else {
      pending.push(token);
    }
  }
  flush();
  return parts;
}
