import { computed, readonly, ref } from "vue";

export type ThemeMode = "light" | "dark";

export const THEME_STORAGE_KEY = "structify-theme";

const DEFAULT_THEME: ThemeMode = "light";
const theme = ref<ThemeMode>(DEFAULT_THEME);
let initialized = false;

function isThemeMode(value: unknown): value is ThemeMode {
  return value === "light" || value === "dark";
}

function readStoredTheme(): ThemeMode {
  if (typeof window === "undefined") return DEFAULT_THEME;

  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isThemeMode(stored) ? stored : DEFAULT_THEME;
  } catch {
    return DEFAULT_THEME;
  }
}

function persistTheme(nextTheme: ThemeMode) {
  if (typeof window === "undefined") return;

  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
  } catch {
    // Storage can be unavailable in private or restricted browser contexts.
  }
}

function applyThemeToDocument(nextTheme: ThemeMode) {
  if (typeof document === "undefined") return;

  document.documentElement.dataset.theme = nextTheme;
  document.documentElement.style.colorScheme = nextTheme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", nextTheme === "dark" ? "#181818" : "#f3f3f1");
}

export function setTheme(nextTheme: ThemeMode) {
  theme.value = nextTheme;
  applyThemeToDocument(nextTheme);
  persistTheme(nextTheme);
}

export function initializeTheme() {
  if (initialized) return theme.value;

  initialized = true;
  theme.value = readStoredTheme();
  applyThemeToDocument(theme.value);
  return theme.value;
}

export function toggleTheme() {
  setTheme(theme.value === "light" ? "dark" : "light");
}

export function useTheme() {
  initializeTheme();

  return {
    theme: readonly(theme),
    isDark: computed(() => theme.value === "dark"),
    setTheme,
    toggleTheme,
  };
}
