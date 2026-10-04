<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ArrowUp, Check, ChevronDown, ChevronRight, FileText, Image, Pencil, Plus, Puzzle, Square, Upload, X } from "@lucide/vue";
import brainSvg from "../../assets/chat/brain.svg?raw";
import { useI18n } from "../../shared/i18n/locale";
import type { ChatReasoningEffort } from "../../shared/types/chat";
import { AttachmentError, CHAT_FILE_ACCEPT, CHAT_PHOTO_ACCEPT, MAX_ATTACHMENTS, MAX_ATTACHMENT_BYTES, readChatAttachment, type ComposerAttachment } from "../chat-attachments";

interface ComposerChapterOption {
  value: string;
  label: string;
}

const props = defineProps<{
  modelValue: string;
  chapterId: string;
  chapterOptions: ComposerChapterOption[];
  thinkingEnabled: boolean;
  reasoningEffort: ChatReasoningEffort;
  attachments: ComposerAttachment[];
  streaming: boolean;
  disabled?: boolean;
  contextKey: number;
}>();
const emit = defineEmits<{
  "update:modelValue": [value: string];
  "update:chapterId": [value: string];
  "update:thinkingEnabled": [value: boolean];
  "update:reasoningEffort": [value: ChatReasoningEffort];
  "update:attachments": [value: ComposerAttachment[]];
  reading: [value: boolean];
  send: [];
  stop: [];
}>();
const { t, isEnglish } = useI18n();
const root = ref<HTMLElement | null>(null);
const area = ref<HTMLTextAreaElement | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const photoInput = ref<HTMLInputElement | null>(null);
const menu = ref<"model" | "actions" | "chapter" | null>(null);
const modelPanel = ref<"info" | "edit">("info");
const skillOpen = ref(false);
const reading = ref(false);
const attachmentError = ref("");
let uploadEpoch = 0;
const canSend = computed(() => !!props.modelValue.trim() && props.modelValue.trim().length <= 4000 && !props.disabled && !reading.value && !props.streaming);
const locked = computed(() => props.disabled || props.streaming || reading.value);
const effortOptions = computed(() => [
  { value: "low" as const, label: isEnglish.value ? "Low" : "低", description: isEnglish.value ? "Faster, lighter reasoning" : "思考较少，响应更快" },
  { value: "high" as const, label: isEnglish.value ? "High · Default" : "高 · 默认", description: isEnglish.value ? "Balanced depth and speed" : "兼顾推理深度与响应速度" },
  { value: "max" as const, label: isEnglish.value ? "Maximum" : "最高", description: isEnglish.value ? "More thorough reasoning" : "投入更多思考，适合复杂问题" },
]);
const thinkingHint = computed(() => isEnglish.value ? "Enable Deep thinking to choose reasoning effort" : "请先开启深度思考，再选择思考强度");
const chapterLabel = computed(() => props.chapterOptions.find(option => option.value === props.chapterId)?.label ?? (isEnglish.value ? "All chapters" : "全部章节"));

function closeMenus() { menu.value = null; skillOpen.value = false; }
function outside(event: PointerEvent) { if (!root.value?.contains(event.target as Node)) closeMenus(); }
function escape(event: KeyboardEvent) {
  if (event.key !== "Escape" || !menu.value) return;
  const current = menu.value;
  closeMenus();
  root.value?.querySelector<HTMLButtonElement>(`[data-trigger="${current}"]`)?.focus();
  event.stopPropagation();
}
async function toggleMenu(value: "model" | "actions" | "chapter") {
  menu.value = menu.value === value ? null : value;
  if (value === "model") modelPanel.value = "info";
  skillOpen.value = false;
  await nextTick();
  root.value?.querySelector<HTMLButtonElement>(value === "chapter" ? ".composer-chapter-menu button" : ".composer-menu button")?.focus();
}
function selectChapter(value: string) { emit("update:chapterId", value); closeMenus(); }
function resize() {
  const element = area.value;
  if (!element) return;
  element.style.height = "auto";
  element.style.height = `${Math.max(84, Math.min(element.scrollHeight, 196))}px`;
}
function input(event: Event) { emit("update:modelValue", (event.target as HTMLTextAreaElement).value); resize(); }
function keydown(event: KeyboardEvent) {
  if (event.key === "Enter" && !event.shiftKey && !event.ctrlKey && !event.metaKey && !event.altKey && !event.isComposing) {
    event.preventDefault();
    if (canSend.value) emit("send");
  }
}
function pick(type: "file" | "image") {
  closeMenus();
  (type === "file" ? fileInput : photoInput).value?.click();
}
async function upload(event: Event, type: "file" | "image") {
  const element = event.target as HTMLInputElement;
  const files = Array.from(element.files ?? []);
  element.value = "";
  if (!files.length || locked.value) return;
  attachmentError.value = "";
  const epoch = ++uploadEpoch;
  reading.value = true;
  emit("reading", true);
  try {
    if (files.length + props.attachments.length > MAX_ATTACHMENTS) throw new AttachmentError("size");
    if (files.reduce((size, file) => size + file.size, 0) + props.attachments.reduce((size, file) => size + file.size, 0) > MAX_ATTACHMENT_BYTES) throw new AttachmentError("size");
    const loaded = await Promise.all(files.map(file => readChatAttachment(file, type)));
    if (epoch !== uploadEpoch || props.streaming) return;
    emit("update:attachments", [...props.attachments, ...loaded]);
  } catch (cause) {
    if (epoch !== uploadEpoch) return;
    const reason = cause instanceof AttachmentError ? cause.reason : "read";
    attachmentError.value = reason === "size"
      ? (isEnglish.value ? "Up to 6 attachments, 8 MB total; each photo 4 MB, text file 2 MB / 500,000 characters." : "最多 6 个附件、共 8 MB；单张照片不超过 4 MB，文本文件不超过 2 MB / 50 万字。")
      : reason === "empty" ? (isEnglish.value ? "The selected file is empty." : "选中的文件没有内容。")
      : reason === "type" ? (isEnglish.value ? "Choose a text/code file, or a JPEG, PNG, GIF or WebP photo. PDF and Office files are not supported." : "请选择文本或代码文件，照片支持 JPEG、PNG、GIF 和 WebP。暂不支持 PDF 和 Office 文件。")
      : (isEnglish.value ? "Unable to read this file. Please choose it again." : "无法读取该文件，请重新选择。");
  } finally {
    if (epoch === uploadEpoch) { reading.value = false; emit("reading", false); }
  }
}
watch(() => props.modelValue, () => nextTick(resize));
watch(() => props.contextKey, () => {
  uploadEpoch++;
  reading.value = false;
  emit("reading", false);
  attachmentError.value = "";
  closeMenus();
});
watch(locked, value => { if (value) closeMenus(); });
onMounted(() => { document.addEventListener("pointerdown", outside); resize(); });
onBeforeUnmount(() => { uploadEpoch++; document.removeEventListener("pointerdown", outside); });
</script>

<template>
  <div ref="root" class="ai-composer" @keydown="escape">
    <input ref="fileInput" class="composer-file-input" type="file" multiple :accept="CHAT_FILE_ACCEPT" @change="upload($event, 'file')" />
    <input ref="photoInput" class="composer-file-input" type="file" multiple :accept="CHAT_PHOTO_ACCEPT" @change="upload($event, 'image')" />
    <div v-if="attachments.length" class="composer-attachments" :aria-label="isEnglish ? 'Attachments' : '附件'">
      <div v-for="item in attachments" :key="item.id" class="composer-attachment" :class="{ 'composer-attachment--image': item.type === 'image' }">
        <img v-if="item.type === 'image'" :src="item.content" :alt="item.name" />
        <FileText v-else :size="24" aria-hidden="true" />
        <div class="composer-attachment__name"><strong :title="item.name">{{ item.name }}</strong><span>{{ item.type === 'image' ? (isEnglish ? 'Photo' : '照片') : (isEnglish ? 'Text file' : '文本文件') }}</span></div>
        <button type="button" class="composer-attachment__remove" :disabled="locked" :aria-label="`${isEnglish ? 'Remove' : '移除'} ${item.name}`" @click="emit('update:attachments', attachments.filter(file => file.id !== item.id))"><X :size="14" /></button>
      </div>
    </div>
    <textarea ref="area" class="composer-textarea" rows="3" :value="modelValue" :disabled="disabled || streaming" :placeholder="t('chat.placeholder')" :aria-label="t('chat.question')" @input="input" @keydown="keydown" />
    <p v-if="modelValue.trim().length > 4000" class="composer-notice" role="status">{{ t('chat.error.tooLong') }}</p>
    <p v-if="attachmentError" class="composer-notice" role="alert">{{ attachmentError }}</p>
    <p v-if="reading" class="composer-notice" role="status">{{ isEnglish ? 'Reading attachments…' : '正在读取附件…' }}</p>
    <div class="composer-toolbar">
      <div class="composer-toolbar__left">
        <button class="composer-tool composer-plus" data-trigger="actions" type="button" :disabled="locked" :aria-label="isEnglish ? 'Add attachment or Skill' : '添加附件或 Skill'" :aria-expanded="menu === 'actions'" aria-haspopup="menu" @click="toggleMenu('actions')"><Plus :size="20" :class="{ 'composer-plus--open': menu === 'actions' }" /></button>
        <button class="composer-model" data-trigger="model" type="button" :disabled="locked" :aria-expanded="menu === 'model'" aria-haspopup="menu" :aria-label="isEnglish ? 'Choose model' : '选择模型'" @click="toggleMenu('model')"><span>DeepSeek V4.1 Flash</span><ChevronDown :size="14" :class="{ 'composer-chevron--open': menu === 'model' }" /></button>
      </div>
      <div class="composer-toolbar__right">
        <div class="composer-chapter-picker">
          <button class="composer-chapter" data-trigger="chapter" type="button" :disabled="locked" :aria-expanded="menu === 'chapter'" aria-haspopup="listbox" :aria-label="isEnglish ? 'Choose chapter' : '选择章节'" @click="toggleMenu('chapter')"><span>{{ chapterLabel }}</span><ChevronDown :size="14" :class="{ 'composer-chevron--open': menu === 'chapter' }" /></button>
          <Transition name="composer-pop">
            <div v-if="menu === 'chapter'" class="composer-chapter-menu" role="listbox" :aria-label="isEnglish ? 'Chapters' : '章节列表'">
              <button v-for="option in chapterOptions" :key="option.value" type="button" role="option" :aria-selected="chapterId === option.value" :class="{ 'composer-chapter-option--selected': chapterId === option.value }" @click="selectChapter(option.value)">{{ option.label }}</button>
            </div>
          </Transition>
        </div>
        <button class="composer-thinking" type="button" :class="{ 'composer-thinking--on': thinkingEnabled }" :disabled="locked" :aria-pressed="thinkingEnabled" @click="emit('update:thinkingEnabled', !thinkingEnabled)"><span class="composer-brain" aria-hidden="true" v-html="brainSvg" /><span>{{ isEnglish ? 'Deep thinking' : '深度思考' }}</span></button>
        <button v-if="streaming" class="composer-send composer-send--stop" type="button" :aria-label="t('chat.stop')" :title="t('chat.stop')" @click="emit('stop')"><Square :size="16" fill="currentColor" /><span class="composer-visually-hidden">{{ t('chat.stop') }}</span></button>
        <button v-else class="composer-send" type="button" :disabled="!canSend" :aria-label="t('chat.send')" :title="t('chat.send')" @click="emit('send')"><ArrowUp :size="21" /></button>
      </div>
    </div>
    <Transition name="composer-pop">
      <div v-if="menu === 'actions'" class="composer-menu composer-menu--actions" role="menu">
        <button type="button" role="menuitem" @click="pick('file')"><Upload :size="17" /><span>{{ t('chat.uploadFile') }}</span></button>
        <button type="button" role="menuitem" @click="pick('image')"><Image :size="17" /><span>{{ t('chat.uploadPhoto') }}</span></button>
        <div class="composer-menu__divider" role="separator" />
        <div class="composer-skill" @mouseenter="skillOpen = true" @mouseleave="skillOpen = false">
          <button type="button" role="menuitem" aria-haspopup="menu" :aria-expanded="skillOpen" @focus="skillOpen = true" @click="skillOpen = !skillOpen" @keydown.right.prevent="skillOpen = true"><Puzzle :size="17" /><span>{{ t('chat.skill') }}</span><ChevronRight :size="15" /></button>
          <div v-if="skillOpen" class="composer-skill__panel" role="menu"><span>{{ isEnglish ? 'No Skills yet' : '暂无Skill' }}</span></div>
        </div>
      </div>
    </Transition>
    <Transition name="composer-pop">
      <div v-if="menu === 'model'" class="composer-menu composer-menu--model">
        <div class="composer-model-list" role="menu" :aria-label="isEnglish ? 'Models' : '模型'">
          <div class="composer-model-row" @mouseenter="modelPanel = modelPanel === 'edit' ? 'edit' : 'info'">
            <button type="button" class="composer-model-choice" role="menuitemradio" aria-checked="true" @click="closeMenus"><span>DeepSeek V4.1 Flash</span><Check :size="15" /></button>
            <button type="button" class="composer-model-edit" :aria-label="isEnglish ? 'Edit reasoning effort' : '编辑思考强度'" :aria-pressed="modelPanel === 'edit'" @click="modelPanel = modelPanel === 'edit' ? 'info' : 'edit'"><Pencil :size="15" /></button>
          </div>
        </div>
        <div class="composer-model-detail">
          <template v-if="modelPanel === 'info'">
            <strong>DeepSeek V4.1 Flash</strong>
            <p>{{ isEnglish ? 'Fast answers for learning and coding. Supports text, photos and 1M context; enable deep thinking for more complex questions.' : '快速响应学习问答与代码分析，支持文本、图片和 1M 上下文。复杂问题可开启深度思考，选择合适的推理强度。' }}</p>
          </template>
          <template v-else>
            <strong class="composer-effort-title">{{ isEnglish ? 'Reasoning effort' : '思考强度' }}</strong>
            <div class="composer-efforts" :title="!thinkingEnabled ? thinkingHint : undefined" role="group" :aria-label="isEnglish ? 'Reasoning effort' : '思考强度'">
              <span v-for="option in effortOptions" :key="option.value" class="composer-effort-wrap" :title="!thinkingEnabled ? thinkingHint : undefined" :tabindex="!thinkingEnabled ? 0 : undefined">
                <button type="button" class="composer-effort" :disabled="!thinkingEnabled" :aria-pressed="reasoningEffort === option.value" :aria-describedby="!thinkingEnabled ? 'chat-thinking-hint' : undefined" @click="emit('update:reasoningEffort', option.value)"><Check :size="14" :style="{ opacity: reasoningEffort === option.value ? 1 : 0 }" /><span><b>{{ option.label }}</b><small>{{ option.description }}</small></span></button>
              </span>
            </div>
            <p v-if="!thinkingEnabled" id="chat-thinking-hint" class="composer-effort-hint">{{ thinkingHint }}</p>
          </template>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.ai-composer { position: relative; flex: 0 0 auto; width: 100%; border: 2px solid var(--line-strong); border-radius: 28px; padding: 16px; background: var(--surface); color: var(--text); box-shadow: 0 1px 2px rgb(8 8 8 / .04), 0 8px 24px -12px rgb(8 8 8 / .14); transition: box-shadow .28s cubic-bezier(.2, 0, 0, 1), border-color .2s; }
.ai-composer:focus-within { box-shadow: 0 2px 8px rgb(8 8 8 / .06), 0 16px 48px -12px rgb(8 8 8 / .22); border-color: color-mix(in srgb, var(--text) 32%, var(--surface)); }
.composer-file-input { display: none; }
.composer-visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.composer-textarea { display: block; width: 100%; height: 84px; min-height: 0; max-height: 196px; padding: 0 4px; resize: none; border: 0; border-radius: 0; outline: none; box-shadow: none; background: transparent; color: var(--text); font: inherit; font-size: 18px; line-height: 28px; }
.composer-textarea::placeholder { color: var(--text-muted); }
.composer-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--line); }
.composer-toolbar__left, .composer-toolbar__right { display: flex; align-items: center; gap: 6px; min-width: 0; }
.composer-chapter-picker { position: relative; flex: 0 0 auto; }
.ai-composer button { font: inherit; cursor: pointer; color: inherit; border: 0; background: transparent; }
.ai-composer button:disabled { cursor: default; opacity: .4; }
.ai-composer button:focus-visible { outline: 2px solid var(--text); outline-offset: 3px; }
.composer-tool { display: grid; place-items: center; width: 36px; height: 36px; flex: 0 0 36px; border-radius: 12px; transition: background .2s, transform .2s; }
.composer-tool:hover:not(:disabled), .composer-model:hover:not(:disabled) { background: color-mix(in srgb, var(--text) 7%, transparent); }
.composer-plus svg, .composer-model svg { transition: transform .22s cubic-bezier(.2, 0, 0, 1); }
.composer-plus--open { transform: rotate(45deg); }
.composer-chevron--open { transform: rotate(180deg); }
.composer-model { display: flex; align-items: center; gap: 8px; min-height: 36px; padding: 6px 9px; border-radius: 12px; font-size: 14px !important; font-weight: 600 !important; white-space: nowrap; }
.composer-chapter { display: flex; align-items: center; justify-content: center; gap: 7px; min-height: 36px; max-width: 176px; padding: 6px 12px; border: 1px solid color-mix(in srgb, var(--text) 10%, transparent) !important; border-radius: 999px; background: color-mix(in srgb, var(--surface) 38%, transparent) !important; color: var(--text); font-size: 14px !important; font-weight: 600 !important; white-space: nowrap; -webkit-backdrop-filter: blur(7px) saturate(1.14); backdrop-filter: blur(7px) saturate(1.14); box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset 0 0 2px color-mix(in srgb, var(--text) 32%, transparent), 0 4px 8px color-mix(in srgb, var(--text) 17%, transparent); transition: transform 180ms ease, filter 180ms ease, background-color 180ms ease; }
.composer-chapter > span { overflow: hidden; text-overflow: ellipsis; }
.composer-chapter:hover:not(:disabled) { transform: translateY(-1px) scale(1.02); filter: brightness(1.06); }
.composer-chapter-menu { position: absolute; z-index: 30; right: 0; bottom: calc(100% + 8px); width: 224px; max-height: min(380px, calc(100vh - 140px)); overflow-y: auto; padding: 6px; border: 2px solid var(--line-strong); border-radius: 16px; background: var(--surface); box-shadow: 0 8px 30px -8px rgb(8 8 8 / .18), 0 2px 8px -2px rgb(8 8 8 / .08); }
.composer-chapter-menu button { display: flex; align-items: center; width: 100%; min-height: 38px; padding: 9px 12px; border-radius: 10px; color: var(--text); text-align: left; font-size: 14px; font-weight: 500; }
.composer-chapter-menu button + button { border-top: 1px solid color-mix(in srgb, var(--text) 10%, transparent); border-top-left-radius: 0; border-top-right-radius: 0; }
.composer-chapter-menu button:hover:not(:disabled), .composer-chapter-option--selected { background: color-mix(in srgb, var(--text) 8%, transparent); }
.composer-thinking { display: flex; align-items: center; justify-content: center; gap: 7px; min-height: 36px; padding: 6px 12px; border: 1px solid color-mix(in srgb, var(--text) 10%, transparent) !important; border-radius: 999px; background: color-mix(in srgb, var(--surface) 38%, transparent) !important; font-size: 14px !important; font-weight: 600 !important; white-space: nowrap; -webkit-backdrop-filter: blur(7px) saturate(1.14); backdrop-filter: blur(7px) saturate(1.14); box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 88%, transparent), inset 0 0 2px color-mix(in srgb, var(--text) 32%, transparent), 0 4px 8px color-mix(in srgb, var(--text) 17%, transparent); transition: transform 180ms ease, filter 180ms ease, background-color 180ms ease, box-shadow 180ms ease; }
.composer-thinking:hover:not(:disabled) { transform: translateY(-1px) scale(1.02); filter: brightness(1.06); }
.ai-composer .composer-thinking--on { background: color-mix(in srgb, var(--surface) 62%, var(--text) 38%) !important; color: var(--text); border-color: color-mix(in srgb, var(--text) 34%, transparent) !important; box-shadow: inset 2px -2px 1px -1px color-mix(in srgb, var(--surface) 94%, transparent), inset -2px 2px 1px -1px color-mix(in srgb, var(--surface) 94%, transparent), inset 0 0 3px color-mix(in srgb, var(--text) 58%, transparent), 0 5px 12px color-mix(in srgb, var(--text) 22%, transparent); }
.ai-composer .composer-thinking--on:hover:not(:disabled) { filter: brightness(1.1); }
.composer-brain { display: flex; }
.composer-brain :deep(svg) { width: 18px; height: 18px; }
.ai-composer .composer-send { display: grid; place-items: center; width: 38px; height: 38px; flex: 0 0 38px; border-radius: 50%; background: var(--text); color: var(--surface); transition: opacity .2s, transform .2s, box-shadow .2s; }
.composer-send:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 10px rgb(0 0 0 / .15); }
.composer-send:active:not(:disabled) { transform: scale(.94); }
.composer-menu { position: absolute; z-index: 25; bottom: 70px; left: 16px; transform-origin: bottom left; font-size: 14px; }
.composer-menu--actions, .composer-model-list, .composer-model-detail, .composer-skill__panel { padding: 6px; border: 2px solid var(--line-strong); border-radius: 16px; background: var(--surface); box-shadow: 0 8px 30px -8px rgb(8 8 8 / .18), 0 2px 8px -2px rgb(8 8 8 / .08); }
.composer-menu--actions { width: 224px; }
.composer-menu--actions button { display: flex; align-items: center; gap: 10px; width: 100%; min-height: 38px; padding: 9px 12px; border-radius: 12px; text-align: left; font-size: 14px; font-weight: 500; }
.composer-menu--actions button span { flex: 1; }
.composer-menu button:hover:not(:disabled) { background: color-mix(in srgb, var(--text) 7%, transparent); }
.composer-menu__divider { height: 1px; margin: 6px 4px; background: var(--line-strong); }
.composer-skill { position: relative; }
.composer-skill__panel { position: absolute; left: calc(100% + 10px); bottom: -8px; width: 174px; min-height: 54px; display: grid; align-items: center; padding: 14px; color: var(--text-muted); }
.composer-menu--model { top: auto; bottom: 62px; left: 54px; display: flex; align-items: flex-end; gap: 12px; transform-origin: bottom left; }
.composer-model-list { width: 276px; }
.composer-model-row { display: flex; align-items: center; border-radius: 12px; background: color-mix(in srgb, var(--text) 5%, transparent); }
.composer-model-choice { flex: 1; display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 42px; padding: 10px 12px; border-radius: 12px; font-size: 14px !important; font-weight: 500 !important; white-space: nowrap; }
.composer-model-edit { display: grid; place-items: center; width: 32px; height: 32px; margin-right: 5px; border-radius: 10px; }
.composer-model-detail { width: 248px; padding: 14px; }
.composer-model-detail > strong { font-size: 14px; font-weight: 650; }
.composer-model-detail p { margin: 8px 0 0; color: var(--text-muted); font-size: 13px; line-height: 1.6; }
.composer-efforts { display: grid; gap: 2px; margin-top: 8px; }
.composer-effort-wrap { display: block; border-radius: 10px; }
.composer-effort { display: flex; align-items: center; gap: 8px; width: 100%; padding: 8px; border-radius: 10px; text-align: left; font-size: 13px !important; }
.composer-effort b { display: block; font-size: 13px; font-weight: 500; }
.composer-effort small { display: block; margin-top: 3px; color: var(--text-muted); font-size: 11px; }
.composer-notice { margin: 4px; color: var(--text-muted); font-size: 13px; line-height: 1.5; }
.composer-attachments { display: flex; flex-wrap: wrap; gap: 10px; padding: 4px 0 16px; }
.composer-attachment { position: relative; display: flex; align-items: center; gap: 10px; width: min(250px, 100%); min-height: 68px; padding: 12px 27px 12px 12px; border: 1px solid var(--line-strong); border-radius: 14px; background: color-mix(in srgb, var(--text) 3%, var(--surface)); }
.composer-attachment > svg { flex: 0 0 24px; color: var(--text-muted); }
.composer-attachment__name { display: grid; gap: 4px; min-width: 0; }
.composer-attachment__name strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 600; }
.composer-attachment__name span { font-size: 11px; color: var(--text-muted); }
.composer-attachment--image { width: 88px; height: 68px; min-height: 68px; padding: 0; overflow: hidden; }
.composer-attachment--image img { width: 100%; height: 100%; object-fit: cover; }
.composer-attachment--image .composer-attachment__name { display: none; }
.ai-composer .composer-attachment__remove { position: absolute; top: 4px; right: 4px; display: grid; place-items: center; width: 22px; height: 22px; padding: 0; line-height: 0; border-radius: 50%; background: var(--surface); box-shadow: 0 1px 4px rgb(0 0 0 / .12); }
.ai-composer .composer-attachment__remove svg { display: block; }
.composer-pop-enter-active, .composer-pop-leave-active { transition: opacity .18s cubic-bezier(.2, 0, 0, 1), transform .18s cubic-bezier(.2, 0, 0, 1); }
.composer-pop-enter-from, .composer-pop-leave-to { opacity: 0; transform: translateY(8px) scale(.97); }
@media (max-width: 1100px) { .composer-menu--model { left: 16px; } }
@media (max-width: 700px) {
  .ai-composer { padding: 14px; }
  .composer-toolbar { flex-wrap: wrap; }
  .composer-toolbar__right { margin-left: auto; }
  .composer-menu--model { flex-direction: column; gap: 8px; width: min(276px, calc(100% - 32px)); }
  .composer-model-list, .composer-model-detail { width: 100%; }
  .composer-skill__panel { width: 128px; }
}
@media (max-width: 400px) { .composer-menu--actions { width: 168px; } .composer-model { font-size: 12px !important; padding-inline: 4px; } }
@media (prefers-reduced-motion: reduce) { .ai-composer *, .composer-pop-enter-active, .composer-pop-leave-active { transition: none !important; } }
</style>
