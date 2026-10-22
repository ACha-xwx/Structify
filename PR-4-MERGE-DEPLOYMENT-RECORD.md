# PR #4 合并与部署记录

日期：2026-10-22（Asia/Shanghai）

## 目标

将 [Structify PR #4](https://github.com/ACha-xwx/Structify/pull/4) 合并到 `master`，并按 `PRODUCTION_DEPLOYMENT_GUIDE.md` 发布合并后的源码。

## PR 基线

- 标题：feat: 新增学习大纲审阅流程与多资源课程编排
- 状态：open；base：`ACha-xwx/Structify:master`；head：`SingularityVirgo/Structify:master`
- PR head：`79331bb240e262af0a9b931d8086c1e662a1ce7a`
- 当前 master：`541c971cab54003acdc945a79e533bc7b82c59f7`
- 变更规模：18 个提交、264 个文件、16,565 行新增、305 行删除
- GitHub 元数据报告 `mergeable=true`、`mergeable_state=unstable`；没有 check-runs 或 commit statuses。PR 描述中的测试结果需独立核验。
- 本地缺少 `gh` CLI；GitHub API 可读取 PR 信息，仓库 remote fetch 可读取 PR head。

## 执行记录

尚未合并或部署。后续在本文件持续记录审查、测试、合并 commit、发布包 SHA-256、备份、preflight、health-check、公网 smoke 和 active-release。

## 2026-10-22 检查点

本次检查在隔离 worktree `C:\Users\ACha_\Documents\GitHub\Structify-pr4` 中进行；主工作区保持在 `master`，除本专属记录文件外没有未提交改动。此处按用户要求暂停，尚未合并 PR、push 或部署。

### 已完成的审查与验证

- 前端审查修正了学习入口指向 `/begin`、切换课堂时清空未发送的问题草稿，以及已取消的异步课堂请求重新挂回旧订阅的问题，并补充了相应回归覆盖。
- 前端定向测试通过 7 项；全量 Vitest 通过 92 个文件、700 项；生产构建与类型检查通过。构建有大 chunk 警告。
- 契约检查通过 39 个案例、37 个路径；验证器自身测试通过 6 项。
- Maven 后端测试完成：624 项，0 失败、0 错误、1 项跳过，`BUILD SUCCESS`。测试运行在 Java 25.0.1；CI 配置使用 Java 21，仍需在目标 Java 版本环境复核。
- 第二轮后端审查未发现额外问题；独立审查提出以下两项待处理风险：
  - `OpenAiCompatibleModelClient` 读取 `finish_reason`，但未将 `length` 作为截断失败处理；流式路径遇到 `[DONE]` 即结束。长回答因此可能被保存为已完成。
  - `ClassroomEventSubscriptions` 对每个 SSE 订阅单独启动轮询，间隔 250 ms、最长 5 分钟，当前没有并发订阅上限。

### 暂停时状态

上述两项风险尚未修复或补测，因此后端审查门禁尚未完成。PR 仍为 open；没有 GitHub 合并 SHA，也没有创建发布包、备份或执行任何部署步骤。恢复后应先处理风险并重跑必要门禁，再继续合并与部署；部署前还需按部署指南核实线上 active-release、备份、版本标签和发布包。

## 继续执行记录

- GitHub API 确认当前账号对本仓库有 push 权限，`master` 无分支保护；PR #4 的 head 在外部仓库 `SingularityVirgo/Structify`，当前账号没有该 fork 的 push 权限。PR 仍为 open，head 为 `79331bb240e262af0a9b931d8086c1e662a1ce7a`，GitHub 报告可合并且未配置检查项。
- 生产主机只读预检成功：SSH 主机为 `VM-0-11-ubuntu`，sudo 与 Docker Compose 5.3.1 可用；当前 `active-release` 为 `v2.1.3-animation-type-field`，远端已有最近两份备份目录 `20261017T154108Z` 和 `20261017T152700Z`。
- DPAPI SSH 凭据文件已存在。仅密钥认证的连接失败，随后通过部署上传脚本既有的凭据读取入口建立 SSH 会话；密码没有读取到终端输出或写入记录。
- `finish_reason=length` 修复已覆盖通用 OpenAI 兼容客户端的定向测试，21 项通过；配额 API 集成测试 10 项通过。固定模型客户端仍需同样处理，正在补齐后再运行 Java 21 全量门禁。
- SSE 订阅全局及单用户上限和回收逻辑正在隔离 worktree 中实现与验证；尚未合并、push 或部署。

### 恢复后的验证进度

- Java 21.0.4 全量 Maven 测试通过：631 项，0 失败、0 错误、1 项跳过；包括通用和固定模型的截断处理、配额结算、SSE 限流测试。
- 前端 Vitest 全量通过：92 个文件、700 项；`npm run build` 的类型检查和生产构建通过；`verify-frontend-build-artifact.js` 通过，产物源哈希为 `5aa571c59e9fe69ad17b32dcda949e14a21aae57faf47b125ac0fe0528539000`。
- API 契约检查通过 39 个案例、37 个路径；`verify-studyroom.test.mjs` 通过 6 项。
- CI 浏览器回归仍未通过：10 项均在登录测试夹具时失败。独立启动同一 harness 后，直连 Spring 和经 Vite 代理登录都返回成功；原因尚待确认，先暂停合并。
- 本机未安装 Docker，根目录 `npm test` 的 `verify-production-config.js` 因找不到 `docker` 命令失败；其他串行静态检查已通过。GitHub CI 与生产主机 Docker 构建尚未运行。

### 2026-10-22 继续执行

- 用户要求继续合并及部署。复核 PR #4 仍为 open，head/base SHA 与记录一致；远端 `master` 仍为 `541c971cab54003acdc945a79e533bc7b82c59f7`。
- GitHub 将 fork PR 的首次 `Verify` 标记为 `action_required` 且未创建 job。使用已认证的仓库权限批准 workflow 后，run `39743734080` 的第 2 次尝试已启动；前端测试/类型/构建、原型回归、Spring 后端测试均已成功，Node 镜像及浏览器 harness 当时仍在运行。
- 在系统 Chrome 重跑完整浏览器套件为 10/10 通过；后端直连和 Vite 代理的测试登录均为 HTTP 200。结论是此前登录失败未复现，现有证据指向瞬态测试环境问题。临时诊断断言已移除。
- PR 后续修正仍未提交或合并；需待 GitHub CI 与只读代码复核完成，再按 PR head SHA 保护合并，并将修正提交到 `master`。

### GitHub 浏览器 CI 结果

- run `39743734080` 最终状态为 failure。原型回归、前端测试/类型/构建、Spring 测试与打包、Node 镜像构建均通过；仅 `Studyroom browser harness` 失败。
- 失败位于 `frontend/e2e/studyroom-classroom-experience.spec.ts` 的长课堂滚动保持用例：记录的解释面板 `scrollTop` 为 `0`，关闭课堂问答后观测到 `320`。Artifact ID：`12418937510`。
- 本机系统 Chrome 的完整浏览器套件此前为 10/10 通过；正在核对 Ubuntu headless Chromium 的事件时序与产品行为，尚未合并。修正后须重跑此浏览器 job 与 master push CI。

### master CI 复验

- master follow-up commit `8273652cf780909393a3fc6d138f5a7ee7b1074c` 的前端、原型及 Node 镜像任务通过；Java 21 全量测试 631 项中有 1 项失败：`ResourceApiIntegrationTest.filtersPublishedResourcesByLicenseScopeAndRole`，学生资源列表预期 2 条、收到 3 条；其余 630 项通过、1 项跳过。
- 同一 Spring job 在 PR merge commit `1d8f378cfcee36aad093621472841ea6889c98e8` 上通过；本机 Java 25 单独执行 `ResourceApiIntegrationTest` 7/7 通过。为确认偶发性，已重跑 master run `39764514052` 的失败 job（run attempt 2），并同步检查测试隔离；结果待补录。
- 目前未因该单项测试失败而启动生产备份、上传或发布；发布包仍来自 `8273652cf780909393a3fc6d138f5a7ee7b1074c`，SHA-256 为 `53d7b1dc6fd7a646daa61c695f50cba5f8f998445eae2a0860403e9f42818b5e`，上传 dry-run 通过。

### 2026-10-22 PR 合并与生产部署完成

- PR #4 已合并到 `master`，合并提交为 `1d8f378cfcee36aad093621472841ea6889c98e8`。为修复 CI 暴露的测试隔离问题，随后提交 `c864ca6ab39202793825f459f81ae8e715a9ddfe`；本地 `master` 与 `origin/master` 当前均指向该提交，最新 master CI 全绿。
- 最终 Java 21 后端测试通过 631 项、跳过 1 项；Chrome 浏览器 E2E 通过 10/10。发布镜像内前端测试通过 696 项、跳过 4 项，类型检查和 Vite 生产构建通过。
- 正式发布标签为 `v2.1.4-pr4-structify-r1`。发布包 SHA-256 为 `661b0cab7c4b7c387f26785a175d6530a066461cc342c6065461447fa4fb57a2`，服务器校验与本地一致。
- 停止旧服务前生成完整备份 `/var/backups/structify/20261022T105653Z`（760 MiB），校验通过。发布流程另生成迁移前备份 `/var/backups/structify/20261022T110947Z`；`chat-attachments.tar.gz`、`manifest.txt`、`mysql.sql`、`node-pdfs.tar.gz`、`node.sqlite`、`private.tar.gz` 的 SHA256SUMS 全部通过。备份保留策略移除了过期的 `20261017T154108Z`。
- 新镜像已启动并健康：Node 镜像 ID 为 `sha256:84491b7d78aee1cee53524a89a381ae0f86e6f1a87eb319d52f4212dbae12f55`，Spring 镜像 ID 为 `sha256:ab29ca7cc3a1cb961b1a5789443ff7965ab7b0841b14d80ba1c13bc10e4ba7e8`。`last-release.env` 与两个运行容器记录的镜像标签、镜像 ID 一致；Node/Spring 仅绑定回环端口，MySQL 健康且未暴露公网端口，Caddy 保持运行。
- 发布脚本回环健康检查通过；随后独立执行的 `health-check.sh --execute` 和面向 `https://structify.cn`、`https://admin.structify.cn` 的公网 `smoke.sh --execute` 均通过。DNS 未修改，旧应用镜像 `v2.1.3-animation-type-field` 保留用于应用回滚。

### 2026-10-22 课程问答页面拆分与后续部署

- 按后续需求恢复旧版课程问答到 `/chat`；合并后新版课程大纲和课堂流程统一保留在 `/studyroom` 及子路由，学习入口进入 `/studyroom`，移除 `/chat-legacy` 路由且不在界面提供旧版 Chat 入口。
- 代码提交 `81311d202d40b122e3488cf02f1e345dfe220d17` 已推送到 GitHub `master`；`master` 与 `origin/master` 一致。
- 正式部署标签为 `v2.1.5-chat-studyroom-r1`。发布包 SHA-256：`313348b17ec9c8be52d23cefb023c8ed7e289a1481f4975e2428bbe256829f55`。
- 发布镜像中的前端测试通过 92 个文件（91 通过、1 跳过）、700 项用例（696 通过、4 跳过）；类型检查和 Vite 生产构建通过。构建存在既有的大 chunk 警告。
- 停止旧服务前备份 `/var/backups/structify/20261022T115828Z`（760 MiB）已通过六项 SHA-256 校验。发布流程的新备份为 `/var/backups/structify/20261022T120416Z`，`chat-attachments.tar.gz`、`manifest.txt`、`mysql.sql`、`node-pdfs.tar.gz`、`node.sqlite`、`private.tar.gz` 六项校验全部通过。
- 正式 `release.sh` 成功，独立 `health-check.sh --execute` 和面向 `https://structify.cn`、`https://admin.structify.cn` 的公网 `smoke.sh --execute` 均通过。公网检查未登录、未调用模型且未执行数据写操作。
- 最终 `active-release`、`last-release.env` 与实际 Node/Spring 容器镜像均为 `v2.1.5-chat-studyroom-r1`；Node healthy、Spring 已启动、MySQL healthy，应用只绑定回环端口，Caddy 保持运行。`v2.1.4-pr4-structify-r1` 仍保留作为上一发布版本。
- CodeGraph 索引同步完成，975 个文件的索引状态为 up to date。
