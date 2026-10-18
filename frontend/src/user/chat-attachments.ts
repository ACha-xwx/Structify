import type { ChatAttachment } from "../shared/types/chat";

export const CHAT_FILE_ACCEPT = ".c,.h,.cpp,.hpp,.cc,.md,.markdown,.txt,.csv,.tsv,.json,.yaml,.yml,.xml,.html,.css,.js,.ts,.jsx,.tsx,.py,.java,.go,.rs,.sql,.log,.ini,.toml,.rst,.sh";
export const CHAT_PHOTO_ACCEPT = "image/jpeg,image/png,image/gif,image/webp";
export const MAX_ATTACHMENTS = 6;
export const MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;

export interface ComposerAttachment extends ChatAttachment {
  id: string;
  size: number;
}

export class AttachmentError extends Error {
  constructor(public readonly reason: "type" | "size" | "empty" | "read") { super(reason); }
}

function bytesOf(file: File): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.onerror = () => reject(new AttachmentError("read"));
    reader.readAsArrayBuffer(file);
  });
}

function imageType(bytes: Uint8Array): string | null {
  if (bytes.length < 12) return null;
  if (bytes[0] === 0x89 && bytes[1] === 80 && bytes[2] === 78 && bytes[3] === 71) return "image/png";
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "image/jpeg";
  const ascii = (from: number, to: number) => String.fromCharCode(...bytes.slice(from, to));
  if (/GIF8[79]a/.test(ascii(0, 6))) return "image/gif";
  if (ascii(0, 4) === "RIFF" && ascii(8, 12) === "WEBP") return "image/webp";
  return null;
}

function base64Of(bytes: Uint8Array): string {
  let binary = "";
  for (let from = 0; from < bytes.length; from += 8192) {
    binary += String.fromCharCode(...bytes.subarray(from, from + 8192));
  }
  return btoa(binary);
}

export async function readChatAttachment(file: File, type: "file" | "image"): Promise<ComposerAttachment> {
  if (!file.size) throw new AttachmentError("empty");
  if (file.size > (type === "image" ? 4 : 2) * 1024 * 1024) throw new AttachmentError("size");
  const bytes = new Uint8Array(await bytesOf(file));
  let content: string;
  let encoding = "utf-8";
  let mimeType: string;
  if (type === "image") {
    const detected = imageType(bytes);
    if (!detected) throw new AttachmentError("type");
    mimeType = detected;
    content = `data:${mimeType};base64,${base64Of(bytes)}`;
  } else {
    const extension = file.name.toLowerCase().match(/\.[^.]+$/)?.[0];
    if (!extension || !CHAT_FILE_ACCEPT.split(",").includes(extension)) throw new AttachmentError("type");
    if (imageType(bytes)) throw new AttachmentError("type");
    try {
      encoding = bytes[0] === 0xff && bytes[1] === 0xfe ? "utf-16le"
        : bytes[0] === 0xfe && bytes[1] === 0xff ? "utf-16be" : "utf-8";
      content = new TextDecoder(encoding, { fatal: true }).decode(bytes);
    } catch {
      try {
        encoding = "gb18030";
        content = new TextDecoder("gb18030", { fatal: true }).decode(bytes);
      }
      catch { throw new AttachmentError("type"); }
    }
    if (!content.trim()) throw new AttachmentError("empty");
    if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(content)) throw new AttachmentError("type");
    if (content.length > 500_000) throw new AttachmentError("size");
    mimeType = "text/plain";
  }
  return {
    id: crypto.randomUUID(),
    size: file.size,
    name: file.name.replace(/[\r\n]/g, " ").slice(0, 255),
    type,
    mimeType,
    content,
    rawBase64: base64Of(bytes),
    encoding: type === "file" ? encoding : undefined,
    byteSize: bytes.byteLength,
  };
}

export function attachmentPayload(items: ComposerAttachment[]): ChatAttachment[] {
  return items.map(({ name, type, mimeType, content, rawBase64, encoding, byteSize, attachmentId, downloadUrl }) => ({
    name, type, mimeType, content, rawBase64, encoding, byteSize, attachmentId, downloadUrl,
  }));
}
