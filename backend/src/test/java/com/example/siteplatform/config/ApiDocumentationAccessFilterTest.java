package com.example.siteplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocumentationAccessFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void blocksAllDocumentationEntrypointsWhenDisabled() throws Exception {
        ApiDocumentationAccessFilter filter = filter(false, false, false);

        assertBlocked(filter, "/doc.html");
        assertBlocked(filter, "/webjars/js/app.js");
        assertBlocked(filter, "/v3/api-docs");
        assertBlocked(filter, "/v3/api-docs/swagger-config");
        assertBlocked(filter, "/swagger-ui/index.html");
    }

    @Test
    void allowsKnife4jResourcesWhenExplicitlyEnabled() throws Exception {
        ApiDocumentationAccessFilter filter = filter(true, false, false);

        assertAllowed(filter, "/doc.html");
        assertAllowed(filter, "/webjars/js/app.js");
        assertBlocked(filter, "/v3/api-docs");
    }

    @Test
    void allowsOpenApiAndSwaggerUiWhenExplicitlyEnabled() throws Exception {
        ApiDocumentationAccessFilter filter = filter(false, true, true);

        assertAllowed(filter, "/v3/api-docs");
        assertAllowed(filter, "/swagger-ui/index.html");
        assertBlocked(filter, "/doc.html");
    }

    @Test
    void doesNotAffectBusinessEndpoints() throws Exception {
        assertAllowed(filter(false, false, false), "/api/v1/auth/captcha");
    }

    private ApiDocumentationAccessFilter filter(
            boolean knife4jEnabled,
            boolean apiDocsEnabled,
            boolean swaggerUiEnabled) {
        return new ApiDocumentationAccessFilter(
                knife4jEnabled,
                apiDocsEnabled,
                swaggerUiEnabled,
                objectMapper);
    }

    private void assertBlocked(ApiDocumentationAccessFilter filter, String path)
            throws ServletException, IOException {
        MockHttpServletResponse response = execute(filter, path);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"code\":404", "请求的资源不存在");
    }

    private void assertAllowed(ApiDocumentationAccessFilter filter, String path)
            throws ServletException, IOException {
        MockHttpServletResponse response = execute(filter, path);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    private MockHttpServletResponse execute(ApiDocumentationAccessFilter filter, String path)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
