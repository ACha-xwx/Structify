package com.feng.dsagent.security;

import com.feng.dsagent.auth.RolePolicy;
import com.feng.dsagent.common.ApiError;
import com.feng.dsagent.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Signed courseware page urls are reachable without a session on purpose. A cache between the browser
     * and the origin fills itself with no credentials at all, so a url that needed a cookie could never be
     * stored - and a page turn would keep costing a full round trip. What stands in for the session is the
     * signature in the path, which the handler verifies; the pattern only admits the two forms a signed url
     * takes and deliberately does not match the session-authenticated {@code /image} path beside it.
     */
    private static final String[] SIGNED_COURSEWARE_IMAGES = {
        "/api/v1/presentation/slides/*/*.png",
        "/api/v1/presentation/slides/*/*.webp",
    };

    @Bean
    JwtTokenService jwtTokenService(SecurityProperties properties, ObjectMapper objectMapper, Clock clock) {
        return new JwtTokenService(
            properties.jwtSecret(),
            properties.tokenTtl(),
            properties.issuer(),
            objectMapper,
            clock
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    RolePolicy rolePolicy(SecurityProperties properties) {
        return new RolePolicy(properties.bootstrapAdminEmail(), properties.teacherEmails());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseOrigins(properties.corsAllowedOrigins()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtFilter,
        ObjectMapper objectMapper
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/node-compat-token").authenticated()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/chapters/**", "/api/v1/resources/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/knowledge/search").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/chat", "/api/v1/chat/stream").permitAll()
                .requestMatchers("/api/v1/code/**", "/api/v1/animations/generate").permitAll()
                .requestMatchers(HttpMethod.GET, SIGNED_COURSEWARE_IMAGES).permitAll()
                .anyRequest().authenticated()
            )
            // Spring Security stamps every response with no-store by default. That is right for tokens and
            // per-user payloads, but it also forced the browser to re-download every courseware page and
            // catalogue on each turn, which is what made stepping through a deck feel slow on a link that
            // pays a full round trip per request. The writer below keeps the default everywhere except the
            // read-only courseware surface, which sets its own policy in PresentationController.
            .headers(headers -> headers
                .cacheControl(cache -> cache.disable())
                .addHeaderWriter(new ApiCacheControlHeaderWriter())
            )
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> writeError(
                    request,
                    response,
                    objectMapper,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_REQUIRED",
                    "请先登录"
                ))
                .accessDeniedHandler((request, response, exception) -> writeError(
                    request,
                    response,
                    objectMapper,
                    HttpServletResponse.SC_FORBIDDEN,
                    "AUTH_FORBIDDEN",
                    "当前账号无权执行该操作"
                ))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    /**
     * Writes the same cache headers Spring Security uses by default, except where a handler has already
     * stated its own freshness policy - the read-only courseware does, because a cacheable page turn is the
     * difference between one round trip and none. Everything else, including credentials, per-user payloads
     * and classroom state, keeps the no-store guarantee, so no intermediary can start reusing it.
     */
    static final class ApiCacheControlHeaderWriter implements HeaderWriter {

        /** The exact value the default writer stamps, kept in one place so the two cannot drift apart. */
        static final String NO_STORE = "no-cache, no-store, max-age=0, must-revalidate";

        @Override
        public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
            // Read at commit time, so a value the handler set is visible here and stands.
            if (response.getHeader("Cache-Control") != null) {
                return;
            }
            response.setHeader("Cache-Control", NO_STORE);
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }
    }

    private List<String> parseOrigins(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .distinct()
            .toList();
    }

    private void writeError(
        HttpServletRequest request,
        HttpServletResponse response,
        ObjectMapper objectMapper,
        int status,
        String code,
        String message
    ) throws java.io.IOException {
        Object requestIdValue = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        String requestId = requestIdValue == null ? "" : requestIdValue.toString();
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiError(
            code,
            message,
            requestId,
            List.of()
        )));
    }
}
