package com.feng.dsagent.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the shared error envelope straight to the servlet response.
 *
 * <p>Controller advice renders errors through the message converters, so the body has to satisfy the
 * request's {@code Accept} header. A client that only accepts {@code text/event-stream} - the normal
 * shape of a call to the SSE endpoint - can therefore never receive that body: the write fails, the
 * exception escapes the servlet, and the container logs an ERROR stack trace for nothing worse than
 * a missing login. Controlled errors are not subject to content negotiation, so the security entry
 * point and the fallback resolver both write the envelope here instead.
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static void write(
        HttpServletRequest request,
        HttpServletResponse response,
        ObjectMapper objectMapper,
        int status,
        String code,
        String message
    ) throws IOException {
        Object requestIdValue = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiError(
            code,
            message,
            requestIdValue == null ? "" : requestIdValue.toString(),
            List.of()
        )));
    }
}
