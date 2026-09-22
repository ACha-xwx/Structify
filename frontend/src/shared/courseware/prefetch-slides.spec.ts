import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

describe("prefetchSlideImages", () => {
  let requested: string[];

  beforeEach(() => {
    vi.resetModules();
    requested = [];
    class FakeImage {
      decoding = "";
      set src(value: string) {
        requested.push(value);
      }
    }
    vi.stubGlobal("Image", FakeImage);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("warms each url once so repeated turns do not refetch", async () => {
    const { prefetchSlideImages } = await import("./prefetch-slides");

    prefetchSlideImages(["/a.png", "/a.png", "/b.png"]);

    expect(requested).toEqual(["/a.png", "/b.png"]);
  });

  it("provides the decoding hint the browser uses to keep the main thread free", async () => {
    const created: { decoding: string }[] = [];
    class RecordingImage {
      decoding = "";
      set src(_value: string) {
        created.push(this);
      }
    }
    vi.stubGlobal("Image", RecordingImage);
    const { prefetchSlideImages } = await import("./prefetch-slides");

    prefetchSlideImages(["/a.png"]);

    expect(created).toHaveLength(1);
    expect(created[0]?.decoding).toBe("async");
  });

  it("ignores empty slots so a missing neighbour is not requested", async () => {
    const { prefetchSlideImages } = await import("./prefetch-slides");

    prefetchSlideImages([undefined, "", null]);

    expect(requested).toEqual([]);
  });

  it("stays silent where no Image constructor exists", async () => {
    vi.stubGlobal("Image", undefined);
    const { prefetchSlideImages } = await import("./prefetch-slides");

    expect(() => prefetchSlideImages(["/a.png"])).not.toThrow();
  });
});

describe("prefetchSlideWindow", () => {
  let requested: string[];

  const deck = (count: number) => Array.from({ length: count }, (_unused, at) => ({ imageUrl: `/s${at}.webp` }));

  beforeEach(() => {
    vi.resetModules();
    requested = [];
    class FakeImage {
      decoding = "";
      set src(value: string) {
        requested.push(value);
      }
    }
    vi.stubGlobal("Image", FakeImage);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("warms the pages ahead, nearest first, so the next turn is already local", async () => {
    const { prefetchSlideWindow } = await import("./prefetch-slides");

    prefetchSlideWindow(deck(20), 5);

    expect(requested.slice(0, 4)).toEqual(["/s6.webp", "/s7.webp", "/s8.webp", "/s9.webp"]);
  });

  it("also warms the page behind, so 上一页 is a lookup too", async () => {
    const { prefetchSlideWindow } = await import("./prefetch-slides");

    prefetchSlideWindow(deck(20), 5);

    expect(requested).toContain("/s4.webp");
  });

  it("never asks for pages past the end of the deck", async () => {
    const { prefetchSlideWindow } = await import("./prefetch-slides");

    prefetchSlideWindow(deck(7), 6);

    expect(requested).toEqual(["/s5.webp"]);
  });

  it("survives a missing courseware pane", async () => {
    const { prefetchSlideWindow } = await import("./prefetch-slides");

    expect(() => prefetchSlideWindow(null, 0)).not.toThrow();
    expect(() => prefetchSlideWindow([], 3)).not.toThrow();
    expect(requested).toEqual([]);
  });

  it("does not re-request a page it already warmed", async () => {
    const { prefetchSlideWindow } = await import("./prefetch-slides");

    prefetchSlideWindow(deck(20), 5);
    const after = requested.length;
    prefetchSlideWindow(deck(20), 5);

    expect(requested.length).toBe(after);
  });

  it("only fetches what stepping back newly needs", async () => {
    const { prefetchSlideWindow } = await import("./prefetch-slides");

    prefetchSlideWindow(deck(20), 5);
    const after = requested.length;
    prefetchSlideWindow(deck(20), 4);

    expect(requested.slice(after)).toEqual(["/s5.webp", "/s3.webp"]);
  });
});
