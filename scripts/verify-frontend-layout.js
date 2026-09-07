const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const net = require("node:net");
const os = require("node:os");
const path = require("node:path");
const { spawn } = require("node:child_process");

const root = path.join(__dirname, "..");
const backendRoot = path.join(root, "backend", "node");
const frontendPath = path.join(root, "frontend", "index.html");
const legacyPrototypePath = path.join(root, "frontend", "prototype.html");

function getFreePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address();
      server.close((error) => error ? reject(error) : resolve(port));
    });
  });
}

async function waitForHealth(baseUrl, child, readStderr) {
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      const detail = typeof readStderr === "function" ? readStderr().trim() : "";
      throw new Error(`server exited early with code ${child.exitCode}${detail ? `: ${detail}` : ""}`);
    }
    try {
      const response = await fetch(`${baseUrl}/healthz`);
      if (response.ok) return;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("server did not become healthy in time");
}

async function stopChild(child) {
  if (child.exitCode !== null) return;
  child.kill();
  await Promise.race([
    new Promise((resolve) => child.once("exit", resolve)),
    new Promise((resolve) => setTimeout(resolve, 1_500))
  ]);
}

function requestRaw(baseUrl, pathname, method = "GET") {
  const target = new URL(baseUrl);
  return new Promise((resolve, reject) => {
    const request = http.request({
      hostname: target.hostname,
      port: target.port,
      method,
      path: pathname,
      headers: { connection: "close" }
    }, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => resolve({
        status: response.statusCode || 0,
        headers: response.headers,
        body: Buffer.concat(chunks)
      }));
    });
    request.on("error", reject);
    request.end();
  });
}

async function verifyHashedAssetRoute() {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "ds-frontend-assets-"));
  const fixtureFrontendDir = path.join(tempDir, "frontend");
  const assetsDir = path.join(fixtureFrontendDir, "assets");
  const assetName = "entry-test-a1b2c3.js";
  const assetBody = "export default 42;\n";
  fs.mkdirSync(assetsDir, { recursive: true });
  fs.writeFileSync(
    path.join(fixtureFrontendDir, "index.html"),
    "<!doctype html><html><body><div id=\"app\"></div></body></html>\n",
    "utf8"
  );
  fs.writeFileSync(path.join(assetsDir, assetName), assetBody, "utf8");

  const port = await getFreePort();
  const baseUrl = `http://127.0.0.1:${port}`;
  const child = spawn(process.execPath, ["server.js"], {
    cwd: backendRoot,
    env: {
      ...process.env,
      NODE_ENV: "test",
      HOST: "127.0.0.1",
      PORT: String(port),
      DB_PATH: path.join(tempDir, "test.db"),
      KNOWLEDGE_DIR: path.join(tempDir, "knowledge"),
      PRESENTATION_DIR: path.join(tempDir, "presentation-materials"),
      FRONTEND_DIR: fixtureFrontendDir,
      JWT_SECRET: "frontend-assets-test-secret-32-characters",
      MODEL_API_KEY: "",
      MIMO_API_KEY: "",
      SMTP_HOST: "",
      SMTP_USER: "",
      SMTP_PASS: "",
      SMTP_FROM: ""
    },
    stdio: ["ignore", "ignore", "pipe"]
  });
  let stderr = "";
  child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });

  try {
    await waitForHealth(baseUrl, child, () => stderr);

    const response = await fetch(`${baseUrl}/assets/${assetName}?v=1`);
    assert.equal(response.status, 200, "hashed frontend assets must be served by the Node compatibility route");
    assert.equal(response.headers.get("content-type"), "text/javascript; charset=utf-8");
    assert.equal(response.headers.get("cache-control"), "public, max-age=31536000, immutable");
    assert.equal(await response.text(), assetBody);

    const headResponse = await fetch(`${baseUrl}/assets/${assetName}`, { method: "HEAD" });
    assert.equal(headResponse.status, 200, "HEAD requests must work for hashed frontend assets");
    assert.equal(headResponse.headers.get("cache-control"), "public, max-age=31536000, immutable");
    assert.equal((await headResponse.arrayBuffer()).byteLength, 0, "HEAD asset responses must not transfer the body");

    const traversalResponse = await requestRaw(baseUrl, "/assets/%2e%2e%2Findex.html");
    assert.equal(traversalResponse.status, 404, "asset requests must reject encoded traversal");
    const unsupportedResponse = await fetch(`${baseUrl}/assets/not-supported.bin`);
    assert.equal(unsupportedResponse.status, 404, "asset requests must reject unsupported extensions");
  } finally {
    await stopChild(child);
    fs.rmSync(tempDir, { recursive: true, force: true });
  }

  if (stderr.trim()) process.stderr.write(stderr);
}

async function main() {
  assert.ok(fs.existsSync(frontendPath), "canonical frontend/index.html is required");
  assert.ok(fs.existsSync(legacyPrototypePath), "legacy frontend/prototype.html must be preserved during migration");
  const frontend = fs.readFileSync(frontendPath, "utf8");
  const legacyPrototype = fs.readFileSync(legacyPrototypePath, "utf8");
  assert.match(frontend, /<div id="app"><\/div>/, "canonical frontend must expose the Vue mount element");
  assert.match(frontend, /<script type="module"/, "canonical frontend must include the built Vue entry module");

  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "ds-frontend-layout-"));
  const port = await getFreePort();
  const baseUrl = `http://127.0.0.1:${port}`;
  const child = spawn(process.execPath, ["server.js"], {
    cwd: backendRoot,
    env: {
      ...process.env,
      NODE_ENV: "test",
      HOST: "127.0.0.1",
      PORT: String(port),
      DB_PATH: path.join(tempDir, "test.db"),
      KNOWLEDGE_DIR: path.join(tempDir, "knowledge"),
      PRESENTATION_DIR: path.join(tempDir, "presentation-materials"),
      FRONTEND_DIR: path.join(root, "frontend"),
      JWT_SECRET: "frontend-layout-test-secret-32-characters",
      MODEL_API_KEY: "",
      MIMO_API_KEY: "",
      SMTP_HOST: "",
      SMTP_USER: "",
      SMTP_PASS: "",
      SMTP_FROM: ""
    },
    stdio: ["ignore", "ignore", "pipe"]
  });
  let stderr = "";
  child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });

  try {
    await waitForHealth(baseUrl, child, () => stderr);
    for (const pathname of ["/", "/index.html", "/login", "/user/chapters", "/admin/users"]) {
      const response = await fetch(`${baseUrl}${pathname}`);
      assert.equal(response.status, 200, `${pathname} must resolve to the canonical frontend`);
      assert.match(response.headers.get("content-type") || "", /^text\/html/);
      assert.equal(
        response.headers.get("cache-control"),
        "no-cache, max-age=0, must-revalidate",
        `${pathname} must keep the HTML shell revalidated`
      );
      assert.equal(await response.text(), frontend, `${pathname} must not serve a divergent frontend copy`);
    }
    const headResponse = await fetch(`${baseUrl}/user/chapters`, { method: "HEAD" });
    assert.equal(headResponse.status, 200, "HEAD /user/chapters must provide a lightweight SPA navigation check");
    assert.match(headResponse.headers.get("content-type") || "", /^text\/html/);
    assert.equal((await headResponse.arrayBuffer()).byteLength, 0, "HEAD / must not transfer the frontend body");
    const prototypeResponse = await fetch(`${baseUrl}/prototype.html`);
    assert.equal(prototypeResponse.status, 200, "/prototype.html must preserve the legacy entry during migration");
    assert.equal(await prototypeResponse.text(), legacyPrototype, "/prototype.html must serve frontend/prototype.html");
    for (const pathname of ["/api/not-a-route", "/presentation/not-found.png", "/pdfs/not-found.pdf", "/vendor/not-found.js", "/unrelated-path"]) {
      const response = await fetch(`${baseUrl}${pathname}`);
      assert.equal(response.status, 404, `${pathname} must not be swallowed by the SPA fallback`);
    }
    const nonNavigationResponse = await fetch(`${baseUrl}/user/chapters`, { method: "POST" });
    assert.equal(nonNavigationResponse.status, 404, "POST requests must not be treated as SPA navigation");
  } finally {
    await stopChild(child);
    fs.rmSync(tempDir, { recursive: true, force: true });
  }

  await verifyHashedAssetRoute();

  if (stderr.trim()) process.stderr.write(stderr);
  console.log("frontend-layout-ok canonical=frontend/index.html legacy=frontend/prototype.html spaHistoryRoutes=5 hashedAssets=1");
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
