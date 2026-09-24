import { describe, expect, it } from "vitest";
import { frameValueText, normalizeFrame, panelRoleLabel } from "./frame";
import type { DsvpState } from "../shared/types/animation";

/**
 * The normalizer is the contract between the engine's 38 frame families and one renderer. These cases
 * pin the shapes the engine actually emits - they were read off real traces, not invented - so a change
 * in the engine that the frontend cannot draw shows up here instead of on a learner's screen.
 */

describe("normalizeFrame", () => {
  it("returns an empty frame for a step the engine did not annotate", () => {
    expect(normalizeFrame(null).panels).toEqual([]);
    expect(normalizeFrame(undefined).kind).toBe("");
  });

  it("draws a flat array panel with its index cursor", () => {
    const state: DsvpState = {
      kind: "sort",
      view: [
        { role: "array", values: [27, 38, 13, 49] },
        { role: "meta", values: [], operation: "quick", low: 0, high: 3, pivotIndex: 2 },
      ],
    };

    const frame = normalizeFrame(state);

    expect(frame.kind).toBe("sort");
    expect(frame.label).toBe("排序");
    expect(frame.panels).toHaveLength(1);
    expect(frame.panels[0].kind).toBe("array");
    expect(frame.panels[0].values).toEqual([27, 38, 13, 49]);
    // pivotIndex is the panel's cursor, and low/high become the outlined range.
    expect(frame.panels[0].focus).toBe(2);
    expect(frame.panels[0].range).toEqual([0, 4]);
    expect(frame.chips.map((chip) => chip.label)).toContain("枢轴位置");
  });

  it("turns bare node labels into drawable tree nodes", () => {
    // The graph family lists nodes as strings; a renderer that only understands objects would draw nothing.
    const state: DsvpState = {
      kind: "graph",
      view: [
        { role: "graph", values: [], nodes: ["A", "B", "C"] as never, edges: [["A", "B", 2], ["A", "C", 5]] },
        { role: "meta", values: [], operation: "dijkstra", start: "A", dist: { A: 0, B: 2, C: null }, current: "B" },
      ],
    };

    const frame = normalizeFrame(state);
    const panel = frame.panels[0];

    expect(panel.kind).toBe("graph");
    expect(panel.nodes).toEqual([
      { id: "A", label: "A" },
      { id: "B", label: "B" },
      { id: "C", label: "C" },
    ]);
    // The renderer needs the structured meta, not just its printed form, to colour the graph.
    expect(frame.raw.dist).toEqual({ A: 0, B: 2, C: null });
    expect(frame.raw.current).toBe("B");
    expect(frame.chips.map((chip) => chip.label)).toEqual(expect.arrayContaining(["距离", "当前", "起点"]));
  });

  it("keeps a B-tree's multi-key nodes identifiable", () => {
    const state: DsvpState = {
      kind: "btree",
      view: [
        {
          role: "tree",
          values: [],
          nodes: [
            { id: 0, label: "30 | 50", keys: [30, 50], depth: 0 } as never,
            { id: 1, label: "10 | 20", keys: [10, 20], depth: 1 } as never,
          ],
          edges: [[0, 1]],
          multiKey: true,
        },
      ],
    };

    const panel = normalizeFrame(state).panels[0];

    expect(panel.kind).toBe("tree");
    expect(panel.multiKey).toBe(true);
    expect(panel.nodes).toHaveLength(2);
  });

  it("draws hash buckets as chains rather than a grid of objects", () => {
    const state: DsvpState = {
      kind: "hash_table",
      view: [
        {
          role: "buckets",
          values: [
            [{ key: "22", val: "" }, { key: "44", val: "" }],
            [],
            [{ key: "69", val: "" }],
          ] as never,
        },
        { role: "meta", values: [], operation: "chaining_insert", key: 69, tableSize: 3 },
      ],
    };

    const panel = normalizeFrame(state).panels[0];

    expect(panel.kind).toBe("matrix");
    expect(panel.variant).toBe("bucket");
    expect(panel.rows).toHaveLength(3);
  });

  it("draws a ragged generalized list without flattening it", () => {
    const state: DsvpState = {
      kind: "generalized_list",
      view: [
        { role: "list", values: ["a", ["b", "c"], ["d", ["e"]]] as never },
        { role: "meta", values: [], operation: "depth", depth: 3 },
      ],
    };

    const panel = normalizeFrame(state).panels[0];

    expect(panel.kind).toBe("array");
    expect(frameValueText(panel.values[2])).toBe("[d, [e]]");
    expect(normalizeFrame(state).chips).toContainEqual({ label: "深度", value: "3" });
  });

  it("points at one cell of a grid when the engine names it, instead of a whole column", () => {
    // The special-matrix mapping marks A[i,j]: the column alone would not say which row the step is on.
    const state: DsvpState = {
      kind: "special_matrix",
      view: [
        { role: "matrix", values: [[1, 0, 0], [2, 3, 0], [4, 5, 6]] },
        { role: "compressed", values: [1, 2, 3, 4, 5, 6], focusIndex: 4 },
        { role: "meta", values: [], n: 3, kind: "lower_triangular", i: 3, j: 2 },
      ],
    };

    const frame = normalizeFrame(state);
    const [matrix, packed] = frame.panels;

    expect(matrix.kind).toBe("matrix");
    expect(matrix.focusCell).toBeNull();
    expect(packed.focus).toBe(4);
    expect(frame.chips).toEqual([
      { label: "阶", value: "3" },
      { label: "类型", value: "lower_triangular" },
      { label: "i", value: "3" },
      { label: "j", value: "2" },
    ]);

    const marked = normalizeFrame({
      kind: "special_matrix",
      view: [{ role: "matrix", values: [[1, 0, 0], [2, 3, 0], [4, 5, 6]], focusCell: [2, 1] }],
    });
    expect(marked.panels[0].focusCell).toEqual([2, 1]);
  });

  it("ignores a malformed cell pointer rather than marking the wrong cell", () => {
    const frame = normalizeFrame({
      kind: "special_matrix",
      view: [{ role: "matrix", values: [[1]], focusCell: ["x", 0] }],
    } as unknown as DsvpState);

    expect(frame.panels[0].focusCell).toBeNull();
  });

  it("renders the legacy stack frame that carries no view at all", () => {
    const state = {
      kind: "stack",
      items: [{ index: 0, value: 2 }, { index: 1, value: 5 }],
      top: 1,
      metadata: { capacity: 10 },
    } as unknown as DsvpState;

    const panel = normalizeFrame(state).panels[0];

    expect(panel.role).toBe("stack");
    expect(panel.values).toEqual([2, 5]);
    expect(panel.focus).toBe(1);
    expect(panel.chips).toEqual([
      { label: "top", value: "1" },
      { label: "容量", value: "10" },
    ]);
  });

  it("renders the legacy merge frame as three tables in one picture", () => {
    const state = {
      kind: "sequential_list_merge",
      left: [1, 3, 5],
      right: [2, 4, 6],
      result: [1],
      i: 1,
      j: 0,
      k: 1,
      selected: { source: "LA", index: 0, value: 1 },
      comparison: "1 ≠ 2",
      metadata: { left_length: 3, right_length: 3 },
    } as unknown as DsvpState;

    const frame = normalizeFrame(state);

    expect(frame.panels.map((panel) => panel.role)).toEqual(["LA", "LB", "LC"]);
    expect(frame.panels[2].values).toEqual([1]);
    expect(frame.chips.map((chip) => chip.label)).toEqual(expect.arrayContaining(["i", "j", "k", "比较"]));
  });

  it("falls back to a labelled list instead of an empty screen for an unknown family", () => {
    const state = { kind: "future_structure", view: [{ role: "mystery", values: [1, 2] }] } as DsvpState;

    const frame = normalizeFrame(state);

    expect(frame.label).toBe("future_structure");
    expect(frame.panels[0].label).toBe("mystery");
    expect(frame.panels[0].values).toEqual([1, 2]);
  });

  it("prints polynomial terms the way the textbook writes them", () => {
    expect(frameValueText({ coef: 3, exp: 4 })).toBe("3x^4");
    expect(frameValueText({ coef: 1, exp: 0 })).toBe("1");
    expect(frameValueText(null)).toBe("∅");
  });

  it("labels known panels in Chinese and leaves the rest recognisable", () => {
    expect(panelRoleLabel("inputA")).toBe("输入缓冲 A");
    expect(panelRoleLabel("cpot")).toBe("cpot 定位");
    expect(panelRoleLabel("something_new")).toBe("something_new");
  });
});
