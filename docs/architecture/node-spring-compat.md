# Node 与 Spring 兼容差异

公开代理按路径分流：Caddy 将 `/api/v1/*` 送到 Spring（8792），旧 `/api/*` 送到 Node（8791）。二者不是同一契约的两个版本；客户端须选定完整工作流并使用对应请求/响应形状。

生产库 schema 以 Spring Flyway 为准。迁移是审核后的分期导入，不是双活写入。

变更契约时先改 `contracts/openapi-v1.yaml` 或相关 JSON Schema，再更新一条完整调用链。API 与数据模型任一侧字段、授权或持久化边界变化时，须在同一次发布中同步更新本文两节。禁止用字符串替换把 `/api/*` 批量改成 `/api/v1/*`。

---

## 1. API 差异矩阵

| Workflow | Node compatibility API | Spring API | Difference / migration rule |
|---|---|---|---|
| Health | `GET /healthz` | `GET /actuator/health` (loopback only) | Node health is public through the root service; Actuator is not routed by Caddy and hides details in prod. |
| Request code | `POST /api/auth/request-code` | `POST /api/v1/auth/request-code` | Both require an email and purpose; Node returns `400 CODE_PURPOSE_INVALID` when `purpose` is missing or unsupported. Production requires real SMTP and never echoes a code. Do not assume the Node error envelope or code is the Spring v1 error contract. |
| Register | `POST /api/auth/register` | `POST /api/v1/auth/register` | Node accepts a 6-character minimum password; Spring validates at least 8 characters and persists roles/status in MySQL. |
| Password login | `POST /api/auth/login` | `POST /api/v1/auth/login` | Both return a Bearer token. Spring also emits the `HttpOnly; Secure; SameSite=Strict` `ds_session` cookie. |
| Code login | `POST /api/auth/verify-code` | No equivalent endpoint | Spring code flow is request-code followed by register/login/reset; do not map this Node endpoint to register. |
| Current user | `GET /api/auth/me` | `GET /api/v1/users/me` | Response field names and role representation differ; use the OpenAPI contract for Spring. |
| Logout | No Node endpoint; client drops local token | `POST /api/v1/auth/logout` | Spring clears the secure cookie. A Node token remains valid until its 7-day expiry unless the client removes it. |
| One chat turn | `POST /api/chat` | `POST /api/v1/chat` | Node accepts the legacy UI envelope and may persist to SQLite; Spring uses the reviewed `ChatRequest` and MySQL session/message rows. |
| Chat SSE | `POST /api/chat` with stream request | `POST /api/v1/chat/stream` | Node streams on the same path with legacy `data:` frames. Spring has named `sources`, `delta`, `done`, and `error` events. |
| Chat history | `GET/PUT/DELETE /api/conversations` and `/api/chat-threads` | `GET/DELETE /api/v1/chat/sessions` and `/api/v1/chat/sessions/{id}` | Node has scenario drafts and typed thread JSON. Spring exposes owned, bounded sessions and message records only. |
| Chapters/resources | `GET /pdfs/*` and upload routes | `GET /api/v1/chapters`, `/chapters/{id}/resources`, `/resources/{id}`, `/resources/{id}/content` | Spring gates chapter/resource status and license scope in every list/detail/content path. Do not expose `RESOURCE_DIR` or map `/pdfs` to it. |
| Knowledge search | `GET /api/knowledge/search?q=...&scenario=...` (Node debug route) | `GET /api/v1/knowledge/search?q=...&chapterId=...&limit=...` | Spring is the reviewed, guest-compatible contract. It requires a non-blank query (max 500), optionally scopes by `chapterId` (max 64), and bounds numeric `limit` to 1-6 (default 4); a non-numeric `limit` returns `400 KNOWLEDGE_LIMIT_INVALID`. Only `VERIFIED` chunks with a complete `VERIFIED` source chain in a published chapter are eligible; merely `PUBLISHED`, legacy-unverified, draft, and excluded material is filtered out. Guests see `PUBLIC`, students add `CLASSROOM_ONLY`, and teachers/admins add `TEAM_ONLY`. Spring returns `{ok, query, results}` with safe source/page/review/publication metadata, bounded excerpts, scores, and content-bound evidence hashes in chat citations. Node returns `{ok, query, knowledge, results}`, uses its fixed configured limit, and is a debug/inspection surface. `NODE_ENV=production` forces that route off even when `KNOWLEDGE_DEBUG_API=true`, returning 404; it must stay disabled in production. |
| Private PPT plan | `GET /api/classroom/presentation-plan?lessonId=...` | No equivalent HTTP route yet | Node returns sanitized slide cards and signed image URLs. The `/presentation/*` asset route must remain on Node. |
| Classroom | `GET /api/assignments` is an authenticated recipient read of active, target-visible tasks; `POST /api/teacher/assignments` is teacher-only create/update of the caller's own task; `DELETE /api/teacher/assignments/{id}` is creator-only archive. Node also has `/api/teacher/*` and `/api/learning-snapshot`. | `GET /api/v1/classroom/scripts`, `POST /api/v1/classroom/sessions`, owned session/actions, and `/api/v1/learning/*` | Do not collapse Node's student-facing `GET /api/assignments` into the teacher write route: the GET only needs authentication and filters task visibility for the current user, while the POST requires the teacher rule and constrains updates by `teacher_id`. Node stores aggregate snapshots and teacher assignments in SQLite. Spring stores reviewed scripts, state transitions, evidence, and per-chapter progress in MySQL. |
| Animation simulation | `POST /api/animation/simulate` (Node also has a direct-only `/api/v1/animations/simulate` branch) | `POST /api/v1/animations/simulate` | Public `/api/v1/*` always reaches Spring, so the duplicate Node branch is not a migration bridge. Both normalize the optional DSVP `context`; Node returns renderer data only. Spring verifies classroom/PPT/explicit chapter sources, returns `evidencePersisted=false` for a context-free preview, and atomically links formal evidence to `animation_records`, `dsvp_request_snapshots`, and chapter progress. |
| Animation generation/observation | No stable Node equivalent | `POST /api/v1/animations/generate`, `POST /api/v1/animations/{id}/observations` | Spring validates the schema, binds ownership, and persists append-only observations. |
| Code execution | `POST /api/execute` | `POST /api/v1/code/runs` (canonical); `POST /api/v1/code/run` (deprecated compatibility alias) | Guest callers may use only code execution. Node tries Judge0 then Piston and accepts C, C++, Python, JavaScript, and Java. Spring accepts C/Python through Piston and persists signed-in runs. Both enforce size/time/concurrency limits. New clients must use `/runs`. |
| Code analysis | No stable Node endpoint | `POST /api/v1/code/analyze` | Authentication is required. Code analysis uses the model, quota, and evidence gates; it never shares the guest execution exception. An owned `runId` creates trusted `CODE_REVIEW` evidence. |
| Learning activity | Aggregate `PUT/GET/DELETE /api/learning-snapshot` | `GET /api/v1/learning/progress`, `POST /api/v1/learning/events` | Spring accepts only the four generic event types; classroom answers, animation observations, DSVP simulations, and code reviews are server-managed. |
| Uploads | `POST /api/upload`, teacher-only `/api/upload-pdf` | No public upload-to-publish route | Production resource import is an operator/database workflow. Uploaded files must not become a public static directory. |

### Authentication and browser behavior

Node returns a token in JSON and the existing frontend stores it as a Bearer
token. Node does not set a session cookie. Spring accepts both the Bearer token
and `ds_session`; its cookie is secure and strict in production. A frontend
change must not assume that a successful Node response can be replayed against
Spring without adapting the endpoint, password policy, ownership rules, and
response schema.

During the current migration, Node signs its local Bearer token with the
separate `NODE_COMPAT_JWT_SECRET`; Spring keeps `JWT_SECRET` private. When
`NODE_COMPAT_ENABLED=true`, Spring accepts a valid Node token only from the
`Authorization: Bearer` header and only for `GET /api/v1/learning/progress`,
`POST /api/v1/learning/events`, `POST /api/v1/animations/simulate`, and an
owned `POST /api/v1/animations/{id}/observations`. It resolves an existing
MySQL user by verified email and uses only that account's database roles,
never the Node token's roles. If no Spring account exists, the compatibility
bridge creates a non-loginable `STUDENT` mirror only; it never grants teacher
or administrator access implicitly. All other Spring endpoints require a
standard Spring token or `ds_session` cookie.

For knowledge search specifically, the current browser sends the v1 request first and retries the legacy Node route only when v1 returns HTTP 404. A v1 `400` (for example, a missing or oversized query) or any upstream/server error is shown as an error and is not silently downgraded to the debug route. The Node route is not merely default-disabled: `NODE_ENV=production` forces it off even if `KNOWLEDGE_DEBUG_API=true`, so production clients must treat the Spring response shape as canonical and keep the fallback only for older non-production-compatible deployments that have not yet exposed v1.

Both services use exact-origin CORS with credentials enabled. The only allowed
production origin is `https://structify.cn`; do not use `*` with credentials.

### Streaming and proxy rules

- Keep `Content-Type: text/event-stream`, `Cache-Control: no-cache`, and
  connection keep-alive intact for Spring `/chat/stream` and legacy Node chat
  streaming.
- Disable proxy buffering for those paths. The supplied Caddyfile uses
  `flush_interval -1` and does not add a WebSocket-only route.
- No current controller or Node branch implements WebSocket messages. A future
  WebSocket endpoint must add an explicit contract, authentication decision,
  smoke test, and route before the frontend uses it.

---

## 2. 数据模型差异矩阵

Node 与 Spring 的所有权、身份与持久化模型不同。

### Storage map

| Node SQLite table/file | Spring MySQL target | Mapping and loss boundary |
|---|---|---|
| `users.id`, `email`, `password_hash`, `created_at` | `users` plus `user_roles` | Integer IDs are inserted as stable BIGINT IDs. Node scrypt hashes are not accepted by Spring BCrypt; the importer writes an active reset sentinel and grants `STUDENT` only. Privileged roles require separate review. |
| `conversations` | `chat_sessions` + `chat_messages` | Each legacy row becomes `legacy-conversation-<id>`. `scenario` is retained in the title, `chapter_id` is null, and one valid `updated_at` is reused for session/message chronology because the source has no per-message timestamps. Missing timestamps or invalid messages are blocked, not fabricated. |
| `chat_threads` | `chat_sessions` + `chat_messages` | Thread ID/title/messages and valid created/updated timestamps are preserved. `type`, `scenario`, and `classroom_state` have no lossless Spring columns and are reported as unmapped. |
| `learning_snapshots` | No direct row target; rebuild `learning_records` from trusted evidence | Aggregate progress, weak memory, report, stats, and teacher tasks are not losslessly event-sourced. They remain an audit-only unmapped report; do not convert JSON guesses into completed evidence. |
| `teacher_assignments` | No Spring assignment aggregate in the current schema | Rows are audit-only. Do not import them as classroom scripts or learning events. |
| `private/presentation-materials/` JSON and `rendered/` media | `presentation_manifests`, `presentation_pages`, `resources` | Node's offline slide index is filesystem data. Spring V11 stores reviewed manifest/page metadata and optional resource references in MySQL; binary media still belongs under a private `RESOURCE_DIR` or Node `PRESENTATION_DIR`. |
| Node `private/knowledge` Markdown | `knowledge_chunks` + `resources` + `content_reviews` | File presence does not publish content. Production keeps local auto-publish off; reviewed chunks must carry chapter/resource/license/status metadata. |
| Node `private/pdfs` and uploads | Spring `resources.file_path` under `RESOURCE_DIR` | Do not copy a directory into public web space. Insert relative, normalized paths only after review; Spring rejects absolute paths, traversal, and symlink escapes. |

### Spring schema additions after V1

Flyway V1 creates users/roles, verification and refresh tokens, chapters,
resources, knowledge, reviews, chat sessions/messages, classroom scripts and
events, animation records, code runs, and learning records. Later migrations:

- V5 adds append-only `animation_observations`.
- V6 preserves classroom answer status, misconception, and feedback.
- V7 freezes the classroom chapter and script snapshot boundary.
- V8 adds knowledge `license_scope` and backfills it from resources.
- V9/V10 backfill classroom script snapshots and legacy animation observations.
- V11 persists reviewed PPT manifests/pages, DSVP request snapshots, classroom
  presentation/animation references, and observation provenance/review fields.

The V11 foreign keys use `ON DELETE CASCADE` only where child evidence is
owned by its parent record; presentation/resource and classroom evidence links
use `SET NULL` where historical evidence should survive removal of an optional
asset. Operators must run the migration integration tests and a restore-tested
MySQL backup before production promotion.

The DSVP evidence repair uses the existing V11 `animation_record_id` foreign
key and existing chapter columns, so it does not rewrite V11 and does not add a
V12 migration. A formal simulation now writes the animation row, linked DSVP
snapshot, and chapter-scoped learning event in one short local transaction.
Requests without a verified chapter remain non-persisted previews. The same
normalized request is idempotent per user, and persisted trace identifiers are
user-scoped to avoid cross-user primary-key collisions.

### Import safeguards

`scripts/sqlite-to-mysql-import.js` opens SQLite read-only and reports:

- source SHA-256, row counts, missing source tables, blocked rows, and
  unmapped fields;
- no SQLite file, private content, or legacy password hash in the SQL bundle;
- deterministic `START TRANSACTION`/`COMMIT` output only for development, test,
  or staging; production targets are rejected;
- `user_roles` rows for imported students and a reset sentinel that keeps the
  normal Spring reset path reachable.

Run the default audit first, review every blocked/unmapped item, import to a
fresh staging database, verify role and password-reset recovery, then exercise
chat ownership and message ordering. A successful importer report is not a
production approval by itself.

### Data-model invariants for new code

1. User ownership is checked in SQL and service code; a foreign ID returns
   `404`, not another user's record.
2. A classroom session stores the reviewed script and chapter snapshot used at
   start time; later content edits do not rewrite its evidence.
3. Trusted `CLASSROOM_ANSWER`, `ANIMATION_OBSERVATION`, `ANIMATION_SIMULATION`, and `CODE_REVIEW`
   records are created by their backend workflow, never by a generic client
   event payload.
4. Resource and knowledge visibility requires publication plus license scope;
   filesystem paths never cross the API boundary.
5. Legacy aggregate JSON remains an audit input until a reviewed event mapping
   exists. It must not be presented as a completed learning record.
6. A DSVP simulation contributes to chapter progress only when the chapter is
   resolved from an owned classroom snapshot, an authorized published PPT
   page, or a published explicit chapter. Client/source conflicts fail before
   any of the three evidence rows are written.
