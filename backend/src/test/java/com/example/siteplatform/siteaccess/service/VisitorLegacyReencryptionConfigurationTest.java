package com.example.siteplatform.siteaccess.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.env.MockEnvironment;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class VisitorLegacyReencryptionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(VisitorLegacyReencryptionConfiguration.class);

    @Test
    void isNotCreatedWithoutDedicatedProfileOrExplicitEnableFlag() {
        contextRunner
                .withPropertyValues("site-access.legacy-reencryption.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApplicationRunner.class);
                    assertThat(context).doesNotHaveBean(VisitorLegacyReencryptionProperties.class);
                });

        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("visitor-legacy-reencrypt"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApplicationRunner.class);
                    assertThat(context).doesNotHaveBean(VisitorLegacyReencryptionProperties.class);
                });
    }

    @Test
    void isCreatedOnlyWhenProfileAndEnableFlagAreBothPresent() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("visitor-legacy-reencrypt"))
                .withPropertyValues(
                        "site-access.legacy-reencryption.enabled=true",
                        "site-access.legacy-reencryption.mode=VERIFY")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(VisitorDataCryptoService.class, () -> mock(VisitorDataCryptoService.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ApplicationRunner.class);
                    assertThat(context).hasSingleBean(VisitorLegacyReencryptionProperties.class);
                    assertThat(context.getBean(VisitorLegacyReencryptionProperties.class).getMode())
                            .isEqualTo(VisitorLegacyReencryptionProperties.Mode.VERIFY);
                });
    }

    @Test
    void offlineGuardRequiresNoWebServerAndExplicitlyDisabledScheduling() {
        MockEnvironment environment = new MockEnvironment();
        assertThatThrownBy(() -> VisitorLegacyReencryptionConfiguration.requireOfflineExecution(environment))
                .hasMessageContaining("web-application-type=none");

        environment.setProperty("spring.main.web-application-type", "none");
        assertThatThrownBy(() -> VisitorLegacyReencryptionConfiguration.requireOfflineExecution(environment))
                .hasMessageContaining("scheduling.enabled=false");

        environment.setProperty("app.scheduling.enabled", "false");
        VisitorLegacyReencryptionConfiguration.requireOfflineExecution(environment);
    }
}
