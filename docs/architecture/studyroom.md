# Studyroom architecture

## Implementation status

Developer harness entry points are being implemented. The fragment generation
architecture below is approved design, not yet the running implementation.
Current production code still uses reviewed outlines, UNIT jobs, per-step
generation and review, and whole-unit publication. Do not claim that first
fragment entry, partial publication, or bounded prefetch already works.

## Source of decisions

- [Approved design](../superpowers/specs/2026-10-18-progressive-studyroom-design.md)
- [Active implementation plan](../superpowers/plans/2026-10-18-progressive-studyroom.md)
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
Missing checks fail instead of being skipped. Until architecture and browser
tests land, use implemented scopes individually and record those limitations.
No ordinary check requires private courseware, a personal account, or a paid API.
