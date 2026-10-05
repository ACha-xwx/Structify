import type { PresentationDeck } from "../types/contracts";

const CHAPTERS = [
  { name: "绪论", aliases: ["绪论"] },
  { name: "线性表", aliases: ["线性表"] },
  { name: "栈和队列", aliases: ["限定性线性表", "栈和队列", "栈与队列"] },
  { name: "串", aliases: ["串"] },
  { name: "数组和广义表", aliases: ["数组和广义表", "数组与广义表"] },
  { name: "树和二叉树", aliases: ["树和二叉树", "树与二叉树"] },
  { name: "图", aliases: ["图"] },
  { name: "查找", aliases: ["查找"] },
  { name: "排序", aliases: ["排序"] },
];
const CHINESE_NUMBERS = "一二三四五六七八九";
const CHAPTER_PREFIX = /^第([一二三四五六七八九\d]+)章[\s_-]*/;

function chapterNumber(value: string): number | undefined {
  const number = /^\d+$/.test(value) ? Number(value)
    : value.length === 1 ? CHINESE_NUMBERS.indexOf(value) + 1 : 0;
  return number >= 1 && number <= CHAPTERS.length ? number : undefined;
}

/** Display labels only: the original metadata remains the API and classroom selection contract. */
export function describeDeck(deck: PresentationDeck): { chapterKey: string; chapterName: string; title: string } {
  const chapter = deck.chapter.trim();
  const filename = deck.title.trim().replace(/\.pptx?$/i, "").replace(/^数据结构[\s_-]*/, "");
  const titleChapter = filename.match(CHAPTER_PREFIX)?.[1];
  const title = filename.replace(CHAPTER_PREFIX, "")
    .replace(/\s*[(（]\d+[)）]\s*$/, "")
    .replace(/[\s_-]*分享$/, "")
    .replace(/[\s_-]+$/, "").trim();
  const titleName = title.replace(/[\s_-]*\d+$/, "");
  const namedChapter = CHAPTERS.findIndex((item) => item.aliases.includes(chapter) || item.aliases.includes(titleName));
  const number = chapterNumber(chapter)
    ?? chapterNumber(chapter.match(CHAPTER_PREFIX)?.[1] ?? "")
    ?? chapterNumber(titleChapter ?? "")
    ?? (namedChapter >= 0 ? namedChapter + 1 : undefined);

  if (number === undefined) {
    return { chapterKey: `other:${chapter}`, chapterName: chapter, title: title || deck.title };
  }

  const chapterName = CHAPTERS[number - 1].name;
  const section = title.match(/\d+$/)?.[0];
  return {
    chapterKey: String(number).padStart(2, "0"),
    chapterName,
    title: section ? `${chapterName} ${Number(section)}` : chapterName,
  };
}
