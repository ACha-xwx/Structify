import { describe, expect, it } from "vitest";
import { getResourcePreview, previewResourcesByChapter, resourcePreviewFixture } from "./resource-preview";

describe("resourcePreviewFixture", () => {
  it("只提供明确标识的游客资料预览", () => {
    const resource = previewResourcesByChapter("sequential-list")[0];

    expect(resource).toEqual(resourcePreviewFixture.resources[0]);
    expect(resource?.versionLabel).toBe("LOCAL FIXTURE");
  });

  it("为预览资源生成独立的可浏览内容", () => {
    const preview = getResourcePreview("preview-sequential-list-handout");

    expect(preview?.mode).toBe("fixture");
    expect(preview?.content.contentType).toBe("image/svg+xml");
    expect(preview?.content.bytes.byteLength).toBeGreaterThan(100);
    expect(getResourcePreview("missing-resource")).toBeNull();
  });
});
