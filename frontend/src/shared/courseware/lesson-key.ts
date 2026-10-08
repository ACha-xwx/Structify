/**
 * Where a piece of reviewed textbook material lives in the courseware.
 *
 * The knowledge base answers a question with a source path such as
 * {@code textbook/lessons/06-06-树与二叉树-哈夫曼树及应用-已核验页188-197.md}. The lesson number inside
 * that name - {@code 06-06} - is the courseware key the deck endpoints are addressed by, so a citation can
 * open the deck that actually teaches it. The rule mirrors the server's own
 * ({@code TextbookLessonIds.coursewareKey}), so the client and the classroom agree on what a lesson is.
 */

/** The lesson number inside a reviewed source path; empty when the name carries none. */
export function coursewareKeyOf(source: string): string {
  if (!source) return "";
  const name = source.replace(/\\/g, "/").split("/").pop() ?? "";
  const withoutExtension = name.endsWith(".md") ? name.slice(0, -3) : name;
  const reviewed = withoutExtension.indexOf("-已核验页");
  const stem = reviewed >= 0 ? withoutExtension.slice(0, reviewed) : withoutExtension;
  return /^(\d{2}-\d{2})/.exec(stem)?.[1] ?? "";
}

export interface CoursewareTarget {
  path: string;
  query: Record<string, string>;
}

/**
 * The courseware route a citation opens, or null when the source names no lesson - in that case the
 * citation stays plain text rather than leading somewhere unrelated.
 */
export function coursewareTarget(source: string, slideId?: string | null): CoursewareTarget | null {
  const key = coursewareKeyOf(source);
  if (!key) return null;
  return { path: "/courseware", query: slideId ? { courseware: key, slide: slideId } : { courseware: key } };
}
