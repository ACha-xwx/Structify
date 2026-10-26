# Studyroom recovery and local resource update

The failed outline screen offers a direct link to a fresh question. Returning does
not retry model generation or alter the failed draft's history. The existing
learning-entry link remains available.

Local extraction bundles without a conversion manifest can be prepared with:

```powershell
node scripts/prepare-courseware-manifest.mjs private/courseware-latest
node scripts/build-original-courseware.mjs private/courseware-latest
./scripts/start-studyroom-preview.ps1
```

The bundle contains `知识库/课件` and `WebP`. Manifest preparation validates page
order, declared counts and image presence. A renamed Markdown file may bind to
one unambiguous image directory. Index generation checks WebP signatures and
hashes image bytes into resource identities. The original files remain private.
The preview script uses this bundle when present; explicit resource environment
variables take precedence. The new fourth chapter uses the existing `04-string`
chapter identity. No schema migration or database reset is required.

Local verification on 2026-10-23 found 23 decks and 1247 pages, including 37 pages
in chapter four. The running API reports the same totals and bootstrap published
1247 courseware evidence chunks. Frontend verification passed 704 tests and the
production build; backend verification passed 633 tests. The subsequently added
chapter-four loader test passed in a targeted run. Manifest tests and contract
checks also passed.

All 15 browser tests passed with `PLAYWRIGHT_CHANNEL=msedge`, including the new
failed-topic recovery test. The first attempt could not launch because the
Playwright-managed Chromium executable was absent; the installed Edge browser
ran the same suite successfully. These tests use the isolated deterministic
harness and do not invoke paid models or the development database.

The screenshot's HTTP 504 is still unresolved. A real request to this development
database returns `COMPILER_NOT_CONFIGURED` (503), so the screenshot's environment
and execution-provider configuration are needed to reproduce that failure.
Neither successful resource loading nor deterministic browser tests establish
that an external code sandbox works.
