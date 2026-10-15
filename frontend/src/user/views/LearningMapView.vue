<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import BrandStage from "../../shared/components/BrandStage.vue";
import { useI18n } from "../../shared/i18n/locale";
import { MAP_SIZE, MAP_SPECS, type MapNodeSpec } from "../../learning/mapTaxonomy";
import type { LearningMap, LearningMapNode, LearningMapSignals } from "../../shared/types/contracts";
import { userApi } from "../runtime";

/**
 * The course drawn the way the course team drew it, with the learner's own mastery painted on it.
 *
 * <p>The whole map does not fit as one expanded tree: the course has far more lessons than the picture has
 * nodes, and showing them all at once buries the nodes under each other. So the map opens like a folder:
 * clicking a chapter makes it the root of the view and shows what is inside it, and a button in the corner
 * goes back up a level. Every chapter can therefore be opened, and nothing ever overlaps.
 */
const { t } = useI18n();
const map = ref<LearningMap | null>(null);
const loading = ref(true);
const error = ref("");
/** Which node the view is standing in. The whole picture is the view at "root". */
const focus = ref("root");
/** Where back leads, deepest last. */
const trail = ref<string[]>([]);
const NODE_RADIUS = 23;
/** The overview fits one screen: the root, and the chapters on a gentle arc a fixed row apart. */
const OVERVIEW_SIZE = { width: 1000, height: 660 };
const OVERVIEW_TOP = 108;
const OVERVIEW_ROW = 75;
const OVERVIEW_ROOT_X = 330;
const OVERVIEW_CHAPTER_X = 545;
/** The overview canvas: the root, the chapters fanned out, and the legend. */
const ROOT_SIZE = { width: 1140, height: 1000 };
const DRILL_X = 300;
const DRILL_CHILD_X = 780;
const DRILL_GAP = 60;

const source = computed(() => map.value?.nodes ?? []);
const specOf = (id: string) => MAP_SPECS.find((spec) => spec.id === id);
const childrenOf = (id: string) => MAP_SPECS.filter((spec) => spec.parentId === id);
const atRoot = computed(() => focus.value === "root");
const focusSpec = computed(() => specOf(focus.value));
const trailTitles = computed(() => trail.value.map((id) => specOf(id)?.title ?? id));
/** The path from the course down to where we are, without repeating the course itself. */
const crumbs = computed(() => atRoot.value
  ? [t("map.course")]
  : [t("map.course"), ...trailTitles.value.slice(1), focusSpec.value?.title ?? ""].filter(Boolean));

/** The reviewed lesson titles carry their chapter and a review note; a graph label only needs the topic. */
function shortTitle(title: string): string {
  const withoutNote = title.replace(/（已核验[^）]*）/g, "").replace(/^[^-]*-/, "").trim();
  return withoutNote.length > 16 ? `${withoutNote.slice(0, 16)}…` : withoutNote;
}

function average(values: number[]): number | null {
  return values.length ? Math.round((values.reduce((sum, value) => sum + value, 0) / values.length) * 1000) / 1000 : null;
}

function chaptersOf(spec: MapNodeSpec): LearningMapNode[] {
  return spec.chapters.map((id) => source.value.find((node) => node.id === id)).filter((node): node is LearningMapNode => Boolean(node));
}

/** The raw counts behind a node, summed over the chapters it merges. */
function signalsOf(spec: MapNodeSpec): LearningMapSignals {
  const fed = chaptersOf(spec);
  const keys = ["classroomSteps","classroomAnswered","classroomCorrect","classroomHints","classroomSkips","classroomAsks","animations","animationRepeats","chatSessions","chatQuestions","codeRuns","codePassed","codeFailed"] as const;
  const out = Object.fromEntries(keys.map((key) => [key, fed.reduce((sum, node) => sum + node.signals[key], 0)])) as unknown as LearningMapSignals;
  return out;
}

/** Each dimension's own score behind a node, averaged over the chapters it merges. */
function componentsOf(spec: MapNodeSpec) {
  const fed = spec.chapters.length > 0 ? chaptersOf(spec) : source.value.filter((node) => node.level === 1);
  const pick = (read: (node: LearningMapNode) => number | null) => {
    const values = fed.map(read).filter((value): value is number => value !== null);
    return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null;
  };
  return { classroom: pick((node) => node.components.classroom), animation: pick((node) => node.components.animation), code: pick((node) => node.components.code) };
}

/** The two lines behind a colour: what each dimension said, and what came out of them. */
function breakdown(spec: MapNodeSpec): string[] {
  const parts = componentsOf(spec);
  const percent = (value: number | null) => value === null ? null : `${Math.round(value * 100)}%`;
  const named: string[] = [];
  if (parts.classroom !== null) named.push(`课堂 ${percent(parts.classroom)}`);
  if (parts.code !== null) named.push(`代码 ${percent(parts.code)}`);
  if (parts.animation !== null) named.push(`动画 ${percent(parts.animation)}`);
  // What the chapter counted minus what reached a lesson: the part no lesson can be blamed for.
  // The lessons this node stands for, however the taxonomy reaches them (directly or through drawn nodes).
  const lessons = spec.chapters.length > 0
    ? lessonChildren(spec)
    : childrenOf(spec.id).flatMap((child) => lessonChildren(child));
  const loose = lessons.length > 0 && signalsOf(spec).classroomAnswered > 0
    && lessons.every((lesson) => lesson.mastery === null);
  return [
    loose ? t("map.unattributed") : "",
    named.length ? named.join("｜") : t("map.notStarted"),
    `合成 ${percent(masteryOf(spec)) ?? t("map.notStarted")}｜可信 ${Math.round(confidenceOf(spec) * 100)}%`,
  ];
}

/** How far the chapters behind a node can be trusted, averaged the same way as the score. */
function confidenceOf(spec: MapNodeSpec): number {
  const fed = spec.chapters.length > 0 ? chaptersOf(spec) : source.value.filter((node) => node.level === 1);
  if (fed.length === 0) return 0;
  return fed.reduce((sum, node) => sum + node.confidence, 0) / fed.length;
}

/** A topic node borrows the mastery of the lesson it names; a chapter node averages the chapters it merges. */
function masteryOf(spec: MapNodeSpec): number | null {
  if (spec.chapters.length > 0) return average(chaptersOf(spec).map((node) => node.mastery).filter((value): value is number => value !== null));
  if (spec.parentId === null) return average(source.value.filter((node) => node.level === 1).map((node) => node.mastery).filter((value): value is number => value !== null));
  for (const keyword of spec.lessons ?? []) {
    const hit = source.value.find((node) => node.level === 2 && node.title.includes(keyword));
    if (hit) return hit.mastery;
  }
  return null;
}

/** The lessons the courseware actually has for a node's chapters - what a folder of this chapter contains. */
function lessonChildren(spec: MapNodeSpec): { id: string; title: string; mastery: number | null }[] {
  const wanted = new Set(spec.chapters);
  return source.value
    .filter((node) => node.level === 2 && wanted.has(node.id.split(":")[0]))
    .map((node) => ({ id: node.id, title: shortTitle(node.title), mastery: node.mastery }));
}

interface Child {
  id: string;
  title: string;
  mastery: number | null;
  /** True when opening it leads somewhere: it has children of its own, or lessons inside it. */
  openable: boolean;
}

/** What stands inside the node we are in: the drawn nodes under it, or else the courseware's lessons. */
const children = computed<Child[]>(() => {
  const spec = focusSpec.value;
  if (!spec) return [];
  const curated = childrenOf(spec.id).map((child) => ({
    id: child.id,
    title: child.title,
    mastery: masteryOf(child),
    openable: childrenOf(child.id).length > 0 || lessonChildren(child).length > 0,
  }));
  if (curated.length > 0) return curated;
  return lessonChildren(spec).map((lesson) => ({ id: lesson.id, title: lesson.title, mastery: lesson.mastery, openable: false }));
});

/** The picture as drawn by the course team, at its own coordinates. */
/** The overview keeps to the course and its chapters: a chapter's own contents belong one level in. */
const overview = computed(() => {
  const chapters = MAP_SPECS.filter((spec) => spec.parentId === "root");
  const nodes = chapters.map((spec, index) => {
    const spread = chapters.length <= 1 ? 0 : (index / (chapters.length - 1)) * 2 - 1;
    return {
      spec,
      // Further from the ends, further right: the same arc the design draws, now without its branches.
      x: OVERVIEW_CHAPTER_X + (1 - Math.abs(spread)) * 130,
      y: OVERVIEW_TOP + index * OVERVIEW_ROW,
      mastery: masteryOf(spec),
      confidence: confidenceOf(spec),
      signals: signalsOf(spec),
      openable: childrenOf(spec.id).length > 0 || lessonChildren(spec).length > 0,
    };
  });
  const root = specOf("root") as MapNodeSpec;
  return {
    nodes,
    root: { spec: root, x: OVERVIEW_ROOT_X, y: OVERVIEW_TOP + ((chapters.length - 1) * OVERVIEW_ROW) / 2, mastery: masteryOf(root), confidence: confidenceOf(root), signals: signalsOf(root), openable: false },
  };
});
const drawn = computed(() => MAP_SPECS
  .filter((spec) => spec.parentId === null || spec.parentId === "root")
  .map((spec) => ({ spec, mastery: masteryOf(spec), openable: childrenOf(spec.id).length > 0 || lessonChildren(spec).length > 0 })));

/** The drilled view: what we are in, in the middle, with what is inside it in a column to the right. */
const drill = computed(() => {
  const height = Math.max(560, children.value.length * DRILL_GAP + 170);
  return {
    height,
    focusAt: { x: DRILL_X, y: height / 2 },
    children: children.value.map((child, index) => ({
      ...child,
      x: DRILL_CHILD_X,
      y: height / 2 + (index - (children.value.length - 1) / 2) * DRILL_GAP,
    })),
  };
});

function hue(mastery: number | null): number | null {
  return 120 * (1 - Math.max(0, Math.min(1, mastery === null ? 0 : mastery)));
}

function fill(mastery: number | null): string {
  // Nothing recorded reads as green (0), the same as the worst case: the map has to be readable at a glance.
  const value = hue(mastery === null ? 0 : mastery) ?? 0;
  return `hsl(${Math.round(value)} 62% 62%)`;
}

function stroke(mastery: number | null): string {
  const value = hue(mastery);
  return value === null ? "color-mix(in srgb, var(--text) 38%, transparent)" : `hsl(${Math.round(value)} 55% 40%)`;
}

/** A colour with little evidence behind it is drawn faint, so a guess cannot look like certainty. */
function opacity(confidence: number): number {
  return 0.32 + 0.68 * Math.max(0, Math.min(1, confidence));
}

/** The sentence behind a colour: the score, how far it can be trusted, and what it is made of. */
function explain(mastery: number | null, confidence: number, signals: LearningMapSignals): string {
  const parts: string[] = [];
  if (signals.classroomAnswered > 0) parts.push(`课堂作答 ${signals.classroomAnswered} 次（对 ${signals.classroomCorrect}）`);
  if (signals.animations > 0) parts.push(`动画 ${signals.animations} 次`);
  if (signals.codeRuns > 0) parts.push(`代码运行 ${signals.codeRuns} 次（成功 ${signals.codePassed}）`);
  if (signals.chatQuestions > 0) parts.push(`提问 ${signals.chatQuestions} 个`);
  const score = mastery === null ? t("map.notStarted") : `${Math.round(mastery * 100)}%`;
  return `掌握度 ${score}｜可信度 ${Math.round(confidence * 100)}%${parts.length ? "｜依据：" + parts.join("、") : ""}`;
}

function read(mastery: number | null): string {
  return `${Math.round((mastery ?? 0) * 100)}%`;
}

function open(id: string, openable: boolean) {
  if (!openable || !specOf(id)) return;
  trail.value = [...trail.value, focus.value];
  focus.value = id;
}

function back() {
  const steps = [...trail.value];
  focus.value = steps.pop() ?? "root";
  trail.value = steps;
}

/** ?demo=1 fills the map with made-up numbers so the display can be reviewed without model calls. */
const demo = new URLSearchParams(window.location.search).has("demo");

function withDemoData(loaded: LearningMap): LearningMap {
  let seed = 7;
  const next = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; };
  return {
    ...loaded,
    nodes: loaded.nodes.map((node) => {
      if (node.level === 0) return node;
      // Spread the colours across the whole scale so every state is visible in one picture.
      const value = node.level === 1 ? 0.25 + next() * 0.65 : (next() < 0.35 ? null : 0.2 + next() * 0.7);
      return { ...node, mastery: value === null ? null : Math.round(value * 1000) / 1000, confidence: value === null ? 0 : 0.35 + next() * 0.6, status: "ESTIMATED" as const };
    }),
  };
}

onMounted(async () => {
  try {
    const loaded = await userApi.getLearningMap();
    map.value = demo ? withDemoData(loaded) : loaded;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t("map.failed");
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <BrandStage wide>
    <section class="map" aria-labelledby="map-title">
      <header class="map__head">
        <div class="map__title-row">
          <h1 id="map-title" class="workbench-title">{{ t("map.title") }}</h1>
        </div>
        <RouterLink class="map__back-link" to="/user">{{ t("map.backToProfile") }}</RouterLink>
      </header>

      <p v-if="!loading && !error" class="map__where">
        {{ crumbs.join(" / ") }}{{ demo ? "（演示数据）" : "" }}
      </p>

      <p v-if="loading" class="map__status">{{ t("map.loading") }}</p>
      <p v-else-if="error" class="map__status" role="alert">{{ error }}</p>

      <template v-else>
        <!-- The whole course, as the design has it. -->
        <div v-if="atRoot" class="map__scroll">
          <svg class="map__canvas" :viewBox="`0 0 ${OVERVIEW_SIZE.width} ${OVERVIEW_SIZE.height}`" role="img" :aria-label="t('map.title')">
            <defs>
              <linearGradient id="map-legend" x1="0" y1="1" x2="0" y2="0">
                <stop offset="0%" stop-color="hsl(120 62% 62%)" />
                <stop offset="50%" stop-color="hsl(60 62% 62%)" />
                <stop offset="100%" stop-color="hsl(0 62% 62%)" />
              </linearGradient>
            </defs>

            <g class="map__self" :transform="`translate(52, ${overview.root.y - 62})`">
              <circle cx="46" cy="46" r="34" fill="#f0c9a8" />
              <path d="M12 46a34 34 0 0 1 68 0z" fill="#3d2b20" />
              <path d="M12 130c0-24 15-40 34-40s34 16 34 40z" fill="#e8592b" />
              <path d="M104 66L86 84l18 18" fill="none" stroke="#f5b21c" stroke-width="14" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M142 66l-18 18 18 18" fill="none" stroke="#f5b21c" stroke-width="14" stroke-linecap="round" stroke-linejoin="round" />
            </g>

            <g class="map__edges">
              <line
                v-for="node in overview.nodes"
                :key="`edge-${node.spec.id}`"
                :x1="overview.root.x + NODE_RADIUS"
                :y1="overview.root.y"
                :x2="node.x - NODE_RADIUS"
                :y2="node.y"
                stroke="color-mix(in srgb, var(--text) 30%, transparent)"
                stroke-width="2"
              />
            </g>

            <g class="map__nodes">
              <g :transform="`translate(${overview.root.x}, ${overview.root.y})`" role="img" :aria-label="`${overview.root.spec.title} ${read(overview.root.mastery)}`">
                <circle :r="NODE_RADIUS + 2" :fill="fill(overview.root.mastery)" :fill-opacity="opacity(overview.root.confidence)" :stroke="stroke(overview.root.mastery)" stroke-width="4" />
                <title>{{ explain(overview.root.mastery, overview.root.confidence, overview.root.signals) }}</title>
                <path d="M-9 0h18" stroke="#ffffff" stroke-width="4" stroke-linecap="round" />
                <text class="map__label" :y="-(NODE_RADIUS + 16)" text-anchor="middle">{{ overview.root.spec.title }}</text>
              </g>

              <g
                v-for="node in overview.nodes"
                :key="node.spec.id"
                class="map__node"
                :transform="`translate(${node.x}, ${node.y})`"
                role="button"
                tabindex="0"
                :aria-label="`${node.spec.title} ${read(node.mastery)}`"
                @click="open(node.spec.id, node.openable)"
                @keydown.enter.prevent="open(node.spec.id, node.openable)"
              >
                <circle :r="NODE_RADIUS + 15" fill="transparent" />
                <title>{{ explain(node.mastery, node.confidence, node.signals) }}</title>
                <circle :r="NODE_RADIUS" :fill="fill(node.mastery)" :fill-opacity="opacity(node.confidence)" :stroke="stroke(node.mastery)" :stroke-width="4" :stroke-dasharray="''" />
                <path v-if="node.openable" d="M-9 0h18M0 -9v18" stroke="#ffffff" stroke-width="4" stroke-linecap="round" />
                <text class="map__label" :y="-(NODE_RADIUS + 18)" text-anchor="middle">{{ node.spec.title }}</text>
              </g>
            </g>

            <g class="map__legend" :transform="`translate(${OVERVIEW_SIZE.width - 215}, 40)`">
              <text class="map__legend-title" x="-46" y="-16">{{ t('map.legendTitle') }}</text>
              <rect x="0" y="0" width="26" height="470" rx="7" fill="url(#map-legend)" />
              <text class="map__legend-tick" x="40" y="18">1（{{ t('map.mastered') }}）</text>
              <text class="map__legend-tick" x="40" y="110">0.8</text>
              <text class="map__legend-tick" x="40" y="204">0.6</text>
              <text class="map__legend-tick" x="40" y="298">0.4</text>
              <text class="map__legend-tick" x="40" y="392">0.2</text>
              <text class="map__legend-tick" x="40" y="472">0（{{ t('map.notMastered') }}）</text>
              <text class="map__legend-note" x="40" y="518">{{ t('map.noData') }}</text>
            </g>
          </svg>
        </div>

        <!-- Inside a chapter: it is the root here, and what it holds is beside it. -->
        <div v-else class="map__scroll">
          <svg class="map__canvas" :viewBox="`0 0 1100 ${drill.height}`" style="height: auto" role="img" :aria-label="focusSpec?.title ?? t('map.title')">
            <g class="map__edges">
              <template v-for="child in drill.children" :key="`edge-${child.id}`">
                <line :x1="drill.focusAt.x + NODE_RADIUS" :y1="drill.focusAt.y" :x2="child.x - NODE_RADIUS" :y2="child.y" stroke="color-mix(in srgb, var(--text) 32%, transparent)" stroke-width="1.8" stroke-dasharray="9 8" />
              </template>
            </g>

            <g class="map__nodes">
              <g
                class="map__node"
                :transform="`translate(${drill.focusAt.x}, ${drill.focusAt.y})`"
                role="button"
                tabindex="0"
                :aria-label="`${focusSpec?.title ?? ''} · ${t('map.up')}`"
                @click="back"
                @keydown.enter.prevent="back"
              >
                <circle :r="NODE_RADIUS + 3" :fill="fill(focusSpec ? masteryOf(focusSpec) : null)" :stroke="stroke(focusSpec ? masteryOf(focusSpec) : null)" :stroke-width="4" />
                <path d="M-9 0h18" stroke="#ffffff" stroke-width="4" stroke-linecap="round" />
                <text class="map__label" :y="-(NODE_RADIUS + 20)" text-anchor="middle">{{ focusSpec?.title }}</text>
                <text class="map__read" y="46" text-anchor="middle">{{ read(focusSpec ? masteryOf(focusSpec) : null) }}</text>
              </g>

              <g class="map__breakdown" :transform="`translate(40, 52)`">
                <text
                  v-for="(line, index) in (focusSpec ? breakdown(focusSpec) : [])"
                  :key="line"
                  class="map__breakdown-line"
                  x="0"
                  :y="index * 28"
                  text-anchor="start"
                >{{ line }}</text>
              </g>

              <template v-for="child in drill.children" :key="child.id">
                <g
                  class="map__node"
                  :class="{ 'map__node--leaf': !child.openable }"
                  :transform="`translate(${child.x}, ${child.y})`"
                  role="button"
                  tabindex="0"
                  :aria-label="`${child.title} ${read(child.mastery)}`"
                  @click="open(child.id, child.openable)"
                  @keydown.enter.prevent="open(child.id, child.openable)"
                >
                  <circle :r="NODE_RADIUS" :fill="fill(child.mastery)" :stroke="stroke(child.mastery)" :stroke-width="child.mastery === null ? 2.5 : 4" :stroke-dasharray="''" />
                  <path v-if="child.openable" d="M-9 0h18M0 -9v18" :stroke="child.mastery === null ? 'color-mix(in srgb, var(--text) 55%, transparent)' : '#ffffff'" stroke-width="4" stroke-linecap="round" />
                  <text class="map__label" :x="NODE_RADIUS + 16" y="7" text-anchor="start">{{ child.title }}</text>
                  <text class="map__child-read" :x="NODE_RADIUS + 16" y="32" text-anchor="start">{{ read(child.mastery) }}</text>
                </g>
              </template>
            </g>
          </svg>
        </div>
      </template>

      
    </section>
  </BrandStage>
</template>

<style scoped>
.map { display: grid; width: min(var(--workbench-width), 100%); margin: 0 auto; gap: 12px; }
.map__head { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; }
.map__title-row { display: flex; align-items: center; gap: 14px; }
.map__back-link { color: var(--text); font-size: 19px; }
.map__where { margin: 0; color: var(--text-muted); font-size: 19px; }
.map__status, .map__note { margin: 0; color: var(--text-muted); font-size: 19px; }
.map__scroll {
  overflow: auto;
  max-height: 74vh;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 26px;
  background: color-mix(in srgb, var(--surface) 55%, transparent);
}
/* The whole map is meant to be seen at once: it scales down to the viewport instead of scrolling. */
.map__canvas { display: block; width: 100%; height: min(70vh, 640px); }
.map__node { cursor: pointer; }
.map__node--leaf { cursor: default; }
.map__label { font-size: 19px; fill: var(--text); }
.map__read { font-size: 17px; fill: var(--text-muted); }
.map__breakdown-line { font-size: 19px; fill: var(--text-muted); }
.map__child-read { font-size: 19px; fill: var(--text-muted); }
.map__legend-title { font-size: 19px; fill: var(--text); }
.map__legend-tick { font-size: 19px; fill: var(--text-muted); }
.map__legend-note { font-size: 17px; fill: var(--text-muted); }
.map__node:focus-visible circle { outline: 3px solid var(--text); }
</style>
