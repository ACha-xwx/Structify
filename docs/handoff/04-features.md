# 功能速查

四条主功能的**详细模块说明**（文件级、API、AI 边界）见：

| 功能 | 路由 | 模块文档 |
|------|------|----------|
| 课堂 | `/classroom` | [modules/classroom.md](./modules/classroom.md) |
| 动画学习 | `/animation` | [modules/animation.md](./modules/animation.md) |
| C 编辑器 | `/compiler` | [modules/compiler.md](./modules/compiler.md) |
| 课程问答 | `/chat` | [modules/chat.md](./modules/chat.md) |

---

## 路由表（学习站）

| 路由 | 登录 | 一句话 |
|------|------|--------|
| `/` | 是 | 选课堂或动画 |
| `/classroom` | 是 | 脚本化互动课 + 课件 |
| `/animation` | 是 | DSVP 动画实验室 |
| `/compiler` | 否 | C/Python 沙箱运行 + AI 分析 |
| `/chat` | 是 | 教材 RAG 问答 |
| `/courseware` | 是 | 浏览课件 deck（课堂配套） |

管理端：`/admin/*`（8 页），见 `frontend/src/admin/routes.ts`。

---

## API 前缀（Spring `/api/v1`）

| 功能 | 前缀 |
|------|------|
| 课堂 | `/classroom/*` |
| 动画 | `/animations/*` |
| 编译器 | `/code/*` |
| 问答 | `/chat/*` |
| 横切 | `/auth/*`, `/learning/*`, `/ai/readiness`, `/knowledge/search` |

完整路径与 body：**[`contracts/openapi-v1.yaml`](../../contracts/openapi-v1.yaml)**。

---

## 横切

- **认证**：JWT；编译器运行可对游客按 IP 限流。  
- **AI 配额**：课堂、问答、代码分析、`/animations/generate` 与 `interpret` 等走 `aiquota`；**`/animations/simulate` 与 `/plan` 不计 token**。  
- **进度**：`learning` 包聚合事件。
