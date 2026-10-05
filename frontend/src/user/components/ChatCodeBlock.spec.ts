import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { highlightChatCode } from "../chat-code";
import ChatCodeBlock from "./ChatCodeBlock.vue";
import { Blob as NodeBlob } from 'node:buffer';
import downloadIcon from '../../assets/chat/download.svg';
import shareIcon from '../../assets/chat/share.svg';
import { CHAT_CODE_STATE_KEY } from '../../shared/compiler/chat-code-import';
import { setLocale } from '../../shared/i18n/locale';

vi.mock("../chat-code", () => ({ highlightChatCode: vi.fn() }));
const highlight = vi.mocked(highlightChatCode);
const push = vi.fn();
vi.mock("vue-router", () => ({ useRouter: () => ({ push }) }));

beforeEach(() => setLocale("zh-CN"));

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.resetAllMocks();
  vi.restoreAllMocks();
});

describe("ChatCodeBlock", () => {
  it.each(['c', 'C', ' c '])("opens %s fragments unchanged in the C compiler", async (language) => {
    const code = '\t/* 中文🌳 */\r\nvoid fragment(Node *node) {\r\n\tnode->next = NULL;\r\n}\r\n';
    const view = mount(ChatCodeBlock, { props: { code, language } });
    const button = view.get('.code-block__share');
    expect(button.attributes('aria-label')).toBe('在 C 编译器里打开');
    expect(button.attributes('title')).toBe('在 C 编译器里打开');
    expect(view.get('.code-block__share-icon').attributes('style')).toContain(shareIcon);
    expect(view.get('.code-block__actions').findAll('button')[0].classes()).toContain('code-block__share');
    await button.trigger('click');
    expect(push).toHaveBeenCalledWith({ name: 'compiler', state: { [CHAT_CODE_STATE_KEY]: code } });
    view.unmount();
  });

  it.each(['text', 'plaintext', '', 'cpp', 'c++', 'cxx', 'cc', 'h', 'python', 'javascript'])
    ("hides the compiler action for %s even when the contents look like C", (language) => {
      const view = mount(ChatCodeBlock, { props: { code: '#include <stdio.h>\nint main(void) { return 0; }', language } });
      expect(view.find('.code-block__share').exists()).toBe(false);
      view.unmount();
    });

  it("updates the compiler action as the language and streaming code change", async () => {
    const view = mount(ChatCodeBlock, { props: { code: 'old', language: 'text', streaming: true } });
    expect(view.find('.code-block__share').exists()).toBe(false);
    await view.setProps({ code: 'void partial(', language: 'c' });
    await view.get('.code-block__share').trigger('click');
    expect(push).toHaveBeenCalledWith({ name: 'compiler', state: { [CHAT_CODE_STATE_KEY]: 'void partial(' } });
    await view.setProps({ language: 'cpp' });
    expect(view.find('.code-block__share').exists()).toBe(false);
    view.unmount();
  });

  it.each([['c', 'code.c'], ['python', 'code.py'], ['unknown/unsafe', 'code.txt']])("downloads %s code with its original whitespace and Unicode", async (language, filename) => {
    vi.useFakeTimers();
    vi.stubGlobal('Blob', NodeBlob);
    const createObjectURL = vi.fn().mockReturnValue('blob:code-download');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', class extends URL { static createObjectURL = createObjectURL; static revokeObjectURL = revokeObjectURL; });
    let saved!: HTMLAnchorElement;
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { saved = this; });
    const code = '/* 中文🌳 */\nint main(void) {\n    return 0;\n}\n';
    const view = mount(ChatCodeBlock, { props: { code, language } });
    await view.get('.code-block__download').trigger('click');
    expect(saved.download).toBe(filename);
    expect(saved.href).toBe('blob:code-download');
    expect(await createObjectURL.mock.calls[0][0].text()).toBe(code);
    expect(view.get('.code-block__download-icon').attributes('style')).toContain(downloadIcon);
    await vi.advanceTimersByTimeAsync(1000);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:code-download');
    expect(document.querySelector('a[download]')).toBeNull();
    view.unmount();
  });

  it("coalesces streamed changes while highlighting is in flight", async () => {
    vi.useFakeTimers();
    let finish!: (html: string) => void;
    highlight.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    highlight.mockResolvedValue('<pre><code>latest</code></pre>');
    const view = mount(ChatCodeBlock, { props: { code: 'first', language: 'c', streaming: true } });
    await vi.advanceTimersByTimeAsync(250);
    for (let i = 0; i < 100; i++) await view.setProps({ code: `chunk ${i}` });
    await vi.advanceTimersByTimeAsync(500);
    expect(highlight).toHaveBeenCalledTimes(1);
    finish('<pre><code>first</code></pre>');
    await flushPromises();
    await view.setProps({ code: 'latest', streaming: false });
    await flushPromises();
    expect(view.get('pre code').text()).toBe('latest');
    expect(highlight).toHaveBeenLastCalledWith('latest', 'c');
    view.unmount();
  });
  it("copies the original code with indentation and shows temporary feedback", async () => {
    vi.useFakeTimers();
    highlight.mockResolvedValue('<pre><code class="language-c">highlighted</code></pre>');
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    const code = 'int main(void)\n{\n    return 0;\n}\n';
    const view = mount(ChatCodeBlock, { props: { code, language: 'c' } });
    await flushPromises();
    expect(view.get('.code-block__language').text()).toBe('C');
    const copyButton = view.get('button[aria-label="复制"]');
    await copyButton.trigger('click');
    await flushPromises();
    expect(writeText).toHaveBeenCalledWith(code);
    expect(copyButton.attributes('aria-label')).toBe('已复制');
    await vi.advanceTimersByTimeAsync(2000);
    expect(copyButton.attributes('aria-label')).toBe('复制');
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
