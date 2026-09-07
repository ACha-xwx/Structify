import type { RouteLocationRaw } from "vue-router";

export function createLoginTarget(currentPath: string): RouteLocationRaw {
  const redirect = currentPath.startsWith("/") ? currentPath : "/user/home";
  return { path: "/login", query: { redirect } };
}
