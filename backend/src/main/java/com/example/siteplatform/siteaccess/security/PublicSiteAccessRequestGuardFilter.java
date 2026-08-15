package com.example.siteplatform.siteaccess.security;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Applies public site-access abuse controls before Spring MVC resolves headers,
 * content types or JSON request bodies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PublicSiteAccessRequestGuardFilter extends OncePerRequestFilter {
    static final int MAX_REQUEST_BODY_BYTES = 64 * 1024;

    private static final String BASE_PATH = "/api/v1/public/site-access";
    private static final RateLimitRule UNKNOWN_PATH_RULE = new RateLimitRule(
            "public-site-access-invalid", 60, Duration.ofMinutes(10));
    private static final Map<String, RateLimitRule> RULES = Map.ofEntries(
            Map.entry(BASE_PATH + "/invitations/resolve",
                    new RateLimitRule("public-site-visit-resolve", 60, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/invitations/submit",
                    new RateLimitRule("public-site-visit-submit", 10, Duration.ofMinutes(30))),
            Map.entry(BASE_PATH + "/project-profile",
                    new RateLimitRule("public-site-project-profile", 60, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/project-profile/images",
                    new RateLimitRule("public-site-project-profile-image", 120, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/project-location/route-image",
                    new RateLimitRule("public-site-project-route-image", 120, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/visitor-sessions",
                    new RateLimitRule("public-site-visitor-session", 20, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/visitor-profiles/list",
                    new RateLimitRule("public-site-visitor-profile-list", 60, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/visitor-profiles/detail",
                    new RateLimitRule("public-site-visitor-profile-detail", 60, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/visitor-profiles/disable",
                    new RateLimitRule("public-site-visitor-profile-disable", 10, Duration.ofMinutes(30))),
            Map.entry(BASE_PATH + "/guard/session",
                    new RateLimitRule("public-site-guard-session", 20, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/guard/submit",
                    new RateLimitRule("public-site-guard-submit", 10, Duration.ofMinutes(30))),
            Map.entry(BASE_PATH + "/guard/project-profile",
                    new RateLimitRule("public-site-guard-project-profile", 60, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/guard/project-profile/images",
                    new RateLimitRule("public-site-guard-project-profile-image", 120, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/guard/profiles/list",
                    new RateLimitRule("public-site-guard-profile-list", 60, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/guard/profiles/detail",
                    new RateLimitRule("public-site-guard-profile-detail", 60, Duration.ofMinutes(10))),
            Map.entry(BASE_PATH + "/guard/profiles/disable",
                    new RateLimitRule("public-site-guard-profile-disable", 10, Duration.ofMinutes(30))));

    private final RedisRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public PublicSiteAccessRequestGuardFilter(RedisRateLimitService rateLimitService,
                                              ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !(BASE_PATH.equals(path) || path.startsWith(BASE_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // A valid browser preflight does not invoke business code. The following real request
        // is still charged against its endpoint-specific quota.
        if (CorsUtils.isPreFlightRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = RULES.getOrDefault(path(request), UNKNOWN_PATH_RULE);
        try {
            rateLimitService.check(rule.scope(), request.getRemoteAddr(), rule.maximum(), rule.window());
        } catch (BusinessException exception) {
            writeError(response, normalizeStatus(exception.getCode()),
                    exception.getCode(), exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            // The public endpoint must fail closed if its abuse-control backend is unavailable.
            // Do not attach the infrastructure exception to the HTTP response or application log.
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE, "请求校验服务暂时不可用");
            return;
        }

        if (request.getContentLengthLong() > MAX_REQUEST_BODY_BYTES) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "请求体不能超过64KB");
            return;
        }

        byte[] body;
        try {
            body = request.getInputStream().readNBytes(MAX_REQUEST_BODY_BYTES + 1);
        } catch (IOException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    HttpServletResponse.SC_BAD_REQUEST, "请求体读取失败");
            return;
        }
        if (body.length > MAX_REQUEST_BODY_BYTES) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "请求体不能超过64KB");
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
    }

    private int normalizeStatus(Integer code) {
        return code != null && code >= 400 && code <= 599 ? code : 500;
    }

    private void writeError(HttpServletResponse response, int status, int code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.error(code, message));
    }

    private record RateLimitRule(String scope, int maximum, Duration window) {
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Requests are fully buffered before MVC binding; synchronous reads are ready.
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] target, int offset, int length) {
                    return input.read(target, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), requestCharset()));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        private java.nio.charset.Charset requestCharset() {
            String encoding = getCharacterEncoding();
            if (encoding == null) return StandardCharsets.UTF_8;
            try {
                return java.nio.charset.Charset.forName(encoding);
            } catch (Exception exception) {
                return StandardCharsets.UTF_8;
            }
        }
    }
}
