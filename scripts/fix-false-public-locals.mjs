/**
 * Undo illicit `public` prefixes on local variable declarations
 * introduced by an over-broad publicize pass.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const javaRoots = [
  path.join(root, 'backend', 'spring', 'src', 'main', 'java'),
  path.join(root, 'backend', 'spring', 'src', 'test', 'java'),
];

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

let files = 0;
let fixes = 0;
for (const f of javaRoots.flatMap((d) => walk(d))) {
  let text = fs.readFileSync(f, 'utf8');
  const before = text;
  // Local vars: 8+ spaces indent, `public Type name =` without static/final
  text = text.replace(
    /^([ \t]{8,})public (?!static\b)(?!final\b)([\w.$]+(?:\s*<[^;={}]+?>)?(?:\s*\[\s*\])*)\s+(\w+)\s*=/gm,
    (m, indent, type, name) => {
      fixes++;
      return `${indent}${type} ${name} =`;
    }
  );
  if (text !== before) {
    fs.writeFileSync(f, text);
    files++;
  }
}
console.log(`Removed false public from locals in ${files} files (${fixes} sites)`);
