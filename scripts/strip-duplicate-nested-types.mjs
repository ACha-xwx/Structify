/**
 * Strip nested/trailing type declarations that already exist as sibling .java files.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const javaRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../backend/spring/src/main/java'
);

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

function pkgOf(text) {
  return text.match(/^package\s+([\w.]+)\s*;/m)?.[1];
}

const files = walk(javaRoot);
const primary = new Map();
for (const f of files) {
  const text = fs.readFileSync(f, 'utf8');
  const pkg = pkgOf(text);
  if (!pkg) continue;
  const name = path.basename(f, '.java');
  primary.set(`${pkg}.${name}`, f);
}

let strippedFiles = 0;
let strippedTypes = 0;

for (const f of files) {
  let text = fs.readFileSync(f, 'utf8');
  const pkg = pkgOf(text);
  if (!pkg) continue;
  const self = path.basename(f, '.java');

  // Any top-level-looking type declaration (including package-private)
  const re = /^(?:public\s+|protected\s+|private\s+)?(record|class|enum|interface)\s+(\w+)/gm;
  const matches = [...text.matchAll(re)];
  if (matches.length === 0) continue;

  // Find types owned by another file
  const toStrip = [];
  for (const m of matches) {
    const typeName = m[2];
    if (typeName === self && m.index === matches[0].index) continue;
    const fqcn = `${pkg}.${typeName}`;
    const owner = primary.get(fqcn);
    if (!owner || owner === f) continue;
    toStrip.push({ index: m.index, name: typeName });
  }
  if (!toStrip.length) continue;

  // Remove each type by finding its declaration end (brace matching)
  // Process from end to start so indices stay valid
  toStrip.sort((a, b) => b.index - a.index);
  for (const item of toStrip) {
    let start = item.index;
    // include preceding javadoc
    const before = text.slice(0, start);
    const doc = before.match(/(\/\*\*[\s\S]*?\*\/\s*)$/);
    if (doc) start = start - doc[1].length;

    // Find opening brace or semicolon (for empty?)
    let i = text.indexOf('{', item.index);
    const semi = text.indexOf(';', item.index);
    let end;
    if (i < 0 || (semi >= 0 && semi < i && !text.slice(item.index, semi).includes('('))) {
      // enum/record might have ( before {
      end = semi + 1;
    } else {
      // Also handle record Name(...) { }
      if (i < 0) {
        console.warn('no brace for', item.name, 'in', f);
        continue;
      }
      let depth = 0;
      end = i;
      for (; end < text.length; end++) {
        const ch = text[end];
        if (ch === '{') depth++;
        else if (ch === '}') {
          depth--;
          if (depth === 0) {
            end++;
            break;
          }
        } else if (ch === '"' && text.slice(end, end + 3) === '"""') {
          const close = text.indexOf('"""', end + 3);
          end = close < 0 ? text.length : close + 2;
        } else if (ch === '"') {
          end++;
          while (end < text.length && text[end] !== '"') {
            if (text[end] === '\\') end++;
            end++;
          }
        } else if (ch === "'") {
          end++;
          while (end < text.length && text[end] !== "'") {
            if (text[end] === '\\') end++;
            end++;
          }
        }
      }
    }
    text = text.slice(0, start) + text.slice(end);
    strippedTypes++;
    console.log(`strip ${item.name} from ${path.relative(javaRoot, f)}`);
  }
  text = text.replace(/\n{3,}/g, '\n\n');
  if (!text.endsWith('\n')) text += '\n';
  fs.writeFileSync(f, text);
  strippedFiles++;
}

console.log(`Stripped ${strippedTypes} nested types from ${strippedFiles} files`);
