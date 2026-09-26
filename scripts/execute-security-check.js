const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const { spawn } = require("node:child_process");

const root = path.join(__dirname, "..");
const nodeRoot = path.join(root, "backend", "node");
const APP_PORT = 8891;
const MOCK_PORT = 18891;
const APP_BASE = `http://127.0.0.1:${APP_PORT}`;
const MOCK_BASE = `http://127.0.0.1:${MOCK_PORT}`;

/**
 * The sandbox is no longer open to anonymous callers, so this harness has to present a token like any
 * other client. It pins the secret it gives the app and signs with the same HMAC the server verifies,
 * which keeps the check independent of any real account.
 */
const TEST_JWT_SECRET = "execute-security-check-secret-0123456789abcdef";

function signTestToken(userId = 1, email = "student@example.com") {
  const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url");
  const payload = Buffer.from(JSON.stringify({
    userId,
    email,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600
  })).toString("base64url");
  const signature = crypto.createHmac("sha256", TEST_JWT_SECRET)
    .update(`${header}.${payload}`)
    .digest("base64url");
  return `${header}.${payload}.${signature}`;
}

/** Signed once at startup and attached to every execute request this harness makes. */
const TEST_TOKEN = signTestToken();

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
    });
    req.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (error) {
        reject(error);
      }
    });
    req.on("error", reject);
  });
}

async function startMockExecutor() {
  const server = http.createServer(async (req, res) => {
    if (req.method !== "POST") {
      res.writeHead(404);
      res.end("not found");
      return;
    }

    const body = await readJson(req).catch(() => ({}));
    const code = String(body.source_code || body.files?.[0]?.content || "");

    if (req.url.startsWith("/submissions")) {
      if (code.includes("JUDGE_HANG")) {
        return;
      }
      if (code.includes("HANG")) {
        return;
      }
      if (code.includes("HOLD")) {
        await sleep(600);
      }
      const stdout = code.includes("PRINT_BIG") ? "X".repeat(500) : "ok\n";
      const payload = {
        stdout,
        stderr: "",
        compile_output: "",
        status: { id: 3, description: "Accepted" }
      };
      res.writeHead(200, { "content-type": "application/json; charset=utf-8" });
      res.end(JSON.stringify(payload));
      return;
    }

    if (req.url === "/execute") {
      if (code.includes("HANG") && !code.includes("JUDGE_HANG")) {
        return;
      }
      if (code.includes("HOLD")) {
        await sleep(600);
      }
      const stdout = code.includes("PRINT_BIG") ? "X".repeat(500) : "ok\n";
      const payload = {
        run: { stdout, stderr: "" },
        compile: { stderr: "" }
      };
      res.writeHead(200, { "content-type": "application/json; charset=utf-8" });
      res.end(JSON.stringify(payload));
      return;
    }

    res.writeHead(404);
    res.end("not found");
  });

  await new Promise((resolve) => server.listen(MOCK_PORT, "127.0.0.1", resolve));
  return server;
}

async function fetchJson(path, body, extraHeaders = {}, timeoutMs = 5000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${APP_BASE}${path}`, {
      method: "POST",
      headers: {
        "content-type": "application/json; charset=utf-8",
        authorization: `Bearer ${TEST_TOKEN}`,
        ...extraHeaders
      },
      body: JSON.stringify(body),
      signal: controller.signal
    });
    const json = await response.json();
    return { status: response.status, json };
  } finally {
    clearTimeout(timer);
  }
}

async function waitForHealth(child, timeoutMs = 10000) {
  const startedAt = Date.now();
  let stderr = "";
  if (child.stderr) {
    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString();
    });
  }

  while (Date.now() - startedAt < timeoutMs) {
    if (child.exitCode !== null) {
      throw new Error(`app server exited early: ${child.exitCode}\n${stderr}`);
    }
    try {
      const response = await fetch(`${APP_BASE}/healthz`);
      if (response.ok) return;
    } catch {}
    await sleep(150);
  }
  throw new Error(`health check timeout\n${stderr}`);
}

async function run() {
  const mock = await startMockExecutor();
  // Self-contained state. Without this the app writes its JWT secret and SQLite file under
  // private/state/node, which a production image either does not have or mounts read-only - the
  // check then dies with EACCES mkdir before it can assert anything.
  const stateDir = fs.mkdtempSync(path.join(os.tmpdir(), "execute-security-check-"));
  const child = spawn(process.execPath, ["server.js"], {
    cwd: nodeRoot,
    env: {
      ...process.env,
      HOST: "127.0.0.1",
      PORT: String(APP_PORT),
      NODE_COMPAT_JWT_SECRET: TEST_JWT_SECRET,
      NODE_STATE_DIR: path.join(stateDir, "state"),
      DB_PATH: path.join(stateDir, "data.db"),
      PDF_DIR: path.join(stateDir, "pdfs"),
      JUDGE0_BASE_URL: MOCK_BASE,
      PISTON_BASE_URL: MOCK_BASE,
      EXECUTE_RATE_WINDOW_MS: "60000",
      EXECUTE_RATE_MAX: "2",
      EXECUTE_MAX_CONCURRENCY: "1",
      EXECUTE_PER_IP_CONCURRENCY: "1",
      EXECUTE_TIMEOUT_MS: "2000",
      EXECUTE_OUTPUT_MAX_CHARS: "120",
      EXECUTE_ERROR_MAX_CHARS: "120"
    },
    stdio: ["ignore", "pipe", "pipe"]
  });

  const cleanup = async () => {
    child.kill("SIGTERM");
    await sleep(300);
    if (child.exitCode === null) child.kill("SIGKILL");
    await new Promise((resolve) => mock.close(resolve));
  };

  let failures = 0;
  try {
    await waitForHealth(child);

    const cases = [
      ["anonymous execute is refused", async () => {
        // The sandbox used to answer anyone who could reach it. A stranger must now be told to sign in,
        // and the refusal has to carry the trace id so the attempt can be followed in the logs.
        const response = await fetch(`${APP_BASE}/api/execute`, {
          method: "POST",
          headers: { "content-type": "application/json", "x-forwarded-for": "10.0.0.20" },
          body: JSON.stringify({ language: "c", code: "int main(void){return 0;}" })
        });
        assert.equal(response.status, 401);
        const body = await response.json();
        assert.equal(body.code, "AUTH_REQUIRED");
        assert.ok(body.requestId, "a refusal must carry a request id");
        assert.equal(response.headers.get("x-request-id"), body.requestId);
      }],
      ["rate limit on execute", async () => {
        const headers = { "x-forwarded-for": "10.0.0.11" };
        const payload = { language: "c", code: "int main(void){return 0;}" };
        const first = await fetchJson("/api/execute", payload, headers);
        const second = await fetchJson("/api/execute", payload, headers);
        const third = await fetchJson("/api/execute", payload, headers);
        assert.equal(first.status, 200);
        assert.equal(second.status, 200);
        assert.equal(third.status, 429);
      }],
      ["per-ip concurrency limit", async () => {
        const headers = { "x-forwarded-for": "10.0.0.12" };
        const payload = { language: "c", code: "// HOLD\nint main(void){return 0;}" };
        const firstPromise = fetchJson("/api/execute", payload, headers, 7000);
        await sleep(150);
        const second = await fetchJson("/api/execute", payload, headers, 7000);
        const first = await firstPromise;
        assert.equal(first.status, 200);
        assert.ok(second.status === 429 || second.status === 503, `expected 429/503, got ${second.status}`);
      }],
      ["execution timeout guard", async () => {
        const headers = { "x-forwarded-for": "10.0.0.13" };
        const payload = { language: "c", code: "// HANG\nint main(void){return 0;}" };
        const startedAt = Date.now();
        const result = await fetchJson("/api/execute", payload, headers, 4500);
        const elapsed = Date.now() - startedAt;
        assert.equal(result.status, 504);
        assert.ok(elapsed < 4000, `timeout response too slow: ${elapsed}ms`);
      }],
      ["fallback after primary executor timeout", async () => {
        const headers = { "x-forwarded-for": "10.0.0.15" };
        const payload = { language: "c", code: "// JUDGE_HANG\nint main(void){return 0;}" };
        const result = await fetchJson("/api/execute", payload, headers, 4500);
        assert.equal(result.status, 200);
        assert.equal(result.json.provider, "piston");
        assert.match(result.json.warning || "", /Judge0/);
      }],
      ["output truncation", async () => {
        const headers = { "x-forwarded-for": "10.0.0.14" };
        const payload = { language: "c", code: "// PRINT_BIG\nint main(void){return 0;}" };
        const result = await fetchJson("/api/execute", payload, headers);
        assert.equal(result.status, 200);
        assert.ok(result.json.output.includes("[输出已截断"), "missing truncation marker");
        assert.ok(result.json.output.length <= 160, `output too long: ${result.json.output.length}`);
      }]
    ];

    for (const [name, fn] of cases) {
      try {
        await fn();
        console.log(`PASS ${name}`);
      } catch (error) {
        failures++;
        console.error(`FAIL ${name}: ${error.message}`);
      }
    }
  } finally {
    await cleanup();
  }

  process.exit(failures === 0 ? 0 : 1);
}

run().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
