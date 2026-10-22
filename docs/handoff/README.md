# Structify 开发者交接（Handoff）

面向**新加入的开发者**：在一天内完成本地跑通，并知道改功能该打开哪些目录。  
产品背景见根目录 [`README.md`](../../README.md)；接口冻结见 [`contracts/openapi-v1.yaml`](../../contracts/openapi-v1.yaml)。

## 阅读顺序

| 顺序 | 文档 | 内容 |
|------|------|------|
| 1 | 本页 | 本地跑通（Minimal / Full） |
| 2 | [01-tech-stack.md](./01-tech-stack.md) | 版本、端口、环境 |
| 3 | [02-architecture.md](./02-architecture.md) | 请求怎么走、Spring / Node / DSVP |
| 4 | [03-modules.md](./03-modules.md) | **按功能**的模块地图（课堂 / 动画 / 编译器 / 问答） |
| 5 | [04-features.md](./04-features.md) | 功能 ↔ 路由 ↔ API 速查 |
| 6 | [05-dependencies.md](./05-dependencies.md) | 外部服务与模块依赖 |

## 真相来源（改代码前先查）

| 问题 | 查哪里 |
|------|--------|
| HTTP 契约 | `contracts/openapi-v1.yaml` |
| 前端路由 | `frontend/src/router/index.ts`、`frontend/src/admin/routes.ts` |
| Spring 模块 | `backend/spring/src/main/java/com/feng/dsagent/` |
| 本地 env | `backend/spring/README.md`、`deployment/.env.spring.example` |
| Node 遗留差异 | `docs/api-node-spring-differences.md` |
| 生产发布 | `PRODUCTION_DEPLOYMENT_GUIDE.md`（非 onboarding 必读） |

**默认改代码位置：** 新功能 → **`backend/spring` + `frontend/src`**。`backend/node` 仅遗留路由，除非你在迁移某条旧 API。

---

## Minimal：无 `private/` 也能跑

**前置：** Java 21、Node.js ≥ 18、npm；Windows 可用 `backend/spring/mvnw.cmd`。

### 1. 启动 Spring（8792）

```powershell
cd backend\spring
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

- Profile **`dev`**：内存 **H2**、部分开发便利（如验证码回显策略见 Spring README）。
- 健康检查：`GET http://127.0.0.1:8792/actuator/health`

### 2. 启动前端（5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://127.0.0.1:5173`。Vite 将 `/api/v1` 代理到 `127.0.0.1:8792`（见 `frontend/vite-routing.ts`）。

### 3. Minimal 下预期行为

| 能力 | 无 `private/`、无模型 Key 时 |
|------|------------------------------|
| 注册/登录（dev） | 通常可用（依赖 SMTP 配置；dev 可能有回显策略） |
| 章节/资源元数据 | 多为种子数据，可读 |
| 课程问答 / 备课 / 动画生成 / 代码分析 | 无 `MODEL_API_KEY` 时返回 **`MODEL_NOT_CONFIGURED`**（fail-closed） |
| 教材 RAG / 完整课堂 | 无审核知识块时检索为空或无法备课 |
| 课件 `/courseware`、Spring `/api/v1/presentation/*` | 无素材目录时目录未就绪或 404 |
| C 编译器 `/compiler` | 可浏览；运行依赖沙箱 URL 配置 |
| Node 8791 | **非必须**；未启动时 `/presentation`、`/api`（非 v1）代理会失败 |

### 4. 测试（Minimal 可跑）

```powershell
cd backend\spring
.\mvnw.cmd test
```

```bash
cd frontend && npm test && npm run build
```

根目录（需 `npm ci`）：`npm test` — Node 侧 `scripts/verify-*.js` 回归。

---

## Full：团队标准本地环境

在 Minimal 基础上补齐私有内容与可选遗留服务。

### 1. 运行时路径

- 复制/维护 **`private/local-test/runtime.properties`**（Git 忽略，不提交）。
- 指向：`private/knowledge/`、`private/presentation-materials/`、`RESOURCE_DIR` 等（见 Spring `application-dev.yml` 与 `private/local-test` 示例）。

### 2. 教材知识包

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-knowledge-pack.ps1 "D:\path\to\knowledge-pack.zip"
```

说明：[`knowledge/README.md`](../../knowledge/README.md)。导入后**重启 Spring**。

### 3. 模型与沙箱

- 开发可在环境变量或管理端配置 OpenAI 兼容 API（见 `deployment/.env.spring.example`）。
- 代码运行：配置 **Piston** 和/或 **Judge0**（`PISTON_BASE_URL` / 管理端沙箱配置）。

### 4. 可选：Node 兼容层（8791）

```bash
cp .env.example backend/node/.env
npm ci
npm start
```

用于遗留 **`/api/*`**、**`/presentation/*`**、**`/healthz`** 等；前端 dev 代理见 `vite-routing.ts`。Spring 已提供大部分 `/api/v1/presentation/*` 时，Full 环境仍可能需要 Node 做课件签名 URL 桥接（见 `frontend/src/app/providers/runtime.ts` 中的 `legacyApi`）。

### 5. Full 验收建议

- Spring health 200  
- 登录后 `/classroom` 能列出课时；选一课能进入会话（需模型额度与知识/课件就绪）  
- `/animation` 能走 DSVP 模拟（不依赖 LLM 的步骤演示）  
- `node backend/dsvp/test/verify-dsvp-engine.js` 通过  

---

## CI

GitHub Actions：`.github/workflows/ci.yml`（前端 build、Spring 测试、部分脚本回归）。

---

## 与其它文档的关系

- **`docs/project-overview.md`**：含 2026-09 生产实测数字，**开发者请以本 handoff 与当前代码为准**。  
- 根目录 **`handoff/`**：历史 Workbuddy 任务书，不是结构化 onboarding。
