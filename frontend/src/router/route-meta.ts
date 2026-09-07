import type { Role } from "../shared/types";

export type RouteLayout = "auth" | "shell" | "workbench" | "admin" | "minimal";
export interface AppRouteMeta {
  requiresAuth?: boolean;
  roles?: Role[];
  layout?: RouteLayout;
  module?: string;
  requiresCapability?: string;
  allowsLocalPreview?: boolean;
}

export function routeMeta(value: AppRouteMeta = {}): AppRouteMeta { return value; }
