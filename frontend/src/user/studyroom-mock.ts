import type { AnimationDefinition, AnimationType, DsvpRequest, DsvpSimulationResponse } from "../shared/types/animation";
import type { ClassroomCodeSamplesResponse, PresentationDeck, PresentationSlide } from "../shared/types/contracts";
import type { StudyroomResource, StudyroomView } from "../shared/types/studyroom";
import { normalizeFrame } from "../animation/frame";

type PresentationApi = Pick<{
  listPresentationDecks(): Promise<PresentationDeck[]>;
  listDeckSlides(deckId: string): Promise<PresentationSlide[]>;
  coursewareImageUrl(slideId: string): string;
}, "listPresentationDecks" | "listDeckSlides" | "coursewareImageUrl">;

interface TopicFixture {
  topic: string;
  keywords: string[];
  codeKeywords?: string[];
  animationKeywords?: string[];
  codeTitle: string;
  sourceFile: string;
  summary: string;
  code: string;
  stdin: string;
  expectedStdout: string;
  animation?: { id: string; type: AnimationType; title: string; initial: number[]; operation: string; state: number[]; note: string };
}

const fixtures: Record<string, TopicFixture> = {
  "01-introduction": { topic: "算法与复杂度", keywords: ["复杂度", "算法", "时间", "效率"], codeTitle: "C 语言：数组遍历", sourceFile: "mock/01-array-traversal.c", summary: "遍历数组并统计元素数量。", code: '#include <stdio.h>\nint main(void) { int values[] = {2, 4, 6}; int count = (int)(sizeof(values) / sizeof(values[0])); printf("元素数量：%d\\n", count); return 0; }', stdin: "", expectedStdout: "元素数量：3" },
  "02-linear-list": { topic: "线性表", keywords: ["线性表", "顺序表", "链表", "结点", "插入", "删除"], codeKeywords: ["顺序表插入", "顺序表的插入"], animationKeywords: ["顺序表插入", "顺序表的插入"], codeTitle: "C 语言：顺序表插入", sourceFile: "mock/02-list-insert.c", summary: "在顺序表指定位置插入一个元素。", code: '#include <stdio.h>\nint main(void) { int values[5] = {1, 3, 5}; int count = 3; int position = 1; for (int index = count; index > position; --index) values[index] = values[index - 1]; values[position] = 2; ++count; printf("插入后：%d %d %d %d\\n", values[0], values[1], values[2], values[3]); return 0; }', stdin: "", expectedStdout: "插入后：1 2 3 5", animation: { id: "mock-animation-02-list", type: "list", title: "线性表插入", initial: [1, 3, 5], operation: "insert", state: [1, 2, 3, 5], note: "元素插入后，后续元素依次后移。" } },
  "03-stack-queue": { topic: "栈", keywords: ["栈", "入栈", "出栈", "后进先出"], codeKeywords: ["入栈", "实现栈"], animationKeywords: ["入栈"], codeTitle: "C 语言：顺序栈入栈", sourceFile: "mock/03-stack-push.c", summary: "使用数组和 top 下标完成入栈。", code: '#include <stdio.h>\nint main(void) { int stack[10] = {3, 5}; int top = 1; stack[++top] = 7; printf("栈顶元素：%d\\n", stack[top]); return 0; }', stdin: "", expectedStdout: "栈顶元素：7", animation: { id: "mock-animation-03-stack", type: "stack", title: "栈顶入栈演示", initial: [3, 5], operation: "push", state: [3, 5, 7], note: "7 成为新的栈顶。" } },
  "04-string": { topic: "串与模式匹配", keywords: ["串", "字符串", "模式匹配", "kmp", "子串"], codeTitle: "C 语言：字符串匹配", sourceFile: "mock/04-string-match.c", summary: "在文本中查找模式串首次出现的位置。", code: '#include <stdio.h>\n#include <string.h>\nint main(void) { const char *text = "datastruct"; const char *pattern = "struct"; const char *match = strstr(text, pattern); printf("匹配位置：%d\\n", match ? (int)(match - text) : -1); return 0; }', stdin: "", expectedStdout: "匹配位置：4" },
  "05-array-generalized-list": { topic: "数组与矩阵", keywords: ["数组", "矩阵", "压缩存储", "稀疏"], codeKeywords: ["数组求和"], animationKeywords: ["二维数组遍历", "行优先"], codeTitle: "C 语言：二维数组求和", sourceFile: "mock/05-array-sum.c", summary: "遍历二维数组并累计元素之和。", code: '#include <stdio.h>\nint main(void) { int matrix[2][2] = {{1, 2}, {3, 4}}; int sum = 0; for (int row = 0; row < 2; ++row) for (int column = 0; column < 2; ++column) sum += matrix[row][column]; printf("元素和：%d\\n", sum); return 0; }', stdin: "", expectedStdout: "元素和：10", animation: { id: "mock-animation-05-array", type: "array", title: "二维数组遍历", initial: [1, 2, 3, 4], operation: "visit", state: [1, 2, 3, 4], note: "按行优先次序访问二维数组。" } },
  "06-tree": { topic: "二叉树遍历", keywords: ["二叉树", "遍历", "先序", "中序", "后序"], codeKeywords: ["先序"], animationKeywords: ["先序"], codeTitle: "C 语言：二叉树先序遍历", sourceFile: "mock/06-tree-preorder.c", summary: "递归输出二叉树的先序序列。", code: '#include <stdio.h>\ntypedef struct Node { int value; struct Node *left, *right; } Node;\nvoid preorder(Node *node) { if (!node) return; printf("%d ", node->value); preorder(node->left); preorder(node->right); }\nint main(void) { Node root = {1, 0, 0}, left = {2, 0, 0}, right = {3, 0, 0}; root.left = &left; root.right = &right; preorder(&root); return 0; }', stdin: "", expectedStdout: "1 2 3 ", animation: { id: "mock-animation-06-tree", type: "tree", title: "二叉树先序遍历", initial: [1, 2, 3], operation: "preorder", state: [1, 2, 3], note: "先访问根结点，再遍历左、右子树。" } },
  "07-graph": { topic: "图的遍历", keywords: ["图", "顶点", "边", "深度优先", "广度优先", "最短路径", "连通"], codeKeywords: ["度数", "顶点的度"], codeTitle: "C 语言：邻接矩阵统计度数", sourceFile: "mock/07-graph-degree.c", summary: "用邻接矩阵统计无向图顶点的度。", code: '#include <stdio.h>\nint main(void) { int adjacency[3][3] = {{0,1,1},{1,0,0},{1,0,0}}; int degree = 0; for (int index = 0; index < 3; ++index) degree += adjacency[0][index]; printf("顶点 0 的度：%d\\n", degree); return 0; }', stdin: "", expectedStdout: "顶点 0 的度：2" },
  "08-search": { topic: "二分查找", keywords: ["二分", "二分查找"], animationKeywords: ["哈希", "散列"], codeTitle: "C 语言：二分查找", sourceFile: "mock/08-binary-search.c", summary: "在有序数组中使用二分查找定位关键字。", code: '#include <stdio.h>\nint main(void) { int values[] = {1, 3, 5, 7, 9}; int lower = 0, upper = 4, target = 7, foundIndex = -1; while (lower <= upper) { int middle = lower + (upper - lower) / 2; if (values[middle] == target) { foundIndex = middle; break; } if (values[middle] < target) lower = middle + 1; else upper = middle - 1; } printf("查找位置：%d\\n", foundIndex); return 0; }', stdin: "", expectedStdout: "查找位置：3", animation: { id: "mock-animation-08-hash", type: "hash", title: "哈希表查找槽位", initial: [0, 3, 0, 7], operation: "find", state: [0, 3, 0, 7], note: "根据键值计算并检查对应槽位。" } },
  "09-internal-sort": { topic: "内部排序", keywords: ["排序", "插入排序", "冒泡", "选择排序", "归并", "快速排序", "堆排序"], codeKeywords: ["插入排序"], animationKeywords: ["插入排序"], codeTitle: "C 语言：插入排序", sourceFile: "mock/09-insertion-sort.c", summary: "将当前元素插入已排序前缀。", code: '#include <stdio.h>\nint main(void) { int values[] = {4, 2, 3}; for (int index = 1; index < 3; ++index) { int key = values[index], position = index - 1; while (position >= 0 && values[position] > key) { values[position + 1] = values[position]; --position; } values[position + 1] = key; } printf("排序结果：%d %d %d\\n", values[0], values[1], values[2]); return 0; }', stdin: "", expectedStdout: "排序结果：2 3 4", animation: { id: "mock-animation-09-array", type: "array", title: "插入排序数组演示", initial: [4, 2, 3], operation: "insert", state: [2, 3, 4], note: "将当前元素插入左侧有序区间。" } },
  "10-external-sort": { topic: "外部排序", keywords: ["外部排序", "归并段", "多路归并", "磁盘", "缓冲区"], codeTitle: "C 语言：有序段合并", sourceFile: "mock/10-merge-runs.c", summary: "合并两个已排序的小数组，演示归并段合并核心。", code: '#include <stdio.h>\nint main(void) { int leftValues[] = {1, 4}, rightValues[] = {2, 3}, result[4], leftIndex = 0, rightIndex = 0, outputIndex = 0; while (leftIndex < 2 && rightIndex < 2) result[outputIndex++] = leftValues[leftIndex] < rightValues[rightIndex] ? leftValues[leftIndex++] : rightValues[rightIndex++]; while (leftIndex < 2) result[outputIndex++] = leftValues[leftIndex++]; while (rightIndex < 2) result[outputIndex++] = rightValues[rightIndex++]; printf("合并结果：%d %d %d %d\\n", result[0], result[1], result[2], result[3]); return 0; }', stdin: "", expectedStdout: "合并结果：1 2 3 4" },
};

function chapterKey(chapterId: string | null): string | null {
  if (!chapterId) return null;
  return Object.keys(fixtures).find((key) => chapterId === key || chapterId.startsWith(`${key.slice(0, 2)}-`) || chapterId === key.slice(0, 2)) ?? null;
}

function matchesTopic(fixture: TopicFixture, step: StudyroomView["steps"][number]): boolean {
  const text = `${step.title} ${step.explanation}`.toLocaleLowerCase();
  return (fixture.codeKeywords ?? fixture.keywords).some((keyword) => text.includes(keyword.toLocaleLowerCase()));
}

const presentationTopics: Record<string, string[]> = {
  "01-introduction": ["复杂度", "算法", "数据结构"],
  "02-linear-list": ["线性表", "顺序表", "链表", "单链表", "双链表"],
  "03-stack-queue": ["栈", "队列", "入栈", "出栈", "入队", "出队", "循环队列"],
  "04-string": ["字符串", "模式匹配", "KMP", "子串"],
  "05-array-generalized-list": ["数组", "矩阵", "广义表", "稀疏", "压缩存储"],
  "06-tree": ["二叉树", "先序", "中序", "后序", "层序", "哈夫曼", "遍历"],
  "07-graph": ["图", "顶点", "邻接矩阵", "邻接表", "最短路径", "拓扑", "深度优先", "广度优先"],
  "08-search": ["查找", "二分", "二叉搜索树", "二叉排序树", "哈希", "散列"],
  "09-internal-sort": ["内部排序", "插入排序", "冒泡", "选择排序", "归并", "快速排序", "堆排序"],
  "10-external-sort": ["外部排序", "归并段", "多路归并"],
};

function evidenceChapter(evidenceIds: string[]): string | null {
  const keys = new Set(evidenceIds.filter((id) => id.startsWith("studyroom-dev-mock-"))
    .map((id) => chapterKey(id.slice("studyroom-dev-mock-".length))).filter((key) => key !== null));
  return keys.size === 1 ? [...keys][0] : null;
}

function textChapter(text: string): string | null {
  const normalized = text.toLowerCase();
  const keys = Object.entries(presentationTopics).filter(([key, terms]) => terms.some((term) => {
    if (term.length < 2 || term === "遍历" || term === "归并") return false;
    if (key === "09-internal-sort" && normalized.includes("外部排序")) return false;
    return normalized.includes(term.toLowerCase());
  })).map(([key]) => key);
  if (normalized.includes("栈") || normalized.includes("队列")) keys.push("03-stack-queue");
  const unique = [...new Set(keys)];
  return unique.length === 1 ? unique[0] : null;
}

function matchingSlide(slides: PresentationSlide[], key: string, step: StudyroomView["steps"][number]): PresentationSlide | undefined {
  const normalized = `${step.title} ${step.explanation}`.toLowerCase();
  const vocabulary = [...presentationTopics[key], "定义", "概念", "性质", "存储", "建立", "叶子", "线索"];
  const titleTerms = vocabulary.filter((term) => step.title.toLowerCase().includes(term.toLowerCase()));
  const terms = titleTerms.length ? titleTerms : vocabulary.filter((term) => normalized.includes(term.toLowerCase()));
  let best: PresentationSlide | undefined;
  let bestScore = 0;
  for (const slide of slides) {
    if (!slide.shouldShow || slide.chapter !== key.slice(0, 2)) continue;
    const title = slide.title.toLowerCase();
    const content = `${slide.rawText} ${slide.semanticSummary} ${slide.concepts.join(" ")}`.toLowerCase();
    const score = terms.reduce((total, term) => total + (title.includes(term.toLowerCase()) ? 6 : content.includes(term.toLowerCase()) ? 1 : 0), 0)
      + ([slide.title, ...slide.concepts].some((value) => value && normalized.includes(value.toLowerCase())) ? 3 : 0);
    if (score > bestScore) { best = slide; bestScore = score; }
  }
  return best;
}

function animationResponse(fixture: TopicFixture): DsvpSimulationResponse | null {
  const config = fixture.animation;
  if (!config) return null;
  const structure = config.type === "list" ? "sequential_list" : config.type;
  const request: DsvpRequest = { version: "1.0", structure, operation: config.operation, params: {}, initial_state: { data: config.initial } };
  let dsvpState: DsvpSimulationResponse["animationData"]["steps"][number]["dsvpState"];
  if (config.type === "stack") {
    dsvpState = { kind: "stack", items: config.state, top: config.state.length - 1, metadata: { capacity: 10 } };
  } else if (config.type === "list") {
    dsvpState = { kind: "sequential_list", items: config.state, length: config.state.length, position: 1 };
  } else if (config.type === "tree") {
    dsvpState = { kind: "tree", view: [{ role: "tree", nodes: [{ id: 1, label: "1" }, { id: 2, label: "2" }, { id: 3, label: "3" }], edges: [[1, 2], [1, 3]] }] };
  } else if (config.type === "hash") {
    dsvpState = { kind: "hash", view: [{ role: "buckets", values: [[], [3], [], [7]] }] };
  } else {
    dsvpState = { kind: "array", view: [{ role: "array", values: config.state }] };
  }
  const animation: AnimationDefinition = { animation: true, type: config.type, title: config.title, description: config.note, initial: config.initial, steps: [{ op: config.operation, label: config.title, note: config.note, state: config.state, dsvpState }] };
  if (config.type === "tree") {
    const treePanel = dsvpState!.view![0];
    animation.initial = [treePanel];
    animation.steps = config.state.map((nodeId, index) => ({
      op: "visit", label: `访问结点 ${nodeId}`, note: `先序序列：${config.state.slice(0, index + 1).join(" → ")}`,
      dsvpState: { kind: "tree", view: [treePanel, { role: "meta", current: nodeId, visited: config.state.slice(0, index + 1) }] },
    }));
  }
  const hasDrawableFrames = animation.steps.every((step) => {
    const frame = normalizeFrame(step.dsvpState);
    return frame.panels.some((panel) => panel.values.length > 0 || panel.nodes.length > 0 || panel.edges.length > 0);
  });
  if (!hasDrawableFrames) return null;
  return { protocol: "dsvp/1.0", request, trace: {}, animationData: animation, evidencePersisted: false, matchSource: "NONE" };
}

export async function attachMockResources(room: StudyroomView, api: PresentationApi): Promise<StudyroomView> {
  const explicitKey = chapterKey(room.chapterId);
  const roomKey = explicitKey ?? textChapter(`${room.initialQuestion} ${room.title}`);
  const stepKeys = room.steps.map((step) => explicitKey ?? evidenceChapter(step.evidenceIds) ?? textChapter(`${step.title} ${step.explanation}`) ?? roomKey);
  const neededChapters = new Set(stepKeys.filter((key) => key && !key.startsWith("04") && !key.startsWith("10")).map((key) => key!.slice(0, 2)));
  const decks = neededChapters.size ? await api.listPresentationDecks().catch(() => []) : [];
  const slides = (await Promise.all(decks.filter((deck) => neededChapters.has(deck.chapter))
    .map((deck) => api.listDeckSlides(deck.deckId).catch(() => [])))).flat();
  const resourcesByStep = room.steps.map((step, index) => {
    const resources = structuredClone(step.resources);
    const key = stepKeys[index];
    if (!key) return resources;
    const fixture = fixtures[key];
    const addIfAbsent = (resource: StudyroomResource) => {
      if (!resources.some((item) => item.type === resource.type && item.available)) resources.push(resource);
    };
    if (slides.length && !resources.some((item) => item.type === "PPT" && item.available)) {
      const slide = matchingSlide(slides, key, step);
      if (slide) addIfAbsent({ type: "PPT", id: slide.id, title: slide.title, description: `原版课件 · 第 ${slide.slideNumber} 页`, parameters: { imageUrl: api.coursewareImageUrl(slide.id), slideNumber: slide.slideNumber, originalResource: true }, available: true });
    }
    if (matchesTopic(fixture, step)) {
      const codeId = `mock-code-${key.slice(0, 2)}-${fixture.codeTitle.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`;
      addIfAbsent({ type: "C_CODE", id: codeId, title: fixture.codeTitle, description: fixture.summary, parameters: { mockResource: true }, available: true });
    }
    if (fixture.animation && animationResponse(fixture) && (fixture.animationKeywords ?? fixture.keywords).some((keyword) => `${step.title} ${step.explanation}`.toLocaleLowerCase().includes(keyword.toLocaleLowerCase()))) {
      addIfAbsent({ type: "ANIMATION", id: fixture.animation.id, title: fixture.animation.title, description: fixture.animation.note, parameters: { animationType: fixture.animation.type, mockResource: true }, available: true });
    }
    return resources;
  });
  return { ...structuredClone(room), steps: room.steps.map((step, index) => ({ ...structuredClone(step), resources: resourcesByStep[index] })) };
}

export function mockPptImageUrl(_resourceId: string): string | null { return null; }

export function getMockAnimation(resourceId: string): DsvpSimulationResponse | null {
  const fixture = Object.values(fixtures).find((item) => item.animation?.id === resourceId);
  return fixture ? animationResponse(fixture) : null;
}

export async function getMockCodeSamples(): Promise<ClassroomCodeSamplesResponse> {
  const lessons = Object.entries(fixtures).map(([chapterId, fixture]) => ({
    coursewareKey: `${chapterId.slice(0, 2)}-demo`, lessonTitle: fixture.topic, chapterId,
    samples: [{ id: `mock-code-${chapterId.slice(0, 2)}-${fixture.codeTitle.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`, title: fixture.codeTitle, sourceFile: fixture.sourceFile, sections: [fixture.topic], targets: [fixture.topic], summary: fixture.summary, stdin: fixture.stdin, expectedStdout: fixture.expectedStdout, code: fixture.code }],
  }));
  return { lessons, sampleCount: lessons.length };
}
