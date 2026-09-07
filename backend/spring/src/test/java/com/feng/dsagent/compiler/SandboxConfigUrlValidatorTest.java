package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.feng.dsagent.common.ApiException;
import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class SandboxConfigUrlValidatorTest {

    @Test
    void acceptsHttpsAndRejectsCredentialsQueriesFragmentsAndNonHttps() {
        SandboxConfigUrlValidator validator = validator(false, ip(1, 1, 1, 1));
        assertThat(validator.validate("https://sandbox.example/api").toString())
            .isEqualTo("https://sandbox.example/api");
        assertUnsafe(validator, "http://sandbox.example");
        assertUnsafe(validator, "https://user:pass@sandbox.example");
        assertUnsafe(validator, "https://sandbox.example?token=secret");
        assertUnsafe(validator, "https://sandbox.example#fragment");
        assertUnsafe(validator, "https://localhost");
    }

    @Test
    void rejectsPrivateAddressesReturnedByDns() {
        assertUnsafe(validator(false, ip(10, 0, 0, 7)), "https://sandbox.example");
        assertUnsafe(validator(false, ip(169, 254, 169, 254)), "https://sandbox.example");
        assertUnsafe(validator(false, ip(172, 16, 0, 9)), "https://sandbox.example");
        assertUnsafe(validator(false, ip(192, 168, 1, 9)), "https://sandbox.example");
    }

    @Test
    void rejectsPrivateIpv6LiteralsIncludingBracketedUriHosts() {
        SandboxConfigUrlValidator validator = new SandboxConfigUrlValidator(false);
        assertUnsafe(validator, "https://[fd00::1]");
        assertUnsafe(validator, "https://[fe80::1]");
        assertUnsafe(validator, "https://[::1]");
    }

    @Test
    void rejectsIpv4MappedIpv6AddressesReturnedByDns() {
        SandboxConfigUrlValidator validator = validator(false, ip(new byte[] {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 127, 0, 0, 1
        }));
        assertUnsafe(validator, "https://sandbox.example");
    }

    @Test
    void allowsLocalHttpOnlyForTestStyleConfiguration() {
        SandboxConfigUrlValidator validator = new SandboxConfigUrlValidator(true);
        assertThat(validator.validate("http://127.0.0.1:8080").getHost()).isEqualTo("127.0.0.1");
        assertThat(validator.validate("http://localhost:8080").getHost()).isEqualTo("localhost");
    }

    @Test
    void normalizesHostBeforeApplyingForbiddenHostRules() {
        SandboxConfigUrlValidator validator = validator(false, ip(1, 1, 1, 1));

        assertUnsafe(validator, "https://metadata.google.internal.");
        assertUnsafe(validator, "https://metadata.google.internal..");
        assertUnsafe(validator, "https://anything.LOCALHOST.");
        assertUnsafe(validator, "https://anything.INTERNAL.");
    }

    @Test
    void returnsResolvedAddressesForAConnectionLayerToPin() {
        InetAddress address = ip(1, 1, 1, 1);
        SandboxConfigResolvedTarget target = validator(false, address).resolve("https://sandbox.example/api");

        assertThat(target.uri()).isEqualTo(URI.create("https://sandbox.example/api"));
        assertThat(target.host()).isEqualTo("sandbox.example");
        assertThat(target.port()).isEqualTo(443);
        assertThat(target.addresses()).containsExactly(address);
    }

    private SandboxConfigUrlValidator validator(boolean allowLocalHttp, InetAddress... addresses) {
        return new SandboxConfigUrlValidator(allowLocalHttp, host -> addresses);
    }

    private void assertUnsafe(SandboxConfigUrlValidator validator, String value) {
        ApiException exception = assertThrows(ApiException.class, () -> validator.validate(value));
        assertThat(exception.code()).isEqualTo("SANDBOX_CONFIG_URL_UNSAFE");
    }

    private InetAddress ip(int first, int second, int third, int fourth) {
        return ip(new byte[] {
            (byte) first,
            (byte) second,
            (byte) third,
            (byte) fourth
        });
    }

    private InetAddress ip(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (java.net.UnknownHostException error) {
            throw new AssertionError(error);
        }
    }
}
