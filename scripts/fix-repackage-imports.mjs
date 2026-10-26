/**
 * After controller/service split, add imports for types that stayed in the parent package
 * (previously same-package, no import needed).
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

function pkgOf(file) {
  const m = fs.readFileSync(file, 'utf8').match(/^package\s+([\w.]+)\s*;/m);
  return m?.[1];
}

function simpleName(file) {
  return path.basename(file, '.java');
}

const all = walk(mainJava).concat(walk(testJava));
const byPkg = new Map();
for (const f of all) {
  const p = pkgOf(f);
  if (!p) continue;
  if (!byPkg.has(p)) byPkg.set(p, []);
  byPkg.get(p).push({ file: f, name: simpleName(f) });
}

let fixed = 0;
for (const f of all) {
  const p = pkgOf(f);
  if (!p) continue;
  const leaf = p.split('.').pop();
  if (leaf !== 'controller' && leaf !== 'service') continue;
  const parentPkg = p.slice(0, -(leaf.length + 1));
  const parentTypes = byPkg.get(parentPkg) || [];
  // also sibling controller/service packages under same parent
  const siblingPkgs = [`${parentPkg}.controller`, `${parentPkg}.service`, `${parentPkg}.mapper`].filter(
    (x) => x !== p
  );
  const candidates = [
    ...parentTypes.map((t) => ({ fqcn: `${parentPkg}.${t.name}`, name: t.name })),
    ...siblingPkgs.flatMap((sp) =>
      (byPkg.get(sp) || []).map((t) => ({ fqcn: `${sp}.${t.name}`, name: t.name }))
    ),
  ];
  // Sort longer names first
  candidates.sort((a, b) => b.name.length - a.name.length);

  let text = fs.readFileSync(f, 'utf8');
  const selfName = simpleName(f);
  let added = [];
  for (const c of candidates) {
    if (c.name === selfName) continue;
    const importLine = `import ${c.fqcn};`;
    if (text.includes(importLine)) continue;
    // Avoid importing when only appears as substring of another identifier
    const re = new RegExp(`\\b${c.name}\\b`);
    if (!re.test(text)) continue;
    added.push(importLine);
  }
  if (!added.length) continue;
  // Dedupe and insert after package
  added = [...new Set(added)];
  text = text.replace(/^(package .+;\r?\n)/m, `$1\n${added.join('\n')}\n`);
  fs.writeFileSync(f, text);
  fixed++;
}

console.log(`Updated imports in ${fixed} controller/service files`);
