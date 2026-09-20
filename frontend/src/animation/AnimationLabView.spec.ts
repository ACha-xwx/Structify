import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouterLinkStub, flushPromises, mount } from "@vue/test-utils";
import AnimationLabView from "./AnimationLabView.vue";
import type { DsvpRequest, DsvpSimulationResponse, DsvpStructure } from "../shared/types/animation";

const api = vi.hoisted(() => ({
  listChapters: vi.fn(),
  planAnimation: vi.fn(),
  simulateAnimation: vi.fn(),
  interpretAnimation: vi.fn(),
  saveObservation: vi.fn(),
}));

vi.mock("../user/runtime", () => ({ userApi: api }));
vi.mock("vue-router", () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}));

/** The lab stands on the shared stage; its brand link is a router link, stubbed for these mounts. */
function mountLab() {
  return mount(AnimationLabView, { global: { stubs: { RouterLink: RouterLinkStub } } });
}

/** A minimal but real-shaped simulation response: one frame with an engine snapshot. */
function simulation(structure: DsvpStructure = "sort"): DsvpSimulationResponse {
  return {
    protocol: "dsvp/1.0",
    request: { version: "1.0", structure, operation: "quick", params: {}, initial_state: { data: [] } },
    trace: { steps: [] },
    animationData: {
      animation: true,
      type: "array",
      title: "快速排序",
      description: "一趟划分",
      initial: [49, 38],
      steps: [
        { op: "partition", label: "完成一次划分", note: "枢轴 49 落在位置 1", dsvpState: { kind: "sort", view: [{ role: "array", values: [38, 49] }] } },
      ],
    },
    evidencePersisted: false,
    matchSource: "NONE",
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  api.listChapters.mockResolvedValue([
    { id: "09-internal-sort", chapterNumber: 9, title: "内部排序", summary: "" },
    { id: "06-tree", chapterNumber: 6, title: "树与二叉树", summary: "" },
  ]);
  api.simulateAnimation.mockResolvedValue(simulation());
});

describe("animation lab", () => {
  it("offers the capabilities of the selected chapter and runs the chosen one deterministically", async () => {
    api.planAnimation.mockResolvedValue({
      status: "ready",
      capability: null,
      missingArguments: [],
      toolRequest: {
        kind: "animation",
        protocol: "dsvp/1",
        capability: "sort.quick",
        purpose: "",
        confidence: 1,
        sourceChunkIds: [],
        demoFallback: false,
        request: { version: "1.0", structure: "sort", operation: "quick", params: {}, initial_state: { data: [] } },
      },
    });

    const wrapper = mountLab();
    await flushPromises();

    // The first chapter is selected on mount, so the structure list must already be chapter 9's.
    const structureSelect = wrapper.findAll("select")[1];
    const structureLabels = structureSelect.findAll("option").map((option) => option.text());
    expect(structureLabels).toContain("排序");
    expect(structureLabels).not.toContain("树与二叉树");

    await wrapper.findAll("button").find((button) => button.text() === "生成动画")!.trigger("click");
    await flushPromises();

    expect(api.planAnimation).toHaveBeenCalledTimes(1);
    const planInput = api.planAnimation.mock.calls[0][0];
    expect(planInput.capability).toBeTypeOf("string");
    // A chosen operation must go through the engine, never through the model.
    expect(api.interpretAnimation).not.toHaveBeenCalled();
    expect(api.simulateAnimation).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("快速排序");
    wrapper.unmount();
  });

  it("says when the engine had to use its own teaching example instead of textbook numbers", async () => {
    api.planAnimation.mockResolvedValue({
      status: "ready",
      capability: null,
      missingArguments: [],
      toolRequest: {
        kind: "animation",
        protocol: "dsvp/1",
        capability: "sort.quick",
        purpose: "",
        confidence: 1,
        sourceChunkIds: [],
        demoFallback: true,
        request: { version: "1.0", structure: "sort", operation: "quick", params: {}, initial_state: { data: [] } },
      },
    });

    const wrapper = mountLab();
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text() === "生成动画")!.trigger("click");
    await flushPromises();

    expect(wrapper.get(".panel__flag").text()).toContain("不是教材原例");
    wrapper.unmount();
  });

  it("reports the arguments the engine says are missing instead of inventing them", async () => {
    api.planAnimation.mockResolvedValue({
      status: "missing-arguments",
      capability: null,
      missingArguments: ["initialData", "key"],
      toolRequest: null,
    });

    const wrapper = mountLab();
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text() === "生成动画")!.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("还缺参数：initialData、key");
    expect(api.simulateAnimation).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("routes a sentence through the model but still computes the frames locally", async () => {
    const request: DsvpRequest = {
      version: "1.0",
      structure: "sort",
      operation: "quick",
      params: {},
      initial_state: { data: [] },
    };
    api.interpretAnimation.mockResolvedValue(request);

    const wrapper = mountLab();
    await flushPromises();
    await wrapper.get("textarea").setValue("看看快速排序一趟怎么划分");
    await wrapper.findAll("button").find((button) => button.text() === "让模型选一个演示")!.trigger("click");
    await flushPromises();

    expect(api.interpretAnimation).toHaveBeenCalledWith({ chapterId: "09-internal-sort", prompt: "看看快速排序一趟怎么划分" });
    expect(api.simulateAnimation).toHaveBeenCalledWith(request);
    // The big title names what the model picked; no small-print status line anymore.
    expect(wrapper.get(".player__title").text()).toBe("快速排序");
    expect(wrapper.text()).not.toContain("模型选择的能力");
    wrapper.unmount();
  });

  it("keeps the generated animation's title when the dropdown changes afterwards", async () => {
    api.planAnimation.mockResolvedValue({
      status: "ready",
      capability: null,
      missingArguments: [],
      toolRequest: {
        kind: "animation",
        protocol: "dsvp/1",
        capability: "sort.quick",
        purpose: "",
        confidence: 1,
        sourceChunkIds: [],
        demoFallback: false,
        request: { version: "1.0", structure: "sort", operation: "quick", params: {}, initial_state: { data: [] } },
      },
    });

    const wrapper = mountLab();
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text() === "生成动画")!.trigger("click");
    await flushPromises();
    const titleBefore = wrapper.get(".player__title").text();

    // Switching the operation must not retitle the animation still on screen.
    const capabilitySelect = wrapper.findAll("select")[2];
    const otherOption = capabilitySelect.findAll("option").find((option) => option.text() !== titleBefore);
    if (otherOption) {
      await capabilitySelect.setValue(otherOption.element.value);
      expect(wrapper.get(".player__title").text()).toBe(titleBefore);
    }
    wrapper.unmount();
  });

  it("surfaces a refusal rather than showing an unrelated animation", async () => {
    api.interpretAnimation.mockRejectedValue(new Error("这个问题不需要动画演示"));

    const wrapper = mountLab();
    await flushPromises();
    await wrapper.get("textarea").setValue("什么是排序？");
    await wrapper.findAll("button").find((button) => button.text() === "让模型选一个演示")!.trigger("click");
    await flushPromises();

    expect(wrapper.get("[role='alert']").text()).toBe("这个问题不需要动画演示");
    expect(api.simulateAnimation).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
