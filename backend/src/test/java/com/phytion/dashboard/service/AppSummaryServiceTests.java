package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.config.AppsProperties;
import com.phytion.dashboard.model.AppSummary;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.model.DeployJob;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.model.EnvironmentStatus;
import com.phytion.dashboard.model.ReleaseImage;
import com.phytion.dashboard.model.ReleaseVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests combining the summary with fake CI, release and environment services: no Spring.
 */
class AppSummaryServiceTests {

    private static final String APP = "sample-app";
    private static final String PLAIN_APP = "plain-app";
    private static final CodeRepo REPO = new CodeRepo("bitbucket", "andy-test-app", "sample-app", null);
    private static final CiPipeline PIPELINE = new CiPipeline("jenkins", "sample-app");
    private static final AppsProperties APPS = new AppsProperties(Map.of(
            APP, new AppsProperties.AppSettings(REPO, PIPELINE, new ReleaseImage("docker-registry", APP),
                    new DeployJob("jenkins", "sample-app-deploy")),
            // built only: no release, no deploy
            PLAIN_APP, new AppsProperties.AppSettings(REPO, PIPELINE, null, null)));
    private static final CiBuild BUILD_12 = new CiBuild("12", CiBuild.Status.SUCCESS, null, "main",
            Instant.parse("2026-11-13T18:00:00Z"), 42_000L, "http://localhost:8081/job/sample-app/12/", null);
    private static final CiBuild BUILD_11 = new CiBuild("11", CiBuild.Status.FAILED, null, "main",
            Instant.parse("2026-11-13T17:00:00Z"), 40_000L, "http://localhost:8081/job/sample-app/11/", null);
    private static final ReleaseVersion VERSION_1_0_1 = new ReleaseVersion("1.0.1", "12", null, null, null);
    private static final EnvironmentApp DEV_1_0_1 = new EnvironmentApp(APP, "1.0.1", null, null, 1, 1, true,
            null, null, null);

    private JenkinsCiService ci;
    private DockerRegistryReleaseService releases;
    private KubernetesEnvironmentService environments;
    private AppSummaryService summaryService;

    @BeforeEach
    void setUp() {
        ci = mock(JenkinsCiService.class);
        releases = mock(DockerRegistryReleaseService.class);
        environments = mock(KubernetesEnvironmentService.class);
        summaryService = new AppSummaryService(new AppService(APPS), ci, releases, environments);

        // every tool answers
        when(ci.listBuilds(APP)).thenReturn(List.of(BUILD_12, BUILD_11));
        when(ci.listBuilds(PLAIN_APP)).thenReturn(List.of());
        when(releases.findLatestVersion(APP)).thenReturn(Optional.of(VERSION_1_0_1));
        when(environments.listEnvironments(null, null)).thenReturn(List.of(
                new EnvironmentStatus("dev", List.of(DEV_1_0_1), List.of()),
                new EnvironmentStatus("staging", List.of(EnvironmentApp.notDeployed(APP)), List.of())));
    }

    @Test
    void summaryHasTheNewestBuildTheLatestVersionAndEachEnvironment() {
        AppSummary summary = summaryOf(APP);

        assertThat(summary.latestBuild()).isEqualTo(BUILD_12);
        assertThat(summary.latestVersion()).isEqualTo(VERSION_1_0_1);
        assertThat(summary.deployments()).containsExactly(
                new AppSummary.Deployment("dev", DEV_1_0_1),
                new AppSummary.Deployment("staging", EnvironmentApp.notDeployed(APP)));
        assertThat(summary.unavailable()).isEmpty();
    }

    @Test
    void appWithoutReleaseOrDeployHasNoVersionAndNoEnvironmentsButNothingUnavailable() {
        AppSummary summary = summaryOf(PLAIN_APP);

        assertThat(summary.latestBuild()).isNull();
        assertThat(summary.latestVersion()).isNull();
        assertThat(summary.deployments()).isEmpty();
        assertThat(summary.unavailable()).isEmpty();
    }

    @Test
    void jenkinsDownLeavesTheBuildOutAndKeepsTheRest() {
        when(ci.listBuilds(APP)).thenThrow(new CiToolException("Jenkins not reachable"));

        AppSummary summary = summaryOf(APP);

        assertThat(summary.latestBuild()).isNull();
        assertThat(summary.latestVersion()).isEqualTo(VERSION_1_0_1);
        assertThat(summary.unavailable()).containsExactly(AppSummary.Part.BUILD);
    }

    @Test
    void registryDownLeavesTheVersionOutAndKeepsTheRest() {
        when(releases.findLatestVersion(APP)).thenThrow(new ReleaseToolException("Registry not reachable"));

        AppSummary summary = summaryOf(APP);

        assertThat(summary.latestVersion()).isNull();
        assertThat(summary.latestBuild()).isEqualTo(BUILD_12);
        assertThat(summary.unavailable()).containsExactly(AppSummary.Part.VERSION);
    }

    @Test
    void kubernetesDownLeavesTheEnvironmentsOutOfDeployedAppsOnly() {
        when(environments.listEnvironments(null, null)).thenThrow(new OperateToolException("Kubernetes not reachable"));

        assertThat(summaryOf(APP).deployments()).isEmpty();
        assertThat(summaryOf(APP).unavailable()).containsExactly(AppSummary.Part.ENVIRONMENTS);
        assertThat(summaryOf(PLAIN_APP).unavailable()).isEmpty();
    }

    @Test
    void appThatIsNotReleasedNeverAsksTheRegistry() {
        summaryOf(PLAIN_APP);

        verify(releases, never()).findLatestVersion(PLAIN_APP);
    }

    @Test
    void buildsAndVersionsAreReadAtTheSameTime() {
        // each call waits for the other to start: read one after the other, the first would time out
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch versionStarted = new CountDownLatch(1);
        when(ci.listBuilds(APP)).thenAnswer(call -> {
            buildStarted.countDown();
            if (!versionStarted.await(5, TimeUnit.SECONDS)) {
                throw new CiToolException("the version wasn't read at the same time");
            }
            return List.of(BUILD_12);
        });
        when(releases.findLatestVersion(APP)).thenAnswer(call -> {
            versionStarted.countDown();
            if (!buildStarted.await(5, TimeUnit.SECONDS)) {
                throw new ReleaseToolException("the build wasn't read at the same time");
            }
            return Optional.of(VERSION_1_0_1);
        });

        AppSummary summary = summaryOf(APP);

        assertThat(summary.unavailable()).isEmpty();
        assertThat(summary.latestBuild()).isEqualTo(BUILD_12);
        assertThat(summary.latestVersion()).isEqualTo(VERSION_1_0_1);
    }

    @Test
    void unexpectedErrorInACallIsThrownAsItself() {
        when(ci.listBuilds(APP)).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> summaryService.listAppSummaries())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bug");
    }

    private AppSummary summaryOf(String appId) {
        return summaryService.listAppSummaries().stream()
                .filter(summary -> summary.app().id().equals(appId))
                .findFirst()
                .orElseThrow();
    }
}
