/**
 * Rewrite fully-qualified type references in source (not just import lines)
 * after Controller/Service package moves.
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

function pkgOf(text) {
  return text.match(/^package\s+([\w.]+)\s*;/m)?.[1];
}

const all = javaRoots.flatMap((d) => walk(d));
/** simpleName -> actual fqcn */
const typeFqcn = new Map();
for (const f of all) {
  const text = fs.readFileSync(f, 'utf8');
  const pkg = pkgOf(text);
  if (!pkg) continue;
  const name = path.basename(f, '.java');
  const fqcn = `${pkg}.${name}`;
  const leaf = pkg.split('.').pop();
  if (leaf === 'service' || leaf === 'controller') {
    typeFqcn.set(name, fqcn);
  } else if (!typeFqcn.has(name)) {
    typeFqcn.set(name, fqcn);
  }
}

// Build old-looking FQCNs that should be rewritten:
// com.feng.dsagent.foo.BarService -> com.feng.dsagent.foo.service.BarService
const replacements = [];
for (const [name, fqcn] of typeFqcn) {
  const pkg = fqcn.slice(0, -(name.length + 1));
  const leaf = pkg.split('.').pop();
  if (leaf !== 'service' && leaf !== 'controller') continue;
  const parent = pkg.slice(0, -(leaf.length + 1));
  const oldFqcn = `${parent}.${name}`;
  if (oldFqcn !== fqcn) {
    replacements.push({ from: oldFqcn, to: fqcn });
  }
}
replacements.sort((a, b) => b.from.length - a.from.length);

let files = 0;
let hits = 0;
for (const f of all) {
  let text = fs.readFileSync(f, 'utf8');
  let changed = false;
  for (const { from, to } of replacements) {
    if (!text.includes(from)) continue;
    // Avoid rewriting the correct package declaration / correct imports already containing .service.
    const re = new RegExp(`(?<!\\.)${from.replace(/\./g, '\\.')}\\b`, 'g');
    const next = text.replace(re, (match, offset) => {
      // Skip if already prefixed with .service. or .controller. immediately before — handled by lookbehind
      hits++;
      changed = true;
      return to;
    });
    text = next;
  }
  if (changed) {
    fs.writeFileSync(f, text);
    files++;
  }
}
console.log(`Rewrote FQCN refs in ${files} files (${hits} replacements, ${replacements.length} type mappings)`);
