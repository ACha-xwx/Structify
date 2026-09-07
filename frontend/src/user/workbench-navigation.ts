export type LearningNavigationId = "path" | "coach" | "classroom" | "practice" | "library";
export type LearningNavigationIcon = "route" | "chat" | "classroom" | "stage" | "library";
export type LearningGlobalNavigationId = "overview" | "course" | "lab" | "library";
export type LearningNavigationQueryValue = string | readonly string[] | null | undefined;

/**
 * Navigation callers can pass the chapter already selected by the learner.
 * Route-query values are supported directly so shared shells do not have to
 * coerce Vue Router's string-array form before building their links.
 */
export interface LearningNavigationContext {
  chapterId?: LearningNavigationQueryValue;
  lessonId?: LearningNavigationQueryValue;
  /** Optional courseware/deck id when a catalog lesson id uses another namespace. */
  coursewareLessonId?: LearningNavigationQueryValue;
  from?: LearningNavigationQueryValue;
}

export interface LearningNavigationItem {
  id: LearningNavigationId;
  to: string;
  icon: LearningNavigationIcon;
  label: { zh: string; en: string };
  caption: { zh: string; en: string };
}

export interface LearningToolItem {
  id: "courseware" | "code";
  to: string;
  icon: "classroom" | "code";
  label: { zh: string; en: string };
  meta: { zh: string; en: string };
}

export interface LearningGlobalNavigationItem {
  id: LearningGlobalNavigationId;
  to: string;
  label: { zh: string; en: string };
}

function readNavigationValue(value: LearningNavigationQueryValue): string {
  const candidates = Array.isArray(value) ? value : [value];
  for (const candidate of candidates) {
    if (typeof candidate !== "string") continue;
    const normalized = candidate.trim();
    if (normalized) return normalized;
  }
  return "";
}

function createTarget(path: string, query: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value) search.set(key, value);
  }
  const serialized = search.toString();
  return serialized ? `${path}?${serialized}` : path;
}

function resolvedContext(context?: LearningNavigationContext) {
  const chapterId = readNavigationValue(context?.chapterId);
  const lessonId = readNavigationValue(context?.lessonId);
  const coursewareLessonId = readNavigationValue(context?.coursewareLessonId);
  const from = readNavigationValue(context?.from);
  return {
    chapterId,
    lessonId,
    coursewareLessonId,
    from: from || "workbench",
    hasProvidedFrom: Boolean(from),
  };
}

/**
 * A chapter-dependent tool must not guess a playable lesson. With no chapter,
 * return the course selector; with a chapter, retain it for the next surface.
 */
export function courseSelectionTarget(context?: LearningNavigationContext): string {
  const current = resolvedContext(context);
  if (!current.chapterId) {
    return createTarget("/user/chapters", { from: current.hasProvidedFrom ? current.from : undefined });
  }
  return createTarget("/user/chapters", {
    chapterId: current.chapterId,
    lessonId: current.lessonId || undefined,
    from: current.from,
  });
}

function chapterTarget(path: string, context?: LearningNavigationContext): string | null {
  const current = resolvedContext(context);
  if (!current.chapterId) return null;
  return createTarget(path, {
    chapterId: current.chapterId,
    lessonId: current.lessonId || undefined,
    from: current.from,
  });
}

function coursewareTarget(context?: LearningNavigationContext): string {
  const current = resolvedContext(context);
  const lessonId = current.coursewareLessonId || current.lessonId;
  if (!current.chapterId || !lessonId) return courseSelectionTarget(context);
  return createTarget("/user/presentation", {
    lessonId,
    chapterId: current.chapterId,
    from: current.from,
  });
}

/** Product-level navigation for the Runtime pill. Local learning modules stay
 * in the Forecast-style sidebar so the two navigation layers do not repeat. */
export function createLearningGlobalNavigation(context?: LearningNavigationContext): readonly LearningGlobalNavigationItem[] {
  const algorithmStageTarget = chapterTarget("/user/animation", context);
  const lab = algorithmStageTarget
    ? {
        id: "lab" as const,
        to: algorithmStageTarget,
        label: { zh: "算法舞台", en: "Algorithm stage" },
      }
    : {
        // A stage needs a concrete chapter. The compiler is a real all-course
        // lab, so this fallback remains useful without pretending a chapter
        // has been selected.
        id: "lab" as const,
        to: "/user/code",
        label: { zh: "代码实验", en: "Code lab" },
      };

  return [
    { id: "overview", to: "/user/home", label: { zh: "学习台", en: "Workbench" } },
    { id: "course", to: courseSelectionTarget(context), label: { zh: "课程", en: "Courses" } },
    lab,
    { id: "library", to: chapterTarget("/user/knowledge", context) ?? "/user/knowledge", label: { zh: "资料库", en: "Library" } },
  ];
}

/**
 * The student IA is intentionally kept outside view templates. Both the
 * canonical workbench and the shared route frame consume this same model.
 */
export function createLearningNavigation(context?: LearningNavigationContext): readonly LearningNavigationItem[] {
  return [
    {
      id: "path",
      to: courseSelectionTarget(context),
      icon: "route",
      label: { zh: "学习路径", en: "Learning path" },
      caption: { zh: "顺序推进", en: "Move forward" },
    },
    {
      id: "coach",
      to: chapterTarget("/user/coach", context) ?? "/user/coach",
      icon: "chat",
      label: { zh: "AI 伴学", en: "AI coach" },
      caption: { zh: "追问概念", en: "Ask concepts" },
    },
    {
      id: "classroom",
      to: chapterTarget("/user/classroom", context) ?? "/user/classroom",
      icon: "classroom",
      label: { zh: "课堂", en: "Classroom" },
      caption: { zh: "脚本学习", en: "Scripted lesson" },
    },
    {
      id: "practice",
      to: chapterTarget("/user/animation", context) ?? courseSelectionTarget(context),
      icon: "stage",
      label: { zh: "实践", en: "Practice" },
      caption: { zh: "算法与代码", en: "Algorithms and code" },
    },
    {
      id: "library",
      to: chapterTarget("/user/knowledge", context) ?? "/user/knowledge",
      icon: "library",
      label: { zh: "资料", en: "Library" },
      caption: { zh: "来源与证据", en: "Sources and evidence" },
    },
  ];
}

export function createLearningTools(context?: LearningNavigationContext): readonly LearningToolItem[] {
  return [
    {
      id: "courseware",
      to: coursewareTarget(context),
      icon: "classroom",
      label: { zh: "课程课件", en: "Courseware" },
      meta: { zh: "公开预览", en: "Public preview" },
    },
    {
      id: "code",
      // The compiler is an all-course tool. It must remain directly reachable
      // even when no chapter has been selected; only chapter-scoped context is
      // appended when it is available.
      to: chapterTarget("/user/code", context) ?? "/user/code",
      icon: "code",
      label: { zh: "C 编译器", en: "C compiler" },
      meta: { zh: "代码练习", en: "Code practice" },
    },
  ];
}

/**
 * Neutral exports remain available to existing consumers. They deliberately
 * do not invent a current chapter; callers with route context should use the
 * localized helpers below (or the create* functions) with that context.
 */
export const learningGlobalNavigation = createLearningGlobalNavigation();
export const learningNavigation = createLearningNavigation();
export const learningTools = createLearningTools();

export const learningUtilities = [
  {
    id: "review",
    to: "/user/progress",
    label: { zh: "学习复盘", en: "Review" },
    meta: { zh: "活动记录", en: "Activity log" },
  },
] as const;

export function localizedLearningNavigation(locale: "zh-CN" | "en-US", context?: LearningNavigationContext) {
  const language = locale === "en-US" ? "en" : "zh";
  return createLearningNavigation(context).map((item) => ({
    ...item,
    label: item.label[language],
    caption: item.caption[language],
  }));
}

export function localizedLearningGlobalNavigation(locale: "zh-CN" | "en-US", context?: LearningNavigationContext) {
  const language = locale === "en-US" ? "en" : "zh";
  return createLearningGlobalNavigation(context).map((item) => ({ ...item, label: item.label[language] }));
}

export function localizedLearningTools(locale: "zh-CN" | "en-US", context?: LearningNavigationContext) {
  const language = locale === "en-US" ? "en" : "zh";
  return createLearningTools(context).map((item) => ({
    ...item,
    label: item.label[language],
    meta: item.meta[language],
  }));
}

export function isLearningNavigationActive(id: LearningNavigationId, path: string): boolean {
  if (id === "path") return path === "/user/home" || path === "/user/chapters" || path.startsWith("/user/chapters/");
  if (id === "coach") return path === "/user/coach";
  if (id === "classroom") return path === "/user/classroom";
  if (id === "practice") return path === "/user/animation" || path === "/user/code";
  if (id === "library") return path === "/user/knowledge" || path.startsWith("/user/resources/") || path === "/user/presentation";
  return false;
}

export function isLearningGlobalNavigationActive(id: LearningGlobalNavigationId, path: string): boolean {
  if (id === "overview") return path === "/user/home";
  if (id === "course") return path === "/user/chapters" || path.startsWith("/user/chapters/");
  if (id === "lab") return path === "/user/animation" || path === "/user/code";
  if (id === "library") return path === "/user/knowledge" || path.startsWith("/user/resources/") || path === "/user/presentation";
  return false;
}
