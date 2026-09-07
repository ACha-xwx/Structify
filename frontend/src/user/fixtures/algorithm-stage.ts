import type { AnimationDefinition, AnimationStep, AnimationType, DsvpRequest, DsvpStructure } from "../../shared/types/contracts";

export type AlgorithmStageTimelineStep = {
  id: string;
  label: string;
  detail: string;
  stepIndex: number;
};

export type AlgorithmStageVisualization = "linear" | "tree" | "graph" | "hash";

export type AlgorithmStageFixture = {
  mode: "fixture";
  contextLabel: string;
  contextDetail: string;
  sourceLabel: string;
  sourceDetail: string;
  chapterId: string;
  requestTemplate: DsvpRequest;
  definition: AnimationDefinition;
  /** Selects the teaching representation without changing the DSVP payload. */
  visualization?: AlgorithmStageVisualization;
  timeline: AlgorithmStageTimelineStep[];
  codeLines: string[];
};

/**
 * This is a fixed local teaching scene, not a server simulation or a learner
 * record. The adapter exposes it for an immediately usable guest-first stage.
 */
export const sequentialListInsertionStageFixture: AlgorithmStageFixture = {
  mode: "fixture",
  contextLabel: "本地教学预览",
  contextDetail: "固定的顺序表插入轨迹，不代表服务端计算、个人进度或已保存的学习记录。",
  sourceLabel: "线性表 · 顺序表插入",
  sourceDetail: "本地 fixture，展示从尾部右移以避免覆盖尚未读取元素的过程。",
  chapterId: "sequential-list",
  requestTemplate: {
    version: "1.0",
    structure: "sequential_list",
    operation: "insert",
    params: { index: 2, value: 23, capacity: 12 },
    initial_state: { data: [12, 18, 27, 31, 44], metadata: { capacity: 12 } },
    chapterId: "sequential-list",
    source_ref: "local-preview/sequential-list-insert",
  },
  definition: {
    animation: true,
    type: "array",
    title: "顺序表的插入",
    description: "在下标 2 写入 23，先从表尾逐格右移，再交出空位。",
    initial: [12, 18, 27, 31, 44],
    steps: [
      {
        op: "inspect",
        index: 2,
        value: 23,
        label: "定位插入位置",
        note: "23 应位于 18 与 27 之间，下标 2 是目标空位。",
      },
      {
        op: "insert",
        index: 5,
        value: 44,
        label: "为表尾腾出一格",
        note: "逻辑长度先扩展一位，最后一个元素 44 暂时复制到新表尾。",
      },
      {
        op: "set",
        index: 4,
        value: 31,
        label: "右移 31",
        note: "从尾部向前复制，31 移到下标 4，原位置留下待处理空位。",
      },
      {
        op: "set",
        index: 3,
        value: 27,
        label: "右移 27",
        note: "27 移到下标 3，目标下标 2 现在可以安全写入。",
      },
      {
        op: "set",
        index: 2,
        value: 23,
        label: "写入 23",
        note: "新元素写入空位，顺序表保持有序，移动成本为 O(n)。",
      },
    ],
  },
  timeline: [
    { id: "locate", label: "定位", detail: "确认 23 的目标下标为 2。", stepIndex: 0 },
    { id: "extend", label: "让位", detail: "扩展逻辑长度，从尾部开始复制。", stepIndex: 1 },
    { id: "shift", label: "右移", detail: "31 与 27 依次右移，空位向左传递。", stepIndex: 3 },
    { id: "insert", label: "写入", detail: "把 23 写入下标 2。", stepIndex: 4 },
  ],
  codeLines: [
    "for (let j = length; j > index; j -= 1)",
    "  data[j] = data[j - 1];",
    "data[index] = value;",
    "length += 1;",
  ],
};

type AlgorithmSceneInput = {
  id: string;
  structure: DsvpStructure;
  type: AnimationType;
  title: string;
  description: string;
  initial: unknown[];
  steps: AnimationStep[];
  codeLines: string[];
  visualization?: AlgorithmStageVisualization;
  operation?: string;
  params?: Record<string, unknown>;
};

/**
 * Build a self-contained teaching scene from content data. These scenes are
 * deliberately local and carry no progress, quota, user count, or server
 * readiness fields. The DSVP request template is the seam for a future live
 * response from the animation service.
 */
function createAlgorithmScene(input: AlgorithmSceneInput): AlgorithmStageFixture {
  const operation = input.operation ?? input.steps.find((step) => step.op !== "inspect" && step.op !== "highlight")?.op ?? "highlight";
  const sourceRef = `local-preview/${input.id}`;
  return {
    mode: "fixture",
    contextLabel: "本地教学预览",
    contextDetail: `固定的“${input.title}”教学轨迹，不代表服务端计算、个人进度或已保存的学习记录。`,
    sourceLabel: `课程小节 · ${input.title}`,
    sourceDetail: "本地 fixture，提供可暂停、可单步的示例，不会写入学习记录。",
    chapterId: input.id,
    requestTemplate: {
      version: "1.0",
      structure: input.structure,
      operation,
      params: input.params ?? {},
      initial_state: { data: input.initial, metadata: { capacity: 12 } },
      chapterId: input.id,
      source_ref: sourceRef,
    },
    definition: {
      animation: true,
      type: input.type,
      title: input.title,
      description: input.description,
      initial: input.initial,
      steps: input.steps,
    },
    visualization: input.visualization ?? "linear",
    timeline: input.steps.map((step, index) => ({
      id: `${input.id}-${String(index + 1).padStart(2, "0")}`,
      label: step.label,
      detail: step.note,
      stepIndex: index,
    })),
    codeLines: input.codeLines,
  };
}

const additionalAlgorithmScenes: Record<string, AlgorithmStageFixture> = {
  "linear-list": createAlgorithmScene({
    id: "linear-list", structure: "sequential_list", type: "array", title: "线性表的基本模型",
    description: "先看连续存储中的位置、长度和边界，再进入具体操作。", initial: [12, 18, 27, 31],
    steps: [
      { op: "inspect", index: 1, label: "读取位置 01", note: "连续存储让下标直接映射到地址，访问当前位置只需一次定位。" },
      { op: "highlight", index: 3, label: "确认表尾", note: "逻辑长度决定最后一个有效位置，容量还可以大于当前长度。" },
      { op: "inspect", index: 2, label: "检查边界", note: "插入和删除都必须先判断位置是否落在 0 到 length 的合法范围内。" },
    ],
    codeLines: ["const length = data.length;", "assert(0 <= index && index <= length);", "read(data[index]);"],
  }),
  "sequential-list-delete": createAlgorithmScene({
    id: "sequential-list-delete", structure: "sequential_list", type: "array", title: "顺序表的删除",
    description: "删除下标 2 的元素，从左向右补齐空位并收缩逻辑长度。", initial: [12, 18, 27, 31, 44], operation: "delete", params: { index: 2 },
    steps: [
      { op: "inspect", index: 2, value: 27, label: "定位删除位置", note: "下标 2 的值为 27，是本次需要移除的元素。" },
      { op: "set", index: 2, value: 31, label: "左移 31", note: "后继元素向左补位，避免删除后留下断开的空洞。" },
      { op: "set", index: 3, value: 44, label: "左移 44", note: "继续向左复制，直到最后一个有效元素完成补位。" },
      { op: "delete", index: 4, value: 44, label: "收缩表尾", note: "逻辑长度减一，原表尾不再属于有效数据。" },
    ],
    codeLines: ["for (let j = index; j < length - 1; j++)", "  data[j] = data[j + 1];", "length -= 1;"],
  }),
  "linked-list": createAlgorithmScene({
    id: "linked-list", structure: "linked_list", type: "list", title: "单链表的插入",
    description: "在节点 18 后接入 23，只改动相邻指针，不搬移整段数据。", initial: [12, 18, 31, 44], operation: "insert", params: { index: 2, value: 23 },
    steps: [
      { op: "highlight", index: 1, node: 1, label: "找到前驱节点", note: "沿 next 指针访问到 18，插入点的前驱已经确定。" },
      { op: "inspect", index: 2, node: 2, label: "保存后继节点", note: "先保存原来的后继 31，避免改指针后丢失剩余链。" },
      { op: "insert", index: 2, value: 23, label: "接入新节点", note: "让 18.next 指向 23，再让 23.next 指向 31。" },
    ],
    codeLines: ["const next = prev.next;", "prev.next = node;", "node.next = next;"],
  }),
  "doubly-linked-list": createAlgorithmScene({
    id: "doubly-linked-list", structure: "linked_list", type: "list", title: "双向与循环链表",
    description: "同时维护 prev 与 next，观察插入时两侧链接如何保持一致。", initial: [12, 18, 31, 44], operation: "insert", params: { index: 2, value: 23 },
    steps: [
      { op: "highlight", index: 1, node: 1, label: "锁定前驱与后继", note: "插入点两侧分别是 18 和 31，两个方向都需要可回退。" },
      { op: "set", index: 2, value: 23, label: "写入中间节点", note: "新节点接过前驱与后继的双向关系。" },
      { op: "highlight", index: 2, node: 2, label: "验证双向链接", note: "从新节点向前回到 18，向后走到 31，循环边界仍然闭合。" },
    ],
    codeLines: ["node.prev = prev;", "node.next = next;", "prev.next = node; next.prev = node;"],
  }),
  stack: createAlgorithmScene({
    id: "stack", structure: "stack", type: "stack", title: "栈的入栈与出栈",
    description: "用一个栈顶指针观察后进先出的顺序。", initial: [4, 7], operation: "push", params: { value: 9 },
    steps: [
      { op: "push", index: 2, value: 9, label: "压入 9", note: "新元素只能从栈顶进入，栈顶移动到 9。" },
      { op: "peek", index: 2, value: 9, label: "查看栈顶", note: "peek 只读取栈顶，不改变栈的内容。" },
      { op: "pop", index: 2, value: 9, label: "弹出 9", note: "最后进入的 9 先离开，栈顶回到 7。" },
    ],
    codeLines: ["stack.push(9);", "const top = stack.at(-1);", "stack.pop();"],
  }),
  queue: createAlgorithmScene({
    id: "queue", structure: "queue", type: "queue", title: "队列的入队与出队",
    description: "沿队尾进入、队首离开，观察先进先出的约束。", initial: ["A", "B"], operation: "enqueue", params: { value: "C" },
    steps: [
      { op: "enqueue", index: 2, value: "C", label: "C 入队", note: "新任务从队尾加入，队首仍然是 A。" },
      { op: "dequeue", index: 0, value: "A", label: "A 出队", note: "队首任务先被处理，B 成为新的队首。" },
      { op: "peek", index: 0, value: "B", label: "查看队首", note: "读取队首不会改变等待顺序。" },
    ],
    codeLines: ["queue.push(C);", "const first = queue.shift();", "peek(queue[0]);"],
  }),
  "circular-queue": createAlgorithmScene({
    id: "circular-queue", structure: "queue", type: "queue", title: "循环队列",
    description: "让队尾绕回数组起点，复用已经释放的存储空间。", initial: ["A", "B", "C"], operation: "enqueue", params: { value: "D" },
    steps: [
      { op: "dequeue", index: 0, value: "A", label: "释放队首槽位", note: "队首前移，数组开头出现可复用的空槽。" },
      { op: "enqueue", index: 0, value: "D", label: "队尾绕回", note: "队尾从数组末端回到 0 号槽，D 进入空出的空间。" },
      { op: "highlight", index: 1, label: "检查队空队满", note: "用 head、tail 与计数或预留槽位区分空队列和满队列。" },
    ],
    codeLines: ["head = (head + 1) % capacity;", "tail = (tail + 1) % capacity;", "count += 1;"],
  }),
  "expression-evaluation": createAlgorithmScene({
    id: "expression-evaluation", structure: "stack", type: "stack", title: "表达式求值",
    description: "用操作数栈与运算符优先级逐步求出 2 + 3 × 4。", initial: ["2", "+", "3", "*", "4"], operation: "push", params: { value: 4 },
    steps: [
      { op: "push", index: 0, value: "2", label: "压入操作数 2", note: "数字直接进入操作数栈。" },
      { op: "push", index: 1, value: "3", label: "压入操作数 3", note: "继续扫描，第二个操作数等待乘法。" },
      { op: "highlight", index: 3, label: "比较乘法优先级", note: "乘法优先于加法，先计算 3 × 4。" },
      { op: "set", index: 2, value: "14", label: "合并为结果 14", note: "先完成乘法，再与 2 相加得到最终结果。" },
    ],
    codeLines: ["if (isOperand(token)) values.push(token);", "while (precedence(top) >= precedence(token)) reduce();", "return values.pop();"],
  }),
  "binary-tree": createAlgorithmScene({
    id: "binary-tree", structure: "tree", type: "tree", title: "二叉树结构",
    description: "从根、左右孩子和叶子节点认识二叉树的层级关系。", initial: [8, 4, 12, 2, 6, 10, 14], operation: "highlight", params: { node: 8 }, visualization: "tree",
    steps: [
      { op: "highlight", index: 0, node: 0, value: 8, label: "访问根节点 8", note: "根节点没有父节点，是整棵树的入口。" },
      { op: "highlight", index: 1, node: 1, value: 4, label: "查看左子树", note: "左孩子 4 与根节点形成第一层分支。" },
      { op: "highlight", index: 2, node: 2, value: 12, label: "查看右子树", note: "右孩子 12 与左子树并列，树的层级由链接定义。" },
    ],
    codeLines: ["node.left = left;", "node.right = right;", "visit(root);"],
  }),
  "tree-traversal": createAlgorithmScene({
    id: "tree-traversal", structure: "tree", type: "tree", title: "树的遍历",
    // Keep the visual array in level order so the renderer's fixed tree grid
    // places 12 under the root's right branch and 2/6 under 4.
    description: "沿一棵固定二叉树走出先序访问序列。", initial: [8, 4, 12, 2, 6, 10, 14], operation: "traverse", params: { order: "preorder" }, visualization: "tree",
    steps: [
      { op: "highlight", index: 0, node: 0, label: "先序访问 8", note: "先访问根，再递归进入左子树。" },
      { op: "highlight", index: 1, node: 1, label: "访问左子树 4", note: "4 是当前子树的根，继续向左寻找 2。" },
      { op: "highlight", index: 2, node: 2, label: "访问叶子 2", note: "叶子没有孩子，完成访问后回溯。" },
      { op: "highlight", index: 4, node: 4, label: "转向右子树 12", note: "左子树完成后回到根，再访问右子树。" },
    ],
    codeLines: ["visit(node);", "preorder(node.left);", "preorder(node.right);"],
  }),
  "binary-search-tree": createAlgorithmScene({
    id: "binary-search-tree", structure: "tree", type: "tree", title: "二叉搜索树查找",
    description: "利用左小右大的不变量定位键 6。", initial: [8, 4, 12, 2, 6, 10, 14], operation: "highlight", params: { key: 6 }, visualization: "tree",
    steps: [
      { op: "highlight", index: 0, node: 0, value: 8, label: "比较根节点 8", note: "目标 6 小于 8，沿左分支继续。" },
      { op: "highlight", index: 1, node: 1, value: 4, label: "比较节点 4", note: "目标 6 大于 4，沿右分支继续。" },
      { op: "highlight", index: 4, node: 4, value: 6, label: "命中键 6", note: "当前节点等于目标键，查找结束。" },
    ],
    codeLines: ["if (key === node.key) return node;", "if (key < node.key) node = node.left;", "else node = node.right;"],
  }),
  heap: createAlgorithmScene({
    id: "heap", structure: "heap", type: "heap", title: "堆与优先队列",
    description: "插入 10 后向上调整，恢复最大堆的序关系。", initial: [9, 7, 8, 3, 4], operation: "insert", params: { value: 10 }, visualization: "tree",
    steps: [
      { op: "insert", index: 5, value: 10, label: "插入 10", note: "新元素先放到堆数组末尾，保持完全二叉树形状。" },
      { op: "swap", i: 2, j: 5, label: "与父节点交换", note: "10 大于父节点 8，向上交换一次。" },
      { op: "swap", i: 0, j: 2, label: "继续上浮到根", note: "10 继续与 9 比较并交换，最大值回到根节点。" },
    ],
    codeLines: ["heap.push(value);", "while (parent(i) < heap[i]) swap(i, parent(i));", "return heap[0];"],
  }),
  "union-find": createAlgorithmScene({
    id: "union-find", structure: "tree", type: "tree", title: "并查集的合并与查找",
    description: "把 1 与 3 合并，并观察路径压缩如何缩短后续查找。", initial: ["1", "2", "3", "4"], operation: "highlight", params: { a: 1, b: 3 }, visualization: "tree",
    steps: [
      { op: "highlight", index: 0, node: 0, label: "查找 1 的根", note: "沿 parent 指针向上，当前集合代表元是 1。" },
      { op: "highlight", index: 2, node: 2, label: "查找 3 的根", note: "3 尚未连接到 1，两个元素属于不同集合。" },
      { op: "set", index: 2, value: "1", label: "合并集合", note: "把 3 的父节点设为代表元 1，两个集合连通。" },
      { op: "highlight", index: 2, node: 2, label: "路径压缩", note: "再次查找时直接指向根，后续操作更快。" },
    ],
    codeLines: ["rootA = find(a); rootB = find(b);", "parent[rootB] = rootA;", "parent[x] = find(parent[x]);"],
  }),
  "graph-storage": createAlgorithmScene({
    id: "graph-storage", structure: "graph", type: "array", title: "图的存储表示",
    description: "对比邻接矩阵的一行与邻接表的一条边。", initial: ["A", "B", "C", "D"], operation: "highlight", params: { vertex: "A" }, visualization: "graph",
    steps: [
      { op: "highlight", index: 0, node: 0, label: "选定顶点 A", note: "从一个顶点出发，查看它与其他顶点的连接关系。" },
      { op: "highlight", index: 1, node: 1, label: "读取邻接关系", note: "矩阵直接查 data[A][B]，邻接表则遍历 A 的边链。" },
      { op: "highlight", index: 2, node: 2, label: "比较存储代价", note: "稠密图适合矩阵，稀疏图使用邻接表更节省空间。" },
    ],
    codeLines: ["matrix[u][v] = 1;", "adj[u].push(v);", "for (const v of adj[u]) visit(v);"],
  }),
  bfs: createAlgorithmScene({
    id: "bfs", structure: "graph", type: "array", title: "广度优先搜索",
    description: "从 A 出发按层扩展，队列保证先发现的顶点先处理。", initial: ["A", "B", "C", "D"], operation: "bfs", params: { start: "A" }, visualization: "graph",
    steps: [
      { op: "highlight", index: 0, node: 0, label: "入队 A", note: "起点标记为已发现并进入队列。" },
      { op: "highlight", index: 1, node: 1, label: "扩展 B", note: "取出 A 后发现第一层邻居 B。" },
      { op: "highlight", index: 2, node: 2, label: "扩展 C", note: "继续处理同一层的另一个邻居 C。" },
      { op: "highlight", index: 3, node: 3, label: "访问 D", note: "最后访问第二层顶点 D，搜索按层完成。" },
    ],
    codeLines: ["queue.push(start); visited[start] = true;", "while (queue.length) {", "  for (const next of adj[current]) queue.push(next);", "}"],
  }),
  dfs: createAlgorithmScene({
    id: "dfs", structure: "graph", type: "array", title: "深度优先搜索",
    description: "沿一条未访问分支深入，再回溯寻找下一条路径。", initial: ["A", "B", "C", "D"], operation: "dfs", params: { start: "A" }, visualization: "graph",
    steps: [
      { op: "highlight", index: 0, node: 0, label: "访问 A", note: "标记起点后，沿第一条边继续深入。" },
      { op: "highlight", index: 1, node: 1, label: "深入 B", note: "优先走未访问分支，而不是立刻处理同层节点。" },
      { op: "highlight", index: 3, node: 3, label: "深入 D", note: "到达更深层后发现没有新分支，准备回溯。" },
      { op: "highlight", index: 2, node: 2, label: "回溯到 C", note: "回到上一层后继续处理尚未访问的 C。" },
    ],
    codeLines: ["visited[node] = true;", "for (const next of adj[node])", "  if (!visited[next]) dfs(next);"],
  }),
  "shortest-path": createAlgorithmScene({
    id: "shortest-path", structure: "graph", type: "array", title: "最短路径",
    description: "用 Dijkstra 的当前最小距离逐步确定 A 到 D 的路径。", initial: ["A", "B", "C", "D"], operation: "dijkstra", params: { start: "A" }, visualization: "graph",
    steps: [
      { op: "highlight", index: 0, node: 0, label: "确定 A = 0", note: "起点距离初始化为 0，其他顶点暂时为无穷大。" },
      { op: "highlight", index: 1, node: 1, label: "松弛 B", note: "通过 A 更新到 B 的候选距离。" },
      { op: "highlight", index: 2, node: 2, label: "松弛 C", note: "比较经过 A、B 的路径，保留更小的距离。" },
      { op: "highlight", index: 3, node: 3, label: "确定 D", note: "D 的最小暂定距离已确定，沿 predecessor 回溯路径。" },
    ],
    codeLines: ["dist[start] = 0;", "u = extractMin();", "for (const edge of adj[u]) relax(edge);"],
  }),
  "minimum-spanning-tree": createAlgorithmScene({
    id: "minimum-spanning-tree", structure: "graph", type: "array", title: "最小生成树",
    description: "按边权从小到大选择不成环的边，连接全部顶点。", initial: ["A", "B", "C", "D"], operation: "highlight", params: { algorithm: "kruskal" }, visualization: "graph",
    steps: [
      { op: "highlight", index: 1, node: 1, label: "选择边 A-B", note: "当前边权最小，加入后不会形成环。" },
      { op: "highlight", index: 2, node: 2, label: "选择边 B-C", note: "连接新的顶点，森林仍然保持无环。" },
      { op: "highlight", index: 3, node: 3, label: "选择边 C-D", note: "最后一个顶点接入后，生成树包含 n - 1 条边。" },
    ],
    codeLines: ["edges.sort(byWeight);", "if (find(u) !== find(v)) union(u, v);", "stop after vertices - 1 edges;"],
  }),
  "topological-sort": createAlgorithmScene({
    id: "topological-sort", structure: "graph", type: "array", title: "拓扑排序",
    description: "每次取入度为 0 的顶点，逐步消解有向无环图的依赖。", initial: ["编译", "链接", "测试", "发布"], operation: "highlight", params: { algorithm: "kahn" }, visualization: "graph",
    steps: [
      { op: "highlight", index: 0, node: 0, label: "取出入度为 0 的顶点", note: "没有前置依赖的任务可以先安排。" },
      { op: "highlight", index: 1, node: 1, label: "更新链接依赖", note: "移除已完成任务的出边，后继入度减一。" },
      { op: "highlight", index: 2, node: 2, label: "安排测试", note: "当入度变为 0，测试任务进入候选队列。" },
      { op: "highlight", index: 3, node: 3, label: "得到线性次序", note: "全部顶点都被取出，说明图中没有环。" },
    ],
    codeLines: ["queue = vertices.filter(v => indegree[v] === 0);", "u = queue.shift();", "if (--indegree[v] === 0) queue.push(v);"],
  }),
  "sequential-search": createAlgorithmScene({
    id: "sequential-search", structure: "array", type: "array", title: "顺序查找",
    description: "从左到右比较，直到找到键 23 或走到表尾。", initial: [7, 12, 18, 23, 31], operation: "get", params: { key: 23 },
    steps: [
      { op: "highlight", index: 0, value: 7, label: "比较 7", note: "7 不是目标，继续向右扫描。" },
      { op: "highlight", index: 1, value: 12, label: "比较 12", note: "12 不是目标，当前仍然需要线性遍历。" },
      { op: "highlight", index: 2, value: 18, label: "比较 18", note: "18 不是目标，比较次数随位置增加。" },
      { op: "highlight", index: 3, value: 23, label: "命中 23", note: "找到目标，最坏情况下需要比较 n 次。" },
    ],
    codeLines: ["for (let i = 0; i < data.length; i++)", "  if (data[i] === key) return i;", "return -1;"],
  }),
  "binary-search": createAlgorithmScene({
    id: "binary-search", structure: "array", type: "array", title: "二分查找",
    description: "在有序数组中折半缩小区间，定位键 27。", initial: [3, 8, 12, 19, 27, 34, 41], operation: "get", params: { key: 27 },
    steps: [
      { op: "highlight", index: 3, value: 19, label: "检查中点 19", note: "27 大于 19，舍弃左半区间。" },
      { op: "highlight", index: 5, value: 34, label: "检查中点 34", note: "27 小于 34，保留左侧候选区间。" },
      { op: "highlight", index: 4, value: 27, label: "命中 27", note: "区间缩小到目标位置，比较次数为 O(log n)。" },
    ],
    codeLines: ["mid = Math.floor((left + right) / 2);", "if (data[mid] < key) left = mid + 1;", "else right = mid - 1;"],
  }),
  "hash-table": createAlgorithmScene({
    id: "hash-table", structure: "hash", type: "hash", title: "散列表的冲突处理",
    description: "将键 24 映射到桶位，并用开放定址处理一次冲突。", initial: ["—", "—", "17", "—", "—", "—"], operation: "put", params: { key: 24, value: "24" }, visualization: "hash",
    steps: [
      { op: "highlight", index: 0, value: 24, label: "计算哈希桶位", note: "h(24) 指向 0 号桶，准备写入。" },
      { op: "highlight", index: 2, value: 17, label: "发现冲突路径", note: "已有键占据候选位置，需要沿探测序列继续寻找。" },
      { op: "set", index: 3, value: 24, label: "写入空桶", note: "探测到 3 号桶为空，键 24 放入该位置。" },
    ],
    codeLines: ["index = hash(key) % capacity;", "while (table[index] !== empty) index = (index + 1) % capacity;", "table[index] = key;"],
  }),
  "balanced-search": createAlgorithmScene({
    id: "balanced-search", structure: "tree", type: "tree", title: "平衡查找树",
    description: "插入键 5 后观察旋转如何保持树高接近平衡。", initial: [8, 4, 12, 2, 6], operation: "highlight", params: { key: 5 },
    steps: [
      { op: "highlight", index: 4, node: 4, value: 6, label: "定位插入点", note: "5 小于 6，沿左分支寻找空位。" },
      { op: "set", index: 4, value: 5, label: "接入键 5", note: "新节点使局部子树高度发生变化。" },
      { op: "highlight", index: 1, node: 1, value: 4, label: "检查平衡因子", note: "沿祖先向上检查高度差，必要时执行旋转。" },
    ],
    codeLines: ["node = insert(node, key);", "updateHeight(node);", "return rebalance(node);"],
  }),
  "insertion-sort": createAlgorithmScene({
    id: "insertion-sort", structure: "array", type: "array", title: "插入排序",
    description: "维护一个有序前缀，把当前值插入正确位置。", initial: [9, 4, 7, 3, 8], operation: "insert", params: { index: 1, value: 4 },
    steps: [
      { op: "highlight", index: 1, value: 4, label: "取出当前值 4", note: "下标 0 的前缀已经有序，4 准备插入其中。" },
      { op: "swap", i: 0, j: 1, label: "移动 9 让位", note: "9 大于 4，向右移动一格。" },
      { op: "highlight", index: 2, value: 7, label: "处理下一个 7", note: "继续扩大有序前缀，保持相等元素的相对顺序。" },
      { op: "swap", i: 1, j: 2, label: "插入 7", note: "7 插入 4 与 9 之间。" },
    ],
    codeLines: ["for (let i = 1; i < n; i++)", "  while (j > 0 && data[j - 1] > key)", "    data[j] = data[--j];"],
  }),
  "selection-sort": createAlgorithmScene({
    id: "selection-sort", structure: "array", type: "array", title: "选择排序",
    description: "每轮在未排序区间找最小值，再与区间首位交换。", initial: [9, 4, 7, 3, 8], operation: "swap", params: { i: 0, j: 3 },
    steps: [
      { op: "highlight", index: 3, value: 3, label: "找到本轮最小值 3", note: "扫描未排序区间后，3 是当前最小值。" },
      { op: "swap", i: 0, j: 3, label: "交换到区间首位", note: "把 3 放到下标 0，左侧前缀完成。" },
      { op: "highlight", index: 1, value: 4, label: "确定下一位 4", note: "剩余区间的最小值已经位于正确位置。" },
    ],
    codeLines: ["for (let i = 0; i < n - 1; i++)", "  min = indexOfMin(data, i);", "  swap(data, i, min);"],
  }),
  "merge-sort": createAlgorithmScene({
    id: "merge-sort", structure: "array", type: "array", title: "归并排序",
    description: "先拆分，再把两个有序子序列合并为一个有序区间。", initial: [8, 3, 7, 4], operation: "merge", params: { left: 0, mid: 1, right: 3 },
    steps: [
      { op: "highlight", index: 1, value: 3, label: "拆分为两个子序列", note: "数组分成 [8, 3] 与 [7, 4] 两段。" },
      { op: "set", index: 0, value: 3, label: "取出较小值 3", note: "归并指针比较两段首元素，3 先写入输出区。" },
      { op: "set", index: 1, value: 8, label: "接着写入 8", note: "左半段耗尽后，继续合并右半段的有序值。" },
      { op: "set", index: 2, value: 4, label: "合并右半段", note: "4 与 7 按序写回，最终得到有序结果。" },
    ],
    codeLines: ["mid = (left + right) >> 1;", "merge(sort(left, mid), sort(mid + 1, right));", "copyBack(buffer);"],
  }),
  "quick-sort": createAlgorithmScene({
    id: "quick-sort", structure: "array", type: "array", title: "快速排序",
    description: "围绕枢轴 7 分区，让较小值落到左侧、较大值落到右侧。", initial: [8, 3, 7, 4, 9], operation: "swap", params: { pivot: 7 },
    steps: [
      { op: "highlight", index: 2, value: 7, label: "选择枢轴 7", note: "以 7 为分界扫描数组两端。" },
      { op: "swap", i: 0, j: 1, label: "交换 8 与 3", note: "8 应在枢轴右侧，3 应在左侧，交换修正分区。" },
      { op: "swap", i: 2, j: 3, label: "交换枢轴位置", note: "4 被放到枢轴左侧，分区边界逐渐收敛。" },
      { op: "highlight", index: 2, value: 7, label: "完成一次分区", note: "枢轴就位，左右区间可以递归处理。" },
    ],
    codeLines: ["pivot = data[right];", "while (left <= scan) partition();", "quickSort(left, pivot - 1); quickSort(pivot + 1, right);"],
  }),
  "complexity-review": createAlgorithmScene({
    id: "complexity-review", structure: "array", type: "array", title: "排序稳定性与复杂度",
    description: "把常见排序的时间、空间与稳定性放在同一条比较轨迹上。", initial: ["O(n²)", "O(n log n)", "稳定", "原地"], operation: "highlight", params: { topic: "comparison" },
    steps: [
      { op: "highlight", index: 0, value: "O(n²)", label: "观察平方级代价", note: "插入、选择等简单排序在最坏情况下需要平方级比较或移动。" },
      { op: "highlight", index: 1, value: "O(n log n)", label: "观察分治代价", note: "归并与快速排序通常达到 n log n 级别。" },
      { op: "highlight", index: 2, value: "稳定", label: "检查稳定性", note: "稳定排序保留相等键的原始相对顺序。" },
      { op: "highlight", index: 3, value: "原地", label: "检查额外空间", note: "原地排序只使用常数级辅助空间或较小栈空间。" },
    ],
    codeLines: ["time = compareComplexity(algorithm);", "space = measureAuxiliaryMemory(algorithm);", "stable = preservesEqualKeyOrder(algorithm);"],
  }),
  "04-string": createAlgorithmScene({
    id: "04-string", structure: "array", type: "array", title: "串的模式匹配",
    description: "在文本中逐位对齐模式串，观察失配后如何移动窗口。", initial: ["a", "b", "a", "b", "a", "c"], operation: "get", params: { pattern: "aba" },
    steps: [
      { op: "highlight", index: 0, value: "a", label: "对齐模式首字符", note: "文本窗口从 0 号位置开始与模式串比较。" },
      { op: "highlight", index: 2, value: "a", label: "匹配 aba", note: "连续三个字符与模式串相同，找到一次出现位置。" },
      { op: "highlight", index: 3, value: "b", label: "移动到下一窗口", note: "继续向后扫描，可复用已知前缀减少重复比较。" },
    ],
    codeLines: ["while (text[i] && pattern[j])", "  if (text[i] === pattern[j]) i++, j++;", "  else j = failure[j];"],
  }),
  "05-array-generalized-list": createAlgorithmScene({
    id: "05-array-generalized-list", structure: "array", type: "array", title: "稀疏矩阵与广义表",
    description: "用三元组记录非零项，观察二维坐标如何压缩成线性数据。", initial: ["(0,0)=3", "(2,1)=5", "(4,4)=9"], operation: "set", params: { index: 1, value: "(3,2)=7" },
    steps: [
      { op: "highlight", index: 0, value: "(0,0)=3", label: "读取非零项", note: "只记录非零元素，零值区域不占用三元组空间。" },
      { op: "set", index: 1, value: "(3,2)=7", label: "更新坐标项", note: "行、列和值组成一条可扫描的压缩记录。" },
      { op: "highlight", index: 2, value: "(4,4)=9", label: "按行组织", note: "按行主序排列后，可高效恢复矩阵中的非零位置。" },
    ],
    codeLines: ["triples.push({ row, col, value });", "triples.sort(byRowThenCol);", "restore(matrix, triples);"],
  }),
  "10-external-sort": createAlgorithmScene({
    id: "10-external-sort", structure: "array", type: "array", title: "外部排序的归并轮次",
    description: "数据分批装入内存排序，再按轮次归并有序段。", initial: [34, 12, 56, 8], operation: "merge", params: { runSize: 2 },
    steps: [
      { op: "set", index: 0, value: 12, label: "生成第一段", note: "内存中排序 [34,12]，写回磁盘成为有序段。" },
      { op: "set", index: 1, value: 34, label: "写出第一段", note: "第一段完成后释放内存，准备读取下一批数据。" },
      { op: "set", index: 2, value: 8, label: "生成第二段", note: "对 [56,8] 重复内部排序，得到第二个有序段。" },
      { op: "set", index: 3, value: 56, label: "归并两段", note: "顺序读取两段的首元素，减少随机外存访问。" },
    ],
    codeLines: ["runs = makeSortedRuns(input, memoryLimit);", "while (runs.length > 1) runs = mergePass(runs);", "writeFinalRun(runs[0]);"],
  }),
  "01-introduction": createAlgorithmScene({
    id: "01-introduction", structure: "array", type: "array", title: "算法复杂度入门",
    description: "用一次线性扫描和一次常数访问，建立复杂度比较的直觉。", initial: ["read", "scan", "compare", "write"], operation: "highlight", params: { topic: "complexity" },
    steps: [
      { op: "highlight", index: 0, label: "一次直接访问", note: "按下标读取数组元素只需要固定次数的操作。" },
      { op: "highlight", index: 1, label: "开始线性扫描", note: "循环逐项处理时，操作次数会随 n 增长。" },
      { op: "highlight", index: 3, label: "比较增长趋势", note: "描述复杂度时关注输入规模增大后的主要增长项。" },
    ],
    codeLines: ["read(data[index]); // O(1)", "for (const item of data) visit(item); // O(n)", "choose the dominant term;"],
  }),
};

/**
 * Complete local scene map for the course catalog. Chapter aliases point to
 * the first concrete lesson so a chapter-only route remains playable while
 * lesson routes get their own operation trace.
 */
export const algorithmStageFixtures: Readonly<Record<string, AlgorithmStageFixture>> = Object.freeze({
  "sequential-list": sequentialListInsertionStageFixture,
  ...additionalAlgorithmScenes,
  "02-linear-list": additionalAlgorithmScenes["linear-list"],
  "03-stack-queue": additionalAlgorithmScenes.stack,
  "06-tree": additionalAlgorithmScenes["binary-tree"],
  "07-graph": additionalAlgorithmScenes["graph-storage"],
  "08-search": additionalAlgorithmScenes["sequential-search"],
  "09-internal-sort": additionalAlgorithmScenes["insertion-sort"],
});

/** Resolve a lesson/chapter context to its local teaching scene. */
export function algorithmStageFixtureFor(contextId?: string | null): AlgorithmStageFixture {
  const normalized = contextId?.trim().toLocaleLowerCase() ?? "";
  return algorithmStageFixtures[normalized] ?? sequentialListInsertionStageFixture;
}
