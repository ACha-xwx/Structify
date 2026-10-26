import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const src = path.join(root, 'backend/spring/src/main/java/com/feng/dsagent');

function read(rel) {
  return fs.readFileSync(path.join(src, rel), 'utf8');
}
function write(rel, text) {
  fs.writeFileSync(path.join(src, rel), text);
}

// 1) ClassroomBookmark: drop nested draft
{
  const rel = 'classroom/ClassroomBookmark.java';
  let t = read(rel);
  t = t.replace(/\r?\n\/\*\* The row to write[\s\S]*$/, '\n');
  write(rel, t);
  console.log('ClassroomBookmark cleaned');
}

// 2) Enum constructors must be private
for (const rel of [
  'animation/DsvpEvidenceSource.java',
  'model/ModelErrorCode.java',
  'compiler/SupportedLanguage.java',
]) {
  const name = path.basename(rel, '.java');
  let t = read(rel);
  const next = t.replace(new RegExp(`public ${name}\\(`, 'g'), `private ${name}(`);
  if (next !== t) {
    write(rel, next);
    console.log('enum ctor private:', name);
  }
}

// 3) Truncate PinnedHttpsTransport to primary class only
{
  const rel = 'modelconfig/PinnedHttpsTransport.java';
  let t = read(rel);
  const start = t.search(/^(?:public\s+)?(?:final\s+)?class\s+PinnedHttpsTransport\b/m);
  if (start < 0) throw new Error('PinnedHttpsTransport class not found');
  let i = t.indexOf('{', start);
  let depth = 0;
  for (; i < t.length; i++) {
    const ch = t[i];
    if (ch === '"' && t.slice(i, i + 3) === '"""') {
      const c = t.indexOf('"""', i + 3);
      i = c < 0 ? t.length : c + 2;
      continue;
    }
    if (ch === '"') {
      i++;
      while (i < t.length && t[i] !== '"') {
        if (t[i] === '\\') i++;
        i++;
      }
      continue;
    }
    if (ch === '{') depth++;
    else if (ch === '}') {
      depth--;
      if (depth === 0) {
        i++;
        break;
      }
    }
  }
  write(rel, t.slice(0, i).replace(/\s+$/, '\n'));
  console.log('PinnedHttpsTransport truncated');
}

// 4) Publicize nested types used across packages
function publicizeNested(rel, typeName) {
  let t = read(rel);
  const re = new RegExp(
    `((?:public|private|protected)\\s+)?((?:static\\s+)?)((?:final\\s+)?)(record|class|interface|enum)\\s+${typeName}\\b`
  );
  const next = t.replace(re, (m, access, st, fin, kind) => {
    if (access && access.includes('public')) return m;
    return `public ${st || ''}${fin || ''}${kind} ${typeName}`;
  });
  if (next !== t) {
    write(rel, next);
    console.log('nested public:', rel, typeName);
  } else {
    console.log('nested unchanged:', rel, typeName);
  }
}

publicizeNested('presentation/PresentationCatalog.java', 'Snapshot');
publicizeNested('compiler/InteractiveCodeSession.java', 'Listener');

// 5) KnowledgeImportManifest Entry compact ctor
{
  const rel = 'knowledge/KnowledgeImportManifest.java';
  let t = read(rel);
  const next = t.replace(/(\n\s*)Entry(\s*\{)/g, '$1public Entry$2');
  if (next !== t) {
    write(rel, next);
    console.log('Entry compact ctor publicized');
  }
}
