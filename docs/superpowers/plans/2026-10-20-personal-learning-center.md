# Personal Learning Center Implementation Plan

> Execute inline in the current working tree to integrate with the in-progress fragment implementation. User approved direct execution; preserve unrelated changes.

**Goal:** Deliver a functioning personal learning center with persisted course management.

**Architecture:** A dedicated course-library service reads owned draft/run/progress summaries. Existing lease and current-run fencing protect deletion. Vue uses a separate API adapter and course presentation helpers; bookmarks move to their own route.

**Tech Stack:** Spring Boot / JdbcTemplate / Flyway / H2, Vue 3 / TypeScript / Vitest / Playwright.

## Tasks

- [ ] Add `CourseLibraryServiceTest.java`: migrated isolated H2 fixtures for owned drafts and confirmed rooms, title changes and cancellation. Run `./mvnw.cmd '-Dtest=CourseLibraryServiceTest' test`, confirm missing feature failure.
- [ ] Add V39 migration and `studyroom/library/CourseLibrary{Service,Controller}.java`. GET/PATCH/DELETE only accept authenticated owner; reject deleted drafts/rooms/runs through existing repositories. Use per-user transaction locking and revoke job leases before removing current-run pointers. Verify service tests.
- [ ] Update user-action activity in `OutlineService`, `FragmentLearningService`, and `PreparationScheduler`. Background jobs do not touch library activity. Verify library tests and fragment regressions.
- [ ] Preserve current bookmarks view as `UserBookmarksView.vue` and move its existing tests. Add explicit bookmark route ahead of wildcard. Add `course-library.ts` adapter/types and presentation helpers with tests for resume ordering and routes.
- [ ] Replace `UserHomeView.vue` with approved responsive layout; extract `CourseCard.vue` and `CourseActionDialog.vue` with native modal focus behavior. Test loading/error, search, draft/preparation/learning links, rename/delete success and failure. Use shared theme/locale.
- [ ] Document endpoint schemas in `contracts/openapi-v1.yaml`; update architecture notes with actual course-library behavior, avoiding fragment implementation claims.
- [ ] Run frontend/backend/contracts verification, frontend build and browser flow using synthetic fixtures. Inspect desktop/mobile. Record outcomes below and leave only relevant files staged if committing.

## Validation outcomes

Execution pending.
