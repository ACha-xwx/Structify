/**
 * Publicize package-private types/members without touching string/text-block contents.
 * Targets: .controller / .service packages, plus *Repository* and other files listed via --all-main.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const mainJava = path.join(root, 'backend', 'spring', 'src', 'main', 'java');
const testJava = path.join(root, 'backend', 'spring', 'src', 'test', 'java');

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

/** Replace text blocks and string literals with same-length spaces so line offsets stay stable enough for line-based edits. */
function maskLiterals(text) {
  const chars = text.split('');
  const maskRange = (start, end) => {
    for (let i = start; i < end; i++) {
      if (chars[i] !== '\n' && chars[i] !== '\r') chars[i] = ' ';
    }
  };
  for (let i = 0; i < text.length; i++) {
    // text block """
    if (text.startsWith('"""', i)) {
      const close = text.indexOf('"""', i + 3);
      if (close < 0) break;
      maskRange(i, close + 3);
      i = close + 2;
      continue;
    }
    // double-quoted string
    if (text[i] === '"') {
      let j = i + 1;
      while (j < text.length) {
        if (text[j] === '\\') {
          j += 2;
          continue;
        }
        if (text[j] === '"') break;
        if (text[j] === '\n') break;
        j++;
      }
      maskRange(i, Math.min(j + 1, text.length));
      i = j;
    }
  }
  return chars.join('');
}

function shouldProcess(file) {
  const norm = file.replace(/\\/g, '/');
  if (/\/(controller|service)\/[^/]+\.java$/.test(norm)) return true;
  if (/Repository\.java$/.test(norm)) return true;
  // Domain types extracted beside services that stay in parent package
  if (/\/(AdminUser|AdminPage|UserStatus|ReviewType|ReviewPage|Review\w+View|SourceAssessment|ModelConfigGenerationControls)\.java$/.test(norm)) {
    return true;
  }
  return false;
}

function publicize(text) {
  const masked = maskLiterals(text);
  const lines = text.split('\n');
  const maskedLines = masked.split('\n');
  const nameMatch = text.match(/(?:class|interface|enum|record)\s+(\w+)/);
  const typeName = nameMatch?.[1];
  const SQL = new Set([
    'SELECT', 'FROM', 'INSERT', 'UPDATE', 'DELETE', 'WITH', 'CREATE', 'ALTER', 'DROP',
    'WHERE', 'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON', 'AND', 'OR', 'SET', 'VALUES',
    'ORDER', 'GROUP', 'LIMIT', 'OFFSET', 'INTO', 'TABLE', 'INDEX', 'COUNT', 'COALESCE',
  ]);

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const mLine = maskedLines[i];
    // Skip if masked line is all/mostly spaces where original had content in strings - compare using masked for matching

    // class/interface/enum/record declaration
    let m = mLine.match(/^(\s*)((?:@\w+(?:\([^)]*\))?\s+)*)((?:final|abstract|sealed)\s+)*(class|interface|enum|record)\s+(\w+)/);
    if (m && !/\bpublic\b/.test(mLine) && !/\bprivate\b/.test(mLine)) {
      lines[i] = line.replace(
        /((?:final|abstract|sealed)\s+)*(class|interface|enum|record)/,
        'public $1$2'
      ).replace('public public ', 'public ');
      continue;
    }

    if (!typeName) continue;

    // compact record ctor: Name {
    m = mLine.match(new RegExp(`^(\\s*)${typeName}\\s*\\{`));
    if (m && !/\b(public|private|protected)\b/.test(mLine)) {
      lines[i] = line.replace(new RegExp(`${typeName}\\s*\\{`), `public ${typeName} {`);
      continue;
    }

    // constructor: Name(
    m = mLine.match(new RegExp(`^(\\s*)${typeName}\\s*\\(`));
    if (m && !/\b(public|private|protected)\b/.test(mLine)) {
      lines[i] = line.replace(new RegExp(`${typeName}\\s*\\(`), `public ${typeName}(`);
      continue;
    }

    // method: ReturnType name(  or  static ReturnType name(
    m = mLine.match(
      /^(\s+)(?!(?:public|private|protected|return|if|while|for|switch|catch|new|throw|else|case|default)\b)(static\s+)?(final\s+)?([\w.$]+(?:\s*<[^>]+>)?(?:\s*\[\s*\])*)\s+(\w+)\s*\(/
    );
    if (m) {
      const ret = m[4];
      const method = m[5];
      if (SQL.has(ret) || SQL.has(method)) continue;
      if (/\b(public|private|protected)\b/.test(mLine)) continue;
      // Skip local vars: masked line should not contain = before (
      const beforeParen = mLine.slice(0, mLine.indexOf('('));
      if (beforeParen.includes('=')) continue;
      // Only class-body indent (2-4 spaces typical; allow up to 8 for nested types)
      const indent = m[1].length;
      if (indent > 8) continue;
      if (mLine.includes('static final')) {
        lines[i] = line.replace(/^(\s+)/, '$1public ');
      } else {
        lines[i] = line.replace(/^(\s+)/, '$1public ');
      }
      lines[i] = lines[i].replace(/\bpublic\s+public\s+/g, 'public ');
      continue;
    }

    // static final fields
    m = mLine.match(/^(\s+)static\s+final\s+/);
    if (m && !/\b(public|private|protected)\b/.test(mLine)) {
      const indent = m[1].length;
      if (indent <= 8) {
        lines[i] = line.replace(/^(\s+)/, '$1public ');
      }
    }
  }

  return lines.join('\n').replace(/\bpublic\s+public\s+/g, 'public ');
}

let n = 0;
for (const f of [...walk(mainJava), ...walk(testJava)].filter(shouldProcess)) {
  const before = fs.readFileSync(f, 'utf8');
  const after = publicize(before);
  if (after !== before) {
    fs.writeFileSync(f, after);
    n++;
  }
}
console.log(`Safe-publicized ${n} files`);
