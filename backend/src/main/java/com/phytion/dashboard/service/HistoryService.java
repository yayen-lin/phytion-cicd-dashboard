package com.phytion.dashboard.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.AuditFilter;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.DeployRun;
import com.phytion.dashboard.model.History;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.phytion.dashboard.service.ParallelCalls.await;

/**
 * The History page: the audit log with each entry's build, read from the CI tool in parallel, once per app and job.
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

    private final AuditService audit;
    private final AppService apps;
    private final JenkinsCiService ci;
    private final JenkinsDeployService deployService;

    public History listHistory() {
        List<AuditEntry> entries = audit.getRecentEntries(new AuditFilter(null, null, null, null, null, null, null));

        // the configured apps the entries started builds of; a removed app's builds can't be looked up
        Map<String, App> configuredApps = apps.listApps().stream()
                .collect(Collectors.toMap(App::id, Function.identity()));
        Set<String> pipelineAppIds = appIdsWithBuilds(entries, configuredApps, false);
        Set<String> deployAppIds = appIdsWithBuilds(entries, configuredApps, true);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // read each app's pipeline builds and deploy builds at the same time
            Map<String, CompletableFuture<List<CiBuild>>> pipelineReads = pipelineAppIds.stream()
                    .collect(Collectors.toMap(Function.identity(),
                            appId -> CompletableFuture.supplyAsync(() -> readPipelineBuilds(appId), executor)));
            Map<String, CompletableFuture<List<CiBuild>>> deployReads = deployAppIds.stream()
                    .collect(Collectors.toMap(Function.identity(),
                            appId -> CompletableFuture.supplyAsync(() -> readDeployBuilds(appId), executor)));

            // wait for them; a null list means the CI tool couldn't be reached
            Map<String, List<CiBuild>> pipelineBuilds = awaitAll(pipelineReads);
            Map<String, List<CiBuild>> deployBuilds = awaitAll(deployReads);
            boolean unavailable = pipelineBuilds.containsValue(null) || deployBuilds.containsValue(null);

            // attach each entry's build
            List<History.Entry> withBuilds = entries.stream()
                    .map(entry -> new History.Entry(entry, findBuild(entry,
                            entry.getActionType() == AuditEntry.ActionType.DEPLOY ? deployBuilds : pipelineBuilds)))
                    .toList();
            return new History(withBuilds, unavailable ? List.of(History.Part.BUILDS) : List.of());
        }
    }

    /**
     * The configured apps that entries started builds of: deploy builds (deploys) or pipeline builds (the rest).
     */
    private static Set<String> appIdsWithBuilds(List<AuditEntry> entries, Map<String, App> configuredApps,
                                                boolean deploys) {
        return entries.stream()
                .filter(entry -> entry.getBuildId() != null)
                .filter(entry -> (entry.getActionType() == AuditEntry.ActionType.DEPLOY) == deploys)
                .map(AuditEntry::getTarget)
                .filter(appId -> configuredApps.containsKey(appId)
                        && (!deploys || configuredApps.get(appId).deploy() != null))
                .collect(Collectors.toSet());
    }

    private static CiBuild findBuild(AuditEntry entry, Map<String, List<CiBuild>> buildsPerApp) {
        List<CiBuild> builds = buildsPerApp.get(entry.getTarget());
        if (entry.getBuildId() == null || builds == null) {
            return null;
        }
        return builds.stream().filter(build -> build.id().equals(entry.getBuildId())).findFirst().orElse(null);
    }

    private static Map<String, List<CiBuild>> awaitAll(Map<String, CompletableFuture<List<CiBuild>>> reads) {
        Map<String, List<CiBuild>> results = new HashMap<>();
        reads.forEach((appId, read) -> results.put(appId, await(read)));
        return results;
    }

    /**
     * The app's recent pipeline builds, or null when the CI tool can't be reached.
     */
    private List<CiBuild> readPipelineBuilds(String appId) {
        try {
            return ci.listBuilds(appId);
        } catch (CiToolException e) {
            log.warn("Couldn't read the builds of {}", appId, e);
            return null;
        }
    }

    /**
     * The app's recent deploy builds, or null when the CI tool can't be reached.
     */
    private List<CiBuild> readDeployBuilds(String appId) {
        try {
            return deployService.listDeploys(appId).stream().map(DeployRun::build).toList();
        } catch (CiToolException e) {
            log.warn("Couldn't read the deploys of {}", appId, e);
            return null;
        }
    }
}
