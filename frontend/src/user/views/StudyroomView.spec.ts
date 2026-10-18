import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import StudyroomView from "./StudyroomView.vue";

const { api } = vi.hoisted(() => ({ api: {
  getStudyroom: vi.fn(),
  createStudyroom: vi.fn(),
  followUpStudyroom: vi.fn(),
  updateStudyroomProgress: vi.fn(),
  coursewareImageUrl: vi.fn((id: string) => `/slides/${id}.png`),
  listPresentationDecks: vi.fn(),
  listDeckSlides: vi.fn(),
  listCodeSamples: vi.fn(),
  planAnimation: vi.fn(),
  simulateAnimation: vi.fn(),
  runCode: vi.fn(),
} }));

vi.mock("../outline-runtime", () => ({ outlineApi: {} }));
vi.mock("../runtime", () => ({ userApi: api }));

const room = {
  id: "room-1", chapterId: "03", title: "栈的工作原理", initialQuestion: "栈是什么？", summary: "理解后进先出。",
  currentStepId: "step-1", version: 1, createdAt: "2026-10-11T00:00:00Z", updatedAt: "2026-10-11T00:00:00Z",
  steps: [{ id: "step-1", stepKey: "intro", type: "concept", title: "后进先出", explanation: "栈只允许从栈顶增删元素。", evidenceIds: [], resources: [{ type: "PPT", id: "slide-1", title: "栈的定义", description: "课件摘要", parameters: {}, available: true }], order: 0, supplemental: false, parentStepId: null, createdAt: "2026-10-11T00:00:00Z" }],
  messages: [{ id: "message-1", role: "assistant", content: "栈是一种线性表。", sourceIds: [], stepId: "step-1", createdAt: "2026-10-11T00:00:00Z" }],
};

function renderStudyroom(path = "/studyroom/room-1") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/studyroom", component: StudyroomView }, { path: "/studyroom/:id", component: StudyroomView },
      { path: "/studyroom-demo", component: StudyroomView }, { path: "/studyroom-demo/:id", component: StudyroomView },
    ],
  });
  return router.push(path).then(async () => {
    await router.isReady();
    return { router, wrapper: mount(StudyroomView, { props: { mock: path.startsWith("/studyroom-demo") }, global: { plugins: [router] } }) };
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  api.getStudyroom.mockResolvedValue(structuredClone(room));
  api.updateStudyroomProgress.mockResolvedValue({ currentStepId: "step-1", version: 1 });
  api.runCode.mockResolvedValue({ language: "c", status: "success", stdout: "real sandbox output", stderr: "", durationMs: 1 });
  api.listPresentationDecks.mockResolvedValue([{ deckId: "deck-03", title: "栈与队列", chapter: "03", slideCount: 1, coverSlideId: "cover" }]);
  api.listDeckSlides.mockResolvedValue([{ id: "ppt-slide-03", deckId: "deck-03", deckTitle: "栈与队列", slideNumber: 3, chapter: "03", title: "顺序栈入栈", rawText: "", speakerNotes: "", semanticSummary: "", teachingRole: "", teachingFocus: "", concepts: ["栈"], visualAnchors: [], shouldShow: true, lessonIds: [], imageUrl: "", section: "", role: "", terms: [] }]);
});

describe("Studyroom learning path", () => {
  it("ignores late responses from another classroom", async () => {
    let finish: (value: typeof room) => void = () => {};
    api.getStudyroom.mockImplementation((id: string) => id === 'room-1' ? new Promise(resolve => { finish = resolve; }) : Promise.resolve({ ...structuredClone(room), id: 'room-2', title: '当前课堂' }));
    const { router, wrapper } = await renderStudyroom();
    await router.push('/studyroom/room-2'); await flushPromises();
    finish(structuredClone(room)); await flushPromises();
    expect(wrapper.get('h1').text()).toBe('当前课堂'); wrapper.unmount();
  });
  it("shows only actual materials and keeps dialogue", async () => {
    const { wrapper } = await renderStudyroom(); await flushPromises();
    expect(wrapper.find('.studyroom__resource-tabs').exists()).toBe(false);
    expect(wrapper.find('img.studyroom__slide').attributes('src')).toBe('/slides/slide-1.png');
    expect(wrapper.text()).toContain('栈是一种线性表。');
    expect(wrapper.text()).not.toContain('当前步骤没有'); wrapper.unmount();
  });
  it("renders text-only steps without empty resource placeholders or injected mock resources", async () => {
    const textRoom = structuredClone(room); textRoom.steps[0].resources = [];
    api.getStudyroom.mockResolvedValue(textRoom);
    const { wrapper } = await renderStudyroom('/studyroom-demo/room-1'); await flushPromises();
    expect(wrapper.text()).toContain('栈只允许从栈顶增删元素。');
    expect(wrapper.find('.scene-content').exists()).toBe(false);
    expect(api.listPresentationDecks).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("sends current page context with a follow-up", async () => {
    api.followUpStudyroom.mockResolvedValue(structuredClone(room));
    const { wrapper } = await renderStudyroom(); await flushPromises();
    await wrapper.get('textarea[aria-label="课程问题"]').setValue('能举例吗？');
    await wrapper.get('form').trigger('submit'); await flushPromises();
    expect(api.followUpStudyroom).toHaveBeenCalledWith('room-1', { question: '能举例吗？', context: { stepId: 'step-1', sceneId: 'legacy-0', resourceId: 'slide-1' } }); wrapper.unmount();
  });
  it("groups multiple PPT pages and remembers the selected page across steps", async () => {
    const next = structuredClone(room);
    next.steps[0].resources.push({ ...next.steps[0].resources[0], id: 'slide-2', title: '例子' });
    next.steps.push({ ...next.steps[0], id: 'step-2', stepKey: 'other', title: '下一步', resources: [], order: 1 });
    api.getStudyroom.mockResolvedValue(next);
    const { wrapper } = await renderStudyroom(); await flushPromises();
    await wrapper.get('button[aria-label="下一页"]').trigger('click');
    expect(wrapper.get('img.studyroom__slide').attributes('src')).toBe('/slides/slide-2.png');
    await wrapper.get('.studyroom__step:nth-child(2)').trigger('click'); await flushPromises();
    await wrapper.get('.studyroom__step:nth-child(1)').trigger('click'); await flushPromises();
    expect(wrapper.get('img.studyroom__slide').attributes('src')).toBe('/slides/slide-2.png');
    api.followUpStudyroom.mockResolvedValue(next);
    await wrapper.get('textarea[aria-label="课程问题"]').setValue('这一页怎么理解');
    await wrapper.get('form').trigger('submit'); await flushPromises();
    expect(api.followUpStudyroom).toHaveBeenLastCalledWith('room-1', expect.objectContaining({ context: expect.objectContaining({ resourceId: 'slide-2' }) }));
    wrapper.unmount();
  });
  it("keeps an edited draft when revisiting a step", async () => {
    const next = structuredClone(room);
    next.steps[0].resources = [{ id: 'code', type: 'C_CODE', title: '实现', description: '', parameters: {}, available: true }];
    next.steps.push({ ...next.steps[0], id: 'step-2', stepKey: 'other', title: '下一步', resources: [], order: 1 });
    api.getStudyroom.mockResolvedValue(next);
    api.listCodeSamples.mockResolvedValue({ lessons: [{ samples: [{ id: 'code', code: 'int main() { return 0; }', stdin: '' }] }] });
    const { wrapper } = await renderStudyroom(); await flushPromises();
    await wrapper.get('button[aria-label="打开代码实验"]').trigger('click');
    await wrapper.get('textarea[aria-label="C 代码编辑器"]').setValue('edited draft');
    await wrapper.get('.studyroom__step:nth-child(2)').trigger('click'); await flushPromises();
    await wrapper.get('.studyroom__step:nth-child(1)').trigger('click'); await flushPromises();
    expect((wrapper.get('textarea[aria-label="C 代码编辑器"]').element as HTMLTextAreaElement).value).toBe('edited draft'); wrapper.unmount();
  });
  it("shows retry after an image failure", async () => {
    const { wrapper } = await renderStudyroom(); await flushPromises();
    await wrapper.get('img.studyroom__slide').trigger('error');
    expect(wrapper.text()).toContain('加载失败');
    await wrapper.get('button[aria-label="重试当前页"]').trigger('click');
    expect(wrapper.find('img.studyroom__slide').exists()).toBe(true); wrapper.unmount();
  });
});
