import { describe, expect, it } from "vitest";
import { isAffirmation } from "./affirmation";

/**
 * The offer is only worth anything if whatever the learner types back is understood. These are the
 * spellings that have actually shown up, plus the shapes that must never be read as a yes.
 */
describe("肯定回答的判定", () => {
  const yes = [
    "好的", "好", "好呀", "好嘞", "好滴", "好哒", "好啊", "好吧",
    "ok", "OK", "okok", "okay", "okie dokie", "okey",
    "行", "行行行", "行吧", "可以", "可以可以", "可以啊", "要", "要得", "要的", "想要的",
    "嗯", "嗯嗯", "嗯嗯嗯", "哦", "噢",
    "来", "来一个", "来吧", "来一个吧", "看", "看看", "看一下", "想看", "我想看",
    "演示", "演示一下", "来个演示", "开始", "继续", "试试",
    "yes", "Yes", "yeah", "yep", "yup", "sure", "sure thing", "go ahead", "go", "do it", "show me",
    "why not", "please", "of course", "absolutely", "fine", "alright",
    "当然", "没问题", "必须的", "安排", "安排上", "整一个", "搞起", "走起", "就来",
    "👍", "👌", "✅", "好的👌", "好的，来一个", "好 的", "好的!", "好。",
  ];

  it.each(yes)("把 %s 读成答应看演示", (text) => {
    expect(isAffirmation(text)).toBe(true);
  });

  const notYes = [
    "", "   ", "不用了", "不用", "算了", "不要", "不想看", "先不看了", "没看懂", "不演示",
    "no", "nope", "nah", "not now", "don't", "stop", "cancel", "skip",
    "那队列呢？", "什么是栈？", "怎么入栈？", "为什么是后进先出？", "能不能详细点？", "how does it work?",
    "我想知道栈和队列的区别", "队列又是怎样的一种线性表呢", "讲一下树的遍历", "栈", "队列", "排序", "stack",
    "栈的入栈出栈时间复杂度是多少，顺便再说一下和队列的差别",
  ];

  it.each(notYes)("不把 %s 当成答应", (text) => {
    expect(isAffirmation(text)).toBe(false);
  });

  it("长句子交给模型当新问题，而不是猜成答应", () => {
    expect(isAffirmation("好的，顺便再帮我讲讲队列和栈在实际工程里怎么选，最好举例说明")).toBe(false);
  });
});
