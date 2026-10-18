import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { auth } from "../app/providers/runtime";
import { createRouteGuard } from "./guards";
const HomeView = () => import("../shared/views/HomeView.vue");
const LandingView = () => import("../shared/views/LandingView.vue");
const ClassroomView = () => import("../classroom/ClassroomView.vue");
const CoursewareView = () => import("../shared/views/CoursewareView.vue");
const AnimationLabView = () => import("../animation/AnimationLabView.vue");
const CodeEditorView = () => import("../compiler/CodeEditorView.vue");
const ChatView = () => import("../user/views/ChatView.vue");
const AuthView = () => import("../shared/views/AuthView.vue");
const ForbiddenView = () => import("../shared/views/ForbiddenView.vue");
const NotFoundView = () => import("../shared/views/NotFoundView.vue");
import { userRoutes } from "../user/routes";
import { adminRoutes } from "../admin/routes";

/**
 * The public root introduces the site; /begin holds the signed-in learning choices.
 * Each learning surface keeps its own path so no lesson starts before it is selected.
 */
export const routes: RouteRecordRaw[] = [
  { path: "/", name: "home", component: LandingView, meta: { layout: "workbench", module: "首页" } },
  { path: "/begin", name: "begin", component: HomeView, meta: { requiresAuth: true, layout: "workbench", module: "入口" } },
  { path: "/classroom", name: "classroom", component: ClassroomView, meta: { requiresAuth: true, layout: "workbench", module: "课堂" } },
  { path: "/courseware", name: "courseware", component: CoursewareView, meta: { requiresAuth: true, layout: "workbench", module: "课件浏览" } },
  { path: "/animation", name: "animation-lab", component: AnimationLabView, meta: { requiresAuth: true, layout: "workbench", module: "动画实验室" } },
  // The original app let guests run C experiments without an account; that stays.
  { path: "/compiler", name: "compiler", component: CodeEditorView, meta: { layout: "workbench", module: "C 编辑器" } },
  // Asking the course directly. Its backend never left; the page was dropped in the stage refactor.
  { path: "/chat", name: "chat", component: ChatView, meta: { requiresAuth: true, layout: "workbench", module: "课程问答" } },
  { path: "/login", name: "login", component: AuthView, props: { mode: "login" }, meta: { layout: "auth" } },
  { path: "/register", name: "register", component: AuthView, props: { mode: "register" }, meta: { layout: "auth" } },
  { path: "/reset-password", name: "reset-password", component: AuthView, props: { mode: "reset" }, meta: { layout: "auth" } },
  ...userRoutes,
  ...adminRoutes,
  { path: "/403", name: "forbidden", component: ForbiddenView, meta: { layout: "minimal" } },
  { path: "/404", name: "not-found", component: NotFoundView, meta: { layout: "minimal" } },
  { path: "/:pathMatch(.*)*", redirect: "/404" },
];

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});

const guard = createRouteGuard({ auth });
router.beforeEach((to) => {
  // Fetch only the destination page while /me is in flight, rather than afterward.
  for (const record of to.matched) {
    const page = record.components?.default;
    if (typeof page === 'function') void Promise.resolve((page as () => Promise<unknown>)()).catch(() => undefined);
  }
  return guard(to);
});

export default router;
