import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { highlightChatCode } from "../chat-code";
import ChatCodeBlock from "./ChatCodeBlock.vue";

vi.mock("../chat-code", () => ({ highlightChatCode: vi.fn() }));
const highlight = vi.mocked(highlightChatCode);

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.resetAllMocks();
});

describe("ChatCodeBlock", () => {
  it("copies the original code with indentation and shows temporary feedback", async () => {
    vi.useFakeTimers();
    highlight.mockResolvedValue('<pre><code class="language-c">highlighted</code></pre>');
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    const code = 'int main(void)\n{\n    return 0;\n}\n';
    const view = mount(ChatCodeBlock, { props: { code, language: 'c' } });
    await flushPromises();
    expect(view.get('.code-block__language').text()).toBe('C');
    await view.get('button').trigger('click');
    await flushPromises();
    expect(writeText).toHaveBeenCalledWith(code);
    expect(view.get('button').attributes('aria-label')).toBe('已复制');
    await vi.advanceTimersByTimeAsync(2000);
    expect(view.get('button').attributes('aria-label')).toBe('复制');
    view.unmount();
  });

  it("discards older highlighting results when streamed code changes", async () => {
    let resolveOld!: (html: string) => void;
    highlight.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve; }));
    highlight.mockResolvedValueOnce('<pre><code>new</code></pre>');
    const view = mount(ChatCodeBlock, { props: { code: 'old', language: 'c' } });
    await view.setProps({ code: 'new' });
    await flushPromises();
    resolveOld('<pre><code>old</code></pre>');
    await flushPromises();
    expect(view.get('pre code').text()).toBe('new');
    view.unmount();
  });

  it("keeps untrusted code escaped when highlighting fails", async () => {
    highlight.mockRejectedValue(new Error('unavailable'));
    const view = mount(ChatCodeBlock, { props: { code: '<script>alert(1)</script>', language: 'unknown' } });
    await flushPromises();
    expect(view.get('pre code').text()).toBe('<script>alert(1)</script>');
    expect(view.find('script').exists()).toBe(false);
    view.unmount();
  });
});
