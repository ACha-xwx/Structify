# Reviewed Outline and Learning Depth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Execution preference is inline in the current conversation; do not dispatch subagents.

**Goal:** 实现三档深度、学生审阅和修改大纲、明确确认后按单元生成，并用知识覆盖及材料承诺约束生成质量。

**Architecture:** Spring/JDBC 保存不可变的大纲版本、确认快照、课堂运行版本及单元任务；数据库任务队列通过租约和发布校验支持恢复。Vue 在现有课堂展示区之前增加大纲协商流程，确认后复用现有 schemaVersion=2 场景与资源渲染。提示词负责教学判断，确定性代码负责版本、权限、材料约束和状态转换。

**Tech Stack:** Java 21、Spring Boot、JdbcTemplate、Flyway、MySQL/H2、现有 AiQuotaExecution、Vue 3、TypeScript、Vitest。不新增 Redis、消息中间件或模型供应商依赖。

---

设计依据：`docs/superpowers/specs/2026-10-18-studyroom-reviewed-outline-design.md`。所有路径相对 `G:/Code/Structify`，命令均明确工作目录。

## 技术路径与关键决定

请求先进入大纲草稿服务，完成证据检索、知识覆盖规划和资源可行性检查；学生提交修改生成新版本；确认事务冻结最新版本并创建课堂运行和首单元任务。Worker 领取任务，按步骤选择/讲解材料，经过教学和结构校验后发布；Vue 轮询拥有权限的任务状态，首单元就绪即可学习，并至多预取下一单元。

现有 `admin/BackgroundTaskService.java` 主要执行 STALE_TASK_RECOVERY，超时恢复是标记失败，不是带业务载荷的通用可靠队列；不能仅套用它的虚拟线程 dispatch 就宣称生成可恢复。本期创建 Studyroom 专用任务表及 Worker，复用现有配额、Clock、调度、配置和管理台账的状态表达方式，不重构其他任务。管理端新增任务操作不在本期范围。

现有 `StudyroomGraph.java` 不继续扩成全能服务。新流程拆为 draft、curriculum、generation 三个包；保留旧课堂读取和对话入口。将资源编排可复用部分抽到独立服务，不在新 Worker 中递归调用原来的整堂课 create。

保留当前工作区为实施基线：现有 Studyroom 很多文件尚未跟踪，默认分支的新 worktree 不包含这些功能。执行前保存 tracked diff、所有将修改的未跟踪文件原文及清单到 private/run-logs；不覆盖或整文件提交无关改动。下列每任务完成后仅提交可安全拆分的补丁，无法安全拆分则在验收记录中说明。

## 一、数据和状态契约

新增概念与已有字段严格分离：`outlineRevision` 是大纲版本，`runRevision` 是课堂运行版本，现有 `room.version` 仍用于进度并发，`orchestration.schemaVersion` 仍为 2。

```ts
type LearningDepth = 'QUICK' | 'STANDARD' | 'DEEP';
type DraftStatus = 'GENERATING' | 'REVIEWABLE' | 'REVISING' | 'FAILED';
type MaterialRequirement = 'REQUIRED' | 'RECOMMENDED' | 'OPTIONAL';
type UnitStatus = 'NOT_REQUESTED' | 'QUEUED' | 'GENERATING' | 'READY' | 'FAILED';
interface OutlineUnit {
  id: string;
  title: string;
  objective: string;
  coverageKeys: string[];
  expansion: 'INTRODUCE' | 'EXPLAIN' | 'PRACTICE';
  activities: Array<'CONCEPT' | 'PROCESS' | 'IMPLEMENTATION' | 'EXPERIMENT' | 'CHECK'>;
  materials: Array<{
    kind: 'PPT' | 'ANIMATION' | 'C_CODE';
    requirement: MaterialRequirement;
    purpose: string;
    candidateIds: string[];
    availability: 'VERIFIED' | 'MISSING' | 'UNRESOLVED';
  }>;
}
interface ReviewedOutline {
  schemaVersion: 1;
  depth: LearningDepth;
  title: string;
  objectives: string[];
  prerequisites: string[];
  units: OutlineUnit[];
  excludedCoverageKeys: string[];
  exclusions: string[];
  evidenceIds: string[];
  curriculumVersion: string | null;
  coverageAssessment: 'REVIEWED_FRAMEWORK' | 'EVIDENCE_ONLY';
}
interface DraftView {
  id: string;
  status: DraftStatus;
  latestRevision: number;
  pendingJobId: string | null;
  outline: ReviewedOutline | null;
  changes: Array<{ unitId: string; kind: 'ADDED' | 'REMOVED' | 'CHANGED' | 'MOVED'; summary: string }>;
  issues: Array<{ code: string; unitId: string | null; severity: 'BLOCKER' | 'WARNING'; message: string }>;
  confirmedRunId: string | null;
}
```

初始防滥用上限：大纲最多 24 单元、单元最多 8 个教学步骤、正文单步沿用现有资源/JSON 上限。它们是工程边界，不是三档固定步数；超出时要求缩小本次范围，不静默截断并标为完整。每单元独立生成，不将整个深入档正文塞入一次 6000-token 输出。

## 二、迁移路径

新增 `backend/spring/src/main/resources/db/migration/V28__studyroom_reviewed_outlines.sql`。执行前确认 V28 尚未占用，如占用使用下一个版本，不重命名或编辑已执行迁移。

创建以下表，每张表的 user_id/room_id 引用现有 users/study_rooms；所有索引和列在任务 2 中同时落地：

- `study_outline_drafts`：id、user_id、question、chapter_id、depth、status、latest_revision、pending_job_id、confirmed_run_id、created_at、updated_at。保留最后有效 revision，失败修改不推进 latest_revision。
- `study_outline_revisions`：id、draft_id、revision、base_revision、outline_json、issues_json、change_summary_json、feedback、curriculum_version、created_at；UNIQUE(draft_id, revision)。已保存记录不可原位修改。
- `study_outline_messages`：id、draft_id、message_order、role、content、revision、created_at；UNIQUE(draft_id, message_order)。修改对话与课堂讲解消息分开。
- `studyroom_runs`：id、room_id、user_id、draft_id、outline_revision、run_revision、status、approved_outline_json、created_at；UNIQUE(room_id, run_revision)，同一确认幂等键只关联一个 run。
- `studyroom_units`：id、run_id、unit_key、unit_order、status、unit_spec_json、content_hash、source_unit_id、error_code、created_at、updated_at；UNIQUE(run_id, unit_key)。复用以授权仍有效的 source_unit_id 关联，不复制资源本体。
- `studyroom_generation_jobs`：id、user_id、draft_id、run_id、unit_id、job_type、job_key、status、payload_json、attempt、lease_token、lease_until、heartbeat_at、next_attempt_at、failure_code、failure_message、created_at、updated_at；UNIQUE(job_key)，索引(status,next_attempt_at,lease_until)。job_type 为 PLAN/REVISE/UNIT/STEP_RETRY。
- `studyroom_generation_calls`：id、job_id、attempt、phase、status、quota_request_id、response_json、failure_code、created_at、updated_at；UNIQUE(job_id,attempt,phase)，UNIQUE(quota_request_id)。正文不写入普通日志，response_json 限制为现有模型输出上限。
- `studyroom_request_keys`：user_id、operation、idempotency_key、payload_hash、result_id、created_at；UNIQUE(user_id,operation,idempotency_key)。同键不同载荷返回 409。

`study_room_steps` 增加可空 unit_id、run_id、step_key_in_unit 和 generation_status；历史记录保持 NULL。新单元用自身序号排序，历史扁平步骤继续按 step_order。写入 room 进度时校验所选步骤属于当前 run，旧课堂仍按原所有权规则工作。

## 三、接口路径与前后端时序

新写接口均要求登录、所有权及 `Idempotency-Key`；业务幂等键与普通请求追踪 ID 分开，客户端重发使用原键，新的修改或显式重试生成新键。新 GET 不触发模型生成。

```text
POST /api/v1/study-outlines
  {question, depth:"STANDARD", chapterId?, sourceRoomId?}
  -> 202 {draftId, jobId}; sourceRoomId 仅用于已拥有课堂的重新规划
GET /api/v1/study-outlines/{draftId}
  -> DraftView（含修改消息和最后有效大纲；每次轮询不返回全套正文）
POST /api/v1/study-outlines/{draftId}/revisions
  {baseRevision, feedback, depth?} -> 202 {draftId, jobId}
GET /api/v1/study-outlines/{draftId}/revisions/{revision}
  -> 不可变的大纲版本及差异
POST /api/v1/study-outlines/{draftId}/restore
  {baseRevision, targetRevision} -> 201 新草稿 revision；必要材料重新校验
POST /api/v1/study-outlines/{draftId}/confirm
  {outlineRevision} -> 202 {roomId, runId, firstUnitId, jobId}
GET /api/v1/study-rooms/{roomId}/units?runId={runId}
  -> 当前有效 run 的单元状态列表及概要
GET /api/v1/study-rooms/{roomId}/units/{unitId}
  -> READY 时返回场景与正文；其他状态返回状态、失败原因和允许的操作
POST /api/v1/study-rooms/{roomId}/units/{unitId}/prepare
  {} -> 202 已有或新建的任务；READY 不再调用模型
POST /api/v1/study-rooms/{roomId}/units/{unitId}/retry
  {failedStepKey?} -> 202 新一次显式重试；只重试失败部分
```

过期 revision、修改中确认、旧 run 写入或同幂等键不同载荷返回 409；必要材料缺失返回 422 和 issues；未登录 401；不属于用户的对象统一 404。配额不足保留有效大纲/正文并返回可理解的任务失败原因。

已有 `GET /study-rooms/{id}`、进度和旧课堂追问继续可用。旧 `POST /study-rooms` 不再直接创建新课堂，返回 409 `STUDYROOM_OUTLINE_REQUIRED` 及新流程入口。已升级前端全部使用新创建流程。已有旧课堂可继续旧补充；新 run 的改变范围请求转为大纲修改建议，不能从 messages 接口绕过审阅追加未确认单元。

## 四、任务租约、版本与模型调用边界

Worker 每秒扫描可执行任务，默认最多 2 个任务并发；每用户最多 1 个正文任务执行。15 秒心跳、120 秒租约，配置须大于既有模型最大单次超时并加余量。可通过 Spring 配置收紧并发和预取，不使用无界 cached thread pool。测试通过注入 Clock 推进时间。

领取采用带条件的原子 UPDATE，不依赖 MySQL 专属语法，兼容 H2：

```sql
UPDATE studyroom_generation_jobs
SET status='RUNNING', lease_token=?, lease_until=?, heartbeat_at=?, updated_at=?
WHERE id=? AND status='QUEUED' AND next_attempt_at<=?;
```

仅影响 1 行的 Worker 可执行；所有续租/提交都带 id、lease_token、status 条件。正文发布事务先锁定 room/run，确认 run 仍为 current，再原子写步骤、单元 READY 和任务成功。重新确认事务使用同样锁顺序切换 current run 并取消旧待执行任务；旧 Worker 即使晚返回也不能发布。过期任务在没有外部调用不确定性的情况下可重新排队。

模型调用全部经过现有 AiQuotaExecution，操作名使用 `study_plan`、`study_revise`、`study_unit`、`study_step_repair`，不超过 AiQuotaRequestId 的 32 字符限制；quota_request_id 由 job/attempt/phase 的稳定服务端值产生，不能只依赖浏览器随机请求 ID。

重要限制：上游模型未提供可核验幂等能力时，不能承诺外部调用严格 exactly-once。发送前持久化 SENT，拿到结果后立即存 response_json；恢复时已有结果直接复用，不再调用。若进程在外部发送后、结果落库前中断，则标为 FAILED / `MODEL_CALL_UNCERTAIN`，不自动重发；界面说明结果未保存、前次调用可能已消耗额度，用户可主动重试。不能拿旧 quota key 重调模型，也不能为恢复而无限生成新计费 key。

任务键示例：`draft:{id}:base:{revision}:revise:{requestKey}`、`run:{id}:unit:{unitKey}:prepare`、`run:{id}:unit:{unitKey}:step:{stepKey}:retry:{requestKey}`。浏览器重复点击同一次请求复用 key；主动新重试是新的调用尝试。

## 五、文件职责与实施任务

下文新增 Java 文件均在 `backend/spring/src/main/java/com/feng/dsagent/studyroom/` 下对应子包；测试在 `backend/spring/src/test/java/com/feng/dsagent/studyroom/` 下同名子包。每任务按“失败测试 → 确认失败 → 最小实现 → 通过 → 安全提交”执行。

### Task 1：修复现有整堂课修复缩水

**修改：** `StudyroomGraph.java`、`StudyroomSceneBinder.java`、`StudyroomModelOutput.java`。**测试：** `StudyroomGraphTest.java`、`StudyroomSceneBinderTest.java`。

- [ ] 增加回归：一堂含多页课件/代码/动画的夹具只有一个步骤结构非法，修复后保留其余步骤和全部已确认目标；实现步骤修复后漏掉直接匹配代码必须不通过。
- [ ] 在 backend/spring 执行 `./mvnw.cmd "-Dtest=StudyroomGraphTest,StudyroomSceneBinderTest" test`，预期新增断言失败，保存具体错误。
- [ ] 删除修复提示中“整堂最多六步、每步一个资源”的旧限制，移除 `state.number(REPAIR_COUNT)<1` 对代码承诺检查的关闭逻辑。格式归一化不修改已验证知识范围；仅把失败 step 的原目标、候选、错误及内容交给修复，按 step key 合并回原结果。
- [ ] 有相关候选但模型选择仍未满足目标时保留失败原因，不能强塞不相关材料，也不能自动删除资源后算成功。重跑上述测试并提交能安全划分的修复。

### Task 2：大纲、版本与任务持久化

**新增：** `draft/OutlineRecords.java`、`draft/JdbcOutlineRepository.java`、`generation/GenerationRecords.java`、`generation/JdbcGenerationRepository.java`；迁移使用第二节指定路径。**测试：** `draft/OutlineRepositoryIntegrationTest.java`、`generation/GenerationRepositoryIntegrationTest.java`。

- [ ] 写测试验证最后有效 revision 不被失败修改覆盖、版本不可原位修改、他人不可读、幂等键同载荷复用/不同载荷拒绝、旧 classroom NULL 字段可读。
- [ ] 执行 `./mvnw.cmd "-Dtest=OutlineRepositoryIntegrationTest,GenerationRepositoryIntegrationTest" test`，预期缺少表/仓储行为导致失败。
- [ ] 创建第二节全部表、索引和可空关联列；JSON 字段用 LONGTEXT，时间使用现有 Timestamp/Instant 转换。仓储提供 createDraft、insertRevision、claimRevision、confirm、claimJob、renewLease、publishUnit、failJob、findOwned 操作，更新方法返回受影响行数并检查 CAS。
- [ ] H2 使用现有集成测试配置验证完整迁移和历史读取；增加外键与唯一键违反用例。重跑测试并提交迁移/仓储补丁。

### Task 3：大纲契约、深度策略与结构校验

**新增：** `draft/ReviewedOutline.java`、`draft/LearningDepth.java`、`draft/OutlineValidator.java`、`draft/OutlineDiff.java`；前端新增 `frontend/src/shared/types/studyroom-outline.ts`。**测试：** `draft/OutlineValidatorTest.java`、`draft/OutlineDiffTest.java`。

- [ ] 测试 QUICK/STANDARD/DEEP 是覆盖/展开差异，不是相同内容加字数；同一单元未变时保留 ID，移动不误判删除新增，明确排除的知识点不得被补回。
- [ ] 执行 `./mvnw.cmd "-Dtest=OutlineValidatorTest,OutlineDiffTest" test`，预期新契约/算法缺失。
- [ ] 实现第一节契约；ID 不重复，单元有明确 objective、coverageKeys、活动与材料用途。校验每个候选 ID/证据 ID 来自授权目录，拒绝模型生成 URL、代码、文件路径和未声明字段。
- [ ] `OutlineDiff` 按稳定 ID 比较目标、覆盖、展开程度、材料承诺和相对位置；正文复用哈希包含这些字段、框架版本及素材版本，不仅比较标题。测试通过后提交。

### Task 4：首批课程框架与资源可行性

**新增：** `curriculum/CurriculumCatalog.java`、`curriculum/CoverageChecker.java`、`draft/OutlineFeasibilityService.java`；数据为 `backend/spring/src/main/resources/studyroom-curriculum/v1/{stack,queue,binary-tree}.json`。**修改：** `StudyroomResourceCatalog.java`、`StudyroomResourceSelector.java`。**测试：** `curriculum/CoverageCheckerTest.java`、`draft/OutlineFeasibilityServiceTest.java`。

- [ ] 用固定目录验证：栈的系统档漏掉边界/核心操作会报告覆盖问题；学生排除表达式求值后不再报此项遗漏；不相关阶乘代码不能满足栈实现材料承诺；资源或沙箱不可用产生具体 issue。
- [ ] 执行 `./mvnw.cmd "-Dtest=CoverageCheckerTest,OutlineFeasibilityServiceTest" test`，预期新检查缺失。
- [ ] 框架节点至少有 key、title、prerequisites、depthRecommendations、activityKinds、materialRoles；栈覆盖设计文档所列范围，队列覆盖 FIFO/顺序与链式/循环队列/空满/操作边界/应用，二叉树覆盖结构术语/表示/遍历/递归与边界/比较与检查。初稿逐条与实际授权教材核对后才标 REVIEWED_FRAMEWORK。
- [ ] 可行性服务返回 VERIFIED/MISSING/UNRESOLVED 和候选引用，不生成正文。PPT 根据定义、图示、过程、实现、例题角色组织页组；未经审核推断标为候选建议。必要资源缺失是 BLOCKER，建议/可选缺失是 WARNING；普通主题没有框架时标 EVIDENCE_ONLY。
- [ ] 测试通过并检查三份数据没有未经核对的完整性声明，提交。

### Task 5：规划与修改服务

**新增：** `draft/OutlineService.java`、`draft/OutlineModelGateway.java`、`draft/OutlineController.java`；复用现有 `KnowledgeSearchService` 和 `AiQuotaExecution`。**测试：** `draft/OutlineServiceTest.java`、`draft/OutlineApiIntegrationTest.java`。

- [ ] 夹具覆盖初次计划、局部修改、换档、失败修改、恢复历史版本；断言这些流程从未调用正文生成器。
- [ ] 执行 `./mvnw.cmd "-Dtest=OutlineServiceTest,OutlineApiIntegrationTest" test`，确认新接口及行为未实现。
- [ ] 实现第三节的创建、读取、修改和恢复接口。PLAN/REVISE 先入库 QUEUED，由 Task 7 Worker 调用 gateway；baseRevision 必须匹配最新有效版，REVISING 时不接受第二个并行修改。没有变化也保留修改解释，不能伪造 revision 已准备。
- [ ] 规划提示显式写三档目标与学生限制；修改提示输入原大纲、框架覆盖、修改意见及不可变 ID 集合。输出经结构、覆盖、可行性检查，合法草稿和 issues 一起保存；有 BLOCKER 的草稿可审阅修改但不能确认。配额或模型失败保留旧版。
- [ ] 测试通过后提交。此时新服务可经测试夹具使用，前端尚不切换。

### Task 6：确认事务、运行版本与旧接口门禁

**新增：** `draft/OutlineConfirmationService.java`；**修改：** `StudyroomController.java`、`StudyroomService.java`、`StudyroomView.java`、`JdbcStudyroomRepository.java`。**测试：** `draft/OutlineConfirmationIntegrationTest.java`、现有 `StudyroomApiIntegrationTest.java`。

- [ ] 写并发测试：两次同键确认只生成一个 run/首任务；同一 baseRevision 修改与确认竞争只有一种成功；确认旧版本返回 409；他人草稿 404；缺必要资源 422。
- [ ] 执行 `./mvnw.cmd "-Dtest=OutlineConfirmationIntegrationTest,StudyroomApiIntegrationTest" test`，确认新增断言失败。
- [ ] 在单一事务锁 draft、检查 latest revision/无 pending 修改/必要资源重新授权，保存 approved_outline_json，创建 room/run/units/首任务。新 run 替换旧 run 时使用 room → run → units 的固定锁顺序；取消旧任务，禁止晚到结果写入新 run。
- [ ] 旧 POST 创建返回 STUDYROOM_OUTLINE_REQUIRED；旧 GET、进度和旧课堂追问保持工作。新 run 的拓展请求返回大纲修改建议/草稿入口，不直接 append 未批准步骤。源码/资源读取不能由客户端给的 JSON 决定。
- [ ] 测试通过后提交，前端切换与 API 门禁同一发布批次，避免旧页面失去创建入口。

### Task 7：持久化 Worker、租约与计费恢复

**新增：** `generation/StudyroomGenerationWorker.java`、`generation/GenerationCallService.java`、`generation/GenerationProperties.java`；修改现有配置文件以增加并发、租约、轮询和预取项。**测试：** `generation/GenerationWorkerIntegrationTest.java`、`generation/GenerationCallServiceTest.java`。

- [ ] 测试两个 Worker 只能领取一次、过期租约不能发布、服务重启可发现 QUEUED、已有 response 不二次调用、SENT 未落结果恢复为 MODEL_CALL_UNCERTAIN、显式重试使用新 attempt/key。
- [ ] 执行 `./mvnw.cmd "-Dtest=GenerationWorkerIntegrationTest,GenerationCallServiceTest" test`，预期新 Worker 缺失。
- [ ] 实现第四节原子领取/心跳/发布规则。周期扫描和有限执行池分离，任务投递前检查本机容量；PLAN/REVISE 调 Task 5 gateway，UNIT 调 Task 8 服务。禁止持有数据库事务等待模型网络调用。
- [ ] 每次模型调用存独立阶段记录并使用稳定 quota key；原调用失败或不确定后不把旧 key 当成缓存重新调用。用户重试前给出实际状态说明；成功结果与 job 发布使用 CAS，不能靠线程存活来保证正确性。
- [ ] 重跑测试并检查配额账本无重复 key；提交。

### Task 8：单元与步骤生成、局部修复及质量门

**新增：** `generation/UnitGenerationService.java`、`generation/StepGenerationService.java`、`generation/TeachingQualityGate.java`；抽取现有 `StudyroomGraph.java` 中可复用编排部分到 `generation/StudyroomResourceBindingService.java`。**测试：** `generation/UnitGenerationServiceTest.java`、`generation/TeachingQualityGateTest.java`。

- [ ] 回归覆盖大纲已确认但正文少一个目标、过程演示被换成纯 PPT、实现漏代码、材料越权、源码函数名不匹配、单步失败后其余步骤不变、修复后仍缺承诺不能通过。
- [ ] 执行 `./mvnw.cmd "-Dtest=UnitGenerationServiceTest,TeachingQualityGateTest" test`，确认新增质量断言失败。
- [ ] 每单元先按冻结目标规划最多 8 步，再按步生成/选择场景；具体模型调用按阶段分开记账，不靠整堂输出重试。成功步骤先写入单元内部结果，单元检查通过才对外 READY；失败步骤保留 FAILED，重试只处理该步。
- [ ] 静态 gate 验证 coverageKeys、必要活动/材料、证据及资源白名单、参数、源码行范围和实验输入；语义 gate 对照实际教材/源码检查解释。语义结果携带目标与材料依据，不能只输出分数。来源不充分时报告缺口，不能伪造完整性。
- [ ] 材料权限变化或必要能力不可用时返回 NEEDS_OUTLINE_REVISION，保留其他已准备单元，生成入口指向新大纲草稿。可选材料失败可显示说明并保留其余内容；不得把可选失败扩大为整堂课重写。
- [ ] 测试通过并核对 Task 1 的旧修复回归仍通过，提交。

### Task 9：单元读取、预取与内容复用

**新增：** `generation/StudyroomUnitController.java`、`generation/StudyroomUnitService.java`。**修改：** `StudyroomView.java`、`StudyroomService.java`。**测试：** `generation/StudyroomUnitApiIntegrationTest.java`。

- [ ] 测试 GET 不生成、prepare 重复请求复用任务、READY 不重调模型、只预取下一单元、旧 run 请求写入被拒绝、草稿重确认只复用目标/材料哈希相同且仍有权限的单元。
- [ ] 执行 `./mvnw.cmd "-Dtest=StudyroomUnitApiIntegrationTest" test`，确认新读写行为缺失。
- [ ] 实现第三节单元路由；返回 unit status、errorCode、可执行操作、READY 正文和已确认目标。新 room GET 增加 currentRunId、outlineRevision、units 概要，旧字段保留以兼容旧课堂。
- [ ] 首单元 READY 或学生选择后准备下一单元，队列已存在则不重复。前端退出不启动新的全课任务；服务端至多一个预取单元。重新确认时复制可复用单元关联并重新授权，不按标题相同复用。
- [ ] 测试通过后提交。

### Task 10：前端三档与大纲协商

**新增：** `frontend/src/user/components/studyroom/StudyroomDepthPicker.vue`、`StudyroomOutlineReview.vue`、`StudyroomOutlineDiff.vue`、`frontend/src/user/studyroom-outline-state.ts`；每个组件新增同名 `.spec.ts`。**修改：** `frontend/src/user/views/StudyroomView.vue`、`frontend/src/user/api.ts`、`frontend/src/router/index.ts`、相关类型与 API 测试。

- [ ] 写失败用例：默认 STANDARD、三档说明可见、首次发送只创建草稿、修改提交 baseRevision、差异显示、失败修改保留原大纲、换档重新审阅、未确认不显示正文、刷新恢复草稿。
- [ ] 在 frontend 执行 `npm test -- src/user/components/studyroom/StudyroomDepthPicker.spec.ts src/user/components/studyroom/StudyroomOutlineReview.spec.ts src/user/components/studyroom/StudyroomOutlineDiff.spec.ts src/user/api.spec.ts`，预期新功能未实现。
- [ ] 增加 `/studyroom/outlines/:draftId` 路由，在 `/studyroom/:id` 之前声明；`/chat` 与 `/studyroom` 显示问题+深度入口。router.replace 草稿地址后保留状态，已有课堂 URL 继续读取，不触发规划。
- [ ] 状态模块只负责 DRAFT/REVIEW/CONFIRMING/LEARNING 及请求序号，不把旧响应写进新草稿。生成和修改提交期间禁用确认；轮询获取最新 revision 后显示差异，不自动确认。恢复旧版也写为新 revision。
- [ ] 幂等 key 在一次 pending 操作中复用，网络重试不重新随机；用户下一次新修改才换 key。键盘焦点、错误重试和 390px 窄屏可用；沿用现有视觉风格。
- [ ] 重跑测试及 `npm run typecheck`，提交。

### Task 11：前端单元学习、状态与重新规划

**新增：** `frontend/src/user/components/studyroom/StudyroomUnitNavigator.vue`、`StudyroomUnitPreparation.vue`、`frontend/src/user/studyroom-unit-poll.ts` 和同名测试。**修改：** `StudyroomView.vue`、`StudyroomSceneStage.vue`、现有 StudyroomView.spec.ts。

- [ ] 测试首单元 READY 即可学习、未准备和准备中区别、失败局部重试、切换单元后旧响应丢弃、当前草稿/旧 run 后台结果不覆盖新 run、代码草稿不因轮询重建组件丢失。
- [ ] 执行 `npm test -- src/user/views/StudyroomView.spec.ts src/user/studyroom-unit-poll.spec.ts src/user/components/studyroom/StudyroomUnitNavigator.spec.ts src/user/components/studyroom/StudyroomUnitPreparation.spec.ts`，预期新状态行为失败。
- [ ] 轮询只在 QUEUED/GENERATING 时每 2 秒一次，页面隐藏退避到 10 秒，终态/路由离开停止；GET 不触发生成。按 runId/unitId/stepId 固定组件 key，不因更新列表重建编辑器。
- [ ] 学习页可查看确认大纲及范围，点击“调整学习计划”进入独立草稿，未确认前原课仍可读。确认失败保留草稿和旧课；成功后按新 run 切换。运行服务缺失说明与模型生成失败分别呈现，不展示虚假学习进度百分比。
- [ ] 测试通过、类型检查通过后提交。

### Task 12：完整质量样本、兼容与交付

**新增：** `backend/spring/src/test/resources/studyroom-reviewed-outline/` 下 nine-case 夹具（栈/队列/二叉树各 QUICK/STANDARD/DEEP），`docs/project/studyroom-reviewed-outline-acceptance-2026-10-18.md`。**修改：** `contracts/openapi-v1.yaml`、`backend/spring/README.md`、必要前后端集成测试。

- [ ] 九类夹具分别定义 selectedCoverage、excludedCoverage、必要活动/材料和深度差异，不以“某词出现”或固定步骤数当作完整性标准。加入自然修改“顺序栈详细、链栈带过、不学表达式求值”、单个实验、无编程、缺沙箱、撤销资源、输出畸形及服务重启用例。
- [ ] 先运行新增集成断言确认缺口，再完成对应最小修正；不得删除教学质量断言来获得通过。所有调用上游的人工验收与固定夹具结果分开记录。
- [ ] 在 backend/spring 执行 `./mvnw.cmd test`；在 frontend 执行 `npm test`、`npm run typecheck`、`npm run build`。执行 `node scripts/verify-api-contract-fixtures.js` 并解析 YAML，核对新增接口与真实控制器一致。
- [ ] 已配置模型时对三个主题各跑三档，记录大纲、修改差异、确认版本、资源角色及单元交付结果；模拟单步失败，比较修复前后承诺，验证无整体缩水。无模型时明确未执行真实验收，不能以夹具成功替代。
- [ ] 浏览器测试完整链路及窄屏：问题/深度 → 大纲 → 两轮修改 → 过期确认拒绝 → 正确确认 → 首单元可学 → 后续单元准备 → 单步重试 → 重新规划；验证同请求多次点击不会新建多任务。截图只保存在 private/run-logs，不提交私有教材截图。
- [ ] 测试服务重启恢复任务、SENT 不确定调用不自动重复、旧 Worker 发布被拒绝；需要时用两个 Worker 的集成夹具验证，不把单进程冒烟称为多实例可靠。
- [ ] 发布包包含最新前端、接口门禁、迁移和 Worker。回退时关闭新任务领取并保留数据，不回滚删除新表；旧课堂只读可继续使用，若回退到旧后端则同时关闭创建入口以免绕过确认。配置文档说明配额、任务不确定性及沙箱边界。
- [ ] `git diff --check`、相关新增文件空白检查通过；编写验收记录，报告已验证与未验证项，只提交可安全分离的本次补丁。不自动部署生产。

## 六、自审与依赖顺序

任务依赖：1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12。中间阶段以测试和受控接口验证，不提前替换用户现有可用入口。前后端门禁和入口同批交付。

设计覆盖：三档→3/4/10；大纲及对话/差异/恢复→2/3/5/10；确认与所有权→2/6；按单元生成和预算→7/8/9；稳定状态及幂等→2/6/7/11；教学与资源检查→4/8/12；单步修复→1/8；材料权限变化→4/6/8；已有课堂兼容→2/6/9/12；重新规划及内容复用→6/9/11；不确定模型调用→7/12；真实样本→12。

命名统一：LearningDepth 与 depth、outlineRevision、runRevision、unitKey、jobId、quota_request_id 各自单一含义。接口传服务端保存的大纲版本号，不接受客户端完整 JSON 作为确认依据。每任务均有具体测试对象、执行命令和可判定结果；执行时仍需根据失败证据调整实现，不跳过红绿验证或用关键词测试冒充教学验收。
