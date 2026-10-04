import { HighlightStyle } from "@codemirror/language";
import { tagHighlighter, tags } from "@lezer/highlight";

// One syntax classification for the editor and read-only Chat code.
const rules = [
  { name: "keyword", tag: [tags.keyword, tags.controlKeyword] },
  { name: "type", tag: [tags.typeName, tags.className, tags.standard(tags.typeName)] },
  { name: "function", tag: [tags.function(tags.variableName), tags.function(tags.definition(tags.variableName))] },
  { name: "string", tag: [tags.string, tags.character, tags.special(tags.string)] },
  { name: "number", tag: [tags.number, tags.bool, tags.null] },
  { name: "variable", tag: [tags.variableName, tags.propertyName] },
  { name: "operator", tag: [tags.operator, tags.meta] },
  { name: "punctuation", tag: [tags.bracket, tags.punctuation] },
  { name: "comment", tag: tags.comment },
];
export const codeColors = HighlightStyle.define(rules.map(({ tag, name }) => ({ tag, color: `var(--code-${name})`, ...(name === 'comment' ? { fontStyle: 'normal' } : {}) })));
export const codeClasses = tagHighlighter(rules.map(({ tag, name }) => ({ tag, class: `code-token-${name}` })));
