package com.phytion.dashboard.service;

import java.util.Comparator;
import java.util.List;

import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.model.EnvironmentOverview;
import com.phytion.dashboard.model.EnvironmentStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

 @Service
@RequiredArgsConstructor
public class OverviewService {

    private static final Logger log = LoggerFactory.getLogger(OverviewService.class);

    private static final Comparator<EnvironmentApp> FAILING_FIRST =
            Comparator.comparing(app -> Boolean.TRUE.equals(app.healthy()));

    private final KubernetesEnvironmentService environmentService;
    private final EnvironmentsProperties environments;

    public List<EnvironmentOverview> listEnvironmentOverviews() {

        // read every environment once
        List<EnvironmentStatus> statuses;
        try {
            statuses = environmentService.listEnvironments(null, null);
        } catch (OperateToolException e) {
            log.warn("Couldn't read the environments", e);
            return environments.environments().stream()
                    .map(environment -> new EnvironmentOverview(environment, null, List.of(),
                            List.of(EnvironmentOverview.Part.APPS)))
                    .toList();
        }

        // keep the running apps, failing first
        return statuses.stream()
                .map(status -> new EnvironmentOverview(
                        status.environment(),
                        status.apps().stream().filter(app -> app.desiredPods() != null).sorted(FAILING_FIRST).toList(),
                        status.runningDeploys().stream().map(EnvironmentOverview.RunningDeploy::from).toList(),
                        List.of()))
                .toList();
    }
}
