/**
 * Carefully publicize types living in .controller / .service packages:
 * - type declaration
 * - constructors
 * - non-private methods
 * - compact record constructors
 * - package-private static final constants
 * Does NOT touch local variables or call sites.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const mainJava = path.join(root, 'backend', 'spring', 'src', 'main', 'java');

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

function isLayerFile(file) {
  const norm = file.replace(/\\/g, '/');
  return /\/(controller|service)\/[^/]+\.java$/.test(norm);
}

let files = 0;
for (const f of walk(mainJava).filter(isLayerFile)) {
  let text = fs.readFileSync(f, 'utf8');
  const name = path.basename(f, '.java');
  const before = text;

  // Ensure top-level type is public
  text = text.replace(
    /^((?:\/\*[\s\S]*?\*\/\s*|\/\/.*\r?\n\s*)*)((?:final\s+|abstract\s+|sealed\s+)*)(class|interface|enum|record)\s+/m,
    (m, lead, mods, kind) => {
      if (/\bpublic\b/.test(lead) || /\bpublic\b/.test(mods)) return m;
      // Check immediately before for public on previous rewrite
      return `${lead}public ${mods}${kind} `;
    }
  );
  // Fix if annotation block then class without public
  text = text.replace(
    /(@(?:\w+)(?:\([^)]*\))?\s+)+(?:(final|abstract|sealed)\s+)*(class|interface|enum|record)\s+/g,
    (m) => (/\bpublic\s+(?:final\s+|abstract\s+|sealed\s+)*(?:class|interface|enum|record)/.test(m) ? m : m.replace(/(class|interface|enum|record)/, 'public $1').replace(/public public/, 'public'))
  );

  // Compact ctor
  text = text.replace(
    new RegExp(`(\\n[ \\t]*)(?:public\\s+)?${name}(\\s*\\{)`, 'g'),
    `$1public ${name}$2`
  );

  // Constructors: line with only optional annotations then Name(
  text = text.replace(
    new RegExp(`(\\n[ \\t]*)(?:public\\s+|protected\\s+|private\\s+)?${name}(\\s*\\()`, 'g'),
    (m, pre, paren) => {
      if (/private\s+$/.test(m) || m.includes('private ')) {
        // keep explicit private ctors private — but our regex consumed modifier
      }
      return m;
    }
  );
  // Simpler constructor pass: package-private ctor `  Name(`
  text = text.replace(
    new RegExp(`(\\n[ \\t]+)(${name})(\\s*\\()`, 'g'),
    '$1public $2$3'
  );
  text = text.replace(/\bpublic\s+public\s+/g, 'public ');
  text = text.replace(/\bpublic\s+private\s+/g, 'private ');
  text = text.replace(/\bpublic\s+protected\s+/g, 'protected ');

  // Methods: at indent 2–4 spaces (class body), return type + name + (
  // Exclude private/public/protected already present.
  text = text.replace(
    /^([ \t]{2,4})(?!(?:public|private|protected|static|final|default|native|abstract|synchronized|@)\b)([\w.$]+(?:\s*<[^;{}()]+?>)?(?:\s*\[\s*\])*)\s+(\w+)\s*\(/gm,
    (m, indent, ret, method) => {
      // Skip control keywords mistaken as types
      if (['if', 'while', 'for', 'switch', 'catch', 'return', 'new', 'throw', 'else', 'case'].includes(ret)) {
        return m;
      }
      // Skip assignments: ret would be weird
      return `${indent}public ${ret} ${method}(`;
    }
  );

  // static methods without access modifier: `  static Foo bar(`
  text = text.replace(
    /^([ \t]{2,4})static\s+(?!public\b)(?!private\b)(?!protected\b)/gm,
    '$1public static '
  );

  // static final constants
  text = text.replace(
    /^([ \t]{2,4})static\s+final\s+/gm,
    '$1public static final '
  );
  text = text.replace(/\bpublic\s+public\s+/g, 'public ');

  if (text !== before) {
    fs.writeFileSync(f, text);
    files++;
  }
}
console.log(`Publicized layer types in ${files} files`);
