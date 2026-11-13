package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.EnvironmentStatus;
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

    @GetMapping("/api/deploy/environments")
    public EnvironmentsResponse listEnvironments(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String appId) {
        return new EnvironmentsResponse(environmentService.listEnvironments(environment, appId));
    }

    public record EnvironmentsResponse(List<EnvironmentStatus> environments) {
    }
}
