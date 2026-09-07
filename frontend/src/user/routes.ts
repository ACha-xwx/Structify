import type { RouteRecordRaw } from "vue-router";
import ChaptersView from "./views/ChaptersView.vue";
import ResourceView from "./views/ResourceView.vue";
import KnowledgeView from "./views/KnowledgeView.vue";
import ChatView from "./views/ChatView.vue";
import ClassroomView from "./views/ClassroomView.vue";
import AnimationView from "./views/AnimationView.vue";
import CodeView from "./views/CodeView.vue";
import ProgressView from "./views/ProgressView.vue";
import ProfileView from "./views/ProfileView.vue";
import PresentationView from "./views/PresentationView.vue";
import LearningWorkbenchView from "./views/LearningWorkbenchView.vue";

const learningMeta = (module: string) => ({ layout: "workbench" as const, module });

export const userRoutes: RouteRecordRaw[] = [
  // Keep bookmarks from the former user root usable while preserving
  // /user/home as the canonical guest-accessible learning entry.
  { path: "/user", redirect: "/user/home", meta: { layout: "workbench", module: "学习路径" } },
  { path: "/user/home", name: "user-home", component: LearningWorkbenchView, meta: learningMeta("学习路径") },
  { path: "/user/chapters", name: "user-chapters", component: ChaptersView, meta: learningMeta("学习路径") },
  // Keep old chapter URLs bookmarkable, but resolve them into the canonical
  // Runtime course map so entering a lesson never switches to the legacy page shell.
  {
    path: "/user/chapters/:chapterId",
    name: "user-chapter-detail",
    redirect: (to) => ({
      path: "/user/chapters",
      query: { ...to.query, chapterId: String(to.params.chapterId ?? "") },
    }),
    meta: learningMeta("学习路径"),
  },
  { path: "/user/resources/:resourceId", name: "user-resource", component: ResourceView, meta: learningMeta("资料") },
  { path: "/user/knowledge", name: "user-knowledge", component: KnowledgeView, meta: learningMeta("资料") },
  { path: "/user/coach", name: "user-coach", component: ChatView, meta: learningMeta("AI 伴学") },
  { path: "/user/classroom", name: "user-classroom", component: ClassroomView, meta: learningMeta("课堂") },
  { path: "/user/animation", name: "user-animation", component: AnimationView, meta: learningMeta("实践") },
  { path: "/user/presentation", name: "user-presentation", component: PresentationView, meta: { ...learningMeta("资料"), allowsLocalPreview: true } },
  { path: "/user/code", name: "user-code", component: CodeView, meta: learningMeta("实践") },
  { path: "/user/progress", name: "user-progress", component: ProgressView, meta: learningMeta("账户工具") },
  // Preserve the former review bookmark while keeping progress as the
  // canonical product surface.
  { path: "/user/review", redirect: "/user/progress", meta: learningMeta("账户工具") },
  { path: "/user/profile", name: "user-profile", component: ProfileView, meta: learningMeta("账户工具") },
];
