import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import AuthView from "./AuthView.vue";

const authMock = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  resetPassword: vi.fn(),
  requestCode: vi.fn(),
}));

vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: { template: "<div>home</div>" } },
      { path: "/begin", component: { template: "<div>learning entry</div>" } },
      { path: "/teacher", component: { template: "<div>teacher console</div>" } },
      { path: "/login", component: AuthView, props: { mode: "login" } },
      { path: "/register", component: AuthView, props: { mode: "register" } },
      { path: "/reset-password", component: AuthView, props: { mode: "reset" } },
    ],
  });
}

async function mountAuth(mode: "login" | "register" | "reset") {
  const router = createTestRouter();
  await router.push(mode === "login" ? "/login" : mode === "register" ? "/register" : "/reset-password");
  await router.isReady();
  return mount(AuthView, { props: { mode }, global: { plugins: [router] } });
}

describe("verification-code auth flow", () => {
  afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it("validates email, sends the real request once, and starts a resend countdown", async () => {
    vi.useFakeTimers();
    let resolveDelivery!: (value: { message: string }) => void;
    authMock.requestCode.mockImplementationOnce(() => new Promise((resolve) => { resolveDelivery = resolve; }));
    const wrapper = await mountAuth("register");

    const send = () => wrapper.get('[data-testid="send-verification-code"]');
    expect(send().attributes("disabled")).toBeDefined();

    await wrapper.get('input[type="email"]').setValue("student@example.com");
    await wrapper.get('[aria-label="继续填写密码"]').trigger("click");
    expect(send().attributes("disabled")).toBeUndefined();
    await send().trigger("click");
    await send().trigger("click");

    expect(authMock.requestCode).toHaveBeenCalledTimes(1);
    expect(authMock.requestCode).toHaveBeenCalledWith({ email: "student@example.com", purpose: "register" });
    expect(send().attributes("disabled")).toBeDefined();

    resolveDelivery({ message: "Verification code has been sent" });
    await flushPromises();

    expect(wrapper.get('[role="status"]').text()).toBe("已发送");
    expect(wrapper.find('[data-testid="verification-sent"] .form-feedback__icon').exists()).toBe(true);
    expect(wrapper.get('[data-testid="verification-countdown"]').text()).toContain("60");
    await vi.advanceTimersByTimeAsync(1_000);
    expect(wrapper.get('[data-testid="verification-countdown"]').text()).toContain("59");
  });

  it("keeps the form in place and gives a retry interval for a rate-limited code request", async () => {
    const rateLimitError = Object.assign(new Error("Too many requests"), {
      status: 429,
      code: "RATE_LIMITED",
      headers: new Headers({ "Retry-After": "30" }),
    });
    authMock.requestCode.mockRejectedValueOnce(rateLimitError);
    const wrapper = await mountAuth("reset");

    await wrapper.get('input[type="email"]').setValue("student@example.com");
    await wrapper.get('[aria-label="继续填写密码"]').trigger("click");
    await wrapper.get('[data-testid="send-verification-code"]').trigger("click");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("30");
    expect(authMock.requestCode).toHaveBeenCalledWith({ email: "student@example.com", purpose: "reset" });
  });

  it("does not claim success when the server reports an invalid or expired verification code", async () => {
    authMock.register.mockRejectedValueOnce(Object.assign(new Error("invalid code"), { status: 401, code: "AUTH_CODE_INVALID" }));
    const wrapper = await mountAuth("register");

    await wrapper.get('input[type="email"]').setValue("student@example.com");
    await wrapper.get('input[autocomplete="one-time-code"]').setValue("123456");
    await wrapper.get('input[type="password"]').setValue("correct-horse-battery-staple");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("验证码无效或已过期");
    expect(authMock.register).toHaveBeenCalledWith({
      email: "student@example.com",
      code: "123456",
      password: "correct-horse-battery-staple",
    });
  });

  it("uses the same sent confirmation and hides the verification subtitle on reset-password", async () => {
    authMock.requestCode.mockResolvedValueOnce({ message: "server-specific response" });
    const wrapper = await mountAuth("reset");

    await wrapper.get('input[type="email"]').setValue("student@example.com");
    await wrapper.get('[aria-label="继续填写密码"]').trigger("click");
    await wrapper.get('[data-testid="send-verification-code"]').trigger("click");
    await flushPromises();
    await new Promise((resolve) => setTimeout(resolve, 260));

    expect(authMock.requestCode).toHaveBeenCalledWith({ email: "student@example.com", purpose: "reset" });
    expect(wrapper.get('[data-testid="verification-sent"]').text()).toBe("已发送");
    expect(wrapper.find('[data-testid="verification-sent"] .form-feedback__icon').exists()).toBe(true);
    expect(wrapper.find(".auth-flow__heading p").exists()).toBe(false);
  });

  it("submits a username through the username login field without breaking email login compatibility", async () => {
    authMock.login.mockResolvedValueOnce({ id: 1, email: "admin@example.com", roles: ["ADMIN"] });
    const wrapper = await mountAuth("login");

    const identifier = wrapper.get('input[autocomplete="username"]');
    expect(identifier.attributes("type")).toBe("text");
    expect(wrapper.get("label").text()).toContain("邮箱或用户名");

    await identifier.setValue("ACha_");
    await wrapper.get('input[type="password"]').setValue("correct-horse-battery-staple");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(authMock.login).toHaveBeenCalledWith({ username: "ACha_", password: "correct-horse-battery-staple" });
    expect(wrapper.vm.$router.currentRoute.value.path).toBe("/begin");
  });

  it("uses the management entry shell when login redirects into the admin console", async () => {
    const router = createTestRouter();
    await router.push("/login?redirect=/admin/mail");
    await router.isReady();
    const wrapper = mount(AuthView, { props: { mode: "login" }, global: { plugins: [router] } });

    expect(wrapper.classes()).toContain("auth-screen--admin");
  });

  it("keeps the public login outside the management entry shell", async () => {
    const wrapper = await mountAuth("login");

    expect(wrapper.classes()).not.toContain("auth-screen--admin");
  });

  it("uses an open and closed eye to toggle password visibility", async () => {
    const wrapper = await mountAuth("login");
    await wrapper.get('input[autocomplete="username"]').setValue("demo-user");
    await wrapper.get('[aria-label="继续填写密码"]').trigger("click");

    const toggle = wrapper.get('[data-testid="password-visibility"]');
    const passwordField = wrapper.get('[aria-label="密码"]');

    expect(toggle.attributes("aria-pressed")).toBe("false");
    expect(toggle.attributes("aria-label")).toBe("显示密码");
    expect(toggle.find(".auth-field__toggle-icon--closed").exists()).toBe(true);
    expect(passwordField.attributes("type")).toBe("password");

    await toggle.trigger("click");

    expect(toggle.attributes("aria-pressed")).toBe("true");
    expect(toggle.attributes("aria-label")).toBe("隐藏密码");
    expect(toggle.find(".auth-field__toggle-icon--open").exists()).toBe(true);
    expect(passwordField.attributes("type")).toBe("text");
  });

  it("offers the three sign-in doors and marks the teacher one when it was asked for", async () => {
    const router = createTestRouter();
    await router.push("/login?redirect=/teacher");
    await router.isReady();
    const wrapper = mount(AuthView, { props: { mode: "login" }, global: { plugins: [router] } });

    const options = wrapper.findAll(".auth-entry__option");
    expect(options.map((option) => option.text())).toEqual(["学生", "老师", "管理员"]);
    expect(options.map((option) => option.attributes("href"))).toEqual(["/login", "/login?redirect=/teacher", "/login?redirect=/admin"]);
    expect(options[1].attributes("aria-current")).toBe("page");
    expect(wrapper.classes()).toContain("auth-screen--admin");
    expect(wrapper.get("#auth-title").text()).toBe("登录教师端");
    wrapper.unmount();
  });

  it("sends a teacher to their own console even when they sign in from the ordinary door", async () => {
    authMock.login.mockResolvedValueOnce({ id: 66, email: "teacher@example.com", roles: ["TEACHER"] });
    const wrapper = await mountAuth("login");

    await wrapper.get('input[autocomplete="username"]').setValue("teacher@example.com");
    await wrapper.get('input[type="password"]').setValue("correct-horse-battery-staple");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.vm.$router.currentRoute.value.path).toBe("/teacher");
    wrapper.unmount();
  });

  it("does not walk a learner into a console that would refuse them", async () => {
    authMock.login.mockResolvedValueOnce({ id: 7, email: "learner@example.com", roles: ["STUDENT"] });
    const router = createTestRouter();
    await router.push("/login?redirect=/teacher");
    await router.isReady();
    const wrapper = mount(AuthView, { props: { mode: "login" }, global: { plugins: [router] } });

    await wrapper.get('input[autocomplete="username"]').setValue("learner@example.com");
    await wrapper.get('input[type="password"]').setValue("correct-horse-battery-staple");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.vm.$router.currentRoute.value.path).toBe("/begin");
    wrapper.unmount();
  });
});
