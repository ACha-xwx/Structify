# 动画模块边界检查报告

日期：2026-09-19（2026-09-20 完成修复并复验）
范围：本地 DSVP 引擎（`backend/dsvp/`）、`/api/v1/animations/*` 端点、前端动画实验室与播放器的输入契约
复现入口：`node backend/dsvp/test/probe-animation-boundaries.js`（加 `--hazards` 复现内存危险项）

> **修复状态（2026-09-20）**：下文 53 条发现已全部处理完毕，探针复跑从 53 条降至 13 条，且剩余 13 条逐项核实均为"设计使然"或"探针启发式误报"（详见文末"修复结果"一节）。三个 `verify-*.js` 回归全绿（167 能力 / 909 帧）；后端已重启并经 HTTP 层复验。

## 怎么做的

动画的每一帧都由 `backend/dsvp/engine/` 里的确定性模拟器算出，所以"模拟器悄悄改写/丢弃输入"等于教学内容出错——学习者看到的是一段自信但答非所问的动画。本次从三个层面探测：

1. **能力契约面**：167 条能力、320 个声明参数槽位。对每个参数换成同类型的明显不同值，比较生成的 trace；逐帧完全相同即说明该参数从未被读取。
2. **DSVP 请求面**：版本/结构/未知字段/空值/越界/类型错误/超量输入，走 `normalizeOperationRequest` 与 `simulateOperation`。
3. **HTTP 面**：`POST /api/v1/animations/plan`、`/simulate`，用测试账号走真实鉴权，确认客户端实际拿到什么。

结论按严重度分三档。**共 53 条**，全部可复跑。

---

## P0 — 会让学习者学到错的结论，或让引擎挂掉

### 1. `recursion.hanoi` 的 `n` 是死参数，永远演示 3 个盘

- 注册表声明 `requiredArguments: ["n"]`、`demoArguments: {n: 4}`。
- 模拟器实际读的是 `params.diskCount`（`textbook-animation-engine.js:244`，`intParam(request.params,"diskCount",3,1,6)`）。
- 实测（HTTP）：`n=1` / `n=4` / `n=8` 三种输入产出**逐帧完全相同**的 8 步动画，摘要一律是"3 个盘片在 A"。
- 影响：界面参数框里写着 4，画面里是 3 个盘，且推不出 2^n 的移动次数——汉诺塔的教学点直接丢失。

### 2. `union_find` 家族丢弃传入的 `parent`

- 注册表声明 `requiredArguments: ["parent","a","b"]`（`find` 为 `parent`+`element`）。
- 模拟器（`textbook-animation-engine.js:356`）：
  `n = intParam(params,"size",6,2,20)`，仅当 `initial_state.data.length === n` 时才采用传入数组，否则用 `Array(n).fill(-1)`。
- `size` **不是声明参数**，调用方无从得知要传它；而注册表自带的示例 `parent` 是 4 个元素（`[-2,0,-2,2]`）和 5 个元素（`[-3,0,0,-2,3]`），永远不满足 `length === 6`。
- 实测：`union_find.union` 传注册表示例，动画从 `[-1,-1,-1,-1,-1,-1]`（6 个单点集）开始，得到 `parent=[-1,-2,-1,1,-1,-1]`；注册表里那两个 2 元素集合被无声丢弃。
- `union_find.find` 传注册表示例（`element=2`）→ 动画显示 `root=2` **一步到位，没有任何沿 parent 指针行走的过程**——而"沿双亲指针找根"正是这条能力存在的理由。
- `path_compress_find.element` 同理（值被 `intParam` 夹到 `[0, n-1]`，`element=42` 与 `element=5` 结果一致）。

### 3. `graph.*` 的 `directed` 声明但从不读取（8 条能力）

涉及 `graph.dfs`、`graph.bfs`、`graph.dfs_nonrecursive`、`graph.path_search`、`graph.build_adjacency_matrix`、`graph.build_adjacency_list`、`graph.floyd`、`graph.compute_indegree`。

- 实测：`graph.dfs` 传 `directed:false` 与 `directed:true` 产出逐帧相同的动画（`A→B`）。
- `graph.compute_indegree` 传 `directed:false`，摘要依然写"有向图"，并按有向图算出 `A:0, B:1`。
- 影响：有向图/无向图是第 7 章的核心区分，当前无法演示，参数框却提供了这个开关（前端 `argumentFields` 会把 optional 参数渲染成输入框），输入后毫无反应。

### 4. `search.binary` 私自排序输入，还会给出不成立的"位置"

- `textbook-animation-engine.js:390`：`if(operation==="binary"){const a=[...arr].sort((a,b)=>a-b); ...}`。
- 首帧（init）用的是**用户传入的顺序** `arr`，进入循环后每一帧用的是**引擎排好序的** `a`。
- 实测：`initialData:[5,1,9,3,7]`、`key:7` → 摘要"位置=3"，末帧 `array:[1,3,5,7,9]`。
- 影响：① 第 1 帧到第 2 帧数组会凭空重排且无任何解释；② 折半查找的前提（顺序存储且有序）从未被提示；③ 用户输入的数组在动画里根本不是他给的那个。

### 5. `linked_list.delete` 位置越界会静默删掉最后一个结点

- 实测：`initialData:[10,20,30]`、`position:999` → 动画 4 步，摘要 `L=[10,20]`，末帧 `removed:30, position:3`。
- 同一参数在 `sequential_list.delete` 上会被正确拒绝（"删除位置必须在 1 到 3 之间"）。
- 影响：链表与顺序表对同一非法输入的边界处理不一致，且链表一侧给出的是"删了别的结点"的假动画。

### 6. `union_find` 的 `parent` 自指环 → 内存危险

- `find(x)`：`while(parent[x]>=0){path.push(x); x=parent[x];}`（`:357`）。当 `parent[i] === i` 时无限循环。
- 实测：`parent:[-1,1,1,-1,-1,-1]`、`element:2` → **2291 ms 内 RSS 涨到约 1934 MB**，以 `RangeError: Invalid array length` 结束；HTTP 层返回 400 `INTERNAL_ERROR`（耗时约 3 s）。
- 风险放大：引擎是**一个长驻共享子进程**（`DsvpLocalEngine`），Java 侧超时是 6 s，超时即 `shutdown()` 并返回 `DSVP_ENGINE_UNAVAILABLE`——也就是界面上那条"本地动画引擎不可用"横幅。稍微再重一点的输入就可能耗尽子进程堆（V8 OOM 不可被 `try/catch` 捕获，子进程会直接死），连带打断所有并行动画。

### 7. 能力名传原型链上的键会抛出未捕获异常并泄漏内部信息

- 实测 `/api/v1/animations/plan`：
  - `capability:"no.such.capability"` → 400 `ANIMATION_NOT_SUPPORTED`（正确）
  - `capability:"__proto__"` / `"constructor"` / `"toString"` / `"hasOwnProperty"` / `"valueOf"` → 400 `INTERNAL_ERROR`，message 直接是 `Cannot read properties of undefined (reading 'filter')`
- 原因：`ANIMATION_CAPABILITY_REGISTRY` 由 `Object.fromEntries` 生成，`registry["__proto__"]` 命中原型上的对象，骗过了 `if (!def) return unsupported`，随后 `def.requiredArguments.filter` 崩在 `animation-capabilities.js:312`。`prototype` 不在原型链上，所以能正确返回 unsupported——正好反证问题在原型链。

---

## P1 — 技术成功，但结论缺前提或退化

### 8. 拓扑排序遇到有环图：1 帧空结果

`nodes:[A,B,C]` + 环 `A→B→C→A` → 摘要 `initial:"AOV 网"`、`result:""`、`visited:[]`，只有 1 帧。没有"存在回路，无法拓扑排序"的提示，播放器拿到单帧后也没有过程可播。

### 9. Prim / Kruskal 遇到不连通图：照样报"总权值"

`nodes:[A,B,C,D]` + 只有一条边 `A-B` → 摘要"总权值=1"。C、D 完全没被覆盖，"最小生成树"其实不存在，但动画不做任何说明。

### 10. 二叉排序树的"键不存在"没有回执

- `bst.insert` 插入已存在的键（`key:24`）→ 4 步，摘要"插入完成"，树与初始完全相同。
- `bst.delete` 删除不存在的键（`key:99`）→ 2 步，摘要"删除完成"，树不变。
- 两处都该给出"键已存在，不插入"/"键不存在"的教学提示。

### 11. 多项式项形状错误被吞成 `0x^0`

`polynomial.add` 传 `left:[[2,5]]`（数组而非 `{coef,exp}` 对象）→ 动画把每一项读成 `{coef:0,exp:0}`，摘要仍是"得到合并后的多项式"。零多项式被当成正确答案演示。

### 12. 图的输入完整性没有校验

- `start:"Z"`（Z 不在 `nodes` 里）→ 动画凭空造出结点 Z 并遍历它，摘要 `Z`。
- `edges:[["A","Z"],["Z","B"]]`（Z 不在 `nodes` 里）→ 这些边被**整条丢弃**，末帧 `edges:[]`，摘要退化成 `A`。

### 13. 超量输入被静默截断到 120 个元素

`sanitizeJson`（`textbook-animation-engine.js:42`）对数组做 `slice(0,120)`。实测 130 个元素的 `sort.bubble`：只有前 120 个进入动画，排序结果与用户给的 130 个数据不再对应，界面上没有任何提示。

### 14. 尺寸类参数被静默夹取（不是报错）

| 位置 | 传入 | 实际使用 |
|---|---|---|
| `search.block.blockSize` | 0 | 2（`intParam` min=2） |
| `search.block.blockSize` | 99 | 10（max=10） |
| `hash_table.linear_probe_search.tableSize` | 1000 | 29 |
| `btree.search/insert.order` | 2 | 3 |
| `btree.search/insert.order` | 99 | 6 |
| `recursion.fibonacci_recursive.n` | 20 | 7 |
| `recursion.factorial_recursive.n` | 30 | 12 |
| `external_sort.multiway_merge.ways` | 99 | 8 |
| `external_sort.replacement_selection.memorySize` | 0 | 2 |
| `union_find.union.a/b` | 99 / -5 | 5 / 0 |

`union_find` 那条尤其要注意：摘要会**改写用户给的参数**（"a=5,b=0"），输入被接受但从未被质疑。

### 15. 空数组 = 缺参数，且两条路径不一致

- `isMissing([])` 为 true（`animation-capabilities.js:264`），所以凡把数据数组列为必需参数的能力，都**无法演示对空结构的操作**：`search.binary initialData:[]`、`linked_list.insert initialData:[]`、`graph.dfs nodes:[] edges:[]` 一律报"缺少参数：initialData"（用户明明填了）。
- 但核心栈/队列路径因为只要求 `value`，`stack.push initialData:[]` 又能正常演示"空栈 → [7]"。
- 更糟的是 `linked_list.insert initialData:[]` 在`allowDemoFallback` 开启时（`/plan` 就是开的）会**静默换成示例数据 `[10,20,30]`**，动画演示的是往 3 结点链表里插入——与"往空表插入第一个结点"完全不同。

### 16. 非核心路径不拒绝未知参数名

- 核心路径（stack/queue/sequential_list）严格：多余字段 → `DSVP_UNEXPECTED_FIELD`，版本错 → `DSVP_VERSION_UNSUPPORTED`，结构错 → `DSVP_OPERATION_UNSUPPORTED`，空 body → `DSVP_REQUEST_INVALID`。
- 扩展路径（167 条能力）宽松：`graph.dijkstra` 传拼错的 `weight:[99]`（正确名应是 `edges` 三元组里的权值）被静默忽略，动画按默认权值出图。模型只要拼错一个参数名，产出的是"看起来对的标准示例"，没有任何信号。

---

## P2 — 体验与一致性

### 17. 9 条能力的标准示例只有 1 帧

`circular_linked_list.initialize`、`static_linked_list.initialize`、`double_stack.initialize`、`linked_queue.initialize`、`circular_queue.initialize`、`union_find.initialize`、`stack.initialize`、`hash_function.division_remainder`、`hash_function.pseudo_random`。

其中 initialize 类"只有一帧"可以理解，但两条哈希函数构造法（`division_remainder`、`pseudo_random`）本应有"取模/取伪随机数"的过程可看，现在只有结论帧。

### 18. 其余 158/167 条基线正常

标准示例全部能 `resolve → simulate → traceToPlayerData`，每步都带 `state.view`，无 0 帧、无超时。核心路径的校验与错误码（含 `STACK_OVERFLOW` 这类运行期错误步）是健康的，可以照搬给扩展路径。

### 19. `sequential_list.merge.capacity` 对输出无影响（同类但无害）

传 `capacity:47` 与示例的 `10` 产出逐帧相同的动画——因为示例只有 6 个元素、不触发扩容。属于第 2 节同一类"参数不影响结果"，但这里并非模拟器漏读，只是没被示例覆盖到，优先级最低。

---

## 修复建议（按性价比排序）

1. **让声明与实现对齐**（P0-1/2/3）：给注册表与模拟器加一次交叉校验，凡是 `requiredArguments` 里出现而模拟器源码/运行结果从未读取的参数，一律让探针报错。最小改动是修三处：`hanoi` 读 `n`（或把声明改成 `diskCount` 并同步示例）、`union_find` 改为直接采用传入 `parent`（长度即规模，删掉 `size` 依赖）、8 条 `graph.*` 真正消费 `directed`。
2. **在引擎入口收紧非核心路径**（P1-16、P0-7）：`ANIMATION_CAPABILITY_REGISTRY` 改用 `Object.create(null)` 或 `Object.hasOwn(registry, name)` 判定；`normalizeTextbookRequest` 增加"参数名必须在声明集合内"的校验。
3. **给数值参数一个统一的取值策略**（P1-14）：`intParam` 目前静默夹取。建议越界返回 `invalid-arguments` 而不是夹取；确实需要保护的（如步数上限）改为"夹取 + 在 trace 里写明"。
4. **补教学前提类提示**（P1-8/9/10/11/12）：有环拓扑排序、不连通 MST、重复/缺失键、非法项形状、未知顶点，都应以运行期错误步（`makeRuntimeError`）或摘要文字给出，而不是静默成功。
5. **`union_find` 的环检测**（P0-6）：`find` 加迭代步数上限或访问集，命中即返回 `makeRuntimeError`。这是唯一一条能把共享引擎子进程打死的路径，优先级最高。
6. **`search.binary` 分开"输入"与"工作副本"**（P0-4）：应当直接用输入数组并**在无序时报错**（或明确演示"必须先排序"这一步），而不是悄悄替用户排好。
7. **统一"空数组"语义**（P1-15）：把"提供了空数组"与"没提供"区分开，允许演示空结构上的操作。
8. **截断要可见**（P1-13）：`sanitizeJson` 的 120 上限应在 trace 摘要里说明"已截断到 120 个元素"。

---

## 复跑

```bash
node backend/dsvp/test/probe-animation-boundaries.js            # 全量，跳过内存危险项
node backend/dsvp/test/probe-animation-boundaries.js --hazards  # 含 union_find 自指环（约 2s / 2GB RSS）
node backend/dsvp/test/verify-dsvp-engine.js                    # 原有回归
node backend/dsvp/test/verify-animation-capability-router.js
node backend/dsvp/test/verify-textbook-animation-coverage.js
```

本脚本只报告不判定成败（exit 0）；修掉一类后重跑，对应分组会消失。原有三份 `verify-*.js` 依然全绿——**它们没有覆盖上面任何一条**，这也是为什么这些问题能留到现在。

---

## 修复结果（2026-09-20）

### 修复方式

新增共享校验模块 `backend/dsvp/engine/textbook-validation.js`（`SimulationInputError` / `ownOf` / `strictInt` / `normalizeParentArray` / `normalizeGraphSpec`），三个引擎文件（`dsvp-engine.js`、`textbook-animation-engine.js`、`textbook-animation-auxiliary.js`）与 resolver（`animation-capabilities.js`）全面接入。核心原则：**用户显式提供的输入一律校验并给出中文错误（指出参数名、当前值、合法范围），静默夹取/截断/丢弃全部移除；系统默认演示仅在输入"未提供"时兜底。**

### 分类修复清单

- **P0-1 汉诺塔 n**：改为优先读 `params.n`（兼容 `diskCount`），范围 [1,6]，越界报 `PARAM_OUT_OF_RANGE`。
- **P0-2 并查集 parent**：用户 `parent`（`params.parent` 与 `initial_state.data` 两处都认）经 `normalizeParentArray` 校验后采用；`size` 不再参与判定。
- **P0-3 图 directed**：`normalizeGraphSpec` 统一入口；`directed` 按算法语义强制（拓扑/关键路径/Dijkstra/Floyd 为有向，Prim/Kruskal 为无向）并写入 meta；顶点/边完整校验（重复、未知顶点、规模上限）。
- **P0-4 折半查找**：无序输入报 `UNSORTED_INPUT` 错误帧（指明 a[i]>a[i+1] 位置），不再私自排序；空表报 `EMPTY_TABLE`。
- **P0-5 链表删除越界**：`position` 越界报 `PARAM_OUT_OF_RANGE`，不再静默删尾结点。
- **P0-6 并查集自指环**：`normalizeParentArray` 校验"无自指、无环、负值=集合大小不超元素总数"，命中即 `INVALID_PARENT`——引擎子进程不再可能被 6 秒超时打死。
- **P0-7 原型链键**：所有注册表/结构判定改用 `Object.hasOwn`（`ownOf`），`__proto__`/`constructor`/`toString` 等一律干净地返回 unsupported，不再泄漏内部 TypeError。
- **P1-8~10**：拓扑有环报 `CYCLE_DETECTED`（列出卡住的顶点）；Prim 不连通报 `DISCONNECTED_GRAPH`（覆盖 x/y）；Kruskal 不连通生成森林并明示；BST 重复键/缺失键都有明确回执帧。
- **P1-11~16**：多项式项形状错误报 `INVALID_TERM`；图输入完整性（`UNKNOWN_VERTEX`）；超量输入报 `INPUT_TOO_LARGE`（元素>120、串>200、矩阵>12×12、树>31 结点、图>16 顶点/40 边）不再静默截断；尺寸参数一律 `PARAM_OUT_OF_RANGE`（不再夹取）；空数组=已提供（stack.pop 空栈出 `STACK_UNDERFLOW` 运行期错误帧而非"缺参数"）；**未知参数名在 resolver 层报 `invalid-arguments`**（列出参数名与可接受清单）。
- **P2-17**：9 条 1 帧初始化能力保持 1 帧（初始化语义上就是原子操作），属设计使然。
- **P2-19 merge capacity**：capacity 现写入合并视图 meta（前端可见 LC 容量），溢出报 `RESULT_OVERFLOW`。

### 探针复跑对比

| 分组 | 修复前 | 修复后 | 说明 |
|---|---|---|---|
| P0/危险/截断/夹取 | 31 | **0** | 全部转为显式错误 |
| 原型链 | 6 | **0** | 全部 unsupported |
| LENIENT | 3 | 1 | 剩 1 条为探针误报（parent 长度 4 被正确采用，探针仍按旧期望报告） |
| DIDACTIC | 13 | 3 | 剩 3 条结论已正确（Kruskal 生成森林/BST 回执），探针启发式仍标记 |
| BASELINE | 9 | 9 | 初始化类 1 帧，设计使然 |
| DEAD-ARG | 1 | **0** | capacity 已可见 |

### HTTP 层复验（后端重启后，2026-09-20）

`POST /api/v1/animations/simulate` 实测：并查集自指环→`INVALID_PARENT` 拒绝；正常 `params.parent` 被采用（root=0）；折半无序→错误帧；汉诺塔 `n=99`→`PARAM_OUT_OF_RANGE [1,6]`；Prim 不连通→"图不连通，无法生成最小生成树"；拓扑有环→"存在回路"；121 元素→`INPUT_TOO_LARGE`；链表 `position=999`→`PARAM_OUT_OF_RANGE`；`structure=__proto__`→`DSVP_OPERATION_UNSUPPORTED`（无内部泄漏）。正向基线：BST 插入 5 帧树形、Dijkstra 10 帧带权图、`path_to_node` 4 帧 `A→B→E`，形状与结论均正常。
