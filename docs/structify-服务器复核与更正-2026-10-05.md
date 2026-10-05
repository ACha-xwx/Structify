# 服务器复核与上一轮的两处更正（2026-10-05 17:20）

> 你让我再看服务器——看完了。结论是：**服务器上跑的还是 06:32 那版代码（11 小时内没有新镜像、新发布、新文件）**，
> 但**我上一轮的判定有两处不够准确**，这里更正。核对方式全部只读。

---

## 一、两处更正（我上轮说轻了 / 说偏了）

### 更正 1：课堂/课件的**读取链他确实改了**，我说"没有应用级缓存层"只对了一半

| 我上轮的说法 | 实际情况 |
|---|---|
| "全前端没有应用级缓存" | ❌ 不准确。**元数据有应用级缓存**：`shared/courseware/courseware-catalog.ts`（+58 行）= 内存缓存，**60s TTL、最多 16 个 deck、登录身份一变就整体丢弃**（私有课件不落持久存储）。**图片**才是靠浏览器 HTTP 缓存（内容寻址 URL） |
| "webp / 预取 / 内容寻址本来就有，属沿用" | ⚠️ 机制是老的，但**他改了预取行为**：`prefetch-slides.ts` **+16 行** —— ① `image.fetchPriority = "low"`（不跟当前显示的那张抢带宽）② `image.onerror` 时把 URL 从"已预热"集合里删掉（允许重试）③ 新增 `scheduleSlidePrefetch()`：用 `requestIdleCallback`（降级 120ms 定时器）调度，并**返回取消函数**，离开页面即取消 |

另外新增 `shared/courseware/use-slide-paging.ts`（+26）、`deck-labels.ts`（+50）、`classroom/demo-courseware.ts`（+57）。

### 更正 2：课堂**前端**他改了很多，我说"只改了 UI"不完整

`ClassroomView.vue` **+528 行**、`SlidePanel.vue` **+214 行**。具体改动（从 diff 里读出来的）：

- 布局拆成**两态**：有会话时 `classroom--split`（左讲右课件）、**无会话时 `classroom--courseware`（纯课件浏览）**；`showSlides` 从条件显示改成**常显**；
- 与课件页**共享"选中课件"**（同一个 `localStorage` 键 `structify.courseware.selected`）；
- **DEV 环境下**没有课件/课件未就绪时，回落到**本地演示课件**（`demo-courseware.ts`，`import.meta.env.DEV` 才开，生产不生效）；
- 按钮换成银边框 `LiquidMetalButton`。

**但有一条经干净 diff 复核仍然成立**：`backend/.../classroom/` 包**他 0 改动**——
`git diff 10d70ee HEAD -- backend/spring/src/main/java/com/feng/dsagent/classroom/` 输出为空。
也就是说：**课堂的"表现层"他重做了，"备课的教学质量层"他完全没碰。**

---

## 二、本轮在服务器上核实到的新事实

| 项 | 实测 | 说明 |
|---|---|---|
| 代码是否变过 | `docker ps` / `docker images` / `releases/` 都是 **06:32 的 v2.0.0**；`find /srv/structify -newermt '2026-10-05 06:35'` **无结果** | 11 小时内没换过版本 |
| Caddy 缓存规则 | 线上 `/srv/structify/caddy/Caddyfile` 与仓库 `deployment/Caddyfile.production` **逐字一致（0 行 diff）** | 没在边缘加缓存/改规则 |
| webp 实测（`rendered/` 实况） | **1210 张**：min 10KB / **中位 44KB** / 均值 **46KB** / max 154KB；PNG 均值 106KB | 他说的"单张 30–70KB"**属实** |
| 生产 MySQL 知识库 | **536 片，全部 VERIFIED，57 个课时文件** | 与本地一致 |
| `/healthz` 里的 670 | 那是 **Node 侧**读的**另一套语料**（`/srv/structify/private/knowledge`：59 个 md，目录时间 2026-08-10，含 `lessons/ raw/ source_normalized/`） | **"670 vs 536 之谜"解开**：Spring 用核验教材（536），Node 用旧语料（670），两套并存——不是他这次动的 |
| 数据库有没有被改 | 今天 06:00 后：`knowledge_chunks` 0、`admin_audit_events` 0、`users` 0 | 无管理级改动 |
| **今天有没有人在用** | 今天新增：**课堂脚本 2 份**（12:57「绪论：数据结构的基础概念」23 步、10:10「6.3 二叉树的遍历与线索化：非递归遍历」67 步）、课堂事件 17 条、聊天消息 2 条 | 有人真的在用（大概是你或他） |

---

## 三、干净 diff 后的权威清单（他的树 vs 我们最后的共同状态 `10d70ee`）

**后端 51 文件，+1738 / -137**，按包：

| 包 | 文件 | 说明 |
|---|---:|---|
| `chat` | **19** | 附件存储新类（`ChatAttachment` / `ChatAttachmentStorage` +118 / `ChatAttachmentFile` / `StoredChatAttachment` / `PendingChat` / `ChatRetry`）、`ChatService` +137/-30、`JdbcChatRepository` **+362/-45**、`ChatController` +50/-6 |
| `model` | 6 | 新增 `ModelPayload` **+48**、reasoning 贯通、`OpenAiCompatibleModelClient` +8/-12 |
| `modelconfig` | 4 | 思考预算/生成控制接线 |
| `knowledge` | 2 | 新增「本地开发种子」可发布标签（+10/-4、+8/-4） |
| `aiquota` | 1 | +8（推理 token 计量） |
| **`classroom`** | **0** | **未动** |

**部署 7 文件**：`docker-compose.production.yml`（`MODEL_MAX_RESPONSE_BYTES` 1 MiB→**32 MiB**、新增 `chat-attachments` 卷与 `CHAT_ATTACHMENTS_DIR`）、`Dockerfile.node`（COPY 代码库数据）、`Dockerfile.node.dockerignore`（+8）、`backup.sh`/`restore.sh`（备份恢复附件）、`.env.spring.example`。

**前端**：`user/components` 10、`shared/components` 10、`shared/views` 9、`shared/courseware` **7**、`assets/*` 14、`shared/compiler` 3…… 共 **新增 73 个文件、改 54 个**。

---

## 四、对你的 10 条问题意味着什么（结论不变）

他动的是三块：**课堂表现层（前端）**、**课件读取与缓存层**、**聊天（前后端 + 5 条迁移）**。
他**没有动**"备课的教学质量层"（`classroom/` 包、提示词、契约校验）。

⇒ 你的第 **2/6 条**（左边讲的内容和 PPT 对不上）**依然是原样**，根因三层（课件数据 96 页标题被污染 + `shouldShow` 只标 31 页 / 提示只给被污染的标题 / 只校验 `slideRefs` 不校验语义）一处未改。**批次 1 仍是第一优先，且要落在 `v2-baseline` 上。**

（上一轮我把"课堂前端大改"和"课堂后端没动"混在一句话里说，容易读成"课堂他没改"——这次的表述以本节为准。）
