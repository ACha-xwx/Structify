/**
 * The shape of the learning map, taken from the design the course team drew: the same nodes, the same
 * labels, the same neighbourhood of each node, at the same coordinates.
 *
 * <p>It is a fixed picture on purpose. A layout computed from the data rearranges itself every time a
 * chapter is folded, and the point of this map is that a learner recognises it: the course they are being
 * taught, drawn the way the course itself is drawn.
 *
 * <p>Our courseware has chapters the design does not (绪论, 串) and splits sorting in two; those are merged
 * into the nearest node rather than dropped, so no activity is lost from the picture.
 */
export interface MapNodeSpec {
  id: string;
  title: string;
  x: number;
  y: number;
  parentId: string | null;
  /** Chapter ids of the courseware that feed this node. Empty for a node that only groups others. */
  chapters: string[];
  /** Lesson titles of the courseware that feed this node: matched by keyword, in order of preference. */
  lessons?: string[];
}

const CANVAS = { width: 1815, height: 1010 };

export const MAP_SPECS: MapNodeSpec[] = [
  { id: "root", title: "数据结构", x: 365, y: 530, parentId: null, chapters: [] },

  { id: "linear-list", title: "线性表", x: 543, y: 163, parentId: "root", chapters: ["02-linear-list", "01-introduction"] },
  { id: "queue", title: "队列", x: 620, y: 282, parentId: "root", chapters: ["03-stack-queue"] },
  { id: "array", title: "数组和广义表", x: 674, y: 397, parentId: "root", chapters: ["05-array-generalized-list", "04-string"] },
  { id: "tree", title: "树和二叉树", x: 676, y: 506, parentId: "root", chapters: ["06-tree"] },
  { id: "graph", title: "图", x: 677, y: 612, parentId: "root", chapters: ["07-graph"] },
  { id: "sort", title: "排序算法", x: 638, y: 716, parentId: "root", chapters: ["09-internal-sort", "10-external-sort"] },
  { id: "search", title: "查找", x: 520, y: 826, parentId: "root", chapters: ["08-search"] },

  { id: "queue-definition", title: "队列的定义", x: 888, y: 182, parentId: "queue", chapters: [], lessons: ["队列的定义"] },
  { id: "queue-sequential", title: "顺序队列", x: 975, y: 268, parentId: "queue", chapters: [], lessons: ["顺序队列", "顺序存储"] },
  { id: "queue-linked", title: "链队列", x: 897, y: 353, parentId: "queue", chapters: [], lessons: ["链队列", "链式存储"] },

  { id: "tree-definition", title: "树的定义", x: 948, y: 438, parentId: "tree", chapters: [], lessons: ["树的定义", "树的基本概念"] },
  { id: "binary-tree", title: "二叉树", x: 1008, y: 523, parentId: "tree", chapters: [], lessons: ["二叉树"] },
  { id: "forest", title: "树和森林", x: 935, y: 605, parentId: "tree", chapters: [], lessons: ["树和森林", "树与森林"] },

  { id: "binary-nature", title: "二叉树的定义与性质", x: 1253, y: 372, parentId: "binary-tree", chapters: [], lessons: ["二叉树的定义与性质", "定义与性质"] },
  { id: "binary-storage", title: "二叉树的存储结构", x: 1308, y: 456, parentId: "binary-tree", chapters: [], lessons: ["存储结构"] },
  { id: "traverse", title: "遍历二叉树", x: 1312, y: 543, parentId: "binary-tree", chapters: [], lessons: ["遍历二叉树", "遍历"] },
  { id: "threaded", title: "线索二叉树", x: 1253, y: 672, parentId: "binary-tree", chapters: [], lessons: ["线索二叉树", "线索化"] },

  { id: "recursion", title: "递归", x: 1528, y: 490, parentId: "traverse", chapters: [], lessons: ["递归"] },
  { id: "iteration", title: "非递归", x: 1528, y: 592, parentId: "traverse", chapters: [], lessons: ["非递归", "迭代"] },

  { id: "static-search", title: "静态查找表", x: 785, y: 743, parentId: "search", chapters: [], lessons: ["基本概念与线性表查找", "线性表查找", "静态查找"] },
  { id: "dynamic-search", title: "动态查找表", x: 860, y: 828, parentId: "search", chapters: [], lessons: ["二叉排序树", "平衡二叉排序树", "B树", "动态查找"] },
  { id: "hash", title: "哈希表", x: 790, y: 932, parentId: "search", chapters: [], lessons: ["哈希", "散列"] },
];

export const MAP_SIZE = CANVAS;
