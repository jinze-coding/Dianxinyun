package com.example.siteplatform.file.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FileCleanupSchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FileCleanupSchedulingConfiguration.class);

    @Test
    void enablesSchedulingByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(FileCleanupSchedulingConfiguration.class));
    }

    @Test
    void canDisableAllSchedulingForIsolatedCompatibilityRuns() {
        contextRunner
                .withPropertyValues("app.scheduling.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(FileCleanupSchedulingConfiguration.class));
    }
}
