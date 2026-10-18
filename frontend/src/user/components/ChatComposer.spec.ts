import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import ChatComposer from "./ChatComposer.vue";

function mountComposer(overrides: Record<string, unknown> = {}) {
  return mount(ChatComposer, {
    props: {
      modelValue: "",
      chapterId: "",
      chapterOptions: [{ value: "", label: "全部章节" }],
      thinkingEnabled: false,
      reasoningEffort: "high",
      attachments: [],
      streaming: false,
      contextKey: 0,
      ...overrides,
    },
  });
}

function transfer(files: File[]) {
  return { types: ["Files"], files, dropEffect: "none" };
}

function dispatch(composer: ReturnType<typeof mountComposer>, type: string, files: File[]) {
  const event = new Event(type, { bubbles: true, cancelable: true });
  Object.defineProperty(event, "dataTransfer", { value: transfer(files) });
  composer.element.dispatchEvent(event);
  return event;
}

async function settleFileRead() {
  await new Promise<void>((resolve) => setTimeout(resolve, 20));
  await flushPromises();
}

describe("ChatComposer drag and drop uploads", () => {
  beforeEach(() => {
    vi.stubGlobal("crypto", { randomUUID: vi.fn(() => "attachment-1") });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows the drop hint and prevents the browser from opening dragged files", async () => {
    const composer = mountComposer();
    const file = new File(["int main() {}"], "main.c", { type: "text/plain" });

    const enter = dispatch(composer, "dragenter", [file]);
    await composer.vm.$nextTick();

    expect(enter.defaultPrevented).toBe(true);
    expect(composer.find(".composer-dropzone").exists()).toBe(true);
    expect(composer.text()).toContain("松开鼠标以上传文件");

    const over = dispatch(composer, "dragover", [file]);
    expect(over.defaultPrevented).toBe(true);
    expect((over as Event & { dataTransfer?: { dropEffect: string } }).dataTransfer?.dropEffect).toBe("copy");
    composer.unmount();
  });

  it("reads dropped text files through the existing attachment pipeline", async () => {
    const composer = mountComposer();
    const file = new File(["int main() {}"], "main.c", { type: "text/plain" });

    dispatch(composer, "dragenter", [file]);
    dispatch(composer, "drop", [file]);
    await settleFileRead();

    const updates = composer.emitted("update:attachments");
    expect(updates).toHaveLength(1);
    expect(updates?.[0]?.[0]).toEqual([expect.objectContaining({ name: "main.c", type: "file", content: "int main() {}" })]);
    expect(composer.find(".composer-dropzone").exists()).toBe(false);
    composer.unmount();
  });

  it("chooses the image path for dropped photos and preserves the original bytes", async () => {
    const composer = mountComposer();
    const png = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0, 0, 0, 0, 0, 0, 0, 0])], "diagram.png", { type: "image/png" });

    dispatch(composer, "drop", [png]);
    await settleFileRead();

    const update = composer.emitted("update:attachments") as unknown[][] | undefined;
    const attachment = (update?.[0]?.[0] as Array<{ type: string; mimeType: string; rawBase64: string }>)[0];
    expect(attachment.type).toBe("image");
    expect(attachment.mimeType).toBe("image/png");
    expect(attachment.rawBase64).toBe("iVBORwAAAAAAAAAA");
    composer.unmount();
  });

  it("keeps the existing validation message for unsupported dropped files", async () => {
    const composer = mountComposer();
    dispatch(composer, "drop", [new File(["not a pdf"], "notes.pdf", { type: "application/pdf" })]);
    await settleFileRead();
    await composer.vm.$nextTick();

    expect(composer.find('[role="alert"]').text()).toContain("暂不支持 PDF 和 Office 文件");
    expect(composer.emitted("update:attachments")).toBeUndefined();
    composer.unmount();
  });

  it("does not accept dropped files while locked", async () => {
    const composer = mountComposer({ streaming: true });
    const file = new File(["int main() {}"], "main.c", { type: "text/plain" });

    dispatch(composer, "dragenter", [file]);
    await composer.vm.$nextTick();
    dispatch(composer, "drop", [file]);
    await flushPromises();

    expect(composer.find(".composer-dropzone").exists()).toBe(false);
    expect(composer.emitted("update:attachments")).toBeUndefined();
    composer.unmount();
  });

  it("previews a persisted image while editing without embedding its original bytes", async () => {
    const image = { id: "stored-image", attachmentId: "stored-image", name: "diagram.png",
      type: "image", mimeType: "image/png", content: "", downloadUrl: "/api/v1/chat/attachments/stored-image" };
    const composer = mountComposer({ editing: true, attachments: [image] });
    expect(composer.get(".composer-attachment img").attributes("src")).toBe(image.downloadUrl);
    await composer.get(".composer-attachment__remove").trigger("click");
    expect(composer.emitted("update:attachments")?.[0]).toEqual([[]]);
    composer.unmount();
  });
});
