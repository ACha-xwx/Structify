<script setup lang="ts">
import { useI18n } from "../i18n/locale";

const { t } = useI18n();
</script>

<template>
  <section class="empty-screen" role="alert" aria-labelledby="forbidden-title">
    <p class="screen-code">403</p>
    <h1 id="forbidden-title">{{ t("forbidden.title") }}</h1>
    <p>{{ t("forbidden.body") }}</p>
    <RouterLink class="button button--primary" to="/">{{ t("common.backToHome") }}</RouterLink>
  </section>
</template>
