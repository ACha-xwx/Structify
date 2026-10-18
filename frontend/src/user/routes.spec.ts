import { describe, expect, it } from "vitest";
import { userRoutes } from "./routes";

describe("user routes", () => {
  it("keeps the profile placeholder and redirects retired user surfaces", () => {
    expect(userRoutes).toHaveLength(3);
    // The learning map is a page of its own, next to the profile it belongs to.
    expect(userRoutes.map((route) => route.path)).toContain("/learning-map");
    expect(userRoutes[0]).toMatchObject({
      path: "/user",
      name: "user-home",
      meta: { layout: "workbench", module: "个人主页" },
    });
    expect(userRoutes[0]?.component).toBeDefined();
    // Found by path rather than by position: adding a page must not break the retired-surface guard.
    const retired = userRoutes.find((route) => route.path.startsWith("/user/:pathMatch"));
    expect(retired).toMatchObject({
      redirect: "/",
      meta: { layout: "workbench", module: "入口" },
    });
    expect(retired?.component).toBeUndefined();
  });
});
