/**
 * For types moved into capability leaf packages (dto/vo/entity/...), add imports
 * for sibling types that remain in the parent capability package (and common).*
 *
 * Usage: node scripts/fix-parent-package-imports.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const javaRoot = path.join(root, 'backend', 'spring', 'src', 'main', 'java');
const LEAVES = new Set(['dto', 'vo', 'entity', 'config', 'exception', 'prompt', 'constant', 'support', 'controller', 'service', 'mapper']);

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

/** Map simpleName -> [fqcn...] */
const typeIndex = new Map();
for (const file of walk(javaRoot)) {
  const text = fs.readFileSync(file, 'utf8');
  const pkg = packageOf(text);
  if (!pkg) continue;
  const simple = path.basename(file, '.java');
  const fqcn = `${pkg}.${simple}`;
  if (!typeIndex.has(simple)) typeIndex.set(simple, []);
  typeIndex.get(simple).push(fqcn);
}

const EXTRA = [
  'org.springframework.http.HttpStatus',
  'com.feng.dsagent.common.RequestIdFilter',
  'java.util.List',
  'java.util.Set',
  'java.util.Optional',
  'java.time.Instant',
];

function ensureImports(file, neededFqcn) {
  let text = fs.readFileSync(file, 'utf8');
  const pkg = packageOf(text);
  const existing = new Set([...text.matchAll(/^import\s+(?:static\s+)?([\w.]+)\s*;/gm)].map((m) => m[1]));
  const toAdd = [];
  for (const fq of neededFqcn) {
    const simple = fq.split('.').pop();
    const ownerPkg = fq.slice(0, -(simple.length + 1));
    if (ownerPkg === pkg) continue;
    if (existing.has(fq)) continue;
    toAdd.push(fq);
  }
  if (toAdd.length === 0) return false;
  toAdd.sort();
  const block = toAdd.map((fq) => `import ${fq};`).join('\n') + '\n';
  const pkgMatch = text.match(/^package\s+[\w.]+\s*;\s*\n/);
  let insertAt = pkgMatch[0].length;
  while (text[insertAt] === '\n') insertAt++;
  const importSection = text.slice(insertAt).match(/^(?:import\s+.+;\s*\n)+/);
  if (importSection) insertAt += importSection[0].length;
  fs.writeFileSync(file, text.slice(0, insertAt) + block + text.slice(insertAt));
  return true;
}

let fixed = 0;
for (const file of walk(javaRoot)) {
  const text = fs.readFileSync(file, 'utf8');
  const pkg = packageOf(text);
  if (!pkg) continue;
  const parts = pkg.split('.');
  const leaf = parts[parts.length - 1];
  // Always check moved leaf packages + common.exception
  if (!LEAVES.has(leaf) && pkg !== 'com.feng.dsagent.common.exception') continue;

  const body = text.replace(/^import\s+.+;\s*$/gm, '');
  const needed = new Set();

  for (const [simple, fqcns] of typeIndex) {
    if (!new RegExp(`\\b${simple}\\b`).test(body)) continue;
    // Prefer type in parent package or same capability
    const parent = parts.slice(0, -1).join('.');
    let pick = fqcns.find((f) => f === `${parent}.${simple}`);
    if (!pick) {
      // Prefer unique match under same capability prefix
      const capPrefix = parent; // e.g. com.feng.dsagent.admin
      const sameCap = fqcns.filter((f) => f.startsWith(capPrefix + '.') || f.startsWith(capPrefix));
      if (sameCap.length === 1) pick = sameCap[0];
      else if (fqcns.length === 1) pick = fqcns[0];
    }
    if (pick) needed.add(pick);
  }

  // Restore known deps for common.exception
  if (pkg === 'com.feng.dsagent.common.exception') {
    if (/\bHttpStatus\b/.test(body)) needed.add('org.springframework.http.HttpStatus');
    if (/\bRequestIdFilter\b/.test(body)) needed.add('com.feng.dsagent.common.RequestIdFilter');
  }

  if (ensureImports(file, [...needed])) fixed++;
}

console.log(`Fixed parent/sibling imports in ${fixed} files`);
