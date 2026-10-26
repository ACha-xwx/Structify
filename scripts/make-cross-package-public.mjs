/**
 * After splitting controller/service packages, types that were package-private
 * must be public to remain visible across subpackages.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../backend/spring/src/main/java/com/feng/dsagent'
);

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

let files = 0;
for (const file of walk(root)) {
  let text = fs.readFileSync(file, 'utf8');
  const before = text;

  // Top-level package-private type declarations (start of line)
  text = text.replace(
    /^(final\s+)?(class|interface|enum|record)\s+(\w+)/gm,
    (m, fin, kind, name) => {
      // skip if already has public on previous token - this line shouldn't start with public
      return `public ${fin || ''}${kind} ${name}`;
    }
  );
  // Undo double public
  text = text.replace(/^public\s+public\s+/gm, 'public ');

  // Nested package-private records/classes commonly used as API DTOs (indent + record/class)
  text = text.replace(
    /^([ \t]+)(final\s+)?(class|record|enum)\s+(\w+)/gm,
    (m, indent, fin, kind, name) => {
      if (m.includes('public ')) return m;
      return `${indent}public ${fin || ''}${kind} ${name}`;
    }
  );
  text = text.replace(/^([ \t]+)public\s+public\s+/gm, '$1public ');

  if (text !== before) {
    fs.writeFileSync(file, text);
    files++;
  }
}
console.log(`Updated visibility in ${files} files`);
