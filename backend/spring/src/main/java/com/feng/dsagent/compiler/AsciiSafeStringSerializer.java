package com.feng.dsagent.compiler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Writes a string as a JSON literal whose bytes are all US-ASCII.
 *
 * A learner's program prints whatever it likes, and in this material that is usually Chinese. On the
 * production edge a run whose stdout or stderr carried a non-ASCII byte never reached the browser:
 * Cloudflare answered with its own "error code: 502" (2026-09-24 — verified on production and stable
 * across every run: ASCII output 200, one Chinese character in stdout or stderr 502, while the same
 * code behind the same sandbox returned 200 locally and every other endpoint happily served Chinese).
 *
 * Escaping every non-ASCII character as a four-digit hexadecimal escape keeps the wire format pure
 * ASCII without changing the value: JSON.parse turns it back into the very same Chinese text, so the
 * browser shows exactly what the program printed. It is attached only to the two fields that carry
 * program output, never to the shared ObjectMapper, so stored lesson scripts, model prompts and SSE
 * copy stay readable UTF-8.
 */
final class AsciiSafeStringSerializer extends ValueSerializer<String> {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    @Override
    public void serialize(String value, JsonGenerator generator, SerializationContext context)
        throws JacksonException {
        generator.writeString(new AsciiLiteral(escape(value == null ? "" : value)));
    }

    /**
     * The body of a JSON string: the escapes JSON requires, plus hexadecimal escapes for non-ASCII.
     * The surrounding quotes are the generator's to write — supplying them here doubles them up.
     */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index += 1) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character >= 0x20 && character <= 0x7e) {
                        out.append(character);
                    } else {
                        appendEscaped(out, character);
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * Surrogate pairs are escaped one char at a time on purpose: that is how JSON carries a code point
     * outside the BMP, and a parser reassembles the pair into the same character.
     */
    private static void appendEscaped(StringBuilder out, char character) {
        out.append("\\u");
        for (int shift = 12; shift >= 0; shift -= 4) {
            out.append(HEX[(character >> shift) & 0xf]);
        }
    }

    /**
     * A string Jackson must write exactly as given. Handing the generator a pre-escaped value is what
     * keeps its value state machine consistent — writing raw text left it expecting a value at the next
     * property, which broke the document. Jackson adds the quotes; this class only carries the body.
     */
    private static final class AsciiLiteral implements SerializableString {

        private final String body;
        private final byte[] utf8;

        AsciiLiteral(String body) {
            this.body = body;
            this.utf8 = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getValue() {
            return body;
        }

        @Override
        public int charLength() {
            return body.length();
        }

        @Override
        public char[] asQuotedChars() {
            return body.toCharArray();
        }

        @Override
        public byte[] asQuotedUTF8() {
            return utf8;
        }

        @Override
        public byte[] asUnquotedUTF8() {
            return utf8;
        }

        @Override
        public int appendQuotedUTF8(byte[] buffer, int offset) {
            System.arraycopy(utf8, 0, buffer, offset, utf8.length);
            return utf8.length;
        }

        @Override
        public int appendQuoted(char[] buffer, int offset) {
            char[] chars = asQuotedChars();
            System.arraycopy(chars, 0, buffer, offset, chars.length);
            return chars.length;
        }

        @Override
        public int appendUnquotedUTF8(byte[] buffer, int offset) {
            byte[] bytes = asUnquotedUTF8();
            System.arraycopy(bytes, 0, buffer, offset, bytes.length);
            return bytes.length;
        }

        @Override
        public int appendUnquoted(char[] buffer, int offset) {
            return appendQuoted(buffer, offset);
        }

        @Override
        public int writeQuotedUTF8(OutputStream out) throws IOException {
            out.write(utf8);
            return utf8.length;
        }

        @Override
        public int writeUnquotedUTF8(OutputStream out) throws IOException {
            return writeQuotedUTF8(out);
        }

        @Override
        public int putQuotedUTF8(ByteBuffer buffer) throws IOException {
            buffer.put(utf8);
            return utf8.length;
        }

        @Override
        public int putUnquotedUTF8(ByteBuffer buffer) throws IOException {
            return putQuotedUTF8(buffer);
        }
    }
}
