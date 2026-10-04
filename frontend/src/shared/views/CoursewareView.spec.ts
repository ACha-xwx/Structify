import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RouterLinkStub, enableAutoUnmount, flushPromises, mount } from "@vue/test-utils";
import CoursewareView from "./CoursewareView.vue";

enableAutoUnmount(afterEach);

const listPresentationDecks = vi.fn();
const listDeckSlides = vi.fn();
const navigation = vi.hoisted(() => ({ push: vi.fn() }));
const catalogSession = vi.hoisted(() => ({ identity: 0 }));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: navigation.push }),
}));

vi.mock("../../user/runtime", async () => {
  const { createCoursewareCatalog } = await import("../courseware/courseware-catalog");
  return { coursewareCatalog: createCoursewareCatalog({
    listPresentationDecks: (...args: unknown[]) => listPresentationDecks(...args),
    listDeckSlides: (...args: unknown[]) => listDeckSlides(...args),
  }, () => catalogSession.identity) };
});

beforeEach(() => {
  catalogSession.identity += 1;
  localStorage.clear();
  navigation.push.mockReset();
  listPresentationDecks.mockReset();
  listDeckSlides.mockReset();
  listDeckSlides.mockResolvedValue([slide("a", 1), slide("b", 2)]);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

function deck(deckId: string, chapter: string, title: string) {
  return { deckId, title, chapter, slideCount: 2, coverSlideId: `${deckId}-s001` };
}

function slide(id: string, number: number) {
  return {
    id,
    deckId: "ch03-deck01",
    deckTitle: "第三章-限定性线性表-01",
    slideNumber: number,
    chapter: "03",
    title: `第 ${number} 页`,
    rawText: "",
    speakerNotes: "",
    semanticSummary: `摘要 ${number}`,
    teachingRole: "concept",
    teachingFocus: "",
    concepts: [],
    visualAnchors: [],
    shouldShow: true,
    lessonIds: [] as string[],
    imageUrl: `/api/v1/presentation/slides/${id}/image`,
    section: "3.1",
    role: "定义",
    terms: [],
  };
}

async function mountView(availableDecks = [deck("ch03-deck01", "03", "第三章-限定性线性表-01"), deck("ch03-deck02", "03", "第三章-限定性线性表-02")]) {
  listPresentationDecks.mockResolvedValue(availableDecks);
  const wrapper = mount(CoursewareView, { attachTo: document.body, global: { stubs: { RouterLink: RouterLinkStub } } });
  await flushPromises();
  return wrapper;
}

describe("courseware browser", () => {
  it("reuses an opened deck and metadata after leaving and returning", async () => {
    const first = await mountView();
    await first.get('[data-deck-id="ch03-deck02"]').trigger("click");
    await flushPromises();
    await first.get('[data-deck-id="ch03-deck01"]').trigger("click");
    await flushPromises();
    expect(listDeckSlides).toHaveBeenCalledTimes(2);
    first.unmount();
    const second = await mountView();
    expect(listPresentationDecks).toHaveBeenCalledTimes(1);
    expect(listDeckSlides).toHaveBeenCalledTimes(2);
    expect(second.get(".courseware__image").attributes("src")).toBe("/api/v1/presentation/slides/a/image");
  });

  it("deduplicates clicks on a deck while its request is pending", async () => {
    let resolve!: (slides: ReturnType<typeof slide>[]) => void;
    listDeckSlides.mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    const wrapper = await mountView();
    await wrapper.get('[data-deck-id="ch03-deck01"]').trigger("click");
    expect(listDeckSlides).toHaveBeenCalledTimes(1);
    resolve([slide("latest", 1)]);
    await flushPromises();
    expect(wrapper.get(".courseware__image").attributes("src")).toBe("/api/v1/presentation/slides/latest/image");
  });

  it("loads the visible image first and cancels pending warming when leaving", async () => {
    const requested: string[] = [];
    vi.stubGlobal("Image", class { set src(value: string) { requested.push(value); } });
    const wrapper = await mountView();
    vi.useFakeTimers();
    const image = wrapper.get(".courseware__image");
    expect(image.attributes("fetchpriority")).toBe("high");
    expect(requested).toEqual([]);
    await image.trigger("load");
    expect(requested).toEqual([]);
    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(1500);
    expect(requested).toEqual([]);
  });

  it("does not start a slide request when the directory arrives after leaving", async () => {
    let resolve!: (decks: ReturnType<typeof deck>[]) => void;
    listPresentationDecks.mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    const wrapper = mount(CoursewareView, { global: { stubs: { RouterLink: RouterLinkStub } } });
    wrapper.unmount();
    resolve([deck("ch03-deck01", "03", "第三章-限定性线性表-01")]);
    await flushPromises();
    expect(listDeckSlides).not.toHaveBeenCalled();
  });

  it("groups normalized deck names beneath an initially expanded chapter", async () => {
    const wrapper = await mountView();

    expect(wrapper.findAll(".runtime-select")).toHaveLength(0);
    expect(wrapper.get('[data-chapter-key="03"]').text()).toBe("栈和队列");
    expect(wrapper.get('[data-chapter-key="03"]').attributes("aria-expanded")).toBe("true");
    expect(wrapper.findAll(".courseware__deck-option").map((option) => option.text())).toEqual(["栈和队列 1", "栈和队列 2"]);
    expect(wrapper.get('[data-deck-id="ch03-deck01"]').attributes("aria-pressed")).toBe("true");
    expect(wrapper.get('[data-deck-id="ch03-deck02"]').attributes("aria-pressed")).toBe("false");
    expect(listDeckSlides).toHaveBeenCalledExactlyOnceWith("ch03-deck01");
    expect(wrapper.get(".courseware__image").attributes("src")).toBe("/api/v1/presentation/slides/a/image");
  });

  it("orders chapters and sections numerically and changes the preview only when a deck is chosen", async () => {
    const wrapper = await mountView([
      deck("ch06-deck10", "06", "第六章-树和二叉树10-"),
      deck("ch09-deck01", "09", "第九章-排序01-分享"),
      deck("ch06-deck02", "06", "第六章-树和二叉树02-"),
      deck("ch01-deck01", "01", "数据结构-第一章-绪论01"),
      deck("ch06-deck01", "06", "第六章-树和二叉树01-"),
    ]);

    expect(wrapper.findAll(".courseware__chapter-trigger").map((trigger) => trigger.text())).toEqual(["绪论", "树和二叉树", "排序"]);
    expect(wrapper.findAll(".courseware__chapter-panel--open .courseware__deck-option").map((option) => option.text())).toEqual(["绪论 1"]);
    await wrapper.get('[data-chapter-key="06"]').trigger("click");

    expect(wrapper.findAll(".courseware__chapter-panel--open .courseware__deck-option").map((option) => option.text())).toEqual(["树和二叉树 1", "树和二叉树 2", "树和二叉树 10"]);
    expect(listDeckSlides).toHaveBeenCalledExactlyOnceWith("ch01-deck01");
    expect(wrapper.get('[data-deck-id="ch01-deck01"]').attributes("aria-pressed")).toBe("true");
    await wrapper.get('[data-deck-id="ch06-deck02"]').trigger("click");
    await flushPromises();
    expect(listDeckSlides).toHaveBeenLastCalledWith("ch06-deck02");
    expect(wrapper.get('[data-deck-id="ch06-deck02"]').attributes("aria-pressed")).toBe("true");
    await wrapper.get('[data-chapter-key="09"]').trigger("click");

    expect(wrapper.findAll(".courseware__chapter-panel--open .courseware__deck-option").map((option) => option.text())).toEqual(["排序 1"]);
    await wrapper.get('[data-deck-id="ch09-deck01"]').trigger("click");
    await flushPromises();

    expect(listDeckSlides).toHaveBeenLastCalledWith("ch09-deck01");
  });

  it("allows one expanded chapter or none without discarding the current preview", async () => {
    const wrapper = await mountView([
      deck("ch01-deck01", "01", "第一章-绪论01"),
      deck("ch03-deck01", "03", "第三章-限定性线性表-01"),
    ]);
    const first = wrapper.get('[data-chapter-key="01"]');
    const third = wrapper.get('[data-chapter-key="03"]');

    await third.trigger("click");
    expect(first.attributes("aria-expanded")).toBe("false");
    expect(third.attributes("aria-expanded")).toBe("true");
    expect(wrapper.findAll(".courseware__chapter-panel--open")).toHaveLength(1);
    const firstPanel = wrapper.get(`#${first.attributes("aria-controls")}`);
    expect(firstPanel.attributes("aria-hidden")).toBe("true");
    expect(firstPanel.attributes("inert")).toBeDefined();
    await third.trigger("click");

    expect(third.attributes("aria-expanded")).toBe("false");
    expect(wrapper.findAll(".courseware__chapter-panel--open")).toHaveLength(0);
    expect(listDeckSlides).toHaveBeenCalledExactlyOnceWith("ch01-deck01");
    expect(wrapper.get(".courseware__image").attributes("src")).toBe("/api/v1/presentation/slides/a/image");
  });

  it("keeps one heading row and pages with circular silver controls", async () => {
    const wrapper = await mountView();

    expect(wrapper.get(".courseware__caption").text()).toBe("第 1 页");
    expect(wrapper.find(".courseware__position").exists()).toBe(false);

    const buttons = wrapper.findAll(".courseware__silver-control .liquid-metal-button__native");
    expect(buttons).toHaveLength(2);
    expect(wrapper.findAll(".courseware__silver-control[data-liquid-geometry=\"circle\"]")).toHaveLength(2);
    expect(buttons[0].attributes("aria-label")).toBe("上一页");
    expect(buttons[1].attributes("aria-label")).toBe("下一页");
    await buttons[1].trigger("click");

    expect(wrapper.find(".courseware__position").exists()).toBe(false);
    expect(wrapper.get(".courseware__caption").text()).toBe("第 2 页");
  });

  it("saves the current deck and its lesson mappings before returning to the classroom", async () => {
    const wrapper = await mountView();
    listDeckSlides.mockResolvedValue([
      { ...slide("c", 1), deckId: "ch03-deck02", lessonIds: ["lesson-2", "lesson-2"] },
      { ...slide("d", 2), deckId: "ch03-deck02", lessonIds: ["lesson-3", ""] },
    ]);
    navigation.push.mockImplementation(() => {
      expect(JSON.parse(localStorage.getItem("structify.courseware.selected") ?? "null")).toEqual({
        deckId: "ch03-deck02",
        title: "第三章-限定性线性表-02",
        chapter: "03",
        lessonIds: ["lesson-2", "lesson-3"],
      });
    });
    await wrapper.get('[data-deck-id="ch03-deck02"]').trigger("click");
    await flushPromises();

    await wrapper.get(".courseware__select-current .liquid-metal-button__native").trigger("click");

    expect(navigation.push).toHaveBeenCalledExactlyOnceWith("/classroom");
    expect(wrapper.get(".courseware__select-current .liquid-metal-button__native").attributes("aria-pressed")).toBe("true");
    wrapper.unmount();
  });

  it("clears the previous slides while a new deck is loading", async () => {
    const wrapper = await mountView();
    let resolve!: (slides: ReturnType<typeof slide>[]) => void;
    listDeckSlides.mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    await wrapper.get('[data-deck-id="ch03-deck02"]').trigger("click");

    expect(wrapper.text()).toContain("正在载入课件");
    expect(wrapper.find(".courseware__image").exists()).toBe(false);
    expect(wrapper.find(".courseware__select-current").exists()).toBe(false);
    resolve([{ ...slide("new", 1), deckId: "ch03-deck02" }]);
    await flushPromises();

    expect(wrapper.get(".courseware__image").attributes("src")).toBe("/api/v1/presentation/slides/new/image");
    expect(wrapper.find(".courseware__select-current").exists()).toBe(true);
  });

  it("does not let a slow initial request replace a more recently chosen deck", async () => {
    let resolveFirst!: (slides: ReturnType<typeof slide>[]) => void;
    let resolveSecond!: (slides: ReturnType<typeof slide>[]) => void;
    listDeckSlides
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveSecond = resolve; }));
    const wrapper = await mountView();
    await wrapper.get('[data-deck-id="ch03-deck02"]').trigger("click");
    resolveFirst([slide("old", 1)]);
    await flushPromises();

    expect(wrapper.text()).toContain("正在载入课件");
    expect(wrapper.find(".courseware__image").exists()).toBe(false);
    resolveSecond([{ ...slide("latest", 1), deckId: "ch03-deck02", lessonIds: ["lesson-2"] }]);
    await flushPromises();

    expect(wrapper.get(".courseware__image").attributes("src")).toBe("/api/v1/presentation/slides/latest/image");
    await wrapper.get(".courseware__select-current .liquid-metal-button__native").trigger("click");
    expect(JSON.parse(localStorage.getItem("structify.courseware.selected") ?? "null")).toMatchObject({
      deckId: "ch03-deck02", lessonIds: ["lesson-2"],
    });
  });

  it("shows an empty state without chapter or deck entries when no courseware is available", async () => {
    const wrapper = await mountView([]);

    expect(wrapper.findAll(".courseware__chapter-trigger")).toHaveLength(0);
    expect(wrapper.findAll(".courseware__deck-option")).toHaveLength(0);
    expect(wrapper.text()).toContain("暂时没有可浏览的课件");
    expect(listDeckSlides).not.toHaveBeenCalled();
  });

  it("reports a deck that cannot be opened instead of showing a blank stage", async () => {
    listDeckSlides.mockRejectedValue(new Error("boom"));
    const wrapper = await mountView();

    expect(wrapper.text()).toContain("boom");
    expect(wrapper.find(".courseware__image").exists()).toBe(false);
  });

  it("pages with keyboard arrows using the same boundaries as the buttons", async () => {
    const wrapper = await mountView();
    const press = async (key: string) => {
      const event = new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true });
      window.dispatchEvent(event);
      await flushPromises();
      expect(event.defaultPrevented).toBe(true);
    };

    await press("ArrowLeft");
    expect(wrapper.get(".courseware__caption").text()).toBe("第 1 页");
    await press("ArrowRight");
    expect(wrapper.get(".courseware__caption").text()).toBe("第 2 页");
    await press("ArrowRight");
    expect(wrapper.get(".courseware__caption").text()).toBe("第 2 页");
    await press("ArrowLeft");
    expect(wrapper.get(".courseware__caption").text()).toBe("第 1 页");
  });

  it("navigates chapter headings by keyboard without paging the preview", async () => {
    const wrapper = await mountView([
      deck("ch01-deck01", "01", "第一章-绪论01"),
      deck("ch03-deck01", "03", "第三章-限定性线性表-01"),
      deck("ch06-deck01", "06", "第六章-树和二叉树01"),
    ]);
    const triggers = wrapper.findAll(".courseware__chapter-trigger");
    (triggers[0].element as HTMLButtonElement).focus();
    await triggers[0].trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(triggers[1].element);
    await triggers[1].trigger("keydown", { key: "End" });
    expect(document.activeElement).toBe(triggers[2].element);
    await triggers[2].trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(triggers[0].element);
    await triggers[0].trigger("keydown", { key: "ArrowUp" });
    expect(document.activeElement).toBe(triggers[2].element);
    await triggers[2].trigger("keydown", { key: "Home" });
    expect(document.activeElement).toBe(triggers[0].element);
    await triggers[0].trigger("keydown", { key: "ArrowRight" });
    await wrapper.get('[data-deck-id="ch01-deck01"]').trigger("keydown", { key: "ArrowRight" });

    expect(wrapper.get(".courseware__caption").text()).toBe("第 1 页");
    expect(listDeckSlides).toHaveBeenCalledExactlyOnceWith("ch01-deck01");
  });

  it("does not consume paging keys during loading or after leaving the courseware page", async () => {
    let resolve!: (slides: ReturnType<typeof slide>[]) => void;
    listDeckSlides.mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    const wrapper = await mountView();
    const loadingKey = new KeyboardEvent("keydown", { key: "ArrowRight", cancelable: true });
    window.dispatchEvent(loadingKey);
    expect(loadingKey.defaultPrevented).toBe(false);
    resolve([slide("a", 1), slide("b", 2)]);
    await flushPromises();
    expect(wrapper.get(".courseware__caption").text()).toBe("第 1 页");
    wrapper.unmount();
    const afterLeaving = new KeyboardEvent("keydown", { key: "ArrowRight", cancelable: true });
    window.dispatchEvent(afterLeaving);
    expect(afterLeaving.defaultPrevented).toBe(false);
  });
});
