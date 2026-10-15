<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { X } from "@lucide/vue";
import BrandStage from "../../shared/components/BrandStage.vue";
import { useI18n } from "../../shared/i18n/locale";
import type { ClassroomBookmark } from "../../shared/types/contracts";
import { userApi } from "../runtime";

/**
 * The learner's own space: the steps they saved while a lesson was running.
 *
 * A saved step keeps the lesson it came from and the page that was on screen, so coming back here
 * offers two honest ways in - the courseware page itself, or the lesson it was part of.
 */
const { t } = useI18n();
const router = useRouter();
const bookmarks = ref<ClassroomBookmark[]>([]);
const loading = ref(true);
const error = ref("");

/** Saved steps read best under their lesson: the same lesson is usually saved more than once. */
const groups = computed(() => {
  const byLesson = new Map<string, { title: string; items: ClassroomBookmark[] }>();
  for (const bookmark of bookmarks.value) {
    const key = bookmark.lessonId || bookmark.sessionId;
    const group = byLesson.get(key) ?? { title: bookmark.lessonTitle || t("profile.lessonFallback"), items: [] };
    group.items.push(bookmark);
    byLesson.set(key, group);
  }
  return [...byLesson.entries()].map(([key, group]) => ({
    key,
    title: group.title,
    items: [...group.items].sort((a, b) => a.stepIndex - b.stepIndex),
  }));
});

function openPage(bookmark: ClassroomBookmark) {
  void router.push({ path: "/courseware", query: { lesson: bookmark.lessonId, slide: bookmark.slideId } });
}

function backToLesson(bookmark: ClassroomBookmark) {
  void router.push({ path: "/classroom", query: { session: bookmark.sessionId } });
}

async function remove(bookmark: ClassroomBookmark) {
  try {
    await userApi.deleteBookmark(bookmark.id);
    bookmarks.value = bookmarks.value.filter((item) => item.id !== bookmark.id);
  } catch {
    error.value = t("profile.failed");
  }
}

function savedAt(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleDateString();
}

onMounted(async () => {
  try {
    bookmarks.value = await userApi.listBookmarks();
  } catch {
    error.value = t("profile.failed");
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <BrandStage wide>
    <section class="profile" aria-labelledby="profile-title">
      <h1 id="profile-title" class="workbench-title">{{ t("home.profile") }}</h1>

      <RouterLink class="profile__map" to="/learning-map">{{ t("map.title") }}</RouterLink>
      <p v-if="loading" class="profile__status">{{ t("profile.loading") }}</p>
      <p v-else-if="!bookmarks.length && !error" class="profile__status">{{ t("profile.empty") }}</p>
      <p v-if="error" class="profile__status" role="alert">{{ error }}</p>

      <div v-for="group in groups" :key="group.key" class="profile__group">
        <h2 class="profile__lesson">{{ group.title }}</h2>
        <ul class="profile__list">
          <li v-for="bookmark in group.items" :key="bookmark.id" class="profile__item">
            <div class="profile__body">
              <p class="profile__step">{{ t("profile.step", { index: bookmark.stepIndex + 1 }) }}</p>
              <p class="profile__title">{{ bookmark.title }}</p>
              <p class="profile__time">{{ savedAt(bookmark.createdAt) }}</p>
            </div>
            <div class="profile__actions">
              <button v-if="bookmark.slideId" class="profile__action" type="button" @click="openPage(bookmark)">{{ t("profile.openPage") }}</button>
              <button class="profile__action" type="button" @click="backToLesson(bookmark)">{{ t("profile.backToLesson") }}</button>
              <button class="profile__action profile__action--quiet" type="button" :aria-label="t('profile.remove')" :title="t('profile.remove')" @click="remove(bookmark)">
                <X :size="18" aria-hidden="true" />
              </button>
            </div>
          </li>
        </ul>
      </div>

      <RouterLink class="profile__back" to="/begin">{{ t("common.backHome") }}</RouterLink>
    </section>
  </BrandStage>
</template>

<style scoped>
.profile { display: grid; width: min(var(--workbench-width), 100%); margin: 0 auto; gap: 22px; justify-items: start; }
.profile__status { margin: 0; color: var(--text-muted); font-size: 19px; line-height: 1.5; }
.profile__group { display: grid; width: 100%; gap: 10px; }
.profile__lesson { margin: 0; font-size: 19px; font-weight: 620; }
.profile__list { display: grid; gap: 12px; margin: 0; padding: 0; list-style: none; }
.profile__item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 22px;
  background: color-mix(in srgb, var(--surface) 58%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--surface) 92%, transparent), 0 10px 24px color-mix(in srgb, var(--text) 8%, transparent);
  -webkit-backdrop-filter: blur(7px) saturate(1.08);
  backdrop-filter: blur(7px) saturate(1.08);
}
.profile__body { display: grid; gap: 4px; min-width: 0; }
.profile__step { margin: 0; color: var(--text-muted); font-size: 19px; }
.profile__title { margin: 0; font-size: 19px; line-height: 1.5; overflow-wrap: anywhere; }
.profile__time { margin: 0; color: var(--text-muted); font-size: 19px; }
.profile__actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.profile__action {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid color-mix(in srgb, var(--text) 20%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 62%, transparent);
  color: var(--text);
  font: inherit;
  font-size: 19px;
  cursor: pointer;
  transition: transform 160ms ease, background 160ms ease;
}
.profile__action:hover { transform: translateY(-1px); background: color-mix(in srgb, var(--text) 8%, transparent); }
.profile__action:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.profile__action--quiet { display: inline-grid; width: 40px; padding: 0; place-items: center; border-color: transparent; background: transparent; }
.profile__map {
  display: inline-flex;
  min-height: 48px;
  align-items: center;
  padding: 0 22px;
  border: 1px double color-mix(in srgb, var(--text) 15%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 58%, transparent);
  color: var(--text);
  font-size: 19px;
  text-decoration: none;
}
.profile__map:hover { background: color-mix(in srgb, var(--text) 8%, transparent); }
.profile__back { color: var(--text); font-size: 19px; }
.profile__back:focus-visible { outline: 2px solid var(--text); outline-offset: 4px; }
@media (prefers-reduced-motion: reduce) { .profile__action { transition: none; } }
@media (prefers-reduced-transparency: reduce) {
  .profile__item { background: var(--surface); -webkit-backdrop-filter: none; backdrop-filter: none; }
}
</style>
