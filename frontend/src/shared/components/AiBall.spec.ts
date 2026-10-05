import { afterEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import AiBall from "./AiBall.vue";

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

function mockFrame() {
  let paint: FrameRequestCallback | undefined;
  const request = vi.spyOn(window, "requestAnimationFrame").mockImplementation((callback) => {
    paint = callback;
    return 17;
  });
  const cancel = vi.spyOn(window, "cancelAnimationFrame").mockImplementation(() => {});
  return { request, cancel, paint: () => { const callback = paint; paint = undefined; callback?.(16); } };
}

function move(x: number, y: number, type = "mouse") {
  const event = new MouseEvent("pointermove", { clientX: x, clientY: y });
  Object.defineProperty(event, "pointerType", { value: type });
  window.dispatchEvent(event);
}

describe("AI ball", () => {
  it("rotates on click, extends a repeated click, and clears its spin timer on unmount", async () => {
    vi.useFakeTimers();
    const view = mount(AiBall, { props: { followPointer: false } });
    expect(view.findAll(".ai-ball__eye")).toHaveLength(2);
    expect(view.find(".ai-ball__stage").exists()).toBe(true);
    expect(view.findAll(".ai-ball__orbit")).toHaveLength(2);
    expect(view.get("button").attributes("type")).toBe("button");
    await view.trigger("click");
    expect(view.attributes("data-spinning")).toBe("true");
    expect(view.attributes("data-awake")).toBe("true");
    expect(view.attributes("data-spin-mode")).toBe("orbit");
    vi.advanceTimersByTime(600);
    await view.trigger("click");
    vi.advanceTimersByTime(700);
    await view.vm.$nextTick();
    expect(view.attributes("data-spinning")).toBe("true");
    vi.advanceTimersByTime(200);
    await view.vm.$nextTick();
    expect(view.attributes("data-spinning")).toBe("false");
    await view.trigger("click");
    view.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("coalesces pointer updates into one frame and bounds the gaze in every direction", () => {
    const frames = mockFrame();
    const view = mount(AiBall);
    vi.spyOn(view.element, "getBoundingClientRect").mockReturnValue({ left: 100, top: 100, width: 40, height: 40 } as DOMRect);
    move(10000, 120);
    move(120, -10000);
    expect(frames.request).toHaveBeenCalledTimes(1);
    const style = (view.element as HTMLElement).style;
    expect(style.getPropertyValue("--ai-gaze-y")).toBe("");
    frames.paint();
    expect(style.getPropertyValue("--ai-gaze-x")).toBe("0.00px");
    expect(style.getPropertyValue("--ai-gaze-y")).toBe("-4.00px");
    move(-10000, 120);
    frames.paint();
    expect(style.getPropertyValue("--ai-gaze-x")).toBe("-4.00px");
    move(10000, 10000);
    frames.paint();
    const x = parseFloat(style.getPropertyValue("--ai-gaze-x"));
    const y = parseFloat(style.getPropertyValue("--ai-gaze-y"));
    expect(x).toBeGreaterThan(0);
    expect(y).toBeGreaterThan(0);
    expect(Math.hypot(x, y)).toBeLessThanOrEqual(4.01);
    view.unmount();
  });

  it("gives a conversation full of message mascots no pointer listeners or JS animation timers", () => {
    vi.useFakeTimers();
    const add = vi.spyOn(window, "addEventListener");
    const frames = mockFrame();
    const views = Array.from({ length: 40 }, () => mount(AiBall, { props: { followPointer: false, size: "message" } }));
    expect(add.mock.calls.filter(([name]) => name === "pointermove" || name === "pointerout" || name === "blur")).toHaveLength(0);
    move(900, 900);
    expect(frames.request).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
    views.forEach((view) => view.unmount());
  });

  it("cycles expressive message mascots through a different random state every 6.5 seconds", async () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const view = mount(AiBall, { props: { followPointer: false, expressive: true, size: "message" } });
    expect(view.attributes("data-expressive")).toBe("true");
    expect(view.attributes("data-expression")).toBe("idle");
    expect(vi.getTimerCount()).toBe(1);

    vi.advanceTimersByTime(6500);
    await view.vm.$nextTick();
    const first = view.attributes("data-expression");
    expect(first).toBe("curious");
    expect(vi.getTimerCount()).toBe(2);

    vi.advanceTimersByTime(1000);
    await view.vm.$nextTick();
    expect(view.attributes("data-expression")).toBe("idle");
    vi.advanceTimersByTime(5500);
    await view.vm.$nextTick();
    expect(view.attributes("data-expression")).not.toBe(first);
    view.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("keeps click space, expression motion, and the ball shape on separate layers", async () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const view = mount(AiBall, { props: { followPointer: false, expressive: true } });
    const stage = view.find(".ai-ball__stage");
    const motion = view.find(".ai-ball__motion");
    const shape = view.find(".ai-ball__shape");
    expect(stage.find(".ai-ball__orbit--back").exists()).toBe(true);
    expect(stage.find(".ai-ball__orbit--front").exists()).toBe(true);
    expect(motion.find(".ai-ball__shape").element).toBe(shape.element);

    await view.trigger("click");
    expect(view.attributes("data-spin-mode")).toBe("orbit");
    expect(view.find(".ai-ball__motion").exists()).toBe(true);
    expect(view.find(".ai-ball__shape").exists()).toBe(true);
  });

  it("pauses pointer gaze while an expressive animation is active", async () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const frames = mockFrame();
    const view = mount(AiBall, { props: { expressive: true } });
    vi.spyOn(view.element, "getBoundingClientRect").mockReturnValue({ left: 0, top: 0, width: 40, height: 40 } as DOMRect);

    vi.advanceTimersByTime(6500);
    await view.vm.$nextTick();
    move(900, 900);
    frames.paint();
    expect((view.element as HTMLElement).style.getPropertyValue("--ai-gaze-x")).toBe("");

    vi.advanceTimersByTime(1000);
    await view.vm.$nextTick();
    move(900, 900);
    frames.paint();
    expect((view.element as HTMLElement).style.getPropertyValue("--ai-gaze-x")).not.toBe("");
    view.unmount();
  });

  it("does not start the expression tour when reduced motion is requested", () => {
    vi.useFakeTimers();
    vi.spyOn(window, "matchMedia").mockImplementation((query) => ({
      matches: query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    } as MediaQueryList));
    const view = mount(AiBall, { props: { followPointer: false, expressive: true } });
    expect(view.attributes("data-expression")).toBe("idle");
    expect(vi.getTimerCount()).toBe(0);
    view.unmount();
  });

  it("resets after leaving the window, ignores touch drags, and removes listeners and queued frames", () => {
    const frames = mockFrame();
    const remove = vi.spyOn(window, "removeEventListener");
    const view = mount(AiBall);
    vi.spyOn(view.element, "getBoundingClientRect").mockReturnValue({ left: 0, top: 0, width: 40, height: 40 } as DOMRect);
    move(90, 90, "touch");
    expect(frames.request).not.toHaveBeenCalled();
    move(90, 90);
    frames.paint();
    window.dispatchEvent(new MouseEvent("pointerout", { relatedTarget: null }));
    expect((view.element as HTMLElement).style.getPropertyValue("--ai-gaze-x")).toBe("");
    move(90, 90);
    view.unmount();
    expect(frames.cancel).toHaveBeenCalledWith(17);
    expect(remove.mock.calls.map(([name]) => name)).toEqual(expect.arrayContaining(["pointermove", "pointerout", "blur"]));
    const requested = frames.request.mock.calls.length;
    move(30, 30);
    expect(frames.request).toHaveBeenCalledTimes(requested);
  });

  it("disables and re-enables gaze tracking when the prop changes", async () => {
    const frames = mockFrame();
    const view = mount(AiBall);
    move(90, 90);
    await view.setProps({ followPointer: false });
    expect(frames.cancel).toHaveBeenCalledWith(17);
    move(90, 90);
    expect(frames.request).toHaveBeenCalledTimes(1);
    await view.setProps({ followPointer: true });
    move(90, 90);
    expect(frames.request).toHaveBeenCalledTimes(2);
    view.unmount();
  });
});
