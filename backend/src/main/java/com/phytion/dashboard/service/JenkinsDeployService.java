package com.phytion.dashboard.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.ci.jenkins.JenkinsAdapter;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.AuditFilter;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiParameterizedBuild;
import com.phytion.dashboard.model.DeployRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Deploying a released version of an app to an environment.
 */
@Service
@RequiredArgsConstructor
public class JenkinsDeployService {

    private final JenkinsAdapter jenkins;
    private final DockerRegistryReleaseService releases;
    private final AuditService audit;
    private final AppService apps;
    private final EnvironmentsProperties environments;

    // jenkins job parameters
    private static final String VERSION = "VERSION";
    private static final String ENVIRONMENT = "ENVIRONMENT";

    /**
     * The app's recent deploys.
     *
     * @param appId the name of the app
     * @return the deploys, newest first; at most the CI tool's max-builds
     * @throws AppNotFoundException when no configured app has that id, or the app isn't deployed
     * @throws CiToolException      when the CI tool can't be reached
     */
    public List<DeployRun> listDeploys(String appId) {
        App app = apps.getApp(appId);
        if (app.deploy() == null) {
            throw AppNotFoundException.noDeploy(appId);
        }
        List<CiParameterizedBuild> builds = jenkins.listParameterizedBuilds(app.deploy().job());

        // who started each build from the dashboard: the CI tool only knows the dashboard's own user
        Map<String, String> dashboardUsers = audit.getRecentEntries(new AuditFilter(appId, AuditEntry.ActionType.DEPLOY,
                        null, AuditEntry.ActionResult.SUCCESS, null, null, null)).stream()
                .filter(entry -> entry.getBuildId() != null)
                .collect(Collectors.toMap(AuditEntry::getBuildId, AuditEntry::getUsername, (newest, older) -> newest));

        // keep the builds that say what they deployed where
        return builds.stream()
                .filter(run -> run.parameters().containsKey(VERSION) && run.parameters().containsKey(ENVIRONMENT))
                .map(run -> new DeployRun(appId, run.parameters().get(VERSION), run.parameters().get(ENVIRONMENT),
                        run.build(), dashboardUsers.getOrDefault(run.build().id(), run.startedBy())))
                .toList();
    }

    /**
     * Get one build of an app's deploy job.
     *
     * @param appId   the name of the app
     * @param buildId the build's id in the deploy job
     * @return the build, or empty when the deploy job has no such build
     * @throws AppNotFoundException when no configured app has that id, or the app isn't deployed
     * @throws CiToolException      when the CI tool can't be reached
     */
    public Optional<CiBuild> getDeployBuild(String appId, String buildId) {
        App app = apps.getApp(appId);
        if (app.deploy() == null) {
            throw AppNotFoundException.noDeploy(appId);
        }
        return jenkins.getBuild(app.deploy().job(), buildId).map(CiBuildDetails::build);
    }

    /**
     * Starts the deploy job for a released version and records it.
     *
     * @param appId       the name of the app
     * @param version     a version in the registry
     * @param environment one of the configured environments
     * @param username    who deploys it
     * @return the deploy build's id, or empty when the CI tool hasn't started the build yet
     * @throws InvalidDeployException when no app is given, the environment doesn't exist or the version isn't released
     * @throws AppNotFoundException   when no configured app has that id, or the app isn't released or deployed
     * @throws ReleaseToolException   when the registry can't be reached to check the version
     * @throws CiToolException        when the deploy job didn't start
     */
    public Optional<String> deploy(String appId, String version, String environment, String username) {

        // check if the app can be deployed
        if (appId == null) {
            throw new InvalidDeployException("No app given");
        }
        App app = apps.getApp(appId);
        if (app.deploy() == null) {
            throw AppNotFoundException.noDeploy(appId);
        }

        // check if the environment exists and the version is released
        if (!environments.contains(environment)) {
            throw new InvalidDeployException("Environments are " + String.join(", ", environments.environments())
                    + ", got: " + environment);
        }
        String auditMessage = version + " to " + environment;
        try {
            if (!releases.hasVersion(appId, version)) {
                throw new InvalidDeployException(appId + " has no released version " + version);
            }
        } catch (ReleaseToolException e) {
            audit.add(username, AuditEntry.ActionType.DEPLOY, appId, AuditEntry.ActionResult.FAILED, null,
                    auditMessage + ": couldn't check the version: " + e.getMessage(), environment, version);
            throw e;
        }

        // start the deploy job and record it
        try {
            Optional<String> buildId = jenkins.triggerBuild(app.deploy().job(),
                    Map.of(VERSION, version, ENVIRONMENT, environment));
            String details = buildId.isPresent()
                    ? auditMessage
                    : auditMessage + "; queued, but the CI tool hadn't started it yet";
            audit.add(username, AuditEntry.ActionType.DEPLOY, appId, AuditEntry.ActionResult.SUCCESS,
                    buildId.orElse(null), details, environment, version);
            return buildId;
        } catch (CiToolException e) {
            audit.add(username, AuditEntry.ActionType.DEPLOY, appId, AuditEntry.ActionResult.FAILED, null,
                    auditMessage + ": " + e.getMessage(), environment, version);
            throw e;
        }
    }
}
