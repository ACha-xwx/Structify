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

public final class PinnedHttpsRequest {
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final byte[] body;

    public PinnedHttpsRequest(String method, String path, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.headers = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(headers, "headers")));
        this.body = Objects.requireNonNull(body, "body").clone();
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public byte[] body() {
        return body.clone();
    }
}
