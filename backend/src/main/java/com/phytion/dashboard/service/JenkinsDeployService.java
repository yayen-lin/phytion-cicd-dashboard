package com.phytion.dashboard.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.ci.jenkins.JenkinsAdapter;
import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.adapter.operate.kubernetes.KubernetesAdapter;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.AuditFilter;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiParameterizedBuild;
import com.phytion.dashboard.model.DeployHistory;
import com.phytion.dashboard.model.DeployRun;
import com.phytion.dashboard.model.OperateWorkload;
import com.phytion.dashboard.model.Promotion;
import com.phytion.dashboard.model.ReleaseVersion;
import com.phytion.dashboard.model.Rollback;
import com.phytion.dashboard.model.RollbackOptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JenkinsDeployService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsDeployService.class);

    private final JenkinsAdapter jenkins;
    private final KubernetesAdapter kubernetes;
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

        // the dashboard action that started each build
        Map<String, AuditEntry> dashboardActions = Stream.of(AuditEntry.ActionType.DEPLOY,
                        AuditEntry.ActionType.PROMOTE, AuditEntry.ActionType.ROLLBACK)
                .flatMap(actionType -> audit.getRecentEntries(new AuditFilter(appId, actionType, null,
                        AuditEntry.ActionResult.SUCCESS, null, null, null)).stream())
                .filter(entry -> entry.getBuildId() != null)
                .collect(Collectors.toMap(AuditEntry::getBuildId, Function.identity(), (newest, older) -> newest));

        // keep the builds that say what they deployed where
        return builds.stream()
                .filter(run -> run.parameters().containsKey(VERSION) && run.parameters().containsKey(ENVIRONMENT))
                .map(run -> {
                    AuditEntry action = dashboardActions.get(run.build().id());
                    return new DeployRun(appId, run.parameters().get(VERSION), run.parameters().get(ENVIRONMENT),
                            run.build(), deployedBy(action, run, app),
                            action != null ? action.getActionType() : AuditEntry.ActionType.DEPLOY);
                })
                .toList();
    }

    /**
     * The app's recent deploys to every environment
     *
     * @param appId the name of the app
     * @return the deploys history list of the app
     * @throws AppNotFoundException when no configured app has that id, or the app isn't deployed
     * @throws CiToolException      when the CI tool can't be reached
     */
    public DeployHistory listDeployHistory(String appId) {
        List<DeployRun> deploys = listDeploys(appId);
        App app = apps.getApp(appId);

        // read the commit of each deployed version at the same time
        Map<String, ReleaseVersion> versions = new HashMap<>();
        boolean unavailable = false;
        if (app.release() != null) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Map<String, CompletableFuture<Optional<ReleaseVersion>>> versionReads = deploys.stream()
                        .map(DeployRun::version)
                        .distinct()
                        .collect(Collectors.toMap(Function.identity(), version -> CompletableFuture.supplyAsync(
                                () -> releases.findVersion(appId, version), executor)));
                for (Map.Entry<String, CompletableFuture<Optional<ReleaseVersion>>> read : versionReads.entrySet()) {
                    ParallelCalls.await(read.getValue()).ifPresent(version -> versions.put(read.getKey(), version));
                }
            } catch (ReleaseToolException e) {
                log.warn("Couldn't read the versions of {}", appId, e);
                versions.clear();
                unavailable = true;
            }
        }

        // attach each deploy's commit
        List<DeployHistory.Entry> entries = deploys.stream()
                .map(run -> {
                    ReleaseVersion version = versions.get(run.version());
                    return new DeployHistory.Entry(run, version != null ? version.commit() : null,
                            version != null ? version.commitUrl() : null);
                })
                .toList();
        return new DeployHistory(appId, environments.environments(), entries,
                unavailable ? List.of(DeployHistory.Part.COMMITS) : List.of());
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

    /**
     * Deploys the version running in an environment to the next environment and records it.
     *
     * @param appId           the name of the app
     * @param fromEnvironment one of the configured environments, not the last
     * @param username        who promotes it
     * @return the promotion, with the deploy build's id, or null for it when the CI tool hasn't started the build yet
     * @throws InvalidDeployException when no app is given, the environment doesn't exist or is the last, no version
     *                                runs there, the next environment runs the same or a higher version, or the
     *                                version isn't released
     * @throws AppNotFoundException   when no configured app has that id, or the app isn't released or deployed
     * @throws OperateToolException   when Kubernetes can't be reached to read the running versions
     * @throws ReleaseToolException   when the registry can't be reached to check the version
     * @throws CiToolException        when the deploy job didn't start
     */
    public Promotion promote(String appId, String fromEnvironment, String username) {

        // check if the app can be deployed
        if (appId == null) {
            throw new InvalidDeployException("No app given");
        }
        App app = apps.getApp(appId);
        if (app.deploy() == null) {
            throw AppNotFoundException.noDeploy(appId);
        }

        // check if the environment exists and has a next one
        if (!environments.contains(fromEnvironment)) {
            throw new InvalidDeployException("Environments are " + String.join(", ", environments.environments())
                    + ", got: " + fromEnvironment);
        }
        String toEnvironment = environments.nextEnvironment(fromEnvironment)
                .orElseThrow(() -> new InvalidDeployException(fromEnvironment + " is the last environment"));

        // read the versions running in the source and the next environment
        String version;
        String toVersion;
        try {
            version = findRunningVersion(appId, fromEnvironment);
            toVersion = findRunningVersion(appId, toEnvironment);
        } catch (OperateToolException e) {
            audit.add(username, AuditEntry.ActionType.PROMOTE, appId, AuditEntry.ActionResult.FAILED, null,
                    "from " + fromEnvironment + " to " + toEnvironment + ": couldn't read the running versions: "
                            + e.getMessage(), toEnvironment, null);
            throw e;
        }

        // check there's a version to promote and the next environment doesn't run it or a higher one
        if (version == null) {
            throw new InvalidDeployException(appId + " has no version running in " + fromEnvironment);
        }
        if (version.equals(toVersion)) {
            throw new InvalidDeployException(toEnvironment + " already runs " + appId + " " + version);
        }
        if (DockerRegistryReleaseService.isHigherVersion(toVersion, version)) {
            throw new InvalidDeployException(toEnvironment + " runs " + appId + " " + toVersion + ", higher than "
                    + version + " in " + fromEnvironment);
        }

        // check if the version is released
        String auditMessage = version + " from " + fromEnvironment + " to " + toEnvironment;
        try {
            if (!releases.hasVersion(appId, version)) {
                throw new InvalidDeployException(appId + " has no released version " + version);
            }
        } catch (ReleaseToolException e) {
            audit.add(username, AuditEntry.ActionType.PROMOTE, appId, AuditEntry.ActionResult.FAILED, null,
                    auditMessage + ": couldn't check the version: " + e.getMessage(), toEnvironment, version);
            throw e;
        }

        // start the deploy job and record it
        try {
            Optional<String> buildId = jenkins.triggerBuild(app.deploy().job(),
                    Map.of(VERSION, version, ENVIRONMENT, toEnvironment));
            String details = buildId.isPresent()
                    ? auditMessage
                    : auditMessage + "; queued, but the CI tool hadn't started it yet";
            audit.add(username, AuditEntry.ActionType.PROMOTE, appId, AuditEntry.ActionResult.SUCCESS,
                    buildId.orElse(null), details, toEnvironment, version);
            return new Promotion(appId, version, fromEnvironment, toEnvironment, buildId.orElse(null));
        } catch (CiToolException e) {
            audit.add(username, AuditEntry.ActionType.PROMOTE, appId, AuditEntry.ActionResult.FAILED, null,
                    auditMessage + ": " + e.getMessage(), toEnvironment, version);
            throw e;
        }
    }

    /**
     * List the versions a rollback in an environment can go to, from the deploy job's history in Jenkins.
     *
     * @param appId       the name of the app
     * @param environment one of the configured environments
     * @return the from version and the versions to go to; no from version and no versions when nothing deployed
     * successfully there, and no versions when nothing lower than the current version did
     * @throws InvalidDeployException when no app is given or the environment doesn't exist
     * @throws AppNotFoundException   when no configured app has that id, or the app isn't deployed
     * @throws CiToolException        when the CI tool can't be reached
     */
    public RollbackOptions listRollbackOptions(String appId, String environment) {

        // check if the app is deployed and the environment exists
        if (appId == null) {
            throw new InvalidDeployException("No app given");
        }
        App app = apps.getApp(appId);
        if (app.deploy() == null) {
            throw AppNotFoundException.noDeploy(appId);
        }
        if (!environments.contains(environment)) {
            throw new InvalidDeployException("Environments are " + String.join(", ", environments.environments())
                    + ", got: " + environment);
        }

        // the versions deployed successfully to the environment, newest first
        List<String> deployedVersions = jenkins.listParameterizedBuilds(app.deploy().job()).stream()
                .filter(run -> run.build().status() == CiBuild.Status.SUCCESS)
                .filter(run -> environment.equals(run.parameters().get(ENVIRONMENT)))
                .map(run -> run.parameters().get(VERSION))
                .filter(Objects::nonNull)
                .toList();
        if (deployedVersions.isEmpty()) {
            return new RollbackOptions(appId, environment, null, List.of());
        }

        // the current version is the newest; the ones below it, highest first
        String currentVersion = deployedVersions.getFirst();
        List<String> lowerVersions = deployedVersions.stream()
                .filter(version -> DockerRegistryReleaseService.isHigherVersion(currentVersion, version))
                .distinct()
                .sorted((version, other) -> DockerRegistryReleaseService.isHigherVersion(version, other) ? -1
                        : DockerRegistryReleaseService.isHigherVersion(other, version) ? 1 : 0)
                .toList();
        return new RollbackOptions(appId, environment, currentVersion, lowerVersions);
    }

    /**
     * Deploys an older version to an environment and records it. The version must be one of the rollback options.
     *
     * @param appId       the name of the app
     * @param environment one of the configured environments
     * @param version     a version deployed successfully there that's lower than the current one
     * @param username    who rolls it back
     * @return the rollback, with the deploy build's id, or null for it when the CI tool hasn't started the build yet
     * @throws InvalidDeployException when no app is given, the environment doesn't exist, or the version isn't one of
     *                                the rollback options
     * @throws AppNotFoundException   when no configured app has that id, or the app isn't deployed
     * @throws CiToolException        when the deploy history can't be read or the deploy job didn't start
     */
    public Rollback rollback(String appId, String environment, String version, String username) {

        // read the versions it can go to from the deploy history
        RollbackOptions options;
        try {
            options = listRollbackOptions(appId, environment);
        } catch (CiToolException e) {
            audit.add(username, AuditEntry.ActionType.ROLLBACK, appId, AuditEntry.ActionResult.FAILED, null,
                    "to " + version + " in " + environment + ": couldn't read the deploy history: " + e.getMessage(),
                    environment, version);
            throw e;
        }

        // check the version is one of them
        if (options.toVersions().isEmpty()) {
            throw new InvalidDeployException(appId + " has no older version to roll back to in " + environment);
        }
        if (!options.toVersions().contains(version)) {
            throw new InvalidDeployException(appId + " can roll back in " + environment + " to "
                    + String.join(", ", options.toVersions()) + ", got: " + version);
        }

        // start the deploy job and record it
        App app = apps.getApp(appId);
        String auditMessage = options.fromVersion() + " to " + version + " in " + environment;
        try {
            Optional<String> buildId = jenkins.triggerBuild(app.deploy().job(),
                    Map.of(VERSION, version, ENVIRONMENT, environment));
            String details = buildId.isPresent()
                    ? auditMessage
                    : auditMessage + "; queued, but the CI tool hadn't started it yet";
            audit.add(username, AuditEntry.ActionType.ROLLBACK, appId, AuditEntry.ActionResult.SUCCESS,
                    buildId.orElse(null), details, environment, version);
            return new Rollback(appId, environment, options.fromVersion(), version, buildId.orElse(null));
        } catch (CiToolException e) {
            audit.add(username, AuditEntry.ActionType.ROLLBACK, appId, AuditEntry.ActionResult.FAILED, null,
                    auditMessage + ": " + e.getMessage(), environment, version);
            throw e;
        }
    }

    /**
     * Who started a deploy build: the dashboard user, else the CI tool's user, else the CI tool.
     */
    private static String deployedBy(AuditEntry dashboardAction, CiParameterizedBuild run, App app) {
        if (dashboardAction != null) {
            return dashboardAction.getUsername();
        }
        return run.startedBy() != null ? run.startedBy() : app.deploy().tool();
    }

    /**
     * The version of an app running in an environment.
     *
     * @return the version, or null when the app doesn't run there or its version can't be told
     * @throws OperateToolException when Kubernetes can't be reached
     */
    private String findRunningVersion(String appId, String environment) {
        return kubernetes.listWorkloads(environment).stream()
                .filter(workload -> workload.name().equals(appId))
                .map(OperateWorkload::version)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
