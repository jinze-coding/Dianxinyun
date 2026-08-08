package com.example.siteplatform.config;

import com.example.siteplatform.system.security.BusinessModulePermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final BusinessModulePermissionInterceptor businessModulePermissionInterceptor;

    public WebCorsConfig(BusinessModulePermissionInterceptor businessModulePermissionInterceptor) {
        this.businessModulePermissionInterceptor = businessModulePermissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(businessModulePermissionInterceptor)
                .addPathPatterns(
                        "/api/v1/project-documents",
                        "/api/v1/project-documents/**",
                        "/api/v1/document-folders",
                        "/api/v1/document-folders/**",
                        "/api/v1/inspection",
                        "/api/v1/inspection/**",
                        "/api/v1/electric-boxes",
                        "/api/v1/electric-boxes/**",
                        "/api/v1/quality/issues",
                        "/api/v1/quality/issues/**",
                        "/api/v1/site-access",
                        "/api/v1/site-access/**",
                        "/api/v1/files",
                        "/api/v1/files/**"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:3002",
                        "http://127.0.0.1:3002",
                        "http://localhost:3003",
                        "http://127.0.0.1:3003"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
