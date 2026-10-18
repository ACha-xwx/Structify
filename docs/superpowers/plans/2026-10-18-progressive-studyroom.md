# Progressive Studyroom Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task in the current conversation. Steps use checkbox (`- [ ]`) syntax for tracking. The user has selected this conversation; do not create another chat or dispatch subagents without authorization.

**Goal:** 将学习教室改为单层知识点路线，首片段就绪后自动进入，后续有界准备、独立发布和局部恢复。

**Architecture:** 保留大纲审阅、数据库队列、租约、配额与版本隔离。新增冻结片段计划和片段任务，知识点状态按片段聚合；程序管理资源绑定，模型生成讲解，高风险内容做教学审查。旧 run 按版本走兼容路径，新 run 使用新版链，不能混用发布器。

**Tech Stack:** Java 21、Spring Boot、JDBC、Flyway、H2/MySQL、Vue 3、TypeScript、Vitest、JUnit/MockMvc。

**Spec:** `G:/Code/Structify/docs/superpowers/specs/2026-10-18-progressive-studyroom-design.md`。

**Status:** 待执行；本文中的测试和命令是实施要求，不能作为已通过的验证结果。

---

## 0. 执行约束与顺序

仓库根目录为 `G:/Code/Structify`。本文文件名均为相对于此根目录的精确路径；B 表示 `backend/spring/src/main/java/com/feng/dsagent/studyroom`，T 表示 `backend/spring/src/test/java/com/feng/dsagent/studyroom`，F 表示 `frontend/src/user`。

所有命令从各任务标注的工作目录执行。PowerShell 调用 Maven 时，含点的 `-D` 参数必须加引号。
执行前查看 AGENTS.md、当前分支、索引和工作区。已有测试账号无限额度改动、课件、知识库、数据库和压缩包均不属于本计划，不能混入提交或覆盖。
需要隔离时先应用 using-git-worktrees 技能；当前预览数据库保持在原位置，不复制到版本库，不让两个进程同时打开同一个 H2 文件。

先按任务 1–5 建立可读模型和新版底座，6–9 接通生成链，10–12 接通交互，13 完成验证与交付。
每个任务先写失败测试，再最小实现并验证，提交只枚举本任务文件。文档提交在被忽略目录下应使用精确路径 `git add -f`，不修改全局忽略规则。
不自动推送半成品；完成后按用户已要求的目标 master 执行正常合并/快进，禁止强推，并报告提交和远端校验结果。

## 1. 文件职责图

- `B/fragment/FragmentPlan.java`：冻结的子目标、活动和必需资源声明。
- `B/fragment/ContentPlanCompiler.java`：从已确认大纲确定性编译片段，不调用模型。
- `B/fragment/JdbcFragmentRepository.java`：片段、修订、问题、进度和聚合查询。
- `B/fragment/FragmentReadService.java`：新 run 聚合与旧步骤只读投影。
- `B/fragment/PreparationScheduler.java`：准备窗口、焦点、优先级和任务去重。
- `B/fragment/FragmentResourceResolver.java`：片段证据与资源候选键绑定。
- `B/fragment/FragmentComposer.java`：单片段生成和有限修复。
- `B/fragment/FragmentValidator.java`：规则检查和风险审查决策。
- `B/fragment/FragmentPublicationService.java`：单片段幂等发布与旧修订保护。
- `B/fragment/FragmentController.java`：焦点、进度和显式局部重试接口。
- `F/components/studyroom/StudyroomKnowledgeRoute.vue`：唯一知识点导航。
- `F/components/studyroom/StudyroomFragmentReader.vue`：当前片段、继续、回看及局部恢复。
- `F/studyroom-fragment-runtime.ts`：片段 API。
- `F/use-studyroom-preparation.ts`：带页面和版本保护的首段等待。

沿用 OutlineService、GenerationCallService、JdbcGenerationRepository、StudyroomGenerationWorker、StudyroomSceneBinder 和已有资源展示组件；不另建并行的通用任务框架。

## 2. 固定协议

下列是各任务共享的目标类型，任务 1 负责落地，后续不得改用另一套字段名。

```java
public record FragmentPlan(
    String key, int order, String objective, String activity,
    java.util.List<String> coverageKeys, java.util.List<String> requiredKinds,
    boolean highRisk) {}
```

```ts
export type FragmentStatus = "PENDING" | "QUEUED" | "GENERATING" | "READY" | "FAILED";
export interface LearningFragment {
  id: string; key: string; order: number; status: FragmentStatus;
  stepId: string | null; contentRevision: number;
  error: { code: string; message: string; retryable: boolean } | null;
}
export interface KnowledgePointState {
  id: string; title: string; order: number;
  status: "PREPARING" | "PARTIAL" | "READY" | "NEEDS_ATTENTION";
  fragments: LearningFragment[]; readableFragmentCount: number;
  hasPendingContent: boolean; failedFragmentCount: number;
}
export interface ProgressiveRun {
  generationVersion: 1 | 2; stateVersion: number;
  firstReadableFragmentId: string | null;
  knowledgePoints: KnowledgePointState[];
  progress: { unitId: string; fragmentId: string; completed: boolean } | null;
}
```

现有 StudyroomRun 保留 roomId、outline、codeExecutionAvailable；以新增字段承载新版读模型。`stepId` 指向既有课堂 step，资源渲染与对话仍沿用该 step 的资源协议。
stateVersion 是持久化的 run 单调递增计数，在片段状态、发布、焦点和进度变更事务中加一；不能用前端时间戳模拟。

## Task 1：协议、迁移和版本隔离

**Files:** Create `B/fragment/FragmentPlan.java`, `backend/spring/src/main/resources/db/migration/V37__studyroom_fragments.sql`, `T/fragment/FragmentSchemaIntegrationTest.java`; Modify `frontend/src/shared/types/outline.ts`, `contracts/openapi-v1.yaml`。

- [ ] 在执行时核对最大 Flyway 版本；本次检查最大为 36，若已变化，将本文 V37 文件名顺延，绝不修改 V34–V36。
- [ ] 写失败测试：旧 run 默认 generation_version=1；同一 run/unit/fragment_key 重复插入失败；不同 run 可使用同一 fragment_key；内容修订、工作修订可并存。
- [ ] 在 `backend/spring` 运行 `./mvnw.cmd '-Dtest=FragmentSchemaIntegrationTest' test`，确认因缺失表/列失败。
- [ ] 创建迁移与 Java/TS 类型。迁移核心结构如下；外键与既有表相连，索引覆盖 run/unit/order 和 status。

```sql
ALTER TABLE studyroom_runs ADD COLUMN generation_version INT NOT NULL DEFAULT 1;
ALTER TABLE studyroom_runs ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE studyroom_generation_jobs ADD COLUMN fragment_id VARCHAR(36);
ALTER TABLE studyroom_generation_jobs ADD COLUMN priority INT NOT NULL DEFAULT 0;
CREATE TABLE studyroom_fragments (
 id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36) NOT NULL, unit_id VARCHAR(36) NOT NULL,
 fragment_key VARCHAR(160) NOT NULL, fragment_order INT NOT NULL, plan_json LONGTEXT NOT NULL,
 plan_hash VARCHAR(64) NOT NULL, resource_revision VARCHAR(64), status VARCHAR(24) NOT NULL,
 content_revision INT NOT NULL DEFAULT 0, pending_revision INT, step_id VARCHAR(36),
 content_json LONGTEXT, error_code VARCHAR(96), issues_json LONGTEXT,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(run_id,unit_id,fragment_key), UNIQUE(unit_id,fragment_order),
 FOREIGN KEY(run_id) REFERENCES studyroom_runs(id) ON DELETE CASCADE,
 FOREIGN KEY(unit_id) REFERENCES studyroom_units(id) ON DELETE CASCADE);
CREATE TABLE studyroom_fragment_versions (
 fragment_id VARCHAR(36) NOT NULL, revision INT NOT NULL, status VARCHAR(24) NOT NULL,
 content_json LONGTEXT, resource_revision VARCHAR(64), issues_json LONGTEXT,
 PRIMARY KEY(fragment_id,revision),
 FOREIGN KEY(fragment_id) REFERENCES studyroom_fragments(id) ON DELETE CASCADE);
CREATE TABLE studyroom_learning_progress (
 user_id BIGINT NOT NULL, run_id VARCHAR(36) NOT NULL, unit_id VARCHAR(36) NOT NULL,
 fragment_id VARCHAR(36) NOT NULL, completed BOOLEAN NOT NULL DEFAULT FALSE,
 focus_sequence BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,run_id), FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 FOREIGN KEY(run_id) REFERENCES studyroom_runs(id) ON DELETE CASCADE);
CREATE INDEX idx_study_fragments_order ON studyroom_fragments(run_id,unit_id,fragment_order);
CREATE INDEX idx_study_jobs_priority ON studyroom_generation_jobs(status,priority,next_attempt_at);
```

- [ ] 测试断言示例：`assertThat(runVersion(existingRun)).isEqualTo(1);`。runVersion 使用 JdbcTemplate 查询 generation_version，不增加生产方法只供测试。
- [ ] 重跑测试；确认 Flyway 在空 H2 与升级既有 schema 均通过，再提交本任务文件：`feat: add versioned studyroom fragment storage`。

## Task 2：冻结计划编译，确认前可见

**Files:** Create `B/fragment/ContentPlanCompiler.java`, `T/fragment/ContentPlanCompilerTest.java`; Modify `B/draft/OutlineService.java`, `F/components/studyroom/StudyroomOutlineFlow.vue`, `frontend/src/shared/types/outline.ts`。

- [ ] 写失败测试：相同大纲两次编译键相同；顺序稳定；未批准 EXPERIMENT 不出现；相同子目标不重复加入前置复习；改变深度不加入排除的 coverageKey。
- [ ] 运行 `./mvnw.cmd '-Dtest=ContentPlanCompilerTest' test` 确认失败。
- [ ] 实现 `List<FragmentPlan> compile(ReviewedOutline outline, ReviewedOutline.Unit unit)`。按已确认 activities 和 coverageKeys 顺序映射模板；概念、过程、实现、实验、检查分别作为主活动。
- [ ] 使用已知课程框架的操作键区分入栈/出栈、前序/中序/后序，不让模型在正文阶段重新拆分。缺少细分键时保留原目标作为一个片段，不自行推断新目标。
- [ ] key 使用 `unit.id + ":" + activity + ":" + stableCoverageKey` 的短 SHA-256；order 从 0 连续编号。每知识点上限 QUICK=3、STANDARD=6、DEEP=8，必要活动超限返回 `OUTLINE_FRAGMENT_LIMIT` 给审阅页，不静默截断。
- [ ] 在大纲 GET 返回 `fragmentPreview`、`planVersion=2` 和 `planHash`。fragmentPreview 为按 unitId 分组的 FragmentPlan 列表；planHash 对大纲修订、编译器版本和全部计划稳定序列化后取 SHA-256。OutlineDraft 同步增加这三个可选字段。
- [ ] 新版确认请求必须带 `{revision, planHash}`；服务器重新编译并比较 hash，不匹配返回 409 `OUTLINE_PLAN_CHANGED`，要求重新审阅。旧协议且开关未开启仍接受仅 revision；不信任客户端上传的计划。

```java
var first = compiler.compile(outline, unit);
assertThat(compiler.compile(outline, unit)).isEqualTo(first);
assertThat(first).allMatch(p -> !p.activity().equals("EXPERIMENT"));
assertThat(first.stream().map(FragmentPlan::key).distinct().count()).isEqualTo(first.size());
```

- [ ] 审阅页仅通过可展开的讲解安排展示 preview，不恢复第二套导航。模型生成知识点过大时要求修改大纲。
- [ ] Java 测试与 `npm run test -- src/user/components/studyroom/StudyroomOutlineFlow.spec.ts` 通过后提交：`feat: freeze reviewed fragment plans before generation`。

## Task 3：新旧课堂统一读模型

**Files:** Create `B/fragment/JdbcFragmentRepository.java`, `B/fragment/FragmentReadService.java`, `T/fragment/FragmentReadServiceTest.java`; Modify `B/generation/UnitPublicationService.java` 的 current/get 查询组装部分。

- [ ] 写失败测试：READY + FAILED 聚合为 PARTIAL 且 failedFragmentCount=1；READY 内容不能被后续失败隐藏；旧 run 的已发布 step 投影为 READY 片段且不排队任务。
- [ ] 运行 `./mvnw.cmd '-Dtest=FragmentReadServiceTest' test` 确认失败。
- [ ] 对 generationVersion=1 使用只读适配，合成键 `legacy:<stepId>`；旧失败/生成任务仍走旧实现，不把缺失计划补造成新版 run。
- [ ] 新 run 按 unit_order、fragment_order 排序，ready 数量来自已发布 step_id；READY=全部就绪，PARTIAL=已有可读但未全就绪，NEEDS_ATTENTION=无可读且失败，其他=PREPARING。
- [ ] Repository 所有读写先校验 user/run 归属；进度 fragment 必须属于该 run，不能仅验证 ID 存在。

```java
assertThat(view.knowledgePoints().getFirst().readableFragmentCount()).isEqualTo(1);
assertThat(view.knowledgePoints().getFirst().failedFragmentCount()).isEqualTo(1);
assertThat(view.knowledgePoints().getFirst().status()).isEqualTo("PARTIAL");
```

- [ ] 执行测试并确认读旧课堂不产生模型调用，提交：`feat: expose progressive and legacy studyroom read models`。

## Task 4：确认大纲时创建新版片段

**Files:** Modify `B/draft/OutlineService.java`; Create `T/fragment/ProgressiveOutlineConfirmationTest.java`。

- [ ] 写失败测试：确认创建 generation_version=2、冻结片段、current_run_id；重复 Idempotency-Key 返回同一个 run；旧修订不能确认；首段之外不批量创建模型调用。
- [ ] 运行 `./mvnw.cmd '-Dtest=ProgressiveOutlineConfirmationTest' test` 确认失败。
- [ ] 在现有确认事务内插入冻结片段，移除新版分支中的 UNIT 任务与仅按 unit content_hash 复用逻辑；旧 run 路径保留。修改 OutlineController.Confirm 增加可空 planHash 字段，按 generationVersion 分支执行 Task 2 的强制校验；OpenAPI 和 outline-runtime.confirm 同步传递。
- [ ] 新 run 初始 state_version=1。现有 sourceRoom/sourceRun 锁和取消旧排队任务逻辑继续生效，确认结果仍返回 roomId/runId。

```java
assertThat(confirm(key).get("runId")).isEqualTo(confirm(key).get("runId"));
assertThat(jdbc.queryForObject(
    "SELECT generation_version FROM studyroom_runs WHERE id=?", Integer.class, runId)).isEqualTo(2);
```

- [ ] 暂不启用新版 feature flag；Task 8 完成前，新链仅通过测试开启，防止用户得到无人处理的 FRAGMENT 任务。配置名 `app.studyroom-generation.progressive-enabled`，默认 false，最终 Task 13 才开启 dev。
- [ ] 测试通过后提交：`feat: create versioned fragment runs on outline confirmation`。

## Task 5：有限准备窗口、焦点与任务公平性

**Files:** Create `B/fragment/PreparationScheduler.java`, `T/fragment/PreparationSchedulerTest.java`; Modify `B/generation/JdbcGenerationRepository.java`。

- [ ] 写失败测试：初始只排当前知识点和下一知识点首段；跳转目标优先；READY 不排；FAILED 仅显式重试排；同片段并发需求只创建一个有效任务；两个用户请求均能被领取。
- [ ] 运行 `./mvnw.cmd '-Dtest=PreparationSchedulerTest' test` 确认失败。
- [ ] 实现 `requestFocus(long user, String run, String unit, long sequence)` 和 `reconcile(long user, String run)`。在锁 user/run 后计算窗口；只有当前 run 接受写入。GET run.progress 同时返回已持久化的 focusSequence；客户端从该值递增，刷新不能从零重置。两个标签页提交相同 sequence 时以首次提交为准，另一方刷新状态，不覆盖已接受焦点。
- [ ] priority 取等待首段=300、当前后续=200、相邻首段=100；同级按入队时间。claim 先筛每用户最高优先候选再全局排序，保留一用户一个 RUNNING 的限制。
- [ ] job_key=`fragment:<fragmentId>:<pendingRevision>`；初次 revision=1，显式内容重生成才加 revision；普通失败重试增加 attempt，不另造业务片段。
- [ ] 旧窗口 QUEUED 任务记 CANCELLED/FOCUS_CHANGED，片段退回 PENDING，返回窗口时原任务可受锁保护地重新排队；不得使用 enqueue 的“已有就直接返回”逻辑把 CANCELLED 永久卡住。
- [ ] 已 RUNNING 的调用不取消，不抢占配额；完成后重新 reconcile。严格遵守配额和并发限制。

```java
assertThat(queuedUnits()).containsOnly(currentUnit, nextUnit);
assertThat(queuedFragments(nextUnit)).hasSize(1);
assertThat(claimNext().get("priority")).isEqualTo(300);
```

- [ ] 测试通过后提交：`feat: schedule bounded studyroom preparation by learning focus`。

## Task 6：当前子目标的依据与资源解析

**Files:** Create `B/fragment/FragmentResourceResolver.java`, `T/fragment/FragmentResourceResolverTest.java`; Modify `backend/spring/src/main/java/com/feng/dsagent/knowledge/KnowledgeSearchService.java`, `B/StudyroomResourceSelector.java`。

- [ ] 写失败测试：同来源的两个不同相关页可进入片段检索；相同片段内容去重；超范围来源被过滤；CONCEPT 不强制代码；REQUIRED 动画缺失返回 `FRAGMENT_REQUIRED_RESOURCE_MISSING`。
- [ ] 运行 `./mvnw.cmd '-Dtest=FragmentResourceResolverTest' test` 确认失败。
- [ ] 给 KnowledgeSearchService 新增 `searchForFragment(query, chapter, limit, audience)`，复用权限、关键词评分，但按 chunk ID/内容 hash 去重而非 source；保持旧 search 行为不变，避免影响 Chat。
- [ ] ResourceResolver 返回类型 `ResolvedResources(List<Evidence> evidence, Map<String,Candidate> candidates, String revision)`；候选短键 e1/r1 只在本次解析内有效，revision 对依据/资源版本、参数边界和权限范围做 hash。
- [ ] 首期每活动最多 PPT=4 组、ANIMATION=2、C_CODE=2 个候选；资源已绑定多页时按页组计算候选。主活动匹配和真实可用性先于相关性打分。
- [ ] 模型输出只引用短键；解析时验证键、真实 ID、页序、代码范围及动画允许参数。真实路径永不接受模型直接输入。

```java
assertThat(resolved.candidates().keySet()).contains("r1");
assertThatThrownBy(() -> resolver.resolveSelected("not-a-key", resolved))
    .isInstanceOf(IllegalArgumentException.class);
```

- [ ] 补充同名 `resolveSelected(String key, ResolvedResources resolved)` 方法，返回映射中的 Candidate 或抛错，禁止猜测替代。运行既有 KnowledgeSearchService 测试并提交：`feat: bind fragment evidence and resources deterministically`。

## Task 7：片段生成、分级检查与一次修复

**Files:** Create `B/fragment/FragmentComposer.java`, `B/fragment/FragmentValidator.java`, `T/fragment/FragmentComposerTest.java`, `T/fragment/FragmentValidatorTest.java`; Modify `B/generation/TeachingQualityGate.java`。

- [ ] 写失败测试：不存在候选键、截断 JSON、代码行越界和虚构运行结果阻断；纯表达建议不阻断；高风险审查不确定不发布；第二次修复调用不发生。
- [ ] 运行 `./mvnw.cmd '-Dtest=FragmentComposerTest,FragmentValidatorTest' test` 确认失败。
- [ ] Composer 使用 GenerationCallService 完成 fragment-generate、fragment-review、fragment-repair、fragment-review-repair 四个明确 phase；每 job 是一个片段，因此 phase 不依赖随机下标。
- [ ] 模型结果协议固定为 objective、explanation、evidenceKeys、scenes；程序将短键映射成既有 StudyroomModelOutput 的单 step，并复用 SceneBinder/ResourceCatalog 硬校验。
- [ ] Validator 的 `ValidationIssue(severity,code,message,evidenceKeys,location)` 仅允许 BLOCKER/ADVICE。代码、运行、算法状态变化、复杂度与边界结论必须审查；概念由规则检查并按稳定 hash 抽样 10%。未检查不能标成已审查。
- [ ] 复用 TeachingQualityGate 的模型调用方式，新增结构化结果读取而不是直接把所有 issues 当异常；旧 review 行为保留给旧链。审查格式错误按不确定阻断并记录 `REVIEW_UNCERTAIN`。
- [ ] 如果生成正文出现高风险声明，即使计划是 CONCEPT 也升级审查；无法识别风险的关键结论在回归评估中检查，不声称规则能保证教学正确。

```java
assertThat(validate(adviceOnly).blocking()).isFalse();
assertThat(validate(unknownCodeReference).blocking()).isTrue();
verify(model, times(1)).complete(argThat(r -> phaseOf(r).equals("fragment-repair")));
```

其中 phaseOf 在测试 fake 的调用记录中由 GenerationCallService 的 phase 参数记录，不从模型 prompt 猜测。
- [ ] 硬错误或必要审查失败只修复一次，失败持久化具体问题；不返回整单元泛化错误。成功时返回单 step JSON 和资源 revision。
- [ ] 测试通过后提交：`feat: generate and validate isolated teaching fragments`。

## Task 8：单片段原子发布与 Worker 接入

**Files:** Create `B/fragment/FragmentPublicationService.java`, `T/fragment/FragmentPublicationServiceTest.java`; Modify `B/generation/StudyroomGenerationWorker.java`, `B/generation/JdbcGenerationRepository.java`, `B/generation/GenerationCallService.java`。

- [ ] 写失败测试：第一个片段成功即可在课堂读取；第二个失败不隐藏第一个；重复发布仅一个 step；旧 run、过期租约、错误 pending_revision 全部拒绝发布。
- [ ] 运行 `./mvnw.cmd '-Dtest=FragmentPublicationServiceTest' test` 确认失败。
- [ ] Worker 新增 FRAGMENT 分支，原 UNIT 分支仅用于 version=1。调用 Composer → PublicationService → scheduler.reconcile，任务领取和心跳继续使用现有逻辑。
- [ ] 发布事务锁 user、room、fragment，验证 current_run_id、lease_token、plan_hash 和 pending_revision；第一次插入 step，后续修订更新同一 step，写版本表并原子切换 content_revision。
- [ ] step_key_in_unit 采用 fragment.key，step_order=`runRevision*10000 + unitOrder*100 + fragmentOrder`；Task 2 上限确保不跨知识点占位。已发布内容仅在新修订通过后替换，失败期间仍可读。
- [ ] 在同一事务中提交 READY、state_version+1 和任务完成；不另在 Worker finally 将失败任务标为成功。
- [ ] 错误按 JSON_INVALID、RESOURCE_BINDING_INVALID、TEACHING_BLOCKED、REVIEW_UNCERTAIN、MODEL_CALL_UNCERTAIN、QUOTA_EXHAUSTED、LEASE_LOST、RUN_SUPERSEDED 分类持久化。
- [ ] 已发送但未知的调用不可自动再发；明确并发冲突以 next_attempt_at 退避，额度耗尽暂停，不能进入紧密重试循环。

```java
publish(validLease, fragmentOne, content);
fail(fragmentTwo, "TEACHING_BLOCKED");
assertThat(readRun().knowledgePoints().getFirst().readableFragmentCount()).isEqualTo(1);
assertThat(stepCount(fragmentOne)).isEqualTo(1);
```

- [ ] 运行任务 4–8 集成测试，通过后提交：`feat: publish ready fragments independently with lease fencing`。

## Task 9：焦点、进度、重试与跨版本 API

**Files:** Create `B/fragment/FragmentController.java`, `T/fragment/FragmentApiIntegrationTest.java`, `F/studyroom-fragment-runtime.ts`; Modify `B/draft/OutlineController.java`, `contracts/openapi-v1.yaml`。

- [ ] 写 MockMvc 失败测试覆盖跨用户、旧 run、错误 unit/fragment 关系、重复 Idempotency-Key、旧 sequence，以及新旧 GET run 输出。
- [ ] 运行 `./mvnw.cmd '-Dtest=FragmentApiIntegrationTest' test` 确认失败。
- [ ] 实现如下接口；全部写接口要求认证和 Idempotency-Key，重复键不同载荷返回 409。

```text
POST /api/v1/studyroom-runs/{run}/focus
  {unitId, sequence} -> {stateVersion}; sequence 小于已记录值时不改变焦点
POST /api/v1/studyroom-runs/{run}/progress
  {unitId, fragmentId, completed} -> {stateVersion}
POST /api/v1/studyroom-runs/{run}/fragments/{fragment}/retry
  {} -> 202 {jobId, stateVersion}; READY 无待处理修订时返回已有结果而非重生成
GET /api/v1/studyroom-runs/{run}
  原字段 + ProgressiveRun 字段
```

- [ ] 老 unit prepare 在新版只提出准备需求；FAILED 必须用片段 retry，旧 run 保持原行为。completed 只能由明确操作提交，不在生成回调中置 true。
- [ ] runtime 精确使用上述路径和类型；接口失败保留 status/code，不把所有错误变成同一字符串。
- [ ] Java 测试通过后在根目录运行 `node scripts/verify-api-contract-fixtures.js`；如果脚本帮助或依赖要求提供参数，按脚本实际 usage 使用本地测试服务和 fixture，不调用生产。
- [ ] 提交：`feat: add authenticated progressive learning operations`。

## Task 10：单层知识点路线与连续片段阅读

**Files:** Create `F/components/studyroom/StudyroomKnowledgeRoute.vue`, `F/components/studyroom/StudyroomFragmentReader.vue` 及对应 `.spec.ts`; Modify `F/views/StudyroomView.vue`, `F/views/StudyroomView.spec.ts`。

- [ ] 先写失败测试：只存在一个学习路线 nav；选择知识点不调用 retry；READY 片段仍显示而后续失败仅局部提示；继续到下一已就绪片段，回看恢复资源状态。
- [ ] 在 `frontend` 运行 `npm run test -- src/user/views/StudyroomView.spec.ts` 确认新断言失败。
- [ ] 路线组件只 emit select(unitId)；页面设置 selection 并通知 focus，点击 FAILED 不隐式修复。进度和准备状态用不同文案显示。
- [ ] Reader 根据 fragment.stepId 复用既有文字、PPT、动画和代码组件；保留多页导航和缩放，不把每页拆成片段。
- [ ] “继续学习”到已就绪后续片段；下一段未就绪时展示局部等待，当前内容仍保留；跨知识点继续时同步路线选择。最后片段显示“完成本知识点”，只在点击后持久化 completed。
- [ ] 旧 run 也使用同一知识点 UI，但缺失 unit 的 legacy room 使用一个兼容知识点，不人为新生成大纲。

```ts
expect(wrapper.findAll('nav[aria-label="知识点路线"]')).toHaveLength(1);
expect(wrapper.find('[aria-label="课堂学习步骤"]').exists()).toBe(false);
await wrapper.get('[data-unit-id="failed-unit"]').trigger('click');
expect(api.retryFragment).not.toHaveBeenCalled();
```

- [ ] 对新组件做键盘焦点、aria-current、窄屏检查；虚拟键盘或路由变化不清空 C 编辑器草稿。
- [ ] 新旧 StudyroomView 测试及新组件测试通过后提交：`feat: simplify classroom navigation to knowledge points`。

## Task 11：首段等待与自动进入保护

**Files:** Create `F/use-studyroom-preparation.ts`, `F/use-studyroom-preparation.spec.ts`; Modify `F/components/studyroom/StudyroomOutlineFlow.vue`, `F/views/StudyroomOutlineView.vue`, `F/components/studyroom/StudyroomOutlineFlow.spec.ts`。

- [ ] 用 fake timers 写失败测试：confirm 成功后首段未好不跳转；首段 READY 仅跳一次；离开、取消、切换 draft/revision 后迟到响应不跳转；手动进入旧课堂不等待新任务。
- [ ] 在 `frontend` 运行 `npm run test -- src/user/use-studyroom-preparation.spec.ts` 确认失败。
- [ ] useStudyroomPreparation 持有 generation token、当前 draft/revision/run 和 AbortController；cleanup 同时 abort、清 timer、失效 token。仅满足全部身份条件且 firstReadableFragmentId 非空时 emit ready(roomId)。
- [ ] 前台每 2 秒查询，后台每 10 秒；每次成功请求完成后才排下一次，不重叠轮询；stateVersion 较旧的响应丢弃；失败后显示恢复，不无限立即请求。
- [ ] Flow 的 confirmed 事件改为 `{roomId, runId, revision}`，父页面先等待，再 router.replace；确认提交携带当前预览的 planHash。返回已有课堂使用独立 enter-room 事件，不复用 confirm。

```ts
const request = deferredRun();
startPreparation(request.promise);
scope.stop();
request.resolve(readyRun);
await flushPromises();
expect(router.replace).not.toHaveBeenCalled();
```

deferredRun 在本测试文件实现为暴露 Promise resolve 的小工具；startPreparation 包装 effectScope.run 调用 useStudyroomPreparation，不引入真实定时等待。
- [ ] 单元测试与原 OutlineFlow 测试通过后提交：`feat: enter classroom automatically when the first fragment is ready`。

## Task 12：结构化观测、风险回归和兼容开关

**Files:** Create `T/fragment/ProgressiveStudyroomRecoveryTest.java`, `T/fragment/ProgressiveStudyroomEvaluationTest.java`, `docs/project/studyroom-progressive-acceptance-2026-10-18.md`; Modify `B/generation/GenerationCallService.java`, `backend/spring/README.md`。

- [ ] 写失败测试覆盖：重复 SENT 不重新计费；崩溃后租约到期恢复；更换 run 后旧 Worker 不能发布；READY 内容修订失败后旧版可读；焦点窗口移动不删除内容。
- [ ] 运行 `./mvnw.cmd '-Dtest=ProgressiveStudyroomRecoveryTest,ProgressiveStudyroomEvaluationTest' test` 确认失败。
- [ ] 记录关联的 run/unit/fragment/job/attempt、phase、耗时、usage source、错误分类与结构化 issues；只输出必要摘要，不记录密钥和完整用户上下文。
- [ ] 用可控 fake 模型实现固定故障注入，检查普通概念、代码、过程和实验的审查路径以及修复上限。真实模型评估单独手工启用，不塞入普通单元测试。
- [ ] 固定栈总览、单独入栈、顺序栈链栈比较、树遍历、C 阅读、无沙箱实验、无证据、资源损坏八类样本；每类三档，验证范围、必要活动、引用与错误恢复。
- [ ] 相同模型/资源版本下，对有真实资源的六类输入每档各跑三次旧/新版对照；记录模型配置、资源 revision、调用/Token、首段耗时、审查问题、人工核查结果和所有失败。无资料/损坏资源为阻断用例，不用虚构成功。
- [ ] 接受条件：确定性恢复用例全过；有效样本总体首段耗时中位数和每片段调用量低于旧链，人工核查未增加实质性错误。报告样本量与离群值，任何不满足项阻止开关默认开启。
- [ ] 提交：`test: verify progressive generation recovery and teaching quality`。

## Task 13：端到端验收和最终交付

**Files:** Modify `backend/spring/src/main/resources/application-dev.yml`, `docs/project/studyroom-progressive-acceptance-2026-10-18.md`。

- [ ] 执行前记录当前工作区变更和测试账号配额改动；仅在 Task 12 达标后启用 dev 的 `app.studyroom-generation.progressive-enabled=true`，生产默认 false，现有 run 永远按 generationVersion 分流。
- [ ] 在 `backend/spring` 执行 `./mvnw.cmd test`，期望退出码 0，无失败/错误；记录实际测试总数，不能引用旧轮的 611。
- [ ] 在 `frontend` 分别执行 `npm run test`、`npm run build`，期望均退出码 0；记录构建警告并判断是否新增。
- [ ] 根目录执行 `git diff --check`；对既有未相关变更不擅自修改。检查 OpenAPI 和实际响应包含相同字段与状态。
- [ ] 使用启动脚本重启本地服务，确认 backend health=UP，frontend HTTP=200；数据库文件路径保持原配置，先备份再做必要迁移验证。
- [ ] 用浏览器验证新大纲确认→等待首段→自动进入→边读边准备→局部失败→重试恢复→刷新续读；另验离开页面无跳转、旧课堂可读、窄屏路线、代码草稿保留。
- [ ] 保存不含凭据的界面证据到 private/run-logs，验收文档写明实测结果、未配置能力和基线对比，不能把 fake 模型测试表述为真实模型稳定性验证。
- [ ] 关闭 progressive-enabled 后验证只影响后续新 run 的选择，已经创建的 version=2 run 仍由新版 Worker 完成，避免开关把任务永久卡住。回滚不得只回退应用二进制使新版任务无人处理；如需二进制回滚先停止新建 run、保留新版 Worker 排空或明确暂停。
- [ ] 提交本计划范围的最终改动；核查每个提交文件清单后，将完整、通过验证的工作正常推送到用户指定的 master，并用远端 ref 核对 SHA。不创建用户未要求的 PR，不强推。

## 覆盖自审

- 单层导航与浏览/生成状态分离：Task 3、10。
- 审阅前可见的冻结计划与三档边界：Task 2、4。
- 有界预准备、公平性、跳转优先级：Task 5。
- 程序资源绑定和片段检索：Task 6。
- 风险审查、局部修复与错误分类：Task 7、8、12。
- 独立发布、修订保留、幂等与租约防旧写入：Task 1、8、9、12。
- 首段自动进入及离开保护：Task 11。
- 旧数据、权限、配额、OpenAPI、回滚：Task 1、3、9、12、13。

计划不要求重建素材库或接入向量数据库。先验证上述改动的收益，再根据实测检索问题决定下一轮算法改进。
