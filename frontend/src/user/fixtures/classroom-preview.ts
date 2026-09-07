import type { ClassroomAction, ClassroomScript, ClassroomSession } from "../../shared/types/contracts";
import {
  courseItemNavigationContext,
  flattenCourseGroups,
  learningCourseGroups,
  type WorkbenchCourseItem,
} from "./learning-workbench";

/**
 * Classroom fixtures are content previews only. They are deliberately kept
 * outside the API adapter so the live service can replace them without
 * changing the classroom view or pretending that a local session is saved.
 */
export type ClassroomPreviewContent = {
  script: ClassroomScript;
  titleEn: string;
  topic: string;
  topicEn: string;
  prompt: string;
  promptEn: string;
  hint: string;
  hintEn: string;
  result: string;
  resultEn: string;
};

export const classroomPreviewSessionId = "preview-classroom-session";

/** The original scene remains the first, stable preview entry for old links. */
export const classroomPreviewScript: ClassroomScript = {
  id: "preview-classroom-sequential-list",
  chapterId: "sequential-list",
  title: "顺序表插入 · 本地课堂",
  versionLabel: "LOCAL PREVIEW",
};

const sequentialListContent: ClassroomPreviewContent = {
  script: classroomPreviewScript,
  titleEn: "Sequential-list insertion · local classroom",
  topic: "顺序表的插入",
  topicEn: "Insertion into a sequential list",
  prompt: "把 23 插入 12, 18, 27, 31, 44 时，为什么从尾部开始移动？",
  promptEn: "When inserting 23 into 12, 18, 27, 31, 44, why do we shift from the end?",
  hint: "先观察空位，再解释覆盖风险。",
  hintEn: "Locate the empty slot, then explain the overwrite risk.",
  result: "先从尾部右移，再把新值写入空位；这样不会覆盖尚未读取的元素。",
  resultEn: "Shift right from the end, then write the new value into the empty slot. This avoids overwriting values that have not yet been read.",
};

type PreviewContext = {
  item: WorkbenchCourseItem;
  content: ClassroomPreviewContent;
  contextIds: string[];
};

function contextIdsForItem(item: WorkbenchCourseItem): string[] {
  return [item.id, item.chapterId, item.parentId, item.routeId]
    .map((value) => value?.trim() ?? "")
    .filter((value, index, values) => Boolean(value) && values.indexOf(value) === index);
}

/**
 * Short instructor prompts for every course-map entry. They are intentionally
 * content rather than progress data: a future published-script API can replace
 * this record without changing the route or session contract.
 */
const lessonPromptCopy: Record<string, Pick<ClassroomPreviewContent, "prompt" | "hint" | "result">> = {
  "01-introduction": {
    prompt: "数据、逻辑结构和存储结构分别回答什么问题？",
    hint: "先区分数据对象之间的关系，再讨论它在计算机中的存放方式。",
    result: "数据结构把数据对象、逻辑关系和存储表示联系起来；算法则给出处理这些对象的步骤。",
  },
  "linear-list": {
    prompt: "线性表中的“线性”描述的是数据值，还是元素之间的前驱后继关系？",
    hint: "从任一非首元素只有一个直接前驱、非尾元素只有一个直接后继开始判断。",
    result: "线性表强调元素的一对一前驱后继关系；顺序表和链表只是两种不同的存储实现。",
  },
  "sequential-list-delete": {
    prompt: "顺序表删除下标 i 的元素后，为什么后面的元素要从前向左移动？",
    hint: "删除后会留下空位，观察谁需要补上这个空位。",
    result: "删除后从 i+1 开始依次左移，可以填补空位并保持顺序表连续存储，移动代价最坏为 O(n)。",
  },
  "linked-list": {
    prompt: "在结点 p 后插入新结点 s 时，为什么要先让 s 指向 p 的后继？",
    hint: "先想一想如果先改写 p->next，原来的后继还能否找到。",
    result: "先保存原有后继，再连接 p 和 s，才能避免断开后半段链表。",
  },
  "doubly-linked-list": {
    prompt: "双向链表插入或删除时，为什么前驱和后继的两个方向都要同步更新？",
    hint: "从正向遍历和反向遍历是否都能经过该结点来检查。",
    result: "双向链表的 prev 与 next 必须保持互相一致；任何一侧遗漏都会造成反向或正向链断裂。",
  },
  "04-string": {
    prompt: "串的模式匹配中，比较失败后为什么不能总是把模式串只右移一位？",
    hint: "观察已经匹配的前缀是否包含可复用的相同前后缀。",
    result: "利用已匹配字符的信息可以跳过不可能成功的位置，这是高效字符串匹配的核心。",
  },
  "05-array-generalized-list": {
    prompt: "对称矩阵为什么可以只存储一半元素？",
    hint: "比较 a[i][j] 与 a[j][i] 的关系。",
    result: "对称矩阵的另一半可由已存元素推得，压缩存储能减少空间但需要明确下标映射。",
  },
  stack: {
    prompt: "括号匹配扫描到右括号时，为什么必须检查栈顶而不是栈底？",
    hint: "回忆后遇到的左括号应当先被匹配。",
    result: "栈按后进先出处理未匹配左括号，栈顶正是当前右括号唯一可能匹配的对象。",
  },
  queue: {
    prompt: "队列为什么从队尾入队、从队头出队？",
    hint: "用排队买票的顺序检验谁应该最先离开结构。",
    result: "队列保持先进先出，尾部追加新元素、头部移除最早进入的元素。",
  },
  "circular-queue": {
    prompt: "循环队列中 rear 到达数组末端后，为什么还能继续入队？",
    hint: "把数组下标看成一个环，并观察取模运算。",
    result: "通过模数组长度回绕下标，循环队列能复用队头释放的位置而不移动全部元素。",
  },
  "expression-evaluation": {
    prompt: "处理中缀表达式时，什么时候应该把运算符从栈顶弹出？",
    hint: "比较当前运算符和栈顶运算符的优先级与结合性。",
    result: "当栈顶运算符应先计算时先弹出它，栈把尚未确定执行时机的运算符有序保存。",
  },
  "binary-tree": {
    prompt: "二叉树的一个结点为什么最多只有两个孩子，而不是两个兄弟？",
    hint: "区分从父结点向下的孩子关系和同一层的兄弟关系。",
    result: "二叉树约束的是每个结点的孩子数至多为二，左右孩子的位置关系构成其递归定义。",
  },
  "tree-traversal": {
    prompt: "前序、中序、后序遍历的差别到底发生在什么时候访问根结点？",
    hint: "把“访问根”放在遍历左、右子树的三个间隙中比较。",
    result: "三种深度优先遍历共享同一递归骨架，只是根结点访问时机不同。",
  },
  "binary-search-tree": {
    prompt: "二叉搜索树查找目标值时，为什么一次比较就能排除一整棵子树？",
    hint: "使用左子树都小于根、右子树都大于根的不变式。",
    result: "搜索树的有序不变式让比较结果决定下一步只进入左子树或右子树。",
  },
  heap: {
    prompt: "堆为什么能快速取出优先级最高的元素，却不能保证整个数组有序？",
    hint: "比较父结点与孩子的局部关系，以及不同分支之间的关系。",
    result: "堆只维护父子间的局部优先关系，根结点因此最优，但同层和跨分支元素不必全局有序。",
  },
  "union-find": {
    prompt: "并查集的路径压缩为什么能让后续查找更快？",
    hint: "观察一次 find 之后路径上的结点会指向哪里。",
    result: "路径压缩把访问过的结点直接连向代表元，后续查询不再重复经过长链。",
  },
  "graph-storage": {
    prompt: "邻接矩阵和邻接表分别在哪类图上更节省空间？",
    hint: "比较顶点数平方的矩阵空间和实际边数相关的链式空间。",
    result: "邻接矩阵适合稠密图且查边直接；邻接表适合稀疏图并按实际边数存储。",
  },
  bfs: {
    prompt: "广度优先搜索为什么要用队列，而不是栈？",
    hint: "观察同一层已发现顶点应以什么顺序扩展。",
    result: "队列保证先发现的顶点先扩展，使搜索按距离起点由近到远逐层推进。",
  },
  dfs: {
    prompt: "深度优先搜索遇到未访问邻接点时，为什么会先沿一条路径走到底？",
    hint: "把递归调用栈看成暂存的回溯路径。",
    result: "递归或显式栈会优先保存回溯点，因此搜索沿当前分支深入，走不通再返回。",
  },
  "shortest-path": {
    prompt: "最短路径算法中的“松弛”操作在比较什么？",
    hint: "比较经过当前边的新路径长度与已知最短距离。",
    result: "松弛尝试用一条新边改进到达目标顶点的当前最短估计，并在改进时更新前驱。",
  },
  "minimum-spanning-tree": {
    prompt: "构造最小生成树时，为什么选择边后要避免形成环？",
    hint: "生成树连接所有顶点所需边数与环的关系是什么？",
    result: "生成树必须连通且无环；一旦成环，总能去掉环中一条边而不破坏连通性。",
  },
  "topological-sort": {
    prompt: "拓扑排序为什么每次只能选择入度为 0 的顶点？",
    hint: "入度大于 0 代表还有哪个前置关系没有被满足。",
    result: "入度为 0 的顶点没有未完成前驱，移除它并更新邻接点入度才能逐步得到合法依赖顺序。",
  },
  "sequential-search": {
    prompt: "顺序查找为什么不要求表中元素有序？",
    hint: "它每一步依赖的是当前位置元素，还是前面元素的大小关系？",
    result: "顺序查找逐项比较即可用于无序表，但最坏情况需检查全部 n 个元素。",
  },
  "binary-search": {
    prompt: "二分查找为什么必须建立在有序表上？",
    hint: "一次比较之后，哪一半元素能够被安全排除？",
    result: "有序性让中点比较决定目标只能在左半区或右半区，从而每次把候选范围减半。",
  },
  "hash-table": {
    prompt: "两个关键字落在同一个散列地址时，为什么不能直接覆盖？",
    hint: "思考不同关键字是否仍需被单独查回。",
    result: "冲突并不表示两个关键字相同，需要用开放定址或链地址等策略同时保存并区分它们。",
  },
  "balanced-search": {
    prompt: "平衡查找树为什么要在插入后关注树高，而不只关注插入位置？",
    hint: "比较退化为链表时和保持近似平衡时的查找路径长度。",
    result: "控制树高能避免查找树退化，保持接近对数级的查找、插入和删除代价。",
  },
  "insertion-sort": {
    prompt: "插入排序为什么把左侧看作已排序区间？",
    hint: "每轮插入完成后，观察当前位置左边是否保持有序。",
    result: "插入排序逐步扩展已排序前缀，把当前元素插入合适位置；近乎有序数据上移动较少。",
  },
  "selection-sort": {
    prompt: "选择排序每一轮确认的是哪个位置的元素？",
    hint: "在未排序区间中找到最小值后，它应被放到哪里。",
    result: "每轮从未排序区间选出最小元素并放到前端，因此已排序区间稳定扩展但比较次数仍较多。",
  },
  "merge-sort": {
    prompt: "归并两个有序子序列时，为什么只需要比较各自当前最前面的元素？",
    hint: "两个子序列内部已经有序，谁会是全局剩余元素中的最小值？",
    result: "两个指针始终指向各子序列未合并部分的最小元素，比较它们即可按序输出。",
  },
  "quick-sort": {
    prompt: "快速排序分区后，枢轴为什么不必再参与同一层递归？",
    hint: "检查枢轴左、右两侧元素与它的大小关系。",
    result: "分区完成后枢轴已处于最终位置，只需递归处理左右两个仍未排序的区间。",
  },
  "complexity-review": {
    prompt: "比较排序算法时，为什么不能只看最坏时间复杂度？",
    hint: "还要考虑稳定性、额外空间、数据规模和输入分布。",
    result: "算法选择要综合时间、空间、稳定性和输入特征；同为 O(n log n) 也可能有不同适用场景。",
  },
  "10-external-sort": {
    prompt: "外部排序为什么先生成初始归并段，再进行多路归并？",
    hint: "主存一次只能容纳有限记录，磁盘访问代价又很高。",
    result: "外部排序用内存可容纳的块建立有序段，再以多路归并减少磁盘读写轮数。",
  },
};

function makeGenericContent(item: WorkbenchCourseItem): ClassroomPreviewContent {
  const topic = item.label;
  const topicEn = item.labelEn ?? item.label;
  const lessonCopy = lessonPromptCopy[item.id];
  const script: ClassroomScript = {
    id: `preview-classroom-${item.id}`,
    chapterId: item.id,
    title: `${item.label} · 本地课堂`,
    versionLabel: "LOCAL PREVIEW",
  };
  return {
    script,
    titleEn: `${topicEn} · local classroom`,
    topic,
    topicEn,
    prompt: lessonCopy?.prompt ?? `围绕“${topic}”，先说出本节操作中最关键的一步。`,
    promptEn: `For “${topicEn}”, name the most important step in this operation.`,
    hint: lessonCopy?.hint ?? "先确认结构状态，再说明操作顺序。",
    hintEn: "Confirm the structure state first, then explain the operation order.",
    result: lessonCopy?.result ?? `在示例中完成“${topic}”的关键步骤，并回顾每一步为什么这样做。`,
    resultEn: `Complete the key steps of “${topicEn}” in this example and review why each step is ordered this way.`,
  };
}

/** A chapter without child lessons still receives one chapter-level script. */
function isClassroomEntry(item: WorkbenchCourseItem): boolean {
  return item.kind === "lesson" || (item.kind === "chapter" && !item.children?.length);
}

const generatedContexts: PreviewContext[] = flattenCourseGroups(learningCourseGroups)
  .filter(isClassroomEntry)
  .filter((item) => item.id !== "sequential-list")
  .map((item) => ({ item, content: makeGenericContent(item), contextIds: contextIdsForItem(item) }));

const previewContexts: PreviewContext[] = [
  {
    item: {
      id: "sequential-list",
      label: "顺序表的插入",
      labelEn: "Insertion in a sequential list",
      meta: "当前学习",
      metaEn: "Current lesson",
      summary: "",
      summaryEn: "",
      capability: "local-interactive-preview",
      kind: "lesson",
      chapterId: "sequential-list",
      parentId: "02-linear-list",
      routeId: "sequential-list",
      lessonNumber: 2,
    },
    content: sequentialListContent,
    contextIds: ["sequential-list", "02-linear-list"],
  },
  ...generatedContexts,
];

const previewContentByScriptId = new Map(previewContexts.map((entry) => [entry.content.script.id, entry.content]));

/**
 * Resolve a local classroom script to the same parent-chapter/lesson pair
 * used by the course map. `ClassroomScript` intentionally stays compatible
 * with the server contract and therefore carries only its chapter field; the
 * richer navigation context lives in this fixture adaptor.
 */
export function classroomPreviewNavigationContext(scriptId: string): { chapterId: string; lessonId?: string } | null {
  const entry = previewContexts.find((candidate) => candidate.content.script.id === scriptId);
  return entry ? courseItemNavigationContext(entry.item) : null;
}

/** All local classroom entries, ordered with the original sequential-list scene first. */
export const classroomPreviewScripts: ClassroomScript[] = previewContexts.map((entry) => entry.content.script);

/** Return a script's local teaching copy for view-level localization. */
export function classroomPreviewContent(scriptId: string): ClassroomPreviewContent | null {
  return previewContentByScriptId.get(scriptId) ?? null;
}

export function classroomPreviewTitleEn(scriptId: string): string | null {
  return classroomPreviewContent(scriptId)?.titleEn ?? null;
}

/**
 * Translate fixture stage strings without putting English text into the
 * content fixture itself. User-entered answers intentionally pass through.
 */
export function classroomPreviewEnglishText(scriptId: string, text: string): string | null {
  const content = classroomPreviewContent(scriptId);
  if (!content) return null;
  const topicSteps: Record<string, string> = {
    [`观察${content.topic}`]: `Observe ${content.topicEn}`,
    [`回答${content.topic}的关键问题`]: `Answer the key question for ${content.topicEn}`,
    [`复盘${content.topic}`]: `Review ${content.topicEn}`,
    "回答抓住了移动边界。": "Your answer identifies the shifting boundary.",
    "再想想覆盖风险和移动方向。": "Consider the overwrite risk and the direction of shifting.",
    "需要从尾部开始，避免覆盖尚未读取的值。": "Start from the tail to avoid overwriting values that have not yet been read.",
    "示例回答已记录，可以继续观察关键步骤。": "Your example answer is recorded. Continue to observe the key steps.",
    "请先写下你观察到的关键步骤。": "Write down the key step you observed first.",
  };
  const map: Record<string, string> = {
    [content.topic]: content.topicEn,
    [content.prompt]: content.promptEn,
    [content.hint]: content.hintEn,
    [content.result]: content.resultEn,
    ...topicSteps,
    "（空回答）": "(No answer)",
  };
  return map[text] ?? null;
}

/**
 * Resolve a route chapter/lesson id. Exact lesson matches win; a canonical
 * chapter id then expands to all lesson scripts under that chapter.
 */
export function classroomPreviewScriptsForChapter(chapterId = ""): ClassroomScript[] {
  const normalized = chapterId.trim();
  if (!normalized) return classroomPreviewScripts.slice();
  const exact = previewContexts
    .filter((entry) => entry.item.id === normalized || entry.content.script.chapterId === normalized)
    .map((entry) => entry.content.script);
  if (exact.length) return exact;
  return previewContexts
    .filter((entry) => entry.contextIds.includes(normalized))
    .map((entry) => entry.content.script);
}

export function isClassroomPreviewScript(scriptId: string): boolean {
  return previewContentByScriptId.has(scriptId);
}

function previewSessionIdForScript(scriptId: string): string {
  if (scriptId === classroomPreviewScript.id) return classroomPreviewSessionId;
  return `preview-classroom-session-${scriptId.replace(/^preview-classroom-/, "")}`;
}

export function classroomPreviewScriptForSessionId(sessionId: string): ClassroomScript | null {
  if (sessionId === classroomPreviewSessionId) return classroomPreviewScript;
  return classroomPreviewScripts.find((script) => previewSessionIdForScript(script.id) === sessionId) ?? null;
}

export function isClassroomPreviewSession(sessionId: string): boolean {
  return classroomPreviewScriptForSessionId(sessionId) !== null;
}

export function createClassroomPreviewSession(scriptId = classroomPreviewScript.id): ClassroomSession {
  const content = classroomPreviewContent(scriptId) ?? sequentialListContent;
  return {
    id: previewSessionIdForScript(content.script.id),
    userId: 0,
    scriptId: content.script.id,
    state: "OPENING",
    paused: false,
    summary: null,
    stage: {
      topic: content.topic,
      prompt: content.prompt,
      hint: content.hint,
    },
  };
}

function genericPreviewAction(session: ClassroomSession, action: ClassroomAction, content: ClassroomPreviewContent, answer = ""): ClassroomSession {
  if (action === "PAUSE") return { ...session, paused: true };
  if (action === "RESUME") return { ...session, paused: false };
  if (action === "FINISH") return {
    ...session,
    state: "SUMMARY",
    summary: content.result,
    stage: { ...session.stage, result: content.result },
  };
  if (action === "ANSWER") {
    const feedback = "示例回答已记录，可以继续观察关键步骤。";
    return {
      ...session,
      state: "DISCUSS",
      answerEvaluation: {
        status: answer.trim() ? "CORRECT" : "MISCONCEPTION",
        misconception: answer.trim() ? null : "请先写下你观察到的关键步骤。",
        feedback,
      },
      stage: { ...session.stage, response: answer.trim() || "（空回答）" },
    };
  }
  if (action === "CONTINUE") {
    const nextState = session.state === "OPENING" ? "EXPLAIN" : session.state === "EXPLAIN" ? "WAITING" : "DISCUSS";
    return {
      ...session,
      state: nextState,
      stage: {
        ...session.stage,
        step: nextState === "EXPLAIN"
          ? `观察${content.topic}`
          : nextState === "WAITING"
            ? `回答${content.topic}的关键问题`
            : `复盘${content.topic}`,
      },
    };
  }
  return session;
}

export function actInClassroomPreview(session: ClassroomSession, action: ClassroomAction, answer = ""): ClassroomSession {
  const content = classroomPreviewContent(session.scriptId) ?? sequentialListContent;
  if (session.scriptId !== classroomPreviewScript.id) return genericPreviewAction(session, action, content, answer);
  if (action === "PAUSE") return { ...session, paused: true };
  if (action === "RESUME") return { ...session, paused: false };
  if (action === "FINISH") return {
    ...session,
    state: "SUMMARY",
    summary: content.result,
    stage: { ...session.stage, result: "O(n) 移动边界" },
  };
  if (action === "ANSWER") {
    const correct = /尾|后|覆盖|右移/.test(answer);
    return {
      ...session,
      state: "DISCUSS",
      answerEvaluation: {
        status: correct ? "CORRECT" : "MISCONCEPTION",
        misconception: correct ? null : "需要从尾部开始，避免覆盖尚未读取的值。",
        feedback: correct ? "回答抓住了移动边界。" : "再想想覆盖风险和移动方向。",
      },
      stage: { ...session.stage, response: answer || "（空回答）" },
    };
  }
  if (action === "CONTINUE") {
    const nextState = session.state === "OPENING" ? "EXPLAIN" : session.state === "EXPLAIN" ? "WAITING" : "DISCUSS";
    return {
      ...session,
      state: nextState,
      stage: {
        ...session.stage,
        step: nextState === "EXPLAIN" ? "比较 27" : nextState === "WAITING" ? "回答移动方向" : "复盘插入结果",
      },
    };
  }
  return session;
}

/** Kept for tests and callers that need to inspect the generated mapping. */
export function classroomPreviewContexts(): ReadonlyArray<PreviewContext> {
  return previewContexts;
}
