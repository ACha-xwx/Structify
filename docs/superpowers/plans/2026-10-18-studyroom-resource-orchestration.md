# Studyroom Resource Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. Execute inline; no subagent dispatch is required.

**Goal:** 消除课程问答资源冗余，支持步骤内多页课件，并按步骤目标选择正确的 C 示例。

**Architecture:** 保留现有 Studyroom API 与资源授权层，增加独立编排契约、分步资源选择器和前端场景组件。生成采用规划与绑定讲解两轮有界模型调用；所有步骤的候选合并在第二轮请求中，避免每步单独请求模型。旧课堂通过读取适配器兼容。

**Tech Stack:** Java 21、Spring Boot、LangGraph4j、JdbcTemplate/Flyway、Vue 3、TypeScript、Vitest。

---

关联设计：`docs/superpowers/specs/2026-10-18-studyroom-resource-orchestration-design.md`。路径均相对 G:/Code/Structify。

## 执行约束

- 当前 Studyroom 主体和多项相关改动尚未提交。开始执行时保存 git status、diff 与相关未跟踪文件清单到 private/run-logs；在当前工作区增量修改，不能以新 worktree 默认分支代替当前内容，不能提交其他已有改动。
- 各任务先补行为测试、确认失败原因，再实现并验证。只对本任务的补丁创建提交；与已有改动混杂的文件不得整文件盲目 stage。若无法安全拆分，保留改动并报告，不能丢失原工作。
- 不编辑已使用的 V26 迁移；执行时确认 V27 空闲，否则使用下一个迁移号。
- 不将模型配置密钥或私有完整教材写入日志、夹具或提交。

## 任务 1：编排契约与兼容读取

**文件：** 新增 `backend/spring/src/main/java/com/feng/dsagent/studyroom/StudyroomScene.java`、`StudyroomSceneAdapter.java`；修改同目录 `StudyroomView.java`；修改 `frontend/src/shared/types/studyroom.ts`；新增同后端测试目录 `StudyroomSceneAdapterTest.java`。

- [x] 写失败测试：无资源旧步骤转换为 TEXT；旧步骤多个同类 PPT 保留顺序；不同类资源形成主场景及补充场景；无效引用不变成可用引用。使用现有 StudyroomServiceTest 的记录构造方式，断言页 ID 顺序和旧 resources 保留。
- [x] 在 backend/spring 运行 `./mvnw.cmd "-Dtest=StudyroomSceneAdapterTest" test`，预期测试因新适配器不存在而失败。
- [x] 实现下列前后端同名契约。schemaVersion 为步骤编排版本，与 room.version 并发版本分离。URL、标题、可用性在服务端解析；模型只产出引用。

```ts
type StudyroomIntent = 'CONCEPT' | 'PROCESS' | 'IMPLEMENTATION' | 'EXPERIMENT' | 'CHECK';
interface StudyroomScene {
  id: string;
  kind: 'TEXT' | 'PPT' | 'ANIMATION' | 'C_CODE';
  purpose: string;
  items: Array<{ resourceId: string; explanation: string; startLine?: number; endLine?: number }>;
  codeMode?: 'READ' | 'EXPERIMENT';
  task?: string;
  stdin?: string;
  expectedObservation?: string;
}
interface StudyroomOrchestration {
  schemaVersion: 2;
  objective: string;
  intent: StudyroomIntent;
  primary: StudyroomScene;
  supplementary: StudyroomScene[];
}
```

- [x] 限制：每步最多 3 场景、每课件场景最多 12 页、每场景动画或代码只引用 1 个资源；不得用截断冒充完整选页，超限则重规划/修复。目的最长 200 字、逐页讲解 500 字、实验任务 1000 字；沿用现有总 JSON 上限并按实际模型预算收紧提示。
- [x] 重跑测试；确认旧资源不会丢失，旧版超过新生成限制的历史数据仍可只读展示。提交本任务补丁。

## 任务 2：持久化与授权解析

**文件：** 新增 `backend/spring/src/main/resources/db/migration/V27__studyroom_orchestration.sql`；修改 `StudyroomRecords.java`、`JdbcStudyroomRepository.java`、`StudyroomService.java`、`StudyroomResourceCatalog.java`；扩展 `StudyroomRepositoryIntegrationTest.java`、`StudyroomServiceTest.java`。

- [x] 写失败用例：新契约往返、NULL 旧数据适配、补充步骤往返、其他用户不可读、引用不在候选列表拒绝、非法代码行号拒绝。
- [x] 运行 `./mvnw.cmd "-Dtest=StudyroomRepositoryIntegrationTest,StudyroomServiceTest" test`，确认新增断言失败。
- [x] 新迁移内容如下；将 orchestrationJson 加入 Step 记录与所有 INSERT/SELECT/追加路径。保留现有 resources_json 作为解析过的资源表，避免复制素材本体。

```sql
ALTER TABLE study_room_steps ADD COLUMN orchestration_json LONGTEXT;
```

- [x] GET 时 NULL 使用任务 1 适配器；非 NULL 严格解析 schemaVersion。每个 scene item 必须引用该步骤的已验证 resources；PPT ID 验证真实页归属，代码 startLine/endLine 需在已有源码范围内且起止有序。
- [x] API 追加 orchestration 字段，不删除旧字段；前端字段设可选以兼容旧响应。重跑本任务测试并提交。

## 任务 3：按步骤检索与诊断

**文件：** 新增 `StudyroomResourceSelector.java`、`StudyroomSelectionDiagnostics.java`；修改 `StudyroomResourceCatalog.java`；新增 `StudyroomResourceSelectorTest.java`，扩展 `StudyroomResourceCatalogTest.java`。均位于现有 studyroom 主代码/测试目录。

- [x] 固定目录加入先序/中序代码、队列入队代码及无关示例。新增宽泛“二叉树”问题搭配“先序递归实现”目标、相近操作、章节映射及目录异常测试。
- [x] 运行 `./mvnw.cmd "-Dtest=StudyroomResourceSelectorTest,StudyroomResourceCatalogTest" test`，确认缺失选择器导致失败。
- [x] 检索 query 合并原问题、objective、步骤 title、算法和 operation；标题相关性权重保留现有 4 倍，代码纳入 sample.targets/sections/summary。先章节授权过滤再排序，稳定以 ID 打破同分；不以字符串前缀放宽章节权限。
- [x] 每步候选预算 PPT 12、动画 4、代码 6；跨步骤只传一次候选元数据，各步骤传候选 ID 集合。实现/实验步骤使用独立代码结果，概念步骤不硬配代码；PPT 结果按目录原始页序组成展示建议，不按相关性分数直接作为讲课页序。
- [x] 结构化日志记录 requestId、stepKey、type、候选数、选中 ID 与原因枚举 `NO_MATCH/CATALOG_ERROR/CANDIDATE_LIMIT/MODEL_NOT_SELECTED/VALIDATION_REMOVED/SELECTED`。零相关分与目录异常明确区分；只能确认截断时才记 CANDIDATE_LIMIT。
- [x] 重跑测试，确保样例选择正确而非只断言 C_CODE 数量。提交补丁。

## 任务 4：两阶段课堂生成

**文件：** 新增 `StudyroomLessonPlanner.java`、`StudyroomSceneBinder.java`；修改 `StudyroomGraph.java`、`StudyroomGraphState.java`、`StudyroomModelOutput.java`；扩展 `StudyroomGraphTest.java`、`StudyroomModelOutputTest.java`、`StudyroomGraphCompatibilityTest.java`。

- [x] 增加模型夹具：第一轮输出目标与 intent，第二轮选择候选并生成逐页/逐片段讲解；覆盖宽泛问题、无候选、错误 ID、输出截断和修复失败。
- [x] 运行 `./mvnw.cmd "-Dtest=StudyroomGraphTest,StudyroomModelOutputTest,StudyroomGraphCompatibilityTest" test`，记录新增行为失败。
- [x] 第一轮仅规划 4–8 个重点步骤；纯简短事实问题允许 1 步。第二轮一次绑定整课，使用任务 3 的逐步白名单。示例中主资源以有序页引用表达，不放入动画 parameters。

```json
{"schemaVersion":2,"objective":"理解先序遍历的递归实现","intent":"IMPLEMENTATION","primary":{"id":"implementation","kind":"C_CODE","purpose":"观察根左右对应的调用顺序","items":[{"resourceId":"candidate-code-id","explanation":"先处理根，再递归访问左右子树。"}],"codeMode":"READ"},"supplementary":[]}
```

- [x] 第二轮只能引用每步候选集合。实现类有直接匹配代码却遗漏时，进行一次带具体错误的修复；修复无效则返回诚实文字降级并记诊断，不强行绑定不相关代码。删除资源时重新生成/修正依赖它的说明，不能保留悬空“如图”。
- [x] 使用现有 modelOutput 配额、超时及输出长度机制；两轮使用同一请求关联标识和不同 operation，核查计费去重不会误吞第二轮。最多一次修复，总调用上限 3，失败不保存半堂课。
- [x] 重跑测试，确认授权证据、所有权和输出预算原断言仍成立。提交补丁。

## 任务 5：追问与开发资源统一

**文件：** 修改 `StudyroomController.java`、`StudyroomService.java`、`StudyroomGraph.java`、`frontend/src/user/api.ts`、`frontend/src/user/studyroom-mock.ts`；扩展 `StudyroomApiIntegrationTest.java`、`frontend/src/user/api.spec.ts`、`frontend/src/user/studyroom-mock.spec.ts`。

- [x] 测试普通追问不修改已保存场景；补充实现步骤调用同一选择器；其他步骤资源 ID 或伪造代码状态不可作为当前资源使用。
- [x] 追问请求增加可选 context，旧客户端只有 question 仍工作：

```ts
interface StudyroomFollowUpContext {
  stepId: string;
  sceneId: string;
  resourceId?: string;
  codeMode?: 'READ' | 'EXPERIMENT';
  runResult?: { stdout: string; stderr: string };
}
```

- [x] 后端验证 context 归属，限制 runResult 每字段 4000 字符，将其作为不可信数据。没有 context 使用服务器 currentStepId；不自动附送编辑器全部源码。补充步骤走规划/绑定契约，普通回答不重生成其他步骤。
- [x] 去除 attachMockResources 在课堂 API 返回后添加资源的行为；开发 fixture 由后端目录提供，生产与开发使用同一授权/绑定逻辑。保留独立播放器测试所需 mock helpers，不扩散移除范围。
- [x] 执行后端 `./mvnw.cmd "-Dtest=StudyroomApiIntegrationTest" test`，前端 `npm test -- src/user/api.spec.ts src/user/studyroom-mock.spec.ts`；通过后提交。

## 任务 6：场景主舞台与多页 PPT

**文件：** 新增 `frontend/src/user/components/studyroom/StudyroomSceneStage.vue`、`StudyroomSlideScene.vue` 及同名 `.spec.ts`；修改 `frontend/src/user/views/StudyroomView.vue`、`StudyroomView.spec.ts`；新增 `frontend/src/user/studyroom-scenes.ts`、`studyroom-scenes.spec.ts`。

- [x] 测试纯文字没有资源空框、仅显示本步补充入口、多页顺序/讲解、旧 API 适配、快速切页旧错误不覆盖新图。
- [x] 运行 `npm test -- src/user/views/StudyroomView.spec.ts src/user/studyroom-scenes.spec.ts src/user/components/studyroom/StudyroomSlideScene.spec.ts`，确认新行为失败。
- [x] 视图只维护课堂与对话；场景组件管理主/补充选择；课件组件管理页位置、加载和显示。所有状态按 stepId/sceneId 隔离。

```css
.studyroom-slide-image { display: block; width: 100%; height: 100%; object-fit: contain; }
.studyroom-slide-viewport { overflow: auto; touch-action: pan-x pan-y; }
```

- [x] 提供上一页/下一页、页集合计数及原始页码、缩略图目录、放大/缩小/恢复整页和全屏。缩略图用现有授权图片接口；缩放模式使用实际增大的画布支持滚动拖动，不能只 transform 导致内容不可达。全屏失败保留页内放大；Escape 可退出。
- [x] 图片错误与重试按页 ID 管理；主说明与页说明层次分明，不重复输出相同文字。键盘按钮有名称，当前页缩略图有 aria-current；切页不主动抢走输入焦点。
- [x] 重跑前端目标测试并 `npm run typecheck`。提交补丁。

## 任务 7：代码讲解与实验

**文件：** 新增 `frontend/src/user/components/studyroom/StudyroomCodeScene.vue`、`StudyroomCodeScene.spec.ts`；修改 `StudyroomSceneStage.vue`、`StudyroomView.vue`。

- [x] 测试 READ 初始只读片段、展开完整源码、EXPERIMENT 的任务/输入、切换步骤保留不同草稿、旧运行响应不覆盖新样例、运行服务缺失仍能看源码。
- [x] 运行 `npm test -- src/user/components/studyroom/StudyroomCodeScene.spec.ts` 确认失败。
- [x] 使用既有 sample.code/stdin/expectedStdout，不生成替代源码；READ 显示行号和已有片段。实验沿用 userApi.runCode；运行中以递增 request token 丢弃过期结果，草稿 Map 以 stepId/resourceId 为键存于课堂页面会话。
- [x] 无精确输出时展示 expectedObservation，不标为自动判题成功；服务器配置错误与编译/运行错误分别呈现。动画继续复用 AnimationPlayer，隐藏场景时停止播放/释放实例。
- [x] 重跑测试和 `npm run typecheck`；提交补丁。

## 任务 8：集成验收与交付

**文件：** 扩展 `StudyroomApiIntegrationTest.java`、`StudyroomGraphTest.java`、`StudyroomView.spec.ts`；更新 `backend/spring/README.md` 和 `contracts/openapi-v1.yaml`；新增 `docs/project/studyroom-resource-orchestration-acceptance-2026-10-18.md`。

- [x] 固定夹具验收三题：“什么是二叉树”“讲讲二叉树并解释先序遍历的递归实现”“修改递归调用顺序会怎样”；分别验证概念不强配代码、实现选择正确样例、实验任务完整。
- [x] 在 backend/spring 运行 `./mvnw.cmd test`；在 frontend 运行 `npm test`、`npm run typecheck`、`npm run build`。记录退出码与失败归属；已有失败与新增回归分开列出，不降低断言来通过。
- [x] 在已运行本地页面重启服务后验证桌面和 390px 窄屏：多页阅读、放大拖动与恢复、全屏退出、代码运行/配置失败、步骤切换草稿、追问与补充、旧课堂打开；截图保存在 private/run-logs，不提交私有教材截图。
- [x] 已配置模型时对上述同题各做一次真实调用，记录逐步候选及绑定结果；模型未配置则明确标记真实模型验收未执行，不伪造成功。无沙箱时保留真实运行未验证说明。
- [x] 自查 spec 每条验收均有结果，更新接口契约、配置和诊断说明，检查 `git diff --check`。交付修改摘要、验证证据及未验证项；只提交本次工作补丁，不发布生产。

## 计划自审

覆盖映射：资源冗余→任务 1/4/6；多页阅读→任务 1/2/6；C 检索→任务 3/4/7；旧数据→任务 1/2；授权及失败→任务 2/3/4/7；追问与开发统一→任务 5；验证与诊断→任务 3/8。schemaVersion、objective、intent、primary、supplementary 命名跨任务一致；room.version 不复用。没有自动讲述或高亮等第二期实现任务。


## 执行记录（2026-10-18）

功能与验收任务已完成，证据见 `docs/project/studyroom-resource-orchestration-acceptance-2026-10-18.md`。分任务提交动作按本计划“无法安全拆分则保留改动”的约束跳过：现有 Studyroom 基础文件原本未跟踪，不能整文件提交为本次新增工作。

根据真实模型验收调整：绑定输出改为扁平选择并由服务端组装场景；去除第一条知识命中导致的隐式章节冻结；补充命名算法主题排序、真实源码上下文、默认/任务特定输入、单个实验数量约束和授权候选中的同课件邻页复用。外部 schemaVersion=2 契约与已批准范围保持一致。真实代码执行受本地沙箱未配置限制，已验证错误呈现并明确记录，未伪造运行通过。
