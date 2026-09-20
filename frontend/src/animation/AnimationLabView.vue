<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AnimationPlayer from "./AnimationPlayer.vue";
import catalog from "./capability-catalog.json";
import BrandStage from "../shared/components/BrandStage.vue";
import type { AnimationCatalog, AnimationCatalogStructure, DsvpCapabilityArgument, DsvpSimulationResponse } from "../shared/types/animation";
import type { Chapter } from "../shared/types/course";
import { useI18n } from "../shared/i18n/locale";
import { userApi } from "../user/runtime";

/**
 * The animation lab: pick a structure and an operation, or describe what you want in a sentence.
 *
 * The split it demonstrates is the point. The pickers and the natural-language box both end up at the
 * same place - one DSVP request executed by the local deterministic engine. The model is only consulted
 * for the sentence path, and even then it only chooses a capability and its arguments; it never produces
 * a frame. Everything drawn below is computed locally.
 */
const capabilities = catalog as AnimationCatalog;
const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const chapters = ref<Chapter[]>([]);
const chapterId = ref("");
const structure = ref("");
const capabilityName = ref("");
const argumentText = ref<Record<string, string>>({});
const prompt = ref("");
const response = ref<DsvpSimulationResponse | null>(null);
const notice = ref("");
const error = ref("");
const busy = ref(false);
/** True when the engine had to fill in its canonical example because the source carried no values. */
const demoFallback = ref(false);
const observation = ref("");
const observationSaved = ref(false);

const structureOptions = computed<AnimationCatalogStructure[]>(() => {
  const chapter = chapterNumber(chapterId.value);
  if (!chapter) return capabilities.structures;
  const scoped = capabilities.structures.filter((item) => item.chapter === chapter);
  return scoped.length ? scoped : capabilities.structures;
});

const capabilityOptions = computed<DsvpCapabilityArgument[]>(
  () => structureOptions.value.find((item) => item.structure === structure.value)?.capabilities ?? [],
);

const selected = computed<DsvpCapabilityArgument | null>(
  () => capabilityOptions.value.find((item) => item.capability === capabilityName.value) ?? null,
);

/** Required arguments first, then the optional ones the canonical example actually supplies. */
const argumentFields = computed(() => {
  const capability = selected.value;
  if (!capability) return [];
  const names = [...capability.requiredArguments, ...capability.optionalArguments];
  return names.map((name) => ({
    name,
    required: capability.requiredArguments.includes(name),
    hint: hintFor(name, capability.demoArguments?.[name]),
  }));
});

/** The engine names traces after the raw operation ("教材排序演示：quick"); the learner picked it by its Chinese label.
 * The label is captured when the animation is *generated* — changing the dropdown afterwards must not
 * retitle an animation that is still on screen. */
const generatedTitle = ref("");
const playerDefinition = computed(() => {
  const data = response.value?.animationData ?? null;
  if (!data) return null;
  return { ...data, title: generatedTitle.value || data.title };
});
const canObserve = computed(() => Boolean(response.value?.animationRecordId));

function chapterNumber(id: string): number | null {
  const match = String(id ?? "").match(/^(\d{1,2})/);
  return match ? Number(match[1]) : null;
}

/** A one-word reminder of the expected shape, from the canonical example the engine would use. */
function hintFor(name: string, demo: unknown): string {
  if (Array.isArray(demo)) return Array.isArray(demo[0]) ? t("lab.hintNested") : t("lab.hintNumbers");
  if (typeof demo === "number") return t("lab.hintValue", { value: demo });
  if (typeof demo === "string") return t("lab.hintValue", { value: demo });
  if (demo && typeof demo === "object") return t("lab.hintJson");
  return name === "initialData" || name === "values" ? t("lab.hintNumbers") : "";
}

/** Comma-separated text becomes numbers when every entry parses as one; brackets mean nested arrays. */
function parseArgument(raw: string): unknown {
  const text = raw.trim();
  if (!text) return undefined;
  if (text.startsWith("[") || text.startsWith("{")) {
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }
  if (text.includes(",")) {
    const parts = text.split(",").map((part) => part.trim());
    return parts.every((part) => part !== "" && Number.isFinite(Number(part))) ? parts.map(Number) : parts;
  }
  return Number.isFinite(Number(text)) ? Number(text) : text;
}

function collectArguments(): Record<string, unknown> {
  const args: Record<string, unknown> = {};
  for (const field of argumentFields.value) {
    const value = parseArgument(argumentText.value[field.name] ?? "");
    if (value !== undefined) args[field.name] = value;
    // A blank field is left out on purpose: the engine then supplies its canonical example and says so.
  }
  return args;
}

function resetArguments() {
  const next: Record<string, string> = {};
  for (const field of argumentFields.value) {
    const demo = selected.value?.demoArguments?.[field.name];
    next[field.name] = Array.isArray(demo) ? JSON.stringify(demo) : demo === undefined || demo === null ? "" : String(demo);
  }
  argumentText.value = next;
}

function failureMessage(cause: unknown): string {
  return cause instanceof Error && cause.message ? cause.message : t("common.failed");
}

async function loadChapters() {
  try {
    chapters.value = await userApi.listChapters();
    const fromQuery = typeof route.query.chapterId === "string" ? route.query.chapterId : "";
    const matched = chapters.value.find((item) => item.id === fromQuery) ?? chapters.value[0];
    chapterId.value = matched?.id ?? "";
  } catch (cause) {
    error.value = failureMessage(cause);
  }
}

/** Runs one capability through the engine and draws the result. */
async function runDeterministic() {
  if (!selected.value || busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  observationSaved.value = false;
  try {
    const resolution = await userApi.planAnimation({
      capability: selected.value.capability,
      arguments: collectArguments(),
      sourceRef: chapterTitle(),
    });
    if (resolution.status !== "ready" || !resolution.toolRequest) {
      demoFallback.value = false;
      notice.value = resolution.missingArguments.length
        ? t("lab.missingArguments", { names: resolution.missingArguments.join(t("common.listSeparator")) })
        : resolution.error || t("lab.cannotGenerate");
      return;
    }
    demoFallback.value = resolution.toolRequest.demoFallback;
    generatedTitle.value = selected.value.label;
    response.value = await userApi.simulateAnimation(resolution.toolRequest.request);
  } catch (cause) {
    error.value = failureMessage(cause);
  } finally {
    busy.value = false;
  }
}

/**
 * The sentence path. The model picks the capability; the engine builds the request and computes the
 * frames, so a model mistake can change *which* animation is shown but can never invent one.
 */
async function runFromPrompt() {
  const text = prompt.value.trim();
  if (!text || !chapterId.value || busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  demoFallback.value = false;
  observationSaved.value = false;
  try {
    const request = await userApi.interpretAnimation({ chapterId: chapterId.value, prompt: text });
    const capability = request.operation ? `${request.structure}.${request.operation}` : request.structure;
    const known = capabilities.structures
      .flatMap((item) => item.capabilities)
      .find((item) => item.capability === capability);
    generatedTitle.value = known?.label ?? capability;
    response.value = await userApi.simulateAnimation(request);
    if (known) {
      structure.value = capabilities.structures.find((item) => item.capabilities.includes(known))?.structure ?? structure.value;
      capabilityName.value = known.capability;
      resetArguments();
    }
  } catch (cause) {
    error.value = failureMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function saveObservation() {
  const text = observation.value.trim();
  const id = response.value?.animationRecordId;
  if (!text || !id || busy.value) return;
  busy.value = true;
  try {
    await userApi.saveObservation(id, { observation: text });
    observationSaved.value = true;
    observation.value = "";
  } catch (cause) {
    error.value = failureMessage(cause);
  } finally {
    busy.value = false;
  }
}

function chapterTitle(): string {
  return chapters.value.find((item) => item.id === chapterId.value)?.title ?? t("lab.title");
}

/* Immediate on purpose: the first chapter can carry no structures of its own, in which case the scoped
   list falls back to the same array and a lazy watch would never fire - leaving both pickers empty. */
watch(structureOptions, () => {
  if (!structureOptions.value.some((item) => item.structure === structure.value)) {
    structure.value = structureOptions.value[0]?.structure ?? "";
  }
}, { immediate: true });
watch(capabilityOptions, () => {
  if (!capabilityOptions.value.some((item) => item.capability === capabilityName.value)) {
    capabilityName.value = capabilityOptions.value[0]?.capability ?? "";
  }
}, { immediate: true });
watch(selected, () => resetArguments(), { immediate: false });
onMounted(() => void loadChapters());
</script>

<template>
  <BrandStage wide>
    <div class="lab">
      <header class="lab__head">
        <h1 class="lab__title">{{ t("lab.title") }}</h1>
        <button class="lab__link" type="button" @click="router.push('/')">{{ t("common.backHome") }}</button>
      </header>

      <div class="lab__grid">
        <section class="panel" :aria-label="t('lab.pickOne')">
          <label class="field">
            <span class="field__label">{{ t("lab.chapter") }}</span>
            <select v-model="chapterId" class="field__control">
              <option v-for="chapter in chapters" :key="chapter.id" :value="chapter.id">{{ chapter.title }}</option>
            </select>
          </label>

          <label class="field">
            <span class="field__label">{{ t("lab.structure") }}</span>
            <select v-model="structure" class="field__control">
              <option v-for="item in structureOptions" :key="item.structure" :value="item.structure">{{ item.label }}</option>
            </select>
          </label>

          <label class="field">
            <span class="field__label">{{ t("lab.operation") }}</span>
            <select v-model="capabilityName" class="field__control">
              <option v-for="item in capabilityOptions" :key="item.capability" :value="item.capability">{{ item.label }}</option>
            </select>
          </label>

          <div v-if="argumentFields.length" class="fields">
            <label v-for="field in argumentFields" :key="field.name" class="field">
              <span class="field__label">{{ field.name }}<em v-if="!field.required">{{ t("common.optional") }}</em></span>
              <input v-model="argumentText[field.name]" class="field__control" type="text" :placeholder="field.hint">
            </label>
          </div>

          <button class="lab__button lab__button--primary" type="button" :disabled="busy || !selected" @click="runDeterministic">
            {{ busy ? t("lab.calculating") : t("lab.generate") }}
          </button>

          <hr class="panel__divider">

          <label class="field">
            <span class="field__label">{{ t("lab.describe") }}</span>
            <textarea
              v-model="prompt"
              class="field__control field__control--area"
              rows="2"
              :placeholder="t('lab.describePlaceholder')"
            />
          </label>
          <button class="lab__button" type="button" :disabled="busy || !prompt.trim() || !chapterId" @click="runFromPrompt">{{ t("lab.askModel") }}</button>
        </section>

        <section class="panel panel--stage" :aria-label="t('lab.playerTitle')">
          <p v-if="demoFallback" class="panel__flag">{{ t("lab.demoFallback") }}</p>
          <p v-if="notice && !error" class="panel__note">{{ notice }}</p>
          <p v-if="error" class="panel__error" role="alert">{{ error }}</p>

          <AnimationPlayer
            :definition="playerDefinition"
            :trace="response?.trace ?? null"
            :placeholder="t('lab.placeholder')"
          />

          <div v-if="response && canObserve" class="observe">
            <label class="field">
              <span class="field__label">{{ t("lab.observationLabel") }}</span>
              <textarea v-model="observation" class="field__control field__control--area" rows="2" :placeholder="t('lab.observationPlaceholder')" />
            </label>
            <button class="lab__button" type="button" :disabled="busy || !observation.trim()" @click="saveObservation">{{ t("lab.saveObservation") }}</button>
            <p v-if="observationSaved" class="panel__note">{{ t("lab.observationSaved") }}</p>
          </div>
        </section>
      </div>
    </div>
  </BrandStage>
</template>

<style scoped>
/* The lab is the same paper as the sign-in screen; the two panels are the same quiet card the lesson
   uses, so a demo opened here and a demo opened in class are framed identically. */
.lab {
  display: grid;
  width: min(1320px, 100%);
  gap: 22px;
  margin: 0 auto;
  color: var(--text);
}

.lab__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.lab__title {
  margin: 0;
  color: var(--text);
  font-family: var(--font-ui);
  font-size: clamp(30px, 3.4vw, 46px);
  font-weight: 400;
  letter-spacing: 0;
  line-height: 1.06;
}

.lab__link {
  min-height: 42px;
  padding: 0 20px;
  border: 1px solid color-mix(in srgb, var(--text) 16%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 24%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 5px 12px color-mix(in srgb, var(--text) 10%, transparent);
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 15px;
  font-weight: 650;
  transition: transform 160ms cubic-bezier(.25, 1, .5, 1), border-color 160ms ease, background-color 160ms ease;
}

.lab__link:hover { border-color: var(--text); background: color-mix(in srgb, var(--surface) 46%, transparent); transform: translateY(-1px); }

.lab__grid {
  display: grid;
  grid-template-columns: minmax(0, 340px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.panel {
  display: grid;
  gap: 14px;
  align-content: start;
  min-width: 0;
  padding: 20px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 24px;
  background: color-mix(in srgb, var(--surface) 58%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 10%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
}

.panel--stage { gap: 12px; }

.field { display: grid; gap: 6px; }
.field__label { color: var(--text-muted); font-size: 14px; font-weight: 620; }
.field__label em { font-style: normal; opacity: .78; }

.field__control {
  width: 100%;
  min-height: 44px;
  padding: 9px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 76%, transparent);
  color: var(--text);
  font: inherit;
  font-size: 15px;
}

.field__control--area { min-height: 68px; border-radius: 20px; resize: vertical; line-height: 1.6; }
.field__control:focus-visible { outline: none; border-color: var(--text); box-shadow: var(--focus-ring); }

.fields { display: grid; gap: 12px; }

.panel__note { margin: 0; color: var(--text-muted); font-size: 14px; line-height: 1.65; }
.panel__divider { height: 1px; margin: 2px 0; border: 0; background: var(--line); }
.panel__flag { margin: 0; padding: 9px 14px; border: 1px solid color-mix(in srgb, var(--text) 15%, transparent); border-radius: 999px; background: color-mix(in srgb, var(--text) 7%, transparent); color: var(--text); font-size: 14px; line-height: 1.6; }
.panel__error { margin: 0; color: var(--text); font-size: 14px; }

.lab__button {
  min-height: 46px;
  padding: 10px 20px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 15px;
  font-weight: 620;
  transition: border-color .16s ease, background-color .16s ease, transform .16s ease;
}

.lab__button:hover:not(:disabled) { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.lab__button:disabled { cursor: default; opacity: .42; }
.lab__button--primary { border-color: transparent; background: var(--text); color: var(--surface); }
.lab__button--primary:hover:not(:disabled) { background: var(--accent-strong); border-color: transparent; }

.observe { display: grid; gap: 10px; }

@media (max-width: 940px) {
  .lab__grid { grid-template-columns: minmax(0, 1fr); }
}

@media (prefers-reduced-transparency: reduce) {
  .panel { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
</style>
