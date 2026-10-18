import type { UserApi } from "../../user/api";
import type { LessonCourseware, PresentationDeck, PresentationSlide } from "../types/contracts";

const MAX_DECKS = 16;
const TTL_MS = 60_000;

/** Reuse metadata within a login session; private courseware never goes into persistent storage. */
export function createCoursewareCatalog(
  api: Pick<UserApi, "listPresentationDecks" | "listDeckSlides" | "getCoursewareSlides" | "getLessonSlides">,
  sessionIdentity: () => unknown = () => api,
) {
  type Entry<T> = { value?: T; expiresAt: number; pending?: Promise<T> };
  let identity = sessionIdentity();
  let generation = 0;
  let decks: Entry<PresentationDeck[]> | undefined;
  const slides = new Map<string, Entry<PresentationSlide[]>>();
  const lessons = new Map<string, Entry<LessonCourseware>>();
  const lessonsById = new Map<string, Entry<LessonCourseware>>();

  function checkSession() {
    const next = sessionIdentity();
    if (next === identity) return;
    identity = next;
    generation += 1;
    decks = undefined;
    slides.clear();
    lessons.clear();
    lessonsById.clear();
  }

  function load<T>(entry: Entry<T>, request: () => Promise<T>): Promise<T> {
    if (entry.value !== undefined && entry.expiresAt > Date.now()) return Promise.resolve(entry.value);
    if (entry.pending) return entry.pending;
    const currentGeneration = generation;
    entry.pending = request().then((value) => {
      // Check identity again: logout can occur while the HTTP request is still running.
      checkSession();
      if (generation === currentGeneration) {
        entry.value = value;
        entry.expiresAt = Date.now() + TTL_MS;
      }
      return value;
    }).finally(() => { entry.pending = undefined; });
    return entry.pending;
  }

  return {
    getDecks(): Promise<PresentationDeck[]> {
      checkSession();
      decks ??= { expiresAt: 0 };
      return load(decks, () => api.listPresentationDecks());
    },
    getSlides(deckId: string): Promise<PresentationSlide[]> {
      checkSession();
      const entry = slides.get(deckId) ?? { expiresAt: 0 };
      slides.delete(deckId);
      slides.set(deckId, entry);
      while (slides.size > MAX_DECKS) slides.delete(slides.keys().next().value!);
      return load(entry, () => api.listDeckSlides(deckId));
    },
    /** The pages of one lesson number (06-06), which is how a citation knows which deck to open. */
    getLessonCourseware(coursewareKey: string): Promise<LessonCourseware> {
      checkSession();
      const entry = lessons.get(coursewareKey) ?? { expiresAt: 0 };
      lessons.delete(coursewareKey);
      lessons.set(coursewareKey, entry);
      while (lessons.size > MAX_DECKS) lessons.delete(lessons.keys().next().value!);
      return load(entry, () => api.getCoursewareSlides(coursewareKey));
    },
    /** The same pages addressed by the lesson id a classroom step carries, which is what a bookmark holds. */
    getLessonSlides(lessonId: string): Promise<LessonCourseware> {
      checkSession();
      const entry = lessonsById.get(lessonId) ?? { expiresAt: 0 };
      lessonsById.delete(lessonId);
      lessonsById.set(lessonId, entry);
      while (lessonsById.size > MAX_DECKS) lessonsById.delete(lessonsById.keys().next().value!);
      return load(entry, () => api.getLessonSlides(lessonId));
    },
  };
}
