# 队友改动交叉核对（2026-10-05）

> 依据：另一位队员给出的 10 条总结 + 我在 `v2-baseline`（他的 v2.0.0 树）上逐条读代码核对。
> **本文只核对，不含改动。** 标注「✅ 一致 / ⚠️ 部分一致 / ❌ 与实现不符 / ➕ 顺带发现」。

---

## 一、逐条核对

| # | 他的说法 | 代码里的实际情况 | 判定 |
|---|---|---|---|
| 1 | 旧首页移到 `/begin`，新首页以后还是做成主页 | `router/index.ts:22-23`：`/` → `LandingView`（无鉴权）、`/begin` → `HomeView`（要鉴权）。`/user` → `UserHomeView`（目前只写"待建设"） | ✅ 一致 |
| 2 | 吉祥物：十余种待机表情、视线追随鼠标、点击旋转（旋转有 bug） | `shared/components/AiBall.vue`：`expressionChoices` 共 **9 种**（curious / look-around / surprised / happy / shy / sleepy / wink / thinking / peek）+ `idle` = **10 种**，每 6.5s 轮换；`followPointer` 用 `pointermove` **只写 CSS 变量、不触发聊天树重渲染**（这点做得对）；点击旋转 `spinning` + `spinToken`，时长 900ms | ✅ 一致（"十余种"实际 10 种） |
| 3 | 一部分按钮换银边框、一部分换玻璃 | `admin/components/LiquidMetalButton.vue`（613 行，`variant` 支持多形态）+ 各页面用 `library__glass` 这类玻璃类名 | ✅ 一致 |
| 4 | 课堂：改 UI 布局/下拉样式；课件**读取原理改成以本地缓存为主**，优先加载邻近页，改 webp，单张 30–70KB | UI：`ClassroomView.vue` 大改（-/+ 与我们的差异 100+ 行）。加载：**没有应用级缓存层**——全前端搜不到 `serviceWorker` / `caches.` / `indexedDB`；实际是「内容寻址 URL（可永久缓存）+ 浏览器 HTTP 缓存 + `prefetch-slides.ts` 预取邻近页（AHEAD=4 / BEHIND=1）」。webp 在 `PresentationCatalog.java:470` 优先取 `.webp` 兄弟文件 | ⚠️ **部分一致**：缓存是**浏览器缓存**，不是"本地缓存为主"；而且 webp / 预取 / 内容寻址**在我们 v1.0.x 就已有**（`build-slide-webp.sh`、同一套 prefetch 常量），属于"沿用并生效"，不是这轮新造 |
| 5 | 课件页：侧栏样式、分类、下拉、"选中当前课件"按钮（点击后返回并切到刚选的课件） | `CoursewareView.vue`：`selectedCoursewareDeckId` + `localStorage["structify.courseware.selected"]` + `:aria-pressed` 标记当前选中 | ✅ 一致 |
| 6 | 动画页：侧栏分两部分并做适配 | `AnimationLabView.vue:272-333`：`lab__panel-nav` 两个 tab（`structured` 结构化选择 / `prompt` 一句话），各自 `lab__panel-content` | ✅ 一致 |
| 7 | 编译器：三级分类、多级下拉、导入/导出、点变量看定义行、点函数看定义/来源库/传参、彩色区分、报错提示、查找替换 | `library-catalog.ts`：**三级结构**（`group: samples/examples/templates` → `Chapter` → `Topic`，含 `aliases` 中文别名）；`CodeEditorView.vue`：`importCode` / `exportCode` / `newBlank` / `loadExample` / `reset` / `restore`；`CCodeEditor.vue`：CodeMirror 6 全套（lint 报错、search+replace、autocomplete、fold、`formatCCode` 格式化）；符号能力在 `editor-symbols.ts` / `standard-symbols.ts` | ✅ 一致（"来源库/如何传参"这一层我**没有逐行验证**，只确认了符号表与 hover 机制存在） |
| 8 | chat 几乎重构：模型选择、深度思考开关、思考强度、文件/照片上传、skill 入口（未导入）、消息时间/编辑重发/复制、思考链与回答分区分流、先检索再思考、**思考上限 65536**、打字机与思考链同步、Markdown、代码框一键导入 C 编辑器/复制/下载、回复时间/复制/重试、侧栏收缩/重命名/置顶/删除（对标 deepseek） | 全部找到对应实现：`ChatComposer.vue`（模型菜单 / thinking 开关 / `reasoningEffort` 低-中-高-最高 / 附件 / `skillOpen`）；`ChatView.vue`（`editingMessageId`+`editingSnapshot` 编辑重发、`retry`+`retryMessageId`、`pendingDelete`、`beginRename`、`sidebarCollapsed`、`pinnedSessions`）；`ChatCodeBlock.vue`（复制/下载/分享到编辑器）；`ChatReasoning.vue`（思考链 + 检索来源分块）；后端 `V30__expand_deepseek_flash_thinking_budget.sql`：**只**把 `deepseek` + `deepseek-flash` 的 `max_output_tokens` 从 32768 抬到 **65536**（保留更小值与其它模型/供应商不变，写得很克制） | ✅ 一致 |
| 9 | 优化打开速度：按需加载 | `router/index.ts` 全部改 `() => import(...)`；同一个文件在 `beforeEach` 里**并行预取目标页 chunk**（不等 `/me` 鉴权返回） | ✅ 一致（而且这个并行预取是加分项） |
| 10 | 改了一些标题/副标题文案 | `shared/i18n/messages.ts` **+103 / -10** | ✅ 一致 |
| — | 改名「数筑 · Structify」 | `shared/brand.ts` → `BRAND_NAME = "数筑 · Structify"`，`<title>` 变成「首页 \| 数筑 · Structify」 | ✅ 一致 |
| — | skill 只占位（deepseek 流式对 skill 适配不好，以后再说） | `ChatComposer.vue:242`：Skill 子面板渲染「**暂无Skill** / No Skills yet」；"+" 按钮的 `aria-label` 也写了"添加附件或 Skill" | ✅ 一致（文案是诚实的，不会骗用户"能用"） |

---

## 二、影响：这件事与我们手上 10 条问题的关系

他这轮**没有碰**下列目录（逐文件哈希/内容一致）：`scripts/`、`contracts/`、`backend/node/`、`.github/`、`tools/`，
也**没有碰课堂备课链**（`ClassroomPreparation` / `ClassroomModelJson` / `SlideSpinePlan` 相对我们那版无改动）。

⇒ 结论：**你报的第 2/6 条（左边讲的内容和 PPT 对不上）依然是原样**。它的根因在三个地方，他这轮一处都没触及：

1. **课件数据层**：1210 页里 96 页标题就是「学校简介」（PPT 母版框被当标题）、41 页空、21 页"谢谢大家"，而 `shouldShow=false` 只标了 31 页；
2. **提示层**：给模型的页面清单里十几页同名，模型根本不知道这页画什么，只能按该节教材内容写；
3. **校验层**：服务端只钉 `slideRefs` 与页序，**不校验"讲稿讲的是不是这一页"**。

所以**批次 1（讲台可信）仍然是第一优先**，而且必须落在 `v2-baseline` 上（不是我们那条旧分支）。

---

## 三、我额外看到的四件事（顺带发现，供你判断）

1. **"本地缓存为主"这个说法要收一收**：页面图吃的是浏览器 HTTP 缓存 + 永久可缓存的内容寻址 URL。它有效，但它**依赖域名/CDN 不变、且用户不清缓存**；换域名或加 CDN 规则时要重新验一遍。真要"应用级本地缓存"得加 Service Worker 或 IndexedDB——可以作为后续项，不急。
2. **思考链 64K 的代价**：`max_output_tokens` 抬到 65536 后，单次最长回复的 token 消耗会显著上升；我们有 `ai_quota_*` 三张表在记账，建议上线一段时间后看一眼实际消耗曲线，别把配额打穿（配额是 admin 配置优先于 env）。
3. **Skill 入口现在是空面板**：文案诚实（"暂无Skill"），但入口摆在那儿会有人点。你说的"deepseek 流式对 skill 适配不好"要真做，得先把流式与 skill 的执行时序理顺；在那之前，把它做成**禁用态**比"点开说暂无"更省心。
4. **吉祥物旋转 bug**（你说你来修）：`spinning` + `spinToken` + 900ms 定时器这一套，容易在**连续点击/组件卸载**时留下错位的 transform 或未清的 timer——修的时候顺手看一下 `onBeforeUnmount` 有没有把 `spinTimer` / `expressionTimer` 都清掉。

---

## 四、下一步（不变）

本地基线已可验证（后端 491 / 前端 595 全过），按"先解决问题"开 **批次 1**：

1. 生成**课件"非教学页"质检清单**给你过目（只出清单，不动代码）；
2. 给模型的"这一页"换成**页面原文摘要 + 术语**，不再用被污染的标题；
3. 加"讲稿是否真在讲这一页"的模型复核，不一致只重写这一步；
4. 前端加"回到当前页"按钮 + 把"本步依据第 X 页"做成可核对。
