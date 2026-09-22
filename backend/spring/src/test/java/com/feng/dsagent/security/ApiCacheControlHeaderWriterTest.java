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

    @Test
    void leavesCoursewareResponsesToTheControllerCachingPolicy() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(
            new MockHttpServletRequest("GET", "/api/v1/presentation/slides/ch03-deck01-3caa7e8-s003/image"),
            response
        );

        assertThat(response.getHeader("Cache-Control")).isNull();
        assertThat(response.getHeader("Pragma")).isNull();
        assertThat(response.getHeader("Expires")).isNull();
    }
}
