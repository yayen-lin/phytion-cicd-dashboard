package com.phytion.dashboard.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.adapter.operate.kubernetes.KubernetesAdapter;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.AuditFilter;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.model.EnvironmentStatus;
import com.phytion.dashboard.model.OperateWorkload;
import com.phytion.dashboard.model.ReleaseVersion;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * What runs in each environment: read from Kubernetes (one namespace per environment), with the commit from the
 * registry and who deployed it from the audit log.
 */
@Service
@RequiredArgsConstructor
public class KubernetesEnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(KubernetesEnvironmentService.class);

    private final KubernetesAdapter kubernetes;
    private final DockerRegistryReleaseService releases;
    private final AuditService audit;
    private final AppService apps;
    private final EnvironmentsProperties environments;

    /**
     * Every environment with every deployable app in it, optionally narrowed to one environment or one app.
     * e.g. ("staging", null) -> [staging: sample-app 1.0.1 healthy, dummy-app not deployed]
     *
     * @param environment one of the configured environments, or null for all
     * @param appId       the name of an app, or null for all deployable apps
     * @return one entry per environment, in the config's order
     * @throws InvalidDeployException when the environment isn't configured
     * @throws AppNotFoundException   when no configured app has that id, or the app isn't deployed
     * @throws OperateToolException   when Kubernetes can't be reached
     */
    public List<EnvironmentStatus> listEnvironments(String environment, String appId) {

        // pick the environments
        List<String> pickedEnvironments;
        if (environment == null) {
            pickedEnvironments = environments.environments();
        } else if (environments.contains(environment)) {
            pickedEnvironments = List.of(environment);
        } else {
            throw new InvalidDeployException("Environments are " + String.join(", ", environments.environments())
                    + ", got: " + environment);
        }

        // pick the apps
        List<App> pickedApps;
        if (appId == null) {
            pickedApps = apps.listApps().stream().filter(app -> app.deploy() != null).toList();
        } else {
            App app = apps.getApp(appId);
            if (app.deploy() == null) {
                throw AppNotFoundException.noDeploy(appId);
            }
            pickedApps = List.of(app);
        }

        // read each environment; a version running in several environments is looked up in the registry once
        Map<String, Optional<ReleaseVersion>> versionsSeen = new HashMap<>();
        return pickedEnvironments.stream()
                .map(env -> {
                    Map<String, OperateWorkload> workloads = kubernetes.listWorkloads(env).stream()
                            .collect(Collectors.toMap(OperateWorkload::name, Function.identity(), (a, b) -> a));
                    List<EnvironmentApp> rows = pickedApps.stream()
                            .map(app -> toEnvironmentApp(app, env, workloads.get(app.id()), versionsSeen))
                            .toList();
                    return new EnvironmentStatus(env, rows);
                })
                .toList();
    }

    /**
     * One app in one environment; not deployed when there's no workload for it.
     */
    private EnvironmentApp toEnvironmentApp(App app, String environment, OperateWorkload workload,
                                            Map<String, Optional<ReleaseVersion>> versionsSeen) {
        if (workload == null) {
            return EnvironmentApp.notDeployed(app.id());
        }
        String version = workload.version();

        // commit of the running version
        Optional<ReleaseVersion> release = version == null
                ? Optional.empty()
                : versionsSeen.computeIfAbsent(app.id() + ":" + version, key -> findRelease(app, version));

        // who deployed it
        Optional<AuditEntry> deploy = version == null
                ? Optional.empty()
                : audit.getRecentEntries(new AuditFilter(app.id(), AuditEntry.ActionType.DEPLOY, null,
                        AuditEntry.ActionResult.SUCCESS, null, environment, version)).stream().findFirst();

        boolean healthy = workload.desiredPods() > 0 && workload.readyPods() >= workload.desiredPods();
        return new EnvironmentApp(
                app.id(),
                version,
                release.map(ReleaseVersion::commit).orElse(null),
                release.map(ReleaseVersion::commitUrl).orElse(null),
                workload.desiredPods(),
                workload.readyPods(),
                healthy,
                deploy.map(AuditEntry::getCreatedAt).orElse(null),
                deploy.map(AuditEntry::getUsername).orElse(null),
                deploy.map(AuditEntry::getBuildId).orElse(null));
    }

    /**
     * The released version, or empty when the app isn't released, the registry doesn't have it, or the registry is
     * down: the commit is extra, so a registry outage shouldn't hide what's running.
     */
    private Optional<ReleaseVersion> findRelease(App app, String version) {
        if (app.release() == null) {
            return Optional.empty();
        }
        try {
            return releases.findVersion(app.id(), version);
        } catch (ReleaseToolException e) {
            log.warn("Couldn't read {} {} from the registry", app.id(), version, e);
            return Optional.empty();
        }
    }
}
