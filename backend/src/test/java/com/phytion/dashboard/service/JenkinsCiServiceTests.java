package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.phytion.dashboard.adapter.ci.jenkins.JenkinsAdapter;
import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.config.AppsProperties;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the CI logic with a fake CI tool and a fake audit log: no Spring, no database.
 */
class JenkinsCiServiceTests {

    private JenkinsAdapter ci;
    private AuditService audit;
    private JenkinsCiService ciService;

    @BeforeEach
    void setUp() {
        ci = mock(JenkinsAdapter.class);
        audit = mock(AuditService.class);
        // The job's name differs from the app id, to show which one goes where
        ciService = new JenkinsCiService(ci, audit, new AppService(new AppsProperties(Map.of("sample-app",
                new AppsProperties.AppSettings(new CodeRepo("bitbucket", "andy-test-app", "sample-app", null),
                        new CiPipeline("jenkins", "sample-app-job"), null)))));
    }

    @Test
    void listBuildsAsksTheToolForTheAppsJob() {
        when(ci.listBuilds("sample-app-job")).thenReturn(List.of());

        assertThat(ciService.listBuilds("sample-app")).isEmpty();

        verify(ci).listBuilds("sample-app-job");
    }

    @Test
    void getBuildStartedThroughTheDashboardShowsTheDashboardUser() {
        when(ci.getBuild("sample-app-job", "4")).thenReturn(Optional.of(new CiBuildDetails(build("4"), "dashboard-bot", null)));
        when(audit.findBuildStarter("sample-app", "4")).thenReturn(Optional.of("andy"));

        assertThat(ciService.getBuild("sample-app", "4")).get()
                .extracting(CiBuildDetails::startedBy, CiBuildDetails::startedBySource)
                .containsExactly("andy", "dashboard");
    }

    @Test
    void getBuildStartedOutsideTheDashboardShowsTheCiToolUser() {
        when(ci.getBuild("sample-app-job", "4")).thenReturn(Optional.of(new CiBuildDetails(build("4"), "Andy Lin", null)));
        when(audit.findBuildStarter("sample-app", "4")).thenReturn(Optional.empty());

        assertThat(ciService.getBuild("sample-app", "4")).get()
                .extracting(CiBuildDetails::startedBy, CiBuildDetails::startedBySource)
                .containsExactly("Andy Lin", "jenkins");
    }

    @Test
    void getBuildStartedByNoOneHasNoSource() {
        when(ci.getBuild("sample-app-job", "4")).thenReturn(Optional.of(new CiBuildDetails(build("4"), null, null)));
        when(audit.findBuildStarter("sample-app", "4")).thenReturn(Optional.empty());

        assertThat(ciService.getBuild("sample-app", "4")).get()
                .extracting(CiBuildDetails::startedBy, CiBuildDetails::startedBySource)
                .containsExactly(null, null);
    }

    @Test
    void getBuildThatDoesNotExistIsEmpty() {
        when(ci.getBuild("sample-app-job", "99")).thenReturn(Optional.empty());

        assertThat(ciService.getBuild("sample-app", "99")).isEmpty();
    }

    private static CiBuild build(String id) {
        return new CiBuild(id, CiBuild.Status.SUCCESS, null, null, Instant.EPOCH, 1000L,
                "http://jenkins/job/sample-app/" + id + "/", null);
    }

    @Test
    void unknownAppIsRejected() {
        assertThatThrownBy(() -> ciService.listBuilds("other-app"))
                .isInstanceOf(AppNotFoundException.class);
        assertThatThrownBy(() -> ciService.triggerBuild("other-app", "andy"))
                .isInstanceOf(AppNotFoundException.class);
    }

    @Test
    void startBuildPassesTheParametersAndLeavesTheAuditLogToTheCaller() {
        when(ci.triggerBuild("sample-app-job", Map.of("GIT_REF", "refs/tags/v1.4.0"))).thenReturn(Optional.of("12"));

        assertThat(ciService.startBuild("sample-app", Map.of("GIT_REF", "refs/tags/v1.4.0"))).contains("12");
        verifyNoInteractions(audit);
    }

    @Test
    void startBuildOfUnknownAppIsRejected() {
        assertThatThrownBy(() -> ciService.startBuild("other-app", Map.of()))
                .isInstanceOf(AppNotFoundException.class);
    }

    @Test
    void triggerBuildRecordsWhoStartedWhichBuild() {
        when(ci.triggerBuild("sample-app-job", Map.of())).thenReturn(Optional.of("4"));

        assertThat(ciService.triggerBuild("sample-app", "andy")).contains("4");

        verify(audit).add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "sample-app", AuditEntry.ActionResult.SUCCESS,
                "4", null);
    }

    @Test
    void triggerBuildStillQueuedIsRecordedWithoutBuildId() {
        when(ci.triggerBuild("sample-app-job", Map.of())).thenReturn(Optional.empty());

        assertThat(ciService.triggerBuild("sample-app", "andy")).isEmpty();

        verify(audit).add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "sample-app", AuditEntry.ActionResult.SUCCESS,
                null, "Queued, but the CI tool hadn't started it yet");
    }

    @Test
    void failedTriggerIsRecordedAndRethrown() {
        when(ci.triggerBuild("sample-app-job", Map.of())).thenThrow(new CiToolException("Jenkins not reachable"));

        assertThatThrownBy(() -> ciService.triggerBuild("sample-app", "andy")).isInstanceOf(CiToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "sample-app", AuditEntry.ActionResult.FAILED,
                null, "Jenkins not reachable");
    }
}
