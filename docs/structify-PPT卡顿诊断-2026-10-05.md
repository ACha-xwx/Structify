# 课堂 PPT 卡顿诊断（2026-10-05）

> 现象：**刚进课堂、还没备课，选课/课件那一屏的 PPT 载入极其卡顿；本地不卡，只有服务器卡。**
> 方法：按 `structify-perf-triage` 把一次请求拆成「源站 / 边缘 / 回源 / 模型」四段来量。全部只读。

---

## 一、结论一句话

**不是应用的问题，是"跨境到欧洲 CF 节点 + 冷缓存"**：
同一张图，**源站回环 4.5 ms**，**经域名首字节 0.7–13.6 秒**。本地开发走 `127.0.0.1` 所以感觉不到，服务器上要绕 Cloudflare 的欧洲节点再回源到中国。

---

## 二、实测数字（全部在服务器上量，同一台机器，可直接复核）

| 请求 | 源站回环（127.0.0.1:18792） | 经 `https://structify.cn`（Cloudflare） |
|---|---:|---:|
| `GET /api/v1/classroom/lessons` | **74 ms** | **4.77 s**（`cf-cache-status: DYNAMIC`） |
| `GET /api/v1/presentation/lessons/{id}/slides` | **13 ms** | **2.36 s**（`DYNAMIC`） |
| 一张幻灯片图（WebP, 43.7 KB） | **4.5 ms** | **0.73 s（HIT）** / **1.7–5.8 s（MISS）** |
| `index.html`（616 B） | — | **3.12 s**（HTML 是 `no-cache`，每次都要回源） |
| `/assets/index-*.js`（216 KB） | — | **3.83 s（MISS）** |
| `/assets/index-*.css`（43 KB） | — | **13.56 s（MISS）** |

**TCP 握手到 CF ≈ 0.20–0.25 s**（IPv4 与 IPv6 一样，偶发 1.2 s）；**`cf-ray` 显示服务器出口落在 `LHR` / `AMS`（伦敦/阿姆斯特丹），我本机落在 `LAX`**。

⇒ 冷启动一屏课堂 ≈ `3.1 + 3.8 + 13.6` 秒才把应用跑起来，再 `4.8 + 2.4` 秒拿数据，再等图 —— **这就是"极其卡顿"**。

---

## 三、三个放大因素（解释了"为什么现在特别明显"）

1. **CF 是"按节点分别缓存"的**：同一张图我在 LAX 是 `MISS`、服务器在 AMS 是 `HIT(age=19h)`。所以**在一个地方预热，只暖了那一个节点**。
2. **新版本换了构建文件名**（`index-D8oFopZm.js` / `index-C2HnXMbh.css`）：`immutable 1y` 只对"同一个文件名"有效，换了名字就是全新对象 ⇒ 他这次发布之后，**所有静态资源对所有节点都是冷的**（CSS 13.6 s 就是这么来的）。
3. **HTML 是 `no-cache`**：每次打开站点都要跨境回源一次（3 s 起）。这是当初为了"发布立刻生效"有意设的。

---

## 四、修复选项（按性价比）

| # | 做法 | 收益 | 代价/前提 |
|---|---|---|---|
| 1 | **预热**：把 1210 张图 + 新构建的 JS/CSS 走一遍公网域名 | 图片 MISS 1.7–5.8 s → HIT ≈ 0.7 s；JS/CSS 从 3.8/13.6 s → ≈1 s | **只暖到出口经过的那个节点**。我正在做（后台跑，从服务器发起） |
| 2 | **CF 开 Tiered Cache**（上层缓存） | 补上"按节点各自冷"这个洞：预热一次，其它节点也能受益；MISS 时不用一路回到中国 | 在 CF 面板改设置，**不动代码、不改 URL** ⇒ 将来备案换大陆 CDN 时可以直接关掉，不违背"备完案不能是另一套" |
| 3 | **把 HTML 的 `no-cache` 放宽到 30–60 s** | 每次打开省 3 s | 改 Caddy；代价是"发布后最多 1 分钟才生效"，需要你拍板 |
| 4 | **备案 + 大陆节点/直连**（根治） | 接口 4.8 s → 几十毫秒 | 需要 ICP 备案；**这是唯一能真正解决"接口慢"的办法**（认证接口不可能靠 CDN 缓存） |
| 5 | 代码侧微调（收益有限，诚实说）：无会话时**完全不做邻近预取**；给首屏图片 `fetchpriority=high` | 少 5 个并发请求 | 他这版已经在"当前图 load 之后、用 idle 回调、`fetchpriority=low`"预取了，所以**这一项不是主因** |

---

## 五、需要你拍板 / 需要你操作的

1. **CF 面板**：确认/打开 **Tiered Cache**（我没 CF 权限，只能建议）——这是第 2 项，收益最大且可逆。
2. **要不要把 HTML 缓存放宽到 30–60 s**（第 3 项）？
3. **备案进度**：接口那 4.8 s 只有备案能治；要不要我把这件事写成一页材料推给学校/云厂商？

---

## 六、复核方式（任何人可复跑）

```bash
# 源站（服务器上）
curl -sS -o /dev/null -w 'source %{time_starttransfer}s\n' http://127.0.0.1:18792/api/v1/classroom/lessons?chapterId=01-introduction

# 公网（服务器上）
curl -sS -o /dev/null -D - -w 'public %{time_starttransfer}s\n' https://structify.cn/api/v1/classroom/lessons?chapterId=01-introduction

# 图片：把 catalogue 返回的 imageUrl 拿来测（签名不能本地推导）
curl -sS -o /dev/null -D - -w 'img %{time_starttransfer}s\n' "https://structify.cn$IMAGE_URL" | grep -iE 'cf-cache-status|age'
```

判据（来自技能）：**源站几十毫秒、公网秒级 ⇒ 不是应用问题**；`HIT` 明显快于 `MISS` ⇒ 差值就是回源成本。
