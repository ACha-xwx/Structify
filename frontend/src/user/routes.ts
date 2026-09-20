import type { RouteRecordRaw } from "vue-router";

/**
 * The former learning surfaces are intentionally detached while Structify is
 * rebuilt. Keep one compatibility redirect so old bookmarks cannot expose the
 * retired interface and authenticated users always land on the entry page.
 */
export const userRoutes: RouteRecordRaw[] = [
  { path: "/user/:pathMatch(.*)*", redirect: "/", meta: { layout: "workbench", module: "入口" } },
];
