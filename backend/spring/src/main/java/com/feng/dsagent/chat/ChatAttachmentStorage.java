package com.feng.dsagent.chat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Private, non-static storage for the original bytes of chat attachments. */
@Component
final class ChatAttachmentStorage {

    private final ChatAttachmentProperties properties;

    ChatAttachmentStorage(ChatAttachmentProperties properties) {
        this.properties = properties;
    }

    StoredChatAttachment store(long userId, long messageId, ChatAttachment attachment) {
        byte[] bytes = bytesOf(attachment);
        String id = UUID.randomUUID().toString();
        String storageKey = userId + "/" + id + ".bin";
        Path path = path(storageKey);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new StoredChatAttachment(id, userId, messageId, attachment.name(), attachment.type(),
                attachment.mimeType(), attachment.encoding(), bytes.length, sha256(bytes), storageKey, Instant.now());
        } catch (IOException error) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            throw new IllegalStateException("Unable to persist chat attachment", error);
        }
    }

    StoredChatAttachment copy(long userId, long messageId, StoredChatAttachment source) {
        if (source.userId() != userId) throw new IllegalArgumentException("Attachment owner mismatch");
        return new StoredChatAttachment(UUID.randomUUID().toString(), userId, messageId, source.name(),
            source.type(), source.mimeType(), source.encoding(), source.byteSize(), source.sha256(),
            source.storageKey(), Instant.now());
    }

    byte[] read(StoredChatAttachment attachment) {
        try {
            return Files.readAllBytes(path(attachment.storageKey()));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read chat attachment", error);
        }
    }

    void delete(StoredChatAttachment attachment) {
        try { Files.deleteIfExists(path(attachment.storageKey())); }
        catch (IOException error) { throw new IllegalStateException("Unable to delete chat attachment", error); }
    }

    ChatAttachment hydrate(StoredChatAttachment stored) {
        byte[] bytes = read(stored);
        String content;
        if ("image".equals(stored.type())) {
            content = "data:" + stored.mimeType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } else {
            content = new String(bytes, charset(stored.encoding()));
        }
        return new ChatAttachment(stored.name(), stored.type(), stored.mimeType(), content, null,
            stored.encoding(), stored.byteSize(), stored.id(), "/api/v1/chat/attachments/" + stored.id());
    }

    ChatAttachment hydrateForModel(StoredChatAttachment stored) {
        return hydrate(stored);
    }

    private byte[] bytesOf(ChatAttachment attachment) {
        if (attachment.rawBase64() != null && !attachment.rawBase64().isBlank()) {
            try { return Base64.getDecoder().decode(attachment.rawBase64()); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid attachment bytes", error); }
        }
        if ("image".equals(attachment.type()) && attachment.content() != null) {
            int comma = attachment.content().indexOf(',');
            if (comma > 0) {
                try { return Base64.getDecoder().decode(attachment.content().substring(comma + 1)); }
                catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid image bytes", error); }
            }
        }
        return (attachment.content() == null ? "" : attachment.content()).getBytes(charset(attachment.encoding()));
    }

    private Charset charset(String encoding) {
        if (encoding == null || encoding.isBlank()) return StandardCharsets.UTF_8;
        return switch (encoding.toLowerCase(java.util.Locale.ROOT).replace('_', '-')) {
            case "utf-16le", "utf16le" -> StandardCharsets.UTF_16LE;
            case "utf-16be", "utf16be" -> StandardCharsets.UTF_16BE;
            case "gb18030", "gbk" -> Charset.forName("GB18030");
            default -> StandardCharsets.UTF_8;
        };
    }

    private Path path(String storageKey) {
        Path root = properties.root();
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Invalid attachment path");
        return resolved;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
