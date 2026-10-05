import { describe, expect, it } from "vitest";
import { describeDeck } from "./deck-labels";

function deck(title: string, chapter = "") {
  return { title, chapter, deckId: "source-deck", slideCount: 12, coverSlideId: "cover" };
}

describe("courseware display names", () => {
  it.each([
    ["数据结构-第一章-绪论01", "01", "绪论 1"],
    ["数据结构-第一章-绪论02.pptx", "01", "绪论 2"],
    ["第二章-线性表03", "02", "线性表 3"],
    ["数据结构-第三章-限定性线性表-01", "03", "栈和队列 1"],
    ["数据结构-第三章-限定性线性表-02", "03", "栈和队列 2"],
    ["第四章-串", "04", "串"],
    ["第五章-数组和广义表-02", "05", "数组和广义表 2"],
    ["第六章-树和二叉树03-", "06", "树和二叉树 3"],
    ["第七章-图02(1)", "07", "图 2"],
    ["第八章-查找04", "08", "查找 4"],
    ["第九章-排序01-分享", "09", "排序 1"],
    ["第九章-排序01-分享.pptx", "09", "排序 1"],
  ])("formats %s without changing chapter grouping", (title, chapter, expected) => {
    expect(describeDeck(deck(title, chapter))).toEqual({
      chapterKey: chapter,
      chapterName: expected.replace(/ \d+$/, ""),
      title: expected,
    });
  });

  it("derives the chapter from the title when metadata is absent", () => {
    expect(describeDeck(deck("第六章-树和二叉树01-"))).toEqual({
      chapterKey: "06", chapterName: "树和二叉树", title: "树和二叉树 1",
    });
    expect(describeDeck(deck("栈与队列-02"))).toEqual({
      chapterKey: "03", chapterName: "栈和队列", title: "栈和队列 2",
    });
  });

  it("accepts named chapter metadata and does not invent a section number", () => {
    expect(describeDeck(deck("限定性线性表", "栈与队列"))).toEqual({
      chapterKey: "03", chapterName: "栈和队列", title: "栈和队列",
    });
  });

  it("keeps unknown and local demonstration decks available", () => {
    expect(describeDeck(deck("Structify 本地演示课件", "demo"))).toEqual({
      chapterKey: "other:demo", chapterName: "demo", title: "Structify 本地演示课件",
    });
    expect(describeDeck(deck("补充练习"))).toEqual({
      chapterKey: "other:", chapterName: "", title: "补充练习",
    });
  });
});
