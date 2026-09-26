package com.feng.dsagent.compiler;

import com.feng.dsagent.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
final class PistonCompilerGateway implements CompilerGateway {

    /**
     * The sandbox is somebody else's computer on the other side of the world. Every refusal used to
     * reach the learner as the same "服务暂时不可用", which made an upstream rate limit, a rejected
     * body and a dead instance look identical from here. The status and the first line of the body go
     * to the log so the next refusal can be read instead of guessed at.
     */
    private static final Logger log = LoggerFactory.getLogger(PistonCompilerGateway.class);
    private static final int PREVIEW_LIMIT = 512;

    private static final int MINIMUM_RESPONSE_LIMIT = 65_536;
    private static final int MAXIMUM_RESPONSE_LIMIT = 10_000_000;
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getMimeDecoder();

    private final CompilerProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SandboxConfigRuntimeSettingsSource runtimeSettings;

    @Autowired
    PistonCompilerGateway(
        CompilerProperties properties,
        ObjectMapper objectMapper,
        SandboxConfigRuntimeSettingsSource runtimeSettings
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.runtimeSettings = runtimeSettings;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    PistonCompilerGateway(CompilerProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new SandboxConfigRuntimeSettingsSource(properties));
    }

    @Override
    public CompilerExecution execute(SupportedLanguage language, String code, String stdin) {
        Optional<SandboxRuntimeSettings> configured = runtimeSettings.current();
        if (configured.isEmpty() || !configured.get().enabled()) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "COMPILER_NOT_CONFIGURED",
                "代码执行服务尚未配置"
            );
        }
        SandboxRuntimeSettings settings = configured.get();
        if (settings.provider() == SandboxProvider.JUDGE0) {
            return executeWithJudge0(settings, language, code, stdin);
        }
        return executeWithPiston(settings, language, code, stdin);
    }

    private CompilerExecution executeWithPiston(
        SandboxRuntimeSettings settings,
        SupportedLanguage language,
        String code,
        String stdin
    ) {
        String requestBody = serialize(new PistonRequest(
            language.pistonRuntime(),
            "*",
            stdin,
            List.of(new PistonFile(language.fileName(), code)),
            properties.compileTimeoutMillis(),
            properties.runTimeoutMillis()
        ));
        HttpRequest request = HttpRequest.newBuilder(executeUri(settings.baseUrl(), "/execute"))
            .timeout(properties.timeout())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    // Judge0 is the only provider still reachable, so this branch is mostly history;
                    // it still logs, so a revived provider never fails silently.
                    log.warn("沙箱拒绝：Piston 返回 {}，响应体 {}", response.statusCode(), preview(body));
                    throw upstreamUnavailable();
                }
                return parse(readLimited(body));
            }
        } catch (ApiException error) {
            throw error;
        } catch (HttpTimeoutException error) {
            throw new ApiException(
                HttpStatus.GATEWAY_TIMEOUT,
                "COMPILER_UPSTREAM_TIMEOUT",
                "代码执行服务响应超时，请稍后重试"
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "COMPILER_EXECUTION_INTERRUPTED",
                "代码执行请求已中断，请重试"
            );
        } catch (IOException error) {
            throw upstreamUnavailable();
        }
    }

    private CompilerExecution executeWithJudge0(
        SandboxRuntimeSettings settings,
        SupportedLanguage language,
        String code,
        String stdin
    ) {
        // Judge0 CE rejects request bodies that carry raw non-ASCII text with
        // "some attributes for this submission cannot be converted to UTF-8".
        // Textbook samples are full of Chinese comments, so this path always
        // speaks base64 both ways.
        String requestBody = serialize(new Judge0Request(
            encodeBase64(code),
            language == SupportedLanguage.C ? 50 : 71,
            encodeBase64(stdin)
        ));
        URI endpoint = executeUri(settings.baseUrl(), "/submissions?base64_encoded=true&wait=true");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(properties.timeout())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (isBusy(response.statusCode())) {
                    // The public sandbox throttles by IP; that is congestion, not a broken service.
                    log.warn("沙箱限流：Judge0 返回 {}", response.statusCode());
                    throw upstreamBusy();
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    // Without this line a refusal is indistinguishable from a dead sandbox: the log is
                    // the only place the upstream's own words ever appear.
                    log.warn("沙箱拒绝：Judge0 返回 {}，响应体 {}", response.statusCode(), preview(body));
                    throw upstreamUnavailable();
                }
                return parseJudge0(readLimited(body));
            }
        } catch (ApiException error) {
            throw error;
        } catch (HttpTimeoutException error) {
            throw new ApiException(
                HttpStatus.GATEWAY_TIMEOUT,
                "COMPILER_UPSTREAM_TIMEOUT",
                "代码执行服务响应超时，请稍后重试"
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "COMPILER_EXECUTION_INTERRUPTED",
                "代码执行请求已中断，请重试"
            );
        } catch (IOException error) {
            throw upstreamUnavailable();
        }
    }

    private String serialize(PistonRequest request) {
        return serializeObject(request);
    }

    private String serialize(Judge0Request request) {
        return serializeObject(request);
    }

    private String serializeObject(Object request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to encode compiler request", error);
        }
    }

    private String readLimited(InputStream body) throws IOException {
        long configuredLimit = (long) properties.maximumOutputLength() * 4L + 16_384L;
        int limit = (int) Math.min(
            MAXIMUM_RESPONSE_LIMIT,
            Math.max(MINIMUM_RESPONSE_LIMIT, configuredLimit)
        );
        byte[] bytes = body.readNBytes(limit + 1);
        if (bytes.length > limit) {
            throw invalidResponse();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private CompilerExecution parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode compile = root.path("compile");
            boolean compileFailed = compile.isObject() && exitCode(compile) != 0;
            if (compileFailed) {
                return new CompilerExecution(
                    "compile_error",
                    output(compile, "stdout"),
                    output(compile, "stderr")
                );
            }

            JsonNode run = root.path("run");
            if (!run.isObject()) {
                throw invalidResponse();
            }
            boolean runFailed = exitCode(run) != 0 || hasText(run.path("signal"));
            String status = runFailed ? "runtime_error" : "success";

            String stdout = output(run, "stdout");
            String compileError = compile.isObject() ? output(compile, "stderr") : "";
            String runError = output(run, "stderr");
            String stderr = joinOutput(compileError, runError);
            return new CompilerExecution(status, stdout, stderr);
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw invalidResponse();
        }
    }

    private CompilerExecution parseJudge0(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode status = root.path("status");
            int statusId = status.path("id").isNumber() ? status.path("id").asInt() : -1;
            if (statusId < 0) {
                throw invalidResponse();
            }
            String stdout = judge0Output(root, "stdout");
            String stderr = joinOutput(
                judge0Output(root, "compile_output"),
                joinOutput(judge0Output(root, "stderr"), judge0Output(root, "message"))
            );
            if (statusId == 1 || statusId == 2) {
                // wait=true should return a terminal result. Treat a still-pending
                // response as an upstream timeout instead of blaming the user's code.
                throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "COMPILER_UPSTREAM_TIMEOUT",
                    "代码执行服务响应超时，请稍后重试"
                );
            }
            if (statusId == 3) {
                return new CompilerExecution("success", stdout, stderr);
            }
            if (statusId == 6) {
                return new CompilerExecution("compile_error", stdout, stderr);
            }
            return new CompilerExecution("runtime_error", stdout, stderr);
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw invalidResponse();
        }
    }

    private URI executeUri(String baseUrl, String suffix) {
        return URI.create(baseUrl.strip().replaceAll("/+$", "") + suffix);
    }

    /** The first line of a refusal, flattened: enough to tell a rate limit from a rejected body. */
    private static String preview(InputStream body) {
        try {
            byte[] head = body.readNBytes(PREVIEW_LIMIT);
            return new String(head, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (IOException error) {
            return "(响应体不可读：" + error.getMessage() + ")";
        }
    }

    private static int exitCode(JsonNode phase) {
        JsonNode code = phase.path("code");
        return code.isNumber() ? code.asInt() : -1;
    }

    private static boolean hasText(JsonNode value) {
        return !value.isMissingNode() && !value.isNull() && !value.asText().isBlank();
    }

    private static String output(JsonNode phase, String field) {
        JsonNode value = phase.path(field);
        if (value.isTextual()) {
            return value.asText();
        }
        JsonNode combined = phase.path("output");
        return combined.isTextual() ? combined.asText() : "";
    }

    /**
     * Judge0 answers with base64 encoded text fields. A blank value stays blank; a value that is
     * not valid base64 is returned verbatim so a misbehaving upstream never costs the learner the
     * output it did produce.
     */
    private static String judge0Output(JsonNode phase, String field) {
        JsonNode value = phase.path(field);
        if (value.isTextual()) {
            return decodeBase64(value.asText());
        }
        JsonNode combined = phase.path("output");
        return combined.isTextual() ? decodeBase64(combined.asText()) : "";
    }

    private static String encodeBase64(String value) {
        return BASE64_ENCODER.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return new String(BASE64_DECODER.decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return value;
        }
    }

    private static String joinOutput(String first, String second) {
        if (first.isBlank()) {
            return second;
        }
        if (second.isBlank()) {
            return first;
        }
        return first + System.lineSeparator() + second;
    }

    private static ApiException upstreamUnavailable() {
        return new ApiException(
            HttpStatus.BAD_GATEWAY,
            "COMPILER_UPSTREAM_UNAVAILABLE",
            "代码执行服务暂时不可用，请稍后重试"
        );
    }

    /** 429/503 from the sandbox means "come back in a moment", which deserves its own message. */
    private static boolean isBusy(int statusCode) {
        return statusCode == 429 || statusCode == 503;
    }

    private static ApiException upstreamBusy() {
        return new ApiException(
            HttpStatus.TOO_MANY_REQUESTS,
            "COMPILER_UPSTREAM_BUSY",
            "此刻同时运行的人较多，请过几秒再试"
        );
    }

    private static ApiException invalidResponse() {
        return new ApiException(
            HttpStatus.BAD_GATEWAY,
            "COMPILER_UPSTREAM_INVALID_RESPONSE",
            "代码执行服务返回了无效结果，请稍后重试"
        );
    }

    private record PistonRequest(
        String language,
        String version,
        String stdin,
        List<PistonFile> files,
        int compile_timeout,
        int run_timeout
    ) {
    }

    private record PistonFile(String name, String content) {
    }

    private record Judge0Request(String source_code, int language_id, String stdin) {
    }
}
