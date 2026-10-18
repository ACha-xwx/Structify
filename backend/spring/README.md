# Data Structure Agent Spring Backend

`backend/spring` is the Spring Boot backend for the data-structure learning agent. It is a modular monolith designed for the team's first vertical slice: approved stack-and-queue content, course-grounded chat, a script-driven classroom, structured animations, and sandboxed code experiments.

The existing Node.js prototype remains a reference implementation during migration. The Spring API uses `/api/v1/*` on port `8792`; it does not replace the legacy `/api/*` endpoints until the new frontend has completed integration.

## Implemented modules

- Account registration, login, password reset, email verification codes, JWT cookie/Bearer authentication, and `STUDENT` / `TEACHER` / `ADMIN` roles.
- Published chapter and resource metadata, plus safe resource streaming without exposing server filesystem paths.
- Reviewed textbook knowledge loading, retrieval, source-aware chat, SSE streaming, and persisted chat history for signed-in users.
- Reviewed classroom scripts, a deterministic classroom state machine, answer evaluation, misconception feedback, and classroom session persistence.
- Whitelist-validated animation definitions for stack, queue, list, tree, heap, hash table, and array demonstrations.
- C/Python execution through a configured remote Piston-compatible or Judge0 sandbox, with bounded input/output, timeout, rate limiting, and concurrency limiting. User code is never run with `ProcessBuilder`, a shell, or a temporary executable on the Spring host. The administrator API persists the selected provider and service URL; a persisted configuration takes precedence over startup environment fallbacks and can be disabled fail-closed.
- Shared learning records and per-chapter activity progress across chat, classroom, animation, resource, and code workflows.

## Run locally

Requirements: Java 21. Maven is provided through the wrapper.

```powershell
cd backend/spring
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

The explicit `dev` profile starts with an in-memory H2 database at `http://127.0.0.1:8792`, enables local verification-code echoing, and may import local knowledge files. The base configuration requires `JWT_SECRET` and keeps those development conveniences disabled, so an accidentally unprofiled deployment fails closed. Health endpoint:

Local course resources now come from the original `知识库/` and `WebP/` directories at the repository root. Before starting Spring, run `node scripts/build-original-courseware.mjs` from the repository root to validate and index all 22 decks / 1,210 pages. The dev profile imports the per-page PPT text into `knowledge_chunks` and serves the corresponding original WebP images. `KNOWLEDGE_DIR`, `PRESENTATION_DIR`, and `PRESENTATION_IMAGE_DIR` can override these paths. These are PPT page references, not verified textbook/PDF page references. Chapters 04 and 10 have no original decks in this bundle and receive no invented content. The repeatable dev migration removes old `dev-mock/` chunks, and the former demo routes redirect to the real studyroom.

```text
GET http://127.0.0.1:8792/actuator/health
```

The `dev` profile also seeds a student account for local testing: `student@structify.test` / `StructifyTest123!`. It is created only when missing and never resets an existing account password. Override the values with `DEV_TEST_ACCOUNT_EMAIL`, `DEV_TEST_ACCOUNT_USERNAME`, and `DEV_TEST_ACCOUNT_PASSWORD` when needed.

For a packaged-JAR health check without development conveniences, use the `verification` profile. It binds only to `127.0.0.1:8793`, uses a separate in-memory H2 database, disables mail and local knowledge publishing, and expects temporary empty resource directories through `KNOWLEDGE_DIR` and `RESOURCE_DIR` when those paths need to be exercised.

Without `MODEL_API_KEY`, authentication, chapters, resources, classroom sessions, and progress still start. Chat, animation generation, and code analysis return `MODEL_NOT_CONFIGURED` instead of silently inventing an answer.

## Environment variables

Use [`deployment/.env.spring.example`](../../deployment/.env.spring.example) as the starting point. Keep the real file outside source control.

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | MySQL JDBC connection. Local development defaults to H2. |
| `JWT_SECRET` | Random Spring session-token secret with at least 64 characters. Do not provide it to the Node container. |
| `NODE_COMPAT_ENABLED`, `NODE_COMPAT_JWT_SECRET` | Temporary Node-to-Spring migration bridge. Use a different 64+ character key; Node Bearer tokens are accepted only for the documented learning/animation evidence endpoints while enabled. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact frontend origins allowed to call the API with credentials. Production requires `https://structify.cn,https://admin.structify.cn`. |
| `BOOTSTRAP_ADMIN_EMAIL` | Leave empty in production. Static administrator elevation is intentionally disabled. |
| `TEACHER_EMAILS` | Leave empty in production; role changes require an audited operator workflow. |
| `MODEL_PROVIDER`, `MODEL_API_KEY`, `MODEL_BASE_URL`, `MODEL_NAME` | OpenAI-compatible model configuration. DeepSeek/OpenAI-compatible providers use Bearer auth; `azure` / `azure-openai` use the `api-key` header. |
| `MODEL_MAX_RESPONSE_BYTES` | Maximum buffered or streamed model response size; defaults to 32 MiB, including SSE envelopes. Set existing deployments to `33554432` if their environment explicitly retains the old 1 MiB value. |
| `KNOWLEDGE_DIR` | Private textbook directory containing `lessons/*.md`. |
| `KNOWLEDGE_AUTO_PUBLISH_LOCAL` | Keep `false` in production so only database-reviewed chunks enter retrieval. |
| `RESOURCE_DIR` | Private root for published PDF, PPT, code, and exercise files. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM` | Verification-code mail delivery configuration. |
| `SMTP_SSL`, `SMTP_STARTTLS`, `SMTP_STARTTLS_REQUIRED` | SMTP transport security. Use implicit TLS or required STARTTLS, not both. |
| `SMTP_*_TIMEOUT_MS`, `SMTP_SSL_CHECK_SERVER_IDENTITY` | SMTP connection/read/write limits and TLS hostname verification; keep hostname verification enabled. |
| `MODEL_CONFIG_MASTER_KEY`, `MAIL_CONFIG_MASTER_KEY` | Optional base64-encoded 32-byte AES keys for the encrypted administrator model and SMTP configuration pages. Keep them only in the external secret store. |
| `PISTON_BASE_URL` | Optional Piston service root used when no administrator-persisted sandbox configuration exists. Production requires an explicit value if Piston is selected. |
| `JUDGE0_BASE_URL` | Optional Judge0 service root used when no administrator-persisted sandbox configuration exists. Production requires an explicit value if Judge0 is selected. |
| `EXECUTE_*` | Compiler timeout, input/output length, and concurrency limits. |

There is no public default administrator password and no production bootstrap administrator. Keep `BOOTSTRAP_ADMIN_EMAIL` and `TEACHER_EMAILS` empty, and keep `ALLOW_FIRST_USER_TEACHER=false`. The current release has no self-service role elevation workflow; any privileged account must be created through a reviewed, auditable database operation outside the public API.

For implicit TLS, normally on port 465, set `SMTP_SSL=true`, `SMTP_STARTTLS=false`, and `SMTP_STARTTLS_REQUIRED=false`. For STARTTLS, normally on port 587, set `SMTP_SSL=false`, `SMTP_STARTTLS=true`, and `SMTP_STARTTLS_REQUIRED=true`. The application defaults `SMTP_STARTTLS_REQUIRED` to the value of `SMTP_STARTTLS`, so an enabled upgrade cannot silently fall back to plaintext.

## Production deployment

The complete `structify.cn` and `admin.structify.cn` deployment, including Caddy routing, Node 8791,
Spring 8792, MySQL, private course/PPT paths, backups, smoke checks, and
rollback is documented in [`PRODUCTION_DEPLOYMENT_GUIDE.md`](../../PRODUCTION_DEPLOYMENT_GUIDE.md).
The executable Compose topology is [`deployment/docker-compose.production.yml`](../../deployment/docker-compose.production.yml).
Use the deployment template only outside source control and keep the real file
at `/etc/structify/structify.env` with mode `0600`.

During migration, Caddy sends `/api/v1/*` to this service and the legacy
`/api/*` surface to Node. The exact route and persistence differences are in
[`docs/api-node-spring-differences.md`](../../docs/api-node-spring-differences.md)
and [`docs/data-model-node-spring-differences.md`](../../docs/data-model-node-spring-differences.md).

Production defaults fail closed: real `JWT_SECRET` and `NODE_COMPAT_JWT_SECRET`
values plus MySQL URL/credentials are required. SMTP, model, and remote sandbox
explicitly disabled until configured; development-code exposure,
knowledge auto-publish, debug retrieval, static role elevation, and code
capture are disabled. Actuator remains a loopback health endpoint with hidden
details.

## Reviewed course content

Private OCR textbook files, teacher PPT files, and other restricted course material must remain outside the public Git repository. The recommended private directory is:

```text
private/course-content/
  chapters/
    03-stack-queue/
      slides/
      code/
      exercises/
      classroom/
```

Resource metadata is stored in MySQL. Both the resource and its parent chapter must be `PUBLISHED` before delivery. Guests see `PUBLIC`, signed-in students also see `CLASSROOM_ONLY`, and `TEACHER` / `ADMIN` accounts also see `TEAM_ONLY`. The same rule applies to list, detail, and content endpoints. `resources.file_path` must be a relative path below `RESOURCE_DIR`; the API exposes only `/api/v1/resources/{id}/content`, never the underlying path. Files outside that root, traversal paths, absolute paths, and symlink escapes are rejected.

Knowledge retrieval follows the same publication and audience boundary. `GET /api/v1/knowledge/search` is guest-compatible and returns only reviewed (`PUBLISHED`) chunks from published chapters/resources: guests receive `PUBLIC`, students additionally receive `CLASSROOM_ONLY`, and teachers/administrators additionally receive `TEAM_ONLY`. The query is required and capped at 500 characters; `chapterId` is optional and capped at 64 characters; `limit` defaults to 4 and is bounded to 1-6. Missing/blank `q`, an oversized `q`, an oversized `chapterId`, and a non-numeric `limit` return `400` with `KNOWLEDGE_QUERY_REQUIRED`, `KNOWLEDGE_QUERY_TOO_LONG`, `KNOWLEDGE_CHAPTER_TOO_LONG`, and `KNOWLEDGE_LIMIT_INVALID` respectively. A valid query with no eligible match returns `200` and an empty result list. Results expose a safe source label, optional page label, review/publication status, a bounded excerpt, and a relevance score. Draft, unreviewed, out-of-scope, and low-relevance chunks are omitted rather than presented as facts.

Classroom scripts are also versioned MySQL records. New content should follow these contracts before review:

- [`contracts/classroom-script.schema.json`](../../contracts/classroom-script.schema.json)
- [`contracts/animation.schema.json`](../../contracts/animation.schema.json)
- [`contracts/examples`](../../contracts/examples)

The detailed handoff for the classroom and content owners is in [`docs/content-import-guide.md`](../../docs/content-import-guide.md).

## Trusted learning evidence

Learning progress distinguishes self-reported activity from evidence produced by a completed backend workflow:

- A guest code run is not persisted and returns a null `runId`. A signed-in run is persisted and returns an owned `runId`.
- `POST /api/v1/code/analyze` accepts ad-hoc code for guests, but a signed-in client should submit `runId`. The backend reloads the owned source and result, ignores client-supplied run fields, and creates `CODE_REVIEW` evidence only after successful analysis.
- Every classroom session stores the exact reviewed script and chapter snapshot used when it starts. Answer events retain the evaluation status, matched misconception, and feedback, and the backend creates `CLASSROOM_ANSWER` evidence.
- Animation observations are append-only. `animation_records.observation` retains the latest value for compatibility, while `animation_observations` preserves the history and the backend creates `ANIMATION_OBSERVATION` evidence. A DSVP simulation resolves an owned classroom snapshot, an authorized published PPT page, or a published explicit chapter. Formal evidence atomically writes the chapter-scoped animation, linked request snapshot, and `ANIMATION_SIMULATION` event; requests without a resolvable chapter return an explicit non-persisted preview.
- The generic learning-event endpoint accepts only `RESOURCE_VIEW`, `RESOURCE_DOWNLOAD`, `REVIEW_COMPLETED`, and `WEAKNESS_RECORDED`. Clients cannot manufacture the four server-managed evidence types above.

Unknown or foreign-owned run, animation, classroom, and chat identifiers are returned as `404` so ownership details are not disclosed.

## API contract

The full HTTP contract is [`contracts/openapi-v1.yaml`](../../contracts/openapi-v1.yaml). Important integration endpoints include:

- `POST /api/v1/chat` and `POST /api/v1/chat/stream`: authenticated formal course chat only. Published authorized evidence, an enabled model, and an available per-user quota are required; streaming uses named `sources`, `delta`, `done`, and `error` events and releases its reservation on failure, timeout, or disconnect.
- `GET /api/v1/chat/sessions`: signed-in users receive their 50 most recently active sessions.
- `GET|DELETE /api/v1/chat/sessions/{id}`: signed-in users can read or delete only their own sessions; reads include the latest 200 messages in chronological order.
- `GET /api/v1/resources/{id}/content`: published resource file streaming.
- `GET /api/v1/knowledge/search`: guest-compatible reviewed knowledge retrieval with optional chapter and bounded result-count filters.
- `POST /api/v1/classroom/sessions/{id}/actions`: answer, pause, resume, continue, or finish a script-driven class.
- `POST /api/v1/animations/generate` and `POST /api/v1/animations/{id}/observations`: validated animations and owned observations.
- `POST /api/v1/animations/simulate`: deterministic DSVP preview or source-verified, chapter-scoped evidence with idempotent retries.
- `POST /api/v1/learning/events`: signed-in, self-reported learning activity only; trusted workflow evidence is server-managed.
- `POST /api/v1/code/runs` and `POST /api/v1/code/analyze`: remote sandbox execution plus ad-hoc or owned-run analysis.

During the frontend compatibility window, the browser tries this Spring endpoint first. It falls back to the legacy `GET /api/knowledge/search` only when the v1 request returns `404`; other v1 errors are surfaced to the user. The legacy route is a Node debug/inspection endpoint and must remain disabled (`KNOWLEDGE_DEBUG_API=false`) in production, so it is not a substitute for the reviewed Spring contract.

## Verify before merging

```powershell
cd backend/spring
.\mvnw.cmd clean test
.\mvnw.cmd -DskipTests package
```

The root project also contains legacy prototype regression checks:

```powershell
npm test
```

The GitHub Actions workflow runs both checks for pushes and pull requests. Docker Compose and Caddy examples are under [`deployment`](../../deployment).

## Studyroom resource orchestration (schemaVersion 2)

Question-driven lessons first plan teaching goals, then retrieve candidates per goal and bind scenes in a second bounded model call. A single repair is allowed; separate operation-scoped quota keys meter each call. `room.version` remains the progress revision and is unrelated to scene `schemaVersion`.

Flyway V27 adds nullable `study_room_steps.orchestration_json`. NULL historical records are adapted without model calls. A PPT scene contains ordered page references and per-page explanations; code scenes distinguish READ and EXPERIMENT. All scene references must belong to the step and its authorized candidate list. Code line ranges are checked against the catalog source. Missing resources never authorize replacement resources.

The UI shows actual step materials, supports page navigation/zoom/fullscreen, and keeps slide positions and code drafts only during the current page session. Follow-up context may include the selected scene/page and bounded run output; it is validated against the owned lesson and treated as untrusted data. Development demo routes use the same backend resource catalog instead of injecting frontend resources.

Per-step candidate budgets: PPT 12, animation 4, code 6. Search uses the original question plus each goal; explicit code activities are normalized to implementation intent. Inspect `Studyroom selection` and `Studyroom resource decision` logs by request ID to distinguish candidate absence, model omission and selected IDs. Catalog errors and validation removal are reported separately. Full material text and credentials are not logged.


## 学习大纲审阅预览

课程问答使用“选择详细程度 → 生成大纲 → 学生修改/确认 → 按单元准备”流程。新增接口与状态见 `../../contracts/openapi-v1.yaml`；实施、恢复策略与验证边界见 [验收记录](../../docs/project/studyroom-reviewed-outline-acceptance-2026-10-18.md)。

从仓库根目录运行 `./scripts/start-studyroom-preview.ps1`，在未设置 DB_URL 时使用 `private/dev-db/reviewed-outline` 文件库，使草稿、任务与课堂在重启后保留。已有外部数据库配置优先。普通 `spring-boot:run` 仍遵循 application.yml 的数据库配置。

Worker 使用数据库租约与模型响应记录；`app.studyroom-generation.enabled=false` 停止自动领取。SENT 调用结果不确定时不会自动再消耗额度，需要显式重试。代码运行服务未配置时可以阅读、编辑源码，规划默认不安排运行实验；显式实验须先具备运行能力。
