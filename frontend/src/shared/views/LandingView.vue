<script setup lang="ts">
import { useRouter } from "vue-router";
import BrandStage from "../components/BrandStage.vue";
import BrandName from "../components/BrandName.vue";
import AiTitle from "../components/AiTitle.vue";
import LiquidMetalButton from "../../admin/components/LiquidMetalButton.vue";
import playIcon from "../../assets/play.svg";
import { useI18n } from "../i18n/locale";

const router = useRouter();
const { t } = useI18n();
const playIconStyle = { "--landing-play-icon": `url("${playIcon}")` };
</script>

<template>
  <BrandStage class="landing-stage" :show-brand="false">
    <section class="landing" aria-labelledby="landing-title">
      <AiTitle expressive><h1 id="landing-title" class="landing__title"><BrandName /></h1></AiTitle>
      <LiquidMetalButton class="landing__start" view-mode="text" @click="router.push('/begin')">
        <template #icon><span class="landing__play" :style="playIconStyle" aria-hidden="true" /></template>
        {{ t("landing.start") }}
      </LiquidMetalButton>
    </section>
  </BrandStage>
</template>

<style scoped>
.landing-stage { padding: 76px 16px; }
.landing { display: grid; justify-items: center; gap: 36px; width: 100%; text-align: center; }
.landing__title { margin: 0; font-size: clamp(32px, 6vw, 68px); font-weight: 500; line-height: 1.2; }
.landing__start { --liquid-width: 213px; --liquid-height: 69px; }
.landing__start :deep(.liquid-metal-button__content-layer) { gap: 9px; font-size: 21px; }
.landing__play { display: block; width: 24px; height: 24px; background: currentColor; mask: var(--landing-play-icon) center / contain no-repeat; }
</style>
