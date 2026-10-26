/**
 * Move dto/vo/entity/config/exception types into capability subpackages and
 * rewrite FQCN/import references across backend/spring.
 *
 * Usage (repo root): node scripts/repackage-dto-entity-config.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const javaRoot = path.join(root, 'backend', 'spring', 'src');

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}

function packageOf(javaFile) {
  const text = fs.readFileSync(javaFile, 'utf8');
  const m = text.match(/^package\s+([\w.]+)\s*;/m);
  if (!m) throw new Error(`No package in ${javaFile}`);
  return m[1];
}

function findJavaFile(simpleName) {
  const hits = [];
  for (const base of ['main/java', 'test/java']) {
    const baseDir = path.join(javaRoot, base, 'com', 'feng', 'dsagent');
    for (const file of walk(baseDir).filter((f) => f.endsWith('.java'))) {
      if (path.basename(file, '.java') === simpleName) hits.push(file);
    }
  }
  return hits;
}

/**
 * Explicit moves: [simpleName, targetLeafPackage, optionalNewSimpleName]
 * targetLeafPackage is relative to the type's capability package (parent of current leaf
 * if already in controller/service, else current package).
 *
 * Convention: for types currently under X.service or X.controller, capability = X.
 * For types under X itself, capability = X.
 */
const MOVES = [
  // --- common.exception ---
  ['ApiException', 'common.exception', null, { absolutePkg: 'com.feng.dsagent.common.exception' }],
  ['ApiError', 'common.exception', null, { absolutePkg: 'com.feng.dsagent.common.exception' }],
  ['ApiErrors', 'common.exception', null, { absolutePkg: 'com.feng.dsagent.common.exception' }],
  ['ApiExceptionHandler', 'common.exception', null, { absolutePkg: 'com.feng.dsagent.common.exception' }],
  ['ApiErrorFallbackResolver', 'common.exception', null, { absolutePkg: 'com.feng.dsagent.common.exception' }],

  // --- admin ---
  ['AdminUser', 'entity', 'AdminUserEntity'],
  ['AdminUserQuery', 'entity'],
  ['AdminAuditQuery', 'entity'],
  ['AdminAuditEvent', 'entity'],
  ['BackgroundTaskQuery', 'entity'],
  ['AdminAuditWrite', 'dto'],
  ['AdminUserView', 'vo'],
  ['AdminCapabilityView', 'vo'],
  ['AdminPage', 'vo'],
  ['BackgroundTaskView', 'vo'],
  ['BackgroundTaskPage', 'vo'],
  ['UserStatus', null], // stay at module root (domain enum)

  // --- review ---
  ['ReviewEntity', 'entity'],
  ['ReviewSourceEntity', 'entity'],
  ['ReviewQuery', 'entity'],
  ['ReviewItemView', 'vo'],
  ['ReviewDetailView', 'vo'],
  ['ReviewHistoryView', 'vo'],
  ['ReviewSourceView', 'vo'],
  ['ReviewPage', 'vo'],
  ['ReviewType', null], // module root enum — move out of service
  ['SourceAssessment', 'support'],

  // --- auth ---
  ['UserView', 'vo'],
  ['AuthProperties', 'config'],
  ['BootstrapAdminProperties', 'config'],

  // --- chat ---
  ['ChatResponse', 'vo'],
  ['ChatMessageView', 'vo'],
  ['ChatSessionView', 'vo'],
  ['ChatAttachmentProperties', 'config'],

  // --- learning ---
  ['LearningEventView', 'vo'],
  ['LearningProgressView', 'vo'],
  ['LearningWorkbenchView', 'vo'],
  ['PersistedWorkbenchSceneView', 'vo'],

  // --- resource ---
  ['ResourceView', 'vo'],
  ['ChapterView', 'vo'],
  ['ResourceProperties', 'config'],

  // --- compiler ---
  ['CodeAnalysisResponse', 'vo'],
  ['RunCodeResponse', 'vo'],
  ['TextbookCodeLibraryResponse', 'vo'],
  ['ClassroomCodeSamplesResponse', 'vo'],
  ['CompilerProperties', 'config'],
  ['SandboxProperties', 'config'],

  // --- animation ---
  ['AnimationObservationView', 'vo'],
  ['AnimationGenerationResponse', 'vo'],
  ['DsvpSimulationResponse', 'vo'],
  ['AnimationConfiguration', 'config'],
  ['DsvpModelContract', 'prompt'],

  // --- classroom ---
  ['ClassroomSessionView', 'vo'],
  ['ClassroomConfiguration', 'config'],
  ['IllegalClassroomTransitionException', 'exception'],

  // --- studyroom ---
  ['StudyroomView', 'vo'],

  // --- knowledge ---
  ['KnowledgeConfiguration', 'config'],
  ['KnowledgeProperties', 'config'],

  // --- presentation ---
  ['PresentationProperties', 'config'],

  // --- security ---
  ['SecurityConfig', 'config', 'SecurityConfiguration'],
  ['SecurityProperties', 'config'],
  ['InvalidTokenException', 'exception'],

  // --- aiquota ---
  ['AiQuotaExecutionProperties', 'config'],
  ['AiStreamAbortedException', 'exception'],

  // --- modelconfig ---
  ['ModelConfigRuntimeUnavailableException', 'exception'],

  // --- model (keep client exception near model; optional model.exception) ---
  ['ModelClientException', 'exception'],
  ['ModelProperties', 'config'],
];

const moves = []; // { oldFqcn, newFqcn, oldSimple, newSimple, oldPkg, newPkg }

function capabilityPackage(oldPkg) {
  const strips = ['.controller', '.service', '.mapper', '.observability.controller', '.observability.service', '.observability.mapper'];
  // Prefer stripping known leaf packages once from the end
  for (const leaf of ['.controller', '.service', '.mapper', '.config', '.dto', '.vo', '.entity', '.exception', '.prompt', '.constant', '.support']) {
    if (oldPkg.endsWith(leaf)) return oldPkg.slice(0, -leaf.length);
  }
  return oldPkg;
}

for (const entry of MOVES) {
  const [simpleName, leaf, renameTo, opts] = entry;
  const newSimple = renameTo || simpleName;
  const files = findJavaFile(simpleName);
  if (files.length === 0) {
    console.warn(`SKIP missing: ${simpleName}`);
    continue;
  }
  // Prefer main over test; skip if multiple main (ambiguous)
  const mainFiles = files.filter((f) => f.includes(`${path.sep}main${path.sep}`));
  const targets = mainFiles.length ? mainFiles : files;
  if (targets.length > 1) {
    console.warn(`SKIP ambiguous ${simpleName}: ${targets.join(', ')}`);
    continue;
  }
  const file = targets[0];
  const oldPkg = packageOf(file);
  let newPkg;
  if (opts?.absolutePkg) {
    newPkg = opts.absolutePkg;
  } else if (leaf == null) {
    // Move to capability root (out of service/controller)
    newPkg = capabilityPackage(oldPkg);
  } else {
    newPkg = `${capabilityPackage(oldPkg)}.${leaf}`;
  }
  if (oldPkg === newPkg && simpleName === newSimple) {
    console.log(`OK already: ${oldPkg}.${simpleName}`);
    continue;
  }

  const destDir = path.join(
    javaRoot,
    file.includes(`${path.sep}main${path.sep}`) ? 'main' : 'test',
    'java',
    ...newPkg.split('.'),
  );
  fs.mkdirSync(destDir, { recursive: true });
  const dest = path.join(destDir, `${newSimple}.java`);
  let text = fs.readFileSync(file, 'utf8');
  text = text.replace(/^package\s+[\w.]+\s*;/m, `package ${newPkg};`);
  if (simpleName !== newSimple) {
    // Rename type declaration (class/record/enum/interface)
    text = text.replace(
      new RegExp(`\\b(public\\s+(?:final\\s+)?(?:class|record|enum|interface)\\s+)${simpleName}\\b`),
      `$1${newSimple}`,
    );
    // Constructor names for non-record
    text = text.replace(new RegExp(`\\b${simpleName}\\s*\\(`, 'g'), `${newSimple}(`);
  }
  fs.writeFileSync(dest, text);
  if (path.resolve(file) !== path.resolve(dest)) {
    fs.unlinkSync(file);
  }
  moves.push({
    oldFqcn: `${oldPkg}.${simpleName}`,
    newFqcn: `${newPkg}.${newSimple}`,
    oldSimple: simpleName,
    newSimple,
    oldPkg,
    newPkg,
  });
  console.log(`MOVE ${oldPkg}.${simpleName} -> ${newPkg}.${newSimple}`);
}

// Rewrite references
const rewriteRoots = [path.join(root, 'backend', 'spring'), path.join(root, 'docs')];
const exts = new Set(['.java', '.xml', '.yml', '.yaml', '.md', '.properties']);

function rewriteFile(file) {
  let text = fs.readFileSync(file, 'utf8');
  let changed = false;
  const sorted = [...moves].sort((a, b) => b.oldFqcn.length - a.oldFqcn.length);
  for (const m of sorted) {
    if (text.includes(m.oldFqcn)) {
      text = text.split(m.oldFqcn).join(m.newFqcn);
      changed = true;
    }
  }
  // Simple name renames (AdminUser -> AdminUserEntity) — word boundary, avoid View/Query/Learning suffixes
  for (const m of sorted) {
    if (m.oldSimple === m.newSimple) continue;
    const re = new RegExp(`\\b${m.oldSimple}\\b`, 'g');
    if (re.test(text)) {
      // Avoid double-renaming already-suffixed names that start with oldSimple
      text = text.replace(re, (match, offset) => {
        const after = text.slice(offset + match.length, offset + match.length + 20);
        // If already becomes AdminUserEntity somehow, skip
        if (after.startsWith('Entity') || after.startsWith('View') || after.startsWith('Query')
          || after.startsWith('Learning') || after.startsWith('Page')) {
          // AdminUserView etc. should NOT match AdminUser due to word boundary — good
          return match;
        }
        return m.newSimple;
      });
      changed = true;
    }
  }
  if (changed) fs.writeFileSync(file, text);
}

for (const r of rewriteRoots) {
  if (!fs.existsSync(r)) continue;
  for (const file of walk(r)) {
    if (!exts.has(path.extname(file))) continue;
    rewriteFile(file);
  }
}

// Add missing imports in java files that reference new FQCNs via short name after package split
console.log(`Done. ${moves.length} moves.`);
fs.writeFileSync(
  path.join(root, 'scripts', 'moved-dto-entity-config.json'),
  JSON.stringify(moves, null, 2),
);
