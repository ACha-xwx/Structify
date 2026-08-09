const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const { spawn, spawnSync } = require("node:child_process");

const root = path.join(__dirname, "..");

function getFreePort() {
  return new Promise((resolve, reject) => {
    const probe = http.createServer();
    probe.once("error", reject);
    probe.listen(0, "127.0.0.1", () => {
      const address = probe.address();
      const port = typeof address === "object" && address ? address.port : 0;
      probe.close((error) => error ? reject(error) : resolve(port));
    });
  });
}

async function waitForHealth(baseUrl, child) {
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`production server exited early with code ${child.exitCode}`);
    }
    try {
      const response = await fetch(`${baseUrl}/healthz`);
      if (response.ok) return response.json();
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("production server did not become healthy in time");
}

async function verifyOptionalServicesDoNotBlockStartup() {
  const port = await getFreePort();
  const dbPath = path.join(os.tmpdir(), `ds-agent-production-config-${process.pid}.db`);
  const child = spawn(process.execPath, ["server.js"], {
    cwd: root,
    env: {
      ...process.env,
      NODE_ENV: "production",
      HOST: "127.0.0.1",
      PORT: String(port),
      NODE_COMPAT_JWT_SECRET: "n".repeat(64),
      CORS_ALLOWED_ORIGINS: "https://structify.cn",
      MODEL_API_KEY: "",
      SMTP_HOST: "",
      SMTP_USER: "",
      SMTP_PASS: "",
      SMTP_FROM: "",
      JUDGE0_BASE_URL: "",
      PISTON_BASE_URL: "",
      DB_PATH: dbPath
    },
    stdio: ["ignore", "pipe", "pipe"]
  });
  let stderr = "";
  child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
  try {
    const health = await waitForHealth(`http://127.0.0.1:${port}`, child);
    assert.equal(health.modelConfigured, false);
    assert.equal(health.smtpConfigured, false);
    assert.equal(health.codeExecutionConfigured, false);
    const mailResponse = await fetch(`http://127.0.0.1:${port}/api/auth/request-code`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: "student@example.test", purpose: "login" })
    });
    assert.equal(mailResponse.status, 503);
    assert.equal((await mailResponse.json()).code, "SMTP_NOT_CONFIGURED");
    const executionResponse = await fetch(`http://127.0.0.1:${port}/api/execute`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ language: "c", code: "int main(void) { return 0; }" })
    });
    assert.equal(executionResponse.status, 503);
    assert.equal((await executionResponse.json()).code, "COMPILER_NOT_CONFIGURED");
    assert.doesNotMatch(stderr, /(?:api[_ -]?key|password|secret)\s*[:=]\s*\S+/i);
  } finally {
    child.kill();
  }
}

function verifyOptionalDeploymentContract() {
  const compose = fs.readFileSync(path.join(root, "deployment", "docker-compose.production.yml"), "utf8");
  const productionEnv = fs.readFileSync(path.join(root, "deployment", ".env.spring.example"), "utf8");
  const preflight = fs.readFileSync(path.join(root, "deployment", "scripts", "preflight.sh"), "utf8");
  const deploy = fs.readFileSync(path.join(root, "deployment", "scripts", "deploy.sh"), "utf8");
  const backup = fs.readFileSync(path.join(root, "deployment", "scripts", "backup.sh"), "utf8");
  const restore = fs.readFileSync(path.join(root, "deployment", "scripts", "restore.sh"), "utf8");
  const health = fs.readFileSync(path.join(root, "deployment", "scripts", "health-check.sh"), "utf8");
  const hostCaddy = fs.readFileSync(path.join(root, "deployment", "Caddyfile.host.production"), "utf8");
  const productionCaddy = fs.readFileSync(path.join(root, "deployment", "Caddyfile.production"), "utf8");
  const productionApplication = fs.readFileSync(path.join(root, "apps", "server", "src", "main", "resources", "application-prod.yml"), "utf8");
  const nodeDockerfile = fs.readFileSync(path.join(root, "deployment", "Dockerfile.node"), "utf8");
  const nodeDockerignore = fs.readFileSync(path.join(root, "deployment", "Dockerfile.node.dockerignore"), "utf8");
  const nodeEntrypoint = fs.readFileSync(path.join(root, "deployment", "node-entrypoint.sh"), "utf8");
  const springDockerfile = fs.readFileSync(path.join(root, "apps", "server", "Dockerfile"), "utf8");

  assert.match(compose, /MODEL_API_KEY:\s+\$\{MODEL_API_KEY:-\}/);
  assert.match(compose, /SMTP_HOST:\s+\$\{SMTP_HOST:-\}/);
  assert.match(compose, /AUTH_MAIL_ENABLED:\s+\$\{AUTH_MAIL_ENABLED:-false\}/);
  assert.match(compose, /NODE_COMPAT_JWT_SECRET:\s+\$\{NODE_COMPAT_JWT_SECRET:\?set a strong NODE_COMPAT_JWT_SECRET\}/);
  assert.match(compose, /NODE_COMPAT_ENABLED:\s+\$\{NODE_COMPAT_ENABLED:-true\}/);
  assert.match(compose, /PISTON_BASE_URL:\s+\$\{PISTON_BASE_URL:-\}/);
  assert.match(compose, /127\.0\.0\.1:\$\{NODE_HOST_PORT:-18791\}:8791/);
  assert.match(compose, /127\.0\.0\.1:\$\{SPRING_HOST_PORT:-18792\}:8792/);
  assert.match(compose, /profiles:\s*\n\s*- container-caddy/);
  assert.doesNotMatch(preflight, /required=\([^)]*\bMODEL_API_KEY\b/);
  assert.doesNotMatch(preflight, /required=\([^)]*\bSMTP_PASS\b/);
  assert.doesNotMatch(preflight, /required=\([^)]*\bPISTON_BASE_URL\b/);
  assert.doesNotMatch(preflight, /AUTH_MAIL_ENABLED\)"\s*=~\s*\^\(true\|1\|yes\|on\)/);
  assert.match(preflight, /AUTH_MAIL_ENABLED/);
  assert.match(preflight, /caddy_mode/);
  assert.match(deploy, /host Caddy mode/);
  assert.match(deploy, /bootstrap data services/);
  assert.match(deploy, /compose up -d mysql node/);
  assert.ok(
    deploy.indexOf("compose up -d mysql node") < deploy.lastIndexOf('"$SCRIPT_DIR/backup.sh"'),
    "first-deployment database bootstrap must precede the persistent-data backup"
  );
  assert.match(backup, /chmod 600 "\$DEST"\/\*/);
  assert.match(backup, /mysqldump[^\n]*--no-tablespaces/);
  assert.match(restore, /compose up -d mysql/);
  assert.ok(
    restore.indexOf("compose up -d mysql") < restore.indexOf('compose exec -T mysql'),
    "restore must start MySQL before importing the database dump"
  );
  assert.doesNotMatch(
    restore,
    /-v "\$BACKUP_DIR:\/restore:ro"/,
    "a 0600 host backup must not be bind-mounted into the non-root Node container"
  );
  assert.match(restore, /for attempt in \$\(seq 1 30\)/, "restore must wait for application health");
  assert.match(health, /node_host_port/);
  assert.match(hostCaddy, /reverse_proxy 127\.0\.0\.1:18791/);
  assert.match(hostCaddy, /reverse_proxy 127\.0\.0\.1:18792/);
  assert.doesNotMatch(hostCaddy, /Strict-Transport-Security/, "HSTS requires an explicit production decision");
  assert.doesNotMatch(productionCaddy, /Strict-Transport-Security/, "HSTS requires an explicit production decision");
  assert.match(productionApplication, /mail-enabled:\s+\$\{AUTH_MAIL_ENABLED:false\}/);
  assert.doesNotMatch(nodeDockerfile, /COPY[^\n]*\bpdfs\b/i, "release image must not package courseware");
  assert.doesNotMatch(nodeDockerignore, /!pdfs(?:\/\*\*)?\s*$/m, "build context must not re-include courseware");
  assert.match(nodeEntrypoint, /if \[ -d \/app\/default-pdfs \]/, "optional default PDFs must be guarded");
  assert.match(nodeDockerfile, /apt-get update/);
  assert.match(nodeDockerfile, /python3/);
  assert.match(nodeDockerfile, /build-essential/);
  assert.match(nodeDockerfile, /ARG NODE_BASE_IMAGE=node:22-bookworm-slim/);
  assert.match(springDockerfile, /ARG JAVA_BUILD_IMAGE=eclipse-temurin:21-jdk/);
  assert.match(springDockerfile, /ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:21-jre/);
  assert.match(compose, /NODE_BASE_IMAGE:\s+\$\{NODE_BASE_IMAGE:-node:22-bookworm-slim\}/);
  assert.match(compose, /JAVA_BUILD_IMAGE:\s+\$\{JAVA_BUILD_IMAGE:-eclipse-temurin:21-jdk\}/);
  assert.match(compose, /JAVA_RUNTIME_IMAGE:\s+\$\{JAVA_RUNTIME_IMAGE:-eclipse-temurin:21-jre\}/);
  assert.match(productionEnv, /^HOST_CADDY_CONFIG=\/etc\/caddy\/Caddyfile$/m);
  assert.match(productionEnv, /^MIN_AVAILABLE_MEMORY_MB=1536$/m);
  assert.match(preflight, /HOST_CADDY_CONFIG is required in host Caddy mode/);
  assert.match(preflight, /caddy validate --config/);
  assert.match(preflight, /MemAvailable/);
}

function bashPath(filePath) {
  if (process.platform !== "win32") return filePath;
  return filePath.replace(/^([A-Za-z]):/, (_, drive) => `/${drive.toLowerCase()}`).replace(/\\/g, "/");
}

function verifyHostCaddyPreflight() {
  const fixtureRoot = fs.mkdtempSync(path.join(os.tmpdir(), "ds-agent-host-caddy-"));
  const envFile = path.join(fixtureRoot, "structify.env");
  const fixture = [
    "COMPOSE_PROJECT_NAME=structify-test",
    "CADDY_MODE=host",
    "NODE_HOST_PORT=18791",
    "SPRING_HOST_PORT=18792",
    "ACME_EMAIL=",
    "NODE_IMAGE=structify-node:test-release",
    "SPRING_IMAGE=structify-spring:test-release",
    "MYSQL_DATABASE=structify",
    "MYSQL_USER=structify_app",
    "MYSQL_PASSWORD=test-database-password",
    "MYSQL_ROOT_PASSWORD=test-root-password",
    `JWT_SECRET=${"j".repeat(64)}`,
    `NODE_COMPAT_JWT_SECRET=${"n".repeat(64)}`,
    "NODE_COMPAT_ENABLED=true",
    "CORS_ALLOWED_ORIGINS=https://structify.cn",
    "AUTH_COOKIE_SECURE=true",
    "AUTH_EXPOSE_DEV_CODE=false",
    "AUTH_MAIL_ENABLED=false",
    "BOOTSTRAP_ADMIN_EMAIL=",
    "TEACHER_EMAILS=",
    "ALLOW_FIRST_USER_TEACHER=false",
    "MODEL_API_KEY=",
    "SMTP_HOST=",
    "SMTP_USER=",
    "SMTP_PASS=",
    "SMTP_FROM=",
    "JUDGE0_BASE_URL=",
    "PISTON_BASE_URL=",
    "VERIFICATION_CODE_FILE=",
    "KNOWLEDGE_DEBUG_API=false",
    "KNOWLEDGE_DIR_HOST=/srv/structify/private/knowledge",
    "RESOURCE_DIR_HOST=/srv/structify/private/course-content",
    "PRESENTATION_DIR_HOST=/srv/structify/private/presentation-materials"
  ].join("\n");
  fs.writeFileSync(envFile, `${fixture}\n`, { mode: 0o600 });

  try {
    const shell = process.platform === "win32" ? "C:\\Program Files\\Git\\bin\\bash.exe" : "bash";
    const result = spawnSync(shell, ["deployment/scripts/preflight.sh", "--env-file", bashPath(envFile)], {
      cwd: root,
      encoding: "utf8"
    });
    const output = `${result.stdout || ""}\n${result.stderr || ""}`;
    assert.equal(result.status, 0, output);
    assert.match(output, /host Caddy mode: Structify will not bind public 80\/443/);
    assert.match(output, /mail integration disabled/);
    assert.doesNotMatch(output, /test-(?:database|root)-password/);
  } finally {
    fs.rmSync(fixtureRoot, { recursive: true, force: true });
  }
}

function writeExecutable(filePath, body) {
  fs.writeFileSync(filePath, `#!/usr/bin/env bash\n${body}\n`, { mode: 0o755 });
  fs.chmodSync(filePath, 0o755);
}

function verifyHostCaddyExecuteGate() {
  const fixtureRoot = fs.mkdtempSync(path.join(os.tmpdir(), "ds-agent-host-caddy-execute-"));
  const binDir = path.join(fixtureRoot, "bin");
  const privateRoot = path.join(fixtureRoot, "private");
  const envFile = path.join(fixtureRoot, "structify.env");
  const hostCaddyConfig = path.join(fixtureRoot, "Caddyfile");
  const bashEnv = path.join(fixtureRoot, "bash-env");
  const privatePaths = [
    path.join(privateRoot, "knowledge"),
    path.join(privateRoot, "course-content"),
    path.join(privateRoot, "presentation-materials")
  ];
  fs.mkdirSync(binDir);
  privatePaths.forEach((privatePath) => fs.mkdirSync(privatePath, { recursive: true }));
  writeExecutable(path.join(binDir, "docker"), "exit 0");
  // Git Bash on Windows cannot represent Linux mode bits on a temporary NTFS
  // fixture. BASH_ENV confines this stable stat result to the test process.
  fs.writeFileSync(bashEnv, [
    "stat() { if [[ \"$1\" == \"-c\" && \"$2\" == \"%a\" ]]; then printf '600\\n'; else command stat \"$@\"; fi; }",
    "awk() { if [[ \"$*\" == *\"/proc/meminfo\"* ]]; then printf '1048576\\n'; else command awk \"$@\"; fi; }"
  ].join("\n"));
  fs.writeFileSync(hostCaddyConfig, "structify.test { respond \\\"ok\\\" }\n", { mode: 0o600 });

  const fixture = [
    "COMPOSE_PROJECT_NAME=structify-test",
    "CADDY_MODE=host",
    "NODE_HOST_PORT=18791",
    "SPRING_HOST_PORT=18792",
    "NODE_IMAGE=structify-node:test-release",
    "SPRING_IMAGE=structify-spring:test-release",
    "MYSQL_DATABASE=structify",
    "MYSQL_USER=structify_app",
    "MYSQL_PASSWORD=test-database-password",
    "MYSQL_ROOT_PASSWORD=test-root-password",
    `JWT_SECRET=${"j".repeat(64)}`,
    `NODE_COMPAT_JWT_SECRET=${"n".repeat(64)}`,
    "NODE_COMPAT_ENABLED=true",
    "CORS_ALLOWED_ORIGINS=https://structify.cn",
    "AUTH_COOKIE_SECURE=true",
    "AUTH_EXPOSE_DEV_CODE=false",
    "AUTH_MAIL_ENABLED=false",
    "BOOTSTRAP_ADMIN_EMAIL=",
    "TEACHER_EMAILS=",
    "ALLOW_FIRST_USER_TEACHER=false",
    "MODEL_API_KEY=",
    "SMTP_HOST=",
    "SMTP_USER=",
    "SMTP_PASS=",
    "SMTP_FROM=",
    "JUDGE0_BASE_URL=",
    "PISTON_BASE_URL=",
    "VERIFICATION_CODE_FILE=",
    "KNOWLEDGE_DEBUG_API=false",
    `KNOWLEDGE_DIR_HOST=${bashPath(privatePaths[0])}`,
    `RESOURCE_DIR_HOST=${bashPath(privatePaths[1])}`,
    `PRESENTATION_DIR_HOST=${bashPath(privatePaths[2])}`
  ].join("\n");
  fs.writeFileSync(envFile, `${fixture}\n`, { mode: 0o600 });

  try {
    const shell = process.platform === "win32" ? "C:\\Program Files\\Git\\bin\\bash.exe" : "bash";
    const env = {
      ...process.env,
      PATH: `${binDir}${path.delimiter}${process.env.PATH}`,
      BASH_ENV: bashPath(bashEnv)
    };
    const missingConfig = spawnSync(shell, [
      "deployment/scripts/preflight.sh", "--env-file", bashPath(envFile), "--execute"
    ], { cwd: root, encoding: "utf8", env });
    const missingConfigOutput = `${missingConfig.stdout || ""}\n${missingConfig.stderr || ""}`;
    assert.notEqual(missingConfig.status, 0, missingConfigOutput);
    assert.match(missingConfigOutput, /HOST_CADDY_CONFIG is required in host Caddy mode/);

    writeExecutable(path.join(binDir, "caddy"), "exit 0");
    fs.appendFileSync(envFile, `HOST_CADDY_CONFIG=${bashPath(hostCaddyConfig)}\nMIN_AVAILABLE_MEMORY_MB=512\n`);
    const configured = spawnSync(shell, [
      "deployment/scripts/preflight.sh", "--env-file", bashPath(envFile), "--execute"
    ], { cwd: root, encoding: "utf8", env });
    const configuredOutput = `${configured.stdout || ""}\n${configured.stderr || ""}`;
    assert.equal(configured.status, 0, configuredOutput);
    assert.match(configuredOutput, /host Caddy configuration validated/);
    assert.doesNotMatch(configuredOutput, /test-(?:database|root)-password/);

    fs.writeFileSync(envFile, fs.readFileSync(envFile, "utf8").replace(
      "MIN_AVAILABLE_MEMORY_MB=512",
      "MIN_AVAILABLE_MEMORY_MB=1536"
    ), { mode: 0o600 });
    const insufficientMemory = spawnSync(shell, [
      "deployment/scripts/preflight.sh", "--env-file", bashPath(envFile), "--execute"
    ], { cwd: root, encoding: "utf8", env });
    const insufficientMemoryOutput = `${insufficientMemory.stdout || ""}\n${insufficientMemory.stderr || ""}`;
    assert.notEqual(insufficientMemory.status, 0, insufficientMemoryOutput);
    assert.match(insufficientMemoryOutput, /available memory 1024 MiB is below configured floor 1536 MiB/);
  } finally {
    fs.rmSync(fixtureRoot, { recursive: true, force: true });
  }
}

function verifyDatabaseRecoveryDryRun() {
  const fixtureRoot = fs.mkdtempSync(path.join(os.tmpdir(), "ds-agent-database-recovery-"));
  const envFile = path.join(fixtureRoot, "structify.env");
  const backupDir = path.join(fixtureRoot, "backup");
  fs.mkdirSync(backupDir);
  fs.writeFileSync(envFile, "CADDY_MODE=host\n", { mode: 0o600 });
  for (const file of ["SHA256SUMS", "mysql.sql", "node.sqlite"]) {
    fs.writeFileSync(path.join(backupDir, file), "fixture\n", { mode: 0o600 });
  }

  try {
    const shell = process.platform === "win32" ? "C:\\Program Files\\Git\\bin\\bash.exe" : "bash";
    const result = spawnSync(shell, [
      "deployment/scripts/restore.sh",
      "--env-file", bashPath(envFile),
      "--backup-dir", bashPath(backupDir)
    ], {
      cwd: root,
      encoding: "utf8"
    });
    const output = `${result.stdout || ""}\n${result.stderr || ""}`;
    assert.equal(result.status, 0, output);
    assert.match(output, /up -d mysql/);
    assert.match(output, /through stdin into \/app\/data/);
    assert.doesNotMatch(output, /restore:ro/);
  } finally {
    fs.rmSync(fixtureRoot, { recursive: true, force: true });
  }
}
const result = spawnSync(process.execPath, ["server.js"], {
  cwd: root,
  encoding: "utf8",
  env: {
    ...process.env,
    NODE_ENV: "production",
    JWT_SECRET: "",
    NODE_COMPAT_JWT_SECRET: "",
    CORS_ALLOWED_ORIGINS: "https://structify.cn",
    SMTP_HOST: "",
    SMTP_USER: "",
    SMTP_PASS: "",
    SMTP_FROM: "",
    DB_PATH: path.join(os.tmpdir(), "ds-agent-production-config-never-created.db")
  }
});

(async () => {
  assert.notEqual(result.status, 0, "production must fail closed without a Node compatibility JWT secret");
  const output = `${result.stdout || ""}\n${result.stderr || ""}`;
  assert.match(output, /NODE_COMPAT_JWT_SECRET is required in production/);
  assert.doesNotMatch(output, /(?:api[_ -]?key|password|secret)\s*[:=]\s*\S+/i);
  await verifyOptionalServicesDoNotBlockStartup();
  verifyOptionalDeploymentContract();
  verifyHostCaddyPreflight();
  verifyHostCaddyExecuteGate();
  verifyDatabaseRecoveryDryRun();
  console.log("production-config-ok jwt-required=1 optional-services-nonblocking=1 host-caddy-preflight=1 host-caddy-execute-gate=1 database-recovery-dry-run=1 no-secret-output=1");
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
