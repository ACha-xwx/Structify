import { beforeEach, describe, expect, it } from "vitest";
import {
  DEFAULT_LOCALE,
  LOCALE_STORAGE_KEY,
  isLocale,
  setLocale,
  toggleLocale,
  useLocale,
} from "./locale";

describe("locale store", () => {
  beforeEach(() => {
    window.localStorage.removeItem(LOCALE_STORAGE_KEY);
    setLocale(DEFAULT_LOCALE);
  });

  it("supports only the application locales", () => {
    expect(isLocale("zh-CN")).toBe(true);
    expect(isLocale("en-US")).toBe(true);
    expect(isLocale("fr-FR")).toBe(false);
    expect(isLocale(null)).toBe(false);
  });

  it("defaults to Chinese and persists a selected locale", () => {
    const state = useLocale();

    expect(state.locale.value).toBe("zh-CN");
    expect(document.documentElement.lang).toBe("zh-CN");

    setLocale("en-US");

    expect(state.locale.value).toBe("en-US");
    expect(document.documentElement.lang).toBe("en-US");
    expect(document.documentElement.dataset.locale).toBe("en-US");
    expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("en-US");
  });

  it("toggles between Chinese and English without changing theme state", () => {
    setLocale("zh-CN");
    document.documentElement.dataset.theme = "dark";

    toggleLocale();
    expect(document.documentElement.lang).toBe("en-US");
    expect(document.documentElement.dataset.theme).toBe("dark");

    toggleLocale();
    expect(document.documentElement.lang).toBe("zh-CN");
    expect(document.documentElement.dataset.theme).toBe("dark");
  });
});
