# 本地 DSVP 引擎（动画实验室的"本地脚本"半边）

## 这份代码从哪来

移植自旧原型的独立实现 `F:\data-structure-agent\data-structure-agent\`（该目录即当时跑在 8793 的完整应用），原文件：

| 本仓库路径 | 来源 | 作用 |
| --- | --- | --- |
| `engine/dsvp-engine.js` | 同名文件 | DSVP 协议校验、操作调度、`traceToPlayerData` |
| `engine/textbook-animation-engine.js` | 同名文件 | 教材主范围能力模拟器（167 能力的主体） |
| `engine/textbook-animation-auxiliary.js` | 同名文件 | 扩展能力模拟器（双端栈、链栈、串、外排序等） |
| `engine/animation-capabilities.js` | 同名文件 | 167 条能力注册表、按章裁剪的提示词、意图解析与参数回填 |
| `test/verify-*.js` | `scripts/verify-dsvp-engine.js`、`scripts/verify-animation-capability-router.js`、`scripts/verify-textbook-animation-coverage.js` | 原样移植的回归自检（仅调整 require 路径） |

移植时刻意**不改动引擎逻辑**：动画是否正确由这套确定性模拟器决定，改动它就等于改动教学正确性。原目录的 `index.html`（实验台 UI）、`server.js`（Node 后端）没有移植——界面在当前项目里重做。

## 为什么是"大模型 + 本地脚本"

- 大模型只做两件事：判断**要不要**动画（语义），以及**选哪个能力、参数是什么**。它永远不生成逐帧步骤。
- 逐帧状态由 `engine/` 里的模拟器算出来，每一步都带完整状态快照（`state.kind` + `view[role=array|tree|graph|meta]` + `highlights` + `actions`）。
- `animation-capabilities.js` 的 `animationCapabilityPrompt({lessonId})` 只把**当前教材章**的能力给模型，避免 167 条全塞进提示词把备课撑爆。

## 自检

```bash
node backend/dsvp/test/verify-dsvp-engine.js
node backend/dsvp/test/verify-animation-capability-router.js
node backend/dsvp/test/verify-textbook-animation-coverage.js
node backend/dsvp/test/verify-special-matrix.js     # 特殊矩阵压缩映射：812 组下标的「A 的值 = B[k] 的值」
```

当前基线（本机 node 22）：

```text
dsvp-engine-ok core-traces=6 extended-peek=1 strict-validation=2
animation capability router verification passed
Textbook animation coverage PASS: 167 capabilities, 911 deterministic trace steps across canonical demos.
Special-matrix compression PASS: 812 index combinations across 4 kinds, 2156 playable steps, invalid kinds rejected with explicit errors.
```

## JSONL 服务（后端调用入口）

`dsvp-service.js` 是行分隔 JSON 服务：stdin 一行一个请求，stdout 一行一个响应。Java 后端保持一个长驻子进程，省掉每次动画的 Node 启动开销。

| 请求 | 响应要点 |
| --- | --- |
| `{"id":1,"op":"health"}` | `{"engine":"dsvp-local","capabilities":167,"node":"v22.22.2"}` |
| `{"id":2,"op":"simulate","request":{...}}` | `request`（规范化后）、`trace`（原始 DSVP trace）、`player`（扁平播放数据） |
| `{"id":3,"op":"capabilities","lessonId":"08-02"}` | 167 条清单（含 demoArguments）与**按章裁剪**的提示词文本 |
| `{"id":4,"op":"resolve","intent":{...},"options":{"allowDemoFallback":true}}` | `status`（ready/not-needed/low-confidence/unsupported/missing-arguments/invalid-arguments）、`missingArguments`、`toolRequest` |

失败一律回 `{"ok":false,"error":{"code","message"}}`，进程不退出。请求上限 512 KB。

## 能力覆盖（167）

第 2–10 章：线性表（顺序表/单链表/循环链表/静态链表/双链表/多项式）、栈与队列（顺序栈/双端栈/链栈/栈应用/链队列/循环队列/循环缓冲）、串（BF/KMP/堆串/比较）、数组与广义表（特殊矩阵压缩、稀疏矩阵转置·快速转置·十字链表、广义表六种运算）、树与二叉树（建树/四种遍历/非递归/线索树/叶子统计/树高/路径/相似判定/森林与二叉树互转/哈夫曼/并查集）、图（邻接矩阵·邻接表·十字链表建图、DFS/BFS、非递归 DFS、路径搜索、连通分量、Prim/Kruskal/拓扑/关键路径/Dijkstra/Floyd、入度计算）、查找（顺序·监视哨·折半·分块、BST 增删查·递归与非递归、AVL 四种旋转与插入、B 树查找·插入·删除·定位·结点插入·分裂）、哈希（线性/二次/伪随机探测、再哈希、链地址，以及六种哈希函数构造法）、排序（10 种 + 一趟划分 + 建堆/调整 + 两路归并 + 荷兰国旗 + 链式基数）、外部排序（二路归并、多路归并、置换选择、输入输出缓冲归并）。

第 1 章（绪论）不做操作动画：以概念与复杂度分析为主，交给课件和板书。
