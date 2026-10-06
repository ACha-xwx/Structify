# Structify 动画「结构特征」盘点（2026-10-06）

> 起因：Rrd 看到**双向链表插入**画成 `[10|next] → [15|next] → [20|next]`——只有向右的箭头、结点里没有 `prior` 一栏，
> 等于把双向链表画成了单链表。要求是：**每一个结构都按它自己的定义，把该有的东西显示出来**
> （top 是多少、next 指向谁、prior 指向谁…），比例合适、风格不变。

本文是**盘点 + 缺口清单**，不是设计稿。每一条都注明了依据（数据从哪来、怎么验的）。

---

## 一、依据与工具（可复跑）

| 脚本 | 干什么 | 位置 |
|---|---|---|
| `structure-inventory.mjs` | 跑遍 167 条能力，按 `structure` 汇总**引擎实际发出的东西**：面板 role、meta 字段、具名指针、值的形状 | 技能 `structify-animation-probe/scripts/` |
| `structure-gaps.mjs` | 机械判据：数字型 meta 字段里，**哪些"位置类"字段既没进指针池（不会标在结构上）、又只是变成胶囊** | 同上 |
| `annotation-check.mjs` | 真机断言：把"该显示的特征"逐条对着真实 DOM 验（含几何断言） | 同上 |

**数据的唯一来源是引擎代码**（`animation-capabilities.js` 的注册表 + 各模拟器发出的 `view`/`meta`），
不是"我以为链表应该有"。判据分两层：**数据里有没有**（决定是改引擎还是改渲染）与**画出来没有**。

---

## 二、34 个结构：定义性特征 vs 现状

图例：✅ 已画 · ⚠️ 部分（画了但缺关键特征）· ❌ 没画 · 🔍 仅盘点数据、未逐帧看过

### 线性表（6）

| 结构 | 定义性特征（该显示的） | 数据里有 | 现状 |
|---|---|---|---|
| `linked_list` 单链表 | 结点 `[data\|next]`、`head`、具名工作指针（`p`/`pre`） | values + `current/position/previous/reversedThrough/head/count` + pointers `next` | ✅ 结点框+箭头（今天）；⚠️ **逆置时 `pre` 指针没标**（`previous` 不在指针池） |
| `doubly_linked_list` 双向链表 | 结点 **`[prior\|data\|next]` 三栏**、结点之间**两根反向箭头**、两端的 `NULL` | values + `position/phase` + pointers `prior`,`next` | ✅ **今天修好**（此前画成单链表） |
| `circular_linked_list` 循环单链表 | 尾结点 `next` 指回 `head`（不是 NULL）、首尾相接 | values + `circular/head/tail/headSelfLoop/merged` | ✅ 今天：末结点 `next` 栏写 `head` + 尾端"回到 head"箭头 |
| `static_linked_list` 静态链表 | 结点表 `{index, data, cursor}`：**指针是数组下标**；备用链 | `nodes` 为对象数组 | ⚠️ 画成记录表（index/data/cursor/free 都在），但**备用链的走向看不出来** |
| `sequential_list` 顺序表 | 连续存储、`length`、插入/删除时的**元素后移** | 扁平快照 `items/length/position/targetIndex/movingIndex` | 🔍 |
| `polynomial` 一元多项式 | 每项 `(coef, exp)`、两表相加时的比较 | `P/PA/PB/PC` + `i/j/current` | 🔍 |

### 栈与队列（9）

| 结构 | 定义性特征 | 数据里有 | 现状 |
|---|---|---|---|
| `stack` 顺序栈 | **top 指在哪**、容量、栈空/栈满 | `items/top/metadata.capacity` | ✅ top 游标 + 容量胶囊 |
| `linked_stack` 链栈 | 栈顶就是链首，`top` 指向首结点 | `stack` + `top`（恒为 0） | ✅ |
| `double_stack` 双端顺序栈 | **两个栈顶在同一个共享数组里从两端往中间长**、还剩几个空位、满的条件 | `capacity/topLeft/topRight` | ✅ 今天：两侧各标 `top`、新增「空位」胶囊；⚠️ 仍是**两行数组**，共享空间没有画成一根数组 |
| `queue` 顺序队列 | `front` 指向队首、`rear` 指向队尾 | 扁平 `items/front/rear` | ✅ 今天：front **和** rear 都标 |
| `linked_queue` 链队列 | 队首/队尾两个指针 | `queue` + `front/rear` | ✅ |
| `circular_queue` 循环队列 | 环形缓冲区 + `front`/`rear`/`count` | `buffer` + `front/rear/count/capacity` | ✅ 今天：front/rear 都标 + 计数；⚠️ 画成一行数组，**没有环形感** |
| `circular_buffer` 循环缓冲区 | 同上（键盘缓冲） | 同上 | ✅ 今天：同循环队列 |
| `stack_app` 栈的应用 | 运算符栈/操作数栈的**两层一起**、当前扫描位置 | `stack/operators/tokens/values/index/current` | 🔍 |
| `queue_app` 队列的应用 | 当前行 `row` | `current/rows` + `row` | 🔍 |

### 串（1）

| `string` 串 | 正文/模式串两行对齐、`i`/`j` 两个游标、失配位置 | `text/pattern/next` + `i/j/position/match/mismatch/count` | 🔍（`i/j` 有专门规则；`match` 尚未标） |

### 数组与广义表（4）

| `special_matrix` 特殊矩阵 | 原矩阵 → 压缩数组的下标映射（`k` 公式） | `matrix/compressed/records` + `i/j/k/formula` | ✅（既有的 `animation-check.mjs` 有 8 项断言守着） |
| `sparse_matrix` 稀疏矩阵 | 三元组表、`cpot` 定位、行列计数 | `matrix/triples/cpot/num/result/cross_nodes` | 🔍 |
| `generalized_list` 广义表 | 表头/表尾、深度、原子个数 | `list/head/tail/copy` + `depth/length/atomCount` | 🔍 |
| `hash_function` 哈希函数 | 关键字 → 地址的**推导过程**（平方取中、除留余数…） | `address/digits/formula/square_digits/sum/mapping` | 🔍 |

### 树（8）

| `tree` 二叉树 | 结点+左右边、遍历顺序、线索 | `tree/visited/left/right` + `depth/maxDepth/threads/path` | 🔍（结点+边已画；**访问顺序编号**没画） |
| `bst` 二叉排序树 | 比大小的走向（`key`）、结点深度 | `tree` + `key/current/depth` | 🔍 |
| `avl` 平衡二叉树 | **平衡因子**、旋转轴、失衡结点 | `tree` + `root/rotation/phase/current` | 🔍（`root` 只是键值，不是位置） |
| `btree` B 树 | 多关键字结点、结点内位置 `pos` | `tree/node/left/right/promote` + `key/pos/order/adjusted` | 🔍（多关键字结点已画） |
| `huffman` 哈夫曼树 | 结点权值、`0/1` 编码表、活跃结点 | `tree/active/codes` + `current` | ✅ 末帧编码表（有断言守着） |
| `forest` 森林 | 多棵树 + 二叉树表示 | `trees/binaryRepresentation` + `currentTree` | 🔍 |
| `heap_string` 堆串 | 低/高端指针、已用区与空闲区 | `chars` + `low/high/position/written/length` | ⚠️ `low/high` 只是胶囊（区间描边只在 sort 生效） |
| `recursion` 递归 | 调用栈、每层的 `n`/结果、汉诺塔三塔 | `call_stack/records/sequence/A/B/C` + `n/k/disk/from/to` | 🔍 |

### 图（2）

| `graph` 图 | 顶点/边、**visited 颜色状态**、当前边加粗、`dist`/`indegree` 逐结点标注 | `graph/matrix/indegree/stack` + `visited/dist/indegree/topo/ve/vl/path/relax/chosenEdges/skipped/frontier/settled` | ✅ 逐结点标注 + 边高亮（最短路/最小生成树/拓扑度）；⚠️ **颜色状态语义**没有统一成"未访问/在队列/已访问" |
| `union_find` 并查集 | `parent` 数组、查找路径、路径压缩 | `parent/path` + `a/b/root/path/compressed` | ⚠️ `a`/`b`（正在查的两个元素）没标 |

### 查找（2）

| `search` 查找 | 折半的 `low/mid/high`、被排除的区间、目标位置 | `R/array/blocks` + `low/mid/high/key/index/found/block` | ✅ `low/mid/high` 游标 + `low..high` 区间描边 |
| `hash_table` 哈希表 | 桶号、探测序列、链地址法的链内位置 | `table/buckets` + `index/attempt/bucket/mode/stored` | ⚠️ 今天修掉"算地址与第 1 次探测指同一个格子"；`bucket`/`stored` 仍未标 |

### 排序（2）

| `sort` 排序 | 具名游标、已排好区间、**比较/交换次数**、不变式 | 16 条能力的 meta（`i/j/k/low/mid/high/current/pivotIndex/selected/scan/sortedEnd/sortedStart/invariant/compareCount/swapCount`） | ✅ 游标/计数器/不变式（今天）；✅ 今天补 `selected`/`scan`；⚠️ `sortedStart`/`sortedEnd`（已排好区间）仍是胶囊 |
| `external_sort` 外排序 | 内存区/输入区/输出顺串、归并路数、pageSize | `runs/outputRuns/memory/output/input/ioBuffer` + `ways/pageSize` | 🔍 |

---

## 三、今天改了什么（4 项，均已真机验证）

1. **双向链表**：结点框按结构定义参数化成 `[prior\|data\|next]`，结点之间**正向 + 反向两根箭头**
   （反向箭头用 `scaleX(-1)` 镜像，方向可验证），首结点 `prior` 与末结点 `next` 写 `NULL`。
   真机实测结点框 = `[["NULL","10","next"],["prior","20","next"],["prior","30","NULL"]]`。
2. **循环单链表**：末结点 `next` 栏写 `head`（不再写 `NULL`），尾端加一根"回到 head"的箭头。
3. **队列/循环队列/循环缓冲区**：`front` 与 `rear` **两个标记都画**（此前只标 `front`）。
4. **双端顺序栈**：两侧各自的 `top` 都标出来（标在**本侧数组的最后一个格子**上——
   引擎给的是共享数组里的全局下标，直接标本侧会越界），并新增引擎字段与胶囊「空位」
   （共享数组还剩几个单元，这是双端栈的定义性不变式）。

同时修掉两处误导：**简单选择排序的比较帧只有「当前最小」和「扫描位置」在变，而这两格没标**
（那几十帧看起来是静止的）；**哈希探测的「计算散列地址」与「第 1 次探测」指着同一个格子**。

---

## 三·五、"代码级别"的粒度标准（2026-10-06 下午，Rrd 追加要求）

> 原话："我希望这个动画的细致程度是**代码级别**的，就是这个代码一行一行的怎么执行的，
> 这个动画就要长什么样子，能少用语言描述就少用。"
> 例子：循环链表合并（头指针）原来 2 帧——"定位两个表尾"→"首尾重新连接"，
> 中间"怎么断的、怎么连的、next/pre 怎么赋值"全在旁白里。

### 现在的约定（新写/改模拟器时照这个来）

1. **一帧 = 一行赋值**。帧的 `title` **和** `note` 都是那行代码（`p->next = B->next`）。
   ⚠️ 页面的大标题取的是 **note**，不是 title——只把代码放 title，页面上看到的还是解释。
2. **改完再报帧**：每帧显示的是这一行**执行之后**的状态，点一次看到一行代码的效果。
3. **面板的槽位由引擎给**：`next: ["1","3","5","2"]`、`prior: [...]` —— 写"指向谁"，
   前端不自己反推（两处各推一份迟早不一致）。
4. **说清这一帧改的是哪个结点的哪个字段**：`write: <显示序>` + `writeSlot: "next"|"prior"`。
   少了 `writeSlot`，`p->next->prior = s` 会去点亮 `next` 格（真机验收抓到过）。
5. **具名指针按面板给**（`pointers: {p:3, A:0}`），不是全帧共用一份——
   两个表各有 p、q 时共用会把 LB 的 q 标到 LA 上。渲染端把它们排在最前（该强调的就是正在动的那个）。
6. **结点框每格两行**：字段名（`prior`/`next`）+ 它此刻指向谁。只写目标值看不出哪格是哪个字段。
7. 代码行用等宽字体（`phase === "assign"` → `.player__headline--code`），一眼分得出"程序"还是"解释"。
8. **两个同名结点要各自命名**：循环表合并里两个头结点必须叫 `headA`/`headB`——
   都叫 `head` 的话，`q->next = A` 那一帧和"指向自己"长得一模一样（契约测试会判它同画面，它确实报了）。
9. **绕回/自环之类的标记要数据说了算**：写完 `p->next = B->next` 之后 A 的尾结点已指向 2，
   那根"回到 head"不能还挂着（只看 kind 就会挂）。

### 已完成 / 待做

| 操作 | 状态 | 帧数变化（详细） |
|---|---|---|
| `circular_linked_list.merge_head_pointer` | ✅ 代码级 | 2 → **11**（精简 **5**） |
| `circular_linked_list.merge_tail_pointer` | ✅ 代码级 | 2 → **4** |
| `circular_linked_list.build` / `initialize` | ✅ 代码级 | 1/4 → 8 / 2 |
| `doubly_linked_list.insert` / `delete` | ✅ 代码级 | 2/2 → **6 / 5**（精简 **3 / 4**） |
| `linked_list.insert` / `delete` / `merge` | ✅ 代码级 | → **5 / 4 / 18**（精简 3 / 3 / 12） |
| `linked_list.reverse` | ✅ 代码级 | → **13**（精简 9）：`q = p->next` → `p->next = r` → `r = p; p = q` |
| `linked_list.build_head` / `build_tail` | ✅ 代码级 | → 16 / 16（精简 12 / 9） |
| `linked_list` 的查找/求长度/初始化 | ✅ 代码级 | 按值查找 `if (p->data == key) → true/false`；长度 `n = n + 1` |
| `linked_stack.push` / `pop` | ✅ 代码级 | → 4 / 3（精简 3 / 3） |
| `linked_queue.initialize` / `enqueue` / `dequeue` | ✅ 代码级 | → 2 / 5 / 3（**带头结点**模型） |
| `static_linked_list.*` | ✅ 代码级 | 初始化 7、申请 3、回收 3（`space[i].cur = i+1` 逐格） |
| 树/图 | ⬜ 待定 | 它们的"一行代码"是另一套（递归调用 / 松弛），要单独定 |
| **排序** | ✅ **两档都做了** | 交换拆成 `temp = a[i]` / `a[i] = a[j]` / `a[j] = temp` 三帧（标 line）；比较帧就是 `if (…) → true/false`。实测：冒泡 详细 **22** → 精简 **14**、堆 26 → 12、插入 13 → 11；7 元素的默认数据 详细 **64** → 精简 **40** |

---

## 三·六、指针怎么表示（2026-10-06 晚，Rrd 提出"有重复数据就不直观"）

结点框的 `prior`/`next` 槽位里**写目标结点的序号**（`→ 3` = 第 3 个结点），结点框上标 `#1 #2 #3…`。

- 为什么不用**值**：`[10,20,20,50,…]` 里两个 20，槽位写"20"分不清指向哪一个——Rrd 一眼看出来的问题。
- 为什么用**序号**：唯一；和"指针就是地址"的直觉一致；结点框上就标着号，不用数。
- 跨表的写 `B#2`（B 表的第 2 个结点）；没有指向写 `NULL`。
- 引擎的 `next`/`prior` 因此发**序号**：`next: [2, 3, 4, null]`。

## 三·七、两档粒度：详细 / 精简（默认详细）

Rrd：可以做两版——**详细**像 debug 一行一行（含比较过程），**精简**"比较一帧、交换一帧"，用户可切。

- **分档标记**：引擎给"中间过程帧"标 `phase: "line"`；**一段连续的 line 只留最后一帧**（那就是该阶段的完成态）。
- **详细（默认）**：一帧不压。**精简**：按上面的规则压。
- **没标 line 的操作（排序/树/图）两版完全一样**——开关不会把它们压坏（有单测守着）。
- 开关在播放器上（连体胶囊「详细 | 精简」），切档回到起点重播。
- 实测：循环表合并 11 → **5**；双向插入 6 → **3**；双向删除 5 → **4**；冒泡/BST 不变。

> ⚠️ 踩过的坑：代码帧的**等宽字体**判定原来绑 `phase === "assign"`，而 phase 现在兼作分档标记（`line`）
> ⇒ 逐行帧丢了等宽字体。改成按引擎约定判断：**代码帧的 title 与 note 都是那行代码**。

### 排序的两档（同一天稍后补齐）

- **比较帧**：`if (a[i] > a[i+1]) → true|false`（代码即标题；之前是散文"49 > 38：成立，要交换。"）。
- **交换**：拆成三条赋值 `temp = a[i]` / `a[i] = a[j]` / `a[j] = temp`，都标 `line`。
  中间那帧数组里**同一个值出现两份**（`[3,3,8,1]`）——"为什么要临时变量"就是这一帧；
  `temp` 本身进胶囊（标签「临时变量」），所以第一步（数组没变）也不会和上一帧同画面。
- 精简版把三帧压成最后一帧 ⇒ **"比较一帧、交换一帧"**，正是 Rrd 描述的那一版。
- 插入/希尔的**后移**、快排的**填坑搬移**也逐格报帧了。

| 操作（`[5,3,8,1]`） | 详细 | 精简 |
|---|---|---|
| 冒泡 | 22 | **14** |
| 简单选择 | 14 | 10 |
| 堆 | 26 | 12 |
| 直接/折半插入 | 13 | 11 |
| 希尔 / 快排 / 归并 / 基数 / 锦标赛 | 11 / 9 / 15 / 6 / 5 | 同左（它们本来就是"一步一格"） |

> ⚠️ **详细/精简的开关在同一个播放器实例上是保留的**（同一页里换动画不会重置）。
> 写验收脚本时要注意：上一段停在精简，下一段就会在精简版上断言（我因此误报过 3 条）。
> 现在 `annotation-check.mjs` 的 `generate()` 每次先归位到「详细」。

9. 绕回/自环之类的标记要数据说了算（写完 `p->next = B->next` 之后 A 的尾结点已指向 2，
   那根"回到 head"不能还挂着（只看 kind 就会挂）。
10. **指针槽位写目标结点的"序号"**（`→ 3`），不写值——值会重复。结点框上标 `#1 #2 …`。
11. **两档粒度**：中间过程帧标 `phase: "line"`，精简版把每段连续 line 压成最后一帧。

### 改链表家族时踩到的四个真错（都可以用契约测试提前抓到）

| 错 | 症状 | 根因 |
|---|---|---|
| 插入后没重建所有 next | `next=[2,3,3,null]`（结点指向自己） | 只改了前后两个结点；结点插进显示序后**所有**序号都变了，next 要整体重建 |
| 合并取 B 的结点也写 `p = p->next` | 游标推错了一边 | 取哪边就推哪边（`p`/`q`） |
| 链栈 push 的 `unshift` 后同样没重建 | `next=[2,2,3,null]` | 同上 |
| "申请结点"两帧同画面 | 契约测试报 18 处 | `s = malloc()` 时数据域就已经填好了 ⇒ 现在先以 `∅` 出现，`s->data = x` 那一帧才有变化；静态链表初始化的 cursor 同理改成逐格设置 |

> 这四类**引擎自检脚本抓不到**（它们只验"能跑"），是**契约测试**（167 能力逐帧过真实渲染器、
> 相邻两帧不许同画面）抓出来的。改任何模拟器后都要跑它。

---

## 四、还没改的（按性价比排序，等定）

| 优先级 | 项 | 为什么 |
|---|---|---|
| 高 | **双端栈画成一根共享数组**（左栈从左、右栈从右，中间空位一眼可见） | 现在的两行数组完全体现不出"共享空间"，而这是这个结构的全部意义 |
| 高 | **循环队列/循环缓冲画成环** | 同上：`front`/`rear` 都标了，但"绕圈"看不出来 |
| 高 | **单链表逆置的 `pre`/`p` 指针** | 逆置是链表最核心的操作，`previous` 已经在数据里 |
| 中 | **已排好区间**（`sortedStart`/`sortedEnd` → 绿色/灰底区间） | 排序的"工作量在缩小"看不见 |
| 中 | **树的访问顺序编号**（遍历时结点旁标 ①②③） | 学生最需要"谁先谁后" |
| 中 | **图的颜色状态**（未访问灰 / 在队列黄 / 已访问绿） | 现在只有"已访问"一种状态 |
| 中 | **静态链表的备用链走向** | 静态链表的重点就是"备用链" |
| 低 | `heap_string` 的 `low/high` 区间、`union_find` 的 `a`/`b`、`hash_table` 的 `bucket`/`stored`、`string` 的 `match` | 都是"数据在、只差画" |
| 待定 | `static_linked_list` 起点空画面、`external_sort` 分页 | 需要先看图 |

**注意**：「⚠️/🔍」的条目里，有一部分是"数据里有、前端没画"（改渲染即可），另一部分是"引擎压根没发"
（要改模拟器）。逐个动手前先用 `structure-inventory.mjs <结构名>` 看一眼，别猜。

---

## 五、回归方式

```bash
# 1. 数据盘点（不依赖服务）
node <技能>/scripts/structure-inventory.mjs            # 全部
node <技能>/scripts/structure-inventory.mjs <结构名>    # 单个结构逐帧明细
node <技能>/scripts/structure-gaps.mjs                 # 只列有缺口的字段

# 2. 真机断言（需要本地栈：后端 8794 + 前端 5175）
STRUCTIFY_FRONTEND_URL=http://127.0.0.1:5175 \
STRUCTIFY_USERNAME=structify_test STRUCTIFY_PASSWORD=… \
node <技能>/scripts/annotation-check.mjs               # 全部结构
STRUCTIFY_ONLY=doubly node …/annotation-check.mjs      # 只跑双向链表
```

⚠️ 改完前端**必须重启 vite** 再跑真机脚本（跑着的 dev server 不会把改动推给新开的浏览器上下文）；
改完 `backend/dsvp/engine/*` **必须重启后端**（引擎跑在长驻 node 子进程里）。
