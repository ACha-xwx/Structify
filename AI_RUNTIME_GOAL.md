# AI Runtime 学习产品视觉与结构 Goal

状态：active  
建立日期：2026-08-26  
适用范围：Vue 3 学习产品前端（根首页、学习工作台、所有 `/user/*` 学习页）

> 2026-09-05 本轮执行目标（统一二级学习页外壳、消除章节双侧栏并完成浏览器/源码门禁）已完成；本文件继续保持 `active`，作为后续 iOS 重构和功能迭代的长期产品合同。

## Goal

把 AI Runtime 作为整个学生端的唯一视觉合同和产品外壳。学习工作台要让用户完成真实任务：发现课程范围、选择章节、理解当前目标、操作算法舞台、打开课件/代码/问答/课堂/资料。AI Runtime 的视觉语言融入这些功能，不把学习台嵌成另一个 iframe，也不把 Forecast 与 Signal 当成两个并列主题。

课程目录不是学习进度统计。当前本地内容地图明确包含 6 个主题、10 个章节、29 个教学单元；只有已有教学场景的单元才可以显示可播放算法预览，其余必须诚实地作为课程目录或待接入内容。

## Source Contract (完整保留自 `C:\Users\ACha_\Desktop\AI Runtime.txt`)

Rebuild this as a **single-viewport, full-bleed video-background landing page** using static **HTML + CSS + vanilla JS** (no framework). Match the current implementation exactly. File structure:

```
index.html
styles.css
main.js
assets/logo.webp
fonts/GeistPixel-Circle.woff2
```

Document title: `Intelligence Designed To Evolve`.  
Body: black `#000`, `overflow: hidden`, height `100vh` / `100dvh`, Inter for UI, retro dot-matrix display font for headline + stat symbols. Antialiased text.

### Exact background video (required)

Full-viewport cover video behind all UI (`position: absolute; inset: 0; object-fit: cover; pointer-events: none; z-index: 0`).

```html
<video class="bg-video" autoplay muted loop playsinline>
  <source
    src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260809_012548_ef22562c-c0ae-4816-ad9d-f8922af4e6a7.mp4"
    type="video/mp4"
  />
</video>
```

Use this **exact CloudFront URL**. Parent `.bg` is black `#000`, absolute inset 0, `overflow: hidden`.

### Fonts (exact)

**1. Inter** (UI) via Google Fonts: weights `400`, `500`, `600`  
`https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&display=swap`  
Stack: `"Inter", "Segoe UI", system-ui, sans-serif`

**2. BubbledotICG-FinePos** (primary display — retro dot-matrix) via OnlineWebFonts CDN — **do not use local Bubbledot files**:

```html
<link
  href="https://db.onlinewebfonts.com/c/8cb707a9b8a73f8a7403336b861c3074?family=BubbledotICG-FinePos"
  rel="stylesheet"
/>
```

Family name exactly: `"BubbledotICG-FinePos"`

**3. Geist Pixel Circle** (fallback display only) local `@font-face`:

- `fonts/GeistPixel-Circle.woff2`
- weight 400, `font-display: swap`
- Display stack: `"BubbledotICG-FinePos", "Geist Pixel Circle", monospace`

**4. Font Awesome 6.5.2** (enterprise brand icons) from cdnjs:

```
https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css
```

integrity:  
`sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==`

### CSS variables (exact)

```css
--bg: #000000;
--text: #ffffff;
--muted: #8e8e8e;
--nav-text: #2e2e2e;
--pill-dark: #28282a;
--sign-in-text: #c8c8c8;
--nav-shadow: 0 4px 14px rgba(0, 0, 0, 0.16);
--trust-bg: #28282a;
--trust-border: rgba(255, 255, 255, 0.4);
--trust-text: #c4c2c3;
--font-sans: "Inter", "Segoe UI", system-ui, sans-serif;
--font-display: "BubbledotICG-FinePos", "Geist Pixel Circle", monospace;
```

### Layout composition (one viewport, 3 vertical regions)

`.page`: flex column, centered, padding `clamp(16px, 2.4vh, 28px) clamp(14px, 3vw, 32px)`, height `100vh`/`100dvh`, overflow hidden.

1. **Header** (top, shrink 0)  
2. **Hero** (flex 1, centered)  
3. **Stats footer** (bottom, shrink 0)

Header / hero / stats / mobile menu: `z-index: 1` above video.

### Header (desktop)

Centered row, max-width `720px`, gap `clamp(18px, 2.8vw, 28px)`.

#### Logo

- Circular button `clamp(40px, 4.4vw, 46px)`, `border-radius: 50%`
- **White background `#fff`**
- Soft shadow `--nav-shadow`
- Image: `assets/logo.webp` (alt empty; width/height attrs 52)
- Icon **inside** scaled to **72%** width/height, `object-fit: contain`, centered with CSS grid (circle size unchanged)
- Hover: `scale(1.04)`

#### Nav pill (white)

- White `#fff` pill, height `clamp(44px, 5.2vw, 48px)`, max-width `430px`, flex 1, padding `4px 8px`, radius 999, same soft shadow
- Links: **Home** (active), **Product**, **Case Studies**, **Contact**
- Inter 500, size `clamp(13px, 1.4vw, 15px)`, letter-spacing `-0.01em`, color `#2e2e2e`
- Default opacity `0.5`; hover `0.75`; active `1`
- Active indicator: three 3×3px black dots under label via `::after` + box-shadow offsets `-5px` / `+5px`, bottom `5px`

#### Sign in

- Dark pill `#28282a`, text `#c8c8c8`, same height as nav, radius 999, soft shadow
- Hover: bg `#323234`, text `#fff`, `translateY(-1px)`

#### Entrance animation

Header: `slideDown 0.7s cubic-bezier(0.22, 1, 0.36, 1) both`  
(from opacity 0, `translateY(-18px)` → settled)

### Hero (center)

Column, text-center, max-width `900px`.

#### Trust row

“Trusted by 2000+ Enterprises” uses an inline flex row; `--trust-size: clamp(36px, 4.5vw, 42px)` (34px at ≤420px), margin-bottom `clamp(16px, 2.5vh, 26px)`, stagger delay `--d: 0.05s`.

Three overlapping avatar rings are dark padded rings with small white inner circles, not full solid disks:

- outer ring size `--trust-size`, bg `#28282a`, border `1px solid rgba(255,255,255,0.4)`, padding `5px`
- inner circle fills padded area, `border-radius: 50%`, bg `#fff`
- black Font Awesome Microsoft, Amazon and Google icons, `font-size: calc(var(--trust-size) * 0.34)`
- later avatars use `margin-left: calc(var(--trust-size) * -0.42)` and z-index `1 / 2 / 4`
- hover lift: avatar 1 `-2px`, avatar 2 `-4px`, avatar 3 `-2px` over `0.35s`

The trust pill overlaps the last avatar, uses the same dark background/border/radius, left margin `-0.42 * trust-size`, left padding `0.58 * trust-size`, and Inter 500 text `#c4c2c3` at `clamp(12px, 1.4vw, 13.5px)` (12px mobile).

#### Headline

Exact two lines:

```
Intelligence
Designed To Evolve
```

- BubbledotICG-FinePos / Geist Pixel Circle fallback
- solid white, no gradient, shimmer, or LED scan
- size `clamp(28px, 6.2vw, 80px)`
- letter spacing `-0.04em` desktop, `-0.08em` ≤720px, `-0.09em` ≤420px
- line height 1.12 (1.05 / 1.04 at smaller breakpoints)
- `white-space: nowrap`, overflow hidden
- per-line fade from opacity 0 / `translateY(14px)` with `headlineFade 0.85s cubic-bezier(.22,1,.36,1)`, delays `0.12s` and `0.3s`

#### Subhead

```text
Build applications that reason, adapt and collaborate using a modular
AI platform designed for production.
```

Max width `min(500px, 92%)`; `clamp(calc(13.5px + 2pt), calc(1.55vw + 2pt), calc(16.5px + 2pt))`; color `#d0d0d0`, opacity `0.8`, line height 1.55, weight 400, delay `--d: 0.28s`.

#### CTA

Text `Get Started`; white pill, black text, Inter 600, size `clamp(13.5px, 1.5vw, 14.5px)`; padding `clamp(11px, 1.6vh, 13px) clamp(22px, 3vw, 28px)`, radius 999; soft white glow:

```css
0 0 0 1px rgba(255,255,255,0.15),
0 0 22px rgba(255,255,255,0.32),
0 0 44px rgba(255,255,255,0.12)
```

Hover `translateY(-2px) scale(1.02)` with stronger glow. Entrance uses `revealPulse`, delay `--d: 0.4s`.

### Stats footer

Grid 4 columns (2×2 on ≤720px), max width `920px`. Each metric is icon → counting value → muted label.

| Icon glyph | Target | Suffix | Decimals | Label |
|---|---:|---|---:|---|
| `<` | 120 | `ms` | 0 | Inference Time |
| `%` | 99.99 | `%` | 2 | Platform Uptime |
| `*` | 24 | `/7` | 0 | Autonomous Runtime |
| `#` | 2.4 | `M` | 1 | Context Windows |

Icon uses BubbledotICG-FinePos at `clamp(22px, 3vw, 33px)`; value uses Inter, white, `clamp(18px, 2.2vw, 26px)`, tabular numerals; label `#8e8e8e`, `clamp(11px, 1.2vw, 12.5px)`. Stagger delays are `0.5s`, `0.58s`, `0.66s`, `0.74s`. Count-up uses easeOutCubic, duration `1500 + i*80`ms, start offset `480 + i*90`ms, once via IntersectionObserver threshold `0.25`.

### Shared entrance animation

`.anim` starts at opacity 0, `translateY(22px) scale(0.98)`, `blur(6px)` and animates with `reveal 0.85s cubic-bezier(.22,1,.36,1) forwards`, delay from inline `--d`.

`prefers-reduced-motion: reduce` kills animations and shows the final state; headline stays solid white.

### Mobile (≤720px)

- hide desktop nav and desktop Sign in
- header uses space-between, logo 48×48 left and circular burger 48×48 right (`#28282a`, 3 white 18×1.5px bars)
- burger open: white circle, bars become black X using translate/rotate
- fixed full-screen overlay `rgba(0,0,0,0.62)`, blur 6px, `overlayIn 0.28s`
- white sheet menu below header, radius 28px, padding `22px 18px 20px`, shadow `0 20px 60px rgba(0,0,0,0.45)`, `menuIn 0.38s`
- links Home / Product / Case Studies / Contact plus full-width Sign in, staggered `linkIn`, active three-dot indicator
- toggle `aria-expanded`, `hidden`, `body.menu-open`; close on overlay, Escape, link click and resize above 720px
- stats become 2 columns
- ≤420px headline/trust and ≤700px height spacing receive the specified tightening

### Visual / interaction constraints

- no hero cards; one composition; real circular brand mark
- no gradient animation on headline; solid white only
- display type comes from OnlineWebFonts BubbledotICG-FinePos, not local Bubbledot files
- trust logos remain small white inner circles inside dark padded rings
- nav/logo shadow is only `0 4px 14px rgba(0,0,0,0.16)`
- first viewport includes header, trust, headline, subhead, CTA and stats over the looping CloudFront video

### Implementation stack in this product

The source contract is expressed in Vue 3 rather than copied as a second HTML app. `AiRuntimeFrame.vue` owns the full-bleed video, header capsule, mobile menu, focus/Escape behavior, theme and reduced-preference handling. Product views render into its page slot. The CloudFront URL and verified logo asset remain the source media; no visual filter is used to hide color problems.

## Product mapping

| AI Runtime contract | Vue 3 responsibility | Learning meaning |
|---|---|---|
| full-bleed video + scrim | `AiRuntimeFrame.vue` / `ai-runtime-frame.css` | stable product atmosphere behind learning content |
| centered nav capsule | `AiRuntimeFrame` + `UserFrame` | 学习台、课程、实验室、资料库 unified navigation |
| single current CTA rhythm | `LearningWorkbenchView.vue` | 当前目标只有一个主行动：继续学习 |
| display font + Inter UI | `pixel-font.css`, `tokens.css` | English/numbers preserve Runtime display hierarchy; Chinese uses glyph fallback |
| mobile burger/X, Escape, focus | `AiRuntimeFrame` and `UserFrame` | same navigation behavior across `/` and `/user/*` |
| black algorithm scene | `algorithm-stage` / `/user/animation` | algorithm playback is a learning task surface, not a second branded skin |
| muted paper light mode | `--wb-*` and theme bridge | all learning surfaces switch with the same light/dark toggle |

## Learning workbench structure

1. **课程地图 rail**：显示 6 个主题、10 章、29 个单元；主题索引是可点击入口，当前主题展开章节与单元；内置独立滚动，不截断地图。
2. **当前学习上下文**：中央标题、摘要和当前目标；游客看到明确的“本地预览”，认证态只显示 API 返回的真实章节上下文。
3. **任务舞台**：只有存在可渲染场景时显示播放、暂停、上一步、下一步、重置、步骤说明和完整舞台入口；其它章节显示可用内容/待接入边界，不伪装成已有算法记录。
4. **上下文 rail**：当前步骤、来源、下一步；每块使用 `details` 渐进披露，避免把所有信息堆进首页。
5. **直接学习工具**：课件预览与 C 编译器始终在学习台侧栏可见；问答、课堂、舞台、资料通过统一上下文链接进入。

## Fixture / API boundary

- `frontend/src/user/fixtures/learning-workbench.ts` 是内容目录和独立教学预览，不代表用户进度、完成率、在线人数或生产状态。
- `frontend/src/user/adapters/learning-workbench.ts` 是唯一切换边界：游客使用 fixture；认证态优先调用章节、进度和 readiness API。
- 认证 API 没有 `scene` 时，工作台必须进入章节上下文状态，不把顺序表 fixture 冒充为该用户的任务。
- 未来服务端应提供 `currentGoal`、`renderableScene`、`capabilities`、来源/步骤/代码输出和资源权限；接入时只替换 adapter，不重写 Runtime 外壳。
- 模型问答、保存个人记录、受限资源等计费/身份动作只能在原位提示登录，不强制跳转登录页。

## Acceptance gates

- `/` 不是空 Hero；`/user/home` 是可操作学习工作台。
- 所有 `/user/*` 共享 AI Runtime 外壳，不能出现独立 Forecast 或 Signal 页面层。
- 课程地图可见并可点击 6 个主题、10 个章节、29 个单元；当前单元 active pip 唯一。
- 课件、C 编译器、问答、课堂、资料、算法舞台均有直接入口，且带正确 `chapterId`/`lessonId` 上下文。
- 游客浏览不被登录页拦截；模型/个人记录动作原位提示身份边界。
- 运行全量测试、`npm run typecheck`、`npm run build`、`git diff --check`。
- 浏览器截图验收 1440×900、1280×800、768×1024、390×844；检查无文字重叠、横向溢出、焦点丢失、大块空白。
- 检查 `prefers-reduced-motion` 与 `prefers-reduced-transparency`；不使用绿色残留、色相/灰阶滤镜或虚构统计。

## Current evidence

- 已有 6 主题 / 10 章 / 29 单元 fixture 与认证 adapter。
- 已有四视口工作台截图和完整算法舞台截图；视频资源已验证可解码播放。
- 本轮新增：课程主题索引改为可点击、默认展开、保留唯一 active 主题；工作台 `data-shell` 归一为 `runtime`。
- 待完成：本轮目标文件落盘后的完整测试、构建、入口 smoke 与四视口新截图。
