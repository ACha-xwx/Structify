import { computed, readonly, ref } from "vue";
import { translate, type MessageKey } from "./messages";

export type Locale = "zh-CN" | "en-US";

export const LOCALE_STORAGE_KEY = "structify-locale";
export const DEFAULT_LOCALE: Locale = "zh-CN";

const locale = ref<Locale>(DEFAULT_LOCALE);
let initialized = false;

export function isLocale(value: unknown): value is Locale {
  return value === "zh-CN" || value === "en-US";
}

function readStoredLocale(): Locale {
  if (typeof window === "undefined") return DEFAULT_LOCALE;

  try {
    const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    return isLocale(stored) ? stored : DEFAULT_LOCALE;
  } catch {
    // Storage can be unavailable in private or restricted browser contexts.
    return DEFAULT_LOCALE;
  }
}

function persistLocale(nextLocale: Locale) {
  if (typeof window === "undefined") return;

  try {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, nextLocale);
  } catch {
    // Storage can be unavailable in private or restricted browser contexts.
  }
}

function applyLocaleToDocument(nextLocale: Locale) {
  if (typeof document === "undefined") return;

  document.documentElement.lang = nextLocale;
  document.documentElement.dataset.locale = nextLocale;
}

export function setLocale(nextLocale: Locale) {
  locale.value = nextLocale;
  applyLocaleToDocument(nextLocale);
  persistLocale(nextLocale);
}

export function initializeLocale() {
  if (initialized) return locale.value;

  initialized = true;
  locale.value = readStoredLocale();
  applyLocaleToDocument(locale.value);
  return locale.value;
}

export function toggleLocale() {
  setLocale(locale.value === "zh-CN" ? "en-US" : "zh-CN");
}

export function useLocale() {
  initializeLocale();

  return {
    locale: readonly(locale),
    isEnglish: computed(() => locale.value === "en-US"),
    setLocale,
    toggleLocale,
  };
}

/**
 * The same store, plus the copy. `t` reads the locale ref while it runs, so every template or computed
 * that calls it re-renders when the language switch flips - which is what makes the switch visible.
 */
export function useI18n() {
  initializeLocale();

  return {
    locale: readonly(locale),
    isEnglish: computed(() => locale.value === "en-US"),
    setLocale,
    toggleLocale,
    t: (key: MessageKey, params?: Record<string, string | number>) => translate(key, locale.value, params),
  };
}
