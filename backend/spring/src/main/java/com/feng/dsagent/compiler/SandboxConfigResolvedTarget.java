package com.feng.dsagent.compiler;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Objects;

final class SandboxConfigResolvedTarget {

    private final URI uri;
    private final String host;
    private final int port;
    private final List<InetAddress> addresses;

    SandboxConfigResolvedTarget(URI uri, String host, int port, List<InetAddress> addresses) {
        this.uri = Objects.requireNonNull(uri, "uri");
        this.host = Objects.requireNonNull(host, "host");
        if (host.isBlank() || port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("Resolved sandbox target is invalid");
        }
        this.port = port;
        this.addresses = List.copyOf(Objects.requireNonNull(addresses, "addresses"));
        if (this.addresses.isEmpty() || this.addresses.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Resolved sandbox target has no addresses");
        }
    }

    URI uri() {
        return uri;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    List<InetAddress> addresses() {
        return addresses;
    }
}
