import type { TextbookCodeExample } from "../shared/types/contracts";
import type { Locale } from "../shared/i18n/locale";
import { translate } from "../shared/i18n/messages";
import { compilerTemplates } from "./templates";

type Label = { zh: string; en: string };
interface Topic { id: string; label: Label; files: string[]; aliases: string[] }
interface Chapter { id: string; label: Label; topics: Topic[] }

export interface LibraryEntry {
  id: string;
  title: string;
  group: "samples" | "examples" | "templates";
  origin: string;
  sourceFile: string;
  chapterId: string;
  topicId: string;
  code: string;
  stdin: string;
  kind: "sample" | "algorithm" | "type" | "template";
  example: TextbookCodeExample | null;
  blocked: string;
}

export interface LibraryMenuItem {
  id: string;
  label: string;
  origin?: string;
  keywords?: string;
  kind?: LibraryEntry["kind"];
  children?: LibraryMenuItem[];
}

export interface LibraryMenu {
  id: string;
  label: string;
  items: LibraryMenuItem[];
  count: number;
}

function topic(id: string, zh: string, en: string, files: string, aliases: string[] = []): Topic {
  return { id, label: { zh, en }, files: files.split(" ").filter(Boolean), aliases: [zh, en, ...aliases] };
}

// Source filenames distinguish structures even when a lesson covers several of them.
export const compilerChapters: Chapter[] = [
  { id: "01", label: { zh: "绪论", en: "Introduction" }, topics: [] },
  { id: "02", label: { zh: "线性表", en: "Linear lists" }, topics: [
    topic("seqlist", "顺序表", "Sequential list", "seqlist"),
    topic("linklist", "单链表", "Singly linked list", "linklist linklist1", ["链表"]),
    topic("clinklist", "循环链表", "Circular linked list", "clinklist"),
    topic("dlinklist", "双向循环链表", "Doubly circular linked list", "dlinklist", ["双向链表"]),
    topic("staticlist", "静态链表", "Static linked list", "staticlist"),
    topic("polylist", "一元多项式", "Polynomial list", "polylist"),
  ] },
  { id: "03", label: { zh: "栈与队列", en: "Stacks and queues" }, topics: [
    topic("seqstack", "顺序栈", "Sequential stack", "seqstack seqstack1 seqstack2 3.1 3.2 3.3 3.4"),
    topic("dqstack", "双端栈", "Shared double stack", "dqstack 3.5 3.6 3.7", ["两栈共享", "双端顺序栈"]),
    topic("linkstack", "链栈", "Linked stack", "linkstack 3.8 3.9"),
    topic("multistack", "多链栈", "Multiple linked stacks", "mltliststack 3.10 3.11", ["多个链栈", "共用的某个链栈"]),
    topic("brackets", "括号匹配", "Bracket matching", "3.12"),
    topic("expression", "表达式求值", "Expression evaluation", "3.13"),
    topic("hanoi", "汉诺塔", "Tower of Hanoi", "3.14 3.15", ["hanoi"]),
    topic("fibonacci", "斐波那契", "Fibonacci", "3.16"),
    topic("factorial", "阶乘", "Factorial", "3.17"),
    topic("linkqueue", "链队列", "Linked queue", "linkqueue 3.18 3.19 3.20"),
    topic("seqqueue", "循环队列", "Circular queue", "seqqueue seqqueue1 seqqueue2 3.21 3.22 3.23"),
    topic("yanghui", "杨辉三角", "Pascal's triangle", "3.24"),
    topic("buffer", "键盘输入缓冲队列", "Keyboard input buffer", "3.25"),
    topic("parking", "停车场模拟", "Parking simulation", "car", ["停车场"]),
    topic("doctor", "看病排队", "Patient queue", "doctor"),
  ] },
  { id: "04", label: { zh: "串", en: "Strings" }, topics: [
    topic("seqstring", "定长顺序串", "Fixed-length string", "seqstring", ["定长串"]),
    topic("heapstr", "堆串", "Heap string", "heapstr", ["堆分配串"]),
    topic("lstr", "链式串", "Linked string", "lstr", ["块链串"]),
  ] },
  { id: "05", label: { zh: "数组与广义表", en: "Arrays and generalized lists" }, topics: [
    topic("transpose", "二维数组与矩阵转置", "Arrays and matrix transpose", "5.1", ["二维数组"]),
    topic("triples", "稀疏矩阵三元组表", "Sparse matrix triples", "array array1 array2 5.2 5.3", ["三元组", "快速转置", "列序递增"]),
    topic("crosslist", "稀疏矩阵十字链表", "Sparse matrix cross list", "crosslistarray crosslistarray1 5.4", ["十字链表"]),
    topic("glist", "广义表", "Generalized list", "glist 5.5 5.6 5.7 5.8 5.9-1 5.9-2 5.10", ["原子个数"]),
    topic("saddle", "马鞍点", "Saddle point", "ma"),
  ] },
  { id: "06", label: { zh: "树与二叉树", en: "Trees and binary trees" }, topics: [
    topic("tree", "树与森林", "Trees and forests", "tree", ["孩子兄弟"]),
    topic("bitree", "二叉树", "Binary tree", "bitree", ["遍历序列", "先序"]),
    topic("threadtree", "线索二叉树", "Threaded binary tree", "threadtree"),
    topic("huffman", "哈夫曼树", "Huffman tree", "huffman", ["哈夫曼编码"]),
  ] },
  { id: "07", label: { zh: "图", en: "Graphs" }, topics: [
    topic("matrix", "邻接矩阵", "Adjacency matrix", "adjmatrix adjmatrix1 adjmatrix2 7.1", ["建网"]),
    topic("adjlist", "邻接表", "Adjacency list", "adjlist"),
    topic("orthlist", "有向图十字链表", "Orthogonal list", "orthlist orthlist1 7.2", ["十字链表"]),
    topic("multilist", "邻接多重表", "Adjacency multilist", "adjmultilist"),
    topic("dfs", "深度优先搜索", "Depth-first search", "7.3 7.4 7.5 7.6 7.7", ["四种遍历"]),
    topic("bfs", "广度优先搜索", "Breadth-first search", "7.8"),
    topic("path", "简单路径与连通性", "Paths and connectivity", "7.9", ["连通性", "遍历复习"]),
    topic("mst", "最小生成树", "Minimum spanning tree", "7.10", ["普里姆"]),
    topic("topology", "拓扑排序", "Topological sorting", "7.11 7.12", ["入度"]),
    topic("critical", "关键路径", "Critical path", "7.13 7.14", ["AOE"]),
    topic("shortest", "最短路径", "Shortest path", "7.15 7.16"),
  ] },
  { id: "08", label: { zh: "查找", en: "Searching" }, topics: [
    topic("seq", "顺序查找", "Sequential search", "seq"),
    topic("bst", "二叉排序树", "Binary search tree", "bst"),
    topic("avl", "平衡二叉排序树", "AVL tree", "avltree", ["平衡因子"]),
    topic("btree", "B 树", "B-tree", "mbtree"),
    topic("hash", "哈希表", "Hash table", "hash", ["哈希查找"]),
  ] },
  { id: "09", label: { zh: "内部排序和外部排序", en: "Internal and external sorting" }, topics: [
    topic("insertion", "插入类排序", "Insertion sorting", "insort", ["一趟插入"]),
    topic("radix", "分配类排序", "Distribution sorting", "radixsort", ["基数排序"]),
  ] },
];

const otherLabel: Label = { zh: "其他代码", en: "Other code" };
const unknownChapter: Chapter = { id: "other", label: { zh: "其他章节", en: "Other chapters" }, topics: [] };

export function classifyCode(chapter: string, sourceFile: string, title: string, id: string) {
  const file = sourceFile.replace(/\\/g, "/");
  const number = /^(?:ch)?(\d{1,2})(?:\D|$)/i.exec(chapter)?.[1]
    ?? /(?:^|\/)ch(\d{1,2})(?:\/|$)/i.exec(file)?.[1]
    ?? /^(?:ch)?(\d{1,2})(?:\D|$)/i.exec(id)?.[1];
  const chapterId = number ? (Number(number) === 10 ? "09" : number.padStart(2, "0")) : "other";
  const definition = compilerChapters.find((item) => item.id === chapterId) ?? unknownChapter;
  const stem = (file.split("/").pop() ?? "").replace(/\.[^.]+$/, "").toLowerCase();
  const byFile = definition.topics.find((item) => item.files.includes(stem));
  const byTitle = byFile ?? [...definition.topics]
    .sort((a, b) => b.label.zh.length - a.label.zh.length)
    .find((item) => item.aliases.some((alias) => title.toLowerCase().includes(alias.toLowerCase())));
  return { chapterId: definition.id, topicId: byTitle?.id ?? "other" };
}

function entryItem(entry: LibraryEntry, locale: Locale): LibraryMenuItem {
  const detail = entry.group === "samples" ? /^[^：:]+[：:]\s*(.+)$/.exec(entry.title)?.[1] : undefined;
  const template = entry.group === "templates" ? compilerTemplates.find((item) => `template-${item.id}` === entry.id) : undefined;
  return {
    id: entry.id,
    label: template ? translate(template.labelKey, locale) : detail ?? entry.title,
    origin: entry.origin,
    keywords: `${entry.title} ${entry.origin} ${entry.id} ${entry.sourceFile}`,
    kind: entry.kind,
  };
}

export function buildLibraryGroups(entries: LibraryEntry[], search: string, locale: Locale) {
  const label = (value: Label) => locale === "en-US" ? value.en : value.zh;
  const query = search.trim().toLowerCase();
  const matches = (entry: LibraryEntry) => {
    const chapter = compilerChapters.find((item) => item.id === entry.chapterId);
    const structure = chapter?.topics.find((item) => item.id === entry.topicId);
    const template = entry.group === "templates" ? compilerTemplates.find((item) => `template-${item.id}` === entry.id) : undefined;
    return !query || [entry.title, entry.origin, entry.id, entry.sourceFile,
      chapter?.label.zh, chapter?.label.en, structure?.label.zh, structure?.label.en,
      template ? translate(template.labelKey, "zh-CN") : "", template ? translate(template.labelKey, "en-US") : "",
    ].join(" ").toLowerCase().includes(query);
  };
  return (["samples", "examples", "templates"] as const).map((key) => {
    const members = entries.filter((entry) => entry.group === key && matches(entry));
    let menus: LibraryMenu[];
    if (key === "templates") {
      menus = [{ id: "starters", label: translate("compiler.chooseTemplate", locale), items: members.map((entry) => entryItem(entry, locale)), count: members.length }];
    } else {
      const chapters = members.some((entry) => entry.chapterId === "other")
        ? [...compilerChapters, unknownChapter] : compilerChapters;
      menus = chapters.map((chapter) => {
        const chapterEntries = members.filter((entry) => entry.chapterId === chapter.id);
        const topics = chapterEntries.some((entry) => entry.topicId === "other")
          ? [...chapter.topics, { id: "other", label: otherLabel, files: [], aliases: [] }] : chapter.topics;
        const items = topics.flatMap((structure) => {
          const children = chapterEntries.filter((entry) => entry.topicId === structure.id).map((entry) => entryItem(entry, locale));
          return children.length ? [{ id: structure.id, label: label(structure.label), children }] : [];
        });
        return { id: chapter.id, label: label(chapter.label), items, count: chapterEntries.length };
      });
    }
    return {
      key,
      label: translate(`compiler.group.${key}`, locale),
      count: members.length,
      menus: query ? menus.filter((menu) => menu.count) : menus,
    };
  }).filter((group) => !query || group.count);
}
