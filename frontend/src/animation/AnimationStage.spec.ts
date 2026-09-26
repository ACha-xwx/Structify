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
});
