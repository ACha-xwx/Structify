import { Marked, type TokensList } from "marked";
import DOMPurify from "dompurify";

const markdown = new Marked({ gfm: true, breaks: true });

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
