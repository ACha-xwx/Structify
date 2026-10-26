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
  const matches = [...text.matchAll(/^(?:public\s+)?(?:final\s+)?(class|interface|enum|record)\s+(\w+)/gm)];
  if (matches.length !== 1) continue;
  if (/^public\s/m.test(matches[0][0])) continue;
  const before = text;
  text = text.replace(
    /^(final\s+)?(class|interface|enum|record)\s+(\w+)/m,
    'public $1$2 $3'
  );
  text = text.replace(/^public\s+public\s+/m, 'public ');
  if (text !== before) {
    fs.writeFileSync(file, text);
    n++;
  }
}
console.log(`Publicized ${n} single-type files`);
