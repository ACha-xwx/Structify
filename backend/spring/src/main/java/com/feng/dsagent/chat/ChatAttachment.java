package com.feng.dsagent.chat;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.model.ModelMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;

public record ChatAttachment(
    String name,
    String type,
    String mimeType,
    String content,
    String rawBase64,
    String encoding,
    Long byteSize,
    String attachmentId,
    String downloadUrl
) {
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;

    public ChatAttachment(String name, String type, String mimeType, String content) {
        this(name, type, mimeType, content, null, null, null, null, null);
    }

    public ChatAttachment(String name, String type, String mimeType, String content,
                          String rawBase64, String encoding, Long byteSize, String attachmentId,
                          String downloadUrl) {
        this.name = name;
        this.type = type;
        this.mimeType = mimeType;
        this.content = content;
        this.rawBase64 = rawBase64;
        this.encoding = encoding;
        this.byteSize = byteSize;
        this.attachmentId = attachmentId;
        this.downloadUrl = downloadUrl;
    }

    public static List<ChatAttachment> validated(List<ChatAttachment> attachments) {
        if (attachments == null) return List.of();
        if (attachments.size() > 6) throw invalid();
        long bytes = 0;
        for (ChatAttachment item : attachments) {
            if (item == null || item.name == null || item.name.isBlank() || item.name.length() > 255
                || item.name.contains("\n") || item.name.contains("\r")) throw invalid();
            boolean stored = item.attachmentId != null && !item.attachmentId.isBlank();
            if ("file".equals(item.type)) {
                if (!stored && (item.content == null || item.content.isBlank())) throw invalid();
                if (item.content != null && (item.content.length() > 500_000 || item.content.indexOf('\0') >= 0)) throw invalid();
                bytes += item.byteSize != null ? item.byteSize : item.content == null ? 0 : item.content.getBytes(StandardCharsets.UTF_8).length;
            } else if ("image".equals(item.type)) {
                if (!IMAGE_TYPES.contains(item.mimeType) || (item.content != null && item.content.length() > 5_600_000)) throw invalid();
                if (!stored) {
                    if (item.content == null || item.content.isBlank()) throw invalid();
                    String prefix = "data:" + item.mimeType + ";base64,";
                    if (!item.content.startsWith(prefix)) throw invalid();
                    byte[] decoded;
                    try { decoded = Base64.getDecoder().decode(item.content.substring(prefix.length())); }
                    catch (IllegalArgumentException error) { throw invalid(); }
                    if (decoded.length > 4 * 1024 * 1024 || !isImage(decoded, item.mimeType)) throw invalid();
                    bytes += decoded.length;
                } else {
                    bytes += item.byteSize == null ? 0 : item.byteSize;
                }
            } else throw invalid();
            if (item.rawBase64 != null && !item.rawBase64.isBlank()) {
                try {
                    if (Base64.getDecoder().decode(item.rawBase64).length > 8 * 1024 * 1024) throw invalid();
                } catch (IllegalArgumentException error) { throw invalid(); }
            }
            if (bytes > MAX_TOTAL_BYTES) throw invalid();
        }
        return List.copyOf(attachments);
    }

    private static boolean isImage(byte[] value, String type) {
        if (value.length < 12) return false;
        return switch (type) {
            case "image/png" -> value[0] == (byte) 0x89 && value[1] == 'P' && value[2] == 'N' && value[3] == 'G';
            case "image/jpeg" -> value[0] == (byte) 0xff && value[1] == (byte) 0xd8 && value[2] == (byte) 0xff;
            case "image/gif" -> new String(value, 0, 6, StandardCharsets.US_ASCII).matches("GIF8[79]a");
            case "image/webp" -> new String(value, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(value, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }

    static ModelMessage message(String role, String prompt, List<ChatAttachment> attachments) {
        StringBuilder text = new StringBuilder(prompt);
        List<String> images = new ArrayList<>();
        for (ChatAttachment attachment : attachments) {
            text.append("\n\n附件：").append(attachment.name());
            if ("image".equals(attachment.type())) images.add(attachment.content());
            else text.append("\n<attached_file>\n").append(attachment.content()).append("\n</attached_file>");
        }
        return new ModelMessage(role, text.toString(), images);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "CHAT_ATTACHMENT_INVALID", "附件格式或大小不符合要求，请重新选择文本文件或图片");
    }
}
