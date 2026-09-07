import { describe, expect, it, vi } from "vitest";
import { createClassroomScriptsPreview, loadClassroomScripts } from "./classroom";

describe("classroom adapter", () => {
  it("uses the local catalogue for guest entry without calling the API", async () => {
    const api = { listClassroomScripts: vi.fn() };

    const snapshot = await loadClassroomScripts(api, { chapterId: "03-stack-queue", signedIn: false });

    expect(api.listClassroomScripts).not.toHaveBeenCalled();
    expect(snapshot.mode).toBe("fixture");
    expect(snapshot.fixtureReason).toBe("guest-preview");
    expect(snapshot.scripts.map((script) => script.chapterId)).toEqual(expect.arrayContaining(["stack", "queue"]));
  });

  it("prefers published scripts for signed-in learners", async () => {
    const liveScript = { id: "live-stack", chapterId: "stack", title: "Stack classroom", versionLabel: "v1" };
    const api = { listClassroomScripts: vi.fn().mockResolvedValue([liveScript]) };

    const snapshot = await loadClassroomScripts(api, { chapterId: "stack", signedIn: true });

    expect(api.listClassroomScripts).toHaveBeenCalledWith("stack");
    expect(snapshot).toEqual({ mode: "live", scripts: [liveScript], fixtureReason: null });
  });

  it("labels an empty published catalogue as a local fallback", async () => {
    const api = { listClassroomScripts: vi.fn().mockResolvedValue([]) };

    const snapshot = await loadClassroomScripts(api, { chapterId: "queue", signedIn: true });

    expect(snapshot.mode).toBe("fixture");
    expect(snapshot.fixtureReason).toBe("api-empty");
    expect(snapshot.scripts).toHaveLength(1);
    expect(snapshot.scripts[0]?.chapterId).toBe("queue");
  });

  it("falls back only for an unavailable catalogue, not an authorization error", async () => {
    const unavailable = { listClassroomScripts: vi.fn().mockRejectedValue({ status: 503 }) };
    const unauthorized = { listClassroomScripts: vi.fn().mockRejectedValue({ status: 401 }) };

    await expect(loadClassroomScripts(unavailable, { chapterId: "bfs", signedIn: true }))
      .resolves.toMatchObject({ mode: "fixture", fixtureReason: "api-unavailable" });
    await expect(loadClassroomScripts(unauthorized, { chapterId: "bfs", signedIn: true })).rejects.toMatchObject({ status: 401 });
  });

  it("does not manufacture a script for an unknown chapter context", () => {
    expect(createClassroomScriptsPreview("unknown-chapter", "api-empty").scripts).toEqual([]);
  });
});
