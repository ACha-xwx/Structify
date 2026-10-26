# Spring 后端重复逻辑审计

整理日期：2026-10-26（状态更新同日晚）。范围：`backend/spring` 按能力模块的重复点与可合并类。  
策略：P0 低风险合并已落地；B2 中 ConnectionTest/Capability VO、Rate limiter、`ApiException`→`BizException` 已完成；SSRF / Crypto / studyroom `Classroom*` 重命名仍延期。

相关规范：[backend-standards.md](backend-standards.md)。

---

## 本轮状态

| 阶段 | 状态 |
|------|------|
| P0（B1） | **已完成** |
| B2 — ConnectionTest / Capability VO、Rate limiter、`ApiException`→`BizException` | **已完成**（2026-10-26） |
| B2 — SSRF URL 校验、AES-GCM、studyroom `Classroom*` 重命名 | **仍延期** |
| B3 | **明确不合并** |

---

## P0 已完成（B1）

共享辅助落在 `common`，各能力仍抛自己的 `PAGE_INVALID` / 业务码。

| 项 | 落点 | 采用方 |
|----|------|--------|
| 分页校验 | [`PageParams`](../../backend/spring/src/main/java/com/feng/dsagent/common/support/PageParams.java) | Admin / Review / AdminKnowledge / BackgroundTask |
| 字符串规范化 | [`StringNormalizer`](../../backend/spring/src/main/java/com/feng/dsagent/common/support/StringNormalizer.java) | 同上（`optional` / `upper`） |
| Query offset | [`PagedQuery`](../../backend/spring/src/main/java/com/feng/dsagent/common/query/PagedQuery.java) | `AdminUserQuery`、`AdminAuditQuery`、`BackgroundTaskQuery`、`ReviewQuery` |
| `ErrorCode.toException()` | [`ErrorCode`](../../backend/spring/src/main/java/com/feng/dsagent/common/error/ErrorCode.java) 默认方法 | Admin / Review 等枚举去掉重复实现 |
| 分页信封工厂 | [`PageVO.of`](../../backend/spring/src/main/java/com/feng/dsagent/common/vo/PageVO.java) | 上述 list 接口（JSON 仍为 `items/page/size/total`） |

已去掉各 Service 私有的 `validatePage` / `normalizeOptional` / `normalizeUpper`（Review 仍保留领域用的 `nonBlank`）。

---

## 按模块发现摘要

检验顺序与依赖一致：`common` → `security/auth` → `admin/review` → `mail/modelconfig/compiler` → `chat/resource/knowledge` → `classroom` → `studyroom/*` → `learning` / 其它。

### common

- P0 辅助已就位。
- ConnectionTest / Capability VO、统一限流、`BizException` 业务失败路径已落地；SSRF / Crypto 见 B2 延期项。

### security / auth

- Auth 限流继续用 [`WindowRateLimiter`](../../backend/spring/src/main/java/com/feng/dsagent/common/WindowRateLimiter.java) 多窗口组合（登录 / 验证码策略不同，不与 FeatureRateLimiter 合并）。
- 认证与业务路径已统一为 `BizException`（`ApiException` 类型已移除）。

### admin / review

- P0 分页与规范化重复 **已消除**。
- 审计写入仍有多处 `appendAudit` 样板，属编排差异，不强制抽公共 Service。

### mail / modelconfig / compiler

- ConnectionTest 已下沉 [`ConnectionTestVO`](../../backend/spring/src/main/java/com/feng/dsagent/common/vo/ConnectionTestVO.java)；Mail / ModelConfig Capability 用 [`ConfigCapabilityVO`](../../backend/spring/src/main/java/com/feng/dsagent/common/vo/ConfigCapabilityVO.java)。Sandbox capability 因附带 runtime 字段仍用 [`SandboxConfigCapabilityVO`](../../backend/spring/src/main/java/com/feng/dsagent/compiler/vo/SandboxConfigCapabilityVO.java)。
- URL 校验双生、AES-GCM 双生 → B2 延期。
- 三套 ConfigService 同形 → B3。

### chat / resource / knowledge

- Chat 与模型功能限流已合并为 [`FeatureRateLimiter`](../../backend/spring/src/main/java/com/feng/dsagent/common/FeatureRateLimiter.java)（底层仍为 `WindowRateLimiter`）。
- Knowledge 两套 Loader → B3。

### classroom vs studyroom

- 顶层 `classroom.*` 课时会话与 `studyroom` 片段流为不同产品边界 → B3。
- studyroom 内大量 `Classroom*` 命名与顶层撞名 → B2 延期（只建议重命名，不合并逻辑）。

### learning / admin 进度

- Admin 学习视图与 Learning 进度服务受众不同 → B3。

---

## B2 — 已完成

| 项 | 落点 | 说明 |
|----|------|------|
| `*ConnectionTestVO` / config `*CapabilityVO` | [`ConnectionTestVO`](../../backend/spring/src/main/java/com/feng/dsagent/common/vo/ConnectionTestVO.java)、[`ConfigCapabilityVO`](../../backend/spring/src/main/java/com/feng/dsagent/common/vo/ConfigCapabilityVO.java)；Mail / ModelConfig / Sandbox testConnection 已采用；Sandbox capability 因 runtime 形态保留专用 VO | 公共 VO 已下沉；Sandbox capability 不强迫套进三字段泛型 |
| Rate limiter 包装 | [`FeatureRateLimiter`](../../backend/spring/src/main/java/com/feng/dsagent/common/FeatureRateLimiter.java) + [`WindowRateLimiter`](../../backend/spring/src/main/java/com/feng/dsagent/common/WindowRateLimiter.java)；ChatController 等采用 FeatureRateLimiter | 原 Chat / model-feature 双包装已去掉 |
| `ApiException` → `BizException` | [`BizException`](../../backend/spring/src/main/java/com/feng/dsagent/common/exception/BizException.java) + [`ApiExceptionHandler`](../../backend/spring/src/main/java/com/feng/dsagent/common/exception/ApiExceptionHandler.java)；`ApiException` 类型已删除 | 全库业务失败走 BizException / ErrorCode |

---

## B2 — 仍延期

| 项 | 证据路径 | 原因 / 后续 |
|----|----------|-------------|
| SSRF URL 校验双生 | [`ModelConfigUrlValidator`](../../backend/spring/src/main/java/com/feng/dsagent/modelconfig/support/ModelConfigUrlValidator.java) ↔ [`SandboxConfigUrlValidator`](../../backend/spring/src/main/java/com/feng/dsagent/compiler/support/SandboxConfigUrlValidator.java) | 安全敏感，需单独 PR |
| AES-GCM 双生 | [`ModelConfigCrypto`](../../backend/spring/src/main/java/com/feng/dsagent/modelconfig/support/ModelConfigCrypto.java) ↔ [`MailConfigCrypto`](../../backend/spring/src/main/java/com/feng/dsagent/mail/support/MailConfigCrypto.java) | 须保持 version / AAD 线兼容 |
| studyroom 内 `Classroom*` 命名 | 例：[`ClassroomLectureService`](../../backend/spring/src/main/java/com/feng/dsagent/studyroom/teaching/service/ClassroomLectureService.java)、[`ClassroomQuestionService`](../../backend/spring/src/main/java/com/feng/dsagent/studyroom/questions/service/ClassroomQuestionService.java) 等，与顶层 [`classroom`](../../backend/spring/src/main/java/com/feng/dsagent/classroom/service/ClassroomService.java) 撞名 | **不合并逻辑**；重命名需产品文档先行 |

---

## B3 — 明确不合并

| 项 | 证据方向 | 理由 |
|----|----------|------|
| `classroom.*` 课时会话 vs `studyroom` 片段流 | [`ClassroomService`](../../backend/spring/src/main/java/com/feng/dsagent/classroom/service/ClassroomService.java) vs [`StudyroomService`](../../backend/spring/src/main/java/com/feng/dsagent/studyroom/service/StudyroomService.java) 及 `studyroom/*` 卫星服务 | 不同产品边界与状态机 |
| 三套 ConfigService 整类合并 | [`MailConfigService`](../../backend/spring/src/main/java/com/feng/dsagent/mail/service/MailConfigService.java)、[`ModelConfigService`](../../backend/spring/src/main/java/com/feng/dsagent/modelconfig/service/ModelConfigService.java)、[`SandboxConfigService`](../../backend/spring/src/main/java/com/feng/dsagent/compiler/service/SandboxConfigService.java) | 密钥域、校验与审计字段不同；可共享 VO/校验器（B2），不宜并成一个 Service |
| Knowledge 两套 Loader | [`KnowledgeCorpusLoader`](../../backend/spring/src/main/java/com/feng/dsagent/knowledge/support/KnowledgeCorpusLoader.java)、[`CoursewareKnowledgeLoader`](../../backend/spring/src/main/java/com/feng/dsagent/knowledge/support/CoursewareKnowledgeLoader.java) | 语料源与导入契约不同 |
| 进度 Admin vs Learning | Admin 用户学习聚合 vs `learning` 进度写读 | 受众与权限模型不同 |

---

## 验证

本轮触及的辅助采用与测试编译修复后：

```text
cd backend/spring
./mvnw.cmd -q test-compile
./mvnw.cmd -q "-Dtest=BackendArchitectureTest" test
```

以及（仓库根）：

```text
node scripts/verify-studyroom.mjs --scope architecture
```

`test-compile` 与 architecture 范围以当次命令输出为准。未宣称 e2e / `verify-studyroom --scope all`。
