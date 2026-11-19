package com.phytion.dashboard.controller;

import com.phytion.dashboard.auth.ActionAccess;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.DeployHistory;
import com.phytion.dashboard.model.Promotion;
import com.phytion.dashboard.model.Rollback;
import com.phytion.dashboard.model.RollbackOptions;
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
    private final ActionAccess actionAccess;
    private final EnvironmentsProperties environmentsProperties;

    /**
     * Starts the app's deploy job
     */
    @PostMapping("/api/deploy/deployments")
    public DeployResponse deploy(@RequestBody @Valid DeployRequest request, Authentication authentication) {
        actionAccess.checkCanActIn(request.environment(), authentication);
        return new DeployResponse(request.appId(), request.version(), request.environment(),
                deployService.deploy(request.appId(), request.version(), request.environment(),
                        authentication.getName()).orElse(null));
    }

    /**
     * Starts the app's deploy job with the version running in an environment to the next environment
     */
    @PostMapping("/api/deploy/promotions")
    public Promotion promote(@RequestBody @Valid PromoteRequest request, Authentication authentication) {
        actionAccess.checkCanAct(authentication);
        environmentsProperties.nextEnvironment(request.fromEnvironment())
                .ifPresent(toEnvironment -> actionAccess.checkCanActIn(toEnvironment, authentication));

        // promote
        return deployService.promote(request.appId(), request.fromEnvironment(), authentication.getName());
    }

    /**
     * Starts the app's deploy job with an older version, one of the rollback options
     */
    @PostMapping("/api/deploy/rollbacks")
    public Rollback rollback(@RequestBody @Valid RollbackRequest request, Authentication authentication) {
        actionAccess.checkCanActIn(request.environment(), authentication);
        return deployService.rollback(request.appId(), request.environment(), request.version(),
                authentication.getName());
    }

    /**
     * The versions a rollback in an environment can go to
     */
    @GetMapping("/api/deploy/rollback-options")
    public RollbackOptions getRollbackOptions(@RequestParam String appId, @RequestParam String environment) {
        return deployService.listRollbackOptions(appId, environment);
    }

    /**
     * The app's recent deploys to every environment.
     */
    @GetMapping("/api/deploy/deployments")
    public DeployHistory getDeployHistory(@RequestParam String appId) {
        return deployService.listDeployHistory(appId);
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
     * @param appId       the name of the app
     * @param environment the environment to roll back
     * @param version     the version to go back to, one of the rollback options
     */
    public record RollbackRequest(@NotBlank String appId, @NotBlank String environment, @NotBlank String version) {
    }

    /**
     * @param buildId the deploy build's id in the app's deploy job, or null when the CI tool hasn't started it yet
     */
    public record DeployResponse(String app, String version, String environment, String buildId) {
    }
}
