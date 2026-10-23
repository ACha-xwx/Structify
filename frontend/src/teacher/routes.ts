import type { RouteRecordRaw } from "vue-router";

const TeacherHomeView = () => import("./views/TeacherHomeView.vue");

/** Staff area for a class teacher: their own classes and their students' progress. */
const teacherMeta = { requiresAuth: true, roles: ["TEACHER", "ADMIN"], layout: "admin" } as const;

export const teacherRoutes: RouteRecordRaw[] = [
  {
    path: "/teacher",
    name: "teacher-home",
    component: TeacherHomeView,
    meta: { ...teacherMeta, module: "My classes" },
  },
];
