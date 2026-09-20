import type { Locale } from "../shared/i18n/locale";

type RouteLike = {
  name?: unknown;
  path?: string;
  meta?: unknown;
};

const titleByName: Record<string, { zh: string; en: string }> = {
  home: { zh: "首页", en: "Home" },
  classroom: { zh: "课堂", en: "Classroom" },
  courseware: { zh: "课件", en: "Courseware" },
  "animation-lab": { zh: "动画实验室", en: "Animation lab" },
  login: { zh: "登录", en: "Sign in" },
  register: { zh: "注册", en: "Register" },
  "reset-password": { zh: "重置密码", en: "Reset password" },
  "user-home": { zh: "学习台", en: "Workbench" },
  "user-chapters": { zh: "章节", en: "Chapters" },
  "user-chapter-detail": { zh: "章节", en: "Chapters" },
  "user-resource": { zh: "资料详情", en: "Resource" },
  "user-knowledge": { zh: "知识检索", en: "Knowledge" },
  "user-coach": { zh: "AI 伴学", en: "AI Coach" },
  "user-classroom": { zh: "课堂", en: "Classroom" },
  "user-animation": { zh: "算法舞台", en: "Algorithm Stage" },
  "user-presentation": { zh: "课件", en: "Courseware" },
  "user-code": { zh: "C 编译器", en: "C Compiler" },
  "user-progress": { zh: "学习复盘", en: "Review" },
  "user-review": { zh: "学习复盘", en: "Review" },
  "user-profile": { zh: "个人资料", en: "Profile" },
  "admin-home": { zh: "管理总览", en: "Admin overview" },
  "admin-users": { zh: "用户与角色", en: "Users and roles" },
  "admin-reviews": { zh: "审核队列", en: "Review queue" },
  "admin-tasks": { zh: "后台任务", en: "Background tasks" },
  "admin-audit": { zh: "审计事件", en: "Audit events" },
  "admin-settings": { zh: "模型设置", en: "Model settings" },
  "admin-mail": { zh: "邮件投递", en: "Mail delivery" },
  "admin-sandbox": { zh: "代码沙箱", en: "Code sandbox" },
  forbidden: { zh: "无权访问", en: "Access denied" },
  "not-found": { zh: "页面不存在", en: "Not found" },
};

const titleByPath: Array<[RegExp, { zh: string; en: string }]> = [
  [/^\/admin(?:\/|$)/, { zh: "管理后台", en: "Admin" }],
  [/^\/user(?:\/|$)/, { zh: "学习台", en: "Workbench" }],
  [/^\/login$/, { zh: "登录", en: "Sign in" }],
  [/^\/register$/, { zh: "注册", en: "Register" }],
  [/^\/reset-password$/, { zh: "重置密码", en: "Reset password" }],
  [/^\/403$/, { zh: "无权访问", en: "Access denied" }],
  [/^\/404$/, { zh: "页面不存在", en: "Not found" }],
];

function routeLabel(route: RouteLike) {
  const name = typeof route.name === "string" ? route.name : "";
  if (name && titleByName[name]) return titleByName[name];

  const path = typeof route.path === "string" ? route.path : "";
  return titleByPath.find(([pattern]) => pattern.test(path))?.[1] ?? { zh: "首页", en: "Home" };
}

export function documentTitleForRoute(route: RouteLike, locale: Locale): string {
  const label = routeLabel(route);
  return `${locale === "en-US" ? label.en : label.zh} | Structify`;
}
