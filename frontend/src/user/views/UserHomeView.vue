<script setup lang="ts">
import BrandStage from "../../shared/components/BrandStage.vue";
import { useI18n } from "../../shared/i18n/locale";

const { t } = useI18n();
</script>

<template>
  <BrandStage wide>
    <section class="profile" aria-labelledby="profile-title">
      <h1 id="profile-title" class="workbench-title">{{ t("home.profile") }}</h1>
      <p class="profile__status">{{ t("profile.pending") }}</p>
      <RouterLink class="profile__back" to="/begin">{{ t("common.backHome") }}</RouterLink>
    </section>
  </BrandStage>
</template>

<style scoped>
.profile { display: grid; width: min(var(--workbench-width), 100%); margin: 0 auto; gap: 22px; justify-items: start; }
.profile__status { margin: 0; color: var(--text-muted); font-size: 19px; }
.profile__back { color: var(--text); font-size: 17px; }
.profile__back:focus-visible { outline: 2px solid var(--text); outline-offset: 4px; }
</style>
