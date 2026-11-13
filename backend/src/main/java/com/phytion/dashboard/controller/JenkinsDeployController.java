package com.phytion.dashboard.controller;

import com.phytion.dashboard.service.JenkinsDeployService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deploying a released version of an app to an environment
 */
@RestController
@RequiredArgsConstructor
public class JenkinsDeployController {

    private final JenkinsDeployService deployService;

    /**
     * Starts the app's deploy job
     */
    @PostMapping("/api/deploy/deployments")
    public DeployResponse deploy(@RequestBody @Valid DeployRequest request, Authentication authentication) {
        return new DeployResponse(request.appId(), request.version(), request.environment(),
                deployService.deploy(request.appId(), request.version(), request.environment(),
                        authentication.getName()).orElse(null));
    }

    /**
     * @param appId       the name of the app
     * @param version     a released version
     * @param environment where to deploy it
     */
    public record DeployRequest(@NotBlank String appId, @NotBlank String version, @NotBlank String environment) {
    }

    /**
     * @param buildId the deploy build's id in the app's deploy job, or null when the CI tool hasn't started it yet
     */
    public record DeployResponse(String app, String version, String environment, String buildId) {
    }
}
