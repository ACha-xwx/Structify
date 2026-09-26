<script setup lang="ts">
import { ref, useId } from "vue";
import { useModalLifecycle } from "./useModalLifecycle";

/**
 * A one-button dialog for the things a learner has to be told in the face.
 *
 * The animation lab used to print rejected arguments as 14px grey text under the stage; the report was
 * "非常小的小字提醒" — nobody reads it, so a bad argument looked exactly like a broken engine. Anything
 * that stops an animation from running is now a dialog: a large title, a large body, one obvious way out.
 *
 * It is the alert sibling of `ConfirmDialog` and shares the same layer styles and focus lifecycle, so the
 * two cannot drift apart in behaviour (focus trap, Escape, background inert, focus restored on close).
 */
const props = defineProps<{
  open: boolean;
  title: string;
  message: string;
  closeLabel: string;
}>();
const emit = defineEmits<{ close: [] }>();
const overlayRef = ref<HTMLElement | null>(null);
const dialogRef = ref<HTMLElement | null>(null);
const titleId = `${useId()}-title`;
const messageId = `${useId()}-message`;
const { onKeydown } = useModalLifecycle(() => props.open, overlayRef, dialogRef, () => emit("close"));
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      ref="overlayRef"
      class="dialog-backdrop"
      data-modal-layer
      role="presentation"
      @click.self="emit('close')"
    >
      <section
        ref="dialogRef"
        class="dialog notice-dialog"
        role="alertdialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        :aria-describedby="messageId"
        tabindex="-1"
        @keydown="onKeydown"
      >
        <h2 :id="titleId" class="notice-dialog__title">{{ title }}</h2>
        <p :id="messageId" class="notice-dialog__message">{{ message }}</p>
        <div class="dialog__actions">
          <button class="button button--primary notice-dialog__close" data-dialog-initial-focus type="button" @click="emit('close')">
            {{ closeLabel }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
/* Deliberately larger than the shared `.dialog` copy: a rejected argument is the whole message, so it is
   the one thing on screen, not a footnote under a stage that says nothing. */
.notice-dialog {
  width: min(560px, 100%);
  max-height: min(82vh, 640px);
  overflow: auto;
}

.notice-dialog__title {
  color: var(--text);
  font-size: 27px;
  font-weight: 650;
  line-height: 1.25;
}

.notice-dialog__message {
  margin-top: 14px;
  color: var(--text);
  font-size: 21px;
  line-height: 1.65;
  white-space: pre-line;
}

.dialog__actions .notice-dialog__close {
  min-height: 48px;
  padding: 0 26px;
  font-size: 18px;
}
</style>
