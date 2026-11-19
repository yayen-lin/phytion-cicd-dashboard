package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.DeployRun;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.model.EnvironmentOverview;
import com.phytion.dashboard.model.EnvironmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the overview with a fake environment service: no Spring.
 */
class OverviewServiceTests {

    private static final EnvironmentApp SAMPLE_HEALTHY = new EnvironmentApp("sample-app", "1.0.1", null, null, 1, 1,
            true, null, null, null);
    private static final EnvironmentApp DUMMY_FAILING = new EnvironmentApp("dummy-app", "0.1.0", null, null, 2, 1,
            false, null, null, null);
    private static final DeployRun RUNNING_TO_PROD = new DeployRun("sample-app", "1.0.2", "prod",
            new CiBuild("9", CiBuild.Status.RUNNING, null, null, Instant.parse("2026-11-14T12:00:00Z"), null,
                    "http://localhost:8081/job/sample-app-deploy/9/", null), "andy");

    private KubernetesEnvironmentService environmentService;
    private OverviewService overviewService;

    @BeforeEach
    void setUp() {
        environmentService = mock(KubernetesEnvironmentService.class);
        overviewService = new OverviewService(environmentService,
                new EnvironmentsProperties(List.of("dev", "prod"), List.of("prod"), List.of("prod")));

        // dev runs both apps, sample-app (first in the config) healthy; prod runs nothing yet, one deploy running
        when(environmentService.listEnvironments(null, null)).thenReturn(List.of(
                new EnvironmentStatus("dev", List.of(SAMPLE_HEALTHY, DUMMY_FAILING), List.of()),
                new EnvironmentStatus("prod", List.of(EnvironmentApp.notDeployed("sample-app"),
                        EnvironmentApp.notDeployed("dummy-app")), List.of(RUNNING_TO_PROD))));
    }

    @Test
    void runningAppsAreListedFailingFirst() {
        EnvironmentOverview dev = overviewService.listEnvironmentOverviews().getFirst();

        assertThat(dev.environment()).isEqualTo("dev");
        assertThat(dev.apps()).containsExactly(DUMMY_FAILING, SAMPLE_HEALTHY);
        assertThat(dev.unavailable()).isEmpty();
    }

    @Test
    void notDeployedAppsAreLeftOutAndRunningDeploysKept() {
        EnvironmentOverview prod = overviewService.listEnvironmentOverviews().get(1);

        assertThat(prod.apps()).isEmpty();
        assertThat(prod.runningDeploys()).containsExactly(EnvironmentOverview.RunningDeploy.from(RUNNING_TO_PROD));
    }

    @Test
    void kubernetesDownMarksEveryEnvironmentsAppsUnavailable() {
        when(environmentService.listEnvironments(null, null)).thenThrow(new OperateToolException("down"));

        assertThat(overviewService.listEnvironmentOverviews()).containsExactly(
                new EnvironmentOverview("dev", null, List.of(), List.of(EnvironmentOverview.Part.APPS)),
                new EnvironmentOverview("prod", null, List.of(), List.of(EnvironmentOverview.Part.APPS)));
    }
}
