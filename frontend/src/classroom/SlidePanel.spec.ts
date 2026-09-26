import { describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import SlidePanel from "./SlidePanel.vue";
import type { LessonCourseware, PresentationSlide } from "../shared/types/contracts";

/** The hands-on pane loads its own samples; keep that off the network in these mounts. */
vi.mock("../user/runtime", () => ({
  userApi: {
    listCodeSamples: vi.fn(async () => ({ lessons: [], sampleCount: 0 })),
    runCode: vi.fn(),
  },
}));

function slide(id: string, number: number, shouldShow = true): PresentationSlide {
  return {
    id,
    deckId: "deck",
    deckTitle: "第八章-查找03",
    slideNumber: number,
    chapter: "08",
    title: `第 ${number} 页`,
    rawText: "",
    speakerNotes: "",
    semanticSummary: `摘要 ${number}`,
    teachingRole: "concept",
    teachingFocus: "",
    concepts: [],
    visualAnchors: [],
    shouldShow,
    lessonIds: [],
    imageUrl: `/api/v1/presentation/slides/${id}/image`,
    section: "8.3.4",
    role: "操作",
    terms: ["节点删除", "三种情况"],
  };
}

const match = {
  slideId: "b",
  kind: "DIRECT" as const,
  score: 0.4,
  source: "auto" as const,
  subLessonId: "08-02B",
  subLessonTitle: "二叉排序树的删除与性能",
  scene: "concept-one",
};

function courseware(slides: PresentationSlide[]): LessonCourseware {
  return { lessonId: "lesson-1", coursewareKey: "08-01", title: "查找", source: "", ready: true, builtAt: "", slides };
}

describe("slide panel", () => {
  it("follows the slide the current step asks for", async () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([slide("a", 1), slide("b", 2), slide("c", 3)]), activeSlideId: "b", match, loading: false, error: "" },
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain("2/3");
    expect(wrapper.get("img").attributes("src")).toBe("/api/v1/presentation/slides/b/image");
    // A directly matched page is the normal case; saying so on screen is noise.
    expect(wrapper.find(".slides__badge").exists()).toBe(false);
    expect(wrapper.find(".slides__annotation").exists()).toBe(false);
  });

  it("lets the student page through the deck with only prev/next", async () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([slide("a", 1), slide("b", 2)]), activeSlideId: "a", match, loading: false, error: "" },
    });
    await wrapper.vm.$nextTick();

    // The page-number pill grid is gone: paging is prev/next (and the full-courseware browser) only.
    expect(wrapper.text()).not.toContain("课件页列表");
    await wrapper.findAll("button").find((button) => button.text() === "下一页")!.trigger("click");
    expect(wrapper.text()).toContain("2/2");
  });

  it("keeps the heading to the deck title so a long page title cannot push the page number out", async () => {
    const longPageTitle = "求单链表的长度：算法描述：可以采用数结点的方法来求出单链表的长度，指针 p 依次指向各个结点";
    const verbose = { ...slide("a", 1), title: longPageTitle };
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([verbose]), activeSlideId: "a", match, loading: false, error: "" },
    });
    await wrapper.vm.$nextTick();

    // The page title lives on the slide itself; the pane's heading stays the short deck title.
    expect(wrapper.get(".slides__title").text()).toBe("第八章-查找03");
    expect(wrapper.get(".slides__title").attributes("title")).toBe("第八章-查找03");
    expect(wrapper.get(".slides__position").text()).toBe("1/1");
  });

  it("explains an empty lesson instead of showing a broken image", () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([]), activeSlideId: null, match: null, loading: false, error: "" },
    });

    expect(wrapper.text()).toContain("本课时没有配套课件");
    expect(wrapper.find("img").exists()).toBe(false);
  });

  it("keeps the learner-facing copy quiet: no internal annotation, no gap essay", async () => {
    const wrapper = mount(SlidePanel, {
      props: {
        courseware: courseware([slide("a", 1)]),
        activeSlideId: "a",
        match: { ...match, slideId: "a", kind: "CONTINUITY" as const },
        loading: false,
        error: "",
      },
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).not.toContain("8.3.4 · 操作");
    expect(wrapper.text()).not.toContain("节点删除");
    expect(wrapper.text()).not.toContain("没有单独一页");
    expect(wrapper.text()).toContain("沿用上一页");
  });

  it("names textbook-only material with a single word instead of an essay", async () => {
    const wrapper = mount(SlidePanel, {
      props: {
        courseware: courseware([slide("a", 1)]),
        activeSlideId: "a",
        match: { ...match, slideId: "a", kind: "CONTINUITY" as const, reason: "scope-only" as const },
        loading: false,
        error: "",
      },
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain("教材延伸");
    expect(wrapper.text()).not.toContain("这一段是教材内容");
  });

  it("reports a failed page image", async () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([slide("a", 1)]), activeSlideId: "a", match, loading: false, error: "" },
    });
    await wrapper.get("img").trigger("error");

    expect(wrapper.text()).toContain("图片加载失败");
  });
});
