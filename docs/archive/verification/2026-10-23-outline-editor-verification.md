# Outline review editor verification

The approved compact-to-expanded review interaction is implemented. Review can
open during generation with a read-only incremental list; closing it does not
stop generation. Once ready, students may edit titles/objectives, drag or use
keyboard-accessible move buttons, insert units, delete and undo. Local edits
survive collapse. Dirty or invalid edits block confirmation and AI revision.
Empty or whitespace AI feedback never reaches the revision API.

The subsequent UI refinement follows OpenMAIC's source layout in Vue: a 176×208
paper preview tilted by three degrees, actual first-four outline previews,
six-dot drag handles, numbered circles, inline auto-sized text controls, trash
icons and insertion dividers. Compatible browsers use a named View Transition
between the paper and editor surfaces; reduced-motion and unsupported browsers
fall back to the Vue state transition. React/Next.js are not introduced. Source
provenance and MIT attribution are retained in `third-party/openmaic/`.

Manual revisions use `POST /api/v1/study-outlines/{id}/manual-revisions` with
baseRevision, bounded id/title/objective entries and an idempotency key. They
save history without enqueuing a model job. The server controls resource binding,
feasibility, fragment planning, current revision, ownership and confirmation.
Changed drafts use the newly computed plan hash at confirmation.

Verification on 2026-10-23:

- Frontend full suite: 706 tests passed; production build passed.
- The source-based Vue refinement also passed the 706-test frontend suite/build,
  all 16 browser tests, and focused TypeScript checks.
- Backend full suite: 635 tests passed.
- Architecture scope and contracts: passed (40 cases, 38 paths).
- Browser suite with installed Edge: 16 tests passed, including real drag,
  deletion/undo, addition, save/reload, collapsed editor preservation and blank
  feedback. Desktop, mobile, landscape and reduced-motion regressions passed.
- Final frontend focused suite: 10 tests and TypeScript check passed.
- Runner self-tests: 6 passed. Ordinary tests used deterministic synthetic
  fixtures and did not call paid models or open the preview H2 database.

The first browser run exposed duplicate hidden progress regions; the component
now mounts only the progress region for the current state. Drag transfer data is
explicit, and deletion undo is disabled during save to prevent lost local edits.

Follow-up edge-case verification: a browser rejecting `startViewTransition` now
falls back to opening the editor rather than leaving review inaccessible. The
new regression test failed before the fix and passed after it. Eleven focused
frontend tests and typechecking passed. Five browser tests passed with long
multi-line titles at desktop, phone and landscape sizes; input scroll-height
checks confirm the titles are not clipped. Manual save/reload, real dragging,
collapse preservation and blank AI feedback remained passing.

Local preview Spring was restarted on 8792 with the existing persistent database;
Vite remains on 5173. Independent quota work and local private courseware remain
untouched. This change has not been committed, pushed or deployed.

## Classroom preparation follow-up

The reviewed-outline confirmation now opens a Vue preparation card adapted from
OpenMAIC generation-preview visualizers. Server phases drive resource preparation,
content writing and publication visuals. Failure stays on the same draft with
status retry and classroom recovery actions. First-readable publication shows a
short ready transition before entering; changing drafts or unmounting cancels it.
There are no invented percentages, agent stages or paid test calls.

Verification: 711 frontend unit tests passed; production build passed. All 17
browser regressions passed, including actual server phase changes, failed
publication, retry and entry fencing. Desktop and phone preparation screenshots
are retained under private/openmaic-vue-proof. Local preview remains on 5173/8792.

## Classroom canvas and narration dock

The classroom now follows OpenMAIC PlaybackChromeRoot's scene sidebar, flexible
canvas and persistent lower narration area. The Vue reader keeps the same
material adapters mounted and places the existing lecture controller after the
canvas. Captions appear above playback controls. Compact material tabs and a
separate directory surface reduce chrome; question recovery, slide focus regions,
code drafts, checks, quotas and publication behavior are unchanged.

A DOM-order regression failed before the move and passed after it. Browser
coverage asserts canvas-before-dock geometry and visible captions at desktop,
phone and landscape sizes, alongside the existing playback and question tests.
The source reference is components/edit/PlaybackChromeRoot.tsx at the previously
recorded OpenMAIC commit. The implementation keeps Structify's real single
lecture rather than introducing simulated agent participants.
