import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(
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

let fixed = 0;
for (const file of walk(root)) {
  if (!/[\\/](controller|service)[\\/]/.test(file)) continue;
  const name = path.basename(file, '.java');
  let text = fs.readFileSync(file, 'utf8');
  const before = text;

  // public   {  or public final   {
  text = text.replace(
    /public(\s+final)?\s+\{\s*\r?\n/,
    (_m, fin) => `public${fin || ''} class ${name} {\n`
  );

  // package-private top-level Controller/Service
  text = text.replace(
    new RegExp(`^((?:@[\\w.]+(?:\\([^)]*\\))?\\s*\\r?\\n)*)(final\\s+)?class\\s+${name}\\b`, 'm'),
    (m, annos, fin) => {
      if (/\bpublic\s+(final\s+)?class\s+/.test(m)) return m;
      return `${annos}public ${fin || ''}class ${name}`;
    }
  );

  // If still "class Name" without public on its own line after annotations
  const lines = text.split(/\n/);
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^(public\s+)?(final\s+)?class\s+(\w+)(.*)$/);
    if (m && m[3] === name) {
      if (!m[1]) lines[i] = `public ${m[2] || ''}class ${name}${m[4]}`;
      break;
    }
  }
  text = lines.join('\n');

  if (text !== before) {
    fs.writeFileSync(file, text);
    fixed++;
  }
}

console.log(`Restored ${fixed} class declarations`);
