import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouterLinkStub, mount } from "@vue/test-utils";
import { nextTick } from "vue";
import CoursewareView from "./CoursewareView.vue";

const listPresentationDecks = vi.fn();
const listDeckSlides = vi.fn();

vi.mock("../../user/runtime", () => ({
  userApi: {
    listPresentationDecks: (...args: unknown[]) => listPresentationDecks(...args),
    listDeckSlides: (...args: unknown[]) => listDeckSlides(...args),
  },
}));

beforeEach(() => {
  listPresentationDecks.mockReset();
  listDeckSlides.mockReset();
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
    lessonIds: [],
    imageUrl: `/api/v1/presentation/slides/${id}/image`,
    section: "3.1",
    role: "定义",
    terms: [],
  };
}

async function mountView() {
  listPresentationDecks.mockResolvedValue([deck("ch03-deck01", "03", "第三章-限定性线性表-01"), deck("ch03-deck02", "03", "第三章-限定性线性表-02")]);
  listDeckSlides.mockResolvedValue([slide("a", 1), slide("b", 2)]);
  const wrapper = mount(CoursewareView, { global: { stubs: { RouterLink: RouterLinkStub } } });
  for (let round = 0; round < 4; round++) {
    await Promise.resolve();
    await nextTick();
  }
  return wrapper;
}

describe("courseware browser", () => {
  it("lists every deck as a single quiet line and opens the first one", async () => {
    const wrapper = await mountView();

    const decks = wrapper.findAll(".courseware__deck");
    expect(decks.length).toBe(2);
    expect(decks[0].text()).toBe("第三章-限定性线性表-01");
    // The chapter/slide count is metadata: it belongs in the tooltip, not in a second small line.
    expect(decks[0].attributes("title")).toContain("第 03 章");
    expect(wrapper.find(".courseware__deckMeta").exists()).toBe(false);
    expect(wrapper.get("img").attributes("src")).toBe("/api/v1/presentation/slides/a/image");
  });

  it("keeps one heading row and one chip row, and pages with them", async () => {
    const wrapper = await mountView();

    expect(wrapper.get(".courseware__caption").text()).toBe("第 1 页");
    expect(wrapper.get(".courseware__position").text()).toBe("1/2");

    const buttons = wrapper.findAll(".courseware__button");
    expect(buttons.map((button) => button.text())).toEqual(["上一页", "下一页"]);
    await buttons[1].trigger("click");

    expect(wrapper.get(".courseware__position").text()).toBe("2/2");
    expect(wrapper.get(".courseware__caption").text()).toBe("第 2 页");
  });

  it("reports a deck that cannot be opened instead of showing a blank stage", async () => {
    listPresentationDecks.mockResolvedValue([deck("ch03-deck01", "03", "第三章-限定性线性表-01")]);
    listDeckSlides.mockRejectedValue(new Error("boom"));
    const wrapper = mount(CoursewareView, { global: { stubs: { RouterLink: RouterLinkStub } } });
    for (let round = 0; round < 4; round++) {
      await Promise.resolve();
      await nextTick();
    }

    expect(wrapper.text()).toContain("boom");
    expect(wrapper.find("img").exists()).toBe(false);
  });
});
