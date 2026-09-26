/**
 * Stepping page by page is the main courseware interaction, and every page is a whole image. On a link
 * that pays most of a second per request, the page the reader is about to reach has to already be in the
 * browser by the time they ask for it - warming it turns 下一页 from a round trip into a local lookup.
 *
 * Slide images are content addressed and cacheable forever, so a warmed page is reused byte for byte by
 * the <img> that follows; warming a page the reader never opens costs one small idle fetch.
 */
const warmed = new Set<string>();

/** Bounded so a long browsing session cannot keep growing the bookkeeping set. */
const WARMED_LIMIT = 512;

/**
 * How far ahead of the reader pages are warmed. A page is one image, so this is the real trade: warm too
 * little and every turn waits on the network, warm too much and the idle fetches compete with the page
 * being looked at. Four pages is roughly a fifth of a megabyte once pages are served as WebP.
 */
const AHEAD = 4;

/** One page behind, so 上一页 is a local lookup too. */
const BEHIND = 1;

/**
 * Warm the pages around the one on screen, nearest first. Callers hand over the ordered pages and the
 * index being shown; only the pages that exist are warmed.
 */
export function prefetchSlideWindow(
  slides: readonly { imageUrl?: string | null }[] | null | undefined,
  index: number,
): void {
  if (!slides || !slides.length) return;
  const urls: (string | null | undefined)[] = [];
  for (let step = index + 1; step <= index + AHEAD; step += 1) urls.push(slides[step]?.imageUrl);
  for (let step = index - 1; step >= index - BEHIND; step -= 1) urls.push(slides[step]?.imageUrl);
  prefetchSlideImages(urls);
}

export function prefetchSlideImages(urls: (string | null | undefined)[]): void {
  if (typeof Image === "undefined") return;
  for (const url of urls) {
    if (!url || warmed.has(url)) continue;
    if (warmed.size >= WARMED_LIMIT) warmed.clear();
    warmed.add(url);
    const image = new Image();
    image.decoding = "async";
    image.src = url;
  }
}
