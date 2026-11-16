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
 * @param deployedAt    when the newest deploy build of this version here started
 *                      null when none of the recent deploy builds did it, or the CI tool couldn't be reached
 * @param deployedBy    the dashboard user, or the CI tool's user when started there (null when no user did)
 * @param deployBuildId that deploy build's id in the app's deploy job
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
