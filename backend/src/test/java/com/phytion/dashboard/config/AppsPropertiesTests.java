package com.phytion.dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.phytion.dashboard.model.App;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Tests how dashboard.apps is read and checked at startup, with only the settings classes loaded.
 */
class AppsPropertiesTests {

    private static final String[] SAMPLE_APP = {
            "dashboard.apps.sample-app.repo.tool=bitbucket",
            "dashboard.apps.sample-app.repo.workspace=andy-test-app",
            "dashboard.apps.sample-app.repo.name=sample-app",
            "dashboard.apps.sample-app.pipeline.tool=jenkins",
            "dashboard.apps.sample-app.pipeline.job=sample-app",
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void appWithoutReleaseIsReadWithNullRelease() {
        runner.withPropertyValues(SAMPLE_APP).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AppsProperties.class).list())
                    .extracting(App::id, App::release)
                    .containsExactly(tuple("sample-app", null));
        });
    }

    @Test
    void appWithoutPipelineStopsStartup() {
        runner.withPropertyValues(
                "dashboard.apps.sample-app.repo.tool=bitbucket",
                "dashboard.apps.sample-app.repo.workspace=andy-test-app",
                "dashboard.apps.sample-app.repo.name=sample-app"
        ).run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("apps[sample-app].pipeline"));
    }

    @Test
    void releaseWithoutImageStopsStartup() {
        runner.withPropertyValues(SAMPLE_APP)
                .withPropertyValues("dashboard.apps.sample-app.release.tool=docker-registry")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("release.image"));
    }

    @Configuration
    @EnableConfigurationProperties(AppsProperties.class)
    static class Config {
    }
}
