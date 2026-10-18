# 技术栈

本文记录**当前仓库**中的运行时与构建选型；版本以 `pom.xml`、`package.json` 为准。

## 总览

| 层级 | 技术 | 说明 |
|------|------|------|
| 生产 API | Java **21** + Spring Boot **4.1.0** | `backend/spring`，默认 `/api/v1`，端口 **8792** |
| 生产 DB | **MySQL 8.4** + **Flyway** | 迁移脚本 `backend/spring/src/main/resources/db/migration/`（当前 **V1–V30**） |
| 本地/测试 DB | **H2**（`MODE=MySQL`） | `spring-boot.run.profiles=dev` 或测试 profile |
| 前端 | **Vue 3.5** + **TypeScript** + **Vite 6** + **Vue Router 4** | `frontend/`，构建产物 `frontend/dist` |
| 前端状态 | 模块内 `reactive` / composables | **无 Pinia**；**无 Element Plus** |
| 遗留 HTTP | **Node.js ≥ 18** | `backend/node/server.js`，端口 **8791**，**SQLite** |
| 动画引擎 | **DSVP**（Node 子进程，JSONL） | `backend/dsvp/`，由 Spring `animation` 包调用 |
| 认证 | Spring Security + **JWT**（Bearer / `ds_session` cookie） | Node 兼容令牌仅少数遗留路由 |
| AI | **OpenAI 兼容** HTTP API | 环境变量或管理端加密 `model_configurations` |
| 代码沙箱 | **Piston** / **Judge0**（远程） | 用户代码不在 JVM 内执行 |
| 生产边缘 | **Caddy** + **Docker Compose** | 见 `deployment/`、`PRODUCTION_DEPLOYMENT_GUIDE.md` |
| 契约 | **OpenAPI 3** | `contracts/openapi-v1.yaml` |

## 端口（本地开发）

| 服务 | 端口 | 路径前缀 |
|------|------|----------|
| Vite dev server | **5173**（可 `VITE_DEV_PORT`） | 浏览器入口 |
| Spring | **8792**（`VITE_DEV_SPRING_ORIGIN`） | `/api/v1` |
| Node 遗留 | **8791**（`VITE_DEV_NODE_ORIGIN`） | `/api`、`/presentation`、`/healthz` 等 |

## 前端依赖（核心）

- `vue` ^3.5.x，`vue-router` ^4.5.x  
- 字体：`@fontsource/inter`、`fusion-pixel`；装饰：`@paper-design/shaders`  
- 测试：**Vitest** + `@vue/test-utils` + jsdom  

## 后端依赖（核心）

Spring starters（见 `pom.xml`）：WebMVC、Security、JDBC、Flyway、Validation、Mail、Actuator；运行时 H2 + MySQL 驱动。

## 环境变量索引

完整表见 [`backend/spring/README.md`](../../backend/spring/README.md)。常用项：

| 变量 | 用途 |
|------|------|
| `JWT_SECRET` | 会话签名（≥64 字符） |
| `DB_URL` / `DB_*` | 生产 MySQL；dev 默认 H2 |
| `MODEL_API_KEY` / `MODEL_BASE_URL` / `MODEL_NAME` | LLM |
| `KNOWLEDGE_DIR` | 私有教材目录 |
| `RESOURCE_DIR` | 发布资源根目录 |
| `PISTON_BASE_URL` / `JUDGE0_BASE_URL` | 沙箱 |
| `CORS_ALLOWED_ORIGINS` | 生产必填精确 origin |
| `NODE_COMPAT_*` | Node→Spring 迁移桥（遗留） |

生产模板：**勿提交真实密钥** — 使用 `deployment/.env.spring.example` → 服务器 `/etc/structify/structify.env`。

## Profile 行为摘要

- **`dev`**：H2、本地知识/资源路径、开发便利；**本地启动请显式指定** `-Dspring-boot.run.profiles=dev`。  
- **无 profile 误部署**：基础配置 fail-closed（无 JWT 密钥等则不应“静默”当生产跑）。

## 测试栈

| 范围 | 命令 |
|------|------|
| Spring | `backend/spring/mvnw test` |
| 前端 | `frontend`: `npm test`, `npm run typecheck`, `npm run build` |
| DSVP | `node backend/dsvp/test/verify-dsvp-engine.js` 等 |
| 仓库回归 | 根目录 `npm ci && npm test` |
