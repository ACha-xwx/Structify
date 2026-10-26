/**
 * Mechanically move production types from capability / studyroom subdomain ROOTS
 * into forced leaf packages. Skips *Repository. Does not touch common/config/model.
 *
 * Usage: node scripts/move-root-types-to-leaves.mjs [--dry-run]
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const javaMain = path.join(root, 'backend', 'spring', 'src', 'main', 'java');
const javaTest = path.join(root, 'backend', 'spring', 'src', 'test', 'java');
const dryRun = process.argv.includes('--dry-run');

const LEAVES = new Set([
  'controller', 'service', 'mapper', 'dto', 'vo', 'entity', 'query',
  'domain', 'config', 'error', 'exception', 'prompt', 'support', 'worker',
]);

/** @type {Record<string, string>} relative path under com/feng/dsagent → leaf */
const MOVES = {
  // admin
  'admin/UserStatus.java': 'domain',

  // aiquota
  'aiquota/AiQuotaAccount.java': 'entity',
  'aiquota/AiQuotaBucket.java': 'entity',
  'aiquota/AiQuotaConcurrency.java': 'domain',
  'aiquota/AiQuotaExecution.java': 'support',
  'aiquota/AiQuotaRequestId.java': 'support',
  'aiquota/AiQuotaReservation.java': 'entity',
  'aiquota/AiQuotaReservationAttempt.java': 'entity',
  'aiquota/AiQuotaReservationRecoveryRunner.java': 'worker',
  'aiquota/AiQuotaReservationRequest.java': 'dto',
  'aiquota/AiQuotaReservationStatus.java': 'domain',
  'aiquota/AiQuotaUsageSource.java': 'domain',
  'aiquota/DevTestAccountQuotaPolicy.java': 'support',

  // animation
  'animation/AnimationDefinition.java': 'domain',
  'animation/AnimationGenerationCommand.java': 'dto',
  'animation/AnimationRecord.java': 'entity',
  'animation/AnimationStep.java': 'domain',
  'animation/AnimationValidationError.java': 'domain',
  'animation/AnimationValidationResult.java': 'domain',
  'animation/AnimationValidator.java': 'support',
  'animation/DsvpAnimationAdapter.java': 'support',
  'animation/DsvpConcurrencyLimiter.java': 'support',
  'animation/DsvpEvidenceContext.java': 'domain',
  'animation/DsvpEvidenceResolution.java': 'domain',
  'animation/DsvpEvidenceResolver.java': 'support',
  'animation/DsvpEvidenceSource.java': 'domain',
  'animation/DsvpLocalEngine.java': 'support',
  'animation/DsvpSimulator.java': 'support',

  // auth
  'auth/AdminAccountProvisioner.java': 'support',
  'auth/AuthRequestRateLimiter.java': 'support',
  'auth/AuthSession.java': 'vo',
  'auth/BootstrapAdminRunner.java': 'worker',
  'auth/DefaultVerificationCodeManager.java': 'support',
  'auth/DevTestAccountRunner.java': 'worker',
  'auth/RolePolicy.java': 'support',
  'auth/UserAccount.java': 'entity',
  'auth/UsernamePolicy.java': 'support',
  'auth/VerificationCodeDelivery.java': 'domain',
  'auth/VerificationCodeHasher.java': 'support',
  'auth/VerificationCodeManager.java': 'support',
  'auth/VerificationCodeRecord.java': 'entity',
  'auth/VerificationCodeSender.java': 'support',

  // chat
  'chat/ChatAttachment.java': 'domain',
  'chat/ChatAttachmentFile.java': 'domain',
  'chat/ChatAttachmentStorage.java': 'support',
  'chat/ChatCommand.java': 'dto',
  'chat/ChatEvidenceFingerprint.java': 'support',
  'chat/ChatRateLimiter.java': 'support',
  'chat/ChatRetry.java': 'domain',
  'chat/ChatSessionSummary.java': 'vo',
  'chat/ChatSource.java': 'domain',
  'chat/ChatTurn.java': 'domain',
  'chat/PendingChat.java': 'support',
  'chat/StoredChatAttachment.java': 'entity',

  // classroom
  'classroom/ClassroomAction.java': 'domain',
  'classroom/ClassroomAnswerEvaluation.java': 'domain',
  'classroom/ClassroomAnswerStatus.java': 'domain',
  'classroom/ClassroomAskExchange.java': 'domain',
  'classroom/ClassroomBookmark.java': 'entity',
  'classroom/ClassroomBookmarkDraft.java': 'dto',
  'classroom/ClassroomEventRecord.java': 'entity',
  'classroom/ClassroomModelJson.java': 'support',
  'classroom/ClassroomPreparation.java': 'support',
  'classroom/ClassroomScript.java': 'domain',
  'classroom/ClassroomScriptParser.java': 'support',
  'classroom/ClassroomScriptPlan.java': 'domain',
  'classroom/ClassroomScriptSummary.java': 'vo',
  'classroom/ClassroomSessionRecord.java': 'entity',
  'classroom/ClassroomSlideAlignment.java': 'support',
  'classroom/ClassroomState.java': 'domain',
  'classroom/ClassroomStateMachine.java': 'support',
  'classroom/ClassroomStatus.java': 'domain',
  'classroom/ClassroomTimeline.java': 'support',
  'classroom/LessonPassageIndex.java': 'support',
  'classroom/SlideSpinePlan.java': 'support',
  'classroom/TextbookLessonIds.java': 'support',

  // compiler
  'compiler/AsciiSafeStringSerializer.java': 'support',
  'compiler/ClassroomCodeCatalog.java': 'support',
  'compiler/ClassroomCodeLesson.java': 'domain',
  'compiler/ClassroomCodeSample.java': 'domain',
  'compiler/ClassroomCodeStudyroomCatalog.java': 'support',
  'compiler/CodeAnalysisRequest.java': 'dto',
  'compiler/CodeExecutionReadiness.java': 'support',
  'compiler/CodeRunSnapshot.java': 'entity',
  'compiler/CompilerConcurrencyLimiter.java': 'support',
  'compiler/CompilerExecution.java': 'domain',
  'compiler/CompilerGateway.java': 'support',
  'compiler/HttpSandboxConnectionTester.java': 'support',
  'compiler/InteractiveCodeSession.java': 'support',
  'compiler/InteractiveRuntimeHeader.java': 'support',
  'compiler/LocalCodeRunner.java': 'worker',
  'compiler/PistonCompilerGateway.java': 'support',
  'compiler/RunCodeRequest.java': 'dto',
  'compiler/SandboxConfigHostResolver.java': 'support',
  'compiler/SandboxConfigResolvedTarget.java': 'domain',
  'compiler/SandboxConfigRuntimeSettingsSource.java': 'support',
  'compiler/SandboxConfigUrlValidator.java': 'support',
  'compiler/SandboxConnectionResult.java': 'domain',
  'compiler/SandboxConnectionTester.java': 'support',
  'compiler/SandboxProvider.java': 'domain',
  'compiler/SandboxRuntimeSettings.java': 'domain',
  'compiler/SupportedLanguage.java': 'domain',
  'compiler/SystemSandboxConfigHostResolver.java': 'support',
  'compiler/TextbookCodeChapter.java': 'domain',
  'compiler/TextbookCodeExample.java': 'domain',
  'compiler/TextbookCodeFragment.java': 'domain',
  'compiler/TextbookCodeLibrary.java': 'support',

  // knowledge
  'knowledge/CoursewareKnowledgeLoader.java': 'support',
  'knowledge/KnowledgeAudience.java': 'domain',
  'knowledge/KnowledgeBootstrapRunner.java': 'worker',
  'knowledge/KnowledgeChunk.java': 'entity',
  'knowledge/KnowledgeCorpus.java': 'domain',
  'knowledge/KnowledgeCorpusLoader.java': 'support',
  'knowledge/KnowledgeCorpusStats.java': 'vo',
  'knowledge/KnowledgeEligibilityChanged.java': 'domain',
  'knowledge/KnowledgeEligibilitySql.java': 'support',
  'knowledge/KnowledgeImportManifest.java': 'domain',
  'knowledge/KnowledgeSearchResult.java': 'vo',
  'knowledge/KnowledgeText.java': 'support',
  'knowledge/TextbookSections.java': 'support',

  // learning
  'learning/ChapterLearningProgress.java': 'vo',
  'learning/LearningEventCommand.java': 'dto',
  'learning/MasteryEstimator.java': 'support',

  // mail
  'mail/EnvironmentMailConfigMasterKeySource.java': 'support',
  'mail/JavaMailSenderRoutingBeanPostProcessor.java': 'config',
  'mail/JavaMailTransport.java': 'support',
  'mail/MailConfigCrypto.java': 'support',
  'mail/MailConfigKeyBinding.java': 'domain',
  'mail/MailConfigMasterKeySource.java': 'support',
  'mail/MailConfigStartupCheck.java': 'support',
  'mail/MailConnection.java': 'domain',
  'mail/MailPolicy.java': 'domain',
  'mail/MailTemplateRenderer.java': 'support',
  'mail/MailTransport.java': 'support',
  'mail/MailVerificationCodeSender.java': 'support',
  'mail/SecurityMode.java': 'domain',
  'mail/SmtpEndpointRouting.java': 'support',

  // modelconfig
  'modelconfig/DatabaseModelConfigRuntimeSettingsSource.java': 'support',
  'modelconfig/EnvironmentModelConfigMasterKeySource.java': 'support',
  'modelconfig/HttpModelConfigConnectionTester.java': 'support',
  'modelconfig/ModelConfigConnection.java': 'domain',
  'modelconfig/ModelConfigConnectionResult.java': 'domain',
  'modelconfig/ModelConfigConnectionTester.java': 'support',
  'modelconfig/ModelConfigCrypto.java': 'support',
  'modelconfig/ModelConfigGenerationControls.java': 'domain',
  'modelconfig/ModelConfigHostResolver.java': 'support',
  'modelconfig/ModelConfigKeyBinding.java': 'domain',
  'modelconfig/ModelConfigMasterKeySource.java': 'support',
  'modelconfig/ModelConfigResolvedTarget.java': 'domain',
  'modelconfig/ModelConfigRuntimeClientFactory.java': 'support',
  'modelconfig/ModelConfigRuntimeSettings.java': 'domain',
  'modelconfig/ModelConfigRuntimeSettingsSource.java': 'support',
  'modelconfig/ModelConfigUrlValidator.java': 'support',
  'modelconfig/ModelGenerationReadiness.java': 'support',
  'modelconfig/OpenAiCompatibleModelConfigRuntimeClientFactory.java': 'support',
  'modelconfig/PersistedModelConfigClient.java': 'support',
  'modelconfig/PinnedHttpsConnection.java': 'support',
  'modelconfig/PinnedHttpsConnectionFactory.java': 'support',
  'modelconfig/PinnedHttpsRequest.java': 'domain',
  'modelconfig/PinnedHttpsResponse.java': 'domain',
  'modelconfig/PinnedHttpsTransport.java': 'support',
  'modelconfig/PinnedOpenAiCompatibleModelClient.java': 'support',
  'modelconfig/SystemModelConfigHostResolver.java': 'support',
  'modelconfig/TlsPinnedHttpsConnectionFactory.java': 'support',

  // presentation
  'presentation/PresentationCatalog.java': 'support',
  'presentation/PresentationSlide.java': 'domain',
  'presentation/SlideImageLinks.java': 'support',
  'presentation/SlidePlanner.java': 'support',

  // resource
  'resource/ResourceAsset.java': 'support',
  'resource/ResourceAudience.java': 'domain',
  'resource/ResourceContent.java': 'domain',

  // review
  'review/ReviewType.java': 'domain',

  // security
  'security/AuthenticatedUser.java': 'domain',
  'security/JwtAuthenticationFilter.java': 'support',
  'security/NodeCompatibilityToken.java': 'domain',
  'security/NodeCompatibilityTokenIssuer.java': 'support',
  'security/NodeCompatibilityTokenVerifier.java': 'support',
  'security/NodeCompatibilityUserResolver.java': 'support',

  // studyroom root
  'studyroom/StudyroomFollowUpContext.java': 'dto',
  'studyroom/StudyroomModelOutput.java': 'support',
  'studyroom/StudyroomRecords.java': 'entity',
  'studyroom/StudyroomResourceCatalog.java': 'support',
  'studyroom/StudyroomResourceSelector.java': 'support',
  'studyroom/StudyroomScene.java': 'domain',
  'studyroom/StudyroomSceneAdapter.java': 'support',

  // studyroom subdomains
  'studyroom/checks/ClassroomQuestionBank.java': 'support',
  'studyroom/curriculum/CurriculumCatalog.java': 'support',
  'studyroom/draft/LearningDepth.java': 'domain',
  'studyroom/draft/OutlineDiff.java': 'support',
  'studyroom/draft/OutlineIncrementalParser.java': 'support',
  'studyroom/draft/OutlineJson.java': 'support',
  'studyroom/draft/OutlineModelGateway.java': 'support',
  'studyroom/draft/OutlineValidator.java': 'support',
  'studyroom/draft/ReviewedOutline.java': 'domain',
  'studyroom/events/ClassroomEvent.java': 'domain',
  'studyroom/events/ClassroomEventSubscriptions.java': 'support',
  'studyroom/fragment/ContentPlanCompiler.java': 'support',
  'studyroom/fragment/FragmentBudget.java': 'domain',
  'studyroom/fragment/FragmentComposer.java': 'support',
  'studyroom/fragment/FragmentExecutionContext.java': 'domain',
  'studyroom/fragment/FragmentOutputContract.java': 'support',
  'studyroom/fragment/FragmentPlan.java': 'domain',
  'studyroom/fragment/FragmentResourceResolver.java': 'support',
  'studyroom/fragment/FragmentValidator.java': 'support',
  'studyroom/fragment/PreparationScheduler.java': 'worker',
  'studyroom/fragment/TeachingReviewContract.java': 'support',
  'studyroom/generation/GenerationLease.java': 'support',
  'studyroom/generation/StudyroomGenerationWorker.java': 'worker',
  'studyroom/questions/ClassroomQuestionWorker.java': 'worker',
  'studyroom/teaching/LectureCompiler.java': 'support',
  'studyroom/teaching/LectureRegion.java': 'domain',
  'studyroom/teaching/TeachingActionValidator.java': 'support',
};

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

function ensureDir(p) {
  fs.mkdirSync(p, { recursive: true });
}

function pkgFromRel(relPosix) {
  const withoutJava = relPosix.replace(/\.java$/, '');
  const dirs = withoutJava.split('/');
  dirs.pop();
  return ['com', 'feng', 'dsagent', ...dirs].join('.');
}

function toPosix(p) {
  return p.split(path.sep).join('/');
}

const basePkgDir = path.join(javaMain, 'com', 'feng', 'dsagent');
/** @type {{ oldFqcn: string, newFqcn: string, simple: string, from: string, to: string }[]} */
const planned = [];
const missing = [];
const skippedRepos = [];

for (const [rel, leaf] of Object.entries(MOVES)) {
  if (!LEAVES.has(leaf)) throw new Error(`Invalid leaf ${leaf} for ${rel}`);
  const fromAbs = path.join(basePkgDir, ...rel.split('/'));
  if (!fs.existsSync(fromAbs)) {
    missing.push(rel);
    continue;
  }
  const simple = path.basename(rel, '.java');
  if (simple.endsWith('Repository')) {
    skippedRepos.push(rel);
    continue;
  }
  const parts = rel.split('/');
  parts.pop();
  const parentLeaf = parts[parts.length - 1];
  if (LEAVES.has(parentLeaf)) {
    throw new Error(`Already in leaf? ${rel}`);
  }
  const destRelParts = [...parts, leaf, `${simple}.java`];
  const toAbs = path.join(basePkgDir, ...destRelParts);
  const oldPkg = pkgFromRel(rel);
  const newPkg = pkgFromRel(destRelParts.join('/'));
  planned.push({
    oldFqcn: `${oldPkg}.${simple}`,
    newFqcn: `${newPkg}.${simple}`,
    simple,
    from: fromAbs,
    to: toAbs,
    rel,
    leaf,
  });
}

// Discover any remaining root types not in MOVES (non-repo)
const PLATFORM = new Set(['common', 'config', 'model']);
const uncovered = [];
for (const file of walk(basePkgDir)) {
  const rel = toPosix(path.relative(basePkgDir, file));
  const parts = rel.split('/');
  const simple = path.basename(file, '.java');
  if (simple.endsWith('Repository')) continue;
  if (parts.length === 1) {
    // capability root: cap/Type.java
    const cap = parts[0];
    // wait — length 1 means file directly under dsagent
    continue;
  }
  // parts: [cap, Type.java] => length 2 root
  // or [studyroom, Type.java]
  // or [studyroom, subdomain, Type.java]
  const dirs = parts.slice(0, -1);
  let isRoot = false;
  if (dirs.length === 1 && !PLATFORM.has(dirs[0])) isRoot = true;
  else if (dirs.length === 2 && dirs[0] === 'studyroom' && !LEAVES.has(dirs[1])) isRoot = true;
  if (!isRoot) continue;
  if (!MOVES[rel] && !rel.endsWith('Repository.java')) {
    // Application class lives under com.feng.dsagent directly — dirs would be empty for that
    uncovered.push(rel);
  }
}

console.log(`Planned moves: ${planned.length}`);
if (missing.length) console.warn('Missing on disk:', missing);
if (skippedRepos.length) console.warn('Skipped repos in map:', skippedRepos);
if (uncovered.length) {
  console.warn(`Uncovered root types (${uncovered.length}):`);
  for (const u of uncovered.sort()) console.warn('  ', u);
}

if (dryRun) {
  for (const m of planned) console.log(`${m.rel} → ${m.leaf}`);
  process.exit(0);
}

// Execute moves
for (const m of planned) {
  let text = fs.readFileSync(m.from, 'utf8');
  const updated = text.replace(
    /^package\s+[\w.]+\s*;/m,
    `package ${m.newFqcn.slice(0, -(m.simple.length + 1))};`,
  );
  if (updated === text && !text.includes(`package ${m.oldFqcn.slice(0, -(m.simple.length + 1))};`)) {
    // still try
  }
  ensureDir(path.dirname(m.to));
  if (path.resolve(m.from) === path.resolve(m.to)) {
    fs.writeFileSync(m.to, updated);
  } else {
    fs.writeFileSync(m.to, updated);
    fs.unlinkSync(m.from);
  }
}

// Rewrite imports / FQCNs across main+test
const allJava = [...walk(javaMain), ...walk(javaTest)];
// Longest FQCN first to avoid partial replacements of nested names
const replacements = planned
  .map((m) => ({ from: m.oldFqcn, to: m.newFqcn }))
  .sort((a, b) => b.from.length - a.from.length);

let filesTouched = 0;
for (const file of allJava) {
  let text = fs.readFileSync(file, 'utf8');
  let next = text;
  for (const { from, to } of replacements) {
    if (!next.includes(from)) continue;
    // Replace FQCN occurrences (imports and qualified refs)
    next = next.split(from).join(to);
  }
  if (next !== text) {
    fs.writeFileSync(file, next);
    filesTouched++;
  }
}

// Fix same-package short-name breakage: add imports when a file references a moved type by simple name
const typeFqcn = new Map();
const ambiguous = new Map();
for (const file of allJava) {
  // re-read after moves — file list may include deleted paths; filter exists
}
const allAfter = [...walk(javaMain), ...walk(javaTest)];
for (const file of allAfter) {
  const text = fs.readFileSync(file, 'utf8');
  const pkg = text.match(/^package\s+([\w.]+)\s*;/m)?.[1];
  if (!pkg) continue;
  const name = path.basename(file, '.java');
  const fqcn = `${pkg}.${name}`;
  if (typeFqcn.has(name) && typeFqcn.get(name) !== fqcn) {
    if (!ambiguous.has(name)) ambiguous.set(name, new Set([typeFqcn.get(name)]));
    ambiguous.get(name).add(fqcn);
  } else {
    typeFqcn.set(name, fqcn);
  }
}

// Prefer moved leaf packages for ambiguous names
for (const [name, set] of ambiguous) {
  const preferred = [...set].find((f) => {
    const leaf = f.split('.').at(-2);
    return LEAVES.has(leaf);
  });
  if (preferred) typeFqcn.set(name, preferred);
}

let importsAdded = 0;
let importFiles = 0;
for (const file of allAfter) {
  let text = fs.readFileSync(file, 'utf8');
  const pkg = text.match(/^package\s+([\w.]+)\s*;/m)?.[1];
  if (!pkg) continue;
  const self = path.basename(file, '.java');
  const existing = new Set(
    [...text.matchAll(/^import\s+(?:static\s+)?([\w.]+)\s*;/gm)].map((m) => m[1]),
  );
  const starPkgs = new Set(
    [...text.matchAll(/^import\s+([\w.]+)\.\*\s*;/gm)].map((m) => m[1]),
  );
  const needed = [];
  for (const m of planned) {
    if (m.simple === self) continue;
    const fqcn = m.newFqcn;
    const typePkg = fqcn.slice(0, -(m.simple.length + 1));
    if (typePkg === pkg) continue;
    if (existing.has(fqcn)) continue;
    if (starPkgs.has(typePkg)) continue;
    // Avoid matching inside strings/comments naively — still OK for compile-driven fix
    const re = new RegExp(`\\b${m.simple}\\b`);
    if (!re.test(text)) continue;
    // Skip if only mentioned in import of different type ending with same? already exact
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

// Write manifest
const manifestPath = path.join(root, 'scripts', 'moved-root-types.json');
fs.writeFileSync(
  manifestPath,
  JSON.stringify(
    planned.map((m) => ({
      simple: m.simple,
      leaf: m.leaf,
      oldFqcn: m.oldFqcn,
      newFqcn: m.newFqcn,
      rel: m.rel,
    })),
    null,
    2,
  ),
);

console.log(`Moved ${planned.length} files`);
console.log(`Rewrote FQCNs in ${filesTouched} files`);
console.log(`Added ${importsAdded} imports across ${importFiles} files`);
console.log(`Manifest: ${manifestPath}`);
if (uncovered.length) process.exitCode = 2;
