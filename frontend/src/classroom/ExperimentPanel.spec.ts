import { beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import ExperimentPanel from "./ExperimentPanel.vue";
import type { ClassroomCodeSamplesResponse } from "../shared/types/contracts";

const runCode = vi.fn();
const listCodeSamples = vi.fn();
const startCodeSession = vi.fn();
const streamCodeSession = vi.fn();
const typeInCodeSession = vi.fn();
const stopCodeSession = vi.fn();

vi.mock("../user/runtime", () => ({
  userApi: {
    listCodeSamples: (...args: unknown[]) => listCodeSamples(...args),
    runCode: (...args: unknown[]) => runCode(...args),
    startCodeSession: (...args: unknown[]) => startCodeSession(...args),
    streamCodeSession: (...args: unknown[]) => streamCodeSession(...args),
    typeInCodeSession: (...args: unknown[]) => typeInCodeSession(...args),
    stopCodeSession: (...args: unknown[]) => stopCodeSession(...args),
  },
}));

/** Drafts persist per sample, so each case starts from a clean store. */
beforeEach(() => {
  window.localStorage.clear();
  listCodeSamples.mockReset();
  runCode.mockReset();
  startCodeSession.mockReset();
  streamCodeSession.mockReset();
  typeInCodeSession.mockReset();
  stopCodeSession.mockReset();
  // The one-shot runner is the fallback: without a live sandbox, these tests exercise it.
  startCodeSession.mockRejectedValue(Object.assign(new Error("交互式运行环境未启用"), { code: "SANDBOX_DISABLED" }));
});

function sample(id: string, title: string, sections: string[], targets: string[]) {
  return {
    id,
    title,
    sourceFile: "ch03/code/seqstack.h",
    sections,
    targets,
    summary: `${title} 的说明`,
    stdin: "abcde\n",
    expectedStdout: "依次出栈: edcba\n",
    code: `#include <stdio.h>\n/* ${title} 教材原文 */\nint main(void) { return 0; }`,
  };
}

function response(): ClassroomCodeSamplesResponse {
  return {
    sampleCount: 4,
    lessons: [
      {
        coursewareKey: "03-01",
        lessonTitle: "栈与队列-栈的定义和表示实现",
        chapterId: "03-stack-queue",
        samples: [
          sample("03-01-s1", "顺序栈的四个基本运算", ["3.1.2"], ["3.1", "3.2"]),
          sample("03-01-s2", "顺序栈的判满边界", ["3.1.2"], ["3.2"]),
          sample("03-01-s3", "两栈共享一片空间", ["3.1.2"], ["3.5"]),
        ],
      },
      {
        coursewareKey: "03-02",
        lessonTitle: "栈与队列-栈的应用举例",
        chapterId: "03-stack-queue",
        samples: [sample("03-02-s1", "括号匹配", ["3.1.3"], ["3.12"])],
      },
    ],
  };
}

async function flush(wrapper: { vm: { $nextTick: () => Promise<unknown> } }) {
  for (let round = 0; round < 3; round++) {
    await Promise.resolve();
    await wrapper.vm.$nextTick();
  }
}

async function mountPanel(pageSection = "3.1.2") {
  listCodeSamples.mockResolvedValue(response());
  const wrapper = mount(ExperimentPanel, { props: { coursewareKey: "03-01", pageSection } });
  await flush(wrapper);
  return wrapper;
}

async function openPane(wrapper: Awaited<ReturnType<typeof mountPanel>>) {
  await wrapper.get(".experiment__toggle").trigger("click");
  await flush(wrapper);
}

describe("classroom hands-on pane", () => {
  it("fetches nothing until the teacher opens it", async () => {
    const wrapper = await mountPanel();

    expect(wrapper.find("#experiment-code").exists()).toBe(false);
    expect(listCodeSamples).not.toHaveBeenCalled();

    await openPane(wrapper);

    const options = wrapper.findAll("#experiment-select option");
    expect(options.length).toBe(4);
    expect(options[0].text()).toBe("顺序栈的四个基本运算");
    // Samples from other lessons are still reachable, under the catch-all group.
    const groups = wrapper.findAll("#experiment-select optgroup");
    expect(groups.map((group) => group.attributes("label"))).toEqual(["本课时", "全部课堂代码"]);
    expect(options[3].text()).toContain("括号匹配");
    expect(listCodeSamples).toHaveBeenCalledWith("03-01");
  });

  it("selects the sample that matches the page on screen and says so", async () => {
    const wrapper = await mountPanel("3.1.2");
    await openPane(wrapper);

    expect((wrapper.get("#experiment-select").element as HTMLSelectElement).value).toBe("03-01-s1");
    expect(wrapper.text()).toContain("这段代码正对本页内容");
    expect((wrapper.get("#experiment-code").element as HTMLTextAreaElement).value).toContain("顺序栈的四个基本运算");
  });

  it("follows the page while the code is untouched and runs what the learner sees", async () => {
    runCode.mockResolvedValue({ language: "c", status: "success", stdout: "依次出栈: edcba\n", stderr: "", durationMs: 9, runId: null });
    const wrapper = await mountPanel("3.5");
    await openPane(wrapper);

    // 3.5 only exists in the 两栈共享 sample's targets.
    expect((wrapper.get("#experiment-select").element as HTMLSelectElement).value).toBe("03-01-s3");

    await wrapper.get(".experiment__run").trigger("click");
    await flush(wrapper);

    expect(runCode).toHaveBeenCalledWith(expect.objectContaining({ language: "c", stdin: "abcde\n" }));
    expect(wrapper.text()).toContain("运行完成");
    expect(wrapper.text()).toContain("依次出栈: edcba");
  });

  it("restores the textbook listing after an edit", async () => {
    const wrapper = await mountPanel();
    await openPane(wrapper);

    const area = wrapper.get("#experiment-code");
    await area.setValue("int main(void) { /* 学生改坏了 */ }");
    expect((area.element as HTMLTextAreaElement).value).toContain("学生改坏了");

    await wrapper.get(".experiment__restore").trigger("click");
    expect((area.element as HTMLTextAreaElement).value).toContain("顺序栈的四个基本运算");
  });

  it("keeps an edited sample in place when the lesson moves on", async () => {
    const wrapper = await mountPanel("3.1.2");
    await openPane(wrapper);

    await wrapper.get("#experiment-code").setValue("int main(void) { /* 我的实验 */ }");
    await wrapper.setProps({ pageSection: "3.5" });
    await flush(wrapper);

    expect((wrapper.get("#experiment-select").element as HTMLSelectElement).value).toBe("03-01-s1");
    expect((wrapper.get("#experiment-code").element as HTMLTextAreaElement).value).toContain("我的实验");
  });

  it("explains a lesson that has no samples yet", async () => {
    listCodeSamples.mockResolvedValue({ lessons: [], sampleCount: 0 });
    const wrapper = mount(ExperimentPanel, { props: { coursewareKey: "99-99", pageSection: "" } });
    await flush(wrapper);
    await openPane(wrapper);

    expect(wrapper.text()).toContain("这个课时还没有配套代码");
  });

  it("treats a missing lesson as an empty state, not a failure", async () => {
    listCodeSamples.mockRejectedValue(
      Object.assign(new Error("该课时还没有配套的课堂代码"), { status: 404, code: "CODE_SAMPLES_LESSON_UNKNOWN" }),
    );
    const wrapper = mount(ExperimentPanel, { props: { coursewareKey: "01-01", pageSection: "" } });
    await flush(wrapper);
    await openPane(wrapper);

    expect(wrapper.text()).toContain("这个课时还没有配套代码");
    expect(wrapper.text()).not.toContain("加载失败");
  });

  it("still reports a real failure", async () => {
    listCodeSamples.mockRejectedValue(new Error("boom"));
    const wrapper = mount(ExperimentPanel, { props: { coursewareKey: "03-01", pageSection: "" } });
    await flush(wrapper);
    await openPane(wrapper);

    expect(wrapper.text()).toContain("代码样本加载失败");
  });
});
