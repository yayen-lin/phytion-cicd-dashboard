package com.phytion.dashboard.model;

import java.time.Instant;

/**
 * An app in an environment: what version runs there, whether it's healthy, and who deployed it. When the app isn't
 * deployed there, every field but appId is null.
 *
 * @param appId         the name of the app
 * @param version       the version running
 * @param commit        the full hash of the commit that version was built from (from the registry)
 * @param commitUrl     that commit's page in the code tool
 * @param desiredPods   how many pods it should have
 * @param readyPods     how many of them are ready
 * @param healthy       whether every desired pod is ready (false when it's scaled to 0)
 * @param deployedAt    when the dashboard deployed this version here; null when it was deployed some other way
 * @param deployedBy    who did
 * @param deployBuildId that deploy's build in the app's deploy job
 */
public record EnvironmentApp(
        String appId,
        String version,
        String commit,
        String commitUrl,
        Integer desiredPods,
        Integer readyPods,
        Boolean healthy,
        Instant deployedAt,
        String deployedBy,
        String deployBuildId) {

    /** An app not deployed in the environment */
    public static EnvironmentApp notDeployed(String appId) {
        return new EnvironmentApp(appId, null, null, null, null, null, null, null, null, null);
    }
}
