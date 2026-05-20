package com.example.siteplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.siteplatform.**.mapper")
public class SitePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(SitePlatformApplication.class, args);
    }
}
