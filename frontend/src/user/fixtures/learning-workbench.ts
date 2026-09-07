import type { Chapter } from "../../shared/types/contracts";

export type WorkbenchStep = {
  id: string;
  index: number;
  label: string;
  detail: string;
  kind: "inspect" | "compare" | "shift" | "insert";
};

export type WorkbenchCourseGroupId =
  | "linear-structures"
  | "stacks-queues"
  | "trees-heaps"
  | "graphs"
  | "search-hash"
  | "sorting-complexity";

export type WorkbenchCourseItemKind = "chapter" | "lesson";

/**
 * Availability is deliberately about content delivery, not learner progress.
 * A catalog entry can be navigated to its chapter context without claiming
 * that a playable lesson or persisted activity already exists.
 */
export type WorkbenchCourseCapability =
  | "catalog"
  | "local-interactive-preview"
  | "api-published";

export type WorkbenchCourseCapabilityPresentation = {
  label: string;
  detail: string;
};

const courseCapabilityPresentation: Record<
  WorkbenchCourseCapability,
  { zh: WorkbenchCourseCapabilityPresentation; en: WorkbenchCourseCapabilityPresentation }
> = {
  catalog: {
    zh: { label: "课程目录", detail: "可查看课程结构；具体学习内容以对应入口实际提供的内容为准。" },
    en: { label: "Course catalog", detail: "The course map is available; learning content depends on the selected entry." },
  },
  "local-interactive-preview": {
    zh: { label: "本地交互预览", detail: "可在当前设备操作示例，不会写入学习记录。" },
    en: { label: "Local interactive preview", detail: "The example runs in this browser and does not save learning records." },
  },
  "api-published": {
    zh: { label: "课程服务内容", detail: "该章节来自当前课程服务响应，可进入相应学习入口。" },
    en: { label: "Course service content", detail: "This chapter was returned by the course service and can open its learning entry." },
  },
};

/** Return learner-facing delivery copy without conflating it with progress. */
export function localizeCourseCapability(
  capability: WorkbenchCourseCapability,
  locale: "zh-CN" | "en-US" = "zh-CN",
): WorkbenchCourseCapabilityPresentation {
  return courseCapabilityPresentation[capability][locale === "en-US" ? "en" : "zh"];
}

export type WorkbenchCourseItem = {
  id: string;
  label: string;
  meta: string;
  labelEn?: string;
  metaEn?: string;
  /** A learner-facing explanation of the concept, separate from its label. */
  summary: string;
  summaryEn: string;
  /** Whether this row is only a map, a local demo, or live API content. */
  capability: WorkbenchCourseCapability;
  current?: boolean;
  /** Distinguishes a server-facing chapter from a local lesson alias. */
  kind?: WorkbenchCourseItemKind;
  /** Canonical chapter scope used when a lesson is opened in another tool. */
  chapterId?: string;
  chapterNumber?: number;
  lessonNumber?: number;
  parentId?: string;
  /** Route id may differ from a synthetic lesson id when no lesson route exists. */
  routeId?: string;
  children?: WorkbenchCourseItem[];
};

export type WorkbenchCourseGroup = {
  id: WorkbenchCourseGroupId;
  order: number;
  label: string;
  labelEn: string;
  description: string;
  descriptionEn: string;
  /** The group's purpose in the curriculum map. */
  summary: string;
  summaryEn: string;
  capability: WorkbenchCourseCapability;
  open?: boolean;
  items: WorkbenchCourseItem[];
};

type WorkbenchCourseSeedItem = Omit<WorkbenchCourseItem, "summary" | "summaryEn" | "capability" | "children"> & {
  children?: WorkbenchCourseSeedItem[];
};

type WorkbenchCourseSeedGroup = Omit<WorkbenchCourseGroup, "summary" | "summaryEn" | "capability" | "items"> & {
  items: WorkbenchCourseSeedItem[];
};

export type WorkbenchFixture = {
  mode: "fixture";
  goalId: string;
  goalStatus: "active" | "complete" | "upcoming";
  courseLabel: string;
  courseCode: string;
  topic: string;
  topicSummary: string;
  structureLabel: string;
  structureType: string;
  initialNodes: number[];
  insertionValue: number;
  insertionIndex: number;
  goal: string;
  goalDetail: string;
  source: string;
  sourceDetail: string;
  nextAction: string;
  nextDetail: string;
  steps: WorkbenchStep[];
  courseOutline: WorkbenchCourseItem[];
  courseGroups: WorkbenchCourseGroup[];
};

/**
 * Content-layer catalog used by guest previews and shared navigation. The
 * metadata describes curriculum placement only; it is not learner progress.
 */
const courseGroupSeeds: WorkbenchCourseSeedGroup[] = [
  {
    id: "linear-structures",
    order: 1,
    label: "线性结构",
    labelEn: "Linear structures",
    description: "从连续存储到节点连接",
    descriptionEn: "From contiguous storage to linked nodes",
    items: [
      {
        id: "01-introduction",
        label: "绪论",
        labelEn: "Introduction",
        meta: "第 01 章",
        metaEn: "Chapter 01",
        kind: "chapter",
        chapterId: "01-introduction",
        chapterNumber: 1,
      },
      {
        id: "02-linear-list",
        label: "线性表",
        labelEn: "Linear lists",
        meta: "第 02 章",
        metaEn: "Chapter 02",
        kind: "chapter",
        chapterId: "02-linear-list",
        chapterNumber: 2,
        children: [
          { id: "linear-list", label: "线性表基础", labelEn: "Linear list fundamentals", meta: "基础单元", metaEn: "Foundation", kind: "lesson", chapterId: "02-linear-list", parentId: "02-linear-list", routeId: "02-linear-list", lessonNumber: 1 },
          // The local teaching scene already uses this stable context id. Its
          // parent records where it belongs in the future API chapter map.
          { id: "sequential-list", label: "顺序表的插入", labelEn: "Insertion in a sequential list", meta: "当前学习", metaEn: "Current lesson", kind: "lesson", chapterId: "sequential-list", parentId: "02-linear-list", current: true, routeId: "sequential-list", lessonNumber: 2 },
          { id: "sequential-list-delete", label: "顺序表的删除", labelEn: "Deletion from a sequential list", meta: "下一节", metaEn: "Next lesson", kind: "lesson", chapterId: "02-linear-list", parentId: "02-linear-list", routeId: "sequential-list-delete", lessonNumber: 3 },
          { id: "linked-list", label: "单链表", labelEn: "Singly linked lists", meta: "后续单元", metaEn: "Later lesson", kind: "lesson", chapterId: "02-linear-list", parentId: "02-linear-list", routeId: "linked-list", lessonNumber: 4 },
          { id: "doubly-linked-list", label: "双向与循环链表", labelEn: "Doubly and circular lists", meta: "扩展单元", metaEn: "Extension", kind: "lesson", chapterId: "02-linear-list", parentId: "02-linear-list", routeId: "doubly-linked-list", lessonNumber: 5 },
        ],
      },
      {
        id: "04-string",
        label: "串",
        labelEn: "Strings",
        meta: "第 04 章",
        metaEn: "Chapter 04",
        kind: "chapter",
        chapterId: "04-string",
        chapterNumber: 4,
      },
      {
        id: "05-array-generalized-list",
        label: "数组与广义表",
        labelEn: "Arrays and generalized lists",
        meta: "第 05 章",
        metaEn: "Chapter 05",
        kind: "chapter",
        chapterId: "05-array-generalized-list",
        chapterNumber: 5,
      },
    ],
  },
  {
    id: "stacks-queues",
    order: 2,
    label: "栈与队列",
    labelEn: "Stacks and queues",
    description: "受限线性表的进出顺序",
    descriptionEn: "Order under constrained access",
    items: [
      {
        id: "03-stack-queue",
        label: "栈与队列",
        labelEn: "Stacks and queues",
        meta: "第 03 章",
        metaEn: "Chapter 03",
        kind: "chapter",
        chapterId: "03-stack-queue",
        chapterNumber: 3,
        children: [
          { id: "stack", label: "栈与括号匹配", labelEn: "Stacks and bracket matching", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "03-stack-queue", parentId: "03-stack-queue", routeId: "03-stack-queue", lessonNumber: 1 },
          { id: "queue", label: "队列与双端队列", labelEn: "Queues and deques", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "03-stack-queue", parentId: "03-stack-queue", routeId: "03-stack-queue", lessonNumber: 2 },
          { id: "circular-queue", label: "循环队列", labelEn: "Circular queues", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "03-stack-queue", parentId: "03-stack-queue", routeId: "03-stack-queue", lessonNumber: 3 },
          { id: "expression-evaluation", label: "表达式求值", labelEn: "Expression evaluation", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "03-stack-queue", parentId: "03-stack-queue", routeId: "03-stack-queue", lessonNumber: 4 },
        ],
      },
    ],
  },
  {
    id: "trees-heaps",
    order: 3,
    label: "树与堆",
    labelEn: "Trees and heaps",
    description: "层级关系、遍历与优先级",
    descriptionEn: "Hierarchy, traversal, and priority",
    items: [
      {
        id: "06-tree",
        label: "树与二叉树",
        labelEn: "Trees and binary trees",
        meta: "第 06 章",
        metaEn: "Chapter 06",
        kind: "chapter",
        chapterId: "06-tree",
        chapterNumber: 6,
        children: [
          { id: "binary-tree", label: "二叉树结构", labelEn: "Binary tree structure", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "06-tree", parentId: "06-tree", routeId: "06-tree", lessonNumber: 1 },
          { id: "tree-traversal", label: "树的遍历", labelEn: "Tree traversal", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "06-tree", parentId: "06-tree", routeId: "06-tree", lessonNumber: 2 },
          { id: "binary-search-tree", label: "二叉搜索树", labelEn: "Binary search trees", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "06-tree", parentId: "06-tree", routeId: "06-tree", lessonNumber: 3 },
          { id: "heap", label: "堆与优先队列", labelEn: "Heaps and priority queues", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "06-tree", parentId: "06-tree", routeId: "06-tree", lessonNumber: 4 },
          { id: "union-find", label: "并查集", labelEn: "Disjoint-set union", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "06-tree", parentId: "06-tree", routeId: "06-tree", lessonNumber: 5 },
        ],
      },
    ],
  },
  {
    id: "graphs",
    order: 4,
    label: "图结构",
    labelEn: "Graph structures",
    description: "连接、路径与网络",
    descriptionEn: "Connections, paths, and networks",
    items: [
      {
        id: "07-graph",
        label: "图",
        labelEn: "Graphs",
        meta: "第 07 章",
        metaEn: "Chapter 07",
        kind: "chapter",
        chapterId: "07-graph",
        chapterNumber: 7,
        children: [
          { id: "graph-storage", label: "图的存储表示", labelEn: "Graph representations", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "07-graph", parentId: "07-graph", routeId: "07-graph", lessonNumber: 1 },
          { id: "bfs", label: "广度优先搜索", labelEn: "Breadth-first search", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "07-graph", parentId: "07-graph", routeId: "07-graph", lessonNumber: 2 },
          { id: "dfs", label: "深度优先搜索", labelEn: "Depth-first search", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "07-graph", parentId: "07-graph", routeId: "07-graph", lessonNumber: 3 },
          { id: "shortest-path", label: "最短路径", labelEn: "Shortest paths", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "07-graph", parentId: "07-graph", routeId: "07-graph", lessonNumber: 4 },
          { id: "minimum-spanning-tree", label: "最小生成树", labelEn: "Minimum spanning trees", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "07-graph", parentId: "07-graph", routeId: "07-graph", lessonNumber: 5 },
          { id: "topological-sort", label: "拓扑排序", labelEn: "Topological sorting", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "07-graph", parentId: "07-graph", routeId: "07-graph", lessonNumber: 6 },
        ],
      },
    ],
  },
  {
    id: "search-hash",
    order: 5,
    label: "查找与散列",
    labelEn: "Searching and hashing",
    description: "定位数据与构造索引",
    descriptionEn: "Locating data and building indexes",
    items: [
      {
        id: "08-search",
        label: "查找",
        labelEn: "Searching",
        meta: "第 08 章",
        metaEn: "Chapter 08",
        kind: "chapter",
        chapterId: "08-search",
        chapterNumber: 8,
        children: [
          { id: "sequential-search", label: "顺序查找", labelEn: "Sequential search", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "08-search", parentId: "08-search", routeId: "08-search", lessonNumber: 1 },
          { id: "binary-search", label: "二分查找", labelEn: "Binary search", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "08-search", parentId: "08-search", routeId: "08-search", lessonNumber: 2 },
          { id: "hash-table", label: "散列表", labelEn: "Hash tables", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "08-search", parentId: "08-search", routeId: "08-search", lessonNumber: 3 },
          { id: "balanced-search", label: "平衡查找", labelEn: "Balanced search trees", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "08-search", parentId: "08-search", routeId: "08-search", lessonNumber: 4 },
        ],
      },
    ],
  },
  {
    id: "sorting-complexity",
    order: 6,
    label: "排序与复杂度",
    labelEn: "Sorting and complexity",
    description: "比较策略、稳定性与代价",
    descriptionEn: "Strategies, stability, and cost",
    items: [
      {
        id: "09-internal-sort",
        label: "内部排序",
        labelEn: "Internal sorting",
        meta: "第 09 章",
        metaEn: "Chapter 09",
        kind: "chapter",
        chapterId: "09-internal-sort",
        chapterNumber: 9,
        children: [
          { id: "insertion-sort", label: "插入排序", labelEn: "Insertion sort", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "09-internal-sort", parentId: "09-internal-sort", routeId: "09-internal-sort", lessonNumber: 1 },
          { id: "selection-sort", label: "选择排序", labelEn: "Selection sort", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "09-internal-sort", parentId: "09-internal-sort", routeId: "09-internal-sort", lessonNumber: 2 },
          { id: "merge-sort", label: "归并排序", labelEn: "Merge sort", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "09-internal-sort", parentId: "09-internal-sort", routeId: "09-internal-sort", lessonNumber: 3 },
          { id: "quick-sort", label: "快速排序", labelEn: "Quicksort", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "09-internal-sort", parentId: "09-internal-sort", routeId: "09-internal-sort", lessonNumber: 4 },
          { id: "complexity-review", label: "稳定性与复杂度比较", labelEn: "Stability and complexity", meta: "学习单元", metaEn: "Lesson", kind: "lesson", chapterId: "09-internal-sort", parentId: "09-internal-sort", routeId: "09-internal-sort", lessonNumber: 5 },
        ],
      },
      {
        id: "10-external-sort",
        label: "外部排序",
        labelEn: "External sorting",
        meta: "第 10 章",
        metaEn: "Chapter 10",
        kind: "chapter",
        chapterId: "10-external-sort",
        chapterNumber: 10,
      },
    ],
  },
];

type CourseCatalogDetail = Pick<WorkbenchCourseItem, "summary" | "summaryEn"> & {
  capability?: WorkbenchCourseCapability;
};

/**
 * Concept descriptions belong to the catalog layer so the same curriculum can
 * drive the workbench, chapter picker, and future API adapters. They never
 * claim that the viewer has completed a unit.
 */
const courseCatalogDetails: Record<string, CourseCatalogDetail> = {
  "01-introduction": {
    summary: "认识数据结构、逻辑结构、存储结构和算法评价的共同语言。",
    summaryEn: "Establish the shared vocabulary of structures, storage, and algorithm analysis.",
  },
  "02-linear-list": {
    summary: "比较顺序存储与链式存储，并掌握线性表的基本操作边界。",
    summaryEn: "Compare contiguous and linked storage and reason about linear-list operations.",
  },
  "04-string": {
    summary: "学习串的表示、基本操作与模式匹配问题。",
    summaryEn: "Study string representations, operations, and pattern matching.",
  },
  "05-array-generalized-list": {
    summary: "理解数组、稀疏矩阵压缩和广义表的层次化表示。",
    summaryEn: "Understand arrays, sparse-matrix compression, and generalized lists.",
  },
  "03-stack-queue": {
    summary: "通过受限访问顺序，理解栈、队列和它们的典型应用。",
    summaryEn: "Use constrained access order to understand stacks, queues, and their applications.",
  },
  "06-tree": {
    summary: "用层级结构组织数据，并掌握遍历、查找和优先级维护。",
    summaryEn: "Organize data hierarchically and learn traversal, search, and priority maintenance.",
  },
  "07-graph": {
    summary: "描述顶点与边的关系，解决遍历、连通和路径问题。",
    summaryEn: "Model vertices and edges for traversal, connectivity, and path problems.",
  },
  "08-search": {
    summary: "比较顺序、二分、树表与散列表的查找策略。",
    summaryEn: "Compare sequential, binary, tree-based, and hash-based search strategies.",
  },
  "09-internal-sort": {
    summary: "从比较、交换和归并等角度分析内存内排序。",
    summaryEn: "Analyze in-memory sorting through comparison, exchange, and merge strategies.",
  },
  "10-external-sort": {
    summary: "理解数据无法一次装入内存时的归并与外存访问代价。",
    summaryEn: "Understand merge passes and external-memory costs when data exceeds memory.",
  },
  "linear-list": {
    summary: "从表长、位置和存储方式出发，建立线性表的基本模型。",
    summaryEn: "Build the basic linear-list model through length, position, and storage choices.",
  },
  "sequential-list": {
    summary: "以有序顺序表为例，观察定位、右移和写入三个插入步骤。",
    summaryEn: "Follow insertion into an ordered sequential list through locate, shift, and write steps.",
    capability: "local-interactive-preview",
  },
  "sequential-list-delete": {
    summary: "删除指定位置元素后，将后续元素左移以保持连续存储。",
    summaryEn: "Delete a position and shift following values left to keep contiguous storage.",
  },
  "linked-list": {
    summary: "用节点和指针表达顺序关系，比较访问与插入的代价。",
    summaryEn: "Use nodes and links to compare access and insertion costs.",
  },
  "doubly-linked-list": {
    summary: "利用前驱和后继指针处理双向遍历与循环边界。",
    summaryEn: "Use predecessor and successor links for bidirectional traversal and circular boundaries.",
  },
  stack: {
    summary: "理解后进先出规则，并将其用于括号匹配等嵌套问题。",
    summaryEn: "Apply last-in, first-out order to nested problems such as bracket matching.",
  },
  queue: {
    summary: "理解先进先出、双端操作及它们适用的任务调度场景。",
    summaryEn: "Understand first-in, first-out order, deque operations, and scheduling use cases.",
  },
  "circular-queue": {
    summary: "用循环下标复用顺序队列的存储空间，并区分队空与队满。",
    summaryEn: "Reuse sequential-queue storage with circular indices and distinguish empty from full.",
  },
  "expression-evaluation": {
    summary: "用运算符栈处理优先级、结合性和表达式求值。",
    summaryEn: "Use an operator stack to handle precedence, associativity, and expression evaluation.",
  },
  "binary-tree": {
    summary: "识别二叉树的结点关系、性质和顺序存储或链式存储方式。",
    summaryEn: "Identify binary-tree relationships, properties, and sequential or linked representations.",
  },
  "tree-traversal": {
    summary: "比较先序、中序、后序与层序遍历访问结点的顺序。",
    summaryEn: "Compare preorder, inorder, postorder, and level-order node visits.",
  },
  "binary-search-tree": {
    summary: "利用左小右大的有序性质完成查找、插入和删除。",
    summaryEn: "Use the left-smaller, right-larger invariant for search, insertion, and deletion.",
  },
  heap: {
    summary: "维护堆序性质，并理解优先队列的插入和删除操作。",
    summaryEn: "Maintain heap order and understand priority-queue insertion and removal.",
  },
  "union-find": {
    summary: "通过集合合并与路径压缩处理动态连通性问题。",
    summaryEn: "Use union and path compression to solve dynamic connectivity problems.",
  },
  "graph-storage": {
    summary: "比较邻接矩阵和邻接表在稠密图、稀疏图中的表示代价。",
    summaryEn: "Compare adjacency matrices and lists for dense and sparse graphs.",
  },
  bfs: {
    summary: "按层扩展相邻顶点，建立广度优先搜索树。",
    summaryEn: "Expand adjacent vertices by layer to build a breadth-first search tree.",
  },
  dfs: {
    summary: "沿未访问分支尽可能深入，再在回溯时继续搜索。",
    summaryEn: "Follow an unvisited branch deeply, then continue during backtracking.",
  },
  "shortest-path": {
    summary: "根据边权条件选择合适算法，计算顶点之间的最短路径。",
    summaryEn: "Choose an algorithm by edge-weight conditions to compute shortest paths.",
  },
  "minimum-spanning-tree": {
    summary: "在保持连通的前提下，用最小总代价连接全部顶点。",
    summaryEn: "Connect all vertices with minimum total cost while preserving connectivity.",
  },
  "topological-sort": {
    summary: "在有向无环图中安排满足依赖关系的线性次序。",
    summaryEn: "Order a directed acyclic graph into a sequence that respects dependencies.",
  },
  "sequential-search": {
    summary: "从表头逐项比较，理解无序数据中的直接查找代价。",
    summaryEn: "Compare items from the start and understand direct search in unordered data.",
  },
  "binary-search": {
    summary: "在有序表中不断缩小区间，理解对数级查找。",
    summaryEn: "Repeatedly halve a sorted range to understand logarithmic search.",
  },
  "hash-table": {
    summary: "用散列函数定位桶位，并处理冲突与装填因子。",
    summaryEn: "Use a hash function to locate buckets and handle collisions and load factor.",
  },
  "balanced-search": {
    summary: "通过平衡条件避免查找树退化，维持较稳定的查找性能。",
    summaryEn: "Use balance conditions to avoid search-tree degeneration and preserve lookup performance.",
  },
  "insertion-sort": {
    summary: "将当前元素插入已排序区间，观察局部移动与稳定性。",
    summaryEn: "Insert each value into a sorted prefix and observe local shifts and stability.",
  },
  "selection-sort": {
    summary: "反复选择未排序区间的最值，并分析比较次数与交换次数。",
    summaryEn: "Repeatedly select an extreme from the unsorted range and analyze comparisons and swaps.",
  },
  "merge-sort": {
    summary: "分解子序列后归并有序结果，理解分治策略与额外空间。",
    summaryEn: "Split sequences and merge ordered results to study divide-and-conquer and extra space.",
  },
  "quick-sort": {
    summary: "围绕枢轴划分区间，比较不同划分对递归深度的影响。",
    summaryEn: "Partition around a pivot and compare how partitions affect recursion depth.",
  },
  "complexity-review": {
    summary: "从稳定性、时间复杂度、空间复杂度和适用数据规模比较排序算法。",
    summaryEn: "Compare sorting algorithms by stability, time, space, and suitable data scale.",
  },
};

function materializeCourseItem(item: WorkbenchCourseSeedItem): WorkbenchCourseItem {
  const details = courseCatalogDetails[item.id];
  if (!details) throw new Error(`Missing catalog details for course item: ${item.id}`);
  return {
    ...item,
    summary: details.summary,
    summaryEn: details.summaryEn,
    capability: details.capability ?? "catalog",
    children: item.children?.map(materializeCourseItem),
  };
}

/**
 * The exported catalog is complete and immutable by convention. Its default
 * state is a browseable catalog; delivery capabilities are explicit on each
 * chapter or unit rather than inferred from a current user.
 */
export const learningCourseGroups: WorkbenchCourseGroup[] = courseGroupSeeds.map((group) => ({
  ...group,
  summary: group.description,
  summaryEn: group.descriptionEn,
  capability: "catalog",
  items: group.items.map(materializeCourseItem),
}));

/**
 * Legacy consumers still expect the original four-item outline. Keep it as a
 * compatibility projection while new surfaces consume `courseGroups`.
 */
// Keep a flat projection for adapters and old callers, but project the full
// curriculum rather than the retired four-row linear-list slice. Visible
// navigation consumes `courseGroups`; this compatibility field must never
// make the product look smaller than the real course map.
const learningCourseOutline: WorkbenchCourseItem[] = flattenCourseGroups(learningCourseGroups);

function cloneCourseItem(item: WorkbenchCourseItem): WorkbenchCourseItem {
  const clone: WorkbenchCourseItem = { ...item };
  if (item.children) clone.children = item.children.map(cloneCourseItem);
  return clone;
}

/** Flatten the grouped catalog for list/search consumers without mutating it. */
export function flattenCourseGroups(
  groups: readonly WorkbenchCourseGroup[] = learningCourseGroups,
  options: { includeLessons?: boolean } = {},
): WorkbenchCourseItem[] {
  const includeLessons = options.includeLessons ?? true;
  const result: WorkbenchCourseItem[] = [];
  const visit = (item: WorkbenchCourseItem) => {
    if (includeLessons || item.kind !== "lesson") result.push(cloneCourseItem(item));
    item.children?.forEach(visit);
  };
  groups.slice().sort((a, b) => a.order - b.order).forEach((group) => group.items.forEach(visit));
  return result;
}

export function findCourseItem(itemId: string, groups: readonly WorkbenchCourseGroup[] = learningCourseGroups): WorkbenchCourseItem | null {
  return flattenCourseGroups(groups).find((item) => item.id === itemId) ?? null;
}

export function findCourseGroup(groupId: WorkbenchCourseGroupId, groups: readonly WorkbenchCourseGroup[] = learningCourseGroups): WorkbenchCourseGroup | null {
  return groups.find((group) => group.id === groupId) ?? null;
}

export function courseItemRouteId(item: Pick<WorkbenchCourseItem, "id" | "routeId">): string {
  return item.routeId ?? item.id;
}

/**
 * Resolve the two ids needed when a catalog row opens another learning
 * surface. A lesson's own `id` selects its scene; `parentId`/`chapterId`
 * scopes the surrounding course context. `routeId` is intentionally only a
 * fallback for chapter rows because several legacy lesson rows reuse their
 * parent's route id.
 */
export function courseItemNavigationContext(
  item: Pick<WorkbenchCourseItem, "id" | "routeId" | "kind" | "chapterId" | "parentId">,
): { chapterId: string; lessonId?: string } {
  const routeId = courseItemRouteId(item);
  if (item.kind === "lesson") {
    const chapterId = item.parentId ?? item.chapterId ?? routeId;
    return {
      chapterId,
      lessonId: item.id,
    };
  }
  return { chapterId: item.chapterId ?? routeId };
}

export function localizedCourseGroups(
  locale: "zh-CN" | "en-US",
  groups: readonly WorkbenchCourseGroup[] = learningCourseGroups,
): WorkbenchCourseGroup[] {
  const english = locale === "en-US";
  const localize = (item: WorkbenchCourseItem): WorkbenchCourseItem => {
    const localized = cloneCourseItem(item);
    localized.label = english ? (item.labelEn ?? item.label) : item.label;
    localized.meta = english ? (item.metaEn ?? item.meta) : item.meta;
    localized.summary = english ? item.summaryEn : item.summary;
    localized.summaryEn = item.summaryEn;
    if (localized.children) localized.children = localized.children.map(localize);
    return localized;
  };
  return groups.slice().sort((a, b) => a.order - b.order).map((group) => ({
    ...group,
    label: english ? group.labelEn : group.label,
    description: english ? group.descriptionEn : group.description,
    summary: english ? group.summaryEn : group.summary,
    items: group.items.map(localize),
  }));
}

export function localizedCourseOutline(
  locale: "zh-CN" | "en-US",
  groups: readonly WorkbenchCourseGroup[] = learningCourseGroups,
): WorkbenchCourseItem[] {
  return localizedCourseGroups(locale, groups).flatMap((group) => group.items.flatMap((item) => {
    const descendants = flattenCourseGroups([{ ...group, items: [item] }]);
    return descendants;
  }));
}

const chapterGroupAliases: Record<string, WorkbenchCourseGroupId> = {
  "01-introduction": "linear-structures",
  "02-linear-list": "linear-structures",
  "linear-list": "linear-structures",
  "sequential-list": "linear-structures",
  "sequential-list-delete": "linear-structures",
  "linked-list": "linear-structures",
  "doubly-linked-list": "linear-structures",
  "04-string": "linear-structures",
  "05-array-generalized-list": "linear-structures",
  "03-stack-queue": "stacks-queues",
  stack: "stacks-queues",
  queue: "stacks-queues",
  "circular-queue": "stacks-queues",
  "expression-evaluation": "stacks-queues",
  "06-tree": "trees-heaps",
  "binary-tree": "trees-heaps",
  "tree-traversal": "trees-heaps",
  "binary-search-tree": "trees-heaps",
  heap: "trees-heaps",
  "union-find": "trees-heaps",
  "07-graph": "graphs",
  "graph-storage": "graphs",
  bfs: "graphs",
  dfs: "graphs",
  "shortest-path": "graphs",
  "minimum-spanning-tree": "graphs",
  "topological-sort": "graphs",
  "08-search": "search-hash",
  "sequential-search": "search-hash",
  "binary-search": "search-hash",
  "hash-table": "search-hash",
  "balanced-search": "search-hash",
  "09-internal-sort": "sorting-complexity",
  "10-external-sort": "sorting-complexity",
  "insertion-sort": "sorting-complexity",
  "selection-sort": "sorting-complexity",
  "merge-sort": "sorting-complexity",
  "quick-sort": "sorting-complexity",
  "complexity-review": "sorting-complexity",
};

/** Return the stable group id for a chapter/lesson id from either fixture or API. */
export function courseGroupIdForChapter(chapterId: string): WorkbenchCourseGroupId {
  const normalized = chapterId.trim().toLocaleLowerCase();
  if (chapterGroupAliases[normalized]) return chapterGroupAliases[normalized];
  if (/(stack|queue)/.test(normalized)) return "stacks-queues";
  if (/(tree|heap|union)/.test(normalized)) return "trees-heaps";
  if (/(graph|bfs|dfs|path)/.test(normalized)) return "graphs";
  if (/(search|hash)/.test(normalized)) return "search-hash";
  if (/(sort|complex)/.test(normalized)) return "sorting-complexity";
  return "linear-structures";
}

const englishChapterTitles: Record<string, string> = {
  "01-introduction": "Introduction",
  "02-linear-list": "Linear lists",
  "03-stack-queue": "Stacks and queues",
  "04-string": "Strings",
  "05-array-generalized-list": "Arrays and generalized lists",
  "06-tree": "Trees and binary trees",
  "07-graph": "Graphs",
  "08-search": "Searching",
  "09-internal-sort": "Internal sorting",
  "10-external-sort": "External sorting",
};

/** Adapt a flat API chapter list into the same six-section shape as the fixture. */
export function groupChaptersByCourseTopic(
  chapters: readonly Chapter[],
  currentChapterId?: string | null,
): WorkbenchCourseGroup[] {
  const byGroup = new Map<WorkbenchCourseGroupId, WorkbenchCourseItem[]>();
  learningCourseGroups.forEach((group) => byGroup.set(group.id, []));
  const seen = new Set<string>();
  const normalizedCurrent = currentChapterId?.trim() ?? "";
  const currentItem = normalizedCurrent ? findCourseItem(normalizedCurrent) : null;
  const canonicalCurrent = currentItem?.kind === "lesson"
    ? currentItem.parentId ?? currentItem.chapterId ?? normalizedCurrent
    : normalizedCurrent;
  chapters.slice().sort((a, b) => a.chapterNumber - b.chapterNumber).forEach((chapter) => {
    const id = chapter.id.trim();
    const seenKey = id.toLocaleLowerCase();
    if (!id || seen.has(seenKey)) return;
    seen.add(seenKey);
    const groupId = courseGroupIdForChapter(id);
    const item: WorkbenchCourseItem = {
      id,
      routeId: id,
      chapterId: id,
      chapterNumber: chapter.chapterNumber,
      kind: "chapter",
      label: chapter.title,
      labelEn: englishChapterTitles[id] ?? chapter.title,
      meta: `第 ${String(chapter.chapterNumber).padStart(2, "0")} 章`,
      metaEn: `Chapter ${String(chapter.chapterNumber).padStart(2, "0")}`,
      summary: chapter.summary,
      summaryEn: chapter.summary,
      capability: "api-published",
      current: id === normalizedCurrent || id === canonicalCurrent,
    };
    byGroup.get(groupId)?.push(item);
  });
  return learningCourseGroups.map((group) => ({
    ...group,
    items: byGroup.get(group.id) ?? [],
  }));
}

/**
 * A deliberately separate local learning scene. It is a teaching preview,
 * never a production metric or a persisted learning record.
 */
export const learningWorkbenchFixture: WorkbenchFixture = {
  mode: "fixture",
  goalId: "sequential-list-insertion-boundary",
  goalStatus: "active",
  courseLabel: "数据结构与算法",
  courseCode: "DSA / 01",
  topic: "顺序表的插入",
  topicSummary: "把一个新元素放到有序位置，先让位，再把指针交给它。",
  structureLabel: "顺序表",
  structureType: "SEQUENTIAL_LIST",
  initialNodes: [12, 18, 27, 31, 44],
  insertionValue: 23,
  insertionIndex: 2,
  goal: "理解插入的移动边界",
  goalDetail: "能指出最后一个需要右移的元素，并解释为什么从尾部开始。",
  source: "线性表 · 第 3 节",
  sourceDetail: "本地演示来源，不代表已发布课程资料。",
  nextAction: "用一句话解释复杂度",
  nextDetail: "完成舞台后进入问答，把 O(n) 的原因说清楚。",
  steps: [
    { id: "inspect", index: 0, label: "定位空位", detail: "23 应该落在 18 和 27 之间。", kind: "inspect" },
    { id: "compare", index: 1, label: "比较 27", detail: "27 大于 23，当前位置需要让出一格。", kind: "compare" },
    { id: "shift", index: 2, label: "右移 27", detail: "从尾部向前移动，避免覆盖尚未读取的值。", kind: "shift" },
    { id: "insert", index: 3, label: "写入 23", detail: "空位交给新节点，顺序表保持有序。", kind: "insert" },
  ],
  courseOutline: learningCourseOutline,
  courseGroups: learningCourseGroups,
};

export function nodesAtWorkbenchStep(stepIndex: number): Array<{ value: number; state: "base" | "active" | "inserted" | "shifted" }> {
  const fixture = learningWorkbenchFixture;
  if (stepIndex >= 3) {
    return [
      { value: 12, state: "base" },
      { value: 18, state: "base" },
      { value: fixture.insertionValue, state: "inserted" },
      { value: 27, state: "shifted" },
      { value: 31, state: "base" },
      { value: 44, state: "base" },
    ];
  }
  return fixture.initialNodes.map((value, index) => ({
    value,
    state: index === (stepIndex === 0 ? fixture.insertionIndex : fixture.insertionIndex + 1)
      ? "active"
      : stepIndex === 2 && index >= fixture.insertionIndex
        ? "shifted"
        : "base",
  }));
}
