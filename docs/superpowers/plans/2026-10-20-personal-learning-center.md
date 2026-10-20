# Personal Learning Center Implementation Plan

> Execute inline in the current working tree to integrate with the in-progress fragment implementation. User approved direct execution; preserve unrelated changes.

**Goal:** Deliver a functioning personal learning center with persisted course management.

**Architecture:** A dedicated course-library service reads owned draft/run/progress summaries. Existing lease and current-run fencing protect deletion. Vue uses a separate API adapter and course presentation helpers; bookmarks move to their own route.

**Tech Stack:** Spring Boot / JdbcTemplate / Flyway / H2, Vue 3 / TypeScript / Vitest / Playwright.

## Tasks

- [x] Add `CourseLibraryServiceTest.java`: migrated isolated H2 fixtures for owned drafts and confirmed rooms, title changes and cancellation. Run `./mvnw.cmd '-Dtest=CourseLibraryServiceTest' test`, confirm missing feature failure.
- [x] Add V39 migration and `studyroom/library/CourseLibrary{Service,Controller}.java`. GET/PATCH/DELETE only accept authenticated owner; reject deleted drafts/rooms/runs through existing repositories. Use per-user transaction locking and revoke job leases before removing current-run pointers. Verify service tests.
- [x] Update user-action activity in `OutlineService`, `FragmentLearningService`, and `PreparationScheduler`. Background jobs do not touch library activity. Verify library tests and fragment regressions.
- [x] Preserve current bookmarks view as `UserBookmarksView.vue` and move its existing tests. Add explicit bookmark route ahead of wildcard. Add `course-library.ts` adapter/types and presentation helpers with tests for resume ordering and routes.
- [x] Replace `UserHomeView.vue` with approved responsive layout; extract `CourseCard.vue` and `CourseActionDialog.vue` with native modal focus behavior. Test loading/error, search, draft/preparation/learning links, rename/delete success and failure. Use shared theme/locale.
- [x] Document endpoint schemas in `contracts/openapi-v1.yaml`; update architecture notes with actual course-library behavior, avoiding fragment implementation claims.
- [x] Run frontend/backend/contracts verification, frontend build and browser flow using synthetic fixtures. Inspect desktop/mobile. Record outcomes below and leave only relevant files staged if committing.

## Validation outcomes

- Frontend verification entry point: 86 files, 686 tests passed; production build and TypeScript checks passed.
- Backend verification entry point: 599 tests passed before final review corrections. Final focused library/outline regression: 14 tests passed, including HTTP authentication/title validation and display-title inheritance.
- Contracts: 23 fixture cases passed. Verification-runner tests: 6 passed. Architecture scope: 6 tests passed.
- Browser scope: 4 tests passed using `PLAYWRIGHT_CHANNEL=msedge` because the bundled Chromium executable is absent. All browser content uses the synthetic harness; no paid model evaluations.
- Desktop and 390px mobile screenshots inspected; mobile has no horizontal overflow. Rename survives reload; deletion disappears after reload and room access returns 404.
- Review corrections: invalidate in-flight list responses on mutation; inherit personal display titles when confirming a same-room replacement outline.
- Local preview: Spring 8792 (existing persistent H2, forward migrations only), Vite 5173; final route `/user`. No credentials, databases, or courseware added to git.
- Implementation remains in the existing working tree alongside prior studyroom changes; no mixed implementation commit or unrelated staging.
