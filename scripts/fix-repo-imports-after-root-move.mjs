/**
 * After root→leaf moves, repair:
 * 1) FQCN prefix corruption (*Repository rewritten under .entity/.domain/…)
 * 2) Missing imports for *Repository types left at capability roots
 * 3) package-private constants broken by package split (KnowledgeImportManifest)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const javaRoots = [
  path.join(root, 'backend', 'spring', 'src', 'main', 'java'),
  path.join(root, 'backend', 'spring', 'src', 'test', 'java'),
];
const LEAVES = new Set([
  'controller', 'service', 'mapper', 'dto', 'vo', 'entity', 'query',
  'domain', 'config', 'error', 'exception', 'prompt', 'support', 'worker',
]);

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

const all = javaRoots.flatMap((d) => walk(d));

/** simpleName -> fqcn for *Repository */
const repoFqcn = new Map();
for (const file of all) {
  const name = path.basename(file, '.java');
  if (!name.endsWith('Repository')) continue;
  const text = fs.readFileSync(file, 'utf8');
  const pkg = text.match(/^package\s+([\w.]+)\s*;/m)?.[1];
  if (!pkg) continue;
  repoFqcn.set(name, `${pkg}.${name}`);
}

let corruptedFixed = 0;
let filesTouched = 0;

for (const file of all) {
  let text = fs.readFileSync(file, 'utf8');
  let next = text;

  // Fix imports/refs like com.feng.dsagent.foo.entity.BarRepository → com.feng.dsagent.foo.BarRepository
  next = next.replace(
    /com\.feng\.dsagent\.([a-z0-9]+(?:\.[a-z0-9]+)*)\.(entity|domain|support|dto|vo|worker|config|query|error|exception|prompt)\.([A-Za-z0-9_]+Repository)\b/g,
    (match, parentPath, leaf, simple) => {
      const correct = repoFqcn.get(simple);
      if (correct) {
        corruptedFixed++;
        return correct;
      }
      // Fallback: strip the leaf segment
      corruptedFixed++;
      return `com.feng.dsagent.${parentPath}.${simple}`;
    },
  );

  if (next !== text) {
    fs.writeFileSync(file, next);
    filesTouched++;
    text = next;
  }
}

// Add missing Repository imports for short-name references
let importsAdded = 0;
let importFiles = 0;
for (const file of all) {
  let text = fs.readFileSync(file, 'utf8');
  const pkg = text.match(/^package\s+([\w.]+)\s*;/m)?.[1];
  if (!pkg) continue;
  const self = path.basename(file, '.java');
  const existing = new Set(
    [...text.matchAll(/^import\s+(?:static\s+)?([\w.]+)\s*;/gm)].map((m) => m[1]),
  );
  const needed = [];
  for (const [simple, fqcn] of repoFqcn) {
    if (simple === self) continue;
    const typePkg = fqcn.slice(0, -(simple.length + 1));
    if (typePkg === pkg) continue;
    if (existing.has(fqcn)) continue;
    if (!new RegExp(`\\b${simple}\\b`).test(text)) continue;
    needed.push(fqcn);
  }
  if (!needed.length) continue;
  const unique = [...new Set(needed)].sort();
  const block = unique.map((fq) => `import ${fq};`).join('\n') + '\n';
  const pkgMatch = text.match(/^package\s+[\w.]+\s*;\s*\n/);
  if (!pkgMatch) continue;
  let insertAt = pkgMatch[0].length;
  while (text[insertAt] === '\n') insertAt++;
  const importSection = text.slice(insertAt).match(/^(?:import\s+.+;\s*\n)+/);
  if (importSection) insertAt += importSection[0].length;
  text = text.slice(0, insertAt) + block + text.slice(insertAt);
  fs.writeFileSync(file, text);
  importFiles++;
  importsAdded += unique.length;
}

// KnowledgeImportManifest: package-private constants used from support
const manifest = path.join(
  root,
  'backend/spring/src/main/java/com/feng/dsagent/knowledge/domain/KnowledgeImportManifest.java',
);
if (fs.existsSync(manifest)) {
  let t = fs.readFileSync(manifest, 'utf8');
  const before = t;
  t = t.replace(/\n    static final String FILE_NAME/, '\n    public static final String FILE_NAME');
  t = t.replace(
    /\n    static final String REVIEWED_LABEL_PREFIX/,
    '\n    public static final String REVIEWED_LABEL_PREFIX',
  );
  t = t.replace(
    /\n    static final String LOCAL_DEVELOPMENT_LABEL_PREFIX/,
    '\n    public static final String LOCAL_DEVELOPMENT_LABEL_PREFIX',
  );
  if (t !== before) {
    fs.writeFileSync(manifest, t);
    console.log('Made KnowledgeImportManifest constants public');
  }
}

console.log(`Fixed ${corruptedFixed} corrupted Repository FQCNs in ${filesTouched} files`);
console.log(`Added ${importsAdded} Repository imports across ${importFiles} files`);
console.log(`Known repositories: ${repoFqcn.size}`);
