<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { auth } from "../../app/providers/runtime";
import brandIconSprite from "../../assets/brand-icons.svg";
import runtimePoster from "../../assets/ai-runtime-poster.webp";
import AiRuntimeFrame, { type AiRuntimeMenuItem } from "../components/AiRuntimeFrame.vue";
import ThemeToggle from "../design/ThemeToggle.vue";
import { useLocale } from "../i18n/locale";

const { locale } = useLocale();
const isEnglish = computed(() => locale.value === "en-US");
const copy = computed(() => isEnglish.value ? {
  brand: "Structify home",
  nav: ["Home", "Product", "Case Studies", "Contact"],
  trust: "Structured data-structure learning",
  title: ["Intelligence", "Designed To Evolve"],
  summary: "Trace structures, compare operations, and keep course material, code practice, and questions in one learning workspace.",
  cta: "Get Started",
  account: auth.state.user ? "Account" : "Sign in",
  metricsNote: "These are AI Runtime visual-contract preview values, not production telemetry.",
  metrics: [
    { label: "Inference Time", detail: "AI Runtime visual-contract preview value; not a production latency claim." },
    { label: "Platform Uptime", detail: "AI Runtime visual-contract preview value; not a production availability claim." },
    { label: "Autonomous Runtime", detail: "AI Runtime visual-contract preview value; not a production service status." },
    { label: "Context Windows", detail: "AI Runtime visual-contract preview value; not a production capacity claim." },
  ],
} : {
  brand: "Structify 首页",
  nav: ["首页", "产品", "案例", "联系"],
  trust: "面向数据结构的智能学习",
  title: ["让智能持续进化", "把结构真正学会"],
  summary: "沿着结构变化学习，比较操作过程，把课件、代码练习和问题追问放进同一张学习工作台。",
  cta: "开始学习",
  account: auth.state.user ? "账户" : "登录",
  metricsNote: "以下是 AI Runtime 视觉合同中的预览值，不代表生产统计或个人学习记录。",
  metrics: [
    { label: "推理耗时", detail: "AI Runtime 视觉合同预览值，不代表生产延迟指标。" },
    { label: "平台可用性", detail: "AI Runtime 视觉合同预览值，不代表生产可用性指标。" },
    { label: "自主运行", detail: "AI Runtime 视觉合同预览值，不代表生产服务状态。" },
    { label: "上下文窗口", detail: "AI Runtime 视觉合同预览值，不代表生产容量指标。" },
  ],
});

const menuItems = computed<AiRuntimeMenuItem[]>(() => [
  { id: "home", label: copy.value.nav[0], href: "/", active: true },
  { id: "product", label: copy.value.nav[1], href: "/user/home" },
  { id: "case-studies", label: copy.value.nav[2], href: "/user/animation?chapterId=sequential-list&from=home" },
  { id: "contact", label: copy.value.nav[3], href: "/user/knowledge" },
]);

const accountHref = computed(() => auth.state.user ? "/user/profile" : "/login");
const accountLabel = computed(() => copy.value.account);

type RuntimeMetric = {
  glyph: string;
  target: number;
  suffix: string;
  decimals: number;
  minimumDigits?: number;
  label: string;
  detail: string;
  to: string;
};

// These values are part of the AI Runtime visual contract. The accessible
// disclosure above keeps them from being mistaken for production telemetry.
const runtimeMetrics = computed<RuntimeMetric[]>(() => [
  { glyph: "<", target: 120, suffix: "ms", decimals: 0, ...copy.value.metrics[0], to: "/user/home" },
  { glyph: "%", target: 99.99, suffix: "%", decimals: 2, ...copy.value.metrics[1], to: "/user/animation?chapterId=sequential-list&from=home" },
  { glyph: "*", target: 24, suffix: "/7", decimals: 0, ...copy.value.metrics[2], to: "/user/presentation?lessonId=01-01A&chapterId=sequential-list&from=home" },
  { glyph: "#", target: 2.4, suffix: "M", decimals: 1, ...copy.value.metrics[3], to: "/user/code?chapterId=sequential-list&from=home" },
]);

const capabilitiesRoot = ref<HTMLElement | null>(null);
const pixelHeadline = ref<HTMLCanvasElement | null>(null);
const capabilityValues = ref<number[]>(runtimeMetrics.value.map(() => 0));
let metricObserver: IntersectionObserver | null = null;
let metricAnimationFrame: number | null = null;
let redrawPixelHeadline: (() => void) | null = null;

function drawChinesePixelHeadline() {
  const canvas = pixelHeadline.value;
  if (!canvas || isEnglish.value) return;
  if (typeof navigator !== "undefined" && /jsdom/i.test(navigator.userAgent)) return;
  const width = Math.max(canvas.parentElement?.clientWidth ?? 320, 240);
  const cssHeight = Math.min(Math.max(width * 0.19, 78), 168);
  // Paint at the display's native pixel density. The previous implementation
  // deliberately rendered at half resolution and then enlarged the bitmap,
  // which made the Chinese glyphs look soft even with a pixel font loaded.
  const pixelRatio = Math.min(Math.max(window.devicePixelRatio || 1, 1), 3);
  canvas.width = Math.round(width * pixelRatio);
  canvas.height = Math.round(cssHeight * pixelRatio);
  canvas.style.height = `${cssHeight}px`;
  let context: CanvasRenderingContext2D | null = null;
  try {
    context = canvas.getContext("2d");
  } catch {
    // jsdom and restricted webviews can omit canvas; the semantic h1 remains.
    return;
  }
  if (!context) return;
  context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
  context.clearRect(0, 0, width, cssHeight);
  context.imageSmoothingEnabled = false;
  const fontSize = Math.min(80, Math.max(34, width * 0.088));
  context.fillStyle = "#ffffff";
  context.textAlign = "center";
  context.textBaseline = "top";
  context.font = `400 ${fontSize}px "Fusion Pixel CJK", "Fusion Pixel 12px Proportional SC", monospace`;
  const lineHeight = fontSize * 1.08;
  copy.value.title.forEach((line, index) => context.fillText(line, width / 2, index * lineHeight));
}

function formatRuntimeMetric(metric: RuntimeMetric, index: number): string {
  const value = capabilityValues.value[index] ?? 0;
  const formatted = metric.decimals > 0
    ? value.toFixed(metric.decimals)
    : String(Math.round(value)).padStart(metric.minimumDigits ?? 1, "0");
  return `${formatted}${metric.suffix}`;
}

function completeCapabilityCounts() {
  capabilityValues.value = runtimeMetrics.value.map((metric) => metric.target);
}

function animateCapabilityCounts() {
  if (metricAnimationFrame !== null) return;
  const startedAt = performance.now();
  const update = (now: number) => {
    capabilityValues.value = runtimeMetrics.value.map((metric, index) => {
      const delay = 480 + index * 90;
      const duration = 1500 + index * 80;
      const progress = Math.min(Math.max((now - startedAt - delay) / duration, 0), 1);
      const eased = 1 - ((1 - progress) ** 3);
      return metric.target * eased;
    });
    if (runtimeMetrics.value.some((metric, index) => (capabilityValues.value[index] ?? 0) < metric.target)) metricAnimationFrame = window.requestAnimationFrame(update);
    else {
      metricAnimationFrame = null;
      completeCapabilityCounts();
    }
  };
  metricAnimationFrame = window.requestAnimationFrame(update);
}

onMounted(() => {
  drawChinesePixelHeadline();
  redrawPixelHeadline = () => drawChinesePixelHeadline();
  window.addEventListener("resize", redrawPixelHeadline);
  if (document.fonts?.load) void document.fonts.load('80px "Fusion Pixel CJK"').then(redrawPixelHeadline);
  if (document.fonts?.ready) void document.fonts.ready.then(redrawPixelHeadline);
  const prefersReducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
  if (prefersReducedMotion || typeof IntersectionObserver === "undefined" || typeof window.requestAnimationFrame !== "function") {
    completeCapabilityCounts();
    return;
  }

  metricObserver = new IntersectionObserver((entries) => {
    if (!entries.some((entry) => entry.isIntersecting)) return;
    metricObserver?.disconnect();
    metricObserver = null;
    animateCapabilityCounts();
  }, { threshold: 0.25 });
  if (capabilitiesRoot.value) metricObserver.observe(capabilitiesRoot.value);
  else completeCapabilityCounts();
});

watch(locale, () => requestAnimationFrame(drawChinesePixelHeadline));

onBeforeUnmount(() => {
  metricObserver?.disconnect();
  if (redrawPixelHeadline) window.removeEventListener("resize", redrawPixelHeadline);
  redrawPixelHeadline = null;
  if (metricAnimationFrame !== null) window.cancelAnimationFrame(metricAnimationFrame);
});
</script>

<template>
  <AiRuntimeFrame
    class="home-runtime"
    mode="landing"
    :menu-items="menuItems"
    :brand-label="copy.brand"
    :sign-in-href="accountHref"
    :sign-in-label="accountLabel"
    :video-poster="runtimePoster"
  >
    <template #actions>
      <ThemeToggle />
      <RouterLink class="ai-runtime-frame__sign-in" :to="accountHref">{{ accountLabel }}</RouterLink>
    </template>

    <template #nav>
      <RouterLink v-for="item in menuItems" :key="item.id" :to="item.href || '/'" :class="{ 'is-active': item.active }" :aria-current="item.active ? 'page' : undefined">{{ item.label }}</RouterLink>
    </template>

    <template #mobile-nav>
      <RouterLink v-for="item in menuItems" :key="item.id" :to="item.href || '/'" :class="{ 'is-active': item.active }" :aria-current="item.active ? 'page' : undefined">{{ item.label }}</RouterLink>
    </template>

    <template #hero>
      <section class="runtime-landing" aria-labelledby="runtime-landing-title">
        <div class="runtime-landing__trust-row runtime-landing__reveal" style="--d: .05s" aria-label="Structured data-structure learning">
          <span class="runtime-landing__ring runtime-landing__ring--one"><span><svg class="runtime-landing__brand-icon runtime-landing__brand-icon--microsoft" viewBox="0 0 24 24" aria-hidden="true"><use :href="`${brandIconSprite}#brand-microsoft`" /></svg></span></span>
          <span class="runtime-landing__ring runtime-landing__ring--two"><span><svg class="runtime-landing__brand-icon runtime-landing__brand-icon--amazon" viewBox="0 0 24 24" aria-hidden="true"><use :href="`${brandIconSprite}#brand-amazon`" /></svg></span></span>
          <span class="runtime-landing__ring runtime-landing__ring--three"><span><svg class="runtime-landing__brand-icon runtime-landing__brand-icon--google" viewBox="0 0 24 24" aria-hidden="true"><use :href="`${brandIconSprite}#brand-google`" /></svg></span></span>
          <span class="runtime-landing__trust-copy">{{ copy.trust }}</span>
        </div>

        <h1 v-if="isEnglish" id="runtime-landing-title" class="runtime-landing__headline">
          <span>{{ copy.title[0] }}</span>
          <span>{{ copy.title[1] }}</span>
        </h1>
        <template v-else>
          <h1 id="runtime-landing-title" class="runtime-sr-only">{{ copy.title.join(" / ") }}</h1>
          <canvas ref="pixelHeadline" class="runtime-landing__pixel-headline" role="img" :aria-label="copy.title.join(' / ')" />
        </template>

        <p class="runtime-landing__summary runtime-landing__reveal" style="--d: .28s">
          {{ copy.summary }}
        </p>

        <RouterLink class="runtime-landing__cta runtime-landing__reveal runtime-landing__pulse" style="--d: .4s" to="/user/home">
          {{ copy.cta }}
        </RouterLink>
      </section>
    </template>

    <template #footer>
      <nav ref="capabilitiesRoot" class="runtime-capabilities" aria-label="Runtime metrics" aria-describedby="runtime-metrics-note">
        <span id="runtime-metrics-note" class="runtime-sr-only">{{ copy.metricsNote }}</span>
        <RouterLink
          v-for="(metric, index) in runtimeMetrics"
          :key="metric.label"
          class="runtime-capability runtime-landing__reveal"
          :style="{ '--d': `${.5 + index * .08}s` }"
          :to="metric.to"
          :aria-label="`${metric.label}: ${formatRuntimeMetric(metric, index)}`"
          :title="metric.detail"
        >
          <span class="runtime-capability__glyph" aria-hidden="true">{{ metric.glyph }}</span>
          <span class="runtime-capability__copy"><strong>{{ formatRuntimeMetric(metric, index) }}</strong><small>{{ metric.label }}</small></span>
        </RouterLink>
      </nav>
    </template>
  </AiRuntimeFrame>
</template>

<style scoped>
.runtime-landing {
  display: grid;
  width: min(900px, 100%);
  margin: auto;
  justify-items: center;
  padding: clamp(24px, 5vh, 56px) 0 clamp(20px, 3vh, 34px);
  color: #ffffff;
  text-align: center;
}

.runtime-landing__trust-row {
  --trust-size: clamp(36px, 4.5vw, 42px);
  display: inline-flex;
  min-height: var(--trust-size);
  align-items: center;
  margin-bottom: clamp(16px, 2.5vh, 26px);
}

.runtime-landing__ring {
  position: relative;
  z-index: 1;
  display: grid;
  width: var(--trust-size);
  height: var(--trust-size);
  place-items: center;
  padding: 5px;
  border: 1px solid rgba(255, 255, 255, .4);
  border-radius: 50%;
  background: #28282a;
  color: #111111;
  transition: transform .35s ease;
}

.runtime-landing__ring + .runtime-landing__ring { margin-left: calc(var(--trust-size) * -.42); }
.runtime-landing__ring--one { z-index: 1; }
.runtime-landing__ring--two { z-index: 2; }
.runtime-landing__ring--three { z-index: 4; }
.runtime-landing__ring > span {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  border-radius: 50%;
  background: #ffffff;
  font-family: var(--font-mono);
  font-size: calc(var(--trust-size) * .34);
  font-weight: 700;
  line-height: 1;
}

.runtime-landing__brand-icon {
  display: block;
  width: calc(var(--trust-size) * .34);
  height: calc(var(--trust-size) * .34);
  color: currentColor;
}

@media (hover: hover) and (pointer: fine) {
  .runtime-landing__ring--one:hover { transform: translateY(-2px); }
  .runtime-landing__ring--two:hover { transform: translateY(-4px); }
  .runtime-landing__ring--three:hover { transform: translateY(-2px); }
}

.runtime-landing__trust-copy {
  z-index: 3;
  display: inline-flex;
  min-height: var(--trust-size);
  align-items: center;
  margin-left: calc(var(--trust-size) * -.42);
  padding: 0 clamp(14px, 2vw, 18px) 0 calc(var(--trust-size) * .58);
  border: 1px solid rgba(255, 255, 255, .4);
  border-radius: 999px;
  background: #28282a;
  color: #c4c2c3;
  font-family: var(--ai-runtime-font-sans);
  font-size: clamp(12px, 1.4vw, 13.5px);
  font-weight: 500;
  white-space: nowrap;
}

.runtime-landing__headline {
  display: grid;
  width: 100%;
  margin: 0;
  color: #ffffff;
  font-family: "Fusion Pixel CJK", "Fusion Pixel 12px Proportional SC", "Geist Pixel Circle", monospace;
  font-size: clamp(28px, 6.2vw, 80px);
  font-weight: 400;
  letter-spacing: 0;
  line-height: 1.12;
}

.runtime-landing__pixel-headline {
  display: block;
  width: 100%;
  height: clamp(78px, 12.5vw, 168px);
  image-rendering: pixelated;
}

.runtime-landing__headline span {
  overflow: hidden;
  animation: runtime-headline-fade .85s cubic-bezier(.22, 1, .36, 1) both;
  white-space: nowrap;
}

.runtime-landing__headline span:nth-child(1) { animation-delay: .12s; }
.runtime-landing__headline span:nth-child(2) { animation-delay: .3s; }

.runtime-landing__summary {
  width: min(500px, 92%);
  margin: clamp(16px, 2.4vh, 22px) 0 0;
  color: #d0d0d0;
  font-family: var(--ai-runtime-font-sans);
  font-size: clamp(calc(13.5px + 2pt), calc(1.55vw + 2pt), calc(16.5px + 2pt));
  font-weight: 400;
  line-height: 1.55;
  opacity: .8;
}

.runtime-landing__cta {
  display: inline-grid;
  min-height: 46px;
  margin-top: clamp(18px, 3vh, 28px);
  place-items: center;
  padding: clamp(11px, 1.6vh, 13px) clamp(22px, 3vw, 28px);
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .15), 0 0 22px rgba(255, 255, 255, .32), 0 0 44px rgba(255, 255, 255, .12);
  color: #000000;
  font-family: var(--ai-runtime-font-sans);
  font-size: clamp(13.5px, 1.5vw, 14.5px);
  font-weight: 600;
  text-decoration: none;
  transition: transform 150ms ease, box-shadow 150ms ease;
}

@media (hover: hover) and (pointer: fine) {
  .runtime-landing__cta:hover {
    box-shadow: 0 0 0 1px rgba(255, 255, 255, .26), 0 0 28px rgba(255, 255, 255, .46), 0 0 58px rgba(255, 255, 255, .18);
    transform: translateY(-2px) scale(1.02);
  }
}

.runtime-landing__cta:active { transform: scale(.97); }
.runtime-landing__cta.runtime-landing__pulse { animation: runtime-reveal-pulse .9s cubic-bezier(.22, 1, .36, 1) both; animation-delay: var(--d, 0s); }

.runtime-capabilities {
  display: grid;
  width: min(920px, 100%);
  margin: 0 auto;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  color: #ffffff;
}

.runtime-capability {
  display: grid;
  min-width: 0;
  grid-template-columns: auto minmax(0, 1fr);
  gap: clamp(9px, 1.15vw, 13px);
  align-items: center;
  padding: 0 clamp(10px, 2vw, 22px);
  border: 0;
  color: inherit;
  text-align: left;
  text-decoration: none;
  transition: opacity 150ms ease, transform 150ms ease;
}

.runtime-capability + .runtime-capability { border-left: 1px solid rgba(255, 255, 255, .24); }
.runtime-capability__glyph {
  color: #ffffff;
  font-family: "Geist Pixel Circle", monospace;
  font-size: clamp(22px, 3vw, 33px);
  line-height: 1;
}
.runtime-capability__copy { display: grid; min-width: 0; gap: 2px; }
.runtime-capability__copy strong { min-width: 0; color: #ffffff; font-family: "Geist Pixel Circle", monospace; font-size: clamp(18px, 2.2vw, 26px); font-variant-numeric: tabular-nums; font-weight: 400; letter-spacing: 0; line-height: 1; }
.runtime-capability__copy small { overflow: hidden; color: #8e8e8e; font-family: var(--ai-runtime-font-sans); font-size: clamp(11px, 1.2vw, 12.5px); line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.runtime-sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); clip-path: inset(50%); white-space: nowrap; }

@media (hover: hover) and (pointer: fine) {
  .runtime-capability:hover { opacity: .74; transform: translateY(-2px); }
}
.runtime-capability:active { transform: scale(.97); }

.runtime-landing__reveal {
  animation: runtime-reveal .85s cubic-bezier(.22, 1, .36, 1) both;
  animation-delay: var(--d, 0s);
}

@keyframes runtime-headline-fade {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes runtime-reveal {
  from { opacity: 0; filter: blur(6px); transform: translateY(22px) scale(.98); }
  to { opacity: 1; filter: blur(0); transform: translateY(0) scale(1); }
}

@keyframes runtime-reveal-pulse {
  0% { opacity: 0; box-shadow: 0 0 0 1px rgba(255, 255, 255, .02), 0 0 0 rgba(255, 255, 255, 0); transform: translateY(22px) scale(.98); }
  72% { opacity: 1; box-shadow: 0 0 0 1px rgba(255, 255, 255, .22), 0 0 30px rgba(255, 255, 255, .42), 0 0 58px rgba(255, 255, 255, .18); transform: translateY(0) scale(1.025); }
  100% { opacity: 1; box-shadow: 0 0 0 1px rgba(255, 255, 255, .15), 0 0 22px rgba(255, 255, 255, .32), 0 0 44px rgba(255, 255, 255, .12); transform: translateY(0) scale(1); }
}

@media (max-width: 720px) {
  .runtime-landing { padding-top: clamp(20px, 5vh, 34px); padding-bottom: 18px; }
  .runtime-landing__headline { letter-spacing: 0; line-height: 1.05; }
  .runtime-capabilities { grid-template-columns: repeat(2, minmax(0, 1fr)); row-gap: 18px; }
  .runtime-capability { padding: 0 clamp(8px, 3vw, 16px); }
  .runtime-capability:nth-child(3) { border-left: 0; }
}

@media (max-width: 420px) {
  .runtime-landing__trust-row { --trust-size: 34px; margin-bottom: 16px; }
  .runtime-landing__trust-copy { max-width: 205px; overflow: hidden; text-overflow: ellipsis; }
  .runtime-landing__headline { font-size: clamp(28px, 10.8vw, 42px); letter-spacing: 0; line-height: 1.04; }
  .runtime-landing__summary { font-size: 15px; }
  .runtime-capability__copy small { white-space: normal; }
}

@media (max-height: 700px) and (max-width: 720px) {
  .runtime-landing { padding-top: 12px; padding-bottom: 12px; }
  .runtime-landing__trust-row { margin-bottom: 12px; }
  .runtime-landing__summary { margin-top: 12px; }
  .runtime-landing__cta { margin-top: 14px; }
  .runtime-capabilities { row-gap: 10px; }
}

@media (prefers-reduced-motion: reduce) {
  .runtime-landing__headline span,
  .runtime-landing__reveal { animation: none; }
}
</style>
