import type { Resource } from "../../shared/types/contracts";

export interface ResourcePreviewContent {
  bytes: ArrayBuffer;
  contentType: string;
  disposition: string;
}

export interface ResourcePreview {
  mode: "fixture";
  label: string;
  detail: string;
  resource: Resource;
  content: ResourcePreviewContent;
}

const localPreviewLabel = "本地资料预览";
const localPreviewDetail = "仅用于游客浏览，不代表已发布课程资料、账号权限或个人学习记录。";

export const resourcePreviewFixture = {
  label: localPreviewLabel,
  detail: localPreviewDetail,
  resources: [
    {
      id: "preview-sequential-list-handout",
      chapterId: "sequential-list",
      type: "SVG 讲义预览",
      title: "顺序表插入步骤图",
      description: "展示从尾部右移到写入新元素的本地教学步骤图。",
      sourceName: localPreviewLabel,
      versionLabel: "LOCAL FIXTURE",
      reviewStatus: "PUBLISHED",
      licenseScope: "PUBLIC",
      contentUrl: null,
    },
  ] satisfies Resource[],
} as const;

function createPreviewSvg(): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 1280 720" role="img" aria-label="顺序表插入步骤图本地预览">
  <rect width="1280" height="720" fill="#111111"/>
  <g fill="none" stroke="#343434" stroke-width="1"><path d="M0 120H1280M0 240H1280M0 360H1280M0 480H1280M0 600H1280"/><path d="M160 0V720M320 0V720M480 0V720M640 0V720M800 0V720M960 0V720M1120 0V720"/></g>
  <text x="72" y="86" fill="#a7a7a7" font-family="monospace" font-size="20" letter-spacing="3">LOCAL FIXTURE / SEQUENTIAL LIST</text>
  <text x="72" y="148" fill="#f4f4f4" font-family="sans-serif" font-size="44" font-weight="700">顺序表的插入</text>
  <text x="72" y="190" fill="#bcbcbc" font-family="sans-serif" font-size="22">从尾部开始右移，避免覆盖尚未读取的元素。</text>
  <g transform="translate(245 282)" fill="#2b2b2b" stroke="#cfcfcf" stroke-opacity=".72" stroke-width="2">
    <rect width="130" height="130"/><rect x="130" width="130" height="130"/><rect x="260" width="130" height="130" fill="#5c5c5c"/><rect x="390" width="130" height="130"/><rect x="520" width="130" height="130"/>
  </g>
  <g fill="#e9e9e9" font-family="monospace" font-size="32" text-anchor="middle"><text x="310" y="360">12</text><text x="440" y="360">18</text><text x="570" y="360">27</text><text x="700" y="360">31</text><text x="830" y="360">44</text></g>
  <path d="M570 454v58m0 0-12-16m12 16 12-16" fill="none" stroke="#ededed" stroke-width="3"/>
  <text x="570" y="550" fill="#ededed" font-family="monospace" font-size="20" text-anchor="middle">writeIndex</text>
  <text x="1028" y="330" fill="#9d9d9d" font-family="monospace" font-size="18" letter-spacing="2">INSERT VALUE</text>
  <text x="1028" y="372" fill="#f0f0f0" font-family="monospace" font-size="38">23</text>
  <text x="72" y="650" fill="#bcbcbc" font-family="sans-serif" font-size="20">本地教学预览，不保存学习记录</text>
</svg>`;
}

function toArrayBuffer(value: string): ArrayBuffer {
  const bytes = new TextEncoder().encode(value);
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

export function previewResourcesByChapter(chapterId: string): Resource[] {
  return resourcePreviewFixture.resources.filter((resource) => resource.chapterId === chapterId);
}

export function getResourcePreview(resourceId: string): ResourcePreview | null {
  const resource = resourcePreviewFixture.resources.find((item) => item.id === resourceId);
  if (!resource) return null;

  return {
    mode: "fixture",
    label: localPreviewLabel,
    detail: localPreviewDetail,
    resource,
    content: {
      bytes: toArrayBuffer(createPreviewSvg()),
      contentType: "image/svg+xml",
      disposition: "inline; filename=sequential-list-insert-preview.svg",
    },
  };
}
