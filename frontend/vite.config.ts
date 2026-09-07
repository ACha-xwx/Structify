import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import {
  createBuildIntegrityPlugin,
  createDevelopmentServerConfig,
  createFrontendBuildConfig,
  createLocalPresentationPreviewPlugin,
} from "./vite-routing";

const frontendRoot = path.dirname(fileURLToPath(import.meta.url));

export function createViteConfig(command: string, env: Record<string, string | undefined>) {
  const build = createFrontendBuildConfig(frontendRoot);

  return {
    root: build.sourceRoot,
    plugins: [
      vue(),
      ...(command === "serve" ? [createLocalPresentationPreviewPlugin(frontendRoot)] : []),
      ...(command === "build" ? [createBuildIntegrityPlugin(frontendRoot, build.outDir)] : []),
    ],
    resolve: {
      alias: {
        "@": build.sourceRoot,
      },
    },
    server: createDevelopmentServerConfig(env),
    build: {
      outDir: build.outDir,
      emptyOutDir: build.emptyOutDir,
      // Keep CSS, JavaScript, fonts, images, and SVGs as independently
      // cacheable content-hashed files. This also prevents the HTML shell from
      // growing into a multi-megabyte inline bundle.
      assetsInlineLimit: 0,
      rollupOptions: {
        input: path.join(build.sourceRoot, "index.html"),
      },
    },
  };
}

export default defineConfig(({ command, mode }) => createViteConfig(command, loadEnv(mode, frontendRoot, "")));
