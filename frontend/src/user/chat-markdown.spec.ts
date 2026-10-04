import { describe, expect, it } from "vitest";
import { answerParts, renderAnswer } from "./chat-markdown";

describe("chat Markdown", () => {
  it("preserves H1 through H6 around code blocks in both render paths", () => {
    const text = '# 一级标题\n\n正文。\n\n## **二级标题**\n\n```c\n#include <stdio.h>\n```\n\n### 三级标题\n\n#### 四级标题\n\n##### 五级标题\n\n###### 六级标题';
    for (const html of [renderAnswer(text), answerParts(text).filter((part) => part.kind === 'html').map((part) => part.html).join('')]) {
      const node = document.createElement('div');
      node.innerHTML = html;
      expect([...node.querySelectorAll('h1, h2, h3, h4, h5, h6')].map((heading) => heading.tagName))
        .toEqual(['H1', 'H2', 'H3', 'H4', 'H5', 'H6']);
      expect(node.querySelector('h2 strong')?.textContent).toBe('二级标题');
    }
  });

  it("accepts compact Chinese headings without rewriting code or ordinary hash text", () => {
    const text = '#核心结论\n\n正文。\n##做得好的地方\n\n`#普通代码`\n\n```c\n#include <stdio.h>\n#代码里的中文\n```\n\n#tag is ordinary text\n\n###需要注意的问题';
    const parts = answerParts(text);
    expect(parts.find((part) => part.kind === 'code')).toMatchObject({ code: '#include <stdio.h>\n#代码里的中文\n' });
    for (const html of [renderAnswer(text), parts.filter((part) => part.kind === 'html').map((part) => part.html).join('')]) {
      const node = document.createElement('div');
      node.innerHTML = html;
      expect([...node.querySelectorAll('h1, h2, h3')].map((heading) => heading.textContent))
        .toEqual(['核心结论', '做得好的地方', '需要注意的问题']);
      expect(node.textContent).toContain('#tag is ordinary text');
      expect(node.querySelector('p code')?.textContent).toBe('#普通代码');
    }
  });

  it("renders code as escaped code and preserves its language", () => {
    const node = document.createElement("div");
    node.innerHTML = renderAnswer('```c\nchar *s = "<script>alert(1)</script>";\n```');
    expect(node.querySelector("code.language-c")?.textContent).toContain('<script>alert(1)</script>');
    expect(node.querySelector("script")).toBeNull();
  });

  it("removes active HTML and unsafe link destinations from model text", () => {
    const node = document.createElement("div");
    node.innerHTML = renderAnswer('<img src=x onerror="alert(1)"><script>alert(1)</script>\n[link](javascript:alert%281%29)\n<a onclick="alert(1)" href="https://example.com">safe</a>');
    expect(node.querySelector("script, img, [onclick], [onerror]")).toBeNull();
    expect(node.querySelector('a[href^="javascript:"]')).toBeNull();
    expect(node.querySelector('a[href="https://example.com"]')).not.toBeNull();
  });

  it("separates code from prose in order while preserving indentation and reference links", () => {
    const parts = answerParts('[参考][docs]\n\n```c\nint main(void)\n{\n    return 0;\n}\n```\n\n结束。\n\n[docs]: https://example.com');
    expect(parts.map((part) => part.kind)).toEqual(['html', 'code', 'html']);
    expect(parts[1]).toEqual({ kind: 'code', language: 'c', code: 'int main(void)\n{\n    return 0;\n}\n' });
    expect(parts[0]).toMatchObject({ html: expect.stringContaining('href="https://example.com"') });
    expect(parts[2]).toMatchObject({ html: expect.stringContaining('结束。') });
  });

  it("preserves an unfinished streaming fence and defaults unlabelled code to text", () => {
    expect(answerParts('```\n  partial <value>')).toEqual([
      { kind: 'code', language: 'text', code: '  partial <value>\n' },
    ]);
  });

  it("sanitizes prose around code without treating code contents as HTML", () => {
    const parts = answerParts('<img src=x onerror="alert(1)">\n\n```unknown\n<script>alert(1)</script>\n```\n\n[link](javascript:alert%281%29)');
    const node = document.createElement('div');
    node.innerHTML = parts.filter((part) => part.kind === 'html').map((part) => part.html).join('');
    expect(node.querySelector('img, script, [onerror], a[href^="javascript:"]')).toBeNull();
    expect(parts.find((part) => part.kind === 'code')).toEqual({
      kind: 'code', code: '<script>alert(1)</script>\n', language: 'unknown',
    });
  });
});
