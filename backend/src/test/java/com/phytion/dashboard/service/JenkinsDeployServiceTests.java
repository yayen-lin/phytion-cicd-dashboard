package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.ci.jenkins.JenkinsAdapter;
import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.adapter.operate.kubernetes.KubernetesAdapter;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.config.AppsProperties;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.AuditFilter;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiParameterizedBuild;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.model.DeployJob;
import com.phytion.dashboard.model.DeployRun;
import com.phytion.dashboard.model.OperateWorkload;
import com.phytion.dashboard.model.Promotion;
import com.phytion.dashboard.model.Rollback;
import com.phytion.dashboard.model.RollbackOptions;
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
    private KubernetesAdapter kubernetes;
    private DockerRegistryReleaseService releases;
    private AuditService audit;
    private JenkinsDeployService deployService;

    @BeforeEach
    void setUp() {
        jenkins = mock(JenkinsAdapter.class);
        kubernetes = mock(KubernetesAdapter.class);
        releases = mock(DockerRegistryReleaseService.class);
        audit = mock(AuditService.class);
        when(releases.hasVersion(APP, "1.0.1")).thenReturn(true);
        deployService = new JenkinsDeployService(jenkins, kubernetes, releases, audit, new AppService(APPS),
                new EnvironmentsProperties(List.of("dev", "staging", "prod"), List.of("prod"), List.of("prod")));
    }

    @Test
    void listDeploysReadsTheDeployJobAndNamesTheDashboardUserOverTheCiToolsUser() {
        CiBuild build9 = deployBuild("9");
        CiBuild build8 = deployBuild("8");
        CiBuild build7 = deployBuild("7");
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                // started from the dashboard, which calls Jenkins as its own user
                new CiParameterizedBuild(build9, STAGING_1_0_1, "dashboard-bot"),
                // started straight in Jenkins
                new CiParameterizedBuild(build8, Map.of("VERSION", "1.0.0", "ENVIRONMENT", "dev"), "admin"),
                // a run without the parameters says nothing about a deploy
                new CiParameterizedBuild(build7, Map.of(), "admin")));
        when(audit.getRecentEntries(new AuditFilter(APP, AuditEntry.ActionType.DEPLOY, null,
                AuditEntry.ActionResult.SUCCESS, null, null, null))).thenReturn(List.of(
                new AuditEntry("andy", AuditEntry.ActionType.DEPLOY, APP, Instant.now(),
                        AuditEntry.ActionResult.SUCCESS, "9", "1.0.1 to staging", "staging", "1.0.1")));

        assertThat(deployService.listDeploys(APP)).containsExactly(
                new DeployRun(APP, "1.0.1", "staging", build9, "andy"),
                new DeployRun(APP, "1.0.0", "dev", build8, "admin"));
    }

    @Test
    void listDeploysNamesTheDashboardUserWhoPromoted() {
        CiBuild build10 = deployBuild("10");
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                new CiParameterizedBuild(build10, STAGING_1_0_1, "dashboard-bot")));
        when(audit.getRecentEntries(new AuditFilter(APP, AuditEntry.ActionType.PROMOTE, null,
                AuditEntry.ActionResult.SUCCESS, null, null, null))).thenReturn(List.of(
                new AuditEntry("sam", AuditEntry.ActionType.PROMOTE, APP, Instant.now(),
                        AuditEntry.ActionResult.SUCCESS, "10", "1.0.1 from dev to staging", "staging", "1.0.1")));

        assertThat(deployService.listDeploys(APP)).containsExactly(
                new DeployRun(APP, "1.0.1", "staging", build10, "sam"));
    }

    @Test
    void listDeploysOfAnAppThatIsNotDeployedIsRejected() {
        assertThatThrownBy(() -> deployService.listDeploys("undeployed-app"))
                .isInstanceOf(AppNotFoundException.class);
        verifyNoInteractions(jenkins);
    }

    private static CiBuild deployBuild(String id) {
        return new CiBuild(id, CiBuild.Status.SUCCESS, null, null, Instant.parse("2026-11-13T18:31:00Z"), 1000L,
                "http://localhost:8081/job/sample-app-deploy-job/" + id + "/", null);
    }

    @Test
    void getDeployBuildReadsTheBuildFromTheDeployJob() {
        CiBuild build = new CiBuild("3", CiBuild.Status.RUNNING, null, null, Instant.parse("2026-11-13T18:31:00Z"),
                null, "http://localhost:8081/job/sample-app-deploy-job/3/", null);
        when(jenkins.getBuild(DEPLOY_JOB, "3")).thenReturn(Optional.of(new CiBuildDetails(build, "admin", "jenkins", null)));

        assertThat(deployService.getDeployBuild(APP, "3")).contains(build);
    }

    @Test
    void getDeployBuildOfAnAppThatIsNotDeployedIsRejected() {
        assertThatThrownBy(() -> deployService.getDeployBuild("undeployed-app", "3"))
                .isInstanceOf(AppNotFoundException.class);
        verifyNoInteractions(jenkins);
    }

    @Test
    void deployStartsTheDeployJobWithVersionAndEnvironmentAndRecordsIt() {
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenReturn(Optional.of("3"));

        assertThat(deployService.deploy(APP, "1.0.1", "staging", "andy")).contains("3");

        verify(audit).add("andy", AuditEntry.ActionType.DEPLOY, APP, AuditEntry.ActionResult.SUCCESS, "3",
                "1.0.1 to staging", "staging", "1.0.1");
    }

    @Test
    void deployWhoseBuildIsStillQueuedIsRecordedWithoutBuildId() {
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenReturn(Optional.empty());

        assertThat(deployService.deploy(APP, "1.0.1", "staging", "andy")).isEmpty();

        verify(audit).add("andy", AuditEntry.ActionType.DEPLOY, APP, AuditEntry.ActionResult.SUCCESS, null,
                "1.0.1 to staging; queued, but the CI tool hadn't started it yet", "staging", "1.0.1");
    }

    @Test
    void deployJobThatDoesNotStartIsRecordedWithTheReason() {
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenThrow(new CiToolException("Jenkins not reachable"));

        assertThatThrownBy(() -> deployService.deploy(APP, "1.0.1", "staging", "andy"))
                .isInstanceOf(CiToolException.class)
                .hasMessage("Jenkins not reachable");

        verify(audit).add("andy", AuditEntry.ActionType.DEPLOY, APP, AuditEntry.ActionResult.FAILED, null,
                "1.0.1 to staging: Jenkins not reachable", "staging", "1.0.1");
    }

    @Test
    void registryThatCanNotBeReachedIsRecordedAndNoJobStarts() {
        when(releases.hasVersion(APP, "1.0.1")).thenThrow(new ReleaseToolException("Registry not reachable"));

        assertThatThrownBy(() -> deployService.deploy(APP, "1.0.1", "staging", "andy"))
                .isInstanceOf(ReleaseToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.DEPLOY, APP, AuditEntry.ActionResult.FAILED, null,
                "1.0.1 to staging: couldn't check the version: Registry not reachable", "staging", "1.0.1");
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

    @Test
    void promoteDeploysTheVersionRunningInTheSourceToTheNextEnvironmentAndRecordsIt() {
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(
                new OperateWorkload("other-app", "2.0.0", 1, 1),
                // not ready: whoever promotes decides
                new OperateWorkload(APP, "1.0.1", 1, 0)));
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenReturn(Optional.of("4"));

        assertThat(deployService.promote(APP, "dev", "andy"))
                .isEqualTo(new Promotion(APP, "1.0.1", "dev", "staging", "4"));

        verify(audit).add("andy", AuditEntry.ActionType.PROMOTE, APP, AuditEntry.ActionResult.SUCCESS, "4",
                "1.0.1 from dev to staging", "staging", "1.0.1");
    }

    @Test
    void promoteOverALowerVersionInTheNextEnvironmentDeploysIt() {
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 1, 1)));
        when(kubernetes.listWorkloads("staging")).thenReturn(List.of(new OperateWorkload(APP, "1.0.0", 1, 1)));
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenReturn(Optional.of("4"));

        assertThat(deployService.promote(APP, "dev", "andy"))
                .isEqualTo(new Promotion(APP, "1.0.1", "dev", "staging", "4"));
    }

    @Test
    void promoteWhenTheNextEnvironmentRunsTheSameOrAHigherVersionIsRejected() {
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 1, 1)));

        // same version
        when(kubernetes.listWorkloads("staging")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 1, 1)));
        assertThatThrownBy(() -> deployService.promote(APP, "dev", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("staging already runs sample-app 1.0.1");

        // higher version, compared by number, not as text
        when(kubernetes.listWorkloads("staging")).thenReturn(List.of(new OperateWorkload(APP, "1.0.10", 1, 1)));
        assertThatThrownBy(() -> deployService.promote(APP, "dev", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("staging runs sample-app 1.0.10, higher than 1.0.1 in dev");

        verifyNoInteractions(jenkins, releases, audit);
    }

    @Test
    void promoteOfAVersionNotInTheRegistryIsRejected() {
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(new OperateWorkload(APP, "9.9.9", 1, 1)));

        assertThatThrownBy(() -> deployService.promote(APP, "dev", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("sample-app has no released version 9.9.9");
        verifyNoInteractions(jenkins, audit);
    }

    @Test
    void promoteWhenTheRegistryCanNotBeReachedIsRecordedAndNoJobStarts() {
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 1, 1)));
        when(releases.hasVersion(APP, "1.0.1")).thenThrow(new ReleaseToolException("Registry not reachable"));

        assertThatThrownBy(() -> deployService.promote(APP, "dev", "andy"))
                .isInstanceOf(ReleaseToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.PROMOTE, APP, AuditEntry.ActionResult.FAILED, null,
                "1.0.1 from dev to staging: couldn't check the version: Registry not reachable", "staging", "1.0.1");
        verifyNoInteractions(jenkins);
    }

    @Test
    void promoteWhoseBuildIsStillQueuedIsRecordedWithoutBuildId() {
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 1, 1)));
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenReturn(Optional.empty());

        assertThat(deployService.promote(APP, "dev", "andy"))
                .isEqualTo(new Promotion(APP, "1.0.1", "dev", "staging", null));

        verify(audit).add("andy", AuditEntry.ActionType.PROMOTE, APP, AuditEntry.ActionResult.SUCCESS, null,
                "1.0.1 from dev to staging; queued, but the CI tool hadn't started it yet", "staging", "1.0.1");
    }

    @Test
    void promoteWhoseDeployJobDoesNotStartIsRecordedWithTheReason() {
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(new OperateWorkload(APP, "1.0.1", 1, 1)));
        when(jenkins.triggerBuild(DEPLOY_JOB, STAGING_1_0_1)).thenThrow(new CiToolException("Jenkins not reachable"));

        assertThatThrownBy(() -> deployService.promote(APP, "dev", "andy"))
                .isInstanceOf(CiToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.PROMOTE, APP, AuditEntry.ActionResult.FAILED, null,
                "1.0.1 from dev to staging: Jenkins not reachable", "staging", "1.0.1");
    }

    @Test
    void promoteWhenKubernetesCanNotBeReachedIsRecordedAndNoJobStarts() {
        when(kubernetes.listWorkloads("dev")).thenThrow(new OperateToolException("Kubernetes not reachable"));

        assertThatThrownBy(() -> deployService.promote(APP, "dev", "andy"))
                .isInstanceOf(OperateToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.PROMOTE, APP, AuditEntry.ActionResult.FAILED, null,
                "from dev to staging: couldn't read the running versions: Kubernetes not reachable", "staging", null);
        verifyNoInteractions(jenkins);
    }

    @Test
    void promoteWithNothingRunningInTheSourceIsRejected() {
        when(kubernetes.listWorkloads("dev")).thenReturn(List.of(
                new OperateWorkload("other-app", "2.0.0", 1, 1),
                // running, but its version can't be told
                new OperateWorkload(APP, null, 1, 1)));

        assertThatThrownBy(() -> deployService.promote(APP, "dev", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("sample-app has no version running in dev");
        verifyNoInteractions(jenkins, audit);
    }

    @Test
    void promoteFromTheLastOrAnUnknownEnvironmentIsRejectedBeforeAnythingHappens() {
        assertThatThrownBy(() -> deployService.promote(APP, "prod", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("prod is the last environment");
        assertThatThrownBy(() -> deployService.promote(APP, "test", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("Environments are dev, staging, prod, got: test");
        assertThatThrownBy(() -> deployService.promote(APP, null, "andy"))
                .isInstanceOf(InvalidDeployException.class);
        verifyNoInteractions(jenkins, kubernetes, audit);
    }

    @Test
    void rollbackOptionsAreTheSuccessfulVersionsBelowTheNewestHighestFirstSkippingFailedDeploysAndOtherEnvironments() {
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                deployRun("6", "1.0.2", "staging", CiBuild.Status.SUCCESS),
                deployRun("5", "1.0.1", "staging", CiBuild.Status.FAILED),
                deployRun("4", "1.0.1", "dev", CiBuild.Status.SUCCESS),
                deployRun("3", "1.0.0", "staging", CiBuild.Status.SUCCESS),
                deployRun("2", "0.0.9", "staging", CiBuild.Status.SUCCESS)));

        assertThat(deployService.listRollbackOptions(APP, "staging"))
                .isEqualTo(new RollbackOptions(APP, "staging", "1.0.2", List.of("1.0.0", "0.0.9")));
    }

    @Test
    void rollbackOptionsAfterARollbackStartFromTheVersionItWentTo() {
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                // the rollback: a deploy of 1.0.0
                deployRun("7", "1.0.0", "staging", CiBuild.Status.SUCCESS),
                deployRun("6", "1.0.2", "staging", CiBuild.Status.SUCCESS),
                deployRun("3", "1.0.0", "staging", CiBuild.Status.SUCCESS),
                deployRun("2", "0.0.9", "staging", CiBuild.Status.SUCCESS)));

        assertThat(deployService.listRollbackOptions(APP, "staging"))
                .isEqualTo(new RollbackOptions(APP, "staging", "1.0.0", List.of("0.0.9")));
    }

    @Test
    void rollbackOptionsAreSortedByNumberWithoutRepeats() {
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                deployRun("4", "1.0.10", "staging", CiBuild.Status.SUCCESS),
                deployRun("3", "1.0.2", "staging", CiBuild.Status.SUCCESS),
                deployRun("2", "1.0.9", "staging", CiBuild.Status.SUCCESS),
                deployRun("1", "1.0.2", "staging", CiBuild.Status.SUCCESS)));

        assertThat(deployService.listRollbackOptions(APP, "staging"))
                .isEqualTo(new RollbackOptions(APP, "staging", "1.0.10", List.of("1.0.9", "1.0.2")));
    }

    @Test
    void rollbackOptionsAreEmptyWithNothingLowerOrNothingDeployed() {
        // only the same or higher versions before the current one
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                deployRun("3", "1.0.0", "staging", CiBuild.Status.SUCCESS),
                deployRun("2", "1.0.2", "staging", CiBuild.Status.SUCCESS),
                deployRun("1", "1.0.0", "staging", CiBuild.Status.SUCCESS)));
        assertThat(deployService.listRollbackOptions(APP, "staging"))
                .isEqualTo(new RollbackOptions(APP, "staging", "1.0.0", List.of()));

        // no successful deploy there
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                deployRun("2", "1.0.1", "staging", CiBuild.Status.FAILED),
                deployRun("1", "1.0.0", "dev", CiBuild.Status.SUCCESS)));
        assertThat(deployService.listRollbackOptions(APP, "staging"))
                .isEqualTo(new RollbackOptions(APP, "staging", null, List.of()));
    }

    @Test
    void rollbackOptionsOfAnUnknownEnvironmentOrAppAreRejected() {
        assertThatThrownBy(() -> deployService.listRollbackOptions(APP, "test"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("Environments are dev, staging, prod, got: test");
        assertThatThrownBy(() -> deployService.listRollbackOptions(null, "staging"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("No app given");
        assertThatThrownBy(() -> deployService.listRollbackOptions("other-app", "staging"))
                .isInstanceOf(AppNotFoundException.class);
        assertThatThrownBy(() -> deployService.listRollbackOptions("undeployed-app", "staging"))
                .isInstanceOf(AppNotFoundException.class);
        verifyNoInteractions(jenkins);
    }

    @Test
    void listDeploysNamesTheDashboardUserWhoRolledBack() {
        CiBuild build11 = deployBuild("11");
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                new CiParameterizedBuild(build11, STAGING_1_0_1, "dashboard-bot")));
        when(audit.getRecentEntries(new AuditFilter(APP, AuditEntry.ActionType.ROLLBACK, null,
                AuditEntry.ActionResult.SUCCESS, null, null, null))).thenReturn(List.of(
                new AuditEntry("sam", AuditEntry.ActionType.ROLLBACK, APP, Instant.now(),
                        AuditEntry.ActionResult.SUCCESS, "11", "1.0.2 to 1.0.1 in staging", "staging", "1.0.1")));

        assertThat(deployService.listDeploys(APP)).containsExactly(
                new DeployRun(APP, "1.0.1", "staging", build11, "sam"));
    }

    @Test
    void rollbackDeploysThePickedVersionAndRecordsIt() {
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                deployRun("6", "1.0.2", "staging", CiBuild.Status.SUCCESS),
                deployRun("5", "1.0.1", "staging", CiBuild.Status.FAILED),
                deployRun("3", "1.0.0", "staging", CiBuild.Status.SUCCESS),
                deployRun("2", "0.0.9", "staging", CiBuild.Status.SUCCESS)));
        when(jenkins.triggerBuild(DEPLOY_JOB, Map.of("VERSION", "0.0.9", "ENVIRONMENT", "staging")))
                .thenReturn(Optional.of("7"));

        // not only the previous good version: any of the options
        assertThat(deployService.rollback(APP, "staging", "0.0.9", "andy"))
                .isEqualTo(new Rollback(APP, "staging", "1.0.2", "0.0.9", "7"));

        verify(audit).add("andy", AuditEntry.ActionType.ROLLBACK, APP, AuditEntry.ActionResult.SUCCESS, "7",
                "1.0.2 to 0.0.9 in staging", "staging", "0.0.9");
    }

    @Test
    void rollbackWhoseBuildIsStillQueuedIsRecordedWithoutBuildId() {
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                deployRun("6", "1.0.2", "staging", CiBuild.Status.SUCCESS),
                deployRun("3", "1.0.0", "staging", CiBuild.Status.SUCCESS)));
        when(jenkins.triggerBuild(DEPLOY_JOB, Map.of("VERSION", "1.0.0", "ENVIRONMENT", "staging")))
                .thenReturn(Optional.empty());

        assertThat(deployService.rollback(APP, "staging", "1.0.0", "andy"))
                .isEqualTo(new Rollback(APP, "staging", "1.0.2", "1.0.0", null));

        verify(audit).add("andy", AuditEntry.ActionType.ROLLBACK, APP, AuditEntry.ActionResult.SUCCESS, null,
                "1.0.2 to 1.0.0 in staging; queued, but the CI tool hadn't started it yet", "staging", "1.0.0");
    }

    @Test
    void rollbackWhoseDeployJobDoesNotStartIsRecordedWithTheReason() {
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                deployRun("6", "1.0.2", "staging", CiBuild.Status.SUCCESS),
                deployRun("3", "1.0.0", "staging", CiBuild.Status.SUCCESS)));
        when(jenkins.triggerBuild(DEPLOY_JOB, Map.of("VERSION", "1.0.0", "ENVIRONMENT", "staging")))
                .thenThrow(new CiToolException("Jenkins not reachable"));

        assertThatThrownBy(() -> deployService.rollback(APP, "staging", "1.0.0", "andy"))
                .isInstanceOf(CiToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.ROLLBACK, APP, AuditEntry.ActionResult.FAILED, null,
                "1.0.2 to 1.0.0 in staging: Jenkins not reachable", "staging", "1.0.0");
    }

    @Test
    void rollbackWhenTheDeployHistoryCanNotBeReadIsRecordedAndNoJobStarts() {
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenThrow(new CiToolException("Jenkins not reachable"));

        assertThatThrownBy(() -> deployService.rollback(APP, "staging", "1.0.0", "andy"))
                .isInstanceOf(CiToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.ROLLBACK, APP, AuditEntry.ActionResult.FAILED, null,
                "to 1.0.0 in staging: couldn't read the deploy history: Jenkins not reachable", "staging", "1.0.0");
        verify(jenkins, never()).triggerBuild(any(), any());
    }

    @Test
    void rollbackWithNothingOlderIsRejected() {
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                deployRun("3", "1.0.0", "staging", CiBuild.Status.SUCCESS)));

        assertThatThrownBy(() -> deployService.rollback(APP, "staging", "0.0.9", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("sample-app has no older version to roll back to in staging");
        verify(jenkins, never()).triggerBuild(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void rollbackToAVersionThatIsNotAnOptionIsRejected() {
        when(jenkins.listParameterizedBuilds(DEPLOY_JOB)).thenReturn(List.of(
                deployRun("6", "1.0.2", "staging", CiBuild.Status.SUCCESS),
                deployRun("5", "1.0.1", "staging", CiBuild.Status.FAILED),
                deployRun("4", "1.0.1", "dev", CiBuild.Status.SUCCESS),
                deployRun("3", "1.0.0", "staging", CiBuild.Status.SUCCESS)));

        // failed there, only deployed elsewhere, the current one, or higher
        for (String version : new String[]{"1.0.1", "1.0.2", "1.0.3"}) {
            assertThatThrownBy(() -> deployService.rollback(APP, "staging", version, "andy"))
                    .isInstanceOf(InvalidDeployException.class)
                    .hasMessage("sample-app can roll back in staging to 1.0.0, got: " + version);
        }
        verify(jenkins, never()).triggerBuild(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void rollbackOfAnUnknownEnvironmentOrAppIsRejectedBeforeAnythingHappens() {
        assertThatThrownBy(() -> deployService.rollback(APP, "test", "1.0.0", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("Environments are dev, staging, prod, got: test");
        assertThatThrownBy(() -> deployService.rollback(null, "staging", "1.0.0", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("No app given");
        assertThatThrownBy(() -> deployService.rollback("undeployed-app", "staging", "1.0.0", "andy"))
                .isInstanceOf(AppNotFoundException.class);
        verifyNoInteractions(jenkins, audit);
    }

    private static CiParameterizedBuild deployRun(String id, String version, String environment,
                                                  CiBuild.Status status) {
        CiBuild build = new CiBuild(id, status, null, null, Instant.parse("2026-11-13T18:31:00Z"), 1000L,
                "http://localhost:8081/job/sample-app-deploy-job/" + id + "/", null);
        return new CiParameterizedBuild(build, Map.of("VERSION", version, "ENVIRONMENT", environment), "admin");
    }

    @Test
    void promoteOfAnUnknownMissingOrUndeployedAppIsRejected() {
        assertThatThrownBy(() -> deployService.promote("other-app", "dev", "andy"))
                .isInstanceOf(AppNotFoundException.class);
        assertThatThrownBy(() -> deployService.promote(null, "dev", "andy"))
                .isInstanceOf(InvalidDeployException.class)
                .hasMessage("No app given");
        assertThatThrownBy(() -> deployService.promote("undeployed-app", "dev", "andy"))
                .isInstanceOf(AppNotFoundException.class);
        verifyNoInteractions(jenkins, kubernetes, audit);
    }
}
