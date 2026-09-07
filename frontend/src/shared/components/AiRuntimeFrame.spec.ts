import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import AiRuntimeFrame from "./AiRuntimeFrame.vue";
import { setLocale } from "../i18n/locale";

function installMatchMedia(matches = false) {
  const listener = vi.fn();
  const matchMedia = vi.fn(() => ({
    matches,
    media: "",
    onchange: null,
    addEventListener: listener,
    removeEventListener: listener,
    addListener: listener,
    removeListener: listener,
    dispatchEvent: () => false,
  }));
  vi.stubGlobal("matchMedia", matchMedia);
  Object.defineProperty(window, "matchMedia", { configurable: true, writable: true, value: matchMedia });
}

async function settle() {
  await nextTick();
  await nextTick();
}

beforeEach(() => {
  installMatchMedia();
  setLocale("zh-CN");
  vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue();
  vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => undefined);
});

afterEach(() => {
  document.body.replaceChildren();
  document.body.className = "";
  document.body.style.overflow = "";
  document.documentElement.style.overflow = "";
  Reflect.deleteProperty(window as unknown as Record<string, unknown>, "matchMedia");
  vi.unstubAllGlobals();
  setLocale("zh-CN");
});

describe("AiRuntimeFrame", () => {
  it("uses the supplied AI Runtime video as a decorative full-bleed layer", () => {
    const wrapper = mount(AiRuntimeFrame);
    const video = wrapper.get("video");

    expect((video.element as HTMLVideoElement).muted).toBe(true);
    expect((video.element as HTMLVideoElement).playsInline).toBe(true);
    expect(video.find("source").attributes("src")).toBe("https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260809_012548_ef22562c-c0ae-4816-ad9d-f8922af4e6a7.mp4");
    wrapper.unmount();
  });

  it("keeps a supplied poster available while media is pending", () => {
    const wrapper = mount(AiRuntimeFrame, { props: { videoPoster: "/assets/runtime-poster.webp" } });
    const video = wrapper.get("video");

    expect(video.attributes("poster")).toBe("/assets/runtime-poster.webp");
    expect(video.attributes("preload")).toBe("auto");
    wrapper.unmount();
  });

  it("localizes its chrome labels without changing the Chinese defaults", async () => {
    const wrapper = mount(AiRuntimeFrame, { attachTo: document.body });
    const menuToggle = wrapper.get(".ai-runtime-frame__menu-toggle");

    expect(wrapper.get(".ai-runtime-frame__nav").attributes("aria-label")).toBe("主导航");
    expect(wrapper.get("video").attributes("aria-label")).toBe("AI Runtime 背景视频");
    expect(menuToggle.attributes("aria-label")).toBe("打开导航菜单");

    setLocale("en-US");
    await settle();

    expect(wrapper.get(".ai-runtime-frame__nav").attributes("aria-label")).toBe("Primary navigation");
    expect(wrapper.get("video").attributes("aria-label")).toBe("AI Runtime background video");
    expect(menuToggle.attributes("aria-label")).toBe("Open navigation menu");

    await menuToggle.trigger("click");
    await settle();

    expect(menuToggle.attributes("aria-label")).toBe("Close navigation menu");
    expect(wrapper.get(".ai-runtime-frame__mobile-menu").attributes("aria-label")).toBe("Primary navigation");
    expect(wrapper.get(".ai-runtime-frame__mobile-nav").attributes("aria-label")).toBe("Mobile primary navigation");
    wrapper.unmount();
  });

  it("pauses media but leaves the poster scene mounted for reduced motion", async () => {
    installMatchMedia(true);
    expect(window.matchMedia("(prefers-reduced-motion: reduce)").matches).toBe(true);
    const wrapper = mount(AiRuntimeFrame, { props: { videoPoster: "/assets/runtime-poster.webp" } });
    const video = wrapper.get("video");
    await settle();

    expect(wrapper.classes()).toContain("is-reduced-motion");
    expect(video.attributes("autoplay")).toBeUndefined();
    expect(video.attributes("poster")).toBe("/assets/runtime-poster.webp");
    wrapper.unmount();
  });

  it("opens the mobile menu, traps focus, and restores the trigger after Escape", async () => {
    const wrapper = mount(AiRuntimeFrame, {
      attachTo: document.body,
      props: { menuItems: [{ label: "学习台", href: "/user/home", active: true }] },
    });
    const toggle = wrapper.get<HTMLButtonElement>(".ai-runtime-frame__menu-toggle");

    await toggle.trigger("click");
    await settle();

    expect(toggle.attributes("aria-expanded")).toBe("true");
    expect(document.body.style.overflow).toBe("hidden");
    expect(document.activeElement).toBe(wrapper.get(".ai-runtime-frame__mobile-nav a").element);

    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true }));
    await settle();

    expect(toggle.attributes("aria-expanded")).toBe("false");
    expect(document.body.style.overflow).toBe("");
    expect(document.activeElement).toBe(toggle.element);
    wrapper.unmount();
  });

  it("lets the same hamburger trigger close the open mobile menu", async () => {
    const wrapper = mount(AiRuntimeFrame, { attachTo: document.body });
    const toggle = wrapper.get<HTMLButtonElement>(".ai-runtime-frame__menu-toggle");

    await toggle.trigger("click");
    await settle();
    expect(toggle.attributes("aria-expanded")).toBe("true");

    await toggle.trigger("click");
    await settle();
    expect(toggle.attributes("aria-expanded")).toBe("false");
    expect(wrapper.get(".ai-runtime-frame__mobile-layer").attributes("hidden")).toBeDefined();
    wrapper.unmount();
  });

  it("supports a controlled menu state without owning the caller's state", async () => {
    const wrapper = mount(AiRuntimeFrame, { props: { menuOpen: false } });

    await wrapper.get(".ai-runtime-frame__menu-toggle").trigger("click");

    expect(wrapper.emitted("update:menuOpen")?.at(-1)).toEqual([true]);
    expect(wrapper.get(".ai-runtime-frame__mobile-layer").attributes("hidden")).toBeDefined();
    wrapper.unmount();
  });

  it("makes a closed mobile layer inert so its contextual controls cannot intercept clicks", async () => {
    const wrapper = mount(AiRuntimeFrame, { attachTo: document.body });
    const layer = wrapper.get(".ai-runtime-frame__mobile-layer");

    expect(layer.attributes("hidden")).toBeDefined();
    expect(layer.attributes("inert")).toBeDefined();

    await wrapper.get(".ai-runtime-frame__menu-toggle").trigger("click");
    await settle();
    expect(layer.attributes("hidden")).toBeUndefined();
    expect(layer.attributes("inert")).toBeUndefined();
    wrapper.unmount();
  });
});
