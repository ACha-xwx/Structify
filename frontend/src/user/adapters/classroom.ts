import type { ClassroomScript } from "../../shared/types/contracts";
import type { UserApi } from "../api";
import { classroomPreviewScriptsForChapter } from "../fixtures/classroom-preview";

/**
 * The classroom API currently exposes only published scripts. This adapter
 * keeps the live/preview boundary in one place: an empty or unavailable
 * script catalogue receives an explicitly labelled local teaching preview;
 * permission and validation responses remain visible to the caller.
 */
export type ClassroomScriptMode = "live" | "fixture";
export type ClassroomFixtureReason = "guest-preview" | "api-empty" | "api-unavailable";

export interface ClassroomScriptsSnapshot {
  mode: ClassroomScriptMode;
  scripts: ClassroomScript[];
  fixtureReason: ClassroomFixtureReason | null;
}
export type ClassroomScriptsApi = Pick<UserApi, "listClassroomScripts">;

function errorStatus(cause: unknown): number | null {
  if (!cause || typeof cause !== "object" || !("status" in cause)) return null;
  const status = (cause as { status?: unknown }).status;
  return typeof status === "number" && Number.isFinite(status) ? status : null;
}

/** Do not replace authentication or invalid-request errors with fake content. */
export function isClassroomScriptsApiUnavailable(cause: unknown): boolean {
  if (cause instanceof TypeError) return true;
  const status = errorStatus(cause);
  return status === 404 || status === 408 || status === 502 || status === 503 || status === 504;
}

export function createClassroomScriptsPreview(
  chapterId?: string,
  reason: ClassroomFixtureReason = "guest-preview",
): ClassroomScriptsSnapshot {
  return {
    mode: "fixture",
    scripts: classroomPreviewScriptsForChapter(chapterId),
    fixtureReason: reason,
  };
}

/**
 * Guests do not make an implicit API call. Signed-in learners use published
 * scripts first, then receive an honest local fallback when the catalogue is
 * empty or the endpoint itself is unavailable.
 */
export async function loadClassroomScripts(
  api: ClassroomScriptsApi,
  options: { chapterId?: string; signedIn?: boolean } = {},
): Promise<ClassroomScriptsSnapshot> {
  const chapterId = options.chapterId?.trim() || undefined;
  if (!options.signedIn) return createClassroomScriptsPreview(chapterId, "guest-preview");

  try {
    const scripts = await api.listClassroomScripts(chapterId);
    if (scripts.length) return { mode: "live", scripts, fixtureReason: null };
    return createClassroomScriptsPreview(chapterId, "api-empty");
  } catch (cause) {
    if (isClassroomScriptsApiUnavailable(cause)) {
      return createClassroomScriptsPreview(chapterId, "api-unavailable");
    }
    throw cause;
  }
}
