/**
 * Rewrite imports after Controller/Service package split.
 * Builds simpleName -> FQCN from disk, then:
 * 1) Fixes wrong/outdated import lines
 * 2) Adds missing imports for same-package references that broke
 * 3) Expands star-imports that no longer cover moved types
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

function simpleName(file) {
  return path.basename(file, '.java');
}

const all = javaRoots.flatMap((d) => walk(d));
/** @type {Map<string, string>} simpleName -> fqcn (unique) */
const typeFqcn = new Map();
/** @type {Map<string, Set<string>>} */
const ambiguous = new Map();

for (const f of all) {
  const text = fs.readFileSync(f, 'utf8');
  const pkg = pkgOf(text);
  if (!pkg) continue;
  const name = simpleName(f);
  const fqcn = `${pkg}.${name}`;
  if (typeFqcn.has(name) && typeFqcn.get(name) !== fqcn) {
    if (!ambiguous.has(name)) ambiguous.set(name, new Set([typeFqcn.get(name)]));
    ambiguous.get(name).add(fqcn);
    // Prefer .service / .controller when colliding with parent leftovers
    const current = typeFqcn.get(name);
    if (fqcn.includes('.service.') || fqcn.endsWith(`.service.${name}`) ||
        fqcn.includes('.controller.') || fqcn.endsWith(`.controller.${name}`)) {
      typeFqcn.set(name, fqcn);
    } else if (!(current.includes('.service.') || current.includes('.controller.'))) {
      // keep first
    }
  } else {
    typeFqcn.set(name, fqcn);
  }
}

if (ambiguous.size) {
  console.warn('Ambiguous simple names (using preferred):');
  for (const [n, set] of [...ambiguous.entries()].sort()) {
    console.warn(`  ${n}: ${[...set].join(' | ')} -> ${typeFqcn.get(n)}`);
  }
}

let filesTouched = 0;
let importsRewritten = 0;
let importsAdded = 0;

for (const f of all) {
  let text = fs.readFileSync(f, 'utf8');
  const pkg = pkgOf(text);
  if (!pkg) continue;
  const self = simpleName(f);
  const selfFqcn = `${pkg}.${self}`;
  let changed = false;

  // 1) Rewrite existing import lines that point at wrong package for a known type
  text = text.replace(/^import\s+([\w.]+)\s*;/gm, (line, fqcn) => {
    const name = fqcn.split('.').pop();
    const actual = typeFqcn.get(name);
    if (!actual || actual === fqcn) return line;
    // Only rewrite if the imported name matches a moved type (package differs)
    if (actual.split('.').pop() !== name) return line;
    // Don't rewrite static imports of nested members incorrectly
    if (line.includes('static')) return line;
    importsRewritten++;
    changed = true;
    return `import ${actual};`;
  });

  // 2) Collect existing imports
  const existing = new Set(
    [...text.matchAll(/^import\s+(?:static\s+)?([\w.]+)\s*;/gm)].map((m) => m[1])
  );
  const starPkgs = new Set(
    [...text.matchAll(/^import\s+([\w.]+)\.\*\s*;/gm)].map((m) => m[1])
  );

  // 3) Find referenced type names that need imports
  const needed = new Set();
  for (const [name, fqcn] of typeFqcn) {
    if (name === self) continue;
    if (fqcn === selfFqcn) continue;
    const typePkg = fqcn.slice(0, -(name.length + 1));
    if (typePkg === pkg) continue; // same package
    // Covered by star import?
    if ([...starPkgs].some((sp) => typePkg === sp || typePkg.startsWith(sp + '.'))) {
      // star only covers exact package, not subpackages
      if (starPkgs.has(typePkg)) continue;
    }
    if (existing.has(fqcn) || existing.has(name) /* unlikely */) {
      if (existing.has(fqcn)) continue;
    }
    // Check if already imported under any form
    let already = false;
    for (const imp of existing) {
      if (imp === fqcn || imp.endsWith('.' + name)) {
        already = true;
        break;
      }
    }
    if (already) continue;

    // Is the name used as a type/identifier in this file?
    const re = new RegExp(`\\b${name}\\b`);
    if (!re.test(text)) continue;

    // Skip if only appears inside import/package lines we already handle
    const stripped = text
      .replace(/^package\s+[\w.]+;/m, '')
      .replace(/^import\s+.+;/gm, '');
    if (!re.test(stripped)) continue;

    needed.add(fqcn);
  }

  if (needed.size) {
    const lines = [...needed].sort().map((fq) => `import ${fq};`);
    text = text.replace(/^(package .+;\r?\n)/m, `$1\n${lines.join('\n')}\n`);
    importsAdded += lines.length;
    changed = true;
  }

  if (changed) {
    fs.writeFileSync(f, text);
    filesTouched++;
  }
}

console.log(
  `Touched ${filesTouched} files; rewritten ${importsRewritten} imports; added ${importsAdded} imports`
);
