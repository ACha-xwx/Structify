import { describe, expect, it } from "vitest";
import { userRoutes } from "./routes";

describe("retired user routes", () => {
  it("redirects every former user surface to the entry page", () => {
    expect(userRoutes).toHaveLength(1);
    expect(userRoutes[0]).toMatchObject({
      path: "/user/:pathMatch(.*)*",
      redirect: "/",
      meta: { layout: "workbench", module: "入口" },
    });
    expect(userRoutes[0]?.component).toBeUndefined();
  });
});
