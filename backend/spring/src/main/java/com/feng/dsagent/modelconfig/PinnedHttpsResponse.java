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

public final class PinnedHttpsResponse implements AutoCloseable {
    private final int statusCode;
    private final InputStream body;
    private final PinnedHttpsConnection connection;

    public PinnedHttpsResponse(int statusCode, InputStream body, PinnedHttpsConnection connection) {
        this.statusCode = statusCode;
        this.body = body;
        this.connection = connection;
    }

    public int statusCode() {
        return statusCode;
    }

    public InputStream body() {
        return body;
    }

    public void setReadTimeout(Duration timeout) throws IOException {
        connection.setReadTimeout(timeout);
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
