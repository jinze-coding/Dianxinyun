package com.example.siteplatform.siteaccess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit, one-shot wiring. Normal application profiles never instantiate the migration tool. */
@Configuration(proxyBeanMethods = false)
@Profile("visitor-legacy-reencrypt")
@ConditionalOnProperty(prefix = "site-access.legacy-reencryption", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(VisitorLegacyReencryptionProperties.class)
class VisitorLegacyReencryptionConfiguration {

    @Bean
    ApplicationRunner visitorLegacyReencryptionRunner(
            JdbcTemplate jdbc,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            VisitorDataCryptoService currentCrypto,
            VisitorLegacyReencryptionProperties properties,
            Environment environment) {
        VisitorLegacyReencryptionStore store = new JdbcVisitorLegacyReencryptionStore(jdbc);
        VisitorLegacyReencryptionTool tool = new VisitorLegacyReencryptionTool(
                store, transactionTemplate, objectMapper, currentCrypto,
                VisitorDataCryptoService.legacyDevelopmentMigrationCipher(), properties);
        return args -> {
            requireOfflineExecution(environment);
            tool.execute();
        };
    }

    static void requireOfflineExecution(Environment environment) {
        String webApplicationType = environment.getProperty("spring.main.web-application-type", "");
        Boolean schedulingEnabled = environment.getProperty("app.scheduling.enabled", Boolean.class);
        if (!"none".equalsIgnoreCase(webApplicationType.trim())) {
            throw new IllegalStateException("访客历史密钥迁移只允许 spring.main.web-application-type=none 的离线进程");
        }
        if (!Boolean.FALSE.equals(schedulingEnabled)) {
            throw new IllegalStateException("访客历史密钥迁移必须显式设置 app.scheduling.enabled=false");
        }
    }
}
