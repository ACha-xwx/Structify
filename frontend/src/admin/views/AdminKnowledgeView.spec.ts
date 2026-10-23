import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import AdminKnowledgeView from "./AdminKnowledgeView.vue";

const knowledgeOverview = vi.hoisted(() => vi.fn());
const knowledgeSettings = vi.hoisted(() => vi.fn());
const saveKnowledgeSettings = vi.hoisted(() => vi.fn());
const resetKnowledgeSettings = vi.hoisted(() => vi.fn());
const testKnowledgeSearch = vi.hoisted(() => vi.fn());
const knowledgeChunks = vi.hoisted(() => vi.fn());
const knowledgeChunk = vi.hoisted(() => vi.fn());
const createKnowledgeChunk = vi.hoisted(() => vi.fn());
const updateKnowledgeChunk = vi.hoisted(() => vi.fn());
const deleteKnowledgeChunk = vi.hoisted(() => vi.fn());
const knowledgeChapters = vi.hoisted(() => vi.fn());

vi.mock("../api", () => ({
  adminApi: {
    knowledgeOverview,
    knowledgeSettings,
    saveKnowledgeSettings,
    resetKnowledgeSettings,
    testKnowledgeSearch,
    knowledgeChunks,
    knowledgeChunk,
    createKnowledgeChunk,
    updateKnowledgeChunk,
    deleteKnowledgeChunk,
    knowledgeChapters,
  },
  adminErrorMessage: (failure: unknown, action: string) =>
    `${action}未完成（${(failure as { code?: string }).code ?? "NETWORK_ERROR"}）`,
  formatDate: (value?: string | null) => value ?? "未记录",
}));

const overview = {
  totalChunks: 542,
  byReviewStatus: { VERIFIED: 536, REJECTED: 6 },
  bySource: { textbook: 400, courseware: 136, admin: 6 },
  snapshotChunks: 536,
  settingsCustomized: false,
};

const settings = {
  minimumScore: 4,
  searchLimit: 6,
  titleWeightLong: 5,
  titleWeightShort: 3,
  customized: false,
  defaults: { minimumScore: 4, searchLimit: 6, titleWeightLong: 5, titleWeightShort: 3 },
};

const chapters = [
  { id: "ch-3", chapterNumber: 3, title: "栈和队列", summary: "" },
  { id: "ch-6", chapterNumber: 6, title: "树和二叉树", summary: "" },
];

const row = {
  id: "admin-1",
  chapterId: "ch-3",
  title: "栈的定义",
  sourcePath: "admin/manual/abc",
  pageLabel: "第 45 页",
  reviewStatus: "VERIFIED",
  licenseScope: "CLASSROOM_ONLY",
  retrievable: true,
  importOwned: false,
  contentLength: 42,
  createdAt: "2026-10-23T08:00:00Z",
};

const detail = { ...row, content: "栈是后进先出的线性表。" };

async function mountPage() {
  const wrapper = mount(AdminKnowledgeView);
  await flushPromises();
  return wrapper;
}

beforeEach(() => {
  vi.clearAllMocks();
  knowledgeOverview.mockResolvedValue(overview);
  knowledgeSettings.mockResolvedValue(settings);
  knowledgeChapters.mockResolvedValue(chapters);
  knowledgeChunks.mockResolvedValue({ items: [row], page: 0, size: 20, total: 1 });
  knowledgeChunk.mockResolvedValue(detail);
  window.confirm = () => true;
});

describe("AdminKnowledgeView", () => {
  it("shows the corpus overview and the settings in effect", async () => {
    const wrapper = await mountPage();

    expect(wrapper.text()).toContain("542");
    expect(wrapper.text()).toContain("默认参数");
    expect(wrapper.text()).toContain("教材 400");
    expect(knowledgeChunks).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("栈的定义");
    expect(wrapper.text()).toContain("可检索");
    wrapper.unmount();
  });

  it("saves the matching knobs and says when it took effect", async () => {
    saveKnowledgeSettings.mockResolvedValue({ ...settings, customized: true, minimumScore: 10 });
    const wrapper = await mountPage();

    const scoreInput = wrapper.get('[data-testid="save-settings"]');
    await scoreInput.trigger("click");
    await flushPromises();

    expect(saveKnowledgeSettings).toHaveBeenCalledWith({
      minimumScore: 4,
      searchLimit: 6,
      titleWeightLong: 5,
      titleWeightShort: 3,
    });
    expect(wrapper.text()).toContain("匹配方式已保存");
    wrapper.unmount();
  });

  it("previews a search with the form knobs without saving them", async () => {
    testKnowledgeSearch.mockResolvedValue({
      effectiveSettings: { minimumScore: 4, searchLimit: 6, titleWeightLong: 5, titleWeightShort: 3 },
      hits: [{ chunkId: "admin-1", title: "栈的定义", sourcePath: "admin/manual/abc", pageLabel: "第 45 页", score: 14.5 }],
    });
    const wrapper = await mountPage();

    await wrapper.get('input[placeholder="如：栈的入栈和出栈"]').setValue("栈");
    await wrapper.get('[data-testid="run-test"]').trigger("click");
    await flushPromises();

    expect(testKnowledgeSearch).toHaveBeenCalledWith(
      expect.objectContaining({ query: "栈", audience: "STUDENT", minimumScore: 4 })
    );
    expect(saveKnowledgeSettings).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("14.5");
    wrapper.unmount();
  });

  it("refuses an empty test query without calling the service", async () => {
    const wrapper = await mountPage();

    await wrapper.get('[data-testid="run-test"]').trigger("click");
    await flushPromises();

    expect(testKnowledgeSearch).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("先输入一个检索词");
    wrapper.unmount();
  });

  it("opens a chunk, edits it, and reports whether it is retrievable after the save", async () => {
    updateKnowledgeChunk.mockResolvedValue({ ...detail, title: "栈的定义（修订）", retrievable: true });
    const wrapper = await mountPage();

    await wrapper.findAll("tbody tr")[0].trigger("click");
    await flushPromises();
    expect(knowledgeChunk).toHaveBeenCalledWith("admin-1");
    expect(wrapper.text()).toContain("当前可以被检索");

    await wrapper.get('[data-testid="save-chunk"]').trigger("click");
    await flushPromises();

    expect(updateKnowledgeChunk).toHaveBeenCalledWith("admin-1", expect.objectContaining({ title: "栈的定义" }));
    expect(wrapper.text()).toContain("已保存，这条现在可以被检索到");
    wrapper.unmount();
  });

  it("warns that import-owned edits are overwritten by the next import", async () => {
    knowledgeChunk.mockResolvedValue({ ...detail, id: "tb-1", sourcePath: "textbook/ds/ch3.md", importOwned: true });
    const wrapper = await mountPage();

    await wrapper.findAll("tbody tr")[0].trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("下次导入会覆盖这里的修改");
    wrapper.unmount();
  });

  it("creates a manual chunk and opens it for editing", async () => {
    createKnowledgeChunk.mockResolvedValue(detail);
    const wrapper = await mountPage();

    await wrapper.get('[data-testid="toggle-create"]').trigger("click");
    await flushPromises();
    await wrapper.get('input[placeholder="检索时权重最高的部分，写清主题"]').setValue("队列的定义");
    await wrapper.get("textarea").setValue("队列是先进先出的线性表。");
    await wrapper.get('[data-testid="create-chunk"]').trigger("click");
    await flushPromises();

    expect(createKnowledgeChunk).toHaveBeenCalledWith(
      expect.objectContaining({ title: "队列的定义", reviewStatus: "VERIFIED", licenseScope: "CLASSROOM_ONLY" })
    );
    expect(knowledgeChunk).toHaveBeenCalledWith("admin-1");
    expect(wrapper.text()).toContain("教材导入不会冲掉它");
    wrapper.unmount();
  });

  it("refuses to create a chunk without title and content", async () => {
    const wrapper = await mountPage();

    await wrapper.get('[data-testid="toggle-create"]').trigger("click");
    await flushPromises();
    await wrapper.get('[data-testid="create-chunk"]').trigger("click");
    await flushPromises();

    expect(createKnowledgeChunk).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("标题和正文都要填");
    wrapper.unmount();
  });

  it("deletes the selected chunk after confirmation", async () => {
    const wrapper = await mountPage();
    await wrapper.findAll("tbody tr")[0].trigger("click");
    await flushPromises();

    await wrapper.get('[data-testid="delete-chunk"]').trigger("click");
    await flushPromises();

    expect(deleteKnowledgeChunk).toHaveBeenCalledWith("admin-1");
    expect(wrapper.text()).toContain("已删除");
    wrapper.unmount();
  });

  it("says which read failed instead of showing an empty page", async () => {
    knowledgeOverview.mockRejectedValue({ code: "NETWORK_ERROR" });
    const wrapper = await mountPage();

    expect(wrapper.text()).toContain("读取知识库未完成（NETWORK_ERROR）");
    wrapper.unmount();
  });
});
