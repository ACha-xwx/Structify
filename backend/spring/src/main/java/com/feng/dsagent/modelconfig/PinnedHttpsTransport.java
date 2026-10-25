package com.feng.dsagent.modelconfig;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class PinnedHttpsTransport {

    private static final int MAXIMUM_REQUEST_BYTES = 1_048_576;
    private static final int MAXIMUM_REQUEST_PATH_BYTES = 8_192;
    private static final int MAXIMUM_RESPONSE_LINE_BYTES = 8_192;
    private static final int MAXIMUM_RESPONSE_HEADER_BYTES = 32_768;

    private final PinnedHttpsConnectionFactory connections;

    @Autowired
    public PinnedHttpsTransport() {
        this(new TlsPinnedHttpsConnectionFactory());
    }

    public PinnedHttpsTransport(PinnedHttpsConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public PinnedHttpsResponse execute(
        ModelConfigResolvedTarget target,
        PinnedHttpsRequest request,
        Duration connectTimeout,
        Duration responseTimeout
    ) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(request, "request");
        validateRequest(target, request);

        IOException failedConnection = null;
        for (InetAddress address : target.addresses()) {
            PinnedHttpsConnection connection;
            try {
                connection = connections.connect(address, target.port(), target.host(), connectTimeout);
                connection.setReadTimeout(responseTimeout);
            } catch (IOException error) {
                failedConnection = error;
                continue;
            }
            try {
                writeRequest(connection.output(), target, request);
                return readResponse(connection);
            } catch (IOException error) {
                closeQuietly(connection);
                throw error;
            }
        }
        if (failedConnection != null) {
            throw failedConnection;
        }
        throw new IOException("No validated model service address is available");
    }

    public static boolean isSafeHeaderValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character > 0x7e) {
                return false;
            }
        }
        return true;
    }

    private void validateRequest(ModelConfigResolvedTarget target, PinnedHttpsRequest request) throws IOException {
        if (!isToken(request.method())
            || request.path() == null
            || !request.path().startsWith("/")
            || request.path().length() > MAXIMUM_REQUEST_PATH_BYTES
            || !isSafeRequestPath(request.path())
            || !isSafeHeaderValue(hostHeader(target))) {
            throw new IOException("Unsafe pinned HTTPS request");
        }
        int bytes = request.method().length() + request.path().length() + request.body().length;
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            if (!isToken(header.getKey()) || !isSafeHeaderValue(header.getValue())) {
                throw new IOException("Unsafe pinned HTTPS header");
            }
            bytes += header.getKey().length() + header.getValue().length() + 4;
        }
        if (bytes > MAXIMUM_REQUEST_BYTES) {
            throw new IOException("Pinned HTTPS request is too large");
        }
    }

    private void writeRequest(OutputStream output, ModelConfigResolvedTarget target, PinnedHttpsRequest request) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append(request.method()).append(' ').append(request.path()).append(" HTTP/1.1\r\n");
        head.append("Host: ").append(hostHeader(target)).append("\r\n");
        head.append("Connection: close\r\n");
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        if (request.body().length > 0) {
            head.append("Content-Length: ").append(request.body().length).append("\r\n");
        }
        head.append("\r\n");
        byte[] headerBytes = head.toString().getBytes(StandardCharsets.US_ASCII);
        if (headerBytes.length + request.body().length > MAXIMUM_REQUEST_BYTES) {
            throw new IOException("Pinned HTTPS request is too large");
        }
        output.write(headerBytes);
        if (request.body().length > 0) {
            output.write(request.body());
        }
        output.flush();
    }

    private PinnedHttpsResponse readResponse(PinnedHttpsConnection connection) throws IOException {
        InputStream input = new BufferedInputStream(connection.input());
        String statusLine = readAsciiLine(input, MAXIMUM_RESPONSE_LINE_BYTES);
        int statusCode = parseStatusCode(statusLine);
        Map<String, String> headers = readHeaders(input);
        InputStream body = responseBody(input, headers);
        return new PinnedHttpsResponse(statusCode, body, connection);
    }

    private int parseStatusCode(String statusLine) throws IOException {
        if (statusLine == null || !statusLine.startsWith("HTTP/")) {
            throw new IOException("Invalid pinned HTTPS response status");
        }
        int firstSpace = statusLine.indexOf(' ');
        int secondSpace = firstSpace < 0 ? -1 : statusLine.indexOf(' ', firstSpace + 1);
        String value = secondSpace < 0
            ? statusLine.substring(Math.max(firstSpace + 1, 0))
            : statusLine.substring(firstSpace + 1, secondSpace);
        if (value.length() != 3 || !value.chars().allMatch(character -> character >= '0' && character <= '9')) {
            throw new IOException("Invalid pinned HTTPS response status");
        }
        return Integer.parseInt(value);
    }

    private Map<String, String> readHeaders(InputStream input) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        int bytes = 0;
        while (true) {
            String line = readAsciiLine(input, MAXIMUM_RESPONSE_LINE_BYTES);
            if (line == null) {
                throw new IOException("Pinned HTTPS response ended before headers");
            }
            bytes += line.length() + 2;
            if (bytes > MAXIMUM_RESPONSE_HEADER_BYTES) {
                throw new IOException("Pinned HTTPS response headers are too large");
            }
            if (line.isEmpty()) {
                return headers;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new IOException("Invalid pinned HTTPS response header");
            }
            String name = line.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).strip();
            if (!isToken(name) || !isSafeResponseHeaderValue(value)) {
                throw new IOException("Invalid pinned HTTPS response header");
            }
            String previous = headers.putIfAbsent(name, value);
            if (previous != null
                && ("content-length".equals(name) || "transfer-encoding".equals(name))
                && !previous.equals(value)) {
                throw new IOException("Conflicting pinned HTTPS response header");
            }
        }
    }

    private InputStream responseBody(InputStream input, Map<String, String> headers) throws IOException {
        String transferEncoding = headers.get("transfer-encoding");
        if (transferEncoding != null) {
            if (!"chunked".equalsIgnoreCase(transferEncoding)) {
                throw new IOException("Unsupported pinned HTTPS transfer encoding");
            }
            return new ChunkedInputStream(input);
        }
        String contentLength = headers.get("content-length");
        if (contentLength == null) {
            return input;
        }
        try {
            long length = Long.parseLong(contentLength);
            if (length < 0) {
                throw new NumberFormatException();
            }
            return new ContentLengthInputStream(input, length);
        } catch (NumberFormatException error) {
            throw new IOException("Invalid pinned HTTPS content length", error);
        }
    }

    private String hostHeader(ModelConfigResolvedTarget target) {
        String host = target.host();
        String renderedHost = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        return target.port() == 443 ? renderedHost : renderedHost + ":" + target.port();
    }

    private boolean isToken(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!isAsciiLetterOrDigit(character) && "!#$%&'*+-.^_`|~".indexOf(character) < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isAsciiLetterOrDigit(char character) {
        return (character >= 'a' && character <= 'z')
            || (character >= 'A' && character <= 'Z')
            || (character >= '0' && character <= '9');
    }

    private boolean isSafeRequestPath(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character <= 0x20 || character > 0x7e) {
                return false;
            }
        }
        return true;
    }

    private boolean isSafeResponseHeaderValue(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character > 0x7e) {
                return false;
            }
        }
        return true;
    }

    public static String readAsciiLine(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                if (line.size() == 0) {
                    return null;
                }
                throw new IOException("Unexpected end of pinned HTTPS response");
            }
            if (value == '\n') {
                byte[] bytes = line.toByteArray();
                if (bytes.length == 0 || bytes[bytes.length - 1] != '\r') {
                    throw new IOException("Invalid pinned HTTPS response line ending");
                }
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            if (line.size() >= maximumBytes) {
                throw new IOException("Pinned HTTPS response line is too long");
            }
            line.write(value);
        }
    }

    private void closeQuietly(PinnedHttpsConnection connection) {
        try {
            connection.close();
        } catch (IOException ignored) {
            // The original request failure is more useful to callers.
        }
    }

    private static final class ContentLengthInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private ContentLengthInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            return read(single, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(single[0]);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int count = delegate.read(buffer, offset, (int) Math.min(length, remaining));
            if (count < 0) {
                throw new IOException("Pinned HTTPS response ended before its content length");
            }
            remaining -= count;
            return count;
        }
    }

    private static final class ChunkedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;
        private boolean receivedChunk;
        private boolean finished;

        private ChunkedInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            return read(single, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(single[0]);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (!nextChunk()) {
                return -1;
            }
            int count = delegate.read(buffer, offset, (int) Math.min(length, remaining));
            if (count < 0) {
                throw new IOException("Pinned HTTPS response ended during a chunk");
            }
            remaining -= count;
            return count;
        }

        private boolean nextChunk() throws IOException {
            while (remaining == 0 && !finished) {
                if (receivedChunk && !"".equals(readAsciiLine(delegate, 2))) {
                    throw new IOException("Invalid pinned HTTPS chunk delimiter");
                }
                String line = readAsciiLine(delegate, MAXIMUM_RESPONSE_LINE_BYTES);
                if (line == null) {
                    throw new IOException("Pinned HTTPS response ended before a chunk header");
                }
                int extension = line.indexOf(';');
                String size = (extension < 0 ? line : line.substring(0, extension)).strip();
                try {
                    remaining = Long.parseLong(size, 16);
                } catch (NumberFormatException error) {
                    throw new IOException("Invalid pinned HTTPS chunk size", error);
                }
                if (remaining < 0) {
                    throw new IOException("Invalid pinned HTTPS chunk size");
                }
                receivedChunk = true;
                if (remaining == 0) {
                    consumeTrailers();
                    finished = true;
                }
            }
            return !finished;
        }

        private void consumeTrailers() throws IOException {
            int bytes = 0;
            while (true) {
                String line = readAsciiLine(delegate, MAXIMUM_RESPONSE_LINE_BYTES);
                if (line == null) {
                    throw new IOException("Pinned HTTPS response ended in trailers");
                }
                bytes += line.length() + 2;
                if (bytes > MAXIMUM_RESPONSE_HEADER_BYTES) {
                    throw new IOException("Pinned HTTPS response trailers are too large");
                }
                if (line.isEmpty()) {
                    return;
                }
            }
        }
    }
}