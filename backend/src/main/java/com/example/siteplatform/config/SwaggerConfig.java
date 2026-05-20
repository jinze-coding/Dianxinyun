package com.example.siteplatform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("电信云平台项目现场综合管理系统API")
                        .version("1.0.0")
                        .description("电信云平台项目现场综合管理系统接口文档")
                        .contact(new Contact()
                                .name("技术支持")
                                .email("support@site.com")));
    }
}
