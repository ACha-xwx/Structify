import { afterEach, describe, expect, it, vi } from "vitest";
import { enableAutoUnmount, mount } from "@vue/test-utils";
import { nextTick } from "vue";
import SlidePanel from "./SlidePanel.vue";
import type { LessonCourseware, PresentationSlide } from "../shared/types/contracts";

enableAutoUnmount(afterEach);

async function pressKey(key: string, options: KeyboardEventInit = {}) {
  const event = new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true, ...options });
  window.dispatchEvent(event);
  await nextTick();
  return event;
}

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

    expect(wrapper.find(".slides__position").exists()).toBe(false);
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
    expect(wrapper.get("img").attributes("src")).toBe("/api/v1/presentation/slides/b/image");
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
    expect(wrapper.find(".slides__position").exists()).toBe(false);
  });

  it("explains an empty lesson instead of showing a broken image", () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([]), activeSlideId: null, match: null, loading: false, error: "" },
    });

    expect(wrapper.text()).toContain("本课时没有配套课件");
    expect(wrapper.find(".slides__stage img").exists()).toBe(false);
  });

  it("keeps the stage visible when the courseware bundle is unavailable", () => {
    const wrapper = mount(SlidePanel, {
      props: {
        courseware: { ...courseware([]), ready: false },
        activeSlideId: null,
        match: null,
        loading: false,
        error: "",
      },
    });

    expect(wrapper.get(".slides__stage--empty").text()).toBe("课件暂时无法访问");
    expect(wrapper.find(".slides__stage img").exists()).toBe(false);
  });

  it("shows loading before reporting that the courseware is unavailable", async () => {
    const wrapper = mount(SlidePanel, {
      props: {
        courseware: { ...courseware([]), ready: false },
        activeSlideId: null,
        match: null,
        loading: true,
        error: "",
      },
    });

    expect(wrapper.get(".slides__stage--empty").text()).toBe("正在载入课件");
    await wrapper.setProps({ loading: false });
    expect(wrapper.get(".slides__stage--empty").text()).toBe("课件暂时无法访问");
    wrapper.unmount();
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

  it("pages with left and right arrows without wrapping past the deck boundaries", async () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([slide("a", 1), slide("b", 2)]), activeSlideId: "a", match, loading: false, error: "" },
    });

    expect((await pressKey("ArrowLeft")).defaultPrevented).toBe(true);
    expect(wrapper.get(".slides__image").attributes("src")).toContain("/a/image");
    await wrapper.get(".slides__image").trigger("error");
    await pressKey("ArrowRight");
    expect(wrapper.get(".slides__image").attributes("src")).toContain("/b/image");
    await pressKey("ArrowRight");
    expect(wrapper.get(".slides__image").attributes("src")).toContain("/b/image");
    await pressKey("ArrowLeft");
    expect(wrapper.get(".slides__image").attributes("src")).toContain("/a/image");
  });

  it.each([
    '<input value="answer">',
    '<textarea>answer</textarea>',
    '<select><option>lesson</option></select>',
    '<div contenteditable="true" tabindex="0"><span>answer</span></div>',
    '<button aria-haspopup="listbox"><span>lesson</span></button>',
    '<div role="listbox" tabindex="0"><span role="option">lesson</span></div>',
    '<input type="range" value="50">',
  ])("keeps arrows available to focused editing and selection controls: %s", async (control) => {
    const wrapper = mount(SlidePanel, {
      attachTo: document.body,
      props: { courseware: courseware([slide("a", 1), slide("b", 2)]), activeSlideId: "a", match, loading: false, error: "" },
      slots: { controls: control },
    });
    const element = wrapper.get(".slides__controls").element.firstElementChild as HTMLElement;
    element.focus();
    const event = new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true, cancelable: true });
    (element.firstElementChild ?? element).dispatchEvent(event);
    await nextTick();

    expect(event.defaultPrevented).toBe(false);
    expect(wrapper.get(".slides__image").attributes("src")).toContain("/a/image");
    element.blur();
  });

  it("ignores shortcuts, composition, other keys, and events already handled by another control", async () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([slide("a", 1), slide("b", 2)]), activeSlideId: "a", match, loading: false, error: "" },
    });
    for (const options of [{ altKey: true }, { ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { isComposing: true }]) {
      expect((await pressKey("ArrowRight", options)).defaultPrevented).toBe(false);
    }
    expect((await pressKey("ArrowDown")).defaultPrevented).toBe(false);
    const handled = new KeyboardEvent("keydown", { key: "ArrowRight", cancelable: true });
    handled.preventDefault();
    window.dispatchEvent(handled);
    await nextTick();

    expect(wrapper.get(".slides__image").attributes("src")).toContain("/a/image");
  });

  it("does not page hidden slides while loading or showing a courseware error", async () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([slide("a", 1), slide("b", 2)]), activeSlideId: "a", match, loading: true, error: "" },
    });
    expect((await pressKey("ArrowRight")).defaultPrevented).toBe(false);
    await wrapper.setProps({ loading: false, error: "unavailable" });
    expect((await pressKey("ArrowRight")).defaultPrevented).toBe(false);
    await wrapper.setProps({ error: "" });
    expect(wrapper.get(".slides__image").attributes("src")).toContain("/a/image");
    await wrapper.setProps({ courseware: courseware([]) });
    expect((await pressKey("ArrowRight")).defaultPrevented).toBe(false);
  });

  it("stops handling keyboard paging after leaving the player", async () => {
    const wrapper = mount(SlidePanel, {
      props: { courseware: courseware([slide("a", 1), slide("b", 2)]), activeSlideId: "a", match, loading: false, error: "" },
    });
    wrapper.unmount();

    expect((await pressKey("ArrowRight")).defaultPrevented).toBe(false);
  });
});
