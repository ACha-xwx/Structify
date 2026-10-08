import { describe, expect, it } from "vitest";
import { coursewareKeyOf, coursewareTarget } from "./lesson-key";

describe("coursewareKeyOf", () => {
  it("reads the lesson number out of a reviewed source path", () => {
    expect(coursewareKeyOf("textbook/lessons/06-06-树与二叉树-哈夫曼树及应用-已核验页188-197.md")).toBe("06-06");
    expect(coursewareKeyOf("textbook/lessons/08-01-查找-基本概念与线性表查找-已核验页264-270.md")).toBe("08-01");
  });

  it("works without a directory or the reviewed marker", () => {
    expect(coursewareKeyOf("03-01-栈与队列-基础.md")).toBe("03-01");
    expect(coursewareKeyOf("textbook\\lessons\\01-06-绪论-数据结构与C语言表示-已核验页18-31.md")).toBe("01-06");
  });

  it("returns nothing when the name carries no lesson number", () => {
    expect(coursewareKeyOf("")).toBe("");
    expect(coursewareKeyOf("private/knowledge/dev/lessons/栈与队列.md")).toBe("");
    expect(coursewareKeyOf("textbook/lessons/README.md")).toBe("");
    expect(coursewareKeyOf("textbook/lessons/6-6-短号.md")).toBe("");
  });
});

describe("coursewareTarget", () => {
  it("opens the lesson's deck", () => {
    expect(coursewareTarget("textbook/lessons/06-06-树与二叉树-已核验页188-197.md"))
      .toEqual({ path: "/courseware", query: { courseware: "06-06" } });
  });

  it("pins the page when one is known", () => {
    expect(coursewareTarget("textbook/lessons/06-06-x-已核验页188-197.md", "ch06-deck01-abc-s012"))
      .toEqual({ path: "/courseware", query: { courseware: "06-06", slide: "ch06-deck01-abc-s012" } });
  });

  it("stays plain when no lesson can be read", () => {
    expect(coursewareTarget("notes.md")).toBeNull();
  });
});
