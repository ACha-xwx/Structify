# Structify

面向数据结构课程的 AI 学习工作台，帮助学生完成「看懂知识点 → 理解过程 → 练习 → 定位错误 → 复习」的学习闭环。

生产面向两个站点：学习站 [structify.cn](https://structify.cn) 与管理端 [admin.structify.cn](https://admin.structify.cn)。

## 功能特性

**学习站**

- **入口选择** — 登录后先选「课堂 / 学习室」或「动画学习」，不会在未选择时自动开课或续课
- **学习室（主链路）** — 审核大纲 → 冻结片段 → 有界准备 → 校验后发布 → 按知识点导航；连续课堂含事件流、追问、自测与顺序讲稿
- **动画学习** — 教材专有算法（图算法、AVL、B 树、外部排序等）与通用结构的逐步演示
- **课件浏览** — 与课时对应的幻灯片，按签名 URL 提供并缓存
- **教材检索增强** — 从已核验教材片段召回内容，连同源与页码交给模型
- **在线编译** — C / Python（受限沙箱，按用户与 IP 限流）
- **学习进度** — 章节活动聚合与薄弱点分析

**管理端**

- 用户与角色 / 禁用控制、审核队列（含来源链与历史）、后台任务台账、审计日志
- 模型 / 邮件 / 代码沙箱配置与连接测试

**横切**

- **AI 配额** — 每人每日限额，UTC 换日（= 北京 08:00）；备课、插问、判答、代码分析共用同一账本
- 本地完整课件只放在 Git 忽略的 `private/`：原始 PPT 在 `private/source-ppt/`，审核后的页面与渲染图在 `private/presentation-materials/`，教材与 PDF 分别在 `private/knowledge/` 与 `private/pdfs/`。生产使用服务器只读挂载，发布树不包含这些文件；**不要**把 `WebP/`、知识包 zip 等提交进公开仓库

## 技术栈

| 层级 | 技术 |
|------|------|
| 生产后端 | Java 21 / Spring Boot，`backend/spring`，`/api/v1/*` |
| 持久化 | MySQL 8 + Flyway；本地开发可用 H2 `MODE=MySQL` |
| 映射 | MyBatis（`resources/mapper/**` XML）+ MapStruct `*Converter` |
| 生产前端 | Vue 3 + TypeScript + Vite，`frontend/` |
| 边缘与代理 | Caddy（Compose），学习站与管理端双域名 |
| 兼容服务 | Node（`backend/node`），旧 `/api/*` + SQLite；**不再新增学习室工作流** |
| 动画引擎 | `backend/dsvp`（长驻子进程，JSONL）；引擎不可用时有进程内兜底模拟 |
| 认证 | Spring JWT（Bearer 与 `ds_session` cookie） |
| AI 模型 | OpenAI 兼容 API（当前 DeepSeek） |
| 代码沙箱 | Piston（`PISTON_BASE_URL`） |
| 契约 | [`contracts/openapi-v1.yaml`](contracts/openapi-v1.yaml) |

Spring 强制叶子分包、命名与 ArchUnit 见 [`docs/architecture/backend-standards.md`](docs/architecture/backend-standards.md)。AI / Agent 必读：[`docs/AI开发规范.md`](docs/AI开发规范.md)、[`AGENTS.md`](AGENTS.md)。

## 后端

生产后端是 [`backend/spring`](backend/spring)：认证、章节资料、教材 RAG、问答、学习室 / 课堂、结构化动画、C/Python 沙箱、代码分析与学习进度，统一走 `/api/v1/*`。

`backend/node` 是上一代服务，只保留尚未迁移的遗留路由；两者是并存协议，不是版本号替换：

- Spring 监听 `8792`，提供 `/api/v1/*`，是生产与新前端默认后端
- Node 监听 `8791`，提供旧 `/api/*`；不是新前端的默认后端
- 生产前端由 `frontend/dist` 经 Caddy 对外；浏览器不直接访问 8791/8792
- Spring 本地默认 H2，生产用 MySQL

环境变量、管理员初始化、知识库与编译器安全见 [`backend/spring/README.md`](backend/spring/README.md)。Node/Spring 差异见 [`docs/architecture/node-spring-compat.md`](docs/architecture/node-spring-compat.md)。部署见 [`docs/部署与运维.md`](docs/部署与运维.md)。

## 快速开始

### 环境要求

- Java 21（`backend/spring/mvnw.cmd` / `mvnw`）
- Node.js >= 18（建议与 CI 一致用 22）与 npm
- 生产另需 MySQL 8 与 Docker Compose，见 [`docs/部署与运维.md`](docs/部署与运维.md)

### 获取代码

```bash
git clone https://github.com/SingularityVirgo/Structify.git
cd Structify
```

### 启动（推荐）

```powershell
# 终端 1：Spring（8792）+ 本地持久 H2（private/dev-db）
.\scripts\start-studyroom-preview.ps1

# 终端 2：前端（5173，/api/v1 代理到 Spring）
cd frontend
npm ci
npm run dev -- --port 5173 --strictPort
```

- 前端：http://localhost:5173/
- 健康检查：http://127.0.0.1:8792/actuator/health
- **不要**对同一 H2 文件再开第二个 Spring 进程；不要在启动时 reset 库

开发账号与配置边界见 [`docs/本地开发.md`](docs/本地开发.md)。

若预览脚本因编码解析失败，可在 `backend/spring` 下：

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

并自行设置 `DB_URL` / `SERVER_PORT`（与预览脚本一致）。根目录 `npm run dev` 启动的是 **Node 兼容服务**，不是 Vue。

需要指向别的后端时用 `VITE_DEV_SPRING_ORIGIN` / `VITE_DEV_NODE_ORIGIN` / `VITE_DEV_PORT` 覆盖。

### 配置

本地运行配置多在 `private/local-test/runtime.properties`（不发布）。遗留 Node 兼容服务读仓库根 `.env` 模板：`cp .env.example backend/node/.env`。

生产不要在仓库内编辑 env：把 [`deployment/.env.spring.example`](deployment/.env.spring.example) 复制到服务器 secret 路径（如 `/etc/structify/structify.env`），填入模型、SMTP、MySQL 和 JWT，并设置 `CORS_ALLOWED_ORIGINS=https://structify.cn,https://admin.structify.cn`、安全 Cookie，关闭调试 / 验证码捕获与静态管理员提升。

### 导入私有教材知识库

教材 OCR 不随公开仓库分发。拿到团队内部知识包后：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-knowledge-pack.ps1 "D:\path\to\knowledge-pack.zip"
```

导入后重启服务。详细边界见 `knowledge/README.md`。`private/knowledge/` 已在 `.gitignore`；公网环境保持 `KNOWLEDGE_DEBUG_API=false`。

### 验证

```bash
# 学习室相关入口（未实现的检查不得报成功）
node scripts/verify-studyroom.mjs --scope architecture
# 可选：backend | frontend | contracts | e2e | all

# 后端 / 前端单测
cd backend/spring && ./mvnw.cmd test
cd frontend && npm test
```

Node 侧遗留回归（`scripts/verify-*.js`）需在装有根依赖的环境运行：

```bash
npm ci && npm test
```

## 项目结构

```
Structify/
├── backend/
│   ├── spring/          # 生产 API（/api/v1/*）
│   ├── node/            # 遗留兼容路由（/api/*）
│   └── dsvp/            # 确定性动画引擎（能力注册表 + 模拟器）
├── frontend/            # Vue 3 + Vite（src → dist）
├── contracts/           # OpenAPI 与结构样例
├── fixtures/http/       # HTTP 请求/响应样例
├── deployment/          # Compose / Caddy / 发布与回滚
├── scripts/             # 校验、导入、预览等
├── knowledge/           # 公开知识库说明（非完整私有教材）
├── docs/                # 活文档入口 docs/README.md；历史在 docs/archive/
└── private/             # 本机私有库与课件（永不提交）
```

## 覆盖范围

教材按 10 章组织：绪论、线性表、栈与队列、串、数组与广义表、树与二叉树、图、查找、内部排序、外部排序。

动画能力由 `backend/dsvp` 的能力注册表决定，覆盖各章专有算法；注册表之外还有进程内兜底模拟器，服务九类通用结构：`stack`、`queue`、`sequential_list`、`linked_list`、`tree`、`graph`、`heap`、`hash`、`array`。引擎不可用时这一层仍能出动画。

给模型的能力清单由注册表按教材章生成（`DsvpAnimationAdapter.animationRules`），不写死在提示词里。

## 文档

完整索引：[`docs/README.md`](docs/README.md)。

| 文档 | 说明 |
|------|------|
| [docs/项目概览.md](docs/项目概览.md) | 产品定位与核心概念 |
| [docs/本地开发.md](docs/本地开发.md) | 本地启动与配置边界 |
| [docs/系统架构.md](docs/系统架构.md) | 服务关系与主链路 |
| [docs/部署与运维.md](docs/部署与运维.md) | 部署导航、沙箱与私有资源 |
| [docs/architecture/studyroom.md](docs/architecture/studyroom.md) | 学习室实现状态与边界 |
| [docs/architecture/backend-standards.md](docs/architecture/backend-standards.md) | Spring 分包 / 命名 / MyBatis |
| [docs/architecture/node-spring-compat.md](docs/architecture/node-spring-compat.md) | Node/Spring 兼容差异 |
| [docs/architecture/backend-duplication-audit.md](docs/architecture/backend-duplication-audit.md) | 重复逻辑审计结论 |
| [docs/产品方向.md](docs/产品方向.md) | 产品演进方向 |
| [docs/AI开发规范.md](docs/AI开发规范.md) | Agent 强制规范 |
| [docs/archive/](docs/archive/) | 历史报告与过程材料 |

## License

MIT
