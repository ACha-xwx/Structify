/**
 * How often a running preparation is asked whether it has finished.
 *
 * Preparing a lesson is a server-side job that takes a minute or more, and the page only needs to learn
 * when it ends. Asking on a fixed short interval spends dozens of requests on the same "still working"
 * answer - over a link that pays most of a second for each one, that is the difference between a page
 * that feels idle and one that feels like it is being hammered.
 *
 * The gap therefore widens after each unanswered poll, but never past a ceiling that still reports a
 * finished lesson promptly, and starts near-instantly because a small lesson can be ready at once.
 */
const FIRST_MS = 1200;
const GROWTH = 1.7;
const CEILING_MS = 6000;

/** Delay before poll number `attempt` (0 = the first one). */
export function preparationPollDelayMs(attempt: number): number {
  const step = Number.isFinite(attempt) && attempt > 0 ? Math.floor(attempt) : 0;
  if (step === 0) return FIRST_MS;
  return Math.min(CEILING_MS, Math.round(FIRST_MS * GROWTH ** step));
}

/** Polls one preparation of this length costs, counted the way the page schedules them. */
export function preparationPollCount(durationMs: number): number {
  let elapsed = 0;
  let polls = 0;
  while (elapsed < durationMs) {
    elapsed += preparationPollDelayMs(polls);
    polls += 1;
  }
  return polls;
}
