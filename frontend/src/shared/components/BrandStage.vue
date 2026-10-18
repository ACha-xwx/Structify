<script setup lang="ts">
import ThemeToggle from "../design/ThemeToggle.vue";
import BrandName from "./BrandName.vue";
import brandIcon from "../../favicon.svg";
import { useI18n } from "../i18n/locale";

/**
 * The one stage every signed-in surface stands on.
 *
 * Sign-in sets the visual language - blueprint grid paper, the brand, a theme switch in the corner -
 * and the pages behind it used to each invent their own backdrop (a dark video for the classroom and the
 * courseware browser). This component owns that backdrop once, so a learner moving from sign-in to the
 * entry page to a lesson never sees the product change clothes on the way.
 *
 * It renders no headings, no nav and no landmarks of its own: the page inside owns its content and its
 * accessibility tree.
 */
const props = withDefaults(defineProps<{
  /** Wide pages (a lesson beside its courseware, the lab grid) fill the stage instead of centring in it. */
  wide?: boolean;
  fixed?: boolean;
  showBrand?: boolean;
}>(), { wide: false, fixed: false, showBrand: true });

const { t } = useI18n();
</script>

<template>
  <div class="stage" :class="{ 'stage--wide': props.wide, 'stage--fixed': props.fixed }">
    <div v-if="props.showBrand" class="stage__brand">
      <RouterLink class="stage__brand-link" to="/" :aria-label="t('common.brand')">
        <img class="stage__mark" :src="brandIcon" width="32" height="32" alt="" aria-hidden="true" />
        <BrandName class="stage__name" />
      </RouterLink>
    </div>
    <div class="stage__theme"><ThemeToggle /></div>
    <div class="stage__body"><slot /></div>
  </div>
</template>

<style scoped>
/* The exact recipe of the sign-in screen: page background, 58px grid, ink from the theme tokens. */
.stage {
  --workbench-width: 1320px;
  position: relative;
  display: grid;
  width: 100%;
  min-height: 100dvh;
  padding: 80px clamp(16px, 2.4vw, 34px) 28px;
  isolation: isolate;
  background-color: var(--bg);
  background-image:
    linear-gradient(0deg, transparent 24%, color-mix(in srgb, var(--text) 6%, transparent) 25%, color-mix(in srgb, var(--text) 6%, transparent) 26%, transparent 27%, transparent 74%, color-mix(in srgb, var(--text) 6%, transparent) 75%, color-mix(in srgb, var(--text) 6%, transparent) 76%, transparent 77%, transparent),
    linear-gradient(90deg, transparent 24%, color-mix(in srgb, var(--text) 6%, transparent) 25%, color-mix(in srgb, var(--text) 6%, transparent) 26%, transparent 27%, transparent 74%, color-mix(in srgb, var(--text) 6%, transparent) 75%, color-mix(in srgb, var(--text) 6%, transparent) 76%, transparent 77%, transparent);
  background-size: 58px 58px;
  color: var(--text);
  color-scheme: inherit;
  font-family: var(--font-ui, var(--font-sans));
}

.stage__brand { position: absolute; z-index: 1; top: 20px; left: 22px; }

.stage__brand-link {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text);
  font-size: 16px;
  font-weight: 750;
  line-height: 1;
  text-decoration: none;
}

.stage__mark {
  display: block;
  width: 32px;
  height: 32px;
  flex: none;
  object-fit: contain;
}

.stage__name { font-variant-numeric: lining-nums; }
.stage__theme { position: absolute; z-index: 2; top: 22px; right: 22px; }

/* Centred on both axes. Vertical centring is by margin, not by align-content, so a page taller than
   the viewport degrades into an ordinary scroll instead of having its top cut off; horizontal
   centring is justify-items, which auto margins on wide pages (margin: 0 auto) override. */
.stage__body {
  display: grid;
  width: 100%;
  min-width: 0;
  margin-block: auto;
  align-content: center;
  justify-items: center;
}

.stage--wide .stage__body { align-content: stretch; justify-items: stretch; }

.stage--fixed { height: 100dvh; min-height: 0; overflow: hidden; }
.stage--fixed .stage__body { min-height: 0; margin-block: 0; align-content: stretch; }

.stage :deep(.workbench-title) {
  margin: 0;
  color: var(--text);
  font-family: var(--font-ui);
  font-size: 46px;
  font-weight: 400;
  letter-spacing: 0;
  line-height: 1.06;
}

@media (hover: hover) and (pointer: fine) {
  .stage__brand-link:hover .stage__mark { transform: translateY(-1px); }
}

@media (min-width: 640px) { .stage__brand { left: 50%; transform: translateX(-50%); } }

@media (max-width: 520px) {
  .stage :deep(.workbench-title) { font-size: 30px; }
  .stage { padding: 76px 14px 22px; }
  .stage__brand { top: 18px; left: 18px; }
  .stage__theme { top: 18px; right: 16px; }
}

@media (prefers-reduced-transparency: reduce) {
  .stage__mark { box-shadow: none; }
}
</style>
