<script setup lang="ts">
import { useI18n } from "../i18n/locale";

const { t } = useI18n();
</script>

<template>
  <section class="empty-screen" role="alert" aria-labelledby="not-found-title">
    <p class="screen-code">404</p>
    <h1 id="not-found-title">{{ t("notFound.title") }}</h1>
    <p>{{ t("notFound.body") }}</p>
    <RouterLink class="button button--primary" to="/">{{ t("common.backToHome") }}</RouterLink>
  </section>
</template>
