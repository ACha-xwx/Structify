# Continuous classroom display verification

Implemented the approved display-first scope. The original outline and objectives
remain available, validated explanation text becomes lossless presentation pages,
and original PPT images, code and animations share a page navigator. The full
explanation lives in a retained right notes panel; directory thumbnails and notes
start closed. Lecture controls are buttons beneath the canvas. Export is deferred.

The display adapter reuses validated text rather than making a second AI call.
It limits each page to 360 Unicode code points, prefers sentence boundaries and
keeps every character of the complete explanation. Large legacy explanations
may therefore contain more pages than newly authored compact lessons.

Canonical bank selection replaces generated checks. Bank question ID and a
content hash are persisted with each snapshot. Answer keys stay out of ordinary
reads. The teacher receives canonical explanations and the latest attempt, with
instructions to give hints before an attempt. Legacy generated questions require
bank preparation; their historical attempts are retained.

Bank delivery JSON and exact topic bindings are external local configuration,
not repository content. The supplied bank is draft material. The local preview
explicitly opts in and has bindings for the inspected array topics. Only
single-choice entries are selected; missing bindings or eligible questions show
an unavailable state. The code-implementation topic has no fabricated binding.

Earlier external UI adaptations were independently replaced. No external source,
style, asset or runtime was copied into the replacement components. The unused
external attribution files were removed after the replacement audit.

Verification:

- 723 frontend unit tests passed across 96 files.
- All 18 browser regressions passed with synthetic test data and no paid models.
- Production frontend build and TypeScript checks passed.
- Full backend suite passed, followed by focused bank/upgrade/progress tests for
  the final legacy-question exclusion behavior.
- Contract fixtures passed: 40 cases over 38 paths. Architecture runner passed.
- Verification-runner self-tests passed: 6 tests.
- Real preview restarted on 8792 using the existing persistent H2 database; V44
  applied successfully and health returned HTTP 200. Vite remains on 5173.

Browser evidence is retained privately in private/continuous-classroom-proof.
Independent quota changes and private source archives were not staged. No code
changes were pushed or deployed. The design document has its separate local
documentation commit; implementation is available for review in the working tree.

## User-requested presentation rollback

The user rejected the independent redesign of outline/preparation views and the
new deterministic text-slide presentation. Restored the prior paper review card,
compact editable outline and animated preparation views from complete session
file records. Classroom View, Reader, SceneStage, KnowledgeRoute and slide controls
were restored to the prior material/explanation interface, including the previously
accepted lower lecture area. Restored external attribution for the recovered
adapted UI; no claim of independent implementation applies to the recovered files.

Existing databases, outline revisions, course material and attempts were not reset.
Bank and server correctness work remains; new presentation modules are inactive.
The rejected working tree and source records are backed up privately under
private/rollback-20261023. Earlier verification above describes the rejected build,
not the current restored interface. Rollback verification: typecheck and build
passed, 13 focused outline/preparation tests passed, and a synthetic browser test
confirmed paper review, compact editing and absence of the new text-slide viewer.

## Exact 17:07 rollback (supersedes the preceding rollback scope)

Later bank selection, revision-map completion, continuous-page components and text slides are now withdrawn as well. The earlier manual-outline API and paper UI remain. The independent clickable prototype is preserved at localhost:49608, separate from the live application on 5173. Existing persistent course data remains intact. V44 stays as applied migration history with inactive additive columns.

Verification after this rollback: frontend scope passed 94 files / 711 tests and production build; 19 focused backend tests passed; contracts passed 40 fixtures / 38 paths; synthetic browser rollback passed using installed Edge (default Playwright Chromium was unavailable). The preview service was restarted with the existing database. No paid model calls, push or deployment occurred.
