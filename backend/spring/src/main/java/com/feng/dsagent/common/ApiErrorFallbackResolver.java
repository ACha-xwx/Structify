package com.feng.dsagent.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers clients that cannot read the JSON error envelope.
 *
 * <p>{@link ApiExceptionHandler} renders errors through the message converters, which makes the body
 * subject to the request's {@code Accept} header. A client that only accepts
 * {@code text/event-stream} - the normal shape of a call to the SSE endpoint, and what the browser
 * client sends - can never receive that body: the write fails, the exception escapes the servlet,
 * and the container logs an ERROR stack trace for nothing worse than a missing login. The observed
 * consequences were a fake server fault per logged-out streaming attempt and a {@code 500
 * INTERNAL_ERROR} whenever the {@code Accept} header did not match the endpoint's {@code produces}.
 *
 * <p>This resolver runs ahead of the advice and only intervenes when negotiation cannot work, so
 * ordinary requests keep the advice's behaviour unchanged. Any other exception is reported exactly as
 * the advice's catch-all would report it, because a client that cannot read JSON cannot be told more.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class ApiErrorFallbackResolver implements HandlerExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorFallbackResolver.class);

    private final ObjectMapper objectMapper;

    ApiErrorFallbackResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelAndView resolveException(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception error
    ) {
        if (acceptsJson(request) || response.isCommitted()) {
            return null;
        }
        int status;
        String code;
        String message;
        if (error instanceof ApiException apiError) {
            status = apiError.status().value();
            code = apiError.code();
            message = apiError.getMessage();
        } else {
            // Without this the failure vanishes into a bare 500 and nothing is ever diagnosed.
            log.warn(
                "unhandled error on {} {} -> INTERNAL_ERROR",
                request.getMethod(),
                request.getRequestURI(),
                error
            );
            status = HttpStatus.INTERNAL_SERVER_ERROR.value();
            code = "INTERNAL_ERROR";
            message = "服务器暂时无法处理该请求";
        }
        try {
            ApiErrors.write(request, response, objectMapper, status, code, message);
        } catch (IOException unwritable) {
            // The client went away or the response can no longer be written to; there is no second
            // chance left, so report it as unresolved rather than masking it.
            return null;
        }
        return new ModelAndView();
    }

    /**
     * True when the envelope could be delivered as it is written today. An absent header means the
     * client stated no preference, which the converters treat as "anything goes".
     */
    private boolean acceptsJson(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return true;
        }
        for (String range : accept.split(",")) {
            String type = range.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (type.equals("*/*") || type.equals("application/*") || type.endsWith("/json") || type.endsWith("+json")) {
                return true;
            }
        }
        return false;
    }
}
