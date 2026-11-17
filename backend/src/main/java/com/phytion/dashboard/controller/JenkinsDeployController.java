package com.phytion.dashboard.controller;

import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.Promotion;
import com.phytion.dashboard.service.JenkinsDeployService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
     * Starts the app's deploy job with the version running in an environment to the next environment
     */
    @PostMapping("/api/deploy/promotions")
    public Promotion promote(@RequestBody @Valid PromoteRequest request, Authentication authentication) {
        return deployService.promote(request.appId(), request.fromEnvironment(), authentication.getName());
    }

    /**
     * One build of the app's deploy job
     */
    @GetMapping("/api/deploy/deployments/{buildId}")
    public ResponseEntity<CiBuild> getDeployBuild(@RequestParam String appId, @PathVariable String buildId) {
        return ResponseEntity.of(deployService.getDeployBuild(appId, buildId));
    }

    /**
     * @param appId       the name of the app
     * @param version     a released version
     * @param environment where to deploy it
     */
    public record DeployRequest(@NotBlank String appId, @NotBlank String version, @NotBlank String environment) {
    }

    /**
     * @param appId           the name of the app
     * @param fromEnvironment the environment whose running version moves to the next one
     */
    public record PromoteRequest(@NotBlank String appId, @NotBlank String fromEnvironment) {
    }

    /**
     * @param buildId the deploy build's id in the app's deploy job, or null when the CI tool hasn't started it yet
     */
    public record DeployResponse(String app, String version, String environment, String buildId) {
    }
}
