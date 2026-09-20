import { describe, expect, it } from "vitest";
import { routes } from "./index";

/**
 * The root path used to mount the classroom itself, so signing in started a lesson nobody had picked.
 * Keep the entry page and the classroom on separate paths.
 */
describe("workbench entry routes", () => {
  it("keeps the root path a chooser and the classroom on its own path", () => {
    const home = routes.find((route) => route.name === "home");
    const classroom = routes.find((route) => route.name === "classroom");

    expect(home?.path).toBe("/");
    expect(classroom?.path).toBe("/classroom");
    expect(home?.component).not.toBe(classroom?.component);
  });
});
