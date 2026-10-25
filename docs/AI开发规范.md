# Structify AI 开发规范

**地位**：本仓库 AI / Agent 开发的权威规则。所有 Cursor Agent、子代理与人工在本仓库做 AI 相关改动时必须遵守。与本规范冲突时，以本规范与已 Accepted 的后端 ADR 为准；细节实现状态以源码与 OpenAPI / Flyway 为准。

**配套入口**：[AGENTS.md](../AGENTS.md)（仓库地图与命令）、[docs/README.md](README.md)（文档索引）。本文件不替代契约与迁移文件。

修订本规范时：同步更新 `.cursor/rules/structify-ai-development.mdc` 中的硬规则摘要，并在 PR / 说明中写明变更原因。

---

## 1. 单一事实来源（SSOT）

| 主题 | 权威位置 |
|------|----------|
| HTTP 字段、错误、路径 | `contracts/openapi-v1.yaml` + Controller + `fixtures/http` |
| 表结构 | `backend/spring/src/main/resources/db/migration`（仅前向 Flyway） |
| 学习室行为与边界 | [architecture/studyroom.md](architecture/studyroom.md) |
| Agent 权限 / 租约 / 预算 / 发布 | [adr/backend/阅读指南.md](adr/backend/阅读指南.md) |
| Spring 分层与 MyBatis | [architecture/backend-standards.md](architecture/backend-standards.md) |
| Node 与 Spring 并存 | [architecture/node-spring-compat.md](architecture/node-spring-compat.md) |
| 产品演进意图（未实施不算完成） | [产品方向.md](产品方向.md) |
| 验证命令 | [测试与验证.md](测试与验证.md)、`scripts/verify-studyroom.mjs` |

禁止在指南或注释中再抄一份会过期的字段表 / DDL。概念可解释，字段以契约为准。

---

## 2. 责任边界（不可谈判）

1. **模型提议，服务器裁定**：教学文案可由模型生成；资源绑定、权限、预算、权威状态与发布必须由服务端代码决定。
2. **生成不得直接发布；发布不得调用模型。**
3. **模型输出不能**：授权资源、改写 run 身份、重置预算、伪造学习证据。
4. **目标链路**：审核大纲 → 冻结片段 → 有界准备 → 校验后发布 → 按知识点导航。禁止恢复已删除的 UNIT 生成链或双模式兼容视图。
5. **保留正确性机制**：草稿修订、run fencing、租约、幂等键、调用账本与 token 记账。这些是现行正确性要求，不是遗留兼容。
6. **Node 兼容服务**（`backend/node`，`/api/*`）可保留既有能力；**禁止**向 Node 写入新的学习室 / 产品主工作流。新能力进 Spring `/api/v1/*`。
7. **学生进度 ≠ 生成就绪**：可读内容发布不等于学生已学完；两类状态分开维护。

---

## 3. 证据与表述纪律

- 源码存在 ≠ 线上已启用；历史验收通过 ≠ 当前再次通过。
- 未运行的测试不得写成“已通过”。未核对的生产配置不得写成根因已关闭。
- `docs/archive/` 与过期报告只作历史；不得当作当前验收证据。
- 决策原因无来源时标「待确认」，禁止补造历史动机。
- 普通单测 / 浏览器回归**禁止**调用付费真实模型；使用确定性替身与 fixture。
- 向量检索、OCR 增强等在 [产品方向.md](产品方向.md) 中仍属提议的能力，未落地前不得写进用户可见文案或 README 功能清单冒充已完成。

---

## 4. 工程硬规则

### 后端（Spring）

- 按业务能力分包；禁止全局 `controller/` / `service/` / `repository/` 技术分层。
- 新持久化访问走 MyBatis Mapper + XML；禁止新代码直接 `JdbcTemplate`；禁止引入 JPA。
- Controller 不做 Mapper / 手写 SQL / 模型写库副作用。
- 数据库变更只加新版本迁移；不改已应用迁移；不启动时重置库。
- 两个进程不得共用同一 H2 文件。

### 契约与前端

- 改 API 先改 OpenAPI（及必要 Schema），再改实现与 fixtures；禁止把 `/api/*` 字符串批量替换成 `/api/v1/*`。
- 学习室 UI 不得在客户端重定义服务端资源权限或生成状态机。

### 仓库卫生

- 不提交凭证、JWT、`.env`、数据库、`private/`、本地课件或压缩包。
- 不用 `git add .`；不改无关脏文件。
- 行为变更同步：相关活文档、ADR（若决策变化）、契约与测试。

---

## 5. Agent 工作流程

1. 改学习室 / 生成链前：读 [studyroom.md](architecture/studyroom.md) 与相关 ADR；状态以架构页为准。
2. 改 Spring 结构 / 持久化前：读 [backend-standards.md](architecture/backend-standards.md)。
3. 实现后：按改动范围运行 `node scripts/verify-studyroom.mjs --scope …`；未跑过的 scope 不得宣称通过。
4. 文档：只更新活文档；历史材料进 `archive/`，不在根目录堆新报告冒充权威。
5. 完成声明必须可核对：改了什么、跑了什么、未跑什么、已知风险。

---

## 6. 明确禁止

- 用模型幻觉的资源 ID、路径或证据哈希直接落库或授权。
- SENT / 不确定调用结果上盲目自动重试耗额度。
- 为“演示通过”跳过校验、租约或发布事务。
- 删除共享模块或 Node 兼容面却未追溯调用方。
- 把 `superpowers/` 旧计划或 `archive/` 验收当作当前已完成清单。

---

## 7. 变更本规范

任何人（含 Agent）修改本文件或对应 Cursor rule 时：

1. 保持条文可执行（禁止空泛口号）。
2. 与 Accepted ADR 冲突时先提 ADR / 架构页修订，再改本规范。
3. 在提交说明中写清「规则变更」及影响面。
