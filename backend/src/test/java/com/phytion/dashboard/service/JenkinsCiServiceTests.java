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
import com.phytion.dashboard.config.CiProperties;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.CiPipeline;
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
        ciService = new JenkinsCiService(ci, audit, new CiProperties(List.of(new CiPipeline("jenkins", "sample-app"))));
    }

    @Test
    void listBuildsAsksTheToolForThePipelinesJob() {
        when(ci.listBuilds("sample-app")).thenReturn(List.of());

        assertThat(ciService.listBuilds("jenkins:sample-app")).isEmpty();

        verify(ci).listBuilds("sample-app");
    }

    @Test
    void getBuildStartedThroughTheDashboardShowsTheDashboardUser() {
        when(ci.getBuild("sample-app", "4")).thenReturn(Optional.of(new CiBuildDetails(build("4"), "dashboard-bot")));
        when(audit.findBuildStarter("jenkins:sample-app", "4")).thenReturn(Optional.of("andy"));

        assertThat(ciService.getBuild("jenkins:sample-app", "4")).map(CiBuildDetails::startedBy).contains("andy");
    }

    @Test
    void getBuildStartedOutsideTheDashboardShowsTheCiToolUser() {
        when(ci.getBuild("sample-app", "4")).thenReturn(Optional.of(new CiBuildDetails(build("4"), "Andy Lin")));
        when(audit.findBuildStarter("jenkins:sample-app", "4")).thenReturn(Optional.empty());

        assertThat(ciService.getBuild("jenkins:sample-app", "4")).map(CiBuildDetails::startedBy).contains("Andy Lin");
    }

    @Test
    void getBuildThatDoesNotExistIsEmpty() {
        when(ci.getBuild("sample-app", "99")).thenReturn(Optional.empty());

        assertThat(ciService.getBuild("jenkins:sample-app", "99")).isEmpty();
    }

    private static CiBuild build(String id) {
        return new CiBuild(id, CiBuild.Status.SUCCESS, null, null, Instant.EPOCH, 1000L,
                "http://jenkins/job/sample-app/" + id + "/", null);
    }

    @Test
    void unknownPipelineIsRejected() {
        assertThatThrownBy(() -> ciService.listBuilds("jenkins:other-app"))
                .isInstanceOf(PipelineNotFoundException.class);
        assertThatThrownBy(() -> ciService.triggerBuild("jenkins:other-app", "andy"))
                .isInstanceOf(PipelineNotFoundException.class);
    }

    @Test
    void pipelineOnAToolWithoutAdapterFailsAtStartup() {
        CiProperties properties = new CiProperties(List.of(new CiPipeline("github-actions", "some-app")));

        assertThatThrownBy(() -> new JenkinsCiService(ci, audit, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("github-actions");
    }

    @Test
    void startBuildPassesTheParametersAndLeavesTheAuditLogToTheCaller() {
        when(ci.triggerBuild("sample-app", Map.of("GIT_REF", "refs/tags/v1.4.0"))).thenReturn(Optional.of("12"));

        assertThat(ciService.startBuild("jenkins:sample-app", Map.of("GIT_REF", "refs/tags/v1.4.0"))).contains("12");
        verifyNoInteractions(audit);
    }

    @Test
    void startBuildOfUnknownPipelineIsRejected() {
        assertThatThrownBy(() -> ciService.startBuild("jenkins:other-app", Map.of()))
                .isInstanceOf(PipelineNotFoundException.class);
    }

    @Test
    void triggerBuildRecordsWhoStartedWhichBuild() {
        when(ci.triggerBuild("sample-app", Map.of())).thenReturn(Optional.of("4"));

        assertThat(ciService.triggerBuild("jenkins:sample-app", "andy")).contains("4");

        verify(audit).add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app", AuditEntry.ActionResult.SUCCESS,
                "4", null);
    }

    @Test
    void triggerBuildStillQueuedIsRecordedWithoutBuildId() {
        when(ci.triggerBuild("sample-app", Map.of())).thenReturn(Optional.empty());

        assertThat(ciService.triggerBuild("jenkins:sample-app", "andy")).isEmpty();

        verify(audit).add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app", AuditEntry.ActionResult.SUCCESS,
                null, "Queued, but the CI tool hadn't started it yet");
    }

    @Test
    void failedTriggerIsRecordedAndRethrown() {
        when(ci.triggerBuild("sample-app", Map.of())).thenThrow(new CiToolException("Jenkins not reachable"));

        assertThatThrownBy(() -> ciService.triggerBuild("jenkins:sample-app", "andy")).isInstanceOf(CiToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app", AuditEntry.ActionResult.FAILED,
                null, "Jenkins not reachable");
    }
}
