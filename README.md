# Data Structure Agent

一个面向数据结构课程的 AI 学习智能体工作台，帮助学生完成"看懂知识点、理解过程、练习题目、定位错误、生成复习建议"的学习闭环。

## 功能特性

生产面向两个站点：学习站 `https://structify.cn` 与管理端 `https://admin.structify.cn`。

学习站：

- **入口选择页** - 登录后先选「课堂」或「动画学习」，不会在用户没选的情况下自动开课或续课
- **课堂** - 按教材章真实备课（讲稿、板书、动画步骤、提问步），可继续上次课堂，也可逐页插问
- **动画学习** - 动画实验室：服务教材专有算法（图算法、AVL、B 树、外部排序等）与九类通用结构的逐步演示
- **课件浏览** - 与课时对应的 PPT 页面，页面图片按签名 URL 提供并缓存
- **教材检索增强** - 从已核验的教材片段召回内容，连同来源与页码交给模型回答
- **在线编译器** - C / Python 代码编辑与运行（受限沙箱，按用户与 IP 限流）
- **学习进度与学习雷达** - 进度聚合与薄弱点分析

管理端：

- **用户与角色 / 禁用控制**、**审核队列（含来源链与历史）**、**后台任务台账**、**审计日志**
- **模型配置与连接测试**、**邮件配置与测试**、**代码沙箱配置与测试**

横切：

- **AI 配额** - 每人每天限额，UTC 换日（= 北京 08:00），备课、插问、判答、代码分析共用同一账本

本地完整课件只放在被 Git 忽略的 `private/` 目录：原始 PPT 位于
`private/source-ppt/`，审核后的页面清单和渲染图位于
`private/presentation-materials/`，教材和 PDF 分别位于
`private/knowledge/` 与 `private/pdfs/`。生产环境使用服务器只读挂载，发布树不包含这些文件。

## 技术栈

| 层级 | 技术 |
|------|------|
| 生产后端 | Java 21 / Spring Boot，`backend/spring`，`/api/v1/*` |
| 生产数据库 | MySQL 8 + Flyway 迁移（`backend/spring/src/main/resources/db/migration`） |
| 生产前端 | Vue 3 + vue-router，Vite 6 构建，`frontend/src` → `frontend/dist` |
| 边缘与代理 | Caddy（Compose），学习站与管理端两个域名 |
| Node 兼容服务 | Node.js (>=18)，`backend/node`，旧 `/api/*`，SQLite (better-sqlite3) |
| 动画引擎 | 本地确定性引擎 `backend/dsvp`（长驻 node 子进程，JSONL 协议），另有无引擎时进程内兜底模拟器 |
| 认证 | Spring JWT（Bearer 与 `ds_session` cookie）；Node 兼容令牌仅用于少数遗留路由 |
| AI 模型 | OpenAI 兼容 API（当前 DeepSeek） |
| 代码沙箱 | Piston（`PISTON_BASE_URL`） |

## 后端

后端是 Java 21 / Spring Boot 的 [`backend/spring`](backend/spring)，使用 MySQL、Flyway、Spring Security 和 `/api/v1/*` 接口，覆盖认证、章节资料、教材 RAG、普通与流式问答、脚本课堂、结构化动画、C/Python 沙箱编译、代码分析和学习进度。它是生产后端。

`backend/node` 是上一代服务，只保留尚未迁移的遗留路由；两者是并存协议，不是版本号替换：

- Spring 后端是生产后端，监听 `8792` 并提供 `/api/v1/*`。
- `backend/node` 只保留尚未迁移的遗留路由，监听 `8791` 并提供旧 `/api/*`；它不是新前端的默认后端。
- 生产前端由 `frontend/dist` 提供，浏览器只访问 Caddy，不直接访问 8791/8792。
- Spring 本地开发默认使用 H2，生产配置使用 MySQL。

```powershell
cd backend/spring
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

后端环境变量、管理员初始化、知识库、编译器安全和部署说明见 [`backend/spring/README.md`](backend/spring/README.md)，冻结的接口契约见 [`contracts/openapi-v1.yaml`](contracts/openapi-v1.yaml)。

生产学习站为 `https://structify.cn`，管理端统一使用 `https://admin.structify.cn`。完整的 Node 8791 / Spring 8792 / MySQL / Caddy 拓扑、私有教材和 PPT 路径、备份、迁移、健康检查、DNS 切换与回滚手册见项目根目录的 [`PRODUCTION_DEPLOYMENT_GUIDE.md`](PRODUCTION_DEPLOYMENT_GUIDE.md)；`docs/production-deployment.md` 仅是兼容入口。接口差异和数据模型差异分别见 [`docs/api-node-spring-differences.md`](docs/api-node-spring-differences.md) 与 [`docs/data-model-node-spring-differences.md`](docs/data-model-node-spring-differences.md)。Git 来源已恢复并核验：远程 `origin` 为 `https://github.com/feng129685/data-structure-agent.git`，截至本次核验 `origin/main` 为 `82b073790d28cffc47fbcbe500d111078d2660c3`（可用 `git ls-remote https://github.com/feng129685/data-structure-agent.git refs/heads/main` 复核）。当前工作区以该 revision 为基线但融合修改尚未提交，不能据此宣称生产线上版本等同；发布前必须创建并记录不可变 release commit/tag 和镜像摘要。

## 快速开始

### 环境要求

- Java 21（Spring 后端）
- Maven Wrapper 随仓库提供：`backend/spring/mvnw.cmd`（Windows）或 `backend/spring/mvnw`
- Node.js >= 18 与 npm（前端；本地动画引擎也用它）
- 生产另需 MySQL 8 与 Docker Compose，见 [`PRODUCTION_DEPLOYMENT_GUIDE.md`](PRODUCTION_DEPLOYMENT_GUIDE.md)

### 获取代码

```bash
git clone https://github.com/feng129685/data-structure-agent.git
cd data-structure-agent
```

### 启动后端

```powershell
cd backend\spring
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Spring 监听 `8792`。本地默认 H2；私有教材、课件素材目录由 `private/local-test/runtime.properties` 指定。

### 启动前端

```bash
cd frontend
npm install
npm run dev      # Vite，默认 5173，/api/v1 代理到 127.0.0.1:8792
```

需要指向别的后端时用 `VITE_DEV_SPRING_ORIGIN` / `VITE_DEV_NODE_ORIGIN` / `VITE_DEV_PORT` 覆盖。

### 配置

本地运行配置在 `private/local-test/runtime.properties`（不发布）。生产不要在仓库内编辑 env：把 [`deployment/.env.spring.example`](deployment/.env.spring.example) 复制到 `/etc/structify/structify.env`，通过 secret manager 填入模型、SMTP、MySQL 和 JWT 值，并设置 `CORS_ALLOWED_ORIGINS=https://structify.cn,https://admin.structify.cn`、安全 Cookie、关闭调试/验证码捕获和静态管理员提升。

### 导入私有教材知识库

教材 OCR 不随公开仓库分发。拿到团队内部的知识包后，在 PowerShell 中执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-knowledge-pack.ps1 "D:\path\to\knowledge-pack.zip"
```

导入后重启服务。`/healthz`（Node）可查看 `knowledge.ready`、课时数和检索片段数。详细说明见 `knowledge/README.md`。

为了避免公开教材内容，`private/knowledge/` 已加入 `.gitignore`；公网环境也应保持 `KNOWLEDGE_DEBUG_API=false`。

### 运行测试

```bash
# 后端（全量，Spring 测试不需要外部依赖）
cd backend/spring && ./mvnw test

# 前端（vitest）
cd frontend && npm test
```

Node 侧回归脚本（`scripts/verify-*.js`）需要在装有根依赖的环境运行（服务器镜像内已 `npm ci`）：

```bash
npm ci && npm test
```

主回归包含教材检索、知识注入、安全响应头、文件上传权限、验证码限流以及模型流式错误/超时验证。

## 项目结构

```
data-structure-agent/
├── backend/               # 后端
│   ├── spring/            # 生产后端（Java 21 / Spring Boot，/api/v1/*）
│   ├── node/              # Node 兼容服务（遗留 /api/*，只服务少数未迁移路由）
│   └── dsvp/              # 本地确定性动画引擎（能力注册表 + 模拟器 + 边界探针）
├── frontend/              # 生产前端（Vue 3 + Vite）
│   ├── src/               # 应用源码与构建入口 src/index.html
│   ├── dist/              # vite build 产物（发布用）
│   ├── index.html         # 已被取代的旧单页原型，仅少数静态校验脚本读取
│   └── prototype.html     # 旧原型入口
├── contracts/             # 冻结的 OpenAPI 契约与结构样例
├── deployment/            # Compose、Caddy、发布与回滚脚本
├── scripts/               # 测试、校验与发布脚本
├── fixtures/http/         # HTTP 请求/响应样例
├── knowledge/             # 公开知识库说明与导入边界
├── docs/                  # 项目文档
│   ├── project/           # 历史项目文档
│   └── superpowers/       # 设计方案与规格文档
└── private/               # 本地私有课件、状态和输出（永不发布）
```

## 文档索引

| 文件 | 说明 |
|------|------|
| `docs/project/00-current-status.md` | 当前进度总览与下一步路线 |
| `docs/project/01-project-proposal.md` | 项目方案与价值说明 |
| `docs/project/02-platform-build-guide.md` | 平台搭建操作步骤 |
| `docs/project/03-agent-prompts.md` | 主智能体和子智能体提示词 |
| `docs/project/04-knowledge-base-seed.md` | 知识库首批内容模板 |
| `docs/project/05-test-cases.md` | 测试用例 |
| `docs/project/06-demo-script.md` | 展示脚本 |
| `docs/project/09-iteration-report.md` | 迭代优化记录 |
| `docs/project/13-cloudflare-deployment-guide.md` | Cloudflare 部署说明 |
| `PRODUCTION_DEPLOYMENT_GUIDE.md` | `structify.cn` 生产发布唯一操作手册 |
| `docs/production-deployment.md` | 兼容入口，跳转到根目录生产手册 |
| `docs/api-node-spring-differences.md` | Node `/api/*` 与 Spring `/api/v1/*` 接口差异表 |
| `docs/data-model-node-spring-differences.md` | SQLite 到 MySQL 数据模型和导入边界 |

## 覆盖范围

教材按 10 章组织：绪论、线性表、栈与队列、串、数组与广义表、树与二叉树、图、查找、内部排序、外部排序。动画能力由 `backend/dsvp` 的能力注册表决定，当前 167 条，覆盖各章专有算法（图的最短路与最小生成树、AVL、B 树、外部排序等）。

注册表之外还有一层进程内兜底模拟器，服务九类通用结构：`stack`、`queue`、`sequential_list`、`linked_list`、`tree`、`graph`、`heap`、`hash`、`array`。引擎不可用时这一层仍能出动画。

给模型的能力清单由注册表按教材章生成（`DsvpAnimationAdapter.animationRules`），不写死在提示词里，因此提示词不会声称某个已实现的算法不可做，也不会提供引擎其实不支持的组合。

## License

MIT
