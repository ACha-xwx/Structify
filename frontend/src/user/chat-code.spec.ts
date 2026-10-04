import { describe, expect, it } from "vitest";
import { highlightChatCode } from "./chat-code";

describe("chat code highlighting", () => {
  it("highlights C for both themes and safely escapes unknown languages", async () => {
    const code = '#include <stdio.h>\nint main(void) { return 0; }\n';
    const node = document.createElement('div');
    node.innerHTML = await highlightChatCode(code, 'c');
    expect(node.querySelector('code.language-c')?.textContent).toBe(code);
    expect(node.querySelector('[style*="--shiki-light"][style*="--shiki-dark"]')).not.toBeNull();
    expect(new Set(Array.from(node.querySelectorAll('span[style]')).map((span) => span.getAttribute('style'))).size).toBeGreaterThan(1);
    const unknown = '<img src=x onerror=alert(1)>';
    node.innerHTML = await highlightChatCode(unknown, 'not-a-language" onclick="alert(1)');
    expect(node.querySelector('code.language-text')?.textContent).toBe(unknown);
    expect(node.querySelector('img, [onclick], [onerror]')).toBeNull();
  });
});
