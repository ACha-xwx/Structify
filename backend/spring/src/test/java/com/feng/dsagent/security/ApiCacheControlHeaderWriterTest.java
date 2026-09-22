package com.feng.dsagent.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The writer replaces Spring Security's default cache headers. What it must not do is widen the policy
 * beyond the read-only courseware surface: credentials and per-user payloads have to stay unstorable.
 */
class ApiCacheControlHeaderWriterTest {

    private final SecurityConfig.ApiCacheControlHeaderWriter writer =
        new SecurityConfig.ApiCacheControlHeaderWriter();

    @Test
    void keepsNoStoreOnPerUserApiTraffic() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(new MockHttpServletRequest("GET", "/api/v1/classroom/sessions/42"), response);

        assertThat(response.getHeader("Cache-Control"))
            .isEqualTo(SecurityConfig.ApiCacheControlHeaderWriter.NO_STORE);
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getHeader("Expires")).isEqualTo("0");
    }

    @Test
    void keepsNoStoreOnAuthentication() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(new MockHttpServletRequest("POST", "/api/v1/auth/login"), response);

        assertThat(response.getHeader("Cache-Control"))
            .isEqualTo(SecurityConfig.ApiCacheControlHeaderWriter.NO_STORE);
    }

    /**
     * The writer defers to a handler that has already stated its own freshness policy. The response has to
     * start from what the handler would have committed, because the deferral is exactly what is under test:
     * the writer reads the response, it does not match paths. Asserting an empty response stays empty
     * asserted the opposite of the implementation - the writer is the thing that guarantees every other
     * response is unstorable.
     */
    @Test
    void leavesCoursewareResponsesToTheControllerCachingPolicy() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String handlerPolicy = "max-age=31536000, public, immutable";
        response.setHeader("Cache-Control", handlerPolicy);

        writer.writeHeaders(
            new MockHttpServletRequest(
                "GET",
                "/api/v1/presentation/slides/ch03-deck01-3caa7e8-s003/9f1c0b2a4d6e8f01.webp"
            ),
            response
        );

        assertThat(response.getHeader("Cache-Control")).isEqualTo(handlerPolicy);
        assertThat(response.getHeader("Pragma")).isNull();
        assertThat(response.getHeader("Expires")).isNull();
    }

    /**
     * The other half of the same rule: a response no handler described keeps the no-store guarantee, even
     * on a courseware url. A signed page is cacheable because the handler chose a policy for it, never
     * because of where it lives.
     */
    @Test
    void stampsNoStoreOnTheSameUrlWhenNoHandlerStatedAPolicy() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(
            new MockHttpServletRequest(
                "GET",
                "/api/v1/presentation/slides/ch03-deck01-3caa7e8-s003/9f1c0b2a4d6e8f01.webp"
            ),
            response
        );

        assertThat(response.getHeader("Cache-Control"))
            .isEqualTo(SecurityConfig.ApiCacheControlHeaderWriter.NO_STORE);
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }
}
