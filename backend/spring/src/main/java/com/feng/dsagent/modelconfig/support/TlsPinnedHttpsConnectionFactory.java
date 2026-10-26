package com.feng.dsagent.modelconfig.support;

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

public final class TlsPinnedHttpsConnectionFactory implements PinnedHttpsConnectionFactory {

    @Override
    public PinnedHttpsConnection connect(InetAddress address, int port, String host, Duration timeout) throws IOException {
        Socket socket = new Socket();
        try {
            int timeoutMillis = timeoutMillis(timeout);
            socket.connect(new InetSocketAddress(address, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            SSLSocket tlsSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                .createSocket(socket, host, port, true);
            configureTls(tlsSocket, host);
            tlsSocket.startHandshake();
            return new TlsPinnedHttpsConnection(tlsSocket);
        } catch (IOException | RuntimeException error) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Preserve the original connection failure.
            }
            if (error instanceof IOException ioError) {
                throw ioError;
            }
            throw new IOException("Unable to establish pinned HTTPS connection", error);
        }
    }

    private void configureTls(SSLSocket socket, String host) throws IOException {
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        if (!isIpLiteral(host)) {
            try {
                parameters.setServerNames(List.of(new SNIHostName(host)));
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid pinned HTTPS host", error);
            }
        }
        socket.setSSLParameters(parameters);
    }

    private boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || isIpv4Literal(host);
    }

    private boolean isIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            int value = 0;
            for (int index = 0; index < part.length(); index++) {
                char character = part.charAt(index);
                if (character < '0' || character > '9') {
                    return false;
                }
                value = value * 10 + (character - '0');
            }
            if (value > 255) {
                return false;
            }
        }
        return true;
    }

    private int timeoutMillis(Duration timeout) throws IOException {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IOException("Pinned HTTPS timeout must be positive");
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeout.toMillis()));
    }

    private static final class TlsPinnedHttpsConnection implements PinnedHttpsConnection {
        private final SSLSocket socket;

        private TlsPinnedHttpsConnection(SSLSocket socket) {
            this.socket = socket;
        }

        @Override
        public InputStream input() throws IOException {
            return socket.getInputStream();
        }

        @Override
        public OutputStream output() throws IOException {
            return socket.getOutputStream();
        }

        @Override
        public void setReadTimeout(Duration timeout) throws IOException {
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                throw new IOException("Pinned HTTPS timeout must be positive");
            }
            socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeout.toMillis())));
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
