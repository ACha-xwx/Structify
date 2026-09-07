import { describe, expect, it } from "vitest";
import { createLoginTarget } from "./login-target";

describe("createLoginTarget", () => {
  it("preserves the current learning route for an in-place permission prompt", () => {
    expect(createLoginTarget("/user/coach?chapterId=sequential-list")).toEqual({
      path: "/login",
      query: { redirect: "/user/coach?chapterId=sequential-list" },
    });
  });

  it("falls back to the learning workbench for an invalid path", () => {
    expect(createLoginTarget("coach")).toEqual({ path: "/login", query: { redirect: "/user/home" } });
  });
});
