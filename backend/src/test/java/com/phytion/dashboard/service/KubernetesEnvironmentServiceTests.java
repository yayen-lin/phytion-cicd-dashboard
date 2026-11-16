package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.adapter.operate.kubernetes.KubernetesAdapter;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.config.AppsProperties;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.model.DeployJob;
import com.phytion.dashboard.model.DeployRun;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.model.EnvironmentStatus;
import com.phytion.dashboard.model.OperateWorkload;
import com.phytion.dashboard.model.ReleaseImage;
import com.phytion.dashboard.model.ReleaseVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the environment logic with a fake Kubernetes, a fake release service and a fake deploy service: no Spring.
 */
class KubernetesEnvironmentServiceTests {

    private static final String APP = "sample-app";
    private static final String OTHER_APP = "dummy-app";
    private static final String COMMIT = "a4dad009a1f3ef48f7239a3dd155c64ca51d466f";
    private static final String COMMIT_URL = "https://bitbucket.org/andy-test-app/sample-app/commits/" + COMMIT;
    private static final Instant DEPLOYED_AT = Instant.parse("2026-11-13T18:31:00Z");
    private static final CodeRepo REPO = new CodeRepo("bitbucket", "andy-test-app", "sample-app", null);
    private static final CiPipeline PIPELINE = new CiPipeline("jenkins", "sample-app");
    private static final ReleaseImage IMAGE = new ReleaseImage("docker-registry", "sample-app");
    private static final DeployJob DEPLOY_JOB = new DeployJob("jenkins", "sample-app-deploy");

    private KubernetesAdapter kubernetes;
    private DockerRegistryReleaseService releases;
    private JenkinsDeployService deployService;
    private KubernetesEnvironmentService environmentService;

    @BeforeEach
    void setUp() {
        kubernetes = mock(KubernetesAdapter.class);
        releases = mock(DockerRegistryReleaseService.class);
        deployService = mock(JenkinsDeployService.class);

        // two deployable apps and one that isn't deployed anywhere, in this order
        LinkedHashMap<String, AppsProperties.AppSettings> apps = new LinkedHashMap<>();
        apps.put(APP, new AppsProperties.AppSettings(REPO, PIPELINE, IMAGE, DEPLOY_JOB));
        apps.put(OTHER_APP, new AppsProperties.AppSettings(REPO, PIPELINE, IMAGE, DEPLOY_JOB));
        apps.put("undeployed-app", new AppsProperties.AppSettings(REPO, PIPELINE, IMAGE, null));
        environmentService = new KubernetesEnvironmentService(kubernetes, releases, deployService,
                new AppService(new AppsProperties(apps)), new EnvironmentsProperties(List.of("dev", "staging", "prod"), List.of("prod")));

        // sample-app 1.0.1 runs in dev and staging; nothing in prod
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 1, 1)));
        when(kubernetes.listWorkloads("staging")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 2, 1)));
        when(kubernetes.listWorkloads("prod")).thenReturn(List.of());
        when(releases.findVersion(APP, "1.0.1"))
                .thenReturn(Optional.of(new ReleaseVersion("1.0.1", "12", COMMIT, COMMIT_URL, DEPLOYED_AT)));
        when(deployService.listDeploys(any())).thenReturn(List.of());
    }

    @Test
    void environmentsAreReadAtTheSameTime() {
        // dev waits for prod to start: read one after the other, dev would time out
        CountDownLatch prodStarted = new CountDownLatch(1);
        when(kubernetes.listWorkloads("dev")).thenAnswer(call -> {
            if (!prodStarted.await(5, TimeUnit.SECONDS)) {
                throw new OperateToolException("prod wasn't read at the same time");
            }
            return List.of(new OperateWorkload(APP, "1.0.1", 1, 1));
        });
        when(kubernetes.listWorkloads("prod")).thenAnswer(call -> {
            prodStarted.countDown();
            return List.of();
        });

        assertThat(environmentService.listEnvironments(null, null)).extracting(EnvironmentStatus::environment)
                .containsExactly("dev", "staging", "prod");
    }

    @Test
    void everyEnvironmentListsEveryDeployableAppInConfigOrder() {
        List<EnvironmentStatus> environments = environmentService.listEnvironments(null, null);

        assertThat(environments).extracting(EnvironmentStatus::environment).containsExactly("dev", "staging", "prod");
        assertThat(environments.getFirst().apps()).extracting(EnvironmentApp::appId).containsExactly(APP, OTHER_APP);
        assertThat(environments.get(2).apps())
                .containsExactly(EnvironmentApp.notDeployed(APP), EnvironmentApp.notDeployed(OTHER_APP));
    }

    @Test
    void deployedAppHasVersionCommitPodsAndItsNewestDeployBuildHere() {
        // newest first: a deploy of another version, one to another environment, then the one that did it
        when(deployService.listDeploys(APP)).thenReturn(List.of(
                deployRun("5", "1.0.2", "dev", "admin"),
                deployRun("4", "1.0.1", "staging", "andy"),
                deployRun("3", "1.0.1", "dev", "andy")));

        EnvironmentApp app = environmentService.listEnvironments("dev", APP).getFirst().apps().getFirst();

        assertThat(app).isEqualTo(new EnvironmentApp(APP, "1.0.1", COMMIT, COMMIT_URL, 1, 1, true, DEPLOYED_AT,
                "andy", "3"));
    }

    @Test
    void eachAppsDeploysAreReadOnceForAllEnvironments() {
        environmentService.listEnvironments(null, APP);

        verify(deployService, times(1)).listDeploys(APP);
    }

    @Test
    void runningDeployBuildsAreListedUnderTheirEnvironment() {
        DeployRun running = new DeployRun(APP, "1.0.2", "prod", new CiBuild("6", CiBuild.Status.RUNNING, null, null,
                DEPLOYED_AT, null, "http://localhost:8081/job/sample-app-deploy/6/", null), "andy");
        when(deployService.listDeploys(APP)).thenReturn(List.of(running, deployRun("3", "1.0.1", "dev", "andy")));

        List<EnvironmentStatus> environments = environmentService.listEnvironments(null, null);

        assertThat(environments.get(2).runningDeploys()).containsExactly(running);
        assertThat(environments.getFirst().runningDeploys()).isEmpty();
    }

    @Test
    void ciToolThatCanNotBeReachedLeavesOnlyWhoDeployedOut() {
        when(deployService.listDeploys(APP)).thenThrow(new CiToolException("Jenkins not reachable"));

        EnvironmentApp app = environmentService.listEnvironments("dev", APP).getFirst().apps().getFirst();

        assertThat(app.version()).isEqualTo("1.0.1");
        assertThat(app.deployedBy()).isNull();
        assertThat(app.deployedAt()).isNull();
    }

    private static DeployRun deployRun(String buildId, String version, String environment, String deployedBy) {
        return new DeployRun(APP, version, environment, new CiBuild(buildId, CiBuild.Status.SUCCESS, null, null,
                DEPLOYED_AT, 1000L, "http://localhost:8081/job/sample-app-deploy/" + buildId + "/", null), deployedBy);
    }

    @Test
    void appWithPodsNotReadyIsUnhealthyAndOneWithoutADeployBuildHasNoDeployer() {
        EnvironmentApp app = environmentService.listEnvironments("staging", APP).getFirst().apps().getFirst();

        assertThat(app.healthy()).isFalse();
        assertThat(app.readyPods()).isEqualTo(1);
        assertThat(app.desiredPods()).isEqualTo(2);
        assertThat(app.deployedBy()).isNull();
    }

    @Test
    void appScaledToZeroIsUnhealthy() {
        when(kubernetes.listWorkloads("prod")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 0, 0)));

        assertThat(environmentService.listEnvironments("prod", APP).getFirst().apps().getFirst().healthy()).isFalse();
    }

    @Test
    void versionRunningInTwoEnvironmentsIsReadFromTheRegistryOnce() {
        environmentService.listEnvironments(null, APP);

        verify(releases, times(1)).findVersion(APP, "1.0.1");
    }

    @Test
    void registryThatCanNotBeReachedLeavesOnlyTheCommitOut() {
        when(releases.findVersion(APP, "1.0.1")).thenThrow(new ReleaseToolException("Registry not reachable"));

        EnvironmentApp app = environmentService.listEnvironments("dev", APP).getFirst().apps().getFirst();

        assertThat(app.version()).isEqualTo("1.0.1");
        assertThat(app.healthy()).isTrue();
        assertThat(app.commit()).isNull();
    }

    @Test
    void kubernetesThatCanNotBeReachedFails() {
        when(kubernetes.listWorkloads("dev")).thenThrow(new OperateToolException("Kubernetes not reachable"));

        assertThatThrownBy(() -> environmentService.listEnvironments("dev", null))
                .isInstanceOf(OperateToolException.class);
    }

    @Test
    void unknownEnvironmentOrAppIsRejectedBeforeAskingKubernetes() {
        assertThatThrownBy(() -> environmentService.listEnvironments("test", null))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("Environments are dev, staging, prod, got: test");
        assertThatThrownBy(() -> environmentService.listEnvironments(null, "other-app"))
                .isInstanceOf(AppNotFoundException.class)
                .hasMessage("Unknown app: other-app");
        assertThatThrownBy(() -> environmentService.listEnvironments(null, "undeployed-app"))
                .isInstanceOf(AppNotFoundException.class)
                .hasMessage("App undeployed-app has no deploy configured");
        verifyNoInteractions(kubernetes);
    }
}
