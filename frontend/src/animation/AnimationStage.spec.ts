import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import AnimationStage from "./AnimationStage.vue";
import type { DsvpState } from "../shared/types/animation";

/**
 * One renderer for every engine frame. These cases pin the two things a frame could carry and the page
 * could still drop on the floor: the frame's own metadata (which the engine sends as a `meta` row) and
 * the single grid cell a step stands on.
 */

function mountState(state: DsvpState) {
  return mount(AnimationStage, { props: { step: null, state } });
}

describe("AnimationStage", () => {
  it("shows the frame's metadata as chips instead of computing and dropping it", () => {
    const wrapper = mountState({
      kind: "special_matrix",
      view: [
        { role: "matrix", values: [[1, 0], [2, 3]], focusCell: [1, 0] },
        { role: "compressed", values: [1, 2, 3], focusIndex: 2 },
        { role: "meta", values: [], n: 2, formula: "k = (i-1)*i/2 + (j-1)", k: 2 },
      ],
    });

    const meta = wrapper.get(".stage__meta");
    expect(meta.findAll(".chip").map((chip) => chip.text())).toEqual([
      "阶2",
      "公式k = (i-1)*i/2 + (j-1)",
      "k2",
    ]);
  });

  it("marks the one cell the step is on, not the whole column", () => {
    const wrapper = mountState({
      kind: "special_matrix",
      view: [{ role: "matrix", values: [[1, 0, 0], [2, 3, 0], [4, 5, 6]], focusCell: [1, 0] }],
    });

    const marked = wrapper.findAll(".matrix td.cell--focus");
    expect(marked).toHaveLength(1);
    expect(marked[0].text()).toBe("2");
  });

  it("still marks by position for frames that carry no cell pointer", () => {
    // The pointer lives on the `meta` row, which is how every simulator reports front/rear/top/index.
    const wrapper = mountState({
      kind: "sort",
      view: [{ role: "array", values: [9, 1, 5] }, { role: "meta", values: [], index: 1 }],
    });

    expect(wrapper.findAll(".cell--focus")).toHaveLength(1);
    expect(wrapper.get(".cell--focus").text()).toContain("1");
  });

  it("writes each named cursor on the cell it stands on", () => {
    // The engine reports i and j for a compare; without these the cell carried a highlight nobody could name.
    const wrapper = mountState({
      kind: "sort",
      view: [{ role: "array", values: [49, 38, 65, 97] }, { role: "meta", values: [], operation: "bubble", i: 1, j: 3 }],
    });

    const cells = wrapper.findAll(".cell");
    expect(cells[1].findAll(".cursor").map((cursor) => cursor.text())).toEqual(["i"]);
    expect(cells[3].findAll(".cursor").map((cursor) => cursor.text())).toEqual(["j"]);
    // Exactly one cursor is the one the step is operating on.
    expect(wrapper.findAll(".cursor--primary")).toHaveLength(1);
  });

  it("draws a linked list as nodes wired together, not as a row of values", () => {
    const wrapper = mountState({
      kind: "linked_list",
      view: [{ role: "L", values: [10, 20, 30] }, { role: "meta", values: [], operation: "insert", current: 1 }],
    });

    const nodes = wrapper.findAll(".chain__node");
    expect(nodes).toHaveLength(3);
    expect(nodes[0].text()).toContain("head");
    expect(nodes[0].text()).toContain("next");
    expect(nodes[2].text()).toContain("NULL");
    // Two links for three nodes, and the working pointer rides the second one.
    expect(wrapper.findAll(".chain__link")).toHaveLength(2);
    expect(wrapper.findAll(".chain__node--focus")).toHaveLength(1);
    expect(nodes[1].findAll(".cursor").map((cursor) => cursor.text())).toEqual(["当前"]);
  });

  it("states what currently holds above the canvas, and counts the work done so far", () => {
    // 计数器与不变式是"效率直觉"的来源：没有它们，一串格子动来动去说明不了任何事。
    const wrapper = mountState({
      kind: "sort",
      view: [
        { role: "array", values: [13, 27, 49, 65, 38] },
        { role: "meta", values: [], operation: "bubble", i: 3, j: 4, invariant: "下标 4 已经排好", compareCount: 7, swapCount: 3 },
      ],
    });

    expect(wrapper.get(".stage__invariant").text()).toBe("下标 4 已经排好");
    const chips = wrapper.get(".stage__meta").findAll(".chip").map((chip) => chip.text());
    expect(chips).toContain("比较次数7");
    expect(chips).toContain("交换次数3");
    // 不变式只出现在它自己那一行，不再重复成一颗胶囊。
    expect(chips.some((chip) => chip.includes("下标 4"))).toBe(false);
  });

  it("shows no invariant line when the engine did not state one", () => {
    const wrapper = mountState({
      kind: "stack",
      view: [{ role: "items", values: [10, 20] }, { role: "meta", values: [], top: 1 }],
    });

    expect(wrapper.find(".stage__invariant").exists()).toBe(false);
  });
});
