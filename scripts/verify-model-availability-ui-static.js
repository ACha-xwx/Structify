const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..", "frontend");
// The legacy page only: frontend/index.html is the built Vue application, so the two are no longer
// byte-identical and only the page that is still served needs its states checked.
const files = ["prototype.html"];
const contents = files.map((file) => fs.readFileSync(path.join(root, file), "utf8"));

function expectMatch(html, pattern, message) {
  assert.ok(pattern.test(html), message);
}

for (const [index, html] of contents.entries()) {
  const file = files[index];
  expectMatch(html, /id="homeAgentStatus"[^>]*aria-live="polite"/, `${file} should expose an announced model status`);
  expectMatch(html, /id="homeModelStatus"/, `${file} should expose a model status label`);
  expectMatch(html, /function syncHomeModelAvailability\(\)/, `${file} should synchronize model availability from healthz`);
  expectMatch(html, /fetch\("\/healthz", \{ cache: "no-store" \}\)/, `${file} should read the live health state without cached status`);
  expectMatch(html, /模型未配置/, `${file} should name the unconfigured-model state`);
  expectMatch(html, /服务状态未知/, `${file} should name the unavailable-health state`);
  expectMatch(html, /setHomeModelAvailability\("模型未配置", "需配置模型服务", "unconfigured"\)/, `${file} should expose an unconfigured state for styling`);
}

console.log("model-availability-ui-static-ok entries=1 states=3 health=live");
