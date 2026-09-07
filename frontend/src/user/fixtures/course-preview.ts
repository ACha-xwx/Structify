import type { Chapter, KnowledgeSearchResult, Resource } from "../../shared/types/contracts";
import { flattenCourseGroups, learningCourseGroups } from "./learning-workbench";
import { knowledgePreviewFixture, searchKnowledgePreview } from "./knowledge-preview";
import { previewResourcesByChapter } from "./resource-preview";

export { previewKnowledgeChapterId } from "./knowledge-preview";

export interface CoursePreviewFixture {
  mode: "fixture";
  label: string;
  detail: string;
  chapters: Chapter[];
  resourcesByChapter: Record<string, Resource[]>;
  knowledge: KnowledgeSearchResult[];
}

const canonicalChapterSummaries: Record<string, string> = {
  "01-introduction": "数据结构基本概念、算法描述与复杂度分析",
  "02-linear-list": "顺序表、链表及其基本操作",
  "03-stack-queue": "栈、队列及典型应用",
  "04-string": "串的存储、匹配与应用",
  "05-array-generalized-list": "数组、矩阵压缩与广义表",
  "06-tree": "树、二叉树、遍历、哈夫曼树与并查集",
  "07-graph": "图的存储、遍历、连通性与路径算法",
  "08-search": "线性查找、树表查找与哈希查找",
  "09-internal-sort": "插入、交换、选择、归并与分配排序",
  "10-external-sort": "外部排序基本方法",
};

/**
 * The guest chapter map follows the same ten canonical chapter ids as the
 * server seed. It is a content outline only: it does not imply publication,
 * permissions, progress, or resource availability.
 */
const canonicalChapterPreview: Chapter[] = flattenCourseGroups(learningCourseGroups, { includeLessons: false })
  .filter((item) => item.kind === "chapter" && item.chapterNumber !== undefined)
  .sort((left, right) => (left.chapterNumber ?? 0) - (right.chapterNumber ?? 0))
  .map((item) => ({
    id: item.chapterId ?? item.id,
    chapterNumber: item.chapterNumber!,
    title: item.label,
    summary: canonicalChapterSummaries[item.chapterId ?? item.id] ?? "课程章节内容将在对应学习入口中展开。",
  }));

const previewPresentationLessons: Record<string, string> = {
  // The local scene is the insertion lesson inside chapter 02, not the
  // chapter-01 introduction deck that older links used by default.
  "sequential-list": "02-02B",
  "01-introduction": "01-01A",
  "02-linear-list": "02-01",
  "03-stack-queue": "03-01A",
  "05-array-generalized-list": "05-01",
  "06-tree": "06-01",
  "07-graph": "07-01",
  "08-search": "08-01A",
  "09-internal-sort": "09-01A",
};

/** Resolve a chapter/lesson context to the first verified presentation id. */
export function previewPresentationLessonId(chapter: Pick<Chapter, "id" | "chapterNumber">): string {
  return previewPresentationLessons[chapter.id]
    ?? `${String(chapter.chapterNumber).padStart(2, "0")}-01`;
}

/** Whether the local presentation bundle contains a plan for this context. */
export function hasPreviewPresentation(chapterId: string): boolean {
  return Object.prototype.hasOwnProperty.call(previewPresentationLessons, chapterId);
}

/**
 * The local algorithm scene is a lesson alias, not an additional published
 * chapter. Keep it addressable for existing workbench links while the visible
 * guest chapter map stays canonical.
 */
const sequentialListLessonPreview: Chapter = {
  id: "sequential-list",
  chapterNumber: 2,
  title: "顺序表的插入",
  summary: "从连续存储开始，理解顺序表的访问、插入与移动边界。",
};

/**
 * Guest-safe course material. It is explicitly labeled as a preview so it
 * cannot be mistaken for a user's published course record. Resources remain
 * limited to the verified local lesson, while knowledge cards cover the
 * catalog so guest search can demonstrate the full learning scope.
 */
export const coursePreviewFixture: CoursePreviewFixture = {
  mode: "fixture",
  label: "本地课程预览",
  detail: "仅用于未登录浏览，不代表已发布课程目录、账号权限或学习记录。",
  chapters: canonicalChapterPreview,
  resourcesByChapter: {
    "sequential-list": previewResourcesByChapter("sequential-list"),
  },
  knowledge: knowledgePreviewFixture.results,
};

export function previewChapter(chapterId: string): Chapter | null {
  return coursePreviewFixture.chapters.find((chapter) => chapter.id === chapterId)
    ?? (chapterId === sequentialListLessonPreview.id ? sequentialListLessonPreview : null);
}

export function previewResources(chapterId: string): Resource[] {
  return coursePreviewFixture.resourcesByChapter[chapterId] ?? [];
}

/** Compatibility export for older callers; the adapter itself lives in its own module. */
export function searchPreviewKnowledge(query: string, chapterId = "", limit = 4, lessonId = ""): KnowledgeSearchResult[] {
  return searchKnowledgePreview(query, chapterId, limit, lessonId);
}
