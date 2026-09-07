import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it } from "vitest";
import UserFrame from "./UserFrame.vue";

describe("UserFrame", () => {
  it("提供跳到主要学习内容的键盘入口", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        "/user/home",
        "/user/chapters",
        "/user/chapters/:chapterId",
        "/user/knowledge",
        "/user/coach",
        "/user/classroom",
        "/user/animation",
        "/user/presentation",
        "/user/code",
        "/user/progress",
        "/user/profile",
        "/login",
      ].map((path) => ({ path, component: { template: "<div />" } })),
    });
    await router.push("/user/home");
    await router.isReady();
    const wrapper = mount(UserFrame, {
      slots: { default: "<h1>当前内容</h1>" },
      global: { plugins: [router] },
    });

    expect(wrapper.get(".user-skip-link").attributes("href")).toBe("#user-learning-content");
    expect(wrapper.get("#user-learning-content").attributes("data-shell")).toBe("runtime");
    expect(wrapper.get("#user-learning-content").attributes("data-has-rail")).toBe("false");
    expect(wrapper.find(".user-frame-layout").exists()).toBe(false);
    expect(wrapper.find(".user-frame-sidebar").exists()).toBe(false);
    expect(wrapper.get(".user-frame-runtime-main").attributes("aria-label")).toBe("学习内容");
    expect(wrapper.find(".ai-runtime-frame--workbench").exists()).toBe(true);
    expect(wrapper.find(".ai-runtime-frame__mark-shape").exists()).toBe(false);
    expect(wrapper.get("img.ai-runtime-frame__mark-image").attributes("src")).toContain("logo.webp");
    expect(wrapper.get(".ai-runtime-frame__sign-in").attributes("href")).toBe("/login?redirect=/user/home");
    expect(wrapper.findAll(".ai-runtime-frame__nav a").find((link) => link.text() === "代码实验")?.attributes("href")).toBe("/user/code");
  });

  it("以课程目录为主导航，并把五个跨模块入口收进折叠区", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        "/user/home",
        "/user/chapters",
        "/user/chapters/:chapterId",
        "/user/knowledge",
        "/user/coach",
        "/user/classroom",
        "/user/animation",
        "/user/presentation",
        "/user/code",
        "/user/progress",
        "/user/profile",
        "/login",
      ].map((path) => ({ path, component: { template: "<div />" } })),
    });
    await router.push("/user/chapters?chapterId=sequential-list&lessonId=02-02B&from=chapter");
    await router.isReady();
    const wrapper = mount(UserFrame, {
      props: { shell: "course" },
      slots: { rail: "<div class='rail-body'>上下文 rail</div>" },
      global: { plugins: [router] },
    });

    const globalLinks = wrapper.findAll(".ai-runtime-frame__nav a");
    expect(globalLinks.map((link) => link.text())).toEqual(["学习台", "课程", "算法舞台", "资料库"]);
    const courseLinks = wrapper.findAll(".user-frame-course-nav .user-frame-course-item");
    expect(wrapper.findAll(".user-frame-course-group")).toHaveLength(6);
    expect(courseLinks.map((link) => link.text())).toEqual(expect.arrayContaining(["01绪论第 01 章", "02线性表第 02 章", "01线性表基础基础单元", "02顺序表的插入当前学习", "03顺序表的删除下一节", "04单链表后续单元"]));
    expect(courseLinks.length).toBeGreaterThan(20);
    expect(wrapper.get('.user-frame-course-nav [data-course-id="sequential-list"]').attributes("href")).toBe("/user/chapters?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
    expect(wrapper.get('.user-frame-course-nav [data-course-id="sequential-list"]').attributes("aria-current")).toBe("page");
    expect(courseLinks.map((link) => link.attributes("href"))).toContain("/user/chapters?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
    expect(courseLinks.find((link) => link.text().includes("顺序表的插入"))?.attributes("aria-current")).toBe("page");
    const navigationLinks = wrapper.findAll(".user-frame-sidebar__other nav a");
    expect(navigationLinks.map((link) => link.text())).toEqual(expect.arrayContaining(["学习路径顺序推进", "AI 伴学追问概念", "课堂脚本学习", "实践算法与代码", "资料来源与证据"]));
    expect(navigationLinks.map((link) => link.attributes("href"))).toContain("/user/chapters?chapterId=sequential-list&from=chapter");
    expect(navigationLinks.map((link) => link.attributes("href"))).toContain("/user/animation?chapterId=sequential-list&from=chapter");
    expect(navigationLinks.find((link) => link.text().includes("学习路径"))?.attributes("aria-current")).toBe("page");
    expect(wrapper.get(".user-frame-sidebar__other").attributes("open")).toBeUndefined();
    expect(wrapper.get(".user-frame-layout").classes()).not.toContain("is-two-column");
    expect(wrapper.get(".user-frame-layout").attributes("data-shell")).toBe("course");
    expect(wrapper.get(".user-frame-layout").attributes("data-has-rail")).toBe("true");
    expect(wrapper.find(".user-frame-runtime-context").exists()).toBe(false);
    expect(wrapper.find(".user-frame-sidebar__footer").exists()).toBe(false);
    expect(wrapper.get(".user-frame-rail .rail-body").text()).toBe("上下文 rail");
    expect(wrapper.findAll(".user-frame-secondary-nav .workbench-nav-row__module-icon svg use")).toHaveLength(5);
    expect(wrapper.findAll(".user-frame-secondary-nav .user-frame-nav__icon")).toHaveLength(0);
    expect(wrapper.findAll(".user-frame-sidebar__direct-tools > a")).toHaveLength(2);
    expect(wrapper.get(".user-frame-sidebar__direct-tools > a").attributes("href")).toBe("/user/presentation?lessonId=02-02B&chapterId=sequential-list&from=chapter");
    expect(wrapper.findAll(".user-frame-sidebar__direct-tools > a")[1]?.attributes("href")).toBe("/user/code?chapterId=sequential-list&from=chapter");
    expect(wrapper.find(".user-frame-runtime-tools").exists()).toBe(false);
    expect(wrapper.find(".user-frame-mobile-other").text()).toContain("学习复盘");
    expect(wrapper.findAll('.theme-toggle__control')).toHaveLength(2);
    expect(wrapper.findAll('.theme-toggle__locale-toggle')).toHaveLength(2);
    expect(wrapper.find('.ai-runtime-frame__mobile-actions [role="switch"]').exists()).toBe(true);
    expect(wrapper.findAll(".user-frame-mobile-course-link").length).toBeGreaterThan(20);
    expect(wrapper.get('.user-frame-mobile-course-link[data-course-id="sequential-list"]').attributes("href")).toBe("/user/chapters?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
    expect(wrapper.get(".user-frame-mobile-other").attributes("open")).toBeUndefined();
  });

  it("为首页提供同一 Runtime chrome 而不重复渲染工作台侧栏", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: ["/user/home", "/user/chapters", "/user/chapters/:chapterId", "/user/knowledge", "/user/coach", "/user/classroom", "/user/animation", "/user/presentation", "/user/code", "/user/progress", "/user/profile", "/login"].map((path) => ({ path, component: { template: "<div />" } })),
    });
    await router.push("/user/home");
    await router.isReady();
    const wrapper = mount(UserFrame, {
      props: { shell: "runtime" },
      slots: { default: "<section data-testid='runtime-content'>算法舞台</section>" },
      global: { plugins: [router] },
    });

    expect(wrapper.find(".user-frame-sidebar").exists()).toBe(false);
    expect(wrapper.find(".user-frame-rail").exists()).toBe(false);
    expect(wrapper.find(".user-frame-layout").exists()).toBe(false);
    expect(wrapper.get("[data-testid='runtime-content']").text()).toBe("算法舞台");
    expect(wrapper.get("#user-learning-content").attributes("data-shell")).toBe("runtime");
    expect(wrapper.findAll(".ai-runtime-frame__nav a")).toHaveLength(4);
    expect(wrapper.findAll(".user-frame-mobile-course-link")).toHaveLength(0);
  });

  it("Runtime 壳保留二级页面的上下文 rail，而不重新挂载课程侧栏", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: ["/user/home", "/user/chapters", "/user/knowledge", "/user/coach", "/user/classroom", "/user/animation", "/user/code", "/user/progress", "/login"].map((path) => ({ path, component: { template: "<div />" } })),
    });
    await router.push("/user/knowledge");
    await router.isReady();
    const wrapper = mount(UserFrame, {
      slots: { default: "<section>资料内容</section>", rail: "<section class='runtime-rail-body'>资料上下文</section>" },
      global: { plugins: [router] },
    });

    expect(wrapper.get("#user-learning-content").attributes("data-has-rail")).toBe("true");
    expect(wrapper.get(".user-frame-runtime-main").text()).toContain("资料内容");
    expect(wrapper.get(".user-frame-runtime-rail").text()).toContain("资料上下文");
    expect(wrapper.find(".user-frame-sidebar").exists()).toBe(false);
  });
});
