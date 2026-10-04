import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import ChatReasoning from "./ChatReasoning.vue";

afterEach(() => vi.useRealTimers());

describe("ChatReasoning", () => {
  it("types live reasoning with a cursor, then collapses when caught up", async () => {
    vi.useFakeTimers();
    const view = mount(ChatReasoning, { props: {
      reasoning: '', working: true, reasoningActive: true, retrieved: true, sources: [],
    } });
    await view.setProps({ reasoning: '树🌳' });
    expect(view.find('.reasoning__cursor').exists()).toBe(true);
    await vi.advanceTimersByTimeAsync(18);
    expect(view.get('.reasoning__text').text()).toBe('树');
    await vi.advanceTimersByTimeAsync(18);
    expect(view.get('.reasoning__text').text()).toBe('树🌳');
    await view.setProps({ working: false, reasoningActive: false, reasoningSeconds: 1.2, seconds: 2 });
    await flushPromises();
    expect(view.find('.reasoning__cursor').exists()).toBe(false);
    expect(view.get('.reasoning__master').attributes('aria-expanded')).toBe('false');
    await view.get('.reasoning__master').trigger('click');
    await view.get('.reasoning__thought').trigger('click');
    expect(view.get('.reasoning__thought').attributes('aria-expanded')).toBe('true');
    view.unmount();
  });

  it("shows saved reasoning immediately and keeps sources behind an explicit expand action", async () => {
    const view = mount(ChatReasoning, { props: {
      reasoning: '保存的思考', working: false, reasoningActive: false, retrieved: true,
      sources: [{ id: '1', chapterId: 'ch03', title: '栈的定义', content: '', source: 'textbook', pageLabel: null, score: 1, evidenceHash: '' }],
    } });
    expect(view.get('.reasoning__text').text()).toBe('保存的思考');
    expect(view.get('.reasoning__master').text()).toBe('已完成');
    expect(view.get('.reasoning__thought').text()).toBe('已思考');
    const sourcesChannel = view.get('.reasoning__sources-channel');
    expect(sourcesChannel.classes()).not.toContain('reasoning__channel--open');
    expect(sourcesChannel.element.hasAttribute('inert')).toBe(true);
    await view.get('.reasoning__master').trigger('click');
    await view.get('.reasoning__row').trigger('click');
    expect(view.get('.reasoning__sources').text()).toBe('栈的定义');
    expect(sourcesChannel.classes()).toContain('reasoning__channel--open');
    expect(sourcesChannel.element.hasAttribute('inert')).toBe(false);
    await view.get('.reasoning__row').trigger('click');
    expect(sourcesChannel.classes()).not.toContain('reasoning__channel--open');
    expect(sourcesChannel.element.hasAttribute('inert')).toBe(true);
    view.unmount();
  });

  it("keeps paragraph breaks but removes blank lines from saved reasoning", () => {
    const view = mount(ChatReasoning, { props: {
      reasoning: '第一段\r\n\r\n第二段\n \t\n\n第三段\n第四段',
      working: false, reasoningActive: false, retrieved: true, sources: [],
    } });
    expect(view.get('.reasoning__text').element.textContent).toBe('第一段\n第二段\n第三段\n第四段');
    expect(view.props('reasoning')).toContain('\r\n\r\n');
    view.unmount();
  });

  it("types normalized paragraphs across streaming chunks without losing the cursor or final text", async () => {
    vi.useFakeTimers();
    const view = mount(ChatReasoning, { props: {
      reasoning: '', working: true, reasoningActive: true, retrieved: true, sources: [],
    } });
    await view.setProps({ reasoning: '先检索\n' });
    await vi.advanceTimersByTimeAsync(180);
    await view.setProps({ reasoning: '先检索\n\n再解释\r\n\r\n写代码' });
    expect(view.find('.reasoning__cursor').exists()).toBe(true);
    await view.setProps({ working: false, reasoningActive: false });
    await vi.advanceTimersByTimeAsync(1000);
    expect(view.get('.reasoning__text').element.textContent).toBe('先检索\n再解释\n写代码');
    expect(view.find('.reasoning__cursor').exists()).toBe(false);
    expect(view.get('.reasoning__master').attributes('aria-expanded')).toBe('false');
    view.unmount();
  });
});
