# 模块文档（按功能）

当前学习站**四条主功能**的代码地图如下。横切能力（认证、`shared/`、配额）见文末。

| 功能 | 路由 | 模块文档 |
|------|------|----------|
| **课堂** | `/classroom` | [modules/classroom.md](./modules/classroom.md) |
| **动画学习** | `/animation` | [modules/animation.md](./modules/animation.md) |
| **C 编辑器** | `/compiler` | [modules/compiler.md](./modules/compiler.md) |
| **课程问答** | `/chat` | [modules/chat.md](./modules/chat.md) |

## 不在此四条内的页面

| 路由 | 说明 | 代码入口 |
|------|------|----------|
| `/` | 登录后选课堂/动画 | `shared/views/HomeView.vue` |
| `/courseware` | 课件浏览（课堂配套） | `shared/views/CoursewareView.vue` + `presentation` 包 |
| `/admin/*` | 管理端 | `frontend/src/admin/` + `admin` 包 |

## 横切模块（改任一功能都可能碰到）

| 模块 | 后端 | 前端 | 作用 |
|------|------|------|------|
| **认证** | `auth`, `security` | `shared/` 登录、`router/guards` | JWT、角色 STUDENT/TEACHER/ADMIN |
| **AI 运行时** | `model`, `modelconfig`, `aiquota`, `ai` | — | `ModelClient` → `AiQuotaExecutionService`（配额 operation 名按功能区分） |
| **教材检索** | `knowledge` | — | 审核 chunk 检索；**非向量库** |
| **学习记录** | `learning` | — | 进度/事件（课堂答题、代码等） |
| **契约类型** | `contracts/` | `shared/types/contracts` | 与 OpenAPI / JSON Schema 对齐 |

## 仓库物理布局（速查）

```
frontend/src/
  classroom/   animation/   compiler/   user/（chat）   shared/   app/
backend/spring/.../com/feng/dsagent/
  classroom/   animation/   compiler/   chat/   knowledge/   presentation/
backend/dsvp/          # 动画确定性引擎（动画 + 课堂 animationRef 共用）
```

详细依赖关系见 [05-dependencies.md](./05-dependencies.md)。
