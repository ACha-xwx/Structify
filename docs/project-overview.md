# Structify 项目总览

> 面向"数据结构（C 语言描述）"课程的 AI 教学系统。
> 生成日期：2026-09-22　　当前生产版本：`v1.0.71-signed-static-courseware-urls`
> 线上：学习站 `https://structify.cn`　管理端 `https://admin.structify.cn`

本文回答三件事：**这东西是什么**、**它是怎么实现的**、**还剩什么要做**。所有数字来自当日对仓库与生产环境的实测，不是规划值。

---

## 一、这是什么

学生打开 `structify.cn`，登录后只有两条路：

| 主线 | 入口 | 学生看到什么 |
|---|---|---|
| **AI 课堂** | `/classroom` | 选一课（共 57 课时 / 10 章），点"开始"——AI 分好段、配好课件，**一页一页讲**：讲一页、问一个问题、等学生答、答错留在原题重答，讲完这一课的课件才算下课。右侧同步显示那一页 PPT。 |
| **动画实验室** | `/animation` | 选"结构 + 操作"（或写一句话让模型自己选），生成算法过程的**逐步可视化**：数组/链表/栈/队列/二叉树/图/B 树/排序/查找等 167 条内置能力，可上一步/播放/回放/倍速/键盘控制。 |

另外两个次要页面：`/courseware`（全册 22 个课件 deck 的浏览与翻页）、`/admin/*`（管理端 8 个页面：总览 / 用户 / 审核 / 任务 / 审计 / 模型 / 邮件 / 沙箱）。

**产品定位**：不是"加了 AI 的题库"，而是**把教材变成可讲的课**——教材页 → 课时 → 课件页 → 讲解脚本 → 逐步交互课堂，每一环都要能回到来源（`slideMatch`、`textbookMatch` 就是这条回溯链的落点）。

---

## 二、技术全景

```
                        学生浏览器（Vue 3 SPA）
                                │
                     HTTPS（未备案 → 只能走 Cloudflare）
                                │
                    ┌───────────▼───────────┐
                    │  Caddy 2.10（独占 80/443）│  TLS、SPA 回退、SSE 不缓冲
                    └───┬───────────────┬───┘
                        │               │
        /api/v1/*  ─────┘               └─────  旧 /api/*、/presentation/*、/pdfs/*
                        │               │
              ┌─────────▼─────────┐  ┌──▼──────────────────┐
              │ Spring Boot (8792)│  │ Node.js 兼容层 (8791) │
              │ 主 API、MySQL      │  │ 旧接口、SQLite        │
              │ Java 21 / 268 文件 │  │ 5 文件 / 4700 行      │
              └──┬──────────────┬─┘  └─────────────────────┘
                 │              │
        ┌────────▼───┐   ┌──────▼──────────────────┐
        │ MySQL 8.4  │   │ DSVP 引擎（长驻 node 子进程）│
        │ Flyway V1–25│   │ dsvp-service.js + 13 文件  │
        │ 1.3 MB     │   │ 167 条能力                │
        └────────────┘   └─────────────────────────┘
                 │
        ┌────────▼──────────────────────────────────────┐
        │ 只读素材挂载（宿主 /srv/structify/private）      │
        │ 课件 272M（1210 页 PNG+WebP 孪生）、原 PPT 321M  │
        │ 教材 4.3M、PDF 2.2M、已核验教材 1.1M            │
        └───────────────────────────────────────────────┘
```

### 代码规模（实测）

| 部分 | 语言/框架 | 文件 | 行数 |
|---|---|---|---|
| `backend/spring` 主后端 | Java 21 / Spring Boot 3 | 268 | 22,345 |
| `backend/spring` 测试 | JUnit + SpringBootTest | 99 | 14,358 |
| `frontend` | Vue 3 + TS + Vite | 140 | 18,861 |
| `backend/node` 兼容层 | Node.js + SQLite | 5 | 4,700 |
| `backend/dsvp` 动画引擎 | Node.js | 13 | 3,050 |
| `scripts/` 校验脚本 | Node / PowerShell | 114 个 `verify-*` | — |
| 文档 | Markdown | 11（`docs/`）+ 21（`docs/project/`） | — |

### 技术栈选型

| 层 | 选择 | 为什么 |
|---|---|---|
| 主后端 | **Java 21 + Spring Boot** | 从 Node 迁移而来（SQLite → MySQL、手写路由 → Spring Security/Flyway）；Node 旧接口保留并行运行，不做一次性替换 |
| 数据库 | **MySQL 8.4**（生产）/ H2（本地与测试，`MODE=MySQL`） | 本地零依赖启动，测试跑同一套 Flyway 迁移 |
| 迁移 | **Flyway V1–V25** | 每个功能一个迁移，生产用同一套脚本，避免手工改表 |
| 前端 | **Vue 3 + TS + Vite**，无 Pinia | 状态量不大，模块级 `reactive` 单例足够；少一层依赖 |
| 动画 | **自研 DSVP 引擎**（JSONL over stdin/stdout） | 确定性、可测试、不依赖浏览器；长驻子进程避免每次启动开销 |
| 部署 | **Docker Compose + Caddy** | 单机四容器（caddy / node / spring / mysql）+ 只读素材挂载 |
| 模型 | **DeepSeek（`deepseek-flash`）**，全链路关闭思考 | 关思考省约 67% token；`deepseek-v4-pro` 仅备用 |

---

## 三、怎么实现的

### 3.1 Spring 后端的包结构

`com.feng.dsagent` 下按业务域分包，每个域一个 Controller + Service + Repository：

| 包 | 职责 | 代表类 |
|---|---|---|
| `auth` | 注册/登录/验证码/重置 | `AuthController`、`DefaultVerificationCodeManager`、`RolePolicy` |
| `security` | 令牌与过滤器链 | `SecurityConfig`、`JwtAuthenticationFilter`、`NodeCompatibilityTokenVerifier` |
| `common` | 错误、请求 id、限流 | `ApiExceptionHandler`、`RequestIdFilter`、`WindowRateLimiter` |
| `resource` | 章节与资源目录 | `ResourceController`、`ResourceService` |
| `knowledge` | 教材 RAG 检索 | `KnowledgeSearchService`（含提示词注入过滤）、`KnowledgeBootstrapRunner` |
| `chat` | 对话与 SSE 流式 | `ChatController`、`ChatService` |
| `classroom` | **课堂状态机** | `ClassroomService`、`ClassroomStateMachine`、`ClassroomTimeline`、`SlideSpinePlan`、`ClassroomPreparation` |
| `presentation` | **课件目录与页面服务** | `PresentationCatalog`、`PresentationController`、`SlideImageLinks` |
| `animation` | 动画编排与引擎桥接 | `AnimationController`、`DsvpAnimationAdapter`、`DsvpLocalEngine`、`DsvpConcurrencyLimiter` |
| `compiler` | 代码沙箱 | `CompilerController`、`PistonCompilerGateway`、`SandboxConfigController` |
| `learning` | 学习进度与证据 | `LearningController`、`LearningWorkbenchService` |
| `admin` / `review` / `modelconfig` / `mail` | 管理与治理 | `AdminService`、`ReviewService`、`ModelConfigCrypto`（API Key AES-GCM） |
| `aiquota` / `ai` / `model` | 配额与模型调用 | `AiQuotaExecutionService`、`AiReadinessController`、`OpenAiCompatibleModelClient` |

### 3.2 数据模型

Flyway V1 建核心表，之后每个功能一个迁移：

- **V1–V10**：`users`/`user_roles`/`refresh_tokens`、`chapters`/`resources`/`knowledge_chunks`、`content_reviews`、`chat_sessions`/`messages`、`classroom_scripts`/`sessions`/`events`、`animation_records`、`code_runs`、`learning_records`；V5 加 `animation_observations`，V6 加课堂证据（答案状态/误区/反馈），V8 加知识片段授权范围。
- **V11–V18**：`presentation_manifests`/`presentation_pages`/`dsvp_request_snapshots`（PPT 证据链）、`admin_audit_events` + 用户禁用、`model_configurations`、`content_review_events`、`background_tasks`。
- **V17/V19**：`ai_quota_*`（桶 + 每用户并发 + 预约）、`usage_source`。
- **V20–V25**：`mail_configurations`、`username` 唯一列、`sandbox_configurations`、动画索引、课堂游标（`runtime_index`/`revision`/`response`）、**`classroom_slide_overrides`（教师钉选课件页）**。

**生产实际数据（当日实测）**：6 用户、10 章、536 知识片段、5 份课堂脚本、5 个会话、5 条动画记录、6 次代码运行、10 条学习记录、15 条审计；**`resources` 与 `presentation_pages` 均为 0 行**——课件页不是数据库记录，而是文件系统上的 `slides.json` + 渲染图；整库仅 1.3 MB。

### 3.3 Node 兼容层的边界

Node（8791）不是"旧代码残留"，而是**仍在服务的并行协议**：

- **只有 Node 有**：`chat-threads`/`conversations` 会话 CRUD、`learning-snapshot`、`teacher/*` 作业聚合、文件上传（`/api/upload`、`upload-pdf`）、`/presentation/*` 与 `/pdfs/*` 静态托管、SQLite 存储。
- **只有 Spring 有**：验证码 + refresh token 体系、管理端全套（users/audit/reviews/tasks/model-config/mail/sandbox）、课堂状态机、课件目录、DB 级知识检索、Piston 沙箱、学习事件。
- **两边都有**（各服务不同工作流）：登录、聊天、`animations/simulate`、知识检索、代码执行。

规则写在 `docs/api-node-spring-differences.md`：**这是并存协议，不是版本号替换**，禁止把 Node token、错误结构、会话 JSON 当成 Spring 等价物。

### 3.4 DSVP 动画引擎：从一句话到一帧帧

引擎是**独立的 node 进程**，由 Spring 通过 stdin/stdout 用换行分隔的 JSON 通信：

```
POST /api/v1/animations/simulate
  → AnimationController
    → DsvpConcurrencyLimiter.acquire（信号量 4）
      → DsvpEvidenceService.simulate
        → DsvpAnimationAdapter.adapt
          → DsvpLocalEngine.simulate ── 长驻子进程 stdin/stdout ──▶ dsvp-service.js
                                                                      → engine/dsvp-engine.js
                                                                        → animation-capabilities.js（167 条能力）
                                                                        → textbook-animation-engine.js（各结构模拟器）
                                                                        → textbook-validation.js（边界校验）
  ← trace + player 数据 ◀──────────────────────────────────────────────┘
→ 落库 dsvp_request_snapshots + animation_observations
```

三条工程细节：

1. **超时 6s 即重启子进程**（`DSVP_ENGINE_TIMEOUT_MS`），返回失败则**降级**到 Java 内置的 `DsvpSimulator`（9 种结构的冻结契约），保证接口永远有响应。
2. **边界校验统一在 `textbook-validation.js`**：越界/类型错一律抛 `SimulationInputError`（中文显式错误），**不静默夹取或截断**。2026-09-20 一次性修掉 53 条边界问题，探针基线稳定在 13 条（设计使然）。
3. **查表必须 `Object.hasOwn`**——能力表是普通对象，防 `__proto__` 污染。

能力注册表同时承担**提示词裁剪**：按当前章节选出相关能力清单喂给模型，避免它为一个不存在的算法编造动画请求。

### 3.5 前端

**路由**（`router/index.ts` + `router/guards.ts`）：

| 路径 | 页面 | 门槛 |
|---|---|---|
| `/` | 入口选择页（两张大卡：课堂 / 动画学习） | 登录 |
| `/classroom` | 课堂 | 登录 |
| `/courseware` | 课件浏览 | 登录 |
| `/animation` | 动画实验室 | 登录 |
| `/login` `/register` `/reset-password` | 认证（同组件，`props.mode` 切换） | — |
| `/admin/*`（8 页） | 管理端 | 登录 + `ADMIN` |
| `/user/*` | 兼容旧书签，重定向到 `/` | — |

守卫行为：管理员域名 `admin.structify.cn` 下 `/` → `/admin`，非管理路径 `location.replace` 回主站；未登录 → `/login?redirect=…`；`disabled`/`forbidden`/角色不足 → `/403`。**任何页面都不会在用户没选的情况下自动开课**——续课只有两条显式路径（`/classroom?session=<id>` 或"继续上次课堂"按钮）。

**数据层**：`shared/api/client.ts` 统一处理 base url（`/api/v1`，避免重复前缀）、`Authorization: Bearer`、`X-Request-Id`、`credentials: include`、按 content-type 推断 json/text/binary/**sse**，错误归一为 `ApiClientError{status,code,message,requestId,details}`。

**课堂怎么跑的**（`ClassroomView.vue`）：

```
选课时 → prepareClassroom → 轮询（1200ms × 1.7ⁿ，封顶 6s，≤20 次）
       → session ready
       → 状态机推进：OPENING → EXPLAIN → QUESTION ⇄ WAITING → DISCUSS → BLACKBOARD → SUMMARY
       → 主按钮 = 状态驱动（OPENING 是"开始"；WAITING 是"回答"/"再答一次"）
       → finished = SUMMARY 且 stepIndex ≥ stepCount（summary 不是结束，游标越过最后一步才是）
```

两条硬规则：**答错 = 没答**（非 CORRECT 不进位，回 WAITING、attempts+1，"下一步"不渲染，学生留在原题重答）；**CONTINUE 永不越过未答题**。

课件面板显示哪一页，由 `stage.slideMatch` 决定，优先级：**人工指定 > slideRefs > slideScope（屏幕不动）> SlidePlanner**。前端**绝不按比例换算页码**——翻页是服务端给结论，前端只渲染。

**动画实验室**：`AnimationPlayer.vue` + `useAnimationPlayback`（`index = -1` 为起点），控制"上一步 / 播放·暂停 / 下一步 / 回到起点"、0.5/1/2/4× 倍速、进度条、键盘 ←/→/空格/Home。渲染用 **SVG**（树用 tidy 布局、图用圆形布局），数组/矩阵/记录走 HTML `ol`/`table`/`dl`；后端帧被 `frame.ts` 归一为 array/matrix/records/tree/graph/text 六类。

**样式体系**：`shared/design/tokens.css` 定义全部变量（`--bg`/`--surface`/`--text`/`--accent`/`--stage-*`、`--radius-sm|md|lg`、玻璃 `backdrop-filter: blur(7px)`），亮暗由 `[data-theme]` 切换（localStorage `structify-theme`），中英由 `[data-locale]` 切换（所有文案走 `t()`，双语齐全）。`BrandStage.vue` 是登录后所有页面的统一舞台（58px 蓝图网格 + `S` 标 + 主题开关）。

**一条贯穿始终的产品约束**：页面上**不渲染解释性小字**——结果要么大字显示，要么删掉。这条约束反过来塑造了很多实现（例如判答提示只有一句"回答不正确，再想想。"）。

### 3.6 横切关注点

| 关注点 | 实现 |
|---|---|
| **认证** | `Authorization: Bearer` 优先，否则 `ds_session` cookie。先验 Spring JWT（HS256，TTL 2h，角色每次读库）；失败且是白名单端点时，用 `NODE_COMPAT_JWT_SECRET` 验 Node 旧 token（≤8 天）→ 镜像建桥接账号。**注意：401 不代表路由存在**（安全过滤器在路由匹配之前拦截）。 |
| **错误** | 统一 `ApiError{code,message,requestId,details}`；`AUTH_REQUIRED`/`AUTH_FORBIDDEN`/`VALIDATION_FAILED`/`INTERNAL_ERROR`。 |
| **请求追踪** | `RequestIdFilter` 透传或生成 `X-Request-Id`，CORS 暴露。 |
| **配额** | `ai_quota_buckets` + `ai_quota_user_concurrency` + `ai_quota_reservations`；**每人每天，UTC 换日 = 北京 08:00**。备课/插问/判答都计配额；真实备课一轮 19 课时 ≈ 47 万 token。 |
| **并发** | `DsvpConcurrencyLimiter`（4）、`CompilerConcurrencyLimiter`。 |
| **限流** | 内存滑动窗口 `WindowRateLimiter`（登录同账号 10 分钟 5 次等）。 |
| **审计** | 写 `admin_audit_events`（管理写操作）、`content_review_events`（内容状态变更）、`background_tasks`、`learning_records`、`dsvp_request_snapshots`、`animation_observations`。 |

### 3.7 内容流水线（这个项目的"重工业"部分）

课件与教材不是数据库记录，而是**离线流水线产出的只读素材**：

**教材**：原书 PDF（374 页，**教材页 N = PDF 页 N+11**）→ 逐页重录 → `revisions/pages/*.md`（正文只放教材内容，核验证据写成 HTML 注释，build 时剥离）→ `merge-revision-fragments.mjs` → `build-reviewed-textbook.mjs` → 受控导入（`app.knowledge.auto-publish-local=true` 开关）。

**课件**：`source-ppt/`（321M 原件）→ 渲染为 `presentation-materials/rendered/**/*.png`（1210 页）→ `slides.json`（每页的 `lessonIds`、标题、`shouldShow`）→ `build-slide-annotations.mjs` 生成每页的 `section/role/terms` 标注 → **`build-slide-webp.sh` 生成 WebP 孪生**（203MB → 64MB，-68%）。

**课时→课件**的对应关系：课时 id 取文件名前 5 位匹配，页面清单 = `slides.json` 里认领该课时页数最多的主 deck 的天然序（第 4、10 章无课件，前端出空态）。

### 3.8 发布与运维

`deployment/` 下是一套完整脚本：`preflight.sh`（不联网预检）→ `deploy.sh` / `release.sh`（Docker 构建 + Compose up）→ `health-check.sh`（回环健康）→ `smoke.sh`；另有 `backup.sh`/`restore.sh`/`rollback.sh`/`dns-check.sh`/`migrate-sqlite.sh`。

发布流程（本地工具链）：`git add` → `pack_release.py`（**从暂存区**打包，2.5MB / 831 文件，自动 CRLF→LF）→ `validate_archive.py`（契约校验）→ `upload_release.py`（SHA-256 远端校验）→ `update_env_images.py`（改镜像 tag，自动备份）→ `run_release.py` → `watch_release.py`（等 `RELEASE HEALTHY`）。旧版本自动裁剪，只保留最近两个。

**红线**：DNS 一个字都不许改；不许重建 MySQL 数据卷、不许删账号；模型只用 `deepseek-flash`。

### 3.9 质量保障

三层，共 **47 个前端 spec / 99 个后端测试类 / 114 个校验脚本**：

- **前端单元/组件测试**（Vitest + jsdom + @vue/test-utils）：**47 个文件 / 261 个用例**，覆盖 API 客户端、路由守卫、每个页面组件、纯逻辑（帧归一化、预取、轮询退避）。
- **后端测试**（JUnit + SpringBootTest + MockMvc + H2 + Flyway）：`*ApiIntegrationTest` 走真实 Spring 上下文与迁移，`*ServiceTest`/`*ControllerTest` 做单测，另有契约与迁移专项。
- **端到端校验脚本**（`scripts/verify-*.js`，114 个）：起真实服务验证 API 契约、动画证据闭环、课件覆盖、`verify-core-regression.js` 作为总闸。
- **构建门禁**：`vue-tsc --noEmit` + `vite build` 在发布流水线里跑（前端 261 用例也在 Docker 构建阶段跑）。

---

## 四、关键设计决策（为什么是现在这样）

| 决策 | 原因 |
|---|---|
| **Node 与 Spring 并存，不做一次性替换** | 前端完成联调前不替换线上服务；两套协议按工作流分工，避免"大爆炸"迁移。 |
| **课件页由服务端决定，前端不换算** | 页码对齐一旦交给前端"按比例算"，人工指定页、延伸页、提问停留页全都会错。 |
| **`slideScope` 改由服务端从 `slideRefs` 派生** | 它本是"由页面 id 唯一决定"的记账字段，模型手抄一次写错就毙掉整段备课。 |
| **被拒段自动退为本地课件脊线** | 宁可整课照常发布、标注"第 N 段由本地课件脊线补齐"，也不让一次概率性笔误毁掉整节课。 |
| **签名 + 扩展名的素材 URL（v1.0.71）** | 页面图对所有人都一样，但原 URL 无扩展名 → 边缘判为 API 调用 → 每翻一页跨境回源一次。现在同时满足"可永久缓存 + 不可遍历 + 任何 CDN 都当静态"，**备案换大陆 CDN 后同一套 URL 继续有效**。 |
| **WebP 孪生而非替换 PNG** | 保留 PNG 作为无损底稿；URL 扩展名跟着"孪生是否真的存在"走，不说谎也不会脏缓存。 |
| **DSVP 走子进程而非 HTTP 微服务** | 单机部署，省一个服务的运维面；超时重启 + Java 内置降级保住可用性。 |
| **关掉模型思考链（`disable-thinking=true`）** | 省约 67% token；本项目任务（讲解结构化、判答）不需要长推理。 |

---

## 五、生产现状（2026-09-22 实测）

| 项 | 值 |
|---|---|
| 版本 | `v1.0.71-signed-static-courseware-urls`（保留 v1.0.70 供回滚） |
| 容器 | caddy（5 周）、node（healthy）、spring、mysql（healthy） |
| 课时 | **57 课时 / 10 章**（01:8、02:7、03:5、04:3、05:5、06:8、07:7、08:7、09:6、10:1） |
| 课件 | 22 个 deck、1210 页（PNG+WebP 孪生各 1210） |
| 知识库 | 536 片段 / 10 章 |
| 数据库 | MySQL 8.4，1.3 MB，Flyway V1–V25 |
| 素材 | 课件 272M、原 PPT 321M、教材 4.3M、PDF 2.2M |
| 素材性能 | 单页 WebP 27KB，`cf-cache-status: HIT`，`immutable` 一年；连翻 5 页 1.95s（改造前 3.4s） |
| 未备案代价 | 每次动态请求跨境往返 ≈0.35–1.2s；`/`、`/classroom/lessons` 等必回源接口仍 1–2s |

---

## 六、后续要做的事

### （A）已知缺陷 —— 已确认存在，尚未修

| # | 事项 | 在哪 | 影响 |
|---|---|---|---|
| A1 | **生产从未部署 `slides.annotations.json`**，课件页标注能力整体静默降级 | `docs/project/00-current-status.md:14` | 每页 `section/role/terms` 全空、脊线清单标注恒为 `[-/-]`、教材候选退化为整课检索、`sceneOf()` 少 role 线索。**这是当前对备课质量影响最大的一条。** |
| A2 | **提示词把已实现的算法列为"未实现"**，抑制本可用的动画 | `ClassroomPreparation.java:154`（说 Dijkstra/AVL/B 树/外部排序未实现）vs `animation-capabilities.js`（已注册 `graph.dijkstra`、`avl.*`、`btree.*`、`external_sort.*`） | 模型被要求对这些算法省略 `animationRef` → 学生看不到本可演示的动画。**纯文字错误，改了立刻有收益。** |
| A3 | **11 项既有测试失败**（`animation.Dsvp*Evidence/Source`、`security.Node*Compat`） | `output/classroom-prep-scope-fix-verification.md:40,52` | 与近期改动无关，但让"全绿"失去意义。 |
| A4 | **动画边界探针只报告不判定成败**（exit 0） | `docs/animation-boundary-report.md:176` | 剩余 13 条边界无失败门禁，回归会悄悄溜过去。 |
| A5 | **Node `/pdfs/*` 匿名可读、无审计** | `backend-api-matrix.md:49`（标**阻塞**） | 已上传 PDF 可被任意人读取。 |
| A6 | **Node `/api/execute` 公开**（仅 IP 限流） | `backend-api-matrix.md:50`（标**阻塞**） | 可被占用沙箱容量。 |
| A7 | **Node 错误结构无 `requestId`** | `backend-api-matrix.md:15` | 与 Spring 契约不一致，排查靠猜。 |
| A8 | **课件覆盖审计：260/360 页未核验、38 份原课时无来源** | `docs/structify-lesson-coverage-audit-2026-09-16.md:357-361` | 教材可信度仍是大缺口（第三轮数字，09-17 后补了部分草稿未导入）。 |
| A9 | **8 个"改写稿"选段待按 PDF 重录**（隔离码 `TEXT_IS_NOT_VERBATIM_SOURCE`） | 同上 `:362` | 04-03、05-03、06-02、06-03、06-04、08-01 等 |
| A10 | **旧动画记录迁移缺口**（只有字符串/animationId 的历史引用） | `docs/structify-repair-handoff-2026-09-15.md:29,47` | 会提示不可执行，不替换为默认演示。 |
| A11 | 过期文档与代码不一致（矩阵称管理端"前端未接入"但已接入；README 仍按 Node 单页前端描述；`AdminHomeView` 保留过时 sandbox 回退分支；`ModulePlaceholderView` 是死代码） | `backend-api-matrix.md:70-78`、`README.md:5-15`、`AdminHomeView.vue:31-33` | 误导后来人。 |

### （B）功能未实现

| # | 事项 | 出处 |
|---|---|---|
| B1 | **Spring 无教师任务/作业聚合**（总览、领取列表、创建/更新、归档）——只有 Node 有 | `api-freeze-v1.md:108` |
| B2 | **Spring 无"上传即发布"API，也无 Node 私有 PPT plan 的 HTTP 浏览/资产服务等价** | `api-freeze-v1.md:109` |
| B3 | **无 WebSocket，仅 HTTP SSE** | `api-freeze-v1.md:110` |
| B4 | **后台任务不是通用 job queue**（当前唯一可提交类型是 `STALE_TASK_RECOVERY`） | `api-freeze-v1.md:111` |
| B5 | **审核域不含 `CLASSROOM_SCRIPT` / `ANIMATION_OBSERVATION`**，非 PPT 的 DSVP 快照不进审核 | `api-freeze-v1.md:26,45` |
| B6 | **Node 遗留兼容路由整体未退役**（11 行矩阵全在服务） | `backend-api-matrix.md:41-51` |
| B7 | **前端学习面整块摘除未替代**：`/user/*` 重定向到 `/`，chat / 资料库 / 代码实验 / 学习工作台 / 教师面板均无路由 | `user/routes.ts:8-10` |
| B8 | **`/api/v1/ai/readiness` 无页面消费**（客户端函数在、页面已摘除） | `backend-api-matrix.md:78` |
| B9 | **教师可编辑角色提示词未实现**（当前静态预设） | `docs/superpowers/specs/…discussion-design.md:143` |
| B10 | 首版明确不做（需保持）：不做完整在线判题、不接学生账号体系、不保证真实运行所有代码 | `00-current-status.md:352-354` |

### （C）已做，但未部署 / 未验证

| # | 事项 | 现状 |
|---|---|---|
| C1 | **31 行 API 矩阵标注"代码已发布；未逐路由完成鉴权 smoke"**（Node 11 行 + Spring 20 行） | 真实生产账号未逐路由复验授权 |
| C2 | 管理端 PATCH / 管理端聚合的 Caddy 预检生产未部署验证 | `backend-api-matrix.md:72,89` |
| C3 | Spring 审核知识检索未在生产数据上验证 | `backend-api-matrix.md:61` |
| C4 | **管理端前端 8 页已接入，但生产逐路由验收未做** | 见 A11 |
| C5 | v1.0.71 签名 URL / WebP / 退避 / 预取**已上线**，但**真实学生网络未采样** | `output/latency-diagnosis.md:45-49` |
| C6 | 课件主线课堂只在**单个课时（08-04）**上做过真实生产备课；可触发面为 48 个有课件课时中的 43 个 | `output/classroom-prep-scope-fix-verification.md:19` |
| C7 | 教材受控导入只进了本机隔离 H2，生产网页仍是旧库内容 | `00-current-status.md:88,93,96` |
| C8 | 平台实测报告（`12-platform-trial-report.md`）仍是未填写模板 | 自评"真实平台落地 约 0%–20%" |

### （D）需要你拍板

| # | 决策点 | 出处 |
|---|---|---|
| D1 | **是否把 `slides.annotations.json` 部署到生产**（A1 的解药） | `00-current-status.md:14` |
| D2 | **备案 / 源站迁境外 / 换 CDN 的路线与时间**——这是跨境 0.35s 地板的唯一解 | `output/latency-diagnosis.md` |
| D3 | **备课结果是否复用**（同课时第二次开课不重跑模型） | `output/classroom-prep-scope-fix-verification.md:53` |
| D4 | 教材核验的范围与节奏（剩余从 `03-02（86–88）` 起，顺序 03→04→…→10） | `structify-lesson-coverage-audit…:361` |
| D5 | 8 个改写稿选段的重录口径与优先级 | 同上 `:362` |
| D6 | 陈旧文件是否清理（`private/reviewed-textbook/lessons/` 9 条、弃用的 `plan-reviewed-textbook-import.mjs`、仓库根 ~60 个 `tmp_*` 探针脚本） | 同上 `:363,369` |
| D7 | 20 条 `generated-%` 草稿课堂是否清理 | `output/qa-grading-spine-verification.md:55` |
| D8 | 11 项既有测试失败是否单独开修（A3） | `output/classroom-prep-scope-fix-verification.md:52` |
| D9 | Node/Spring 收敛范围与 legacy 前端退役节奏（B6/B7） | `docs/api-node-spring-differences.md:68-73` |
| D10 | 仍需老师提供：课程 PPT/讲义、实验指导/作业题、指定平台或账号、真实平台智能体分享链接、小组人数与成员特长 | `00-current-status.md:342-350` |

---

## 七、建议的推进顺序

> 2026-09-22 更新：第一批的 **A2 / A5 / A6**、第二批的 **A3 / A4**、以及 A7 / A11 已完成，本地全绿（后端 433 项、前端 261 项），见第八节。逐路由鉴权 smoke 又带出 **A12 / A13 / A14** 三个缺陷（SSE 端点的 401 无法渲染、同端点 500、课件资产可被匿名枚举），同轮一并修掉并发布。第一批现在只剩 **D1 → A1**（要你拍板）。

**第一批（性价比最高，都在几百行以内）**
1. **A2 改提示词**——把"未实现的算法"名单与能力注册表对齐。这是纯文字错误，改完学生立刻能看到 Dijkstra/AVL/B 树/外部排序的动画。
2. **D1 → A1 部署 `slides.annotations.json`**——一次性把每页的 `section/role/terms` 补齐，直接改善备课质量与教材候选精度。
3. **A5 + A6 收口 Node 两个"阻塞"安全项**（`/pdfs/*` 加鉴权、`/api/execute` 加登录门槛）。

**第二批（守住质量）**
4. **A3 修 11 项既有失败 + A4 给动画边界探针加失败门禁**——让"全绿"重新可信。
5. **C1/C4 逐路由鉴权 smoke**——31 行矩阵不能靠"代码已发布"当结论。

**第三批（补完产品面）**
6. **B7/B8 前端学习面**：把 chat / 资料库 / 代码实验重新接上（接口都在，页面被摘了），顺带让 readiness 有页面消费。
7. **B1/B2 把教师侧能力迁到 Spring**，为最终退役 Node 铺路（B6）。

**第四批（内容工程，最重）**
8. **A8/A9 教材核验剩余 260 页 + 8 个改写稿重录**——这是这个项目真正的护城河，也是唯一无法靠改代码加速的部分。
9. **C7 把已核验教材导入生产。**

**并行/独立**
- **D2 备案**：一旦落定，v1.0.71 的签名 URL 直接受益（同一套 URL 换大陆节点，HIT 从 0.35s 降到几十毫秒），**不需要任何代码改动**。
- **D6/D7 清理**：可以随时做，不阻塞别的。

---

## 八、本轮处理结果（2026-09-22）

未动（B）类。以下为（A）/（C）/（D）中已处理的部分。

### 已修并已验证

| # | 事项 | 改了什么 | 验证 |
|---|---|---|---|
| A2 | 提示词漂移——**实际比原描述更严重** | 四处手写清单（`ClassroomPreparation`、`ClassroomTimeline`、`DsvpModelContract.INSTRUCTIONS`、`AnimationIntentController`）统一改为由引擎能力表生成：`DsvpAnimationAdapter.animationRules(chapterOrLessonId)` = 引擎按章清单 + 内建模拟器 `OPERATIONS` 表 + 参数形状规则。旧清单同时犯两个**方向相反**的错误：给出引擎根本不支持的 `array/heap/hash`，又把引擎已实现的 Dijkstra/AVL/B 树/外部排序说成"本平台未实现"。 | 引擎不可用时仍保留内建清单（那层自己会画）；167 条能力各自的 `demoArguments` 全跑通 `resolveVisualizationIntent → simulateOperation`，167/167 产出 trace |
| A3 | 11 项既有测试失败 | **根因不是"测试过期"**：证据层拿 `response.request()` 做鉴权，而本地引擎回显的请求不含 `context`（引擎不转发它），于是课堂会话 / 课件页 / `source_ref` 三类来源在校验时全部变成"调用方没有指定来源"，`TEAM_ONLY` 的 403 从未生效。修法：`DsvpAnimationAdapter.withClientContext()` 在引擎回显上补回客户端上下文，授权、证据哈希、快照、回显共用同一形状（`canonicalContext`）。 | 关引擎→通过、开引擎→200，钉死根因；修复后 `mvn -o test` **430 项、0 失败、BUILD SUCCESS** |
| A4 | 边界探针无门禁 | `probe-animation-boundaries.js` 改为基线门禁（默认 `BASELINE=13`，高于基线 exit 1，低于则提示下调） | 实测 `PASS：边界发现 13 条，与基线一致`，exit 0 |
| A5 | Node `/pdfs/*` 匿名可读 | 加登录门槛；`cache-control` 由 `public` 改 `private` | 断言改为匿名 401 / 带 token 200 / 越界与符号链接 404；**生产 `v1.0.72` 复跑通过**：匿名 `/pdfs/nope.pdf` → 401，`verify-security-hardening.js` → `security-hardening-ok headers=4 uploads=3 auth-rate=1 code-attempts=1 lock=1 no-code-logs=1` |
| A6 | Node `/api/execute` 公开 | 加登录门槛。Spring `/api/v1/code/**` 的 guest 语义是已文档化契约（矩阵标"guest 或登录"），**不动** | 新增"匿名 execute 被拒 401 + `code=AUTH_REQUIRED` + `requestId` 与响应头一致"；**生产 `v1.0.72` 复跑通过**：匿名 `/api/execute` → 401 + `code=AUTH_REQUIRED`，`execute-security-check.js` 在同一镜像里 **6/6 PASS**（`PASS anonymous execute is refused`、`PASS rate limit on execute`、`PASS per-ip concurrency limit`、`PASS execution timeout guard`、`PASS fallback after primary executor timeout`、`PASS output truncation`，exit 0） |
| A7 | Node 错误无 `requestId` | `sendJson` 收口：错误体自动补齐 `requestId/code/message/details`，保留 `error` 兼容旧前端；请求入口生成/回显 `X-Request-Id` | 同上两个脚本覆盖；**生产实测**：匿名拒绝体带 `requestId`，且与响应头 `X-Request-Id` 一致 |
| A11 | 文档与代码不一致 | `AdminHomeView` 删掉 sandbox 的本地兜底假状态（服务端本来就返回 `sandboxSettings`）；矩阵 7 行管理端"前端未接入"改为已接入并注明 `frontend/src/admin/api.ts`；README 的功能特性 / 技术栈 / 项目结构 / 快速开始按"Spring 主后端 + Vue 前端 + Node 遗留"重写 | 前端 `vitest` 47 文件 261 项通过；`vue-tsc --noEmit` 干净 |
| D8 | 跟随 A3 | 11 项失败全部转绿，不需要单独开修 | 同 A3 |

### 顺带让校验脚本能在只读生产镜像里跑

`execute-security-check.js` 原来把状态目录交给应用默认值（`private/state/node`）。生产镜像是 `read-only` rootfs，脚本每次都在断言之前就 `EACCES: mkdir /app/private/state/node` 死掉。现在脚本自带状态目录：

```js
const stateDir = fs.mkdtempSync(path.join(os.tmpdir(), "execute-security-check-"));
// NODE_STATE_DIR / DB_PATH / PDF_DIR 全部指向它
```

配合"发布容器 rootfs 只读、`docker cp` 进不去 → 改用 `docker run --rm -v <dir>:/app/scripts:ro` 一次性容器"的做法，这套校验从此在任何镜像上都能自跑。

### 顺带补上的逐路由鉴权 smoke（原 C1 / C3 / C4）

新增 `scripts/production-auth-smoke.mjs`：把矩阵里每条路由的鉴权承诺变成**无凭据的真实请求**（59 项，含管理端 Origin 的 PATCH 预检），只有全部符合预期才 exit 0。它一上来就把三个此前没人看过的缺陷带了出来：

| # | 缺陷 | 证据 | 修法 |
|---|---|---|---|
| A12 | `POST /api/v1/chat/stream` 的 401 无法渲染：该端点声明 `produces=text/event-stream`，而错误信封要经内容转换器渲染，浏览器客户端只发 `Accept: text/event-stream`——写不出去，异常逃出 servlet | 生产日志每次登出状态下的提问都多一条 `Servlet.service() ... threw exception [ApiException: Authentication is required]` ERROR 堆栈（受控计数实验：`Accept: text/event-stream` → 新增 ERROR 行 = 1，`*/*` → 0）；客户端实际拿到入口点的 `AUTH_REQUIRED`，与文档写的 `AI_QUOTA_AUTHENTICATION_REQUIRED` 不符 | `common/ApiErrorFallbackResolver`（`HIGHEST_PRECEDENCE`）在客户端无法接受 JSON 时直接写错误信封；信封构造抽成 `common/ApiErrors.write`，安全入口点改为共用它 |
| A13 | 同一个端点换成 `Accept: application/json` 直接 **500 INTERNAL_ERROR**：`produces` 不匹配抛出的 `HttpMediaTypeNotAcceptableException` 落到 `@ExceptionHandler(Exception.class)` 兜底 | 生产与本地 MockMvc 都稳定复现 500 `INTERNAL_ERROR` | `ApiExceptionHandler` 增明确的 `HttpMediaTypeNotAcceptableException` → `406 NOT_ACCEPTABLE`（客户端错误不该记成服务端故障） |
| A14 | Node `/presentation/{asset}` **先查存在性再查鉴权**：匿名调用者仅凭 401/404 就能枚举课件页 | 匿名 `GET /presentation/nope` → 404（而非 401） | `servePresentationAsset` 先判访问权，已授权但路径不存在/越界才 404 |

回归：`ChatStreamUnauthenticatedContractTest`（3 项，含"匿名 SSE 客户端拿到文档里的 code 且不抛异常"）；后端全量 `433` 项、0 失败。

已验证的生产结论（smoke 全绿在后）：C1 逐路由鉴权 59 项、C3 管理端 Origin 的 PATCH 预检 `204` + `allow-methods` 含 PATCH、C4 用验收账号在生产数据上查 `栈`/`顺序表` 各命中 3 条，`/api/v1/ai/readiness?operation=CHAT` 报 `availableKnowledgeChunkCount=536`、`evidenceAvailable=true`、`allowFormalGeneration=true`（顺带确认：**匿名检索恒为空是既定策略**，教材片段入库即 `CLASSROOM_ONLY`，而 guest 只允许 `PUBLIC`）。

### 顺带修掉的两条"测试写了实现、不是契约"

| 测试 | 问题 | 修法 |
|---|---|---|
| `ApiCacheControlHeaderWriterTest.leavesCoursewareResponsesToTheControllerCachingPolicy` | 拿一个空响应断言"写入器什么都不写"——而写入器的提前返回条件是"handler 已经写过 Cache-Control"，空响应永远不满足；它用的还是签名 URL 改造**之前**的旧路径 | 先写入 handler 会写的策略，再断言"被保留"；另加一条"没有 handler 声明时仍必须 no-store" |
| `DsvpAnimationApiIntegrationTest` 断言 `steps[0].op == "push"` | 引擎的 `stack.push` 首步是 `check_condition`，进程内模拟器首步才是 `push`——断言钉的是执行路径，且测试开/关引擎会给出不同结果 | 改为断言能力契约：`animationData.type == stack`、steps 非空、步骤序列里含 `push` |

### 仍未处理（要你拍板或属内容工程）

- **A8 / A9 / A10** 教材核验剩余页、8 个改写稿重录、旧动画记录迁移——内容工程，代码加不了速。
- **C2** 管理端 8 页生产验收——需要管理员会话在真实浏览器里逐页走一遍，本轮只做了接口层（capability/用户/审计/审核/后台任务/模型配置的匿名拒绝 + 预检）。
- **D2 / D3 / D6 / D7 / D9** 备案路线、备课结果复用、陈旧文件清理、Node 收敛节奏。

### 2026-09-22 晚补记：A15 兜底讲稿"念课本" + A1 落地（用户截图实证）

用户在课堂上截到一段"超长、整段背教材原文"的讲稿。排查结论是一条新缺陷（A15）叠加 A1：

| 项 | 现象与根因 | 修法 |
|---|---|---|
| **A15** | 兜底讲稿模板把教材片段**原文**整段拼进 `content`（`"教材（第 N 页）的表述：…"`，上限 360 字）。当模型答案连续 3 次没过契约校验时，该段课堂按设计用本地脊线补齐——学员看到的就是这段背书。生产日志实锤：19:29 `Lesson textbook-087292… part 2/3 was taught from the local spine`（模型三次把第 10 步指错页） | `SlideSpinePlan.narration()` 改为"本页是 X + 教材第 N 页摘要 ≤110 字"，总上限 360→200；完整原文仍在该步的 evidence 里，不丢失 |
| **A1** | `slides.annotations.json` 在 `private/` 下且被 `.gitignore` 忽略、从未 `git add` → 发布包（读 git 索引）永远带不上它 → 生产课件页 `section/role/terms` 全空 → 脊线清单退化为 `[-/-]`、教材候选整课检索 → 模型更容易答错 → A15 兜底被触发。**A1 与 A15 是一条因果链** | `git add -f` 入库随发布分发；compose 给 spring-api 增 `APP_PRESENTATION_ANNOTATIONS=/app/annotations/slides.annotations.json` 与相对发布树的只读挂载 |

配套：前端讲稿面板本就支持滚动（细滚动条不明显），讲稿变短后一步一屏可读完；后续新生成的课堂应显著减少落入兜底的段落。

---

## 附：常用入口速查

| 目的 | 位置 |
|---|---|
| 产品与设计原则 | `PRODUCT.md` |
| 部署拓扑与手册 | `PRODUCTION_DEPLOYMENT_GUIDE.md`、`docs/production-deployment.md` |
| 接口契约（冻结） | `contracts/openapi-v1.yaml`、`docs/project/api-freeze-v1.md` |
| 接口/数据模型差异 | `docs/api-node-spring-differences.md`、`docs/data-model-node-spring-differences.md` |
| 逐路由可用性矩阵 | `docs/project/backend-api-matrix.md` |
| 历史进度流水 | `docs/project/00-current-status.md` |
| 教材核验缺口 | `docs/structify-lesson-coverage-audit-2026-09-16.md` |
| 动画边界 | `docs/animation-boundary-report.md` |
| 性能诊断（含 v1.0.71 实测） | `output/latency-diagnosis.md` |
