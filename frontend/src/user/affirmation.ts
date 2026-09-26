/**
 * Deciding whether a short reply means "yes, show it".
 *
 * A learner who has just been offered an animation answers in whatever words come to mind — "好的",
 * "okok", "嗯嗯", "来一个", "整", "sure", "go ahead", "👍" — and no closed list of whole answers can
 * cover that. This module judges the reply the way a person does: fold the stutter, look for a
 * positive cue, and let any hint of a refusal or a new question win. Anything genuinely ambiguous
 * stays a normal question, which is the safe direction: the offer's button still builds the demo.
 *
 * It reads one reply, never a conversation, so it stays pure and testable.
 */

/** Punctuation, whitespace and filler separators carry no decision. */
const IGNORED = /[\s，。！？,.!?~～、;；:："'“”‘’()\[\]{}<>《》\-_*#@]/g;

/** A reply that asks something is a new question, however short: "那队列呢？" is not a yes. */
const QUESTION_CUES = ["吗", "呢", "什么", "怎么", "怎样", "为什么", "为何", "哪", "谁", "几", "多少", "能不能", "可不可以", "是不是", "?", "？"];

/**
 * English question words need word boundaries: "show" contains "how", and "show me" is an answer.
 */
const QUESTION_WORDS = /\b(what|how|why|when|where|which|who)\b/;

/** Agreement wearing a refusal character: "没问题" contains 没, "why not" contains not. */
const POSITIVE_OVERRIDES = ["没问题", "没毛病", "没意见", "没说的", "不赖", "不错", "不来才怪"];

/** A refusal wins over any positive word sitting next to it: "不看了" is not a yes. */
const NEGATIVE_CUES = ["不", "没", "别", "算了", "免了", "不用", "不必", "先不", "不用了", "算了", "no", "nope", "nah", "not", "don", "dont", "stop", "cancel", "skip", "later", "forget"];

/**
 * Positive cues are matched as substrings on purpose. "好呀", "好嘞", "好滴", "妥了", "来一个吧",
 * "安排上", "整一个", "走起" and their unthought-of cousins all contain one of these roots.
 */
const POSITIVE_CUES = [
  "好", "行", "可以", "要", "想要", "想", "看", "来", "嗯", "哦", "噢", "成", "妥", "赞",
  "安排", "搞", "整", "做", "演", "演示", "试", "开始", "继续", "上吧", "来吧", "就来",
  "当然", "没问题", "必须", "走起", "yeah", "yep", "yup", "yes",
  "ok", "okay", "okie", "okey", "sure", "go", "do", "show", "please", "course", "fine", "alright",
  "sounds", "lets", "let's", "why not", "absolutely", "definitely", "roger", "aye", "ahead", "bring",
];

/**
 * Phrases that mean yes on their own at any length. "给我演示一下入栈出栈" is a request for the demo —
 * "我想知道栈和队列的区别" is a new question — and only the first contains one of these.
 */
const EXPLICIT_YES = [
  "好的", "好呀", "好嘞", "好滴", "好哒", "好啊", "可以", "没问题", "当然", "要得", "要的",
  "来吧", "就来", "来一个", "来个", "看一下", "想看", "试试", "演示", "开始", "继续", "走起",
  "安排", "整一个", "搞起", "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "okey", "course",
  "alright", "sounds", "ahead", "do it", "show me", "why not", "please", "absolutely",
  "definitely", "fine", "roger", "aye",
];

/** Agreement is often sent as a picture. */
const POSITIVE_EMOJI = ["👍", "👌", "🙌", "👏", "✅", "🆗", "😀", "😄", "😊", "🤝", "💪", "🙏", "👀", "🎬", "▶️"];

/** A real question or a real refusal is longer than a yes; past this, ask the model instead. */
const MAX_AFFIRMATION_LENGTH = 20;

/** How long a reply may be before a bare cue ("想", "看") is no longer trusted as agreement. */
const SHORT_REPLY_LENGTH = 6;

/**
 * "okok" -> "ok", "好的好的" -> "好的", "嗯嗯嗯" -> "嗯".
 *
 * Enthusiasm repeats the whole word, and matching roots against the folded form is what makes the
 * doubled spellings land instead of falling through to a fresh question.
 */
function foldPeriod(source: string): string | null {
  for (let period = 1; period <= Math.floor(source.length / 2); period += 1) {
    if (source.length % period !== 0) continue;
    const unit = source.slice(0, period);
    if (unit.repeat(source.length / period) === source) return unit;
  }
  return null;
}

/** Trailing particles stand between the stutter and the word: "行行行吧" folds through "行行行". */
const TAIL_PARTICLES = "吧呀了啊哦呃滴哒嘞嘛呐咯啦喽";

function foldRepeats(text: string): string {
  const source = text.toLowerCase();
  const exact = foldPeriod(source);
  if (exact) return exact;
  const last = source.slice(-1);
  if (last && TAIL_PARTICLES.includes(last)) {
    const folded = foldPeriod(source.slice(0, -1));
    if (folded) return folded;
  }
  return source;
}

/**
 * True when the reply reads as taking up an offer.
 *
 * The cue is only trusted while the reply is short and carries no question or refusal, so a learner
 * who actually asks something new gets an answer rather than a demo they did not ask for.
 */
export function isAffirmation(raw: string): boolean {
  // Punctuation becomes a space so phrases stay whole: "show me" must not collapse into "showme".
  const spaced = (raw ?? "").replace(IGNORED, " ").replace(/\s+/g, " ").trim().toLowerCase();
  const compact = spaced.replace(/\s+/g, "");
  if (!compact || compact.length > MAX_AFFIRMATION_LENGTH) return false;

  const folded = foldRepeats(compact);
  const haystack = `${folded} ${compact} ${spaced}`;

  // Two shapes read like a refusal and mean yes, so they are settled before the refusal check.
  if (/\bwhy\s*not\b|为什么不/.test(spaced)) return true;
  if (POSITIVE_OVERRIDES.some((cue) => haystack.includes(cue))) return true;
  if (QUESTION_CUES.some((cue) => compact.includes(cue)) || QUESTION_WORDS.test(spaced)) return false;
  if (NEGATIVE_CUES.some((cue) => haystack.includes(cue))) return false;

  if (POSITIVE_EMOJI.some((emoji) => compact.includes(emoji))) return true;
  if (EXPLICIT_YES.some((cue) => haystack.includes(cue))) return true;
  // Anything longer has room to be a real question, so only a short reply is trusted on a bare cue.
  return folded.length <= SHORT_REPLY_LENGTH && POSITIVE_CUES.some((cue) => haystack.includes(cue));
}
