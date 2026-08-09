const assert = require("node:assert/strict");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const root = path.join(__dirname, "..");
const result = spawnSync(process.execPath, ["server.js"], {
  cwd: root,
  encoding: "utf8",
  env: {
    ...process.env,
    NODE_ENV: "production",
    JWT_SECRET: "",
    CORS_ALLOWED_ORIGINS: "https://structify.cn",
    SMTP_HOST: "",
    SMTP_USER: "",
    SMTP_PASS: "",
    SMTP_FROM: "",
    DB_PATH: path.join(os.tmpdir(), "ds-agent-production-config-never-created.db")
  }
});

assert.notEqual(result.status, 0, "production must fail closed without a JWT secret");
const output = `${result.stdout || ""}\n${result.stderr || ""}`;
assert.match(output, /JWT_SECRET is required in production/);
assert.doesNotMatch(output, /(?:api[_ -]?key|password|secret)\s*[:=]\s*\S+/i);
console.log("production-config-ok jwt-required=1 no-secret-output=1");
