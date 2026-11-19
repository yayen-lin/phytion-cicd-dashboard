package com.phytion.dashboard.model;

import java.time.Instant;
import java.util.List;

/**
 * One environment at a glance: the apps running there with their health, and the deploys to it still running.
 *
 * @param environment    the environment's name
 * @param apps           the apps running there, failing ones first, then in the config's order; null when Kubernetes
 *                       couldn't be reached
 * @param runningDeploys the deploy builds to it still running, newest first
 * @param unavailable    the parts whose tool couldn't be reached
 */
public record EnvironmentOverview(
        String environment,
        List<EnvironmentApp> apps,
        List<RunningDeploy> runningDeploys,
        List<Part> unavailable) {

    public enum Part {
        APPS
    }

    /**
     * A deploy build still running.
     *
     * @param appId     the app being deployed
     * @param version   the version being deployed
     * @param startedAt when the deploy build started
     * @param startedBy the user who started the build
     * @param buildId   the deploy build's id
     * @param url       the deploy build's page in the CI tool
     */
    public record RunningDeploy(String appId, String version, Instant startedAt, String startedBy, String buildId,
                                String url) {

        public static RunningDeploy from(DeployRun run) {
            return new RunningDeploy(run.appId(), run.version(), run.build().startedAt(), run.deployedBy(),
                    run.build().id(), run.build().url());
        }
    }
}
