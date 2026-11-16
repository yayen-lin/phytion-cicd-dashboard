package com.phytion.dashboard.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.adapter.operate.kubernetes.KubernetesAdapter;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.DeployRun;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.model.EnvironmentStatus;
import com.phytion.dashboard.model.OperateWorkload;
import com.phytion.dashboard.model.ReleaseVersion;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.phytion.dashboard.service.ParallelCalls.await;

@Service
@RequiredArgsConstructor
public class KubernetesEnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(KubernetesEnvironmentService.class);

    private final KubernetesAdapter kubernetes;
    private final DockerRegistryReleaseService releases;
    private final JenkinsDeployService deployService;
    private final AppService apps;
    private final EnvironmentsProperties environments;

    /**
     * Returns evironments with its app.
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

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // read each app's deploys once for all environments
            Map<String, CompletableFuture<List<DeployRun>>> deployReads = pickedApps.stream()
                    .collect(Collectors.toMap(App::id,
                            app -> CompletableFuture.supplyAsync(() -> readDeploys(app), executor)));

            // read every environment's workloads
            List<CompletableFuture<Map<String, OperateWorkload>>> workloadReads = pickedEnvironments.stream()
                    .map(env -> CompletableFuture.supplyAsync(() -> kubernetes.listWorkloads(env).stream()
                            .collect(Collectors.toMap(OperateWorkload::name, Function.identity(), (a, b) -> a)),
                            executor))
                    .toList();

            // as each environment arrives, look up all its apps
            Map<String, CompletableFuture<Optional<ReleaseVersion>>> versionReads = new ConcurrentHashMap<>();
            List<List<CompletableFuture<EnvironmentApp>>> rowReads = IntStream.range(0, pickedEnvironments.size())
                    .mapToObj(i -> {
                        String env = pickedEnvironments.get(i);
                        Map<String, OperateWorkload> workloads = await(workloadReads.get(i));
                        return pickedApps.stream()
                                .map(app -> CompletableFuture.supplyAsync(() -> toEnvironmentApp(app, env,
                                        workloads.get(app.id()), versionReads, deployReads.get(app.id()), executor),
                                        executor))
                                .toList();
                    })
                    .toList();

            // wait for every row and the deploys
            List<DeployRun> deploys = pickedApps.stream()
                    .flatMap(app -> await(deployReads.get(app.id())).stream())
                    .toList();
            return IntStream.range(0, pickedEnvironments.size())
                    .mapToObj(i -> new EnvironmentStatus(pickedEnvironments.get(i),
                            rowReads.get(i).stream().map(ParallelCalls::await).toList(),
                            runningDeploys(deploys, pickedEnvironments.get(i))))
                    .toList();
        }
    }

    /**
     * The deploy builds to an environment still running, newest first.
     */
    private static List<DeployRun> runningDeploys(List<DeployRun> deploys, String environment) {
        return deploys.stream()
                .filter(run -> run.environment().equals(environment) && run.build().status() == CiBuild.Status.RUNNING)
                .sorted(Comparator.comparing((DeployRun run) -> run.build().startedAt()).reversed())
                .toList();
    }

    /**
     * One app in one environment; not deployed when there's no workload for it.
     */
    private EnvironmentApp toEnvironmentApp(App app, String environment, OperateWorkload workload,
                                            Map<String, CompletableFuture<Optional<ReleaseVersion>>> versionReads,
                                            CompletableFuture<List<DeployRun>> deployRead,
                                            ExecutorService executor) {
        if (workload == null) {
            return EnvironmentApp.notDeployed(app.id());
        }
        String version = workload.version();

        // commit of the running version
        CompletableFuture<Optional<ReleaseVersion>> releaseRead = version == null
                ? CompletableFuture.completedFuture(Optional.empty())
                : versionReads.computeIfAbsent(app.id() + ":" + version,
                        key -> CompletableFuture.supplyAsync(() -> findRelease(app, version), executor));

        // the newest deploy build of this version to here: who deployed it and when
        Optional<DeployRun> deploy = version == null
                ? Optional.empty()
                : await(deployRead).stream()
                        .filter(run -> run.environment().equals(environment) && run.version().equals(version))
                        .findFirst();
        Optional<ReleaseVersion> release = await(releaseRead);

        boolean healthy = workload.desiredPods() > 0 && workload.readyPods() >= workload.desiredPods();
        return new EnvironmentApp(
                app.id(),
                version,
                release.map(ReleaseVersion::commit).orElse(null),
                release.map(ReleaseVersion::commitUrl).orElse(null),
                workload.desiredPods(),
                workload.readyPods(),
                healthy,
                deploy.map(run -> run.build().startedAt()).orElse(null),
                deploy.map(DeployRun::deployedBy).orElse(null),
                deploy.map(run -> run.build().id()).orElse(null));
    }

    /**
     * The app's recent deploys, or none when the CI tool is down.
     */
    private List<DeployRun> readDeploys(App app) {
        try {
            return deployService.listDeploys(app.id());
        } catch (CiToolException e) {
            log.warn("Couldn't read the deploys of {}", app.id(), e);
            return List.of();
        }
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
