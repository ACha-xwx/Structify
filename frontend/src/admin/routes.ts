import type { RouteRecordRaw } from "vue-router";
const AdminHomeView = () => import("./views/AdminHomeView.vue");
const AdminUsersView = () => import("./views/AdminUsersView.vue");
const AdminReviewsView = () => import("./views/AdminReviewsView.vue");
const AdminTasksView = () => import("./views/AdminTasksView.vue");
const AdminAuditView = () => import("./views/AdminAuditView.vue");
const AdminSettingsView = () => import("./views/AdminSettingsView.vue");
const AdminMailConfigView = () => import("./views/AdminMailConfigView.vue");
const AdminSandboxConfigView = () => import("./views/AdminSandboxConfigView.vue");

const adminMeta = { requiresAuth: true, roles: ["ADMIN"], layout: "admin" } as const;

export const adminRoutes: RouteRecordRaw[] = [
  { path: "/admin", name: "admin-home", component: AdminHomeView, meta: { ...adminMeta, module: "Admin overview" } },
  { path: "/admin/users", name: "admin-users", component: AdminUsersView, meta: { ...adminMeta, module: "Users and roles" } },
  { path: "/admin/reviews", name: "admin-reviews", component: AdminReviewsView, meta: { ...adminMeta, module: "Review queue" } },
  { path: "/admin/tasks", name: "admin-tasks", component: AdminTasksView, meta: { ...adminMeta, module: "Background tasks" } },
  { path: "/admin/audit", name: "admin-audit", component: AdminAuditView, meta: { ...adminMeta, module: "Audit events" } },
  { path: "/admin/settings", name: "admin-settings", component: AdminSettingsView, meta: { ...adminMeta, module: "Model settings" } },
  { path: "/admin/mail", name: "admin-mail", component: AdminMailConfigView, meta: { ...adminMeta, module: "Mail delivery" } },
  { path: "/admin/sandbox", name: "admin-sandbox", component: AdminSandboxConfigView, meta: { ...adminMeta, module: "Code sandbox" } },
];
