# Studyroom architecture

## Implementation status

The working implementation uses reviewed outlines, frozen fragment plans,
FRAGMENT jobs, bounded preparation and atomic publication of individual validated
fragments. The former UNIT generation chain and graph implementation have been
removed. A published fragment remains readable when later preparation fails.

The continuous classroom adds persisted event streams with transactional
snapshots, real incremental outline previews, asynchronous FOLLOWUP questions,
material position restoration and server-graded single-choice checks. SSE reads
committed state; reconnecting or disconnecting never starts or cancels a model
invocation. The classroom experience has passed backend, frontend, contract,
architecture and deterministic browser verification; see the
[verification record](../archive/verification/studyroom-classroom-experience-verification.md).

The classroom uses a viewport-sized workspace: a collapsible knowledge route,
retained material/explanation panels, an explicitly selected self-check panel,
and a bounded question transcript with its composer kept visible. Long content
scrolls within its panel. Phone questions use a bottom sheet; short viewports use
a viewport-height overlay. Text position snapshots now refer to the reader's
scroll region, while slide, animation and code adapters retain their identities.
See the [compact classroom design](../superpowers/specs/2026-10-21-compact-classroom-design.md).

Sequential lecture scripts are compiled on the server from the current published
fragment, existing resource bindings, actual code line counts, deterministic
animation frame notes, and explicit slide-region annotations. Script compilation
does not invoke a model. Region writes are authenticated, current-run/revision
fenced, geometry validated and bounded to twelve per fragment revision; V43
retains removed regions as tombstones. Browser playback uses cancellable cues,
sentence-level system speech or timed silent captions, and device-local cursors
keyed by the script fingerprint. Manual material interaction, questions, hidden
pages and navigation stop playback. Adjacent fragments can continue within the
same point; a final check cue waits for the student and cannot complete a point.
Checks share the material navigation with slides, animations and code.

Slide region geometry is relative to the actual image, excluding object-fit
letterboxing and following zoom/resize. Automatic OCR, cloud voice generation
and cross-device lecture cursors are not part of this implementation. See the
[sequential lecture design](../superpowers/specs/2026-10-21-sequential-lecture-design.md).

## Personal course library

`/user` reads the authenticated `/api/v1/users/courses` projection of outline
drafts and current confirmed runs. Display names and user activity are stored
separately from outline content and background publication timestamps. Course
removal is a soft deletion under the per-user lock: it revokes queued/running
job leases, cancels runs, and clears the room's current-run pointer. Historical
revisions and generation accounting remain stored. Bookmarks are available at
`/user/bookmarks`; unrelated classroom bookmarks are preserved.

See [learning-center design](../superpowers/specs/2026-10-20-personal-learning-center-design.md).

## Source of decisions (studyroom redesign)

- [Approved design](../superpowers/specs/2026-10-18-progressive-studyroom-design.md)
- [Active implementation plan](../superpowers/plans/2026-10-18-progressive-studyroom.md)
- [Continuous classroom plan](../superpowers/plans/2026-10-20-studyroom-classroom-experience.md)
- [API contract](../../contracts/openapi-v1.yaml)

The project has not launched. The studyroom redesign replaces its old chain;
it does not introduce compatibility views or dual generation modes.

## Target responsibilities

1. Outline service records reviewed learning scope and revisions.
2. Content plan compiler freezes stable fragments before confirmation.
3. Preparation scheduler queues the current point and the next point's first
   fragment, prioritizing what the student is waiting to read.
4. Resource resolver assembles relevant evidence and typed resource bindings.
5. Composer writes one fragment within a shared execution budget.
6. Validator checks schema, resource authorization and teaching correctness.
7. Publication service atomically publishes validated content under a live lease.
8. Read model exposes one route of knowledge points and independent fragments.

Generation must not publish directly. Publication must not call a model.
Model output cannot authorize resources, change run identity, or reset budgets.
Published content remains readable if later work fails. A superseded run cannot
overwrite the active run. Student progress is distinct from generated readiness.

## Local verification

The root runner provides backend tests, frontend tests/build, API contracts,
architecture checks, and isolated browser tests. It stops at the first failure.
Missing checks fail instead of being skipped. Architecture checks inspect actual
bytecode boundaries. Browser checks use an isolated in-memory database and a
deterministic streaming model; they do not access the development H2 file.
No ordinary check requires private courseware, a personal account, or a paid API.

## Continuous classroom boundaries

- Streams are owned by a user and bound to an outline, current run or question.
  Events and their authoritative snapshot cursor commit together. Payloads are
  bounded to 16 KiB, replay batches to 100 events; terminal event rows expire
  after 24 hours while snapshots remain available.
- Only closed root `units` objects become read-only previews. Formal outline
  revisions still require full validation. A failed revision retains its last
  valid predecessor; no partial output can be confirmed.
- A question freezes run, fragment, content revision and validated scene context.
  POST returns 202 and an idempotent receipt. Worker jobs have priority 400 with
  null `unit_id` and `fragment_id`, so failure/cancellation cannot change the
  source lesson or its budget. Calls still use the quota ledger and lease fence.
- FOLLOWUP work has at most two model calls, 20,000 tokens and a 120-second active
  budget. Optional teaching attachments cannot discard a completed textual answer.
  Actions are limited to bound code lines, existing slide resources and actual
  deterministic animation frames; client adapters check identity and support again.
- Quiz generation is optional in the last frozen fragment's existing model call.
  Invalid checks do not reject valid lesson content. Explicit preparation uses
  a separate CHECK job, one call and a 10,000-token budget. Only server submission
  reveals the answer key. Attempts remain separate from reading completion.
- The material stage stays mounted while questions open. Local snapshots retain
  slide/zoom/scroll, animation playback and code/stdin drafts. Restoration checks
  the full material identity and navigation version before changing the screen.
