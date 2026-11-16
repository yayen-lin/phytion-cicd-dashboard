package com.phytion.dashboard.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.AppSummary;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.EnvironmentStatus;
import com.phytion.dashboard.model.ReleaseVersion;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.phytion.dashboard.service.ParallelCalls.await;

/**
 * Every app at a glance, combined from the CI tool, the registry and Kubernetes in one answer, read in parallel. A tool
 * that can't be reached leaves its part empty and marked unavailable, so the other parts still show.
 */
@Service
@RequiredArgsConstructor
public class AppSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AppSummaryService.class);

    private final AppService apps;
    private final JenkinsCiService ci;
    private final DockerRegistryReleaseService releases;
    private final KubernetesEnvironmentService environments;

    public List<AppSummary> listAppSummaries() {
        List<App> configuredApps = apps.listApps();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // start every call: the environments once for all apps, the builds and versions once per app
            CompletableFuture<Read<List<EnvironmentStatus>>> environmentsRead =
                    CompletableFuture.supplyAsync(this::readEnvironments, executor);
            List<CompletableFuture<Read<CiBuild>>> buildReads = configuredApps.stream()
                    .map(app -> CompletableFuture.supplyAsync(() -> readLatestBuild(app), executor))
                    .toList();
            List<CompletableFuture<Read<ReleaseVersion>>> versionReads = configuredApps.stream()
                    .map(app -> CompletableFuture.supplyAsync(() -> readLatestVersion(app), executor))
                    .toList();

            // wait for all of them and combine each app's parts
            Read<List<EnvironmentStatus>> environmentStatuses = await(environmentsRead);
            return IntStream.range(0, configuredApps.size())
                    .mapToObj(i -> summarize(configuredApps.get(i), await(buildReads.get(i)),
                            await(versionReads.get(i)), environmentStatuses))
                    .toList();
        }
    }

    /**
     * What one tool call read, or that the tool couldn't be reached (value null).
     */
    private record Read<T>(T value, boolean unavailable) {
        static <T> Read<T> of(T value) {
            return new Read<>(value, false);
        }

        static <T> Read<T> failed() {
            return new Read<>(null, true);
        }
    }

    /**
     * One app's summary, from the parts read for it and the environments read for all apps.
     */
    private AppSummary summarize(App app, Read<CiBuild> latestBuild, Read<ReleaseVersion> latestVersion,
                                 Read<List<EnvironmentStatus>> environmentStatuses) {

        // the parts whose tool couldn't be reached
        List<AppSummary.Part> unavailable = new ArrayList<>();
        if (latestBuild.unavailable()) {
            unavailable.add(AppSummary.Part.BUILD);
        }
        if (latestVersion.unavailable()) {
            unavailable.add(AppSummary.Part.VERSION);
        }

        // what runs in each environment, for deployed apps
        List<AppSummary.Deployment> deployments = List.of();
        if (app.deploy() != null) {
            if (environmentStatuses.unavailable()) {
                unavailable.add(AppSummary.Part.ENVIRONMENTS);
            } else {
                deployments = environmentStatuses.value().stream()
                        .flatMap(status -> status.apps().stream()
                                .filter(state -> state.appId().equals(app.id()))
                                .map(state -> new AppSummary.Deployment(status.environment(), state)))
                        .toList();
            }
        }

        return new AppSummary(app, latestBuild.value(), latestVersion.value(), deployments,
                List.copyOf(unavailable));
    }

    /**
     * The newest build of the app's pipeline (value null when there's none), or failed when Jenkins can't be reached.
     */
    private Read<CiBuild> readLatestBuild(App app) {
        try {
            return Read.of(ci.listBuilds(app.id()).stream().findFirst().orElse(null));
        } catch (CiToolException e) {
            log.warn("Couldn't read the builds of {}", app.id(), e);
            return Read.failed();
        }
    }

    /**
     * The highest released version (value null when there's none or the app isn't released), or failed when the
     * registry can't be reached.
     */
    private Read<ReleaseVersion> readLatestVersion(App app) {
        if (app.release() == null) {
            return Read.of(null);
        }
        try {
            return Read.of(releases.findLatestVersion(app.id()).orElse(null));
        } catch (ReleaseToolException e) {
            log.warn("Couldn't read the versions of {}", app.id(), e);
            return Read.failed();
        }
    }

    /**
     * Every environment with every deployed app in it, or failed when Kubernetes can't be reached.
     */
    private Read<List<EnvironmentStatus>> readEnvironments() {
        try {
            return Read.of(environments.listEnvironments(null, null));
        } catch (OperateToolException e) {
            log.warn("Couldn't read the environments", e);
            return Read.failed();
        }
    }
}
