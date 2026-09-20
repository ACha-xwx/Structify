import { afterEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import AnimationPlayer from "./AnimationPlayer.vue";
import type { AnimationDefinition } from "../shared/types/animation";

const definition: AnimationDefinition = {
  animation: true,
  type: "array",
  title: "快速排序",
  description: "观察枢轴如何就位",
  initial: [49, 38, 13],
  steps: [
    { op: "partition", label: "完成一次划分", note: "枢轴 49 落在位置 2", dsvpState: { kind: "sort", view: [{ role: "array", values: [13, 38, 49] }] } },
    { op: "done", label: "排序完成", note: "[13, 38, 49]", dsvpState: { kind: "sort", view: [{ role: "array", values: [13, 38, 49] }] } },
  ],
};

function buttons(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll("button").map((button) => button.text());
}

function press(wrapper: ReturnType<typeof mount>, key: string) {
  return wrapper.get(".player__viewport").trigger("keydown", { key });
}

afterEach(() => {
  vi.useRealTimers();
});

describe("animation player", () => {
  it("starts on the initial state so the input is visible before the first frame", () => {
    const wrapper = mount(AnimationPlayer, { props: { definition } });

    // Non-compact callers render the title in the header, so the initial state does not repeat it.
    expect(wrapper.get(".player__headline").text()).toBe("初始状态");
    expect(wrapper.get(".player__position").text()).toBe("起点 / 共 2 步");
    // The first frame is reachable: the renderer already shows the engine's initial values.
    expect(wrapper.text()).toContain("49");
    wrapper.unmount();
  });

  it("draws the active step's own frame, not the input, once playback leaves the start", async () => {
    // Regression: the initial frame used to shadow every step frame, so the last step still showed
    // the unsorted input.
    const wrapper = mount(AnimationPlayer, { props: { definition } });
    await wrapper.get(".player__viewport").trigger("keydown", { key: "ArrowRight" });

    expect(wrapper.get(".player__position").text()).toBe("1 / 2");
    const cells = wrapper.findAll(".cell__value").map((cell) => cell.text());
    expect(cells.join(",")).toBe("13,38,49");
    wrapper.unmount();
  });

  it("prefers the engine's own initial frame over the flattened value list", () => {
    // A tree cannot be reconstructed from `initial`, which is a flat list of scalars. The executed trace
    // carries the real frame, so the player must use it when the caller passes the trace along.
    const tree: AnimationDefinition = {
      animation: true,
      type: "tree",
      title: "中序遍历",
      description: "",
      initial: [{ id: 0, label: "A" }] as never,
      steps: [{ op: "visit", label: "访问 A", note: "" }],
    };
    const trace = {
      steps: [
        {
          state: {
            kind: "tree",
            view: [
              { role: "tree", values: [], nodes: [{ id: 0, label: "A" }, { id: 1, label: "B" }], edges: [[0, 1]] },
              { role: "visited", values: [] },
            ],
          },
        },
      ],
    };

    const wrapper = mount(AnimationPlayer, { props: { definition: tree, trace } });

    // The trace frame has two nodes; the flattened `initial` only carried one, so drawing B proves the
    // engine's own frame is what is on screen.
    expect(wrapper.find("svg").exists()).toBe(true);
    expect(wrapper.text()).toContain("已访问");
    expect(wrapper.text()).toContain("B");
    wrapper.unmount();
  });

  it("walks the trace with the step buttons and stops at the ends", async () => {
    const wrapper = mount(AnimationPlayer, { props: { definition } });

    await wrapper.findAll("button").find((button) => button.text() === "下一步")!.trigger("click");
    // The big line shows the step's concrete outcome (note) rather than its category (label).
    expect(wrapper.get(".player__headline").text()).toBe("枢轴 49 落在位置 2");
    expect(wrapper.get(".player__position").text()).toBe("1 / 2");

    await wrapper.findAll("button").find((button) => button.text() === "下一步")!.trigger("click");
    expect(wrapper.get(".player__position").text()).toBe("2 / 2");
    const next = wrapper.findAll("button").find((button) => button.text() === "下一步")!;
    expect(next.attributes("disabled")).toBeDefined();

    await wrapper.findAll("button").find((button) => button.text() === "上一步")!.trigger("click");
    expect(wrapper.get(".player__position").text()).toBe("1 / 2");

    await wrapper.findAll("button").find((button) => button.text() === "回到起点")!.trigger("click");
    expect(wrapper.get(".player__position").text()).toBe("起点 / 共 2 步");
    wrapper.unmount();
  });

  it("supports the arrow keys and space on the canvas", async () => {
    const wrapper = mount(AnimationPlayer, { props: { definition } });

    await press(wrapper, "ArrowRight");
    expect(wrapper.get(".player__position").text()).toBe("1 / 2");
    await press(wrapper, "ArrowRight");
    expect(wrapper.get(".player__position").text()).toBe("2 / 2");
    await press(wrapper, "ArrowLeft");
    expect(wrapper.get(".player__position").text()).toBe("1 / 2");
    await press(wrapper, "Home");
    expect(wrapper.get(".player__position").text()).toBe("起点 / 共 2 步");
    wrapper.unmount();
  });

  it("advances on its own while playing and restarts from the end", async () => {
    vi.useFakeTimers();
    const wrapper = mount(AnimationPlayer, { props: { definition } });

    await wrapper.findAll("button").find((button) => button.text() === "播放")!.trigger("click");
    expect(buttons(wrapper)).toContain("暂停");

    vi.advanceTimersByTime(1_200);
    await wrapper.vm.$nextTick();
    expect(wrapper.get(".player__position").text()).toBe("1 / 2");

    vi.advanceTimersByTime(1_200);
    await wrapper.vm.$nextTick();
    // It stops at the end rather than looping, and the button offers to play again.
    expect(wrapper.get(".player__position").text()).toBe("2 / 2");
    expect(buttons(wrapper)).toContain("播放");

    // Playing from the end replays instead of doing nothing.
    await wrapper.findAll("button").find((button) => button.text() === "播放")!.trigger("click");
    await wrapper.vm.$nextTick();
    expect(wrapper.get(".player__position").text()).toBe("起点 / 共 2 步");
    vi.clearAllTimers();
    wrapper.unmount();
  });

  it("jumps to a position from the scrubber", async () => {
    const wrapper = mount(AnimationPlayer, { props: { definition } });

    await wrapper.get("input[type='range']").setValue("1");
    expect(wrapper.get(".player__position").text()).toBe("2 / 2");
    wrapper.unmount();
  });

  it("shows the caller's placeholder instead of dead controls when there is no trace", () => {
    const wrapper = mount(AnimationPlayer, { props: { definition: null, placeholder: "先选一个演示" } });

    expect(wrapper.text()).toContain("先选一个演示");
    expect(wrapper.find(".player__controls").exists()).toBe(false);
    wrapper.unmount();
  });

  it("hides the headline when the surrounding page already shows the title", () => {
    const wrapper = mount(AnimationPlayer, { props: { definition, compact: true } });

    expect(wrapper.find(".player__title").exists()).toBe(false);
    // The step line stays: it is what changes as the animation runs.
    expect(wrapper.get(".player__headline").text()).toBe("快速排序");
    wrapper.unmount();
  });
});
