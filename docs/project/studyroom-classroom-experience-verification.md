# Continuous classroom verification — 2026-10-20

The current working tree implements the approved classroom experience. Existing
uncommitted fragment, course-library and quota changes were retained. No broad
staging, push, merge or database reset was performed.

## Delivered behavior

- Outlines expose real generation phases and closed knowledge-point previews.
  Previews remain read-only until full validation publishes a formal revision.
- Persisted event streams recover authoritative snapshots and cursors. Reconnect
  never starts a model call; cancellation is an explicit separate operation.
- Classroom questions return an idempotent 202 receipt, stream from existing
  leased background jobs and retain partial output on failure or cancellation.
- The original grid backdrop, AI ball and capsule controls surround the material
  stage, knowledge navigation and responsive question panel.
- Material adapters retain slide/zoom/scroll, animation state and local code/stdin.
  Restore checks identity and navigation; later local edits are not overwritten.
- Optional teaching actions are restricted to bound targets. Their combined
  payload is capped below the event limit, so an attachment cannot discard text.
- Single-choice checks hide answer keys before submission. Server grading records
  independent attempts without marking reading complete or inferring mastery.

## Evidence

- `node scripts/verify-studyroom.mjs --scope backend`: 622 tests passed; subsequent
  bounded-action and related HTTP/disconnect tests also passed.
- `node scripts/verify-studyroom.mjs --scope frontend`: 689 tests passed, followed
  by successful focused material/classroom tests and a production build after the
  final small reference-label change.
- `node scripts/verify-studyroom.mjs --scope contracts`: 36 cases, 34 paths passed.
- `node scripts/verify-studyroom.mjs --scope architecture`: 6 tests passed.
- `node --test scripts/verify-studyroom.test.mjs`: 6 tests passed.
- `PLAYWRIGHT_CHANNEL=msedge node scripts/verify-studyroom.mjs --scope e2e`:
  8 browser scenarios passed. Coverage includes outline previews, contextual
  questions, cancellation, reload without duplicate POST, retained code/stdin,
  server grading, teaching highlights, mobile overflow/Escape and leaving during
  preparation. The existing personal-course regression also passed.

Browser fixtures use an isolated in-memory database, public synthetic material
and a deterministic streaming model. No paid model evaluation was run. Animation
capture/pause/restore has component coverage; the browser fixture exercises code
materials rather than every production animation capability or private deck.

## Local preview

Spring runs on port 8792 and Vite on port 5173. The existing persistent development
database received only V40–V42 forward migrations, after the previous Spring
process stopped. The prior database file was backed up under ignored `private/`.
Screenshots and command logs remain under ignored local test/implementation paths.


## Compact classroom refinement (2026-10-21)

The classroom now fills the viewport with independently scrolling material,
explanation, self-check and question regions. The question composer and learning
footer remain reachable. The route collapses on phones, and short viewports use
an overlay to keep the composer visible. Hidden readers stay mounted and text
position snapshots use the local explanation scroll region.

Verification: frontend scope passed 693 tests and the production build; e2e
scope passed 9 deterministic browser tests using installed Edge
(`PLAYWRIGHT_CHANNEL=msedge`). Added coverage includes long synthetic lessons,
30 historical messages, scroll restoration, and 1366×768 / 390×844 / 844×390
viewport bounds. No paid model calls or development database resets were used.


## Sequential lecture and region focus (2026-10-21)

Backend 626 tests, frontend 698 tests plus build/types, contracts 39 cases / 37
paths, architecture 6 checks and runner 6 tests passed. Ten deterministic Edge
browser regressions passed, including a speech-completion double, saved
image-relative regions and refreshed script focus, same-level self-check,
question interruption, retained drafts and bounded desktop/mobile layouts.
The synthetic spotlight screenshot is `frontend/test-results/lecture-focus.png`
(ignored). Development migration V43 was applied after a stopped-database backup.

The implementation uses system/browser voice with explicit silent fallback;
voice audibility/quality cannot be inferred from the automated speech double.
Regions are explicitly drawn and saved, not automatically OCR-derived. Lecture
cursors and voice preferences are local to the device. Generation leases,
publication revisions and model accounting were not changed.
