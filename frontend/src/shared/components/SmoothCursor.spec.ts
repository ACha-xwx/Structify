import { mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SmoothCursor from "./SmoothCursor.vue";

let wrapper: VueWrapper | undefined;
let finePointer = true;
let target: HTMLDivElement;

beforeEach(() => {
  target = document.createElement("div");
  document.body.append(target);
  finePointer = true;
  vi.useFakeTimers();
  vi.stubGlobal("matchMedia", vi.fn(() => ({
    get matches() { return finePointer; },
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })));
});

afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  target.remove();
  vi.restoreAllMocks();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

function moveMouse(x: number, y: number, eventTarget: EventTarget = target) {
  eventTarget.dispatchEvent(new MouseEvent("mousemove", { clientX: x, clientY: y, bubbles: true }));
}

describe("SmoothCursor", () => {
  it("follows each mouse position immediately and only resets the glyph scale after idling", () => {
    wrapper = mount(SmoothCursor);
    const cursor = document.querySelector<HTMLDivElement>(".smooth-cursor")!;
    expect(cursor.hidden).toBe(true);

    moveMouse(40, 60);
    expect(cursor.hidden).toBe(false);
    expect(cursor.style.transform).toContain("translate3d(40px, 60px, 0)");
    expect(document.documentElement.classList.contains("structify-custom-cursor")).toBe(true);
    expect(target.classList.contains("structify-native-arrow-hidden")).toBe(true);

    moveMouse(800, 420);
    // No Vue tick or animation frame is needed to reach the new position.
    expect(cursor.style.transform).toContain("translate3d(800px, 420px, 0)");
    const glyph = cursor.querySelector<SVGSVGElement>("svg")!;
    expect(glyph.style.transform).toContain("scale(0.475)");
    vi.advanceTimersByTime(150);
    expect(glyph.style.transform).toContain("scale(0.5)");
    expect(cursor.style.transform).toContain("translate3d(800px, 420px, 0)");
  });

  it("restores the system cursor on blur and unmount and removes its event listeners", () => {
    wrapper = mount(SmoothCursor);
    moveMouse(80, 90);
    window.dispatchEvent(new Event("blur"));
    expect(document.querySelector<HTMLDivElement>(".smooth-cursor")!.hidden).toBe(true);
    expect(document.documentElement.classList.contains("structify-custom-cursor")).toBe(false);
    expect(target.classList.contains("structify-native-arrow-hidden")).toBe(false);

    moveMouse(100, 110);
    wrapper.unmount();
    wrapper = undefined;
    moveMouse(150, 160);
    expect(document.querySelector(".smooth-cursor")).toBeNull();
    expect(document.documentElement.classList.contains("structify-custom-cursor")).toBe(false);
    expect(vi.getTimerCount()).toBe(0);
  });

  it.each(["text", "vertical-text", "pointer", "not-allowed", "grab", "grabbing", "ew-resize", "ns-resize", "crosshair", "wait", "none"])(
    "preserves the native %s cursor and restores the custom arrow when leaving it",
    (nativeCursor) => {
      const specialTarget = document.createElement("div");
      specialTarget.style.cursor = nativeCursor;
      target.append(specialTarget);
      wrapper = mount(SmoothCursor);
      const cursor = document.querySelector<HTMLDivElement>(".smooth-cursor")!;

      moveMouse(40, 60);
      moveMouse(80, 90, specialTarget);
      expect(cursor.hidden).toBe(true);
      expect(target.classList.contains("structify-native-arrow-hidden")).toBe(false);
      expect(specialTarget.classList.contains("structify-native-arrow-hidden")).toBe(false);
      expect(specialTarget.style.cursor).toBe(nativeCursor);

      moveMouse(100, 110);
      expect(cursor.hidden).toBe(false);
      expect(cursor.style.transform).toContain("translate3d(100px, 110px, 0)");
    },
  );

  it.each(["input", "textarea", "select", "a", "contenteditable"])(
    "keeps browser-selected auto cursors for %s and their children",
    (kind) => {
      const nativeTarget = document.createElement(kind === "contenteditable" ? "div" : kind);
      nativeTarget.style.cursor = "auto";
      if (kind === "a") nativeTarget.setAttribute("href", "/");
      if (kind === "contenteditable") nativeTarget.setAttribute("contenteditable", "true");
      target.append(nativeTarget);
      wrapper = mount(SmoothCursor);

      moveMouse(40, 60);
      moveMouse(80, 90, nativeTarget);
      expect(document.querySelector<HTMLDivElement>(".smooth-cursor")!.hidden).toBe(true);
      expect(document.documentElement.classList.contains("structify-custom-cursor")).toBe(false);
      expect(nativeTarget.classList.contains("structify-native-arrow-hidden")).toBe(false);

      if (kind === "a" || kind === "contenteditable") {
        const child = document.createElement("span");
        nativeTarget.append(child);
        moveMouse(40, 60);
        moveMouse(80, 90, child);
        expect(document.querySelector<HTMLDivElement>(".smooth-cursor")!.hidden).toBe(true);
      }
    },
  );

  it("keeps the I-beam over selectable text while using the custom arrow in its padding", () => {
    target.textContent = "Selectable text";
    target.style.cursor = "auto";
    vi.spyOn(document, "createRange").mockReturnValue({
      selectNodeContents: vi.fn(),
      getClientRects: () => [{ left: 10, right: 100, top: 20, bottom: 40 }],
    } as unknown as Range);
    wrapper = mount(SmoothCursor);
    const cursor = document.querySelector<HTMLDivElement>(".smooth-cursor")!;

    moveMouse(150, 60);
    expect(cursor.hidden).toBe(false);
    moveMouse(40, 30);
    expect(cursor.hidden).toBe(true);
    expect(target.classList.contains("structify-native-arrow-hidden")).toBe(false);
    moveMouse(150, 60);
    expect(cursor.hidden).toBe(false);
  });

  it("rechecks cursor changes on the same element without changing its inline styles", () => {
    target.style.cursor = "default";
    wrapper = mount(SmoothCursor);
    const cursor = document.querySelector<HTMLDivElement>(".smooth-cursor")!;

    moveMouse(40, 60);
    expect(cursor.hidden).toBe(false);
    target.style.cursor = "not-allowed";
    moveMouse(80, 90);
    expect(cursor.hidden).toBe(true);
    expect(target.style.cursor).toBe("not-allowed");
    target.style.cursor = "default";
    moveMouse(100, 110);
    expect(cursor.hidden).toBe(false);
  });

  it("keeps the system cursor on devices without a fine pointer", () => {
    finePointer = false;
    wrapper = mount(SmoothCursor);
    moveMouse(40, 60);
    expect(document.querySelector<HTMLDivElement>(".smooth-cursor")!.hidden).toBe(true);
    expect(document.documentElement.classList.contains("structify-custom-cursor")).toBe(false);
  });
});
