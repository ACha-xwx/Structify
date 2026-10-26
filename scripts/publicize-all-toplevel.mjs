/**
 * Make every top-level type in main/java public (and its compact ctor / static finals).
 * Safe for package split visibility without touching method bodies/SQL.
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

let n = 0;
for (const f of walk(javaRoot)) {
  let text = fs.readFileSync(f, 'utf8');
  const name = path.basename(f, '.java');
  const before = text;

  // Top-level type: first class/record/enum/interface declaration
  text = text.replace(
    /^((?:(?:package|import)\s+.+;\s*)*)((?:\/\*[\s\S]*?\*\/\s*|\/\/.*\r?\n\s*|@\w+(?:\([^)]*\))?\s+)*)(?!\s*public\b)((?:final|abstract|sealed)\s+)*(class|interface|enum|record)\s+/m,
    '$1$2public $3$4 '
  );
  text = text.replace(/\bpublic\s+public\s+/g, 'public ');

  // Compact ctor
  text = text.replace(
    new RegExp(`(\\n\\s*)(?!public\\s+|private\\s+|protected\\s+)${name}(\\s*\\{)`, 'g'),
    `$1public ${name}$2`
  );

  // Package-private constructor of the top-level type
  text = text.replace(
    new RegExp(`(\\n\\s*)(?!public\\s+|private\\s+|protected\\s+)${name}(\\s*\\()`, 'g'),
    `$1public ${name}$2`
  );

  // Package-private methods at 4-space indent (class body) — only signature lines without '=' before '('
  const lines = text.split('\n');
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const m = line.match(
      /^(\s{2,4})(?!(?:public|private|protected|static|final|default|native|abstract|synchronized|@|return|if|while|for|switch|catch|new|throw|else|case)\b)(static\s+)?([\w.$]+(?:\s*<[^>]+>)?(?:\s*\[\s*\])*)\s+(\w+)\s*\(/
    );
    if (!m) {
      // static without access
      if (/^\s{2,4}static\s+(?!public\b|private\b|protected\b)/.test(line) && !line.includes('=')) {
        // only if looks like method or field
        if (/\(/.test(line) || /static\s+final\s+/.test(line)) {
          lines[i] = line.replace(/^(\s+)/, '$1public ');
        }
      }
      continue;
    }
    const beforeParen = line.slice(0, line.indexOf('('));
    if (beforeParen.includes('=')) continue;
    const ret = m[3];
    if (['SELECT', 'FROM', 'INSERT', 'UPDATE', 'DELETE', 'COUNT', 'COALESCE'].includes(ret)) continue;
    lines[i] = line.replace(/^(\s+)/, '$1public ');
  }
  text = lines.join('\n').replace(/\bpublic\s+public\s+/g, 'public ');
  text = text.replace(/\bpublic\s+private\s+/g, 'private ');
  text = text.replace(/\bpublic\s+protected\s+/g, 'protected ');

  if (text !== before) {
    fs.writeFileSync(f, text);
    n++;
  }
}
console.log(`Publicized top-level visibility in ${n} files`);
