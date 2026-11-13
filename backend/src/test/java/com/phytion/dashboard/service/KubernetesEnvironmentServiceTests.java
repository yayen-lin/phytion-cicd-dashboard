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

import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.adapter.operate.kubernetes.KubernetesAdapter;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.config.AppsProperties;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.AuditFilter;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.model.DeployJob;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.model.EnvironmentStatus;
import com.phytion.dashboard.model.OperateWorkload;
import com.phytion.dashboard.model.ReleaseImage;
import com.phytion.dashboard.model.ReleaseVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the environment logic with a fake Kubernetes, a fake release service and a fake audit log: no Spring.
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
    private AuditService audit;
    private KubernetesEnvironmentService environmentService;

    @BeforeEach
    void setUp() {
        kubernetes = mock(KubernetesAdapter.class);
        releases = mock(DockerRegistryReleaseService.class);
        audit = mock(AuditService.class);

        // two deployable apps and one that isn't deployed anywhere, in this order
        LinkedHashMap<String, AppsProperties.AppSettings> apps = new LinkedHashMap<>();
        apps.put(APP, new AppsProperties.AppSettings(REPO, PIPELINE, IMAGE, DEPLOY_JOB));
        apps.put(OTHER_APP, new AppsProperties.AppSettings(REPO, PIPELINE, IMAGE, DEPLOY_JOB));
        apps.put("undeployed-app", new AppsProperties.AppSettings(REPO, PIPELINE, IMAGE, null));
        environmentService = new KubernetesEnvironmentService(kubernetes, releases, audit,
                new AppService(new AppsProperties(apps)), new EnvironmentsProperties(List.of("dev", "staging", "prod")));

        // sample-app 1.0.1 runs in dev and staging; nothing in prod
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 1, 1)));
        when(kubernetes.listWorkloads("staging")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 2, 1)));
        when(kubernetes.listWorkloads("prod")).thenReturn(List.of());
        when(releases.findVersion(APP, "1.0.1"))
                .thenReturn(Optional.of(new ReleaseVersion("1.0.1", "12", COMMIT, COMMIT_URL, DEPLOYED_AT)));
        when(audit.getRecentEntries(any())).thenReturn(List.of());
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
    void deployedAppHasVersionCommitPodsAndWhoDeployedIt() {
        AuditEntry deploy = new AuditEntry("andy", AuditEntry.ActionType.DEPLOY, APP, DEPLOYED_AT,
                AuditEntry.ActionResult.SUCCESS, "3", "1.0.1 to dev", "dev", "1.0.1");
        when(audit.getRecentEntries(new AuditFilter(APP, AuditEntry.ActionType.DEPLOY, null, AuditEntry.ActionResult.SUCCESS,
                null, "dev", "1.0.1"))).thenReturn(List.of(deploy));

        EnvironmentApp app = environmentService.listEnvironments("dev", APP).getFirst().apps().getFirst();

        assertThat(app).isEqualTo(new EnvironmentApp(APP, "1.0.1", COMMIT, COMMIT_URL, 1, 1, true, DEPLOYED_AT,
                "andy", "3"));
    }

    @Test
    void appWithPodsNotReadyIsUnhealthyAndOneNotDeployedByTheDashboardHasNoDeployer() {
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
