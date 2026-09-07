<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppShell from "./app-shell/AppShell.vue";
import MorphingSquareLoader from "../shared/components/MorphingSquareLoader.vue";
import { useLocale } from "../shared/i18n/locale";
import { documentTitleForRoute } from "./document-title";

const route = useRoute();
const router = useRouter();
const { locale } = useLocale();
const routerReady = ref(false);
const shellLayout = computed(() => route.meta.layout !== "auth" && route.meta.layout !== "minimal");

watchEffect(() => {
  if (typeof document !== "undefined") document.title = documentTitleForRoute(route, locale.value);
});

onMounted(() => {
  void router.isReady().then(() => {
    routerReady.value = true;
  });
});
</script>

<template>
  <main v-if="!routerReady" class="app-bootstrap" aria-busy="true">
    <MorphingSquareLoader message="正在准备页面" />
  </main>
  <RouterView v-else v-slot="{ Component }">
    <AppShell v-if="shellLayout"><component :is="Component" /></AppShell>
    <component v-else :is="Component" />
  </RouterView>
</template>

<style scoped>
.app-bootstrap {
  display: grid;
  min-block-size: 100dvh;
  place-items: center;
  padding: 1.5rem;
  background: var(--page, var(--surface, #fff));
}
</style>
