import type { ClassroomLesson } from "../user/api";
import type { LessonCourseware, PresentationSlide } from "../shared/types/contracts";
import demoSlide01 from "../assets/structify-demo/slide-01.png";
import demoSlide02 from "../assets/structify-demo/slide-02.png";
import demoSlide03 from "../assets/structify-demo/slide-03.png";
import demoSlide04 from "../assets/structify-demo/slide-04.png";

/** Local-only fallback so the classroom stage remains inspectable without the private slide bundle. */
export const LOCAL_DEMO_LESSON_ID = "local-demo";

export const localDemoLesson: ClassroomLesson = {
  id: LOCAL_DEMO_LESSON_ID,
  chapterId: "local-demo",
  title: "Structify 本地演示课件",
  source: "local-demo/structify-classroom-demo.pptx",
  pages: "1-4",
};

const demoImages = [demoSlide01, demoSlide02, demoSlide03, demoSlide04];

const demoSlide = (number: number, title: string, section: string): PresentationSlide => ({
  id: `local-demo-slide-${String(number).padStart(2, "0")}`,
  deckId: "local-demo",
  deckTitle: "Structify 本地演示课件",
  slideNumber: number,
  chapter: "local-demo",
  title,
  rawText: title,
  speakerNotes: "仅供本地课堂页面布局验收使用。",
  semanticSummary: title,
  teachingRole: "concept",
  teachingFocus: "课堂课件播放区域布局",
  concepts: ["课堂布局", "课件播放"],
  visualAnchors: ["标题", "内容区", "页码"],
  shouldShow: true,
  lessonIds: [LOCAL_DEMO_LESSON_ID],
  imageUrl: demoImages[number - 1],
  section,
  role: "演示",
  terms: ["本地演示", "占位课件"],
});

export const localDemoCourseware: LessonCourseware = {
  lessonId: LOCAL_DEMO_LESSON_ID,
  coursewareKey: "local-demo",
  title: "Structify 本地演示课件",
  source: localDemoLesson.source,
  ready: true,
  builtAt: "local-development",
  slides: [
    demoSlide(1, "课堂课件播放区域", "01"),
    demoSlide(2, "课件舞台与选课控制合并", "02"),
    demoSlide(3, "为后续课堂内容预留空间", "03"),
    demoSlide(4, "本地占位演示结束", "04"),
  ],
};
