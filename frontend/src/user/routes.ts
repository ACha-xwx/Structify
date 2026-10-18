import type { RouteRecordRaw } from "vue-router";
const UserHomeView = () => import("./views/UserHomeView.vue");
const LearningMapView = () => import("./views/LearningMapView.vue");

/**
 * The former learning surfaces are intentionally detached while Structify is
 * rebuilt. Keep one compatibility redirect so old bookmarks cannot expose the
 * retired interface and authenticated users always land on the entry page.
 */
export const userRoutes: RouteRecordRaw[] = [
  { path: "/user", name: "user-home", component: UserHomeView, meta: { requiresAuth: true, layout: "workbench", module: "个人主页" } },
  { path: "/learning-map", name: "learning-map", component: LearningMapView, meta: { requiresAuth: true, layout: "workbench", module: "学习图谱" } },
  { path: "/user/:pathMatch(.*)*", redirect: "/", meta: { layout: "workbench", module: "入口" } },
];
