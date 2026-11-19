package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.config.AppsProperties;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.model.DeployJob;
import com.phytion.dashboard.model.DeployRun;
import com.phytion.dashboard.model.History;
import com.phytion.dashboard.model.ReleaseImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests attaching builds to the audit log with fake audit, CI and deploy services: no Spring.
 */
class HistoryServiceTests {

    private static final String APP = "sample-app";
    private static final String PLAIN_APP = "plain-app";
    private static final CodeRepo REPO = new CodeRepo("bitbucket", "andy-test-app", "sample-app", null);
    private static final CiPipeline PIPELINE = new CiPipeline("jenkins", "sample-app");
    private static final Instant AT = Instant.parse("2026-11-14T10:00:00Z");
    // the same number in the pipeline and the deploy job, to show which one each entry gets
    private static final CiBuild PIPELINE_BUILD_3 = build("3", CiBuild.Status.FAILED, "sample-app");
    private static final CiBuild DEPLOY_BUILD_3 = build("3", CiBuild.Status.SUCCESS, "sample-app-deploy");
    private static final AuditEntry BUILD_3 = entry(AuditEntry.ActionType.BUILD, APP, "3");
    private static final AuditEntry DEPLOY_3 = entry(AuditEntry.ActionType.DEPLOY, APP, "3");

    private AuditService audit;
    private JenkinsCiService ci;
    private JenkinsDeployService deployService;
    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        audit = mock(AuditService.class);
        ci = mock(JenkinsCiService.class);
        deployService = mock(JenkinsDeployService.class);

        // a deployed app and one only built
        LinkedHashMap<String, AppsProperties.AppSettings> apps = new LinkedHashMap<>();
        apps.put(APP, new AppsProperties.AppSettings(REPO, PIPELINE, new ReleaseImage("docker-registry", APP),
                new DeployJob("jenkins", "sample-app-deploy")));
        apps.put(PLAIN_APP, new AppsProperties.AppSettings(REPO, PIPELINE, null, null));
        historyService = new HistoryService(audit, new AppService(new AppsProperties(apps)), ci, deployService);

        when(audit.getRecentEntries(any())).thenReturn(List.of(DEPLOY_3, BUILD_3));
        when(ci.listBuilds(APP)).thenReturn(List.of(PIPELINE_BUILD_3));
        when(deployService.listDeploys(APP)).thenReturn(List.of(
                new DeployRun(APP, "1.0.1", "prod", DEPLOY_BUILD_3, "andy", AuditEntry.ActionType.DEPLOY)));
    }

    private static CiBuild build(String id, CiBuild.Status status, String job) {
        return new CiBuild(id, status, null, null, AT, 1000L, "http://localhost:8081/job/" + job + "/" + id + "/", null);
    }

    private static AuditEntry entry(AuditEntry.ActionType type, String target, String buildId) {
        return new AuditEntry("andy", type, target, AT, AuditEntry.ActionResult.SUCCESS, buildId, null, null, null);
    }

    @Test
    void deployEntryGetsTheDeployJobsBuildAndTheOthersThePipelines() {
        History history = historyService.listHistory();

        assertThat(history.entries()).containsExactly(
                new History.Entry(DEPLOY_3, DEPLOY_BUILD_3),
                new History.Entry(BUILD_3, PIPELINE_BUILD_3));
        assertThat(history.unavailable()).isEmpty();
    }

    @Test
    void entryWithoutBuildOrOfARemovedAppHasNoBuildAndIsNotLookedUp() {
        AuditEntry failed = entry(AuditEntry.ActionType.DEPLOY, PLAIN_APP, null);
        AuditEntry removed = entry(AuditEntry.ActionType.BUILD, "removed-app", "1");
        when(audit.getRecentEntries(any())).thenReturn(List.of(failed, removed));

        assertThat(historyService.listHistory().entries()).containsExactly(
                new History.Entry(failed, null), new History.Entry(removed, null));
        verify(ci, never()).listBuilds(any());
        verify(deployService, never()).listDeploys(any());
    }

    @Test
    void buildOlderThanTheRecentBuildsIsNull() {
        AuditEntry old = entry(AuditEntry.ActionType.BUILD, APP, "1");
        when(audit.getRecentEntries(any())).thenReturn(List.of(old));

        assertThat(historyService.listHistory().entries()).containsExactly(new History.Entry(old, null));
    }

    @Test
    void ciToolDownKeepsTheEntriesWithoutBuildsAndMarksThemUnavailable() {
        when(ci.listBuilds(APP)).thenThrow(new CiToolException("Jenkins not reachable"));

        History history = historyService.listHistory();

        assertThat(history.entries()).extracting(History.Entry::entry).containsExactly(DEPLOY_3, BUILD_3);
        assertThat(history.entries().get(1).build()).isNull();
        assertThat(history.unavailable()).containsExactly(History.Part.BUILDS);
    }
}
