import { describe, expect, it } from "vitest";
import { attachMockResources, getMockAnimation, getMockCodeSamples, mockPptImageUrl } from "./studyroom-mock";
import { normalizeFrame } from "../animation/frame";
import type { StudyroomView } from "../shared/types/studyroom";

function makeRoom(stepCount = 3): StudyroomView {
  const steps = Array.from({ length: stepCount }, (_, index) => ({
    id: `step-${index}`, stepKey: `step-${index}`, type: "explanation", title: `步骤 ${index + 1}`,
    explanation: `模型生成的讲解 ${index + 1}`, evidenceIds: [], resources: [], order: index,
    supplemental: false, parentStepId: null, createdAt: "2026-10-11T00:00:00Z",
  }));
  return {
    id: "live-room", chapterId: null, title: "真实模型课堂", initialQuestion: "真实问题", summary: "真实模型摘要",
    currentStepId: "step-0", version: 1, createdAt: "2026-10-11T00:00:00Z", updatedAt: "2026-10-11T00:00:00Z",
    steps,
    messages: [{ id: "ai-1", role: "assistant", content: "真实模型的回答。", sourceIds: [], stepId: "step-0", createdAt: "2026-10-11T00:00:00Z" }],
  };
}

describe("Studyroom mock resource catalog", () => {
  it("matches a null-chapter classroom to real text in a later deck using its evidence", async () => {
    const original = makeRoom(1);
    original.steps[0].title = "先序遍历";
    original.steps[0].evidenceIds = ["studyroom-dev-mock-06-tree"];
    const slide = { id: "tree-real", deckId: "tree-second", deckTitle: "树和二叉树02", slideNumber: 15, chapter: "06", title: "遍历方法", rawText: "先序遍历：根、左、右", speakerNotes: "", semanticSummary: "", teachingRole: "", teachingFocus: "", concepts: [], visualAnchors: [], shouldShow: true, lessonIds: [], imageUrl: "", section: "", role: "", terms: [] };
    const decorated = await attachMockResources(original, {
      listPresentationDecks: async () => [
        { deckId: "tree-first", chapter: "06", title: "树01", slideCount: 1, coverSlideId: "first" },
        { deckId: "tree-second", chapter: "06", title: "树02", slideCount: 1, coverSlideId: "tree-real" },
      ],
      listDeckSlides: async (deckId) => deckId === "tree-second" ? [slide] : [],
      coursewareImageUrl: (id) => `/api/slides/${id}/image`,
    });
    expect(decorated.steps[0].resources.find((resource) => resource.type === "PPT")?.id).toBe("tree-real");
    expect(decorated.steps[0].resources.find((resource) => resource.type === "ANIMATION")?.id).toBe("mock-animation-06-tree");
    expect(decorated.chapterId).toBeNull();
  });

  it("adds chapter-matched resources without changing generated content", async () => {
    const original = makeRoom();
    original.chapterId = "03-stack-queue";
    original.steps[0].title = "栈的入栈操作";
    const decorated = await attachMockResources(original, {
      listPresentationDecks: async () => [{ deckId: "deck-03", chapter: "03", title: "栈与队列", slideCount: 1, coverSlideId: "cover" }],
      listDeckSlides: async () => [{ id: "slide-stack-real", deckId: "deck-03", deckTitle: "栈与队列", slideNumber: 2, chapter: "03", title: "栈", rawText: "", speakerNotes: "", semanticSummary: "", teachingRole: "", teachingFocus: "", concepts: [], visualAnchors: [], shouldShow: true, lessonIds: [], imageUrl: "", section: "", role: "", terms: [] }],
      coursewareImageUrl: (id: string) => `/api/slides/${id}/image`,
    });

    expect(decorated.id).toBe(original.id);
    expect(decorated.title).toBe(original.title);
    expect(decorated.messages).toEqual(original.messages);
    expect(decorated.steps.map((step) => step.explanation)).toEqual(original.steps.map((step) => step.explanation));
    expect(decorated.steps[0].resources.find((resource) => resource.type === "PPT")?.id).toBe("slide-stack-real");
    expect(decorated.steps[0].resources.find((resource) => resource.type === "PPT")?.parameters).toEqual(expect.objectContaining({ imageUrl: "/api/slides/slide-stack-real/image", originalResource: true }));
    expect(decorated.steps[0].resources.some((resource) => resource.type === "C_CODE")).toBe(true);
    expect(original.steps.every((step) => step.resources.length === 0)).toBe(true);
  });

  it.each(["04-string", "10-external-sort"])("leaves chapter %s without a mock PPT", async (chapterId) => {
    const room = makeRoom(1);
    room.chapterId = chapterId;
    room.steps[0].title = chapterId.startsWith("04") ? "串匹配" : "外部排序";
    const decorated = await attachMockResources(room, emptyPresentationApi);

    expect(decorated.steps[0].resources.some((resource) => resource.type === "PPT")).toBe(false);
  });

  it("does not attach unrelated stack examples to another topic", async () => {
    const room = makeRoom(1);
    room.chapterId = "06-tree";
    room.steps[0].title = "哈夫曼编码";
    const decorated = await attachMockResources(room, emptyPresentationApi);

    expect(decorated.steps[0].resources.some((resource) => resource.type === "ANIMATION" && (resource.parameters as { animationType?: string }).animationType === "stack")).toBe(false);
    expect(decorated.steps[0].resources.some((resource) => resource.type === "C_CODE" && resource.title.includes("栈"))).toBe(false);
  });

  it("does not attach a hash animation to a binary-search step", async () => {
    const room = makeRoom(1);
    room.chapterId = "08-search";
    room.steps[0].title = "二分查找";
    const decorated = await attachMockResources(room, emptyPresentationApi);

    expect(decorated.steps[0].resources.some((resource) => resource.type === "ANIMATION")).toBe(false);
    expect(decorated.steps[0].resources.some((resource) => resource.type === "C_CODE" && resource.title.includes("二分查找"))).toBe(true);
  });

  it("keeps the room usable when deck or slide loading fails", async () => {
    const room = makeRoom(1);
    room.chapterId = "06-tree";
    room.steps[0].title = "二叉树遍历";
    const deckFailure = await attachMockResources(room, {
      ...emptyPresentationApi,
      listPresentationDecks: async () => { throw new Error("catalog unavailable"); },
    });
    const slideFailure = await attachMockResources(room, {
      ...emptyPresentationApi,
      listPresentationDecks: async () => [{ deckId: "deck-06", title: "树", chapter: "06", slideCount: 1, coverSlideId: "cover" }],
      listDeckSlides: async () => { throw new Error("slides unavailable"); },
    });

    expect(deckFailure.steps[0].resources.some((resource) => resource.type === "PPT")).toBe(false);
    expect(slideFailure.steps[0].resources.some((resource) => resource.type === "PPT")).toBe(false);
  });

  it("does not attach an unrelated chapter slide when no slide matches the step", async () => {
    const room = makeRoom(1);
    room.chapterId = "03-stack-queue";
    room.steps[0].title = "队列的循环存储";
    room.steps[0].explanation = "队列的队首和队尾指针";
    const decorated = await attachMockResources(room, {
      listPresentationDecks: async () => [{ deckId: "deck-03", chapter: "03", title: "栈与队列", slideCount: 1, coverSlideId: "cover" }],
      listDeckSlides: async () => [{ id: "slide-stack", deckId: "deck-03", deckTitle: "栈与队列", slideNumber: 2, chapter: "03", title: "顺序栈入栈", rawText: "后进先出", speakerNotes: "", semanticSummary: "栈顶操作", teachingRole: "", teachingFocus: "", concepts: ["栈"], visualAnchors: [], shouldShow: true, lessonIds: [], imageUrl: "", section: "", role: "", terms: [] }],
      coursewareImageUrl: (id: string) => `/api/slides/${id}/image`,
    });

    expect(decorated.steps[0].resources.some((resource) => resource.type === "PPT")).toBe(false);
  });

  it("keeps existing API resources and avoids mutating their objects", async () => {
    const room = makeRoom(1);
    room.chapterId = "03-stack-queue";
    room.steps[0].title = "栈入栈";
    room.steps[0].resources.push({ type: "C_CODE", id: "api-sample", title: "API 栈代码", description: "", parameters: {}, available: true });
    const before = structuredClone(room);
    const decorated = await attachMockResources(room, emptyPresentationApi);

    expect(decorated.steps[0].resources.find((resource) => resource.type === "C_CODE")?.id).toBe("api-sample");
    expect(room).toEqual(before);
  });

  it.each([
    ["03-stack-queue", "出栈操作"], ["06-tree", "中序遍历"], ["09-internal-sort", "快速排序"],
  ])("does not replace %s %s with a different algorithm fixture", async (chapterId, title) => {
    const original = makeRoom(1);
    original.chapterId = chapterId;
    original.steps[0].title = title;
    original.steps[0].explanation = title;
    const decorated = await attachMockResources(original, emptyPresentationApi);
    expect(decorated.steps[0].resources).toEqual([]);
  });

  it("provides chapter keyed C examples and only supported animation types", async () => {
    const samples = await getMockCodeSamples();
    expect(new Set(samples.lessons.map((lesson) => lesson.chapterId))).toEqual(new Set([
      "01-introduction", "02-linear-list", "03-stack-queue", "04-string", "05-array-generalized-list", "06-tree", "07-graph", "08-search", "09-internal-sort", "10-external-sort",
    ]));
    for (const chapterId of ["01-introduction", "02-linear-list", "03-stack-queue", "04-string", "05-array-generalized-list", "06-tree", "07-graph", "08-search", "09-internal-sort", "10-external-sort"]) {
      expect(samples.lessons.find((lesson) => lesson.chapterId === chapterId)?.samples[0].code.length).toBeGreaterThan(0);
    }
    expect(getMockAnimation("mock-animation-03-stack")?.animationData.type).toBe("stack");
  });

  it.each([
    ["02-list", "list"], ["03-stack", "stack"], ["05-array", "array"], ["06-tree", "tree"], ["08-hash", "hash"], ["09-array", "array"],
  ])("renders nonempty %s frames for %s animation", (fixture, type) => {
    const simulation = getMockAnimation(`mock-animation-${fixture}`);
    expect(simulation).not.toBeNull();
    expect(simulation!.animationData.type).toBe(type);
    const step = simulation!.animationData.steps[0];
    const frame = normalizeFrame(step.dsvpState);
    expect(frame.panels.length).toBeGreaterThan(0);
    expect(frame.panels.some((panel) => panel.values.length > 0 || panel.nodes.length > 0 || panel.edges.length > 0)).toBe(true);
  });

  it("does not fabricate PPT image URLs", () => {
    expect(mockPptImageUrl("slide-stack-real")).toBeNull();
  });
});

const emptyPresentationApi = {
  listPresentationDecks: async () => [],
  listDeckSlides: async () => [],
  coursewareImageUrl: (id: string) => `/api/slides/${id}/image`,
};
