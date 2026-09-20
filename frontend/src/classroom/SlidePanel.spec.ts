import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import SlidePanel from "./SlidePanel.vue";
import type { LessonCourseware, PresentationSlide } from "../shared/types/contracts";

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

  it("lets the teacher pin the page for the current step", async () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([slide("a", 1), slide("b", 2)]), activeSlideId: "a", match, loading: false, error: "" },
    });
    await wrapper.vm.$nextTick();

    await wrapper.findAll("button").find((button) => button.text() === "本段改用这一页")!.trigger("click");
    await wrapper.get("select[aria-label='选择本段使用的课件页']").setValue("b");
    await wrapper.findAll("button").find((button) => button.text() === "确定")!.trigger("click");

    expect(wrapper.emitted("pin")).toEqual([["b"]]);
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
