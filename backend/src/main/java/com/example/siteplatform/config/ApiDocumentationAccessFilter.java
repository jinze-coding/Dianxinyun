package com.example.siteplatform.config;

import com.example.siteplatform.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiDocumentationAccessFilter extends OncePerRequestFilter {

    private final boolean knife4jEnabled;
    private final boolean apiDocsEnabled;
    private final boolean swaggerUiEnabled;
    private final ObjectMapper objectMapper;

    public ApiDocumentationAccessFilter(
            @Value("${knife4j.enable:false}") boolean knife4jEnabled,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiEnabled,
            ObjectMapper objectMapper) {
        this.knife4jEnabled = knife4jEnabled;
        this.apiDocsEnabled = apiDocsEnabled;
        this.swaggerUiEnabled = swaggerUiEnabled;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isBlocked(path)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.error(404, "请求的资源不存在"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isBlocked(String path) {
        if (matches(path, "/v3/api-docs") || matches(path, "/swagger-resources")) {
            return !apiDocsEnabled;
        }
        if (matches(path, "/swagger-ui") || "/swagger-ui.html".equals(path)) {
            return !swaggerUiEnabled;
        }
        if ("/doc.html".equals(path) || matches(path, "/webjars")) {
            return !knife4jEnabled;
        }
        return false;
    }

    private boolean matches(String path, String prefix) {
        return prefix.equals(path) || path.startsWith(prefix + "/");
    }
}
