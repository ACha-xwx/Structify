import type { KnowledgeSearchResult } from "../../shared/types/contracts";
import {
  flattenCourseGroups,
  findCourseItem,
  learningCourseGroups,
  type WorkbenchCourseItem,
} from "./learning-workbench";

/**
 * Guest knowledge is a small, local content index. It is deliberately kept
 * separate from the API adapter so a preview can never look like a server
 * response or a learner's saved record.
 */
export interface KnowledgePreviewFixture {
  mode: "fixture";
  label: string;
  detail: string;
  results: KnowledgeSearchResult[];
}

export interface KnowledgePreviewSuggestion {
  query: string;
  chapterId: string;
  label: string;
  labelEn: string;
}

/** A short, fixed set of discoverable topics for the empty guest state. */
export const knowledgePreviewSuggestions: KnowledgePreviewSuggestion[] = [
  { query: "顺序栈", chapterId: "03-stack-queue", label: "顺序栈", labelEn: "Sequential stack" },
  { query: "树的遍历", chapterId: "06-tree", label: "树的遍历", labelEn: "Tree traversal" },
  { query: "广度优先搜索", chapterId: "07-graph", label: "广度优先搜索", labelEn: "Breadth-first search" },
  { query: "二分查找", chapterId: "08-search", label: "二分查找", labelEn: "Binary search" },
  { query: "归并排序", chapterId: "09-internal-sort", label: "归并排序", labelEn: "Merge sort" },
];

type PreviewContent = {
  title?: string;
  excerpt: string;
  keywords?: string[];
  kind?: KnowledgeSearchResult["kind"];
};

const previewContentById: Record<string, PreviewContent> = {
  "01-introduction": {
    excerpt: "建立数据、结构、存储和算法之间的共同语言，再用复杂度描述代价。",
    keywords: ["数据结构", "算法", "复杂度", "绪论"],
  },
  "02-linear-list": {
    excerpt: "比较连续存储与链式存储，理解线性表访问、插入和删除的边界。",
    keywords: ["线性表", "顺序表", "链表"],
  },
  "linear-list": {
    excerpt: "从表长、位置和存储方式出发，建立线性表的基本模型。",
    keywords: ["线性表", "表长", "位置"],
  },
  "sequential-list": {
    excerpt: "先定位插入位置，再从尾部右移元素，最后把新值写入空位。",
    keywords: ["顺序表", "插入", "右移", "尾部", "空位"],
    kind: "answer",
  },
  "sequential-list-delete": {
    excerpt: "删除指定位置后，将后续元素左移，保持顺序表连续且有序。",
    keywords: ["顺序表", "删除", "左移", "连续存储"],
  },
  "linked-list": {
    excerpt: "通过节点和指针表达顺序关系，比较链表访问与插入的代价。",
    keywords: ["单链表", "节点", "指针", "链表"],
  },
  "doubly-linked-list": {
    excerpt: "利用前驱与后继指针完成双向遍历，并处理循环链表的边界。",
    keywords: ["双向链表", "循环链表", "前驱", "后继"],
  },
  "03-stack-queue": {
    excerpt: "从受限访问顺序出发，理解栈、队列及其在表达式和调度中的应用。",
    keywords: ["栈", "顺序栈", "队列", "先进先出", "后进先出"],
  },
  stack: {
    title: "顺序栈的入栈条件",
    excerpt: "顺序栈的入栈条件是栈顶未越过容量边界；入栈和出栈都从栈顶开始。",
    keywords: ["栈", "顺序栈", "入栈", "出栈", "括号匹配", "后进先出"],
    kind: "answer",
  },
  queue: {
    excerpt: "用队头和队尾维护先进先出顺序，并认识双端队列的两端操作。",
    keywords: ["队列", "顺序队列", "队头", "队尾", "先进先出", "双端队列"],
  },
  "circular-queue": {
    excerpt: "用循环下标复用队列空间，同时区分队空、队满和有效元素数量。",
    keywords: ["循环队列", "队空", "队满", "取模"],
  },
  "expression-evaluation": {
    excerpt: "借助运算符栈处理优先级和结合性，再逐步完成中缀表达式求值。",
    keywords: ["表达式", "求值", "运算符栈", "优先级", "结合性"],
  },
  "04-string": {
    excerpt: "学习串的顺序表示、基本操作和模式匹配，理解字符位置与前缀信息。",
    keywords: ["串", "字符串", "模式匹配", "KMP", "前缀"],
  },
  "05-array-generalized-list": {
    excerpt: "通过数组、稀疏矩阵和广义表观察多维数据的压缩与层次化表示。",
    keywords: ["数组", "稀疏矩阵", "广义表", "压缩存储"],
  },
  "06-tree": {
    excerpt: "用层级结构组织数据，串联二叉树遍历、查找、堆和并查集。",
    keywords: ["树", "二叉树", "遍历", "堆", "并查集"],
  },
  "binary-tree": {
    excerpt: "认识二叉树的左右孩子关系和基本性质，并比较顺序与链式存储。",
    keywords: ["二叉树", "结点", "左右孩子", "存储"],
  },
  "tree-traversal": {
    excerpt: "比较先序、中序、后序和层序遍历，追踪同一棵树的访问顺序。",
    keywords: ["树的遍历", "先序", "中序", "后序", "层序"],
  },
  "binary-search-tree": {
    excerpt: "利用左小右大的不变量完成二叉搜索树的查找、插入与删除。",
    keywords: ["二叉搜索树", "二叉排序树", "查找", "插入", "删除"],
  },
  heap: {
    excerpt: "维护堆序性质，观察优先队列插入、上浮和删除最值的过程。",
    keywords: ["堆", "大根堆", "小根堆", "优先队列", "上浮", "下沉"],
  },
  "union-find": {
    excerpt: "通过集合合并、按秩合并和路径压缩处理动态连通性问题。",
    keywords: ["并查集", "合并", "路径压缩", "连通性"],
  },
  "07-graph": {
    excerpt: "描述顶点与边的关系，串联图的表示、遍历、连通性和路径算法。",
    keywords: ["图", "顶点", "边", "遍历", "路径"],
  },
  "graph-storage": {
    excerpt: "比较邻接矩阵与邻接表在稠密图、稀疏图中的空间和访问代价。",
    keywords: ["图的存储", "邻接矩阵", "邻接表", "稠密图", "稀疏图"],
  },
  bfs: {
    excerpt: "从起点按层扩展相邻顶点，用队列记录边界并生成广度优先树。",
    keywords: ["BFS", "广度优先搜索", "广搜", "队列", "按层"],
  },
  dfs: {
    excerpt: "沿未访问分支尽可能深入，再在回溯时继续搜索并记录访问次序。",
    keywords: ["DFS", "深度优先搜索", "深搜", "递归", "回溯"],
  },
  "shortest-path": {
    excerpt: "根据边权条件选择合适算法，计算顶点之间的最短路径和前驱。",
    keywords: ["最短路径", "Dijkstra", "Floyd", "边权", "前驱"],
  },
  "minimum-spanning-tree": {
    excerpt: "在保持连通的前提下，用最小总代价连接全部顶点并避免环。",
    keywords: ["最小生成树", "Prim", "Kruskal", "连通", "环"],
  },
  "topological-sort": {
    excerpt: "在有向无环图中安排满足依赖关系的线性次序，并识别环路。",
    keywords: ["拓扑排序", "有向无环图", "DAG", "入度", "依赖"],
  },
  "08-search": {
    excerpt: "比较顺序查找、二分查找、树表查找与散列查找的适用边界。",
    keywords: ["查找", "顺序查找", "二分查找", "散列查找"],
  },
  "sequential-search": {
    excerpt: "从表头逐项比较，在无序数据中用线性扫描定位目标。",
    keywords: ["顺序查找", "线性查找", "逐项比较"],
  },
  "binary-search": {
    excerpt: "在有序区间中不断折半，利用比较结果将查找范围缩小一半。",
    keywords: ["二分查找", "折半查找", "有序表", "对数"],
  },
  "hash-table": {
    excerpt: "用散列函数定位桶位，并通过冲突处理和装填因子控制查找代价。",
    keywords: ["哈希", "散列", "散列表", "哈希表", "冲突", "装填因子"],
  },
  "balanced-search": {
    excerpt: "通过平衡条件避免查找树退化，维持较稳定的查找和更新性能。",
    keywords: ["平衡查找", "AVL", "红黑树", "旋转", "树高"],
  },
  "09-internal-sort": {
    excerpt: "从比较、交换、选择和归并等角度分析内存内排序的代价与稳定性。",
    keywords: ["内部排序", "排序", "稳定性", "复杂度"],
  },
  "insertion-sort": {
    excerpt: "将当前元素插入已排序前缀，观察局部移动和稳定排序的条件。",
    keywords: ["插入排序", "直接插入", "稳定", "已排序区间"],
  },
  "selection-sort": {
    excerpt: "反复从未排序区间选择最值，比较交换次数与数据移动成本。",
    keywords: ["选择排序", "简单选择", "最小值", "交换"],
  },
  "merge-sort": {
    excerpt: "先分解子序列，再归并两个有序结果，理解分治和额外空间。",
    keywords: ["归并排序", "分治", "归并", "稳定", "额外空间"],
  },
  "quick-sort": {
    excerpt: "围绕枢轴划分区间，比较不同划分对递归深度和最坏情况的影响。",
    keywords: ["快速排序", "快排", "枢轴", "划分", "递归"],
  },
  "complexity-review": {
    excerpt: "从稳定性、时间复杂度、空间复杂度和数据规模比较排序算法。",
    keywords: ["排序比较", "稳定性", "时间复杂度", "空间复杂度", "选择"],
  },
  "10-external-sort": {
    excerpt: "当数据无法一次装入内存时，用归并轮次和外存访问次数衡量代价。",
    keywords: ["外部排序", "外存", "多路归并", "归并段", "磁盘"],
  },
};

const contextAliases: Record<string, string> = {
  "01-introduction": "01-introduction",
  "02-linear-list": "02-linear-list",
  "03-stack-queue": "03-stack-queue",
  "04-string": "04-string",
  "05-array-generalized-list": "05-array-generalized-list",
  "06-tree": "06-tree",
  "07-graph": "07-graph",
  "08-search": "08-search",
  "09-internal-sort": "09-internal-sort",
  "10-external-sort": "10-external-sort",
  "linear-list": "02-linear-list",
  "sequential-list": "02-linear-list",
  "sequential-list-delete": "02-linear-list",
  "linked-list": "02-linear-list",
  "doubly-linked-list": "02-linear-list",
  stack: "03-stack-queue",
  queue: "03-stack-queue",
  "circular-queue": "03-stack-queue",
  "expression-evaluation": "03-stack-queue",
  "binary-tree": "06-tree",
  "tree-traversal": "06-tree",
  "binary-search-tree": "06-tree",
  heap: "06-tree",
  "union-find": "06-tree",
  "graph-storage": "07-graph",
  bfs: "07-graph",
  dfs: "07-graph",
  "shortest-path": "07-graph",
  "minimum-spanning-tree": "07-graph",
  "topological-sort": "07-graph",
  "sequential-search": "08-search",
  "binary-search": "08-search",
  "hash-table": "08-search",
  "balanced-search": "08-search",
  "insertion-sort": "09-internal-sort",
  "selection-sort": "09-internal-sort",
  "merge-sort": "09-internal-sort",
  "quick-sort": "09-internal-sort",
  "complexity-review": "09-internal-sort",
};

const previewItems = flattenCourseGroups(learningCourseGroups).filter((item) => item.kind === "chapter" || item.kind === "lesson");
const previewItemsById = new Map(previewItems.map((item) => [item.id, item]));

function chapterForItem(item: WorkbenchCourseItem): string {
  return item.kind === "lesson"
    ? item.parentId ?? item.chapterId ?? item.id
    : item.chapterId ?? item.id;
}

function chapterNumberLabel(item: WorkbenchCourseItem): string {
  const chapter = item.chapterNumber ?? previewItemsById.get(chapterForItem(item))?.chapterNumber;
  return chapter ? String(chapter).padStart(2, "0") : "00";
}

function lessonNumberLabel(item: WorkbenchCourseItem): string | null {
  if (item.kind !== "lesson") return null;
  return `${chapterNumberLabel(item)}-${String(item.lessonNumber ?? 1).padStart(2, "0")}`;
}

function createPreviewResult(item: WorkbenchCourseItem, index: number): KnowledgeSearchResult {
  const content = previewContentById[item.id] ?? { excerpt: item.summary, keywords: [item.label] };
  const parent = item.parentId ? previewItemsById.get(item.parentId) : null;
  // Keep the verified local scene addressable as its own lesson scope. Other
  // lessons stay attached to their canonical parent chapter.
  const chapterId = item.id === "sequential-list" ? item.id : chapterForItem(item);
  const locationLabel = parent ? `${parent.label} · ${item.label}` : item.label;
  const result: KnowledgeSearchResult = {
    id: `preview-knowledge-${item.id}`,
    // Keep the historical lesson alias addressable while context matching
    // below resolves it back to the canonical parent chapter.
    chapterId: item.id === "sequential-list" ? "sequential-list" : chapterId,
    title: content.title ?? (item.kind === "chapter" ? `${item.label}：关键概念` : item.label),
    lessonNumber: lessonNumberLabel(item),
    kind: content.kind ?? (index % 3 === 0 ? "answer" : "textbook"),
    source: "数据结构课程预览",
    pageLabel: item.kind === "lesson" ? `单元 ${lessonNumberLabel(item)}` : `章节 ${chapterNumberLabel(item)}`,
    sourceLabel: "本地 fixture",
    locationLabel,
    reviewStatus: "预览内容",
    publicationStatus: "PUBLISHED",
    excerpt: content.excerpt,
    score: 1,
    ...(item.kind === "lesson" ? { lessonId: item.id } : {}),
  };
  return result;
}

export const knowledgePreviewFixture: KnowledgePreviewFixture = {
  mode: "fixture",
  label: "本地资料预览",
  detail: "覆盖课程目录中的章节与学习单元，仅用于游客浏览，不代表已发布课程或个人记录。",
  results: previewItems.map(createPreviewResult),
};

function normalize(value: string): string {
  return value.trim().toLocaleLowerCase().replace(/\s+/g, "");
}

const queryAliases: Record<string, string[]> = {
  顺序栈: ["栈", "入栈", "出栈"],
  堆栈: ["栈", "入栈", "出栈"],
  栈: ["顺序栈", "入栈", "出栈"],
  广搜: ["bfs", "广度优先搜索"],
  深搜: ["dfs", "深度优先搜索"],
  哈希: ["散列", "散列表", "hash"],
  散列: ["哈希", "散列表", "hash"],
  快排: ["快速排序"],
  折半查找: ["二分查找"],
};

function expandedQueryTerms(query: string): string[] {
  const normalized = normalize(query);
  const terms = new Set([normalized]);
  Object.entries(queryAliases).forEach(([alias, expansions]) => {
    if (normalized.includes(normalize(alias))) expansions.forEach((term) => terms.add(normalize(term)));
  });
  return [...terms].filter(Boolean);
}

function matchesChapter(result: KnowledgeSearchResult, chapterId: string, lessonId: string): boolean {
  const requestedLesson = normalize(lessonId);
  if (requestedLesson && normalize(result.lessonId ?? "") !== requestedLesson) return false;
  const requestedChapter = normalize(chapterId);
  if (!requestedChapter) return true;
  const resultChapter = normalize(result.chapterId ?? "");
  const canonicalResult = contextAliases[normalize(result.lessonId ?? resultChapter)] ?? resultChapter;
  const canonicalRequested = contextAliases[requestedChapter] ?? requestedChapter;
  return canonicalResult === canonicalRequested
    || resultChapter === canonicalRequested
    || resultChapter === requestedChapter
    || normalize(result.lessonId ?? "") === requestedChapter;
}

/** Search only the local preview index; this is never used for authenticated API results. */
export function searchKnowledgePreview(
  query: string,
  chapterId = "",
  limit = 4,
  lessonId = "",
): KnowledgeSearchResult[] {
  const normalizedQuery = normalize(query);
  const terms = expandedQueryTerms(query);
  if (!terms.length) return [];
  const matchingResults = knowledgePreviewFixture.results
    .filter((result) => matchesChapter(result, chapterId, lessonId))
    .map((result) => {
      const content = previewContentById[result.lessonId ?? result.chapterId ?? ""];
      const searchable = normalize([
        result.title,
        result.excerpt,
        result.locationLabel,
        ...(content?.keywords ?? []),
      ].join(" "));
      const title = normalize(result.title);
      const directMatch = searchable.includes(normalizedQuery);
      const titleMatch = title.includes(normalizedQuery);
      const aliasMatch = terms.some((term) => searchable.includes(term));
      return { result, directMatch, titleMatch, aliasMatch };
    })
    .filter((entry) => entry.aliasMatch);

  // A specific phrase should not fan out into every related operation. Keep
  // direct matches first, and when a lesson title carries the phrase, use that
  // focused teaching card instead of a broad chapter overview.
  const directMatches = matchingResults.filter((entry) => entry.directMatch);
  const titleMatches = directMatches.filter((entry) => entry.titleMatch);
  const narrowedResults = titleMatches.length ? titleMatches : directMatches.length ? directMatches : matchingResults;
  return narrowedResults
    .sort((left, right) => Number(right.titleMatch) - Number(left.titleMatch))
    .map((entry) => entry.result)
    .slice(0, Math.max(1, Math.min(6, limit)));
}

/** Resolve a lesson id to the parent chapter used by chapter-scoped routes. */
export function previewKnowledgeChapterId(lessonId: string): string | null {
  const item = findCourseItem(lessonId);
  return item ? chapterForItem(item) : null;
}
