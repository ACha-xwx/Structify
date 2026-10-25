# Working in Structify

## Authority

**Mandatory AI / agent rules:** [docs/AI开发规范.md](docs/AI开发规范.md).
Cursor injects `.cursor/rules/structify-ai-development.mdc` on every session.
If guidance conflicts, follow that constitution and Accepted backend ADRs;
implementation truth is source + OpenAPI + Flyway.

## Repository map

- `frontend/`: Vue 3 + TypeScript application. Run frontend commands here.
- `backend/spring/`: Java 21 Spring Boot API. Use the Maven wrapper here.
- `backend/node/`: existing compatibility service for other product features;
  do not remove it as part of the studyroom redesign; do not add new studyroom
  workflows here.
- `contracts/openapi-v1.yaml`: HTTP API contract.
- [Docs index](docs/README.md): living docs entry; historical material under
  `docs/archive/`.
- [Studyroom architecture](docs/architecture/studyroom.md): current behavior,
  target boundaries, and the active implementation plan.
- [Spring backend standards](docs/architecture/backend-standards.md): layering,
  MyBatis access rules, API/error conventions, and ArchUnit allowlists.
- [Node/Spring compat](docs/architecture/node-spring-compat.md): API and data-model
  migration boundaries.
- [Backend ADRs](docs/adr/backend/阅读指南.md): agent permissions, reliability,
  bounded prep, and publication decisions.

## Development

From the repository root, `./scripts/start-studyroom-preview.ps1` starts the
Spring development service on port 8792 with a local persistent database.
From `frontend/`, `npm run dev -- --port 5173 --strictPort` starts Vite.
The development server proxies `/api/v1` to Spring; root `npm run dev` starts
the separate Node compatibility service, not the Vue application.

Never start two processes against the same H2 file. Do not reset databases on
startup. Database changes use forward Flyway migrations with unique versions.
On PowerShell, quote Maven properties such as `'-Dtest=SomeTest'`.

## Verification

From the root, run `node scripts/verify-studyroom.mjs --scope <scope>`.
Scopes: `backend`, `frontend`, `contracts`, `architecture`, `e2e`, `all`.
An unimplemented check must never report success.
Test the runner with `node --test scripts/verify-studyroom.test.mjs`.
CI uses the same verification commands. Other existing project checks remain
required; this entry point does not replace unrelated regression coverage.

## Studyroom boundaries

Target flow: reviewed outline → frozen fragments → bounded preparation →
validated fragment publication → one knowledge-point navigation.
The target does not retain the former UNIT generation chain or legacy views.
During implementation, consult the architecture status before claiming the
fragment flow works. Keep plans updated with actual completed tasks.
The model proposes teaching content; server code controls resource binding,
budgets, permissions, authoritative state, and publication.
Preserve draft revisions, run fencing, leases, idempotency, and token accounting.
These are active correctness requirements, not legacy compatibility.

## Data and changes

Do not commit credentials, JWTs, `.env`, databases, `private/`, local courseware,
textbook exports, or compressed copies of them. Use synthetic/public fixtures.
Do not modify unrelated dirty files or stage with `git add .`.
Test-account quota changes may be in progress independently of the redesign.
Do not run paid model evaluations during ordinary unit or browser tests.
Delete obsolete studyroom code by tracing its callers; preserve shared
resources and other product modules. Update contracts and docs with behavior.
