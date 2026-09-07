import { createApiClient } from "../../shared/api";
import { createAuthStore } from "../../shared/auth";

let bearerToken: string | null = null;
export const api = createApiClient({ tokenProvider: () => bearerToken });
// The legacy Node runtime owns the signed classroom presentation assets. It
// intentionally lives beside, rather than inside, the Spring v1 client so the
// rest of the user API cannot accidentally drift onto the compatibility path.
export const legacyApi = createApiClient({ baseUrl: "/api", tokenProvider: () => bearerToken });
export const auth = createAuthStore({ api, onTokenChange: (token) => { bearerToken = token; } });

/**
 * The Spring session cookie is intentionally HttpOnly. When the legacy Node
 * classroom adapter needs a Bearer token, mint a five-minute compatibility
 * token through the authenticated Spring boundary instead of exposing the
 * session token or sharing the two signing secrets.
 */
export async function issueNodeCompatibilityToken(): Promise<string | null> {
  if (!auth.state.user) return null;
  const response = await api.request<{ token?: unknown }>("/auth/node-compat-token");
  if (response.kind !== "json" || typeof response.data.token !== "string" || !response.data.token.trim()) {
    throw new Error("课件兼容令牌响应无效");
  }
  return response.data.token;
}
