import { describe, expect, it } from "vitest";
import { messages, translate, type MessageKey } from "./messages";

const keys = Object.keys(messages) as MessageKey[];

describe("message catalog", () => {
  it("has copy for both languages on every key", () => {
    for (const key of keys) {
      expect(messages[key].zh.trim(), `${key}.zh`).not.toBe("");
      expect(messages[key].en.trim(), `${key}.en`).not.toBe("");
    }
  });

  it("keeps the placeholders of a key identical in both languages", () => {
    // A translation that drops `{value}` would silently print a sentence with a hole in it.
    for (const key of keys) {
      const placeholders = (template: string) => (template.match(/\{(\w+)\}/g) ?? []).sort().join(",");
      expect(placeholders(messages[key].en), `${key} placeholders`).toBe(placeholders(messages[key].zh));
    }
  });

  it("translates into the requested language and fills placeholders", () => {
    expect(translate("home.title", "zh-CN")).toBe("从哪开始？");
    expect(translate("home.title", "en-US")).toBe("Where do you want to start?");
    expect(translate("classroom.step", "zh-CN", { index: 2, total: 7 })).toBe("第 2 / 7 步");
    expect(translate("classroom.step", "en-US", { index: 2, total: 7 })).toBe("Step 2 of 7");
  });

  it("leaves an unfilled placeholder visible instead of printing undefined", () => {
    expect(translate("classroom.step", "en-US", { index: 1 })).toContain("{total}");
  });

  it("covers the learner chrome that used to be hard-coded Chinese only", () => {
    // The switch was reported as doing nothing; these are the surfaces it has to move.
    for (const key of ["home.title", "auth.title.login", "classroom.start", "lab.title", "player.play", "courseware.next"] as MessageKey[]) {
      expect(messages[key].en).not.toBe(messages[key].zh);
    }
  });
});
