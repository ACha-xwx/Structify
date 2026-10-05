import { describe, expect, it } from "vitest";
import { userRoutes } from "./routes";

describe("user routes", () => {
  it("keeps the profile placeholder and redirects retired user surfaces", () => {
    expect(userRoutes).toHaveLength(2);
    expect(userRoutes[0]).toMatchObject({
      path: "/user",
      name: "user-home",
      meta: { layout: "workbench", module: "个人主页" },
    });
    expect(userRoutes[0]?.component).toBeDefined();
    expect(userRoutes[1]).toMatchObject({
      path: "/user/:pathMatch(.*)*",
      redirect: "/",
      meta: { layout: "workbench", module: "入口" },
    });
    expect(userRoutes[1]?.component).toBeUndefined();
  });
});
