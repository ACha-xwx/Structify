import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouterLinkStub, flushPromises, mount } from "@vue/test-utils";
import ClassroomView from "./ClassroomView.vue";
import { DEFAULT_LOCALE, setLocale } from "../shared/i18n/locale";
import type { ClassroomSession } from "../shared/types/contracts";
import type { ClassroomLesson } from "../user/api";

const api = vi.hoisted(() => ({
  listClassroomLessons: vi.fn(),
  prepareClassroom: vi.fn(),
  getClassroomPreparation: vi.fn(),
  getClassroomSession: vi.fn(),
  actInClassroom: vi.fn(),
  simulateAnimation: vi.fn(),
  getLessonCourseware: vi.fn(),
  pinClassroomSlide: vi.fn(),
}));

vi.mock("../user/runtime", () => ({ userApi: api }));

const navigation = vi.hoisted(() => ({ push: vi.fn(), query: {} as Record<string, unknown> }));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: navigation.push }),
  useRoute: () => ({ query: navigation.query }),
}));

/** Resuming a lesson is explicit now: it arrives as a session id in the url. */
function openSession(id: string) {
  navigation.query = { session: id };
}

/**
 * The page stands on the shared stage, whose brand link is a router link. These tests mount the view
 * alone, so the link is stubbed rather than resolved by a real router.
 */
function mountClassroom() {
  return mount(ClassroomView, { global: { stubs: { RouterLink: RouterLinkStub } } });
}

const noCourseware = {
  lessonId: "lesson-1",
  coursewareKey: "",
  title: "",
  source: "",
  ready: true,
  builtAt: "",
  slides: [],
};

const opening: ClassroomSession = {
  id: "session-1",
  userId: 7,
  scriptId: "script-1",
  state: "OPENING",
  paused: false,
  summary: null,
  stage: { content: "准备开始", stepIndex: -1, stepCount: 3, revision: 0, chapterId: "06-tree" },
  answerEvaluation: null,
};

beforeEach(() => {
  localStorage.clear();
  setLocale(DEFAULT_LOCALE);
  vi.resetAllMocks();
  navigation.query = {};
  api.listClassroomLessons.mockResolvedValue([
    { id: "lesson-1", chapterId: "06-tree", title: "二叉树", source: "textbook", pages: "157-161" },
  ]);
  api.prepareClassroom.mockResolvedValue({ id: "job-1", state: "ready", phase: "ready", session: opening });
  api.getLessonCourseware.mockResolvedValue(noCourseware);
  api.pinClassroomSlide.mockResolvedValue(opening);
});

describe("minimal classroom", () => {
  it("shows loading while fetching lessons and courseware, then shows the resolved empty state", async () => {
    let resolveLessons!: (lessons: ClassroomLesson[]) => void;
    let resolveCourseware!: (courseware: typeof noCourseware) => void;
    api.listClassroomLessons.mockReturnValue(new Promise<ClassroomLesson[]>((resolve) => { resolveLessons = resolve; }));
    api.getLessonCourseware.mockReturnValue(new Promise<typeof noCourseware>((resolve) => { resolveCourseware = resolve; }));

    const wrapper = mountClassroom();
    expect(wrapper.get(".slides__stage--empty").text()).toBe("正在载入课件");

    resolveLessons([{ id: "lesson-1", chapterId: "06-tree", title: "二叉树", source: "textbook", pages: "157-161" }]);
    await flushPromises();
    expect(api.getLessonCourseware).toHaveBeenCalledWith("lesson-1");
    expect(wrapper.get(".slides__stage--empty").text()).toBe("正在载入课件");

    resolveCourseware(noCourseware);
    await flushPromises();
    expect(wrapper.get(".slides__stage--empty").text()).toBe("本课时没有配套课件");
    wrapper.unmount();
  });

  it("stops showing loading if the lesson list cannot be fetched", async () => {
    api.listClassroomLessons.mockRejectedValue(new Error("offline"));

    const wrapper = mountClassroom();
    await flushPromises();

    expect(wrapper.get(".slides__stage--empty").text()).toBe("课件暂时无法访问");
    wrapper.unmount();
  });

  it("reads the selected deck into the picker and loads its mapped lesson without resuming the old session", async () => {
    localStorage.setItem("structify.classroom.last", "session-1");
    localStorage.setItem("structify.courseware.selected", JSON.stringify({
      deckId: "tree-deck",
      title: "树的遍历课件",
      lessonIds: ["lesson-unavailable", "lesson-2"],
    }));
    api.listClassroomLessons.mockResolvedValue([
      { id: "lesson-1", chapterId: "06-tree", title: "二叉树", source: "textbook", pages: "157-161" },
      { id: "lesson-2", chapterId: "06-tree", title: "树的遍历", source: "textbook", pages: "162-168" },
    ]);
    api.getLessonCourseware.mockResolvedValue({
      ...noCourseware,
      lessonId: "lesson-2",
      title: "树的遍历课件",
      slides: [{
        id: "tree-deck-s001",
        deckId: "tree-deck",
        deckTitle: "树的遍历课件",
        slideNumber: 1,
        imageUrl: "/api/v1/presentation/slides/tree-deck-s001/image",
      }],
    });

    const wrapper = mountClassroom();
    await flushPromises();

    expect(wrapper.get(".classroom__lesson-select .runtime-select__trigger").attributes("aria-label")).toContain("树的遍历");
    expect(api.getLessonCourseware).toHaveBeenCalledExactlyOnceWith("lesson-2");
    expect(wrapper.get("img.slides__image").attributes("src")).toBe("/api/v1/presentation/slides/tree-deck-s001/image");
    expect(api.getClassroomSession).not.toHaveBeenCalled();
    await wrapper.get("button[aria-label='开始']").trigger("click");
    await flushPromises();
    expect(api.prepareClassroom).toHaveBeenCalledWith("lesson-2");
    wrapper.unmount();
  });

  it("falls back to the first lesson in the selected deck chapter when slide lesson ids are unavailable", async () => {
    localStorage.setItem("structify.courseware.selected", JSON.stringify({
      deckId: "tree-deck-01",
      title: "第六章-树和二叉树01",
      chapter: "06",
      lessonIds: [],
    }));
    api.listClassroomLessons.mockResolvedValue([
      { id: "lesson-intro", chapterId: "01-intro", title: "绪论-数据结构的基础概念", source: "textbook", pages: "1-8" },
      { id: "lesson-tree-01", chapterId: "06-tree", title: "树与二叉树-树的基本概念", source: "textbook", pages: "150-156" },
      { id: "lesson-tree-02", chapterId: "06-tree", title: "树与二叉树-树的遍历", source: "textbook", pages: "157-162" },
    ]);
    api.getLessonCourseware.mockResolvedValue({ ...noCourseware, lessonId: "lesson-tree-01", title: "第六章-树和二叉树01" });

    const wrapper = mountClassroom();
    await flushPromises();

    expect(api.getLessonCourseware).toHaveBeenCalledWith("lesson-tree-01");
    expect(wrapper.get(".classroom__chapter-select .runtime-select__trigger").attributes("aria-label")).toContain("树与二叉树");
    expect(wrapper.get(".classroom__lesson-select .runtime-select__trigger").attributes("aria-label")).toContain("树的基本概念");
    await wrapper.get("button[aria-label='开始']").trigger("click");
    await flushPromises();
    expect(api.prepareClassroom).toHaveBeenCalledWith("lesson-tree-01");
    wrapper.unmount();
  });

  it("can infer a chapter from legacy saved deck titles", async () => {
    localStorage.setItem("structify.courseware.selected", JSON.stringify({
      deckId: "tree-deck-01",
      title: "第六章-树和二叉树01",
      lessonIds: [],
    }));
    api.listClassroomLessons.mockResolvedValue([
      { id: "lesson-intro", chapterId: "01-intro", title: "绪论", source: "textbook", pages: "1-8" },
      { id: "lesson-tree-01", chapterId: "06-tree", title: "树和二叉树", source: "textbook", pages: "150-156" },
    ]);

    const wrapper = mountClassroom();
    await flushPromises();

    expect(api.getLessonCourseware).toHaveBeenCalledWith("lesson-tree-01");
    wrapper.unmount();
  });

  it("groups topics by chapter and selects the first topic when switching chapters", async () => {
    api.listClassroomLessons.mockResolvedValue([
      { id: "intro-1", chapterId: "01-intro", title: "绪论-数据结构的基础概念", source: "textbook", pages: "1-2" },
      { id: "intro-2", chapterId: "01-intro", title: "绪论－算法与算法描述", source: "textbook", pages: "3-4" },
      { id: "list-1", chapterId: "02-list", title: "线性表—线性表的定义", source: "textbook", pages: "5-6" },
      { id: "list-2", chapterId: "02-list", title: "线性表-单链表-插入", source: "textbook", pages: "7-8" },
    ]);
    api.getLessonCourseware.mockImplementation(async (id: string) => ({ ...noCourseware, lessonId: id }));
    const wrapper = mountClassroom();
    await flushPromises();

    const chapters = wrapper.get(".classroom__chapter-select select");
    const topics = wrapper.get(".classroom__lesson-select select");
    expect(chapters.findAll("option").map((option) => option.text())).toEqual(["绪论", "线性表"]);
    expect(topics.findAll("option").map((option) => option.text())).toEqual(["数据结构的基础概念", "算法与算法描述"]);

    await topics.setValue("intro-2");
    await flushPromises();
    expect(api.getLessonCourseware).toHaveBeenLastCalledWith("intro-2");

    await chapters.setValue("02-list");
    await flushPromises();
    expect(api.getLessonCourseware).toHaveBeenLastCalledWith("list-1");
    expect(topics.findAll("option").map((option) => option.text())).toEqual(["线性表的定义", "单链表-插入"]);
    expect(wrapper.get(".classroom__lesson-select .runtime-select__trigger").attributes("aria-label")).toContain("线性表的定义");

    await wrapper.get("button[aria-label='开始']").trigger("click");
    await flushPromises();
    expect(api.prepareClassroom).toHaveBeenCalledWith("list-1");
    wrapper.unmount();
  });

  it("renders only large functional controls before class", async () => {
    const wrapper = mountClassroom();
    await flushPromises();

    expect(wrapper.get(".classroom__lesson-select .runtime-select__trigger").text()).toContain("二叉树");
    // The stage carries the theme switch, so the lesson controls are addressed by role, not by position.
    expect(wrapper.get(".classroom__silver-start .liquid-metal-button__native").text()).toBe("开始");
    expect(wrapper.find("small").exists()).toBe(false);
    expect(wrapper.find("nav").exists()).toBe(false);
    expect(wrapper.find(".classroom__conversation").exists()).toBe(false);
    expect(wrapper.find("aside.slides--courseware").exists()).toBe(true);
    wrapper.unmount();
  });

  it("shows the lesson picker in English once the language switch is set", async () => {
    setLocale("en-US");

    const wrapper = mountClassroom();
    await flushPromises();

    expect(wrapper.find(".classroom__lesson-select .runtime-select__trigger").exists()).toBe(true);
    expect(wrapper.get(".classroom__silver-start .liquid-metal-button__native").text()).toBe("Start");
    wrapper.unmount();
  });

  it("shows the lesson name without the review note baked into its title", async () => {
    // Reviewed sources carry an audit suffix in their title; it belongs to the review pipeline, not to the
    // learner choosing a lesson.
    api.listClassroomLessons.mockResolvedValue([
      { id: "lesson-2", chapterId: "08-search", title: "查找-二叉排序树（已核验教材第 271–278 页选段）", source: "textbook", pages: "271-278" },
    ]);

    const wrapper = mountClassroom();
    await flushPromises();

    expect(wrapper.get(".classroom__chapter-select .runtime-select__trigger").text()).toBe("查找");
    expect(wrapper.get(".classroom__lesson-select .runtime-select__trigger").text()).toBe("二叉排序树");
    wrapper.unmount();
  });

  it("starts, advances, and submits the current answer with revision control", async () => {
    const waiting: ClassroomSession = {
      ...opening,
      state: "WAITING",
      stage: { prompt: "根结点是什么？", stepIndex: 0, stepCount: 3, revision: 1, chapterId: "06-tree" },
    };
    api.actInClassroom
      .mockResolvedValueOnce(waiting)
      .mockResolvedValueOnce({
        ...waiting,
        state: "DISCUSS",
        stage: {
          ...waiting.stage,
          revision: 2,
          teacherResponse: { feedback: "回答正确", answered: true, kind: "answer" },
        },
      });

    const wrapper = mountClassroom();
    await flushPromises();
    await wrapper.get(".classroom__silver-start .liquid-metal-button__native").trigger("click");
    await flushPromises();

    expect(wrapper.get(".classroom__speech").text()).toBe("准备开始");
    await wrapper.get(".classroom__silver-start .liquid-metal-button__native").trigger("click");
    await flushPromises();
    expect(api.actInClassroom).toHaveBeenNthCalledWith(1, "session-1", { action: "CONTINUE", content: undefined, expectedRevision: 0 });
    expect(wrapper.get(".classroom__speech").text()).toBe("根结点是什么？");

    await wrapper.get("textarea").setValue("没有父结点的结点");
    const answer = wrapper.findAll("button").find((button) => button.text() === "回答");
    expect(answer).toBeDefined();
    await answer!.trigger("click");
    await flushPromises();

    expect(api.actInClassroom).toHaveBeenNthCalledWith(2, "session-1", {
      action: "ANSWER",
      content: "没有父结点的结点",
      expectedRevision: 1,
    });
    expect(wrapper.get(".classroom__speech").text()).toBe("回答正确");
    wrapper.unmount();
  });

  /**
   * The narration used to switch between a large centred layout and a smaller reading layout once the
   * copy passed 160 characters, on top of a viewport-relative size - so one step could look quite
   * different from the next, and from one window to another. One register now.
   */
  it("keeps one layout for the narration no matter how long the step is", async () => {
    const longPrompt = "改用尾指针表示后，两个循环单链表分别用 RA、RB 指向它们的终端结点，先把 RB 的开始结点链到 RA 的终端结点之后，再把 RA 的头结点链到 RB 的终端结点之后。".repeat(3);
    const waiting: ClassroomSession = {
      ...opening,
      state: "WAITING",
      stage: { prompt: longPrompt, stepIndex: 0, stepCount: 3, revision: 1, chapterId: "02-list" },
    };
    api.actInClassroom.mockResolvedValue(waiting);

    const wrapper = mountClassroom();
    await flushPromises();
    await wrapper.get(".classroom__silver-start .liquid-metal-button__native").trigger("click");
    await flushPromises();
    await wrapper.get(".classroom__silver-start .liquid-metal-button__native").trigger("click");
    await flushPromises();

    const speech = wrapper.get(".classroom__speech");
    expect(speech.text()).toBe(longPrompt);
    expect(speech.classes()).toEqual(["classroom__speech"]);
  });

  /**
   * A learner who cannot answer gets two honest ways out instead of having to pretend they answered: a
   * hint first (which keeps the question open), then the explained skip, which the lesson budgets.
   */
  it("offers a hint first, then the explained skip, while a question is open", async () => {
    const waiting: ClassroomSession = {
      ...opening,
      state: "WAITING",
      stage: { prompt: "根结点是什么？", stepIndex: 0, stepCount: 3, revision: 1, chapterId: "06-tree", skipsUsed: 0, skipLimit: 3 },
    };
    const hinted: ClassroomSession = {
      ...waiting,
      stage: {
        ...waiting.stage,
        revision: 2,
        teacherResponse: { feedback: "先看它有没有孩子", kind: "hint", hinted: true, answered: false },
      },
    };
    api.actInClassroom.mockResolvedValueOnce(waiting).mockResolvedValueOnce(hinted);

    const wrapper = mountClassroom();
    await flushPromises();
    await wrapper.get(".classroom__silver-start .liquid-metal-button__native").trigger("click");   // 开始
    await flushPromises();
    await wrapper.get(".classroom__silver-start .liquid-metal-button__native").trigger("click");   // 进入这一步
    await flushPromises();
    expect(wrapper.get(".classroom__speech").text()).toBe("根结点是什么？");

    const hint = wrapper.findAll(".classroom__silver-action .liquid-metal-button__native").find((button) => button.text() === "要点提示");
    expect(hint, "提问时应当先给出「要点提示」").toBeTruthy();
    await hint!.trigger("click");
    await flushPromises();

    expect(api.actInClassroom).toHaveBeenLastCalledWith("session-1", { action: "HINT", content: undefined, expectedRevision: 1 });
    expect(wrapper.get(".classroom__speech").text()).toBe("先看它有没有孩子");
    // The question is still open, so the learner can answer it - or take the explained skip now.
    expect(wrapper.findAll(".classroom__silver-action .liquid-metal-button__native").some((button) => button.text() === "回答")).toBe(true);
    expect(wrapper.findAll(".classroom__silver-action .liquid-metal-button__native").some((button) => button.text() === "看讲解，跳过这题")).toBe(true);
    expect(wrapper.findAll(".classroom__silver-action .liquid-metal-button__native").some((button) => button.text() === "要点提示")).toBe(false);
  });

  it("stops offering the skip once the lesson has spent its budget", async () => {
    const spent: ClassroomSession = {
      ...opening,
      state: "WAITING",
      stage: {
        prompt: "根结点是什么？",
        stepIndex: 0, stepCount: 3, revision: 1, chapterId: "06-tree", skipsUsed: 3, skipLimit: 3,
        teacherResponse: { feedback: "先看它有没有孩子", kind: "hint", hinted: true, answered: false },
      },
    };
    api.actInClassroom.mockResolvedValue(spent);

    const wrapper = mountClassroom();
    await flushPromises();
    await wrapper.get(".classroom__silver-start .liquid-metal-button__native").trigger("click");
    await flushPromises();
    await wrapper.get(".classroom__silver-start .liquid-metal-button__native").trigger("click");
    await flushPromises();

    const labels = wrapper.findAll(".classroom__silver-action .liquid-metal-button__native").map((button) => button.text());
    expect(labels).not.toContain("看讲解，跳过这题");
    expect(labels).toContain("回答");
  });

  it("keeps a wrong answer on the same question until it is answered again", async () => {
    // The regression this guards: grading a wrong answer used to consume the question, so the very next
    // "下一步" walked past it - and off the end of the lesson when the question was the last step.
    const question: ClassroomSession = {
      ...opening,
      state: "WAITING",
      stage: {
        prompt: "二叉排序树的中序遍历结果是什么？",
        stepIndex: 4,
        stepCount: 9,
        revision: 7,
        chapterId: "08-search",
        questionSource: "slide",
        slideRefs: ["deck-s005"],
        slideMatch: { slideId: "deck-s005", kind: "DIRECT", score: 1, source: "script", reason: "script-refs" },
      },
    };
    openSession(question.id);
    api.getClassroomSession.mockResolvedValue(question);
    api.actInClassroom.mockResolvedValue({
      ...question,
      stage: {
        ...question.stage,
        revision: 8,
        teacherResponse: {
          status: "INCORRECT",
          feedback: "漏掉了右子树，再按中序的顺序看一遍",
          answered: false,
          retry: true,
          kind: "answer",
          attempts: 1,
        },
      },
    });

    const wrapper = mountClassroom();
    await flushPromises();
    expect(wrapper.find("p[aria-label='问题来源']").exists()).toBe(false);

    await wrapper.get("textarea").setValue("1 2 3");
    await wrapper.findAll("button").find((button) => button.text() === "回答")!.trigger("click");
    await flushPromises();

    expect(api.actInClassroom).toHaveBeenCalledWith("session-1", {
      action: "ANSWER",
      content: "1 2 3",
      expectedRevision: 7,
    });
    expect(wrapper.get(".classroom__speech").text()).toBe("漏掉了右子树，再按中序的顺序看一遍");
    expect(wrapper.get("p[aria-label='判答结果']").text()).toBe("回答不正确，再想想。");
    const labels = wrapper.findAll("button").map((button) => button.text());
    expect(labels).toContain("再答一次");
    expect(labels).not.toContain("下一步");
    // The learner answers again in place instead of the lesson moving on.
    expect(wrapper.find("textarea").exists()).toBe(true);
    await wrapper.get("textarea").setValue("1 2 3 4 5");
    await wrapper.findAll("button").find((button) => button.text() === "再答一次")!.trigger("click");
    await flushPromises();
    expect(api.actInClassroom).toHaveBeenLastCalledWith("session-1", {
      action: "ANSWER",
      content: "1 2 3 4 5",
      expectedRevision: 8,
    });

    // Asking the teacher for help on an open question keeps the answer box: the learner still has to
    // answer it, so hiding the box would leave no way to do so.
    api.actInClassroom.mockResolvedValue({
      ...question,
      stage: {
        ...question.stage,
        revision: 9,
        teacherResponse: { feedback: "从中序的顺序想一想：先左子树，再根，再右子树。", answered: false, kind: "question" },
      },
    });
    await wrapper.get("textarea").setValue("中序应该怎么排？");
    await wrapper.findAll("button").find((button) => button.text() === "提问")!.trigger("click");
    await flushPromises();
    expect(wrapper.get(".classroom__speech").text()).toContain("先左子树");
    expect(wrapper.find("textarea").exists()).toBe(true);
    expect(wrapper.findAll("button").find((button) => button.text() === "回答")?.text()).toBe("回答");
    wrapper.unmount();
  });

  it("shows the courseware page the prepared step asks for", async () => {
    const slides = [1, 2, 3].map((number) => ({
      id: `deck-s00${number}`,
      deckId: "deck",
      deckTitle: "第六章-树和二叉树01",
      slideNumber: number,
      chapter: "06",
      title: `第 ${number} 页`,
      rawText: "",
      speakerNotes: "",
      semanticSummary: "",
      teachingRole: "concept",
      teachingFocus: "",
      concepts: [],
      visualAnchors: [],
      shouldShow: true,
      lessonIds: [],
      imageUrl: `/api/v1/presentation/slides/deck-s00${number}/image`,
      section: "6.3",
      role: "定义",
      terms: ["二叉树"],
    }));
    api.getLessonCourseware.mockResolvedValue({ ...noCourseware, slides });
    const explain: ClassroomSession = {
      ...opening,
      state: "EXPLAIN",
      stage: {
        content: "先看课件",
        stepIndex: 0,
        stepCount: 3,
        revision: 2,
        chapterId: "06-tree",
        lessonId: "lesson-1",
        slideRefs: ["deck-s003"],
        slideMatch: { slideId: "deck-s003", kind: "DIRECT", score: 0.5, source: "auto", subLessonTitle: "树与二叉树" },
      },
    };
    openSession(explain.id);
    api.getClassroomSession.mockResolvedValue(explain);

    const wrapper = mountClassroom();
    await flushPromises();

    const panel = wrapper.get("aside[aria-label='课件']");
    expect(panel.find(".slides__position").exists()).toBe(false);
    expect(panel.get("img.slides__image").attributes("src")).toBe("/api/v1/presentation/slides/deck-s003/image");

    await panel.findAll("button").find((button) => button.text() === "上一页")!.trigger("click");
    expect(panel.get("img.slides__image").attributes("src")).toBe("/api/v1/presentation/slides/deck-s002/image");
    wrapper.unmount();
  });

  it("moves the lesson picker into the courseware stage before a class starts", async () => {
    api.getLessonCourseware.mockResolvedValue({
      ...noCourseware,
      title: "二叉树课件",
      slides: [{
        id: "deck-s001",
        deckId: "deck",
        deckTitle: "二叉树课件",
        slideNumber: 1,
        imageUrl: "/api/v1/presentation/slides/deck-s001/image",
        section: "1.1",
      }],
    });

    const wrapper = mountClassroom();
    await flushPromises();

    expect(wrapper.find(".classroom--courseware").exists()).toBe(true);
    expect(wrapper.find(".classroom__conversation").exists()).toBe(false);
    expect(wrapper.find("aside.slides--courseware .classroom__lesson-select .runtime-select__trigger").exists()).toBe(true);
    expect(wrapper.get("aside.slides--courseware .classroom__silver-start .liquid-metal-button__native").text()).toBe("开始");
    wrapper.unmount();
  });

  it("shows the local demo deck when no classroom courseware is published", async () => {
    api.listClassroomLessons.mockResolvedValue([]);
    api.getLessonCourseware.mockResolvedValue({ ...noCourseware, ready: false });

    const wrapper = mountClassroom();
    await flushPromises();

    const panel = wrapper.get("aside.slides--courseware");
    expect(panel.get(".slides__title").text()).toBe("Structify 本地演示课件");
    expect(panel.get("img.slides__image").attributes("src")).toContain("slide-01");
    expect(panel.find(".slides__position").exists()).toBe(false);
    expect(panel.get(".classroom__lesson-select .runtime-select__trigger").text()).toContain("Structify 本地演示课件");
    expect(panel.get(".classroom__silver-start .liquid-metal-button__native").attributes("disabled")).toBeDefined();
    wrapper.unmount();
  });

  it("shows which reviewed textbook pages back the step on screen", async () => {
    const explain: ClassroomSession = {
      ...opening,
      state: "EXPLAIN",
      stage: {
        content: "删除结点分三种情况",
        stepIndex: 1,
        stepCount: 3,
        revision: 2,
        chapterId: "08-search",
        lessonId: "lesson-1",
        slideRefs: ["deck-s002"],
        slideMatch: { slideId: "deck-s002", kind: "DIRECT", score: 1, source: "script", reason: "script-refs" },
        sourcePages: ["第 276 页"],
        textbookMatch: "exact",
      },
    };
    openSession(explain.id);
    api.getClassroomSession.mockResolvedValue(explain);

    const wrapper = mountClassroom();
    await flushPromises();

    expect(wrapper.find("p[aria-label='教材出处']").exists()).toBe(false);
    wrapper.unmount();
  });

  it("keeps an interruption on the current step", async () => {
    const explain: ClassroomSession = {
      ...opening,
      state: "EXPLAIN",
      stage: { content: "树由结点和边组成", stepIndex: 0, stepCount: 3, revision: 4, chapterId: "06-tree" },
    };
    openSession(explain.id);
    api.getClassroomSession.mockResolvedValue(explain);
    api.actInClassroom.mockResolvedValue({
      ...explain,
      stage: {
        ...explain.stage,
        revision: 5,
        teacherResponse: { feedback: "叶子结点没有孩子", answered: false, kind: "question" },
      },
    });

    const wrapper = mountClassroom();
    await flushPromises();
    await wrapper.get("textarea").setValue("什么是叶子结点？");
    await wrapper.findAll("button").find((button) => button.text() === "提问")!.trigger("click");
    await flushPromises();

    expect(api.actInClassroom).toHaveBeenCalledWith("session-1", {
      action: "ASK",
      content: "什么是叶子结点？",
      expectedRevision: 4,
    });
    expect(wrapper.get(".classroom__speech").text()).toBe("叶子结点没有孩子");
    expect(wrapper.findAll("button").find((button) => button.text() === "回到课堂")?.text()).toBe("回到课堂");
    wrapper.unmount();
  });

  it("keeps teaching after a summary page until the last step is done", async () => {
    // A deck's own 小结 page reports the summary state but is not the end of the lesson: the next
    // sub-lesson still follows it, so the classroom must not offer to leave the course here.
    const midSummary: ClassroomSession = {
      ...opening,
      state: "SUMMARY",
      stage: { content: "本小节小结", stepIndex: 1, stepCount: 3, revision: 6, chapterId: "06-tree" },
    };
    openSession(midSummary.id);
    api.getClassroomSession.mockResolvedValue(midSummary);

    const wrapper = mountClassroom();
    await flushPromises();

    const labels = wrapper.findAll("button").map((button) => button.text());
    expect(labels).toContain("下一步");
    expect(labels).not.toContain("换课");
    expect(wrapper.find("textarea").exists()).toBe(true);
    wrapper.unmount();
  });

  it("ends the lesson once the cursor has passed the last step", async () => {
    const done: ClassroomSession = {
      ...opening,
      state: "SUMMARY",
      stage: { content: "本课已结束。", stepIndex: 3, stepCount: 3, revision: 9, chapterId: "06-tree" },
    };
    openSession(done.id);
    api.getClassroomSession.mockResolvedValue(done);

    const wrapper = mountClassroom();
    await flushPromises();

    expect(wrapper.findAll("button").map((button) => button.text())).toContain("换课");
    expect(wrapper.find("textarea").exists()).toBe(false);
    wrapper.unmount();
  });

  it("lets the learner leave mid-lesson and pick the same session back up", async () => {
    const explain: ClassroomSession = {
      ...opening,
      state: "EXPLAIN",
      stage: {
        content: "先看课件",
        stepIndex: 0,
        stepCount: 3,
        revision: 2,
        chapterId: "06-tree",
        lessonId: "lesson-1",
      },
    };
    openSession(explain.id);
    api.getClassroomSession.mockResolvedValue(explain);

    const wrapper = mountClassroom();
    await flushPromises();

    expect(wrapper.get("p[aria-label='当前课时']").text()).toBe("二叉树");
    expect(wrapper.get(".classroom__topbar-meta").text()).toBe("第 1 / 3 步");

    await wrapper.findAll("button").find((button) => button.text() === "退出课堂")!.trigger("click");
    await flushPromises();

    // Leaving only unhooks the session; it stays recallable from the picker and the entry page.
    expect(localStorage.getItem("structify.classroom.last")).toBe("session-1");
    expect(wrapper.find(".classroom__lesson-select .runtime-select__trigger").exists()).toBe(true);
    expect(wrapper.findAll("button").map((button) => button.text())).toContain("继续上次课堂");

    await wrapper.findAll("button").find((button) => button.text() === "继续上次课堂")!.trigger("click");
    await flushPromises();

    expect(api.getClassroomSession).toHaveBeenLastCalledWith("session-1");
    expect(wrapper.find(".classroom__lesson-select .runtime-select__trigger").exists()).toBe(false);
    expect(wrapper.get("p[aria-label='当前课时']").text()).toBe("二叉树");
    wrapper.unmount();
  });

  it("opens on the lesson picker instead of the session stored from last time", async () => {
    // The regression this guards: a stored session id used to be opened on mount, so a learner who had
    // not picked anything yet was dropped into the middle of the previous lesson.
    localStorage.setItem("structify.classroom.last", "session-1");
    api.getClassroomSession.mockResolvedValue(opening);

    const wrapper = mountClassroom();
    await flushPromises();

    expect(api.getClassroomSession).not.toHaveBeenCalled();
    expect(wrapper.get(".classroom__lesson-select .runtime-select__trigger").text()).toContain("二叉树");
    expect(wrapper.find(".classroom__topbar").exists()).toBe(false);
    expect(wrapper.findAll("button").map((button) => button.text())).toContain("继续上次课堂");
    wrapper.unmount();
  });

  it("shows the glass home button in the slide card before a lesson starts", async () => {
    const wrapper = mountClassroom();
    await flushPromises();

    const homeButton = wrapper.get(".slides__header-actions .classroom__icon-button[aria-label='回到首页']");
    await homeButton.trigger("click");
    await flushPromises();

    expect(navigation.push).toHaveBeenCalledWith("/");
    wrapper.unmount();
  });

  it("opens the session named in the url and ignores one the server has forgotten", async () => {
    openSession("session-1");
    api.getClassroomSession.mockResolvedValue(opening);

    const wrapper = mountClassroom();
    await flushPromises();

    expect(api.getClassroomSession).toHaveBeenCalledWith("session-1");
    expect(wrapper.get("p[aria-label='当前课时']").text()).toBe("二叉树");
    wrapper.unmount();

    navigation.query = { session: "session-gone" };
    api.getClassroomSession.mockRejectedValue(new Error("课堂不存在"));

    const stale = mountClassroom();
    await flushPromises();

    // A stale link falls back to the picker rather than an error screen.
    expect(stale.find(".classroom__lesson-select .runtime-select__trigger").exists()).toBe(true);
    expect(stale.find(".classroom__error").exists()).toBe(false);
    stale.unmount();
  });

  it("goes back to the entry page without forgetting the lesson", async () => {
    const explain: ClassroomSession = {
      ...opening,
      state: "EXPLAIN",
      stage: { content: "先看课件", stepIndex: 0, stepCount: 3, revision: 2, chapterId: "06-tree", lessonId: "lesson-1" },
    };
    openSession(explain.id);
    api.getClassroomSession.mockResolvedValue(explain);

    const wrapper = mountClassroom();
    await flushPromises();

    await wrapper.findAll("button").find((button) => button.text() === "回到首页")!.trigger("click");
    await flushPromises();

    expect(navigation.push).toHaveBeenCalledWith("/");
    expect(localStorage.getItem("structify.classroom.last")).toBe("session-1");
    wrapper.unmount();
  });
});
