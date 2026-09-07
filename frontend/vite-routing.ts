import { createHash } from "node:crypto";
import { existsSync, realpathSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import type { Plugin } from "vite";

// Keep separately copied runtime assets inside the build-integrity scope.
const FRONTEND_RUNTIME_ASSETS = ["prototype.html"] as const;

export interface DevelopmentServerConfig {
  host: string;
  port: number;
  proxy: Record<string, { target: string; changeOrigin: boolean }>;
}

export interface FrontendBuildConfig {
  sourceRoot: string;
  outDir: string;
  emptyOutDir: true;
}

/**
 * Keep the Spring v1 route ahead of the wider Node compatibility route. The
 * browser always calls a same-origin path, so this is local-dev routing only.
 */
export function createDevelopmentServerConfig(env: Record<string, string | undefined>): DevelopmentServerConfig {
  const nodeOrigin = env.VITE_DEV_NODE_ORIGIN || "http://127.0.0.1:8791";
  const springOrigin = env.VITE_DEV_SPRING_ORIGIN || "http://127.0.0.1:8792";

  return {
    host: "0.0.0.0",
    port: Number(env.VITE_DEV_PORT || 5173),
    proxy: {
      "/api/v1": { target: springOrigin, changeOrigin: false },
      "/api": { target: nodeOrigin, changeOrigin: false },
      "/healthz": { target: nodeOrigin, changeOrigin: false },
      "/presentation": { target: nodeOrigin, changeOrigin: false },
      "/pdfs": { target: nodeOrigin, changeOrigin: false },
      "/vendor": { target: nodeOrigin, changeOrigin: false },
    },
  };
}

export function createLocalPresentationPreviewPlugin(frontendRoot: string): Plugin {
  const workspaceRoot = path.resolve(frontendRoot, "..");
  const bundleDirCandidates = [
    path.join(workspaceRoot, "private", "presentation-materials"),
    path.resolve(workspaceRoot, "..", "data-structure-agent-class-simulation", "presentation-materials"),
    path.resolve(workspaceRoot, "..", "data-structure-agent-main", "private", "presentation-materials"),
  ];
  const bundleDir = bundleDirCandidates.find((candidate) => existsSync(path.join(candidate, "slides.json")) && existsSync(path.join(candidate, "lesson-presentation-plans.json")));

  return {
    name: "local-presentation-preview-token",
    configureServer(server) {
      server.middlewares.use("/__preview/presentation-plan", (request, response, next) => {
        if (request.method !== "GET") {
          next();
          return;
        }

        const forwarded = String(request.headers["x-forwarded-for"] || "");
        const remoteAddress = forwarded.split(",")[0]?.trim() || request.socket.remoteAddress || "";
        if (!isLocalRequest(remoteAddress)) {
          response.statusCode = 403;
          response.setHeader("Content-Type", "application/json; charset=utf-8");
          response.end(JSON.stringify({ error: "Local preview is only available on localhost." }));
          return;
        }

        try {
          if (!bundleDir) {
            response.statusCode = 404;
            response.setHeader("Content-Type", "application/json; charset=utf-8");
            response.end(JSON.stringify({ error: "Local presentation bundle is unavailable." }));
            return;
          }

          const lessonId = String(new URL(request.url || "", "http://localhost").searchParams.get("lessonId") || "").trim();
          const payload = buildLocalPresentationResponse(bundleDir, lessonId);
          response.statusCode = 200;
          response.setHeader("Cache-Control", "no-store");
          response.setHeader("Content-Type", "application/json; charset=utf-8");
          response.end(JSON.stringify(payload));
        } catch (error) {
          response.statusCode = 500;
          response.setHeader("Content-Type", "application/json; charset=utf-8");
          response.end(JSON.stringify({ error: error instanceof Error ? error.message : "Unable to build local preview response." }));
        }
      });

      server.middlewares.use("/__preview/presentation", (request, response, next) => {
        if (request.method !== "GET") {
          next();
          return;
        }

        const forwarded = String(request.headers["x-forwarded-for"] || "");
        const remoteAddress = forwarded.split(",")[0]?.trim() || request.socket.remoteAddress || "";
        if (!isLocalRequest(remoteAddress)) {
          response.statusCode = 403;
          response.end("Local preview is only available on localhost.");
          return;
        }
        if (!bundleDir) {
          response.statusCode = 404;
          response.end("Local presentation bundle is unavailable.");
          return;
        }

        const requestUrl = new URL(request.url || "", "http://localhost");
        const relative = requestUrl.pathname.replace(/^\/__preview\/presentation\/?/, "");
        const decoded = relative.split("/").map((segment) => decodeURIComponent(segment)).join("/");
        const safePath = path.normalize(decoded).replace(/^([\\/])+/, "");
        if (!safePath || safePath.includes("..") || path.isAbsolute(safePath)) {
          response.statusCode = 404;
          response.end("Not found");
          return;
        }
        const filePath = path.resolve(bundleDir, safePath);
        const renderedRoot = path.resolve(bundleDir, "rendered");
        const relativePath = path.relative(renderedRoot, filePath);
        if (!relativePath || relativePath.startsWith("..") || path.isAbsolute(relativePath)) {
          response.statusCode = 404;
          response.end("Not found");
          return;
        }
        try {
          const realRenderedRoot = realpathSync(renderedRoot);
          const realFile = realpathSync(filePath);
          const realRelative = path.relative(realRenderedRoot, realFile);
          if (!realRelative || realRelative.startsWith("..") || path.isAbsolute(realRelative)) {
            response.statusCode = 404;
            response.end("Not found");
            return;
          }
          const ext = path.extname(realFile).toLowerCase();
          const contentType = ext === ".png"
            ? "image/png"
            : ext === ".jpg" || ext === ".jpeg"
              ? "image/jpeg"
              : ext === ".webp"
                ? "image/webp"
                : "application/octet-stream";
          response.statusCode = 200;
          response.setHeader("Content-Type", contentType);
          response.setHeader("Cache-Control", "no-store");
          response.end(readFileSync(realFile));
        } catch {
          response.statusCode = 404;
          response.end("Not found");
        }
      });
    },
  };
}

export function createFrontendBuildConfig(frontendRoot: string): FrontendBuildConfig {
  return {
    sourceRoot: path.join(frontendRoot, "src"),
    outDir: path.join(frontendRoot, "dist"),
    emptyOutDir: true,
  };
}

export function createBuildIntegrityPlugin(frontendRoot: string, outDir: string) {
  return {
    name: "frontend-build-integrity",
    closeBundle() {
      const manifest = {
        schemaVersion: 1,
        sourceHash: frontendSourceHash(frontendRoot),
      };
      writeFileSync(path.join(outDir, "build-integrity.json"), `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
    },
  };
}

function frontendSourceHash(frontendRoot: string): string {
  const sourceFiles = [
    ...collectFiles(path.join(frontendRoot, "src"), frontendRoot),
    "package.json",
    "package-lock.json",
    "tsconfig.json",
    "vite.config.ts",
    "vite-routing.ts",
    "vitest.config.ts",
    ...FRONTEND_RUNTIME_ASSETS,
  ].sort();
  const hash = createHash("sha256");
  for (const relativePath of sourceFiles) {
    hash.update(relativePath.replace(/\\/g, "/"));
    hash.update("\0");
    hash.update(readFileSync(path.join(frontendRoot, relativePath)));
    hash.update("\0");
  }
  return hash.digest("hex");
}

function collectFiles(directory: string, frontendRoot: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(directory, entry.name);
    return entry.isDirectory()
      ? collectFiles(fullPath, frontendRoot)
      : [path.relative(frontendRoot, fullPath)];
  });
}

function readNodeCompatibilitySecret(candidates: string[]): string {
  for (const candidate of candidates) {
    if (!candidate || !existsSync(candidate)) continue;
    const secret = readFileSync(candidate, "utf8").trim();
    if (secret.length >= 32) return secret;
  }
  return "";
}

function buildLocalPresentationResponse(bundleDir: string, lessonId: string) {
  const slidesDoc = JSON.parse(readFileSync(path.join(bundleDir, "slides.json"), "utf8")) as {
    builtAt?: string;
    slides?: Array<Record<string, unknown>>;
  };
  const plansDoc = JSON.parse(readFileSync(path.join(bundleDir, "lesson-presentation-plans.json"), "utf8")) as {
    builtAt?: string;
    lessons?: Record<string, {
      title?: string;
      scenes?: Record<string, { slides?: string[]; primarySlideId?: string }>;
      slideOrder?: string[];
    }>;
  };
  const slides = Array.isArray(slidesDoc.slides) ? slidesDoc.slides : [];
  const lessons = plansDoc.lessons && typeof plansDoc.lessons === "object" ? plansDoc.lessons : {};
  const plan = lessons[lessonId] || null;
  const slidesById = new Map(slides.map((slide) => [String(slide.id || ""), slide]));

  const publicSlide = (slideId: string) => {
    const raw = slidesById.get(String(slideId || "").trim());
    if (!raw) return null;
    const imagePath = String(raw.imagePath || "").replace(/\\/g, "/").replace(/^\/+/, "");
    const imageUrl = createLocalPresentationImageUrl(bundleDir, imagePath);
    return {
      id: String(raw.id || "").slice(0, 120),
      deckId: String(raw.deckId || "").slice(0, 100),
      deckTitle: String(raw.deckTitle || "").slice(0, 160),
      slideNumber: Math.max(1, Number(raw.slideNumber) || 1),
      chapter: String(raw.chapter || "").slice(0, 12),
      title: String(raw.title || "").slice(0, 220),
      rawText: String(raw.rawText || "").slice(0, 5000),
      speakerNotes: String(raw.speakerNotes || "").slice(0, 2400),
      semanticSummary: String(raw.semanticSummary || "").slice(0, 700),
      teachingRole: String(raw.teachingRole || "").slice(0, 40),
      teachingFocus: String(raw.teachingFocus || "").slice(0, 700),
      concepts: Array.isArray(raw.concepts) ? raw.concepts.map(String).slice(0, 10) : [],
      visualAnchors: Array.isArray(raw.visualAnchors) ? raw.visualAnchors.map(String).slice(0, 8) : [],
      animationCapabilities: Array.isArray(raw.animationCapabilities) ? raw.animationCapabilities.map(String).slice(0, 12) : [],
      imageUrl,
    };
  };

  const slideIds = new Set<string>();
  for (const scene of Object.values(plan?.scenes || {})) {
    for (const slideId of scene?.slides || []) slideIds.add(String(slideId));
    if (scene?.primarySlideId) slideIds.add(String(scene.primarySlideId));
  }

  const publicSlides: Record<string, ReturnType<typeof publicSlide>> = {};
  for (const slideId of slideIds) {
    const slide = publicSlide(slideId);
    if (slide) publicSlides[slideId] = slide;
  }

  return {
    ok: true,
    ready: slides.length > 0,
    lessonId,
    plan: plan ? {
      lessonId,
      title: String(plan.title || "").slice(0, 160),
      scenes: plan.scenes || {},
      slideOrder: Array.isArray(plan.slideOrder) ? plan.slideOrder.map(String) : [],
    } : null,
    slides: publicSlides,
    meta: {
      ready: slides.length > 0,
      builtAt: slidesDoc.builtAt || plansDoc.builtAt || "",
      slideCount: slides.length,
      lessonCount: Object.keys(lessons).length,
    },
  };
}

/**
 * Local preview only: inline the already-rendered PNG so browser extensions
 * cannot mistake the localhost asset middleware for a blocked subresource.
 * Production responses still come from the signed backend asset URL.
 */
function createLocalPresentationImageUrl(bundleDir: string, imagePath: string): string {
  if (!imagePath) return "";
  const resolved = path.resolve(bundleDir, imagePath);
  const relative = path.relative(bundleDir, resolved);
  if (!relative || relative.startsWith("..") || path.isAbsolute(relative) || !existsSync(resolved)) return "";
  const extension = path.extname(resolved).toLowerCase();
  const contentType = extension === ".png"
    ? "image/png"
    : extension === ".jpg" || extension === ".jpeg"
      ? "image/jpeg"
      : extension === ".webp"
        ? "image/webp"
        : "";
  if (!contentType) return "";
  return `data:${contentType};base64,${readFileSync(resolved).toString("base64")}`;
}

function isLocalRequest(value: string): boolean {
  return value === "127.0.0.1"
    || value === "::1"
    || value === "::ffff:127.0.0.1"
    || value === "::ffff:localhost"
    || value === "localhost";
}
