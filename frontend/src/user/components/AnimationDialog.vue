<script setup lang="ts">
import { ref, useId } from "vue";
import AnimationPlayer from "../../animation/AnimationPlayer.vue";
import { useModalLifecycle } from "../../shared/components/useModalLifecycle";
import type { AnimationDefinition } from "../../shared/types/animation";

/**
 * The demo opens over the conversation, not inside it.
 *
 * A trace drawn inside a reply is a few hundred pixels wide on a phone, pushes the answer off the
 * screen, and leaves the player's controls fighting the bubble's padding. Given its own surface it gets
 * the whole viewport. One component serves both: a centred panel with room for a tree on a desktop, a
 * sheet rising from the bottom edge on a phone - adapted, never merely shrunk.
 */
const props = withDefaults(defineProps<{
  open: boolean;
  title: string;
  definition: AnimationDefinition | null;
  trace?: Record<string, unknown> | null;
  placeholder?: string;
  closeLabel: string;
}>(), { trace: null, placeholder: "" });

const emit = defineEmits<{ close: [] }>();
const overlayRef = ref<HTMLElement | null>(null);
const dialogRef = ref<HTMLElement | null>(null);
const titleId = `${useId()}-title`;
const { onKeydown } = useModalLifecycle(() => props.open, overlayRef, dialogRef, () => emit("close"));
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      ref="overlayRef"
      class="dialog-backdrop animation-layer"
      data-modal-layer
      role="presentation"
      @click.self="emit('close')"
    >
      <section
        ref="dialogRef"
        class="animation-dialog"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        tabindex="-1"
        @keydown="onKeydown"
      >
        <header class="animation-dialog__head">
          <h2 :id="titleId" class="animation-dialog__title">{{ title }}</h2>
          <button class="animation-dialog__close" data-dialog-initial-focus type="button" @click="emit('close')">
            {{ closeLabel }}
          </button>
        </header>

        <div class="animation-dialog__body">
          <!-- The dialog's own header carries the title, so the player skips its duplicate headline. -->
          <AnimationPlayer :definition="definition" :trace="trace" :placeholder="placeholder" compact />
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.animation-dialog {
  display: flex;
  flex-direction: column;
  width: min(1080px, 100%);
  max-height: min(88dvh, 940px);
  padding: 20px 24px 24px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--surface) 96%, transparent);
  box-shadow: var(--shadow-md);
}

.animation-dialog__head {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.animation-dialog__title { margin: 0; color: var(--text); font-family: var(--font-ui); font-size: 22px; font-weight: 650; line-height: 1.25; }

.animation-dialog__close {
  min-height: 44px;
  padding: 0 20px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  font: inherit;
  font-size: 17px;
  font-weight: 620;
  transition: border-color .16s ease, background-color .16s ease;
}

.animation-dialog__close:hover { border-color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }

.animation-dialog__body { flex: 1 1 auto; min-height: 0; padding-top: 16px; overflow: auto; overscroll-behavior: contain; }

/* A phone gets a sheet from the bottom edge: full width, nearly full height, and the head stays put
   while the trace scrolls underneath it. */
@media (max-width: 720px) {
  .animation-layer { place-items: end stretch; padding: 0; }

  .animation-dialog {
    width: 100%;
    max-height: 94dvh;
    padding: 6px 14px calc(16px + env(safe-area-inset-bottom));
    border-width: 1px 0 0;
    border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  }

  .animation-dialog__head { padding-bottom: 10px; }
  .animation-dialog__title { font-size: 20px; }
  .animation-dialog__close { min-height: 40px; padding: 0 16px; font-size: 17px; }
  .animation-dialog__body { padding-top: 12px; }
}
</style>
