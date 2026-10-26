/**
 * Add imports for moved types into test sources that still use short names.
 * Usage: node scripts/fix-test-imports-after-move.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const moves = JSON.parse(
  fs.readFileSync(path.join(root, 'scripts', 'moved-dto-entity-config.json'), 'utf8'),
);
const testRoot = path.join(root, 'backend', 'spring', 'src', 'test', 'java');

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (full.endsWith('.java')) out.push(full);
  }
  return out;
}

function packageOf(text) {
  const m = text.match(/^package\s+([\w.]+)\s*;/m);
  return m ? m[1] : null;
}

let fixed = 0;
for (const file of walk(testRoot)) {
  let text = fs.readFileSync(file, 'utf8');
  const pkg = packageOf(text);
  if (!pkg) continue;
  const existing = new Set([...text.matchAll(/^import\s+(?:static\s+)?([\w.]+)\s*;/gm)].map((m) => m[1]));
  const needed = [];
  for (const m of moves) {
    if (existing.has(m.newFqcn)) continue;
    if (pkg === m.newPkg) continue;
    if (!new RegExp(`\\b${m.newSimple}\\b`).test(text)) continue;
    // Also catch old simple if renamed and still present
    needed.push(m.newFqcn);
  }
  // Also rewrite old FQCNs
  let changed = false;
  for (const m of [...moves].sort((a, b) => b.oldFqcn.length - a.oldFqcn.length)) {
    if (text.includes(m.oldFqcn)) {
      text = text.split(m.oldFqcn).join(m.newFqcn);
      changed = true;
    }
    if (m.oldSimple !== m.newSimple) {
      const re = new RegExp(`\\b${m.oldSimple}\\b`, 'g');
      if (re.test(text)) {
        text = text.replace(re, m.newSimple);
        changed = true;
      }
    }
  }
  if (needed.length) {
    const unique = [...new Set(needed)].sort();
    const block = unique.map((fq) => `import ${fq};`).join('\n') + '\n';
    const pkgMatch = text.match(/^package\s+[\w.]+\s*;\s*\n/);
    let insertAt = pkgMatch[0].length;
    while (text[insertAt] === '\n') insertAt++;
    const importSection = text.slice(insertAt).match(/^(?:import\s+.+;\s*\n)+/);
    if (importSection) insertAt += importSection[0].length;
    text = text.slice(0, insertAt) + block + text.slice(insertAt);
    changed = true;
  }
  if (changed) {
    fs.writeFileSync(file, text);
    fixed++;
  }
}
console.log(`Fixed ${fixed} test files`);
