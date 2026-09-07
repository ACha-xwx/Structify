import type { RouteLocationNormalized, RouteLocationRaw } from "vue-router";
import type { AuthStore } from "../shared/auth";
import type { AppRouteMeta } from "./route-meta";

export interface GuardRoute extends Pick<RouteLocationNormalized, "path" | "fullPath"> { meta: AppRouteMeta }

interface BrowserLocation {
  hostname: string;
}

const ADMIN_HOSTNAME = "admin.structify.cn";
const PUBLIC_ORIGIN = "https://structify.cn";

function currentBrowserLocation(): BrowserLocation | undefined {
  if (typeof window === "undefined") return undefined;
  return window.location;
}

function isLocalPreviewHost(location: BrowserLocation | undefined): boolean {
  const hostname = location?.hostname?.toLowerCase() || "";
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "[::1]";
}

function isAdminHostPath(path: string): boolean {
  return path === "/admin"
    || path.startsWith("/admin/")
    || path === "/login"
    || path === "/reset-password"
    || path === "/403";
}

function publicUrl(fullPath: string): string {
  const safePath = fullPath.startsWith("/") && !fullPath.startsWith("//") ? fullPath : "/";
  return `${PUBLIC_ORIGIN}${safePath}`;
}

function replaceBrowserLocation(url: string) {
  window.location.replace(url);
}

export function createRouteGuard(options: {
  auth: AuthStore;
  location?: BrowserLocation;
  redirectToPublic?: (url: string) => void;
}) {
  const location = options.location ?? currentBrowserLocation();
  const redirectToPublic = options.redirectToPublic ?? replaceBrowserLocation;

  return async (to: GuardRoute): Promise<true | false | RouteLocationRaw> => {
    if (location?.hostname.toLowerCase() === ADMIN_HOSTNAME) {
      if (to.path === "/") return { path: "/admin" };
      if (!isAdminHostPath(to.path)) {
        redirectToPublic(publicUrl(to.fullPath || to.path));
        return false;
      }
    }

    const auth = options.auth;
    const meta = to.meta || {};
    const localPreviewAllowed = Boolean(meta.allowsLocalPreview && isLocalPreviewHost(location));
    const hasSessionHint = typeof auth.hasSessionHint === "function" ? auth.hasSessionHint() : false;
    const requiresSessionBootstrap = Boolean(
      (meta.requiresAuth && !localPreviewAllowed)
      || meta.roles?.length
      || meta.requiresCapability
      || hasSessionHint,
    );
    if (requiresSessionBootstrap && (auth.state.status === "idle" || auth.state.status === "restoring")) {
      await auth.restoreSession();
    }
    // A transport failure only preserves access when a previously restored user
    // is still present. Cold-start failures must not turn an unknown session
    // into an implicit grant for protected routes.
    const sessionIndeterminate = Boolean(auth.state.user)
      && (auth.state.status === "offline" || auth.state.status === "error");
    if (meta.requiresAuth && !localPreviewAllowed && (!auth.state.user || auth.state.status !== "authenticated") && !sessionIndeterminate) {
      if (auth.state.status === "disabled") return { name: "forbidden", query: { reason: "disabled" } };
      if (auth.state.status === "forbidden") return { name: "forbidden" };
      return { name: "login", query: { redirect: to.fullPath || to.path } };
    }
    if (meta.roles?.length) {
      const hasRole = typeof auth.hasAnyRole === "function"
        ? auth.hasAnyRole(meta.roles)
        : Boolean(auth.state.user && meta.roles.some((role) => auth.state.user?.roles.includes(role)));
      // An outage may preserve an existing session, but it cannot grant roles
      // that are absent from the last known user record.
      if (!hasRole) return { name: "forbidden" };
    }
    if (meta.requiresCapability) {
      const capabilities = auth.state.capabilities || (typeof auth.loadCapabilities === "function" ? await auth.loadCapabilities() : null);
      if (!auth.state.user && !sessionIndeterminate) {
        if (auth.state.status === "disabled") return { name: "forbidden", query: { reason: "disabled" } };
        return { name: "login", query: { redirect: to.fullPath || to.path } };
      }
      if (!capabilities) {
        const errorStatus = auth.state.error?.status;
        if (errorStatus === 403 || auth.state.status === "forbidden") return { name: "forbidden" };
        return true;
      }
      const capability = capabilities?.modules?.[meta.requiresCapability];
      if (!capability?.available) return { name: "forbidden" };
    }
    return true;
  };
}
