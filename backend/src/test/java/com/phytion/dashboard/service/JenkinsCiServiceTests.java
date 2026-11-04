package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.phytion.dashboard.adapter.ci.jenkins.JenkinsAdapter;
import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.model.AuditEntry;
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
        ciService = new JenkinsCiService(ci, audit, "sample-app");
    }

    @Test
    void triggerBuildRecordsWhoStartedWhichBuild() {
        when(ci.triggerBuild("sample-app")).thenReturn(Optional.of("4"));

        assertThat(ciService.triggerBuild("andy")).contains("4");

        verify(audit).add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "sample-app", AuditEntry.ActionResult.SUCCESS,
                "4", null);
    }

    @Test
    void triggerBuildStillQueuedIsRecordedWithoutBuildId() {
        when(ci.triggerBuild("sample-app")).thenReturn(Optional.empty());

        assertThat(ciService.triggerBuild("andy")).isEmpty();

        verify(audit).add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "sample-app", AuditEntry.ActionResult.SUCCESS,
                null, "Queued, but the CI tool hadn't started it yet");
    }

    @Test
    void failedTriggerIsRecordedAndRethrown() {
        when(ci.triggerBuild("sample-app")).thenThrow(new CiToolException("Jenkins not reachable"));

        assertThatThrownBy(() -> ciService.triggerBuild("andy")).isInstanceOf(CiToolException.class);

        verify(audit).add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "sample-app", AuditEntry.ActionResult.FAILED,
                null, "Jenkins not reachable");
    }
}
