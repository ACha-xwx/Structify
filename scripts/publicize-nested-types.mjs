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

let n = 0;
for (const file of walk(root)) {
  let text = fs.readFileSync(file, 'utf8');
  const before = text;
  // Indented nested type without public
  text = text.replace(
    /^([ \t]+)((?:static\s+)?)((?:final\s+)?)(class|interface|enum|record)\s+(\w+)/gm,
    (m, indent, stat, fin, kind, name) => {
      if (/\bpublic\b/.test(m)) return m;
      return `${indent}public ${stat || ''}${fin || ''}${kind} ${name}`;
    }
  );
  text = text.replace(/^([ \t]+)public\s+public\s+/gm, '$1public ');
  if (text !== before) {
    fs.writeFileSync(file, text);
    n++;
  }
}
console.log(`Updated nested visibility in ${n} files`);
