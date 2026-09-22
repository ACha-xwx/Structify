import type { Locale } from "./locale";

/**
 * The learner-facing copy, in both languages.
 *
 * The locale switch already existed (and already drove `document.documentElement.lang` and the document
 * title) but no surface ever read it, so switching to English changed nothing a learner could see. Every
 * string a learner can read on the sign-in screen, the entry page, the classroom, the courseware browser
 * and the animation lab lives here; the two languages sit next to each other so a new string cannot be
 * added in one language only.
 *
 * Server content (lesson names, teaching copy, model output) is not translated here - it arrives already
 * written, and the language of the material is the author's, not the chrome's.
 */
export type MessageEntry = { zh: string; en: string };

export const messages = {
  /** Chrome shared by every learner surface. */
  "common.brand": { zh: "返回 Structify", en: "Back to Structify" },
  "common.backHome": { zh: "回到首页", en: "Back to home" },
  "common.backToClassroom": { zh: "返回课堂", en: "Back to classroom" },
  "common.signOut": { zh: "退出登录", en: "Sign out" },
  "common.confirm": { zh: "确定", en: "Confirm" },
  "common.cancel": { zh: "取消", en: "Cancel" },
  "common.listSeparator": { zh: "、", en: ", " },
  "common.optional": { zh: "（可选）", en: "(optional)" },
  "common.failed": { zh: "操作失败", en: "That did not work" },
  "common.loading": { zh: "正在准备页面", en: "Preparing the page" },

  /** The entry page. */
  "home.title": { zh: "从哪开始？", en: "Where do you want to start?" },
  "home.choices": { zh: "学习入口", en: "Learning entry" },
  "home.account": { zh: "账户操作", en: "Account" },
  "home.classroom": { zh: "课堂", en: "Classroom" },
  "home.animation": { zh: "动画学习", en: "Animation lab" },
  "home.compiler": { zh: "C 编辑器", en: "C editor" },
  "home.resume": { zh: "继续上次课堂", en: "Resume last class" },

  /** The C editor restored from the original single-page app. */
  "compiler.title": { zh: "C 编辑器", en: "C editor" },
  "compiler.templates": { zh: "代码模板", en: "Code templates" },
  "compiler.template.hello": { zh: "基础输入", en: "Basic input" },
  "compiler.template.stack": { zh: "栈 push/pop", en: "Stack push/pop" },
  "compiler.template.list": { zh: "链表头插", en: "Linked list head insert" },
  "compiler.template.queue": { zh: "循环队列", en: "Circular queue" },
  "compiler.template.tree": { zh: "二叉树遍历", en: "Binary tree traversal" },
  "compiler.code": { zh: "C 代码", en: "C code" },
  "compiler.stdin": { zh: "程序输入（stdin）", en: "Program input (stdin)" },
  "compiler.run": { zh: "运行代码", en: "Run code" },
  "compiler.running": { zh: "运行中…", en: "Running…" },
  "compiler.emptyCode": { zh: "代码不能为空。", en: "Code cannot be empty." },
  "compiler.tooLong": { zh: "代码过长，请先缩小到 20000 字符以内再运行。", en: "Code is too long; keep it under 20000 characters." },
  "compiler.noOutput": { zh: "（程序没有输出）", en: "(no output)" },
  "compiler.statusSuccess": { zh: "运行完成", en: "Finished" },
  "compiler.statusCompile": { zh: "编译错误", en: "Compile error" },
  "compiler.statusRuntime": { zh: "运行时错误", en: "Runtime error" },
  "compiler.statusNetwork": { zh: "执行请求失败", en: "Execution request failed" },

  /** Sign in, register and password reset. */
  "auth.title.login": { zh: "开始使用", en: "Get started" },
  "auth.title.loginAdmin": { zh: "登录管理端", en: "Sign in to admin" },
  "auth.title.register": { zh: "创建账户", en: "Create account" },
  "auth.title.reset": { zh: "重置密码", en: "Reset password" },
  "auth.title.password": { zh: "输入密码", en: "Enter your password" },
  "auth.title.verify": { zh: "验证账户", en: "Verify your account" },
  "auth.title.newPassword": { zh: "设置新密码", en: "Set a new password" },
  "auth.subtitle.loginAdmin": { zh: "使用管理员账户继续", en: "Continue with an administrator account" },
  "auth.subtitle.login": { zh: "使用 Structify 账户继续", en: "Continue with your Structify account" },
  "auth.subtitle.register": { zh: "先输入你的邮箱地址", en: "Start with your email address" },
  "auth.subtitle.reset": { zh: "先验证你的邮箱地址", en: "Verify your email address first" },
  "auth.subtitle.password": { zh: "请输入账户密码", en: "Enter the account password" },
  "auth.subtitle.verify": { zh: "验证码和新密码将由服务器安全校验", en: "The server verifies the code and the new password" },
  "auth.identity.login": { zh: "邮箱或用户名", en: "Email or username" },
  "auth.identity.email": { zh: "邮箱", en: "Email" },
  "auth.identity.placeholderLogin": { zh: "邮箱或用户名", en: "Email or username" },
  "auth.identity.placeholderEmail": { zh: "邮箱地址", en: "Email address" },
  "auth.continueToPassword": { zh: "继续填写密码", en: "Continue to the password" },
  "auth.code.label": { zh: "邮箱验证码", en: "Email code" },
  "auth.code.placeholder": { zh: "输入验证码", en: "Enter the code" },
  "auth.code.send": { zh: "发送", en: "Send" },
  "auth.code.sending": { zh: "发送中", en: "Sending" },
  "auth.password.label": { zh: "密码", en: "Password" },
  "auth.password.placeholder": { zh: "密码", en: "Password" },
  "auth.password.placeholderNew": { zh: "至少 8 位密码", en: "At least 8 characters" },
  "auth.password.show": { zh: "显示密码", en: "Show password" },
  "auth.password.hide": { zh: "隐藏密码", en: "Hide password" },
  "auth.submit.login": { zh: "登录", en: "Sign in" },
  "auth.submit.register": { zh: "创建账号", en: "Create account" },
  "auth.submit.reset": { zh: "更新密码", en: "Update password" },
  "auth.back": { zh: "返回", en: "Back" },
  "auth.links": { zh: "账户操作", en: "Account actions" },
  "auth.link.signIn": { zh: "返回登录", en: "Back to sign in" },
  "auth.link.register": { zh: "创建账号", en: "Create account" },
  "auth.link.forgot": { zh: "忘记密码", en: "Forgot password" },
  "auth.error.identityRequired": { zh: "请输入邮箱或用户名。", en: "Enter your email or username." },
  "auth.error.emailInvalid": { zh: "请输入有效的邮箱地址。", en: "Enter a valid email address." },
  "auth.error.passwordRequired": { zh: "请输入密码。", en: "Enter your password." },
  "auth.error.credentialsRequired": { zh: "请输入验证码和密码。", en: "Enter the code and the new password." },
  "auth.error.codeInvalid": { zh: "验证码无效或已过期，请重新发送。", en: "That code is invalid or expired. Send a new one." },
  "auth.error.unauthorized": { zh: "身份凭证无效或已过期，请重新登录或确认验证码。", en: "Those credentials are invalid or expired. Sign in again or check the code." },
  "auth.error.forbidden": { zh: "当前会话没有执行此操作的权限。", en: "This session is not allowed to do that." },
  "auth.error.notFound": { zh: "认证服务接口不存在，请稍后重试。", en: "The sign-in endpoint is missing. Try again later." },
  "auth.error.rateLimited": { zh: "请求过于频繁，请在 {seconds} 秒后重试。", en: "Too many attempts. Try again in {seconds} seconds." },
  "auth.error.unavailable": { zh: "认证服务暂时不可用，请稍后重试。", en: "Sign-in is temporarily unavailable. Try again later." },
  "auth.error.offline": { zh: "网络不可用，请检查连接后重试。", en: "No network connection. Check it and try again." },
  "auth.error.timeout": { zh: "请求超时，请重试。", en: "The request timed out. Try again." },
  "auth.error.server": { zh: "服务器暂时无法处理请求，请重试。", en: "The server could not handle that. Try again." },
  "auth.error.generic": { zh: "请求未完成，请稍后重试。", en: "The request did not go through. Try again later." },

  /** The classroom. */
  "classroom.eyebrow": { zh: "数据结构课堂", en: "Data structures class" },
  "classroom.pickLesson": { zh: "选择课时", en: "Pick a lesson" },
  "classroom.start": { zh: "开始", en: "Start" },
  "classroom.preparing": { zh: "备课中", en: "Preparing the lesson" },
  "classroom.lab": { zh: "动画实验室", en: "Animation lab" },
  "classroom.ready": { zh: "课堂准备好了", en: "The classroom is ready" },
  "classroom.inSession": { zh: "课堂进行中", en: "Class in progress" },
  "classroom.currentLesson": { zh: "当前课时", en: "Current lesson" },
  "classroom.opening": { zh: "正在开课", en: "Opening the lesson" },
  "classroom.finishedAll": { zh: "已完成全部 {total} 步", en: "All {total} steps done" },
  "classroom.step": { zh: "第 {index} / {total} 步", en: "Step {index} of {total}" },
  "classroom.exit": { zh: "退出课堂", en: "Leave class" },
  "classroom.ask": { zh: "提问", en: "Ask" },
  "classroom.answer": { zh: "回答", en: "Answer" },
  "classroom.answerAgain": { zh: "再答一次", en: "Try again" },
  "classroom.answerWrong": { zh: "回答不正确，再想想。", en: "Not quite. Think it through and try again." },
  "classroom.verdict": { zh: "判答结果", en: "Answer verdict" },
  "classroom.input": { zh: "课堂输入", en: "Class input" },
  "classroom.placeholderAsk": { zh: "提问", en: "Ask" },
  "classroom.placeholderAnswer": { zh: "回答", en: "Answer" },
  "classroom.next": { zh: "下一步", en: "Next step" },
  "classroom.demo": { zh: "演示", en: "Show it" },
  "classroom.backToLesson": { zh: "返回课堂", en: "Back to class" },
  "classroom.toLab": { zh: "去动画实验室", en: "Open the animation lab" },
  "classroom.continue": { zh: "回到课堂", en: "Back to class" },
  "classroom.changeLesson": { zh: "换课", en: "Change lesson" },
  "classroom.prepareFailed": { zh: "备课失败", en: "The lesson could not be prepared" },

  /** The courseware pane beside the lesson. */
  "slides.title": { zh: "课件", en: "Courseware" },
  "slides.pane": { zh: "课件", en: "Courseware" },
  "slides.position": { zh: "第 {page} 页", en: "Page {page}" },
  "slides.previous": { zh: "上一页", en: "Previous" },
  "slides.next": { zh: "下一页", en: "Next" },
  "slides.loading": { zh: "正在载入课件", en: "Loading courseware" },
  "slides.empty": { zh: "本课时没有配套课件", en: "No courseware for this lesson" },
  "slides.imageFailed": { zh: "这一页图片加载失败，请翻到下一页或稍后重试。", en: "This page failed to load. Turn to the next page or try again later." },
  "slides.pickPage": { zh: "选择本段该用的页面", en: "Choose the page for this part" },
  "slides.pickAction": { zh: "选择本段使用的课件页", en: "Choose the courseware page for this part" },
  "slides.useThis": { zh: "本段改用这一页", en: "Use this page for this part" },
  "slides.useOther": { zh: "改用其他页", en: "Use another page" },
  "slides.browseAll": { zh: "浏览全部课件", en: "Browse all courseware" },
  "slides.pinnedByTeacher": { zh: "老师指定", en: "Chosen by the teacher" },
  "slides.followSource": { zh: "教材延伸", en: "Follows the textbook" },
  "slides.followPrevious": { zh: "沿用上一页", en: "Keeps the previous page" },

  /** The animation lab. */
  "lab.title": { zh: "动画实验室", en: "Animation lab" },
  "lab.chapter": { zh: "章节", en: "Chapter" },
  "lab.structure": { zh: "结构", en: "Structure" },
  "lab.operation": { zh: "操作", en: "Operation" },
  "lab.generate": { zh: "生成动画", en: "Generate" },
  "lab.calculating": { zh: "计算中", en: "Computing" },
  "lab.describe": { zh: "用一句话描述", en: "Describe it in a sentence" },
  "lab.describePlaceholder": { zh: "例如：看看折半查找每次怎么缩小范围", en: "For example: how binary search narrows its range" },
  "lab.askModel": { zh: "让模型选一个演示", en: "Let the model pick one" },
  "lab.playerTitle": { zh: "动画播放", en: "Animation" },
  "lab.pickOne": { zh: "选择一个演示", en: "Choose a demo" },
  "lab.placeholder": { zh: "在左边选一个结构和一个操作，或者用一句话描述想看的过程。", en: "Pick a structure and an operation on the left, or describe the process you want to see." },
  "lab.demoFallback": { zh: "参数不足，这里用的是引擎内置的标准教学示例，不是教材原例。", en: "Not enough arguments, so this is the engine's built-in teaching example rather than the textbook one." },
  "lab.missingArguments": { zh: "还缺参数：{names}", en: "Still missing: {names}" },
  "lab.cannotGenerate": { zh: "这个演示暂时无法生成", en: "This demo cannot be generated right now" },
  "lab.observationLabel": { zh: "写下你观察到的规律", en: "Write down the pattern you noticed" },
  "lab.observationPlaceholder": { zh: "例如：每一趟都会把枢轴放到最终位置", en: "For example: every pass puts the pivot in its final place" },
  "lab.saveObservation": { zh: "保存观察", en: "Save the note" },
  "lab.observationSaved": { zh: "已保存。", en: "Saved." },
  "lab.hintNumbers": { zh: "如 1,2,3", en: "e.g. 1,2,3" },
  "lab.hintNested": { zh: "如 [[1,5,9],[2,6,8]]", en: "e.g. [[1,5,9],[2,6,8]]" },
  "lab.hintValue": { zh: "如 {value}", en: "e.g. {value}" },
  "lab.hintJson": { zh: "JSON", en: "JSON" },

  /** The shared animation transport. */
  "player.label": { zh: "动画播放器", en: "Animation player" },
  "player.labelOf": { zh: "动画播放器：{title}", en: "Animation player: {title}" },
  "player.initial": { zh: "初始状态", en: "Initial state" },
  "player.canvas": { zh: "动画画布（左右方向键切换步骤，空格播放）", en: "Animation canvas (arrow keys step, space plays)" },
  "player.empty": { zh: "这一步没有可绘制的内容", en: "Nothing to draw for this step" },
  "player.previous": { zh: "上一步", en: "Previous" },
  "player.play": { zh: "播放", en: "Play" },
  "player.pause": { zh: "暂停", en: "Pause" },
  "player.next": { zh: "下一步", en: "Next" },
  "player.reset": { zh: "回到起点", en: "Back to start" },
  "player.speed": { zh: "速度", en: "Speed" },
  "player.speedLabel": { zh: "播放速度", en: "Playback speed" },
  "player.scrubber": { zh: "播放进度", en: "Playback position" },
  "player.placeholder": { zh: "还没有生成动画", en: "No animation yet" },

  /** The animation renderer. */
  "stage.emptyBucket": { zh: "空", en: "empty" },
  "stage.panelLabel": { zh: "{label}结构图", en: "{label} diagram" },

  /** The courseware browser. */
  "courseware.list": { zh: "课件列表", en: "Courseware list" },
  "courseware.deckMeta": { zh: "第 {chapter} 章 · {count} 页", en: "Chapter {chapter} · {count} slides" },
  "courseware.empty": { zh: "暂时没有可浏览的课件", en: "No courseware to browse yet" },
  "courseware.loading": { zh: "正在载入课件", en: "Loading courseware" },
  "courseware.imageFailed": { zh: "这一页图片加载失败，请翻页或换一份课件重试。", en: "This page failed to load. Turn the page or pick another deck." },
  "courseware.previous": { zh: "上一页", en: "Previous" },
  "courseware.next": { zh: "下一页", en: "Next" },
  "courseware.pickDeck": { zh: "请选择左侧的课件", en: "Pick a deck on the left" },

  /** The two error screens. */
  "common.backToHome": { zh: "返回首页", en: "Back to home" },
  "forbidden.title": { zh: "当前账号没有访问权限", en: "This account cannot open that page" },
  "forbidden.body": { zh: "请使用具备相应角色或模块能力的账号继续。", en: "Continue with an account that carries the right role or module access." },
  "notFound.title": { zh: "页面不存在", en: "Page not found" },
  "notFound.body": { zh: "地址可能已经改变，或该模块尚未开放。", en: "The address may have changed, or this module is not open yet." },
} as const satisfies Record<string, MessageEntry>;

export type MessageKey = keyof typeof messages;

/** Fill `{name}` placeholders; a missing value keeps the placeholder visible instead of printing "undefined". */
export function translate(key: MessageKey, locale: Locale, params?: Record<string, string | number>): string {
  const entry: MessageEntry = messages[key];
  const template = locale === "en-US" ? entry.en : entry.zh;
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) => {
    const value = params[name];
    return value === undefined ? match : String(value);
  });
}
