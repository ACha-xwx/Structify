<script setup lang="ts">
import ThemeToggle from "../design/ThemeToggle.vue";
import { useI18n } from "../i18n/locale";

/**
 * The one stage every signed-in surface stands on.
 *
 * Sign-in sets the visual language - blueprint grid paper, the S lockup, a theme switch in the corner -
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
}>(), { wide: false });

const { t } = useI18n();
</script>

<template>
  <div class="stage" :class="{ 'stage--wide': props.wide }">
    <div class="stage__brand">
      <RouterLink class="stage__brand-link" to="/" :aria-label="t('common.brand')">
        <span class="stage__mark" aria-hidden="true">S</span>
        <span class="stage__name">Structify</span>
      </RouterLink>
    </div>
    <div class="stage__theme"><ThemeToggle /></div>
    <div class="stage__body"><slot /></div>
  </div>
</template>

<style scoped>
/* The exact recipe of the sign-in screen: page background, 58px grid, ink from the theme tokens. */
.stage {
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
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 1px solid var(--text);
  border-radius: 9px;
  background: var(--text);
  box-shadow: inset 1px 1px color-mix(in srgb, var(--surface) 18%, transparent), 0 5px 12px color-mix(in srgb, var(--text) 13%, transparent);
  color: var(--surface);
  font-family: var(--font-ui);
  font-size: 18px;
  font-weight: 700;
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

@media (hover: hover) and (pointer: fine) {
  .stage__brand-link:hover .stage__mark { transform: translateY(-1px); }
}

@media (min-width: 640px) { .stage__brand { left: 50%; transform: translateX(-50%); } }

@media (max-width: 520px) {
  .stage { padding: 76px 14px 22px; }
  .stage__brand { top: 18px; left: 18px; }
  .stage__theme { top: 18px; right: 16px; }
}

@media (prefers-reduced-transparency: reduce) {
  .stage__mark { box-shadow: none; }
}
</style>
