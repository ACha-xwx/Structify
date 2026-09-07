package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpSandboxConnectionTesterTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsACompatiblePistonRuntimeResponse() throws Exception {
        startServer("/runtimes", 200, "[{\"language\":\"c\",\"version\":\"14.1.0\"}]");

        SandboxConnectionResult result = tester().test(settings(SandboxProvider.PISTON));

        assertThat(result).isEqualTo(new SandboxConnectionResult(true, "CONNECTION_OK"));
    }

    @Test
    void acceptsACompatibleJudge0AboutResponse() throws Exception {
        startServer("/about", 200, "{\"version\":\"1.0.0\"}");

        SandboxConnectionResult result = tester().test(settings(SandboxProvider.JUDGE0));

        assertThat(result).isEqualTo(new SandboxConnectionResult(true, "CONNECTION_OK"));
    }

    @Test
    void rejectsInvalidResponsesRedirectsAndDisabledSettings() throws Exception {
        startServer("/runtimes", 200, "{\"unexpected\":true}");
        assertThat(tester().test(settings(SandboxProvider.PISTON)).code())
            .isEqualTo("CONNECTION_RESPONSE_INVALID");

        server.stop(0);
        server = null;
        startServer("/runtimes", 302, "redirect");
        assertThat(tester().test(settings(SandboxProvider.PISTON)).code())
            .isEqualTo("REDIRECT_REJECTED");

        assertThat(tester().test(new SandboxRuntimeSettings(
            SandboxProvider.PISTON,
            "http://127.0.0.1:" + server.getAddress().getPort(),
            false
        ))).isEqualTo(new SandboxConnectionResult(false, "CONNECTION_FAILED"));
    }

    @Test
    void boundsTheResponseBody() throws Exception {
        startServer("/runtimes", 200, "[\"" + "x".repeat(70_000) + "\"]");

        SandboxConnectionResult result = tester().test(settings(SandboxProvider.PISTON));

        assertThat(result.code()).isEqualTo("CONNECTION_RESPONSE_INVALID");
    }

    private HttpSandboxConnectionTester tester() {
        return new HttpSandboxConnectionTester();
    }

    private SandboxRuntimeSettings settings(SandboxProvider provider) {
        return new SandboxRuntimeSettings(
            provider,
            "http://127.0.0.1:" + server.getAddress().getPort(),
            true
        );
    }

    private void startServer(String path, int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> respond(exchange, status, body));
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
