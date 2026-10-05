import { afterEach, describe, expect, it, vi } from "vitest";
import { createCoursewareCatalog } from "./courseware-catalog";
import type { PresentationDeck, PresentationSlide } from "../types/contracts";

afterEach(() => vi.useRealTimers());

function setup() {
  const api = {
    listPresentationDecks: vi.fn(async (): Promise<PresentationDeck[]> => []),
    listDeckSlides: vi.fn(async (_id: string): Promise<PresentationSlide[]> => []),
  };
  return { api, catalog: createCoursewareCatalog(api) };
}

describe("courseware metadata cache", () => {
  it("shares in-flight directory and deck requests and reuses successful results", async () => {
    const { api, catalog } = setup();
    expect(catalog.getDecks()).toBe(catalog.getDecks());
    expect(catalog.getSlides("a")).toBe(catalog.getSlides("a"));
    await Promise.all([catalog.getDecks(), catalog.getSlides("a"), catalog.getSlides("b")]);
    await catalog.getDecks();
    await catalog.getSlides("a");
    expect(api.listPresentationDecks).toHaveBeenCalledTimes(1);
    expect(api.listDeckSlides.mock.calls).toEqual([["a"], ["b"]]);
  });

  it("refreshes expired results and does not retain a failed request", async () => {
    vi.useFakeTimers();
    const { api, catalog } = setup();
    await catalog.getDecks();
    await catalog.getSlides("a");
    vi.advanceTimersByTime(60_001);
    api.listDeckSlides.mockRejectedValueOnce(new Error("offline"));
    await expect(catalog.getSlides("a")).rejects.toThrow("offline");
    await catalog.getSlides("a");
    await catalog.getDecks();
    expect(api.listPresentationDecks).toHaveBeenCalledTimes(2);
    expect(api.listDeckSlides).toHaveBeenCalledTimes(3);
  });

  it("drops the previous login cache and ignores its late completion", async () => {
    const { api } = setup();
    let identity = "first";
    let finish!: (slides: PresentationSlide[]) => void;
    api.listDeckSlides.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    const catalog = createCoursewareCatalog(api, () => identity);
    const previous = catalog.getSlides("a");
    await catalog.getDecks();
    identity = "second";
    await catalog.getSlides("a");
    await catalog.getDecks();
    finish([{ id: "private-first-login" } as PresentationSlide]);
    await previous;
    expect(await catalog.getSlides("a")).toEqual([]);
    expect(api.listDeckSlides).toHaveBeenCalledTimes(2);
    expect(api.listPresentationDecks).toHaveBeenCalledTimes(2);
  });

  it("bounds cached decks and keeps the most recently opened one", async () => {
    const { api, catalog } = setup();
    for (let i = 0; i < 16; i += 1) await catalog.getSlides(String(i));
    await catalog.getSlides("0");
    await catalog.getSlides("16");
    await catalog.getSlides("0");
    expect(api.listDeckSlides).toHaveBeenCalledTimes(17);
    await catalog.getSlides("1");
    expect(api.listDeckSlides).toHaveBeenCalledTimes(18);
  });
});
