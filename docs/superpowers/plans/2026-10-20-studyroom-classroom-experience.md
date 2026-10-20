# Continuous Classroom Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task in the current conversation. Steps use checkbox (`- [ ]`) syntax for tracking. Preserve the current working tree and do not dispatch agents or create a separate chat unless the user selects that execution mode.

**Goal:** 交付阶段动画与真实增量大纲、连续材料舞台、带引用的流式问答和位置恢复，并补齐受控教学动作与知识点小练习。

**Architecture:** 复用既有草稿、冻结片段、生成任务、租约及额度执行；增加持久事件和快照作为恢复依据，SSE 只负责传输。前端分离订阅、课堂会话、材料适配器和呈现组件。模型只能提出内容，服务端控制权限、预算、版本和发布。

**Tech Stack:** Java 21、Spring Boot、JdbcTemplate、Flyway、H2/MySQL、Vue 3、TypeScript、JUnit/MockMvc、Vitest、Playwright。

**Spec:** `docs/superpowers/specs/2026-10-20-studyroom-classroom-experience-design.md`，用户已通过“继续”批准书面设计并进入计划阶段。

**Status:** 待执行。本文的测试、代码示例和命令是实施要求，不是已通过结果。

---

## 执行约束与依赖

根目录为 `G:/Code/Structify`。下面文件路径均相对于该根目录；命令明确标注工作目录。

当前工作树已有未提交的片段、课程库、额度策略和浏览器夹具，不得从 HEAD 创建一个丢失这些依赖的干净副本后声称功能完成。先记录相关文件差异与运行进程，保留无关改动；提交已有脏文件时按任务选择修改块，新文件逐个枚举，禁止 `git add .`。迁移、代码和文档不得包含密钥、数据库、`private/`、教材或压缩包。

目前最大迁移版本为 V39。执行任务 2 前重新检查；计划使用 V40、V41、V42，若被其他任务占用，则选择新的连续唯一版本并同步本计划，不能修改已应用迁移。开发数据库不重置，不能启动两个进程访问同一个 H2 文件。

普通单元、契约与浏览器测试只用合成数据或确定性模型夹具。真实演示请求单独执行和记账，不作为普通回归步骤。

任务 8–12 是同一个接口切换窗口：开发分步测试可以逐项执行，但不能在前端尚未迁移到异步问题契约时发布或宣布课堂可用。项目未上线，不增加旧同步模式开关或第二套兼容生成链。

依赖顺序：1 → 2 → 3 → 4 → 5 → 6；5 → 7；6 → 8；3+4+6 → 9；7+8 → 10 → 11；9+10+11 → 12；12 → 13 → 14 → 15；16 最终验收。任务 13–15 仍是本次交付范围。

## 文件职责图

后端已有入口：

- `backend/spring/src/main/java/com/feng/dsagent/studyroom/draft/OutlineModelGateway.java`：授权资料与大纲模型输入。
- `backend/spring/src/main/java/com/feng/dsagent/studyroom/draft/OutlineService.java`：正式版本、确认、修订及幂等。
- `backend/spring/src/main/java/com/feng/dsagent/studyroom/generation/GenerationCallService.java`：受额度保护的生成调用与调用记录。
- `backend/spring/src/main/java/com/feng/dsagent/studyroom/generation/StudyroomGenerationWorker.java`：既有调度入口，改为显式 job_type 分发。
- `backend/spring/src/main/java/com/feng/dsagent/studyroom/StudyroomFollowUpService.java`：提取原有授权追问上下文，迁移后删除同步模型调用路径。

新增后端包：

- `studyroom/events`：事件、订阅、快照、所有权和保留期。
- `studyroom/questions`：异步问题、逐段回答、取消与正式消息提交。
- `studyroom/teaching`：动作目标校验与教学附件。
- `studyroom/checks`：单选题、服务端判分与作答记录。

这些包完整路径前缀为 `backend/spring/src/main/java/com/feng/dsagent/`；对应测试前缀为 `backend/spring/src/test/java/com/feng/dsagent/`。下列任务逐个指定文件名，不能在现有 View 中持续堆叠职责。

前端新增文件：

- `frontend/src/shared/types/classroom-experience.ts`：共享事件、问题、快照、动作与练习读模型。
- `frontend/src/user/classroom-events.ts`、`use-classroom-events.ts`：SSE 传输及按身份恢复。
- `frontend/src/user/use-outline-generation.ts`：大纲生成与旧版保留。
- `frontend/src/user/use-classroom-session.ts`：问答状态与恢复快照。
- `frontend/src/user/classroom-materials.ts`：材料适配器接口及注册。
- `frontend/src/user/classroom-question-runtime.ts`、`classroom-check-runtime.ts`：问题与练习 API。
- `frontend/src/user/components/studyroom/StudyroomGenerationFeedback.vue`：真实阶段与增量动画。
- `frontend/src/user/components/studyroom/StudyroomClassroomLayout.vue`：响应式三栏布局。
- `frontend/src/user/components/studyroom/StudyroomQuestionPanel.vue`：引用、输入、流式回答和恢复。
- `frontend/src/user/components/studyroom/StudyroomCheckPanel.vue`：练习与结果。
- `frontend/src/user/use-teaching-actions.ts`：受支持材料动作执行与去重。

## 固定类型与语义

任务 1 落地以下类型。之后不得给同一概念换字段名：

```ts
export type ClassroomEventKind =
  | 'phase' | 'outline_item' | 'outline_validated' | 'content_published'
  | 'answer_delta' | 'answer_completed' | 'failed' | 'cancelled' | 'reset';
export interface ClassroomIdentity {
  streamId: string; operationId: string; attempt: number;
  runId: string | null; revision: number | null;
}
export interface ClassroomEvent extends ClassroomIdentity {
  sequence: number; stateVersion: number; kind: ClassroomEventKind;
  payload: Record<string, unknown>;
}
export interface ClassroomStreamSnapshot extends ClassroomIdentity {
  cursor: number; floor: number; terminal: boolean;
  stateVersion: number; state: Record<string, unknown>;
}
export type QuestionStatus = 'QUEUED'|'RUNNING'|'COMPLETED'|'FAILED'|'CANCELLED';
export type ClassroomMode = 'READING'|'ASKING'|'ANSWERING'|'AWAITING_RESUME';
export interface MaterialIdentity {
  roomId: string; runId: string; unitId: string; fragmentId: string;
  contentRevision: number; sceneId: string; resourceId: string | null;
}
export type MaterialPosition =
  | {kind:'TEXT'; scrollTop:number}
  | {kind:'PPT'; pageId:string; zoom:number; scrollLeft:number; scrollTop:number}
  | {kind:'ANIMATION'; traceId:string; index:number; speed:number;
      playing:boolean; detail:'debug'|'brief'}
  | {kind:'C_CODE'; code:string; stdin:string; editing:boolean; scrollTop:number};
export interface LearningSnapshot {
  identity: MaterialIdentity; navigationVersion: number; position: MaterialPosition;
}
export interface TeachingAction {
  id:string; sequence:number; runId:string; fragmentId:string; contentRevision:number;
  sceneId:string; resourceId:string;
  kind:'CODE_HIGHLIGHT'|'MATERIAL_FOCUS'|'ANIMATION_SEEK';
  targetId?:string; startLine?:number; endLine?:number; stepIndex?:number;
}
export interface CheckQuestion {
  id:string; unitId:string; contentRevision:number; prompt:string;
  options:Array<{id:string;text:string}>;
}
```

阶段码只允许 `QUEUED`、`RETRIEVING`、`PLANNING`、`VALIDATING`、`PREPARING_RESOURCES`、`GENERATING_CONTENT`、`PUBLISHING`。只有实际生产路径发出对应事件；没有细分阶段的任务仅报告确知阶段。

流 ID 固定为 `outline:{draftId}`、`run:{runId}`、`question:{questionId}`。sequence 是流内单调序号，不是百分比；attempt 变化清除未验证预览，不改变先前正式 revision。

### Task 1: 固定契约与记录基线

**Files:** `contracts/openapi-v1.yaml`；`frontend/src/shared/types/classroom-experience.ts`；`frontend/src/shared/types/outline.ts`；`frontend/src/shared/types/studyroom.ts`；`scripts/verify-api-contract-fixtures.js` 与 `fixtures/http/spring-v1-admin-and-ai.json`。

- [ ] 记录 `git status --short`、`git diff --cached --name-only`、当前分支、迁移列表和 8792/5173 监听进程。保存相关差异到被忽略的 `.superpowers/`，不重置或暂存无关文件。
- [ ] 扩展现有契约验证和合成响应案例，固定 `QuestionStatus`、事件种类、问题 202 响应和练习提交前不包含 answer。用实际接口响应匹配案例及 schema，不能测试自造常量对象。有效响应形状为：

```json
{"questionId":"q1","status":"QUEUED","streamId":"question:q1","cursor":0}
```

```json
{"id":"check1","unitId":"u1","contentRevision":1,"prompt":"先弹出谁？","options":[{"id":"a","text":"1"},{"id":"b","text":"2"}]}
```

- [ ] 从根目录执行 `node scripts/verify-studyroom.mjs --scope contracts`。增加覆盖新端点的契约夹具后确认缺少类型/端点时报失败，不能仅断言本地对象就宣布服务契约通过。
- [ ] 实现本节完整 TS 类型，并在 OpenAPI 明确 SSE 游标、reset、幂等键、401/404/409/202、停止与断线的区别。`POST /study-rooms/{id}/messages` 返回回执；GET 问题快照和其 events/cancel；大纲/run events；练习与作答接口。
- [ ] 更新合成 HTTP 夹具并重跑契约验证。此时不改变生产 Controller 行为；提交只包括本任务契约及类型块。

### Task 2: 事件存储与快照

**Create:** `backend/spring/src/main/resources/db/migration/V40__studyroom_event_streams.sql`；`studyroom/events/ClassroomEvent.java`、`JdbcClassroomEventRepository.java`、`ClassroomEventService.java`；测试 `studyroom/events/ClassroomEventRepositoryTest.java`。

- [ ] 用隔离 H2 与 Flyway 写失败测试：同一流顺序为 1、2；两个用户不能读对方流；回滚追加不留下事件；snapshot.cursor 与快照状态对应同一提交。
- [ ] 从 `backend/spring` 执行 `./mvnw.cmd '-Dtest=ClassroomEventRepositoryTest' test`，记录缺失存储的失败。
- [ ] 重新检查迁移版本；创建以下表并加外键到 users，复用现有 JSON 存储与时间写法：

```sql
CREATE TABLE studyroom_event_streams (
 stream_id VARCHAR(96) PRIMARY KEY, user_id BIGINT NOT NULL,
 room_id VARCHAR(36), entity_kind VARCHAR(16) NOT NULL, entity_id VARCHAR(36) NOT NULL,
 operation_id VARCHAR(36) NOT NULL, attempt INT NOT NULL, run_id VARCHAR(36), revision INT,
 last_sequence BIGINT NOT NULL DEFAULT 0, floor_sequence BIGINT NOT NULL DEFAULT 0,
 state_version BIGINT NOT NULL DEFAULT 0, snapshot_json LONGTEXT NOT NULL,
 terminal BOOLEAN NOT NULL DEFAULT FALSE, terminal_at TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE studyroom_events (
 stream_id VARCHAR(96) NOT NULL, sequence BIGINT NOT NULL, kind VARCHAR(32) NOT NULL,
 operation_id VARCHAR(36) NOT NULL, attempt INT NOT NULL, payload_json LONGTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(stream_id,sequence),
 FOREIGN KEY(stream_id) REFERENCES studyroom_event_streams(stream_id) ON DELETE CASCADE
);
CREATE INDEX idx_studyroom_stream_retention ON studyroom_event_streams(terminal,terminal_at);
```

- [ ] 实现追加事务：按“用户 → 当前任务/问题 → stream”顺序加锁，检查身份及租约，分配 last_sequence+1，写事件并更新 snapshot_json/版本/终态。单条事件 payload 限制 16 KiB；终态后拒绝新的内容增量。读按序最多返回 100 条；快照和 cursor 必须在同一事务读取。
- [ ] 验证并发追加、旧 attempt、已删除课程及终态追加拒绝。保留期清理仅处理终态 24 小时后的事件，更新 floor_sequence，保留快照；活动流不能清理。重跑测试后单独提交。

### Task 3: 只读 SSE 订阅与重连

**Create:** `studyroom/events/ClassroomEventController.java`、`ClassroomEventSubscriptions.java`；测试 `studyroom/events/ClassroomEventApiTest.java`。**Modify:** 任务 2 的事件服务。

- [ ] 写 MockMvc 失败用例：未登录 401、非所有者 404、已删除 404、负游标 400、游标超过当前 cursor 返回 reset、游标小于 floor 返回 reset。两个订阅不得调用模型。
- [ ] 运行 `./mvnw.cmd '-Dtest=ClassroomEventApiTest' test`。
- [ ] 用 Spring `SseEmitter` 实现三个 events 路由。每个连接用短读事务按游标读取，批量写出事件；内部无变化时退让等待，不持有数据库事务睡眠。15 秒心跳注释，连接最多 5 分钟后关闭供客户端续接。断线仅关闭订阅，不取消任务。
- [ ] SSE 数据格式固定如下，事件 `id` 与 payload.sequence 一致；reset 携带权威 snapshot 和它的 cursor：

```text
id: 12
event: outline_item
data: {"streamId":"outline:d1","operationId":"j1","attempt":1,"runId":null,"revision":1,"sequence":12,"stateVersion":4,"kind":"outline_item","payload":{"unit":{"id":"stack","title":"栈"}}}

```

- [ ] 测试追加与读快照之间的竞态、正常终态排空、客户端断线清理、每次批读重新检查访问权。reset 后不能漏读快照之后的新事件。重跑测试后提交，不新增模型执行入口。

### Task 4: 将真实流式调用接入现有额度与租约

**Modify:** `studyroom/generation/GenerationCallService.java`、`studyroom/generation/JdbcGenerationRepository.java`；**Create tests:** `studyroom/generation/GenerationStreamingCallTest.java`。

- [ ] 写可控 ModelClient 夹具：onContent 分两次到达、onUsage 报总量；测试两个内容回调都在 complete 之前发生、仅预留一次额度、租约丢失后不再写事件。
- [ ] 运行 `./mvnw.cmd '-Dtest=GenerationStreamingCallTest' test` 观察失败。
- [ ] 抽取现有 complete 的“创建调用/检查已有调用/结算/记录用量”共用实现，增加：

```java
public String stream(long user, String jobId, String phase, String system,
        String prompt, int maximumTokens, java.util.function.Consumer<String> content);
```

- [ ] 实现中调用既有 `AiQuotaExecution.stream`，在 onContent 检查取消、current-run 与租约，累计至原有 64,000 字符上限后转发；onReasoning 不交给学生；onUsage 交由既有结算路径处理。将回答与用量写入原生成调用记录，不额外 reserve/settle，不把读事件当调用。
- [ ] 测试断流、模型异常、超长输出、已有成功调用和结果不确定调用。缓存成功结果可以用于恢复状态，但不得伪装为新的实时增量。重跑本测试及 `AiQuotaLedgerIntegrationTest`，只提交本任务块。

### Task 5: 完整单元增量解析与大纲事件

**Create:** `studyroom/draft/OutlineIncrementalParser.java`；测试 `studyroom/draft/OutlineIncrementalParserTest.java`、`OutlineStreamingWorkflowTest.java`。**Modify:** `OutlineModelGateway.java`、`OutlineService.java`、`OutlineController.java`、`StudyroomGenerationWorker.java`。

- [ ] 先测完整对象边界：把以下输入逐字符 feed，unit 只出现一次；直到完整文档结束且正式校验通过，confirm 保持拒绝：

```json
{"schemaVersion":1,"units":[{"id":"stack","title":"引号\"与括号 }","objective":"理解","materials":[]}]}
```

- [ ] 执行 `./mvnw.cmd '-Dtest=OutlineIncrementalParserTest,OutlineStreamingWorkflowTest' test`，确认失败。
- [ ] 解析器定位根对象的 units 数组，逐字符跟踪容器栈、字符串和转义；每个完整单元闭合后用 OutlineJson 解析并校验预览字段，最多 24 条/条目 16 KiB。不扫描其他嵌套 units、不修复未闭合 JSON、不向前端发送原始片段。
- [ ] worker claim 后写 QUEUED/RETRIEVING/PLANNING；gateway 复用任务 4 stream，在回调中写 outline_item。完整响应后写 VALIDATING，沿用证据、schema、范围、资源与 planHash 校验；只有原正式版本事务成功后才能追加 outline_validated。GET 草稿补充 generation 快照。修订失败保留上版，重试切 attempt 并重置仅预览部分。
- [ ] 回归逐字符/跨字符串/嵌套数组/重复单元 ID/多用户/旧租约/重试/校验失败。部分 JSON 未结束不允许触发 confirm；stream 中断不能保存正式 revision。通过后提交。

### Task 6: 前端订阅与生成反馈

**Create:** `frontend/src/user/classroom-events.ts`、`use-classroom-events.ts`、`use-outline-generation.ts` 及对应 `.spec.ts`；`frontend/src/user/components/studyroom/StudyroomGenerationFeedback.vue` 和 `.spec.ts`。**Modify:** `StudyroomOutlineFlow.vue`、`outline-runtime.ts`、`frontend/src/shared/i18n/messages.ts`。

- [ ] 写失败测试：同 sequence 去重、低版本丢弃、attempt 切换清空预览、reset 换快照；断线后携带最后 cursor 重连；订阅重连不再次调用 create/revise。
- [ ] 从 `frontend` 执行 `npm test -- --run src/user/use-classroom-events.spec.ts src/user/use-outline-generation.spec.ts src/user/components/studyroom/StudyroomGenerationFeedback.spec.ts`。
- [ ] 复用共享 API `responseType:'sse'` 和现有 SSE parser，实现可取消订阅与 1/2/5/10 秒退避，上限 10 秒；卸载清理。先读快照再从 cursor 订阅；按完整身份和序号接收，不以组件可见性启动生成。纯接收判定为：

```ts
function belongs(event:ClassroomEvent, identity:ClassroomIdentity, cursor:number) {
  return event.streamId===identity.streamId && event.operationId===identity.operationId
    && event.attempt===identity.attempt && event.runId===identity.runId
    && event.revision===identity.revision && event.sequence>cursor;
}
```

  此判定用于已经确认身份的增量；reset 或收到不同 operationId/attempt 时先读取权威快照，再切换订阅身份与预览。不能把所有新 attempt 永久丢弃，也不能凭一个未知增量自行改变正式 revision。

- [ ] 生成反馈接收真实 phase、preview、terminal/error、旧正式大纲和可确认状态；复用 AiBall，条目淡入并允许展开。生成中的列表只读；正式大纲仍使用现有修订/确认入口。减少动态效果下禁用循环与位移动画；状态播报只播阶段/完成/失败，不逐条打断用户。
- [ ] 回归确认按钮、旧版保留、长列表不自动抢滚动、减少透明度与多语言。关闭页面只中止订阅。通过后提交，禁止用定时追加已完成大纲冒充实时生成。

### Task 7: 首段候课改用权威发布事件

**Modify:** `studyroom/fragment/FragmentResourceResolver.java`、`FragmentComposer.java`、`FragmentPublicationService.java`、`FragmentReadService.java`；`frontend/src/user/use-studyroom-preparation.ts` 及 `.spec.ts`。**Create test:** `studyroom/fragment/PreparationEventsTest.java`。

- [ ] 写事件时序测试：资源准备/生成/检查/发布只在实际路径出现；第一段验证发布前 firstReadableFragmentId 为空；事务回滚不能发出 content_published。
- [ ] 执行 `./mvnw.cmd '-Dtest=PreparationEventsTest,ProgressiveOutlineConfirmationTest' test`；前端执行 `npm test -- --run src/user/use-studyroom-preparation.spec.ts`。
- [ ] 发布服务在同一事务写片段状态与事件；准备快照从当前 run 聚合。READY 事件只触发读取权威读模型，不由前端拼装正文。后台其他知识点阶段不得错误显示为首段阶段。
- [ ] 将现有 epoch、revision、draft/run 比对迁移到事件订阅；只有当前页面身份仍匹配且权威状态可读才自动进入。重连期间反馈“正在恢复连接”；失败反馈来自服务端；离开页面不取消有界准备任务、不自动跳回。
- [ ] 测试首段失败、后续失败不挡已读、低 stateVersion、用户切草稿、页面卸载、事件丢失 reset。通过后提交，移除被事件取代的重复轮询。

### Task 8: 问题持久化、幂等与取消

**Create:** `V41__studyroom_questions.sql`；`studyroom/questions/QuestionRecords.java`、`JdbcQuestionRepository.java`、`QuestionService.java`、`QuestionController.java`；测试 `QuestionLifecycleTest.java`、`QuestionApiTest.java`。**Modify:** `StudyroomController.java` 及其旧同步响应测试。

- [ ] 测试同幂等键同请求返回同 questionId，异内容 409；同一用户当前 run 最多一个活动问题；非所有者 404；来源修订已变化 409；删除和 supersede 后取消。
- [ ] 执行 `./mvnw.cmd '-Dtest=QuestionLifecycleTest,QuestionApiTest' test`。
- [ ] 迁移建 `studyroom_questions`，字段至少含 id/user_id/room_id/run_id/unit_id/fragment_id/content_revision、question/context_json、status、answer_draft、user_message_id/assistant_message_id、created_at/updated_at；建 `(user_id,run_id)` 活动锁表或以现有用户行锁串行检查，不依赖应用进程内布尔值。将用户消息写入已有 transcript；问题事件流使用同一事务。
- [ ] POST messages 接受有效来源和 Idempotency-Key，创建一次问题/用户消息和 FOLLOWUP 任务，返回 202；同一任务中移除 StudyroomController 原 POST 映射，避免重复路由注册。GET 问题快照只暴露所有者数据，GET room 补充 activeQuestionId 供刷新恢复。cancel 幂等：终态原样返回；活动任务撤销租约并标 CANCELLED，阻止后续正文/动作发布。
- [ ] 取消不删除问题、用户消息或额度记录。SSE 断线不调用 cancel。测试所有并发与重试规则后提交。

### Task 9: 流式追问 worker 与唯一正式回答

**Create:** `studyroom/questions/QuestionContextResolver.java`、`QuestionGenerationService.java`；测试 `QuestionStreamingTest.java`。**Modify:** `StudyroomGenerationWorker.java`、`StudyroomFollowUpService.java`、`StudyroomController.java`、`GenerationCallService.java`。

- [ ] 用确定性模型夹具测试 text delta 先到、正式助手消息只在完成后写一次；重复任务不重复调用；取消、删除和旧 run 不发布迟到回答。
- [ ] 执行 `./mvnw.cmd '-Dtest=QuestionStreamingTest,QuestionApiTest' test`。
- [ ] 抽取现有 FollowUpService 授权依据、绑定资源与最近消息装配逻辑供 resolver 使用。代码行引用对照授权源码，动画/课件引用对照当前资源；学生输入仅为数据，不能扩大资源权限。最多保留现有 8 条历史消息，沿用 4,000 字符问题/12,000 字符回答上限及 1,800 输出 token 预算。
- [ ] worker 明确 switch PLAN/REVISE/FRAGMENT/FOLLOWUP，不把新任务落入原 fragment 分支。FOLLOWUP 优先级 400，高于前台片段 300；不抢占已发送调用，仍沿用每用户并发限制。问题任务的 unit_id/fragment_id 留空，来源只存于问题记录和 payload.questionId，防止原 fail/预算逻辑误改片段；run_id 和 draft_id 仍绑定当前课程以支持删除围栏。流式回答使用普通文本模式，不能沿用现有“直接回答却 jsonObject=true”的不一致配置。100ms 或 1KiB 合并 answer_delta，先持久化再发出；最终在用户/current-run/租约检查下提交唯一助手消息和 answer_completed。
- [ ] 失败保留 answer_draft 并标未完成；明确重试创建新尝试与额度请求，GET 和重连不重试模型。迁移完本模块调用方后删除旧同步 answer 执行链和过时测试，保留其他模块聊天。验证准实时展示、优先级、结算与发布围栏后提交。

### Task 10: 材料适配器与可恢复快照

**Create:** `frontend/src/user/classroom-materials.ts`、`use-classroom-session.ts` 及 `.spec.ts`。**Modify:** `StudyroomSceneStage.vue`、`StudyroomSlideScene.vue`、`StudyroomCodeScene.vue`、`frontend/src/animation/AnimationPlayer.vue`。共享动画回归：`frontend/src/animation/useAnimationPlayback.spec.ts`。

- [ ] 写快照测试：原本播放才恢复播放；原本暂停仍暂停；导航版本变化不强制回退；相同材料身份恢复页码/滚动/代码/标准输入。代码对象不作为默认模型输入。
- [ ] 执行 `npm test -- --run src/user/use-classroom-session.spec.ts src/user/classroom-materials.spec.ts src/animation/useAnimationPlayback.spec.ts`。
- [ ] 定义材料接口，所有组件依照同一 API 实现；不支持的动作通过布尔能力报告：

```ts
export interface MaterialAdapter {
  identity():MaterialIdentity;
  capture():MaterialPosition;
  pause():void;
  restore(position:MaterialPosition):boolean;
  supports(kind:TeachingAction['kind']):boolean;
  apply(action:TeachingAction):boolean;
}
```

- [ ] AnimationPlayer 暴露 capture/pause/restore，复用现有 useAnimationPlayback 的 goTo/play/pause/speed；保留 -1 初始帧和 debug/brief 粒度。PPT 记录当前 resource 页 ID、缩放和滚动；代码保留 code/stdin/editing/output 的现有组件生命期；主舞台使用稳定 key 与 v-show 保留已访问材料，不能因显示问答卸载它们。
- [ ] 会话状态从 READING→ASKING→ANSWERING→AWAITING_RESUME，提交时冻结快照；恢复只有身份、内容修订和 navigationVersion 匹配才执行，否则提示来源变化。测试动画实验室原控件也保持正常后提交。

### Task 11: 连续课堂布局与引用提示

**Create:** `StudyroomClassroomLayout.vue`、`StudyroomQuestionPanel.vue` 和对应 `.spec.ts`。**Modify:** `StudyroomView.vue`、`StudyroomKnowledgeRoute.vue`、`StudyroomFragmentReader.vue`、`frontend/src/shared/i18n/messages.ts`。

- [ ] 写 DOM 行为测试：唯一知识点路线；桌面目录/材料/问答三个区域；问答收起后入口仍在；手机面板有标题、关闭、Escape 和焦点返回。引用删除后回退当前知识点，不附带已删除引用。
- [ ] 执行 `npm test -- --run src/user/views/StudyroomView.spec.ts src/user/components/studyroom/StudyroomClassroomLayout.spec.ts src/user/components/studyroom/StudyroomQuestionPanel.spec.ts`。
- [ ] 复用 BrandStage、AiTitle、既有字号/玻璃/胶囊 token。宽度 ≥1200px 三栏；768–1199px 目录折叠；<768px 材料全宽、目录和问答为独立面板。使用 minmax(0,1fr) 保证材料不撑宽页面；输入面板使用动态视口与安全区域，软键盘出现时可滚动到发送按钮。
- [ ] 问答头部显示服务器可识别的来源：知识点、课件页、授权代码行；来源快照不因发送后的材料切换而变化。未就绪材料不可发送需要该材料依据的问题，错误明确显示。减少透明度/动态效果样式在同任务落地。
- [ ] 将 View 中布局与对话呈现移入新组件，View 只负责路由和组合控制器；保留现有继续/完成/失败重试与单一知识点导航。通过后提交。

### Task 12: 接通问题 API、停止与问答恢复

**Create:** `frontend/src/user/classroom-question-runtime.ts`、`use-classroom-question.ts` 和 `.spec.ts`。**Modify:** `StudyroomQuestionPanel.vue`、`use-classroom-session.ts`、`frontend/src/user/api.ts`、`StudyroomView.vue`。

- [ ] 写失败测试：提交中重复点击不创建第二问题；断线重订阅不重发 POST；页面刷新恢复 activeQuestionId；取消后迟到 delta 不覆盖；完成后显示恢复按钮；旧问题引用不跟随新导航。
- [ ] 执行 `npm test -- --run src/user/use-classroom-question.spec.ts src/user/use-classroom-session.spec.ts src/user/views/StudyroomView.spec.ts`。
- [ ] 接入任务 8/9 的异步回执、GET 快照、SSE 和 cancel；幂等键按冻结 payload 保存至该次提交完成，网络重试仍复用。delta 只追加到匹配 question/attempt 的未完成回答；正式 transcript 从完成快照读取，不同时追加本地完整消息和服务端完整消息造成重复。
- [ ] 显式停止发送取消命令，并等待或读取权威终态；断线显示恢复连接而非模型失败。用户离开只清订阅，重进后恢复已有任务。失败保留问题输入、来源和未完成标记。
- [ ] 关联材料 pause/restore 按钮与会话状态；保存代码草稿，不自动播放原本暂停的动画。移除本模块 `followUpStudyroom():Promise<StudyroomView>` 的同步类型与无调用旧入口。通过后提交。

### Task 13: 受控教学附件与三类动作

**Create:** `V42__studyroom_teaching_and_checks.sql`；`studyroom/teaching/TeachingAttachment.java`、`TeachingActionValidator.java`、`TeachingAttachmentService.java`；`frontend/src/user/use-teaching-actions.ts` 及 `.spec.ts`。**Modify:** `FragmentComposer.java`、`FragmentPublicationService.java`、`QuestionGenerationService.java`、材料适配器。

- [ ] 写后端失败测试：最多 20 动作、错误资源/行号/元素/动画范围拒绝、旧内容修订不发布；前端同序号重复不再次 seek、学生关闭效果后不执行。
- [ ] 执行 `./mvnw.cmd '-Dtest=TeachingActionValidatorTest,TeachingAttachmentPublicationTest' test`；前端 `npm test -- --run src/user/use-teaching-actions.spec.ts`。
- [ ] 创建修订绑定附件表，含 run/fragment/content_revision、kind、validated_json、状态和错误；动作正文使用任务 1 类型。生成输入只提供已授权候选短键与适配器能力清单；资源短键由服务端绑定，不接受模型提供路径或任意浏览器代码。
- [ ] 主正文继续通过现有 FragmentOutputContract/Validator。新增附件字段在绑定前提取独立校验，不让可选动作/题目坏数据拖垮合格正文。问答先完成文字增量，再在同一 FOLLOWUP 任务中按剩余预算最多追加一次受额度保护的附件调用；问题任务上限为 2 次调用、20,000 总 token、120 秒活动时间，文字输出上限 1,800 token、附件输出上限 800 token。预估不满足剩余预算或附件失败时保留文字，不循环修复动作；不使用来源片段的剩余预算。
- [ ] `answer_completed` 在可选附件阶段结束后一次发出，携带通过校验的 actions；随后流进入终态，不允许事后追加动作事件。正文已经逐段显示，附件等待使用真实状态提示。片段动作通过 content_published 后的权威读取交付，不新增未定义事件种类。
- [ ] 校验动作后交付到权威消息/片段读模型。客户端匹配身份、修订、sequence 与适配器能力后执行；不支持图片内部元素的 PPT 只提供文字说明，不伪造节点高亮。适配器目标必须来自实际渲染材料；切材料不强制跳回。通过后提交。

### Task 14: 知识点题目生成、独立校验和服务端判分

**Create:** `studyroom/checks/CheckQuestion.java`、`CheckQuestionValidator.java`、`JdbcCheckRepository.java`、`CheckService.java`、`CheckController.java`；测试 `CheckValidationTest.java`、`CheckAttemptApiTest.java`。**Modify:** 任务 13 迁移、附件服务和既有生成 worker。

- [ ] 测试 1–3 题、2–4 个非空且不重复选项、唯一正确选项、题干/解释长度、错误单位/修订拒绝。GET 不含 answer；POST 由数据库答案判分；同幂等键只产生一次尝试。
- [ ] 执行 `./mvnw.cmd '-Dtest=CheckValidationTest,CheckAttemptApiTest' test`。
- [ ] 在任务 13 新迁移尚未应用时一并定义题目/尝试表；若已应用则新增唯一向前迁移。字段：题目 id、user/run/unit/fragment、content_revision、prompt/options_json、answer_option、analysis、status；作答 id、question_id、user_id、attempt_index、selected_option、correct、idempotency_key、created_at。答案字段不进入提交前 DTO。
- [ ] 只在每知识点最后一个冻结片段请求 1–3 个可选单选题；随片段已有模型调用生成、独立提取校验。不合格题目保留失败状态，合格正文照常发布。显式重新准备只走一个 CHECK 类型任务：来源记录在 payload、unit_id/fragment_id 留空，预算为 1 次调用、10,000 总 token、120 秒活动时间，输出最多 1,200 token；不扩大原 fragment 的预算、不自动循环。worker 增加明确 CHECK 分支。GET checks 返回状态/题目/已有尝试；POST attempts 在用户与当前修订锁下判分并返回解析。
- [ ] 练习不更新 completed_knowledge 或学习地图 mastery。测试非所有者、删除、旧 run、重复提交、显式重做保留历史及未就绪不阻塞阅读，验证通过后提交。

### Task 15: 练习面板与完整浏览器夹具

**Create:** `frontend/src/user/classroom-check-runtime.ts`、`components/studyroom/StudyroomCheckPanel.vue` 和 `.spec.ts`；`frontend/e2e/classroom-experience.spec.ts`。**Modify:** 既有 `backend/spring/src/test/java/com/feng/dsagent/studyroom/harness/` 确定性模型配置与 `frontend/e2e/fixtures/studyroom.ts`。

- [ ] 写失败测试：题目未就绪不禁用阅读；回答选择后才可提交；失败保留选择与幂等键；提交结果来自服务器；重做创建新键，已有尝试仍显示。
- [ ] 执行 `npm test -- --run src/user/components/studyroom/StudyroomCheckPanel.spec.ts`。
- [ ] 只在当前知识点讲解完成后展示练习入口；显示未就绪、可作答、提交中、结果、可重做状态。结果解释与正确答案仅在服务器提交结果后显示。所有文本进入 i18n；键盘可选择单选项并提交。
- [ ] 扩展测试 harness：可暂停的大纲 chunks、完整与非法 unit、明确失败、回答增量、取消后迟到响应、一个安全动作、一组确定性题目。测试必须对模型网关网络访问设置失败保护，不能使用真实 provider 凭据。
- [ ] 浏览器执行以下完整路径：问题→增量大纲→禁止提前确认→校验→确认→候课→材料→提问→流式回答→恢复→练习。额外测试离开不跳回、重载恢复、断线、旧事件和 390px 手机溢出、软键盘模拟与减少动态效果。保存并检查截图；`$env:PLAYWRIGHT_CHANNEL='msedge'; npm run test:e2e -- classroom-experience.spec.ts` 必须通过。

### Task 16: 全量验证、删除过时入口和实际状态文档

**Modify:** `docs/architecture/studyroom.md`、`contracts/openapi-v1.yaml`、相关 HTTP 夹具、当前计划与设计状态；仅清理已追踪无调用的旧课堂同步交付代码。

- [ ] 用 `rg` 追踪旧 `followUpStudyroom`、大纲重复轮询、过时消息 POST 返回类型及旧生成分支；先迁移调用者再删除。保留 Node 兼容服务、其他模块聊天与共享资源。已有脏文件选择本任务块提交。
- [ ] 从根目录依次运行：

```powershell
node --test scripts/verify-studyroom.test.mjs
node scripts/verify-studyroom.mjs --scope backend
node scripts/verify-studyroom.mjs --scope frontend
node scripts/verify-studyroom.mjs --scope contracts
node scripts/verify-studyroom.mjs --scope architecture
$env:PLAYWRIGHT_CHANNEL='msedge'
node scripts/verify-studyroom.mjs --scope e2e
```

- [ ] 检查每个命令的退出码和测试计数；缺少测试、浏览器或夹具时修复原因，不以跳过代替通过。若共享动画、API parser 或配额改动触及其他模块，运行其既有回归。
- [ ] 桌面/手机检查真实阶段、增量可读、失败停止动画、恢复位置、受控动作、小练习、键盘与主题；记录合成数据证据。仅在用户另行要求时运行付费教学演示，不为测试获取或提交密钥。
- [ ] 按实际完成情况更新 architecture status 与本计划复选框；标明已验证路径及仍未验证的能力。检查 diff、暂存清单、迁移唯一性及秘密/私有资料排除，随后只提交相关完成项；不自动推送或合并到其他分支。

## 计划自查与验收映射

- 真实阶段/增量/校验确认/旧版保留：任务 2–7。
- 发布围栏/预算/额度/断线和取消：任务 2–5、7–9。
- 主舞台/常驻输入/引用/手机/原有视觉：任务 10–12。
- 问答恢复/代码草稿/动画播放状态：任务 10、12、15。
- 安全动作及不支持目标回退：任务 13、15。
- 单选题/答案隐藏/独立作答证据/失败不挡正文：任务 14–15。
- 完整生命周期与跨模块回归、文档/契约真实状态：任务 16。

所有引用的新类型在固定协议或对应创建任务中定义。无未决定的产品范围；任务内遇到与当前工作树不一致的类名/接口时，先核对实际调用和测试再更新本计划，不额外建立平行实现。
