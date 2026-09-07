import type { AiReadiness, Chapter, LearningProgress } from "../../shared/types/contracts";
import type { UserApi } from "../api";
import { learningWorkbenchFixture, type WorkbenchFixture } from "../fixtures/learning-workbench";
import { projectPersistedWorkbenchStage, type WorkbenchLiveStage } from "./workbench-stage-launch";

export interface LearningWorkbenchSnapshot {
  mode: "live" | "fixture";
  /** The server owns the live context. A live snapshot does not contain a local scene. */
  scene: WorkbenchFixture | null;
  /** A server-owned saved scene, projected without triggering a new simulation. */
  persistedStage: WorkbenchLiveStage | null;
  contextMode: "live" | "fixture";
  fixtureReason: "guest-preview" | "api-empty" | "api-unavailable" | null;
  chapters: Chapter[];
  currentChapter: Chapter | null;
  currentProgress: LearningProgress["chapters"][number] | null;
  readiness: AiReadiness | null;
  contextLabel: string;
  contextDetail: string;
}

function selectCurrentProgress(progress: LearningProgress): LearningProgress["chapters"][number] | null {
  return progress.chapters
    .filter((item) => Boolean(item.lastActivityAt))
    .sort((left, right) => String(right.lastActivityAt).localeCompare(String(left.lastActivityAt)))[0] ?? null;
}

export function createLearningWorkbenchPreview(options: {
  reason?: "guest-preview" | "api-empty" | "api-unavailable";
} = {}): LearningWorkbenchSnapshot {
  const reason = options.reason ?? "guest-preview";
  return {
    mode: "fixture",
    contextMode: "fixture",
    scene: learningWorkbenchFixture,
    persistedStage: null,
    fixtureReason: reason,
    chapters: [],
    currentChapter: null,
    currentProgress: null,
    readiness: null,
    contextLabel: reason === "api-empty" ? "暂无真实学习任务" : "本地教学预览",
    contextDetail: reason === "api-empty"
      ? "服务端没有返回当前学习章节或任务，当前显示本地教学预览。"
      : reason === "api-unavailable"
        ? "真实学习上下文暂不可用，当前显示本地教学预览，不保存学习记录。"
        : learningWorkbenchFixture.sourceDetail,
  };
}

export async function loadLearningWorkbench(api: Pick<UserApi, "listChapters" | "getLearningWorkbench" | "getReadiness">): Promise<LearningWorkbenchSnapshot> {
  const [chapters, workbench, readiness] = await Promise.all([
    api.listChapters(),
    api.getLearningWorkbench(),
    // Readiness only controls optional AI actions. A transient readiness
    // failure must not hide a persisted stage that the learner can resume.
    api.getReadiness({ operation: "CHAT" }).catch(() => null),
  ]);
  const latestProgress = selectCurrentProgress(workbench.progress);
  const progressChapter = latestProgress
    ? chapters.find((chapter) => chapter.id === latestProgress.chapterId) ?? null
    : null;
  const selectedChapter = workbench.currentChapterId
    ? chapters.find((chapter) => chapter.id === workbench.currentChapterId) ?? null
    : null;
  const savedSceneChapter = workbench.scene
    ? chapters.find((chapter) => chapter.id === workbench.scene?.chapterId) ?? null
    : null;
  const persistedStage = savedSceneChapter && workbench.scene
    ? projectPersistedWorkbenchStage(savedSceneChapter, workbench.scene)
    : null;
  const currentChapter = savedSceneChapter ?? selectedChapter ?? progressChapter;
  const currentProgress = currentChapter
    ? workbench.progress.chapters.find((item) => item.chapterId === currentChapter.id) ?? null
    : latestProgress;

  // Keep the authenticated context separate from the guest teaching fixture.
  // A saved scene only becomes renderable after its chapter is found in the
  // published chapter catalog above.
  if (!currentChapter) {
    const preview = createLearningWorkbenchPreview({ reason: "api-empty" });
    return {
      // The API has answered successfully, but it has not supplied a current
      // learner task. Keep the real catalog and render an honest empty
      // context; the guest-only fixture must never masquerade as a saved goal.
      ...preview,
      mode: "live",
      contextMode: "live",
      scene: null,
      persistedStage: null,
      fixtureReason: null,
      chapters,
      currentProgress,
      readiness,
      contextDetail: chapters.length
        ? "已读取课程目录，但暂未找到当前学习任务；当前目标使用本地教学预览。"
        : preview.contextDetail,
    };
  }

  return {
    mode: "live",
    contextMode: "live",
    scene: null,
    persistedStage,
    fixtureReason: null,
    chapters,
    currentChapter,
    currentProgress,
    readiness,
    contextLabel: `第 ${currentChapter.chapterNumber} 章 ${currentChapter.title}`,
    contextDetail: currentProgress
      ? `最近活动 ${currentProgress.totalActivities} 项，问答 ${currentProgress.chatCount} · 课堂 ${currentProgress.classroomCount} · 动画 ${currentProgress.animationCount} · 代码 ${currentProgress.codeRunCount}`
      : "服务端已返回章节，但未聚合出最近活动。",
  };
}
