package com.feng.dsagent.compiler;

import com.feng.dsagent.common.ApiException;
import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
final class SandboxConfigUrlValidator {

    private static final Set<String> METADATA_HOSTS = Set.of(
        "metadata",
        "metadata.google",
        "metadata.google.internal",
        "metadata.azure.com",
        "instance-data",
        "instance-data.ec2.internal"
    );

    private final boolean allowLocalHttp;
    private final SandboxConfigHostResolver resolver;

    @Autowired
    SandboxConfigUrlValidator(Environment environment, SandboxConfigHostResolver resolver) {
        this(
            java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equals("test") || profile.equals("dev")),
            resolver
        );
    }

    SandboxConfigUrlValidator(boolean allowLocalHttp) {
        this(allowLocalHttp, InetAddress::getAllByName);
    }

    SandboxConfigUrlValidator(boolean allowLocalHttp, SandboxConfigHostResolver resolver) {
        this.allowLocalHttp = allowLocalHttp;
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    URI validate(String value) {
        return resolve(value).uri();
    }

    SandboxConfigResolvedTarget resolve(String value) {
        URI uri = validateStructure(value);
        if (hasBracketedHost(uri)) {
            throw unsafeUrl();
        }
        String host = normalizedHost(uri.getHost());
        boolean local = isLocalHost(host);
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean http = "http".equalsIgnoreCase(uri.getScheme());
        boolean localHttpException = allowLocalHttp && local && http;
        if (!https && !localHttpException) {
            throw unsafeUrl();
        }
        if (!localHttpException && isForbiddenHost(host)) {
            throw unsafeUrl();
        }

        if (localHttpException) {
            // Keep the test/dev loopback escape hatch deliberately narrow. It is
            // checked before DNS so a local machine need not publish a hostname.
            return new SandboxConfigResolvedTarget(
                uri,
                host,
                uri.getPort() == -1 ? 80 : uri.getPort(),
                java.util.List.of(loopbackAddress(host))
            );
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException error) {
            throw unsafeUrl();
        }
        if (addresses == null || addresses.length == 0) {
            throw unsafeUrl();
        }
        for (InetAddress address : addresses) {
            if (address == null || isForbiddenAddress(address)) {
                throw unsafeUrl();
            }
        }
        return new SandboxConfigResolvedTarget(
            uri,
            host,
            uri.getPort() == -1 ? 443 : uri.getPort(),
            java.util.List.of(addresses)
        );
    }

    private URI validateStructure(String value) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.strip()).normalize();
        } catch (IllegalArgumentException error) {
            throw unsafeUrl();
        }
        if (uri.isOpaque()
            || uri.getRawUserInfo() != null
            || uri.getRawQuery() != null
            || uri.getRawFragment() != null
            || uri.getPort() == 0
            || uri.getPort() > 65_535) {
            throw unsafeUrl();
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw unsafeUrl();
        }
        String host = normalizedHost(uri.getHost());
        if (host == null || host.isBlank()) {
            throw unsafeUrl();
        }
        return uri;
    }

    private String normalizedHost(String rawHost) {
        if (rawHost == null) {
            return null;
        }
        String host = rawHost.strip();
        if (host.isEmpty() || host.endsWith("..")) {
            return null;
        }
        while (host.endsWith(".") && host.length() > 1) {
            host = host.substring(0, host.length() - 1);
        }
        return ".".equals(host) ? null : host.toLowerCase(Locale.ROOT);
    }

    private boolean hasBracketedHost(URI uri) {
        String authority = uri.getRawAuthority();
        return authority != null && (authority.indexOf('[') >= 0 || authority.indexOf(']') >= 0);
    }

    private InetAddress loopbackAddress(String host) {
        if ("127.0.0.1".equals(host)) {
            try {
                return InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
            } catch (UnknownHostException error) {
                throw unsafeUrl();
            }
        }
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException error) {
            throw unsafeUrl();
        }
    }

    private boolean isForbiddenHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        return host.equals("localhost")
            || host.endsWith(".localhost")
            || host.endsWith(".local")
            || host.endsWith(".internal")
            || METADATA_HOSTS.contains(host);
    }

    private boolean isLocalHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }

    private boolean isForbiddenAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isForbiddenIpv4(bytes[0], bytes[1], bytes[2], bytes[3]);
        }
        if (isIpv4MappedIpv6(bytes)) {
            return isForbiddenIpv4(bytes[12], bytes[13], bytes[14], bytes[15]);
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return (first & 0xfe) == 0xfc || (first == 0xfe && (second & 0xc0) == 0x80);
    }

    private boolean isForbiddenIpv4(byte firstByte, byte secondByte, byte thirdByte, byte fourthByte) {
        int first = Byte.toUnsignedInt(firstByte);
        int second = Byte.toUnsignedInt(secondByte);
        int third = Byte.toUnsignedInt(thirdByte);
        int fourth = Byte.toUnsignedInt(fourthByte);
        if (first == 0 || first == 10 || first == 127 || first >= 224) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        if (first == 192 && (second == 0 || second == 168 || second == 2)) {
            return true;
        }
        if (first == 198 && (second == 18 || second == 19 || second == 51)) {
            return true;
        }
        if (first == 203 && second == 0 && third == 113) {
            return true;
        }
        return first == 100 && second == 100 && third == 100 && fourth == 200;
    }

    private boolean isIpv4MappedIpv6(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private ApiException unsafeUrl() {
        return new ApiException(HttpStatus.BAD_REQUEST, "SANDBOX_CONFIG_URL_UNSAFE", "沙箱服务地址不安全");
    }
}
