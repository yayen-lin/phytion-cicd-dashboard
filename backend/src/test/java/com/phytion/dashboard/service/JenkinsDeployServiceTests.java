package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.ci.jenkins.JenkinsAdapter;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.config.AppsProperties;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.model.DeployJob;
import com.phytion.dashboard.model.ReleaseImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the deploy logic with a fake Jenkins, a fake release service and a fake audit log: no Spring.
 */
class JenkinsDeployServiceTests {

    private static final String APP = "sample-app";
    // the deploy job's name differs from the app id, to show which one goes where
    private static final String DEPLOY_JOB = "sample-app-deploy-job";
    private static final CodeRepo REPO = new CodeRepo("bitbucket", "andy-test-app", "sample-app", null);
    private static final CiPipeline PIPELINE = new CiPipeline("jenkins", "sample-app");
    private static final ReleaseImage IMAGE = new ReleaseImage("docker-registry", "sample-app");
    private static final AppsProperties APPS = new AppsProperties(Map.of(
            APP, new AppsProperties.AppSettings(REPO, PIPELINE, IMAGE, new DeployJob("jenkins", DEPLOY_JOB)),
            "undeployed-app", new AppsProperties.AppSettings(REPO, PIPELINE, IMAGE, null)));
    private static final Map<String, String> STAGING_1_0_1 = Map.of("VERSION", "1.0.1", "ENVIRONMENT", "staging");

    private JenkinsAdapter jenkins;
    private DockerRegistryReleaseService releases;
    private AuditService audit;
    private JenkinsDeployService deployService;

    @BeforeEach
    void setUp() {
        jenkins = mock(JenkinsAdapter.class);
        releases = mock(DockerRegistryReleaseService.class);
        audit = mock(AuditService.class);
        when(releases.hasVersion(APP, "1.0.1")).thenReturn(true);
        deployService = new JenkinsDeployService(jenkins, releases, audit, new AppService(APPS),
                new EnvironmentsProperties(List.of("dev", "staging", "prod")));
    }

    @Test
    void deployStartsTheDeployJobWithVersionAndEnvironmentAndRecordsIt() {
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenReturn(Optional.of("3"));

        assertThat(deployService.deploy(APP, "1.0.1", "staging", "andy")).contains("3");

        verify(audit).add("andy", AuditEntry.ActionType.DEPLOY, APP, AuditEntry.ActionResult.SUCCESS, "3",
                "1.0.1 to staging");
    }

    @Test
    void deployWhoseBuildIsStillQueuedIsRecordedWithoutBuildId() {
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenReturn(Optional.empty());

        assertThat(deployService.deploy(APP, "1.0.1", "staging", "andy")).isEmpty();

        verify(audit).add("andy", AuditEntry.ActionType.DEPLOY, APP, AuditEntry.ActionResult.SUCCESS, null,
                "1.0.1 to staging; queued, but the CI tool hadn't started it yet");
    }

    @Test
    void deployJobThatDoesNotStartIsRecordedWithTheReason() {
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenThrow(new CiToolException("Jenkins not reachable"));

        assertThatThrownBy(() -> deployService.deploy(APP, "1.0.1", "staging", "andy"))
                .isInstanceOf(CiToolException.class)
                .hasMessage("Jenkins not reachable");

        verify(audit).add("andy", AuditEntry.ActionType.DEPLOY, APP, AuditEntry.ActionResult.FAILED, null,
                "1.0.1 to staging: Jenkins not reachable");
    }

    @Test
    void registryThatCanNotBeReachedIsRecordedAndNoJobStarts() {
        when(releases.hasVersion(APP, "1.0.1")).thenThrow(new ReleaseToolException("Registry not reachable"));

        assertThatThrownBy(() -> deployService.deploy(APP, "1.0.1", "staging", "andy"))
                .isInstanceOf(ReleaseToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.DEPLOY, APP, AuditEntry.ActionResult.FAILED, null,
                "1.0.1 to staging: couldn't check the version: Registry not reachable");
        verifyNoInteractions(jenkins);
    }

    @Test
    void versionNotInTheRegistryIsRejectedBeforeAnythingHappens() {
        assertThatThrownBy(() -> deployService.deploy(APP, "9.9.9", "staging", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("sample-app has no released version 9.9.9");
        verifyNoInteractions(jenkins, audit);
    }

    @Test
    void unknownEnvironmentIsRejectedBeforeAnythingHappens() {
        for (String environment : new String[]{"test", "Prod", ""}) {
            assertThatThrownBy(() -> deployService.deploy(APP, "1.0.1", environment, "andy"))
                    .isInstanceOf(InvalidDeployException.class)
                    .hasMessage("Environments are dev, staging, prod, got: " + environment);
        }
        assertThatThrownBy(() -> deployService.deploy(APP, "1.0.1", null, "andy"))
                .isInstanceOf(InvalidDeployException.class);
        verifyNoInteractions(jenkins, releases, audit);
    }

    @Test
    void unknownMissingOrUndeployedAppIsRejected() {
        assertThatThrownBy(() -> deployService.deploy("other-app", "1.0.1", "staging", "andy"))
                .isInstanceOf(AppNotFoundException.class)
                .hasMessage("Unknown app: other-app");
        assertThatThrownBy(() -> deployService.deploy(null, "1.0.1", "staging", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("No app given");
        assertThatThrownBy(() -> deployService.deploy("undeployed-app", "1.0.1", "staging", "andy"))
                .isInstanceOf(AppNotFoundException.class)
                .hasMessage("App undeployed-app has no deploy configured");
        verifyNoInteractions(jenkins, releases, audit);
    }
}
