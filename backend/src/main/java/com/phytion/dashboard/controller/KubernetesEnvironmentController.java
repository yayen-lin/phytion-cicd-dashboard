package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.service.KubernetesEnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What runs in each environment
 */
@RestController
@RequiredArgsConstructor
public class KubernetesEnvironmentController {

    private final KubernetesEnvironmentService environmentService;
    private final EnvironmentsProperties environmentsProperties;

    @GetMapping("/api/deploy/environments")
    public EnvironmentsResponse listEnvironments(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String appId) {
        return new EnvironmentsResponse(environmentService.listEnvironments(environment, appId).stream()
                .map(status -> new EnvironmentStatusResponse(status.environment(),
                        environmentsProperties.needsConfirmation(status.environment()),
                        environmentsProperties.nextEnvironment(status.environment())
                                .map(next -> new EnvironmentController.EnvironmentResponse(next,
                                        environmentsProperties.needsConfirmation(next)))
                                .orElse(null),
                        status.apps()))
                .toList());
    }

    public record EnvironmentsResponse(List<EnvironmentStatusResponse> environments) {
    }

    public record EnvironmentStatusResponse(String environment, boolean needsConfirmation,
                                            EnvironmentController.EnvironmentResponse nextEnvironment,
                                            List<EnvironmentApp> apps) {
    }
}
