# Personal learning center

Approved in chat on 2026-10-20, including layout, lifecycle, and deletion semantics. The user requested direct implementation and review of the final result.

## Scope and layout

Replace `/user`'s bookmark-only view with a personal learning center. Keep Structify's shared BrandStage, theme tokens, and responsive styling. Reference the local OpenMAIC library's search, course cards, rename, and delete interactions. No folders in this release.

The page contains a greeting and new-learning link, one prominent resume panel, a searchable course grid ordered by recent activity, and links to separate bookmarks and learning-map pages. Never invent course data or progress for an empty account.

## Identity and lifecycle

A course begins as a persisted outline draft. After confirmation, its current room/run supplies preparation and progress; it remains one library entry. When a revised outline is confirmed into an existing room, show its current draft only, rather than duplicate historical runs. Unconfirmed revisions remain separate drafts until confirmed.

Statuses: DRAFT, PREPARING, LEARNING, COMPLETED, NEEDS_ATTENTION. Draft generation failures retain any prior revision. Readable fragments allow entry during preparation. Completed means all knowledge points have recorded completion. Resume uses the server cursor and does not generate a new course. Latest learning activity takes priority in the resume panel; without learning activity use the most recently edited task. Fully completed courses offer review.

Explicit user actions update activity; background publication must not reorder the library. Search is by displayed title, case insensitive. Rename changes only a display title (1–160 trimmed characters). Sorting uses activity descending with a stable ID tie break.

## API and persistence

Add authenticated `/api/v1/users/courses` GET, `/users/courses/{id}` PATCH and DELETE. Course IDs are draft IDs; summaries include room/run IDs, status, completion counts, readable count, last point, last learning time and activity time. List contains only owned, visible drafts and current confirmed runs. User ownership is checked server-side for every mutation.

Use a forward Flyway migration for title, activity, and soft-deletion fields. Delete is idempotent for an owned record and requires confirmation in the UI. Under the existing per-user lock, cancel queued/running related jobs and revoke their leases, cancel related runs, clear the current room run, hide its drafts and bookmarks, and reject further draft/room/run access. Preserve revisions, generation call records, token accounting, and shared resources. A late worker must fail existing lease/current-run checks. Do not reset any database.

Existing bookmarks belong to the classroom module; preserve that module and move its current view to `/user/bookmarks`. Only hide a bookmark when its owned session identifies the removed room; unrelated classroom bookmarks remain intact.

## Errors, accessibility, and validation

First-load failure has retry; failed actions retain existing cards and dialog inputs. Distinguish an empty library from no search results. Dialogs have accessible titles, focus containment, Escape handling, and focus restoration; destructive requests disable duplicate submits. Support keyboard navigation, dark/light themes, reduced motion and mobile without horizontal overflow.

Tests use migrated isolated H2 and synthetic browser data, with no paid models. Cover ownership, deduplication, status derivation, resume priority, stable activity, rename validation, repeated deletion and revoked worker leases; frontend tests cover resume links, search, dialogs, errors and bookmark navigation. Run repository verification entry points and production frontend build. Browser verification checks desktop/mobile and the final page.
