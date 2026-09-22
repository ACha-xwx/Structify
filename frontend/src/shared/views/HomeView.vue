<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import BrandStage from "../components/BrandStage.vue";
import DirectionalArrowIcon from "../components/DirectionalArrowIcon.vue";
import { useI18n } from "../i18n/locale";
import { auth } from "../../app/providers/runtime";

/**
 * The entry page. Structify has two learning surfaces, and this page exists to ask which one to open.
 * The classroom used to be mounted on the root path, so signing in dropped a learner into a lesson
 * they had not picked yet. Nothing here starts a lesson, loads a courseware deck, or resumes a
 * session on its own - both choices are one click away and both are deliberate.
 *
 * Visually it is the same stage as the sign-in screen, because that stage is shared (BrandStage), so
 * signing in feels like moving one step forward rather than into another product.
 */
const LAST_KEY = "structify.classroom.last";
const { t } = useI18n();
const router = useRouter();
const lastSessionId = ref("");
const leaving = ref(false);

const signedIn = computed(() => Boolean(auth.state.user));
/** Resuming stays a link, not an implicit restore: only a learner who asks for it goes back in. */
const resumeTarget = computed(() => ({ path: "/classroom", query: { session: lastSessionId.value } }));

async function signOut() {
  if (leaving.value) return;
  leaving.value = true;
  try {
    await auth.logout();
    await router.replace("/login");
  } finally {
    leaving.value = false;
  }
}

onMounted(() => {
  lastSessionId.value = localStorage.getItem(LAST_KEY) ?? "";
});
</script>

<template>
  <BrandStage>
    <section class="entry-flow" aria-labelledby="entry-title">
      <h1 id="entry-title" class="entry__title">{{ t("home.title") }}</h1>

      <nav class="entry__choices" :aria-label="t('home.choices')">
        <RouterLink class="choice" to="/classroom">
          <span class="choice__symbol" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" focusable="false">
              <rect x="3" y="4" width="18" height="12.5" rx="2.2" />
              <path d="M12 16.5V20M8.6 20h6.8" />
            </svg>
          </span>
          <span class="choice__name">{{ t("home.classroom") }}</span>
          <span class="choice__go" aria-hidden="true"><DirectionalArrowIcon direction="right" /></span>
        </RouterLink>

        <RouterLink class="choice" to="/animation">
          <span class="choice__symbol" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" focusable="false">
              <circle cx="12" cy="12" r="8.6" />
              <path d="M10.2 8.6l5.4 3.4-5.4 3.4z" />
            </svg>
          </span>
          <span class="choice__name">{{ t("home.animation") }}</span>
          <span class="choice__go" aria-hidden="true"><DirectionalArrowIcon direction="right" /></span>
        </RouterLink>

        <RouterLink class="choice" to="/compiler">
          <span class="choice__symbol" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" focusable="false">
              <path d="M8.6 8.2L4.8 12l3.8 3.8" />
              <path d="M15.4 8.2l3.8 3.8-3.8 3.8" />
            </svg>
          </span>
          <span class="choice__name">{{ t("home.compiler") }}</span>
          <span class="choice__go" aria-hidden="true"><DirectionalArrowIcon direction="right" /></span>
        </RouterLink>
      </nav>

      <nav class="entry-links" :aria-label="t('home.account')">
        <RouterLink v-if="lastSessionId" class="entry__resume" :to="resumeTarget">{{ t("home.resume") }}</RouterLink>
        <button v-if="signedIn" class="entry__signout" type="button" :disabled="leaving" @click="signOut">{{ t("common.signOut") }}</button>
      </nav>
    </section>
  </BrandStage>
</template>

<style scoped>
.entry-flow { display: grid; width: min(100%, 420px); gap: 28px; justify-items: center; text-align: center; }
.entry__title { margin: 0; color: var(--text); font-family: var(--font-ui); font-size: clamp(40px, 5vw, 58px); font-weight: 400; letter-spacing: 0; line-height: 1.04; }

.entry__choices { display: grid; width: 100%; gap: 14px; }

/* Each entry is the same pill as a sign-in field: double hairline, glass surface, lit rim. */
.choice {
  --field-angle: 220deg;
  position: relative;
  display: flex;
  width: 100%;
  min-height: 58px;
  align-items: center;
  gap: 10px;
  padding: 5px 6px 5px 10px;
  overflow: visible;
  border: 1px double color-mix(in srgb, var(--text) 16%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 18%, transparent);
  box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 90%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 90%, transparent), inset 6px -6px 1px -6px color-mix(in srgb, var(--surface) 55%, transparent), inset -6px 6px 1px -6px color-mix(in srgb, var(--surface) 55%, transparent), inset 0 0 2px color-mix(in srgb, var(--text) 44%, transparent), 0 7px 13px color-mix(in srgb, var(--text) 12%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
  color: var(--text);
  font: inherit;
  text-decoration: none;
  transition: transform 180ms cubic-bezier(0.25, 1, 0.5, 1), box-shadow 180ms ease, background-color 180ms ease;
}

.choice::before,
.choice::after { position: absolute; border-radius: inherit; content: ""; pointer-events: none; }

.choice::before {
  z-index: 2;
  inset: -1px;
  padding: 1.2px;
  background: conic-gradient(from var(--field-angle) at 50% 50%, color-mix(in srgb, var(--text) 52%, transparent), color-mix(in srgb, var(--surface) 86%, transparent) 15%, transparent 29% 40%, color-mix(in srgb, var(--text-muted) 56%, transparent) 53%, color-mix(in srgb, var(--surface) 82%, transparent) 61%, transparent 74% 86%, color-mix(in srgb, var(--text) 48%, transparent));
  opacity: 0.58;
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  mask-composite: exclude;
  transition: opacity 180ms ease, filter 180ms ease;
}

.choice::after {
  z-index: 1;
  inset: 2px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--surface) 24%, transparent);
  background: linear-gradient(118deg, transparent 0 35%, color-mix(in srgb, var(--surface) 48%, transparent) 47%, transparent 59%);
  mix-blend-mode: screen;
  opacity: 0.74;
  transition: opacity 180ms ease, transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.choice:hover,
.choice:focus-visible {
  outline: none;
  background: color-mix(in srgb, var(--surface) 28%, transparent);
  box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 94%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 94%, transparent), inset 0 0 2px color-mix(in srgb, var(--text) 52%, transparent), 0 10px 17px color-mix(in srgb, var(--text) 16%, transparent);
  transform: translateY(-1px);
}

.choice:hover::before,
.choice:focus-visible::before { filter: saturate(1.18) brightness(1.08); opacity: 0.96; }
.choice:hover::after,
.choice:focus-visible::after { opacity: 1; transform: translateX(9%); }

.choice__symbol { position: relative; z-index: 4; display: grid; width: 36px; height: 36px; flex: 0 0 36px; place-items: center; border-radius: 50%; color: var(--text-muted); }
.choice__symbol svg { display: block; width: 20px; height: 20px; }
.choice__name { position: relative; z-index: 4; flex: 1 1 auto; min-width: 0; text-align: left; font-size: 16px; font-weight: 650; letter-spacing: 0.01em; line-height: 1.2; }

/* The right-hand chip is the same round affordance the sign-in fields carry. */
.choice__go {
  position: relative;
  z-index: 5;
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  place-items: center;
  border-radius: 50%;
  background: color-mix(in srgb, var(--text) 8%, transparent);
  color: var(--text);
  transition: background-color 180ms ease, transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.choice:hover .choice__go,
.choice:focus-visible .choice__go { background: color-mix(in srgb, var(--text) 14%, transparent); transform: translateX(3px); }

.entry-links { display: flex; width: 100%; align-items: center; justify-content: center; gap: 32px; padding-top: 2px; color: var(--text-muted); font-size: 17px; font-weight: 650; text-align: center; }

.entry-links a,
.entry__signout {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-decoration: none;
  transition: color 150ms ease, transform 150ms cubic-bezier(0.25, 1, 0.5, 1);
}

.entry-links a::after,
.entry__signout::after { position: absolute; right: 0; bottom: -4px; left: 0; height: 1px; background: var(--text); content: ""; transform: scaleX(0); transform-origin: left; transition: transform 160ms cubic-bezier(0.22, 1, 0.36, 1); }
.entry-links a:focus-visible,
.entry__signout:focus-visible { color: var(--text); outline: 0; }
.entry-links a:focus-visible::after,
.entry__signout:focus-visible::after { transform: scaleX(1); }
.entry__signout:disabled { cursor: default; opacity: 0.5; }

@media (hover: hover) and (pointer: fine) {
  .entry-links a:hover,
  .entry__signout:hover:not(:disabled) { color: var(--text); transform: translateY(-1px); }
  .entry-links a:hover::after,
  .entry__signout:hover:not(:disabled)::after { transform: scaleX(1); }
}

@media (max-width: 520px) {
  .entry-flow { gap: 24px; }
  .entry__title { font-size: 42px; }
}

@media (prefers-reduced-motion: reduce) { .entry-flow * { animation: none !important; transition-duration: 1ms !important; } }
@media (prefers-reduced-transparency: reduce) {
  .choice { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
  .choice::after { display: none; }
}
</style>
